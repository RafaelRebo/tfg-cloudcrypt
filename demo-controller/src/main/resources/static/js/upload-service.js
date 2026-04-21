const UploadService = {
    // Sube un archivo informando del progreso global al componente Vue
    async uploadSingle(file, folderPath, uploadedBytesSoFar, totalSize, context) {
        return new Promise((resolve, reject) => {
            const formData = new FormData();
            formData.append("file", file);
            formData.append("username", context.username);
            formData.append("password", context.password);
            formData.append("folderPath", folderPath);
            formData.append("fileName", file.name);

            const xhr = new XMLHttpRequest();

            xhr.upload.addEventListener("progress", (e) => {
                if (e.lengthComputable) {
                    const currentFileProgress = e.loaded;
                    const globalPercent = Math.round(((uploadedBytesSoFar + currentFileProgress) / totalSize) * 100);
                    context.uploadProgress = globalPercent;

                    if (totalSize === file.size) { // Es un solo archivo
                        context.status = globalPercent === 100 ? "Cifrando e integrando..." : `Subiendo: ${globalPercent}%`;
                    } else { // Es una carpeta
                        context.status = `Subiendo carpeta: ${globalPercent}% (${(uploadedBytesSoFar / (1024*1024)).toFixed(1)} MB enviados)`;
                    }
                }
            });

            xhr.onreadystatechange = () => {
                if (xhr.readyState === XMLHttpRequest.DONE) {
                    if (xhr.status === 200) resolve();
                    else reject(xhr.responseText);
                }
            };

            xhr.open("POST", "/api/files/upload", true);
            xhr.send(formData);
        });
    }
};