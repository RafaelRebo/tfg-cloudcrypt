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
            alert("Error: " + err.message);
            context.status = "Error en la descarga.";
        }
    },
    async deleteFile(id, context) {
        if (confirm("¿Estás seguro de que quieres borrar este archivo para siempre?")) {
            try {
                const res = await API.deleteFile(id);
                if (res.ok) {
                    context.status = "Archivo eliminado correctamente.";
                    await context.refreshAppData();
                } else {
                    alert("Error al intentar borrar el archivo.");
                }
            } catch (error) { console.error(error); }
        }
    }
};