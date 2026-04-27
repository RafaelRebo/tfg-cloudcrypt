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
            // USAMOS EL NUEVO MODAL DEDICADO
            proceed = await context.askConfirmation(`Vas a eliminar "${file.fileName}" permanentemente. No podrás recuperarlo.`);
        }

        if (!proceed) return;

        try {
            const res = await API.deleteFile(file.id);
            if (res.ok) {
                const msg = isTrashed ? "Archivo destruido permanentemente" : "Archivo movido a la papelera";
                context.showInfo(msg); // <--- AHORA ES AZUL (INFO)
                await context.refreshAppData();
            }
        } catch (error) {
            context.showError("Error al eliminar el archivo");
        }
    },

    async restoreFile(file, context) {
        try {
            const res = await fetch(`/api/files/${file.id}/restore`, { method: 'POST' });
            if (res.ok) {
                context.showInfo("Archivo restaurado en su ubicación original"); // <--- INFO
                await context.refreshAppData();
            }
        } catch (e) { context.showError("Error al restaurar"); }
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