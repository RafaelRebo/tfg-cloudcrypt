const FileService = {
    async downloadFile(fileId, fileName, password, context) {
        context.status = "Descifrando y preparando descarga...";
        try {
            const res = await API.download(fileId, password);
            if (!res.ok) throw new Error("Error en la descarga");

            const blob = await res.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = fileName;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            a.remove();
            context.status = "Descarga completada.";
        } catch (err) {
            context.showError("Error en la descarga: " + err.message);
            context.status = "Error en la descarga.";
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

    getDisplayFiles(allUserFiles, currentFolder, currentCategory) {
        const isDeleted = f => !!f.deletedAt;
        const viewingTrash = currentCategory === 'trash';
        const currentNormalized = this.normalizePath(currentFolder);

        if (viewingTrash) {
            if (currentNormalized === '/') {
                return allUserFiles.filter(f => {
                    const fPath = this.normalizePath(f.folderPath);
                    if (fPath === '/') return true;

                    const parts = fPath.split('/').filter(p => p);
                    const parentName = parts[parts.length - 1];
                    const grandparentPath = this.normalizePath('/' + parts.slice(0, -1).join('/'));

                    const isParentInList = allUserFiles.some(p =>
                        p.fileName === parentName &&
                        this.normalizePath(p.folderPath) === grandparentPath &&
                        isDeleted(p)
                    );
                    return !isParentInList;
                });
            } else {
                return allUserFiles;
            }
        }
        return allUserFiles;
    },

    getPathSegments(currentFolder, currentCategory) {
        if (!currentFolder || currentFolder === '/') return [];

        const segments = [];
        const parts = currentFolder.split('/').filter(p => p !== '');
        let pathAccumulated = '';

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