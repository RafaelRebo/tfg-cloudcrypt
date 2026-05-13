const FileService = {
    // En FileService.js
   // Sustituye el método downloadFile en FileService.js
   async downloadFile(fileId, fileName, _, context) { // Quitamos el parámetro password
       context.status = "Descargando y descifrando...";
       try {
           // 1. Descargamos el archivo cifrado (Bytes brutos)
           const res = await API.download(fileId);
           if (!res.ok) throw new Error("Acceso denegado al archivo");
           const encryptedBlob = await res.blob();

           // 2. Pedimos el 'Sobre Digital' (la llave AES cifrada para nosotros)
           const keyRes = await fetch(`/api/files/${fileId}/key`, { headers: API.getAuthHeader() });
           if (!keyRes.ok) throw new Error("No tienes permiso para obtener la llave");
           const { encryptedFileKey } = await keyRes.json();

           // 3. Desciframos la llave AES usando nuestra Llave Privada RSA
           const aesKeyObj = await CryptoService.unwrapKey(encryptedFileKey, window.userPrivateKey);

           // 4. Desciframos el contenido del archivo con la AES recuperada
           const decryptedBuffer = await CryptoService.decryptFile(encryptedBlob, aesKeyObj);

           // 5. Ofrecer la descarga al navegador
           const url = window.URL.createObjectURL(new Blob([decryptedBuffer]));
           const a = document.createElement('a');
           a.href = url;
           a.download = fileName;
           document.body.appendChild(a);
           a.click();
           window.URL.revokeObjectURL(url);

           context.status = "Descarga completada.";
           context.showInfo("Archivo descargado y descifrado correctamente.");
       } catch (err) {
           console.error(err);
           context.showError("Error crítico: " + err.message);
       }
   },

    async deleteFile(file, context) {
        const isTrashed = !!file.deletedAt;
        const isShared = context.currentCategory === 'shared';
        let proceed = false;

        if (isTrashed) {
            proceed = await context.askConfirmation(`¿Eliminar "${file.fileName}" permanentemente?`);
        } else if (isShared) {
            proceed = await context.askConfirmation(`¿Deseas quitar tu acceso a "${file.fileName}"? No podrás volver a verlo a menos que te lo compartan de nuevo.`);
        } else {
            proceed = await context.askConfirmation(`¿Mover "${file.fileName}" a la papelera?`);
        }

        if (!proceed) return;

        try {
            const res = await API.deleteFile(file.id);
            if (res.ok) {
                context.showInfo(isShared ? "Acceso revocado" : (isTrashed ? "Eliminado" : "Papelera"));
                await context.refreshAppData();
            } else {
                throw new Error();
            }
        } catch (error) {
            context.showError("No se pudo eliminar el elemento.");
        }
    },

    async restoreFile(file, context) {
        try {
            const res = await fetch(`/api/files/${file.id}/restore`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('jwtToken')}`
                }
            });
            if (res.ok) {
                context.showInfo("Restaurado correctamente");
                await context.refreshAppData();
            }
        } catch (e) {
            context.showError("Error al restaurar");
        }
    },

    // En file-service.js
    getDisplayFiles(allUserFiles, currentFolder, currentCategory) {
        if (currentCategory === 'trash') {
            // En la papelera solo mostramos lo que tiene fecha de borrado
            return allUserFiles.filter(f => f.deletedAt !== null);
        } else if (currentCategory === 'shared') {
            return allUserFiles;
        } else {
            // En "Mis Archivos" o categorías, NUNCA mostrar lo que tenga fecha de borrado
            return allUserFiles.filter(f => f.deletedAt === null);
        }
    },

    getPathSegments(currentFolder, currentCategory, trashRootPath) {
        if (!currentFolder || currentFolder === '/') return [];

        const segments = [];
        const parts = currentFolder.split('/').filter(p => p !== '');
        let pathAccumulated = '';

        if (currentCategory === 'trash' && trashRootPath) {
            const rootParts = trashRootPath.split('/').filter(p => p !== '');
            let inTrashPath = false;

            parts.forEach((name, index) => {
                pathAccumulated += '/' + name;

                // Solo empezamos a añadir al breadcrumb cuando llegamos
                // a la carpeta que marcó el inicio de la papelera
                if (name === rootParts[rootParts.length - 1] || inTrashPath) {
                    inTrashPath = true;
                    segments.push({
                        name: name,
                        path: pathAccumulated
                    });
                }
            });
            return segments;
        }

        // Ruta normal para Mis Archivos
        parts.forEach((name) => {
            pathAccumulated += '/' + name;
            segments.push({
                name: name,
                path: pathAccumulated
            });
        });

        return segments;
    },

    normalizePath(path) {
        if (!path || path === '/') return '/';
        let p = path.replace(/\/+/g, '/');
        if (p.endsWith('/') && p.length > 1) p = p.slice(0, -1);
        if (!p.startsWith('/')) p = '/' + p;
        return p;
    },

    getFileIcon(mime) {
        if (!mime) return '📄';
        if (mime.startsWith('image/')) return '🖼️';
        if (mime.startsWith('video/')) return '🎬';
        if (mime.startsWith('audio/')) return '🎵';
        if (mime === 'application/pdf') return '📕';
        if (mime.includes('text') || mime.includes('json')) return '📝';
        return '📄';
    },

};