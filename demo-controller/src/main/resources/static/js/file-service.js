const FileService = {
    // En FileService.js
    async downloadFile(fileId, fileName, password, context) {
        context.status = "Descargando y descifrando...";
        try {
            // 1. Bajar archivo y llave
            const res = await API.download(fileId, password);
            const keyRes = await fetch(`/api/files/${fileId}/key`, { headers: API.getAuthHeader() });

            const encryptedBlob = await res.blob();
            const { encryptedFileKey } = await keyRes.json();

            // 2. Descifrar
            const aesKey = await CryptoService.unwrapKey(encryptedFileKey, window.userPrivateKey);
            const decryptedBlob = await CryptoService.decryptFile(encryptedBlob, aesKey);

            // 3. Ofrecer al navegador
            const url = window.URL.createObjectURL(decryptedBlob);
            const a = document.createElement('a');
            a.href = url;
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            context.status = "Descarga completada.";
        } catch (err) {
            context.showError("Error al descifrar el archivo.");
        }
    },

    async deleteFile(file, context) {
        const isTrashed = !!file.deletedAt;
        let proceed = true;

        if (isTrashed) {
            proceed = await context.askConfirmation(`¿Eliminar "${file.fileName}" permanentemente?`);
        }

        if (!proceed) return;

        try {
            const res = await API.deleteFile(file.id);
            if (res.ok) {
                context.showInfo(isTrashed ? "Eliminado definitivamente" : "Movido a la papelera");
                await context.refreshAppData();
            }
        } catch (error) {
            context.showError("Error al eliminar");
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