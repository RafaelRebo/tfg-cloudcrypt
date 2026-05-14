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

    getFileIconSvg(mime) {
        // ICONO DE IMAGEN (PNG, JPG, etc.)
        if (mime && mime.startsWith('image/')) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="#5cbeff" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect width="18" height="18" x="3" y="3" rx="2" ry="2"/>
                <circle cx="9" cy="9" r="2"/>
                <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>
            </svg>`;
        }

        // ICONO DE AUDIO (MP3, WAV, etc.)
        if (mime && mime.startsWith('audio/')) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="#93a2b8" stroke="#93a2b8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 18V5l12-2v13"/>
                <circle cx="6" cy="18" r="3"/>
                <circle cx="18" cy="16" r="3"/>
            </svg>`;
        }

        if (mime && mime.startsWith('video/')) {
            return `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#93a2b8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-clapperboard-icon lucide-clapperboard"><path d="m12.296 3.464 3.02 3.956"/><path d="M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z"/><path d="M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="m6.18 5.276 3.1 3.899"/></svg>`;
        }

        // ICONO POR DEFECTO / DOCUMENTOS (PDF, TXT, etc.)
        // Mantiene el diseño que te gustó de la imagen image_792770.png
        return `<svg width="20" height="20" viewBox="0 0 24 24" fill="#ffffff" stroke="#93a2b8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/>
            <path d="M14 2v4a2 2 0 0 0 2 2h4"/>
        </svg>`;
    }

};