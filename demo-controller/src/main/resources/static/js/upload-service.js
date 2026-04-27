const UploadService = {
    // Orquestador principal: Recibe los archivos y decide cómo procesarlos
    async processUpload(files, context, isFolder = false) {
        const totalSize = files.reduce((acc, f) => acc + f.size, 0);

        // Validación de cuota (Idéntica a la que tenías)
        if (totalSize > context.stats.maxQuota - context.stats.totalSize) {
            const msg = isFolder ? "La carpeta completa supera tu espacio disponible." : "El total de los archivos seleccionados supera tu espacio disponible.";
            context.showError(msg);
            return false; // Indica que no se inició nada
        }

        let currentUploadedBytes = 0;
        let base = context.currentFolder === '/' ? '' : context.currentFolder;

        for (let f of files) {
            // Lógica de carpetas: si es carpeta calcula el path relativo, si no usa la actual
            let targetPath = context.currentFolder;
            if (isFolder) {
                const rel = f.webkitRelativePath.substring(0, f.webkitRelativePath.lastIndexOf('/'));
                targetPath = (base + '/' + rel).replace(/\/+/g, '/');
            }

            try {
                await this.uploadSingle(f, targetPath, currentUploadedBytes, totalSize, context);
                currentUploadedBytes += f.size;
            } catch (e) {
                // Lógica de "Todo o nada": Si falla uno, lanza el error hacia afuera para parar el bucle
                throw { message: e, fileName: f.name };
            }
        }
        return true; // Éxito total
    },

    // El motor XHR (Mantengo tu lógica intacta)
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

                    if (totalSize === file.size) {
                        context.status = globalPercent === 100 ? "Cifrando e integrando..." : `Subiendo: ${globalPercent}%`;
                    } else {
                        context.status = `Subiendo: ${globalPercent}% (${(uploadedBytesSoFar / (1024*1024)).toFixed(1)} MB enviados)`;
                    }
                }
            });

            xhr.onerror = () => reject("Error de red o servidor no alcanzable");
            xhr.onreadystatechange = () => {
                if (xhr.readyState === XMLHttpRequest.DONE) {
                    if (xhr.status >= 200 && xhr.status < 300) resolve();
                    else {
                        const msg = xhr.status === 0 ? "Conexión perdida" : xhr.responseText;
                        reject(msg);
                    }
                }
            };

            xhr.open("POST", "/api/files/upload", true);
            xhr.send(formData);
        });
    }
};