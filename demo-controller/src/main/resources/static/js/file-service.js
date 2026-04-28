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