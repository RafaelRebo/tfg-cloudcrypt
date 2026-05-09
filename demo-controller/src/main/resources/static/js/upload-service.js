const UploadService = {
    // 1. Añadimos 'signal' como cuarto parámetro
    async processUpload(files, context, isFolder = false, signal = null) {
        const totalSize = files.reduce((acc, f) => acc + f.size, 0);
        const fileProgressMap = new Map();
        let globalAction = null;
        let currentTargetId = context.currentFolderId;

        if (isFolder && files.length > 0) {
            const rootName = files[0].webkitRelativePath.split('/')[0];
            // En las llamadas a API también podrías pasar el signal si quieres que se cancelen las creaciones de carpetas
            const check = await API.checkExists(rootName, currentTargetId);

            if (check.exists) {
                const res = await context.askUserForDuplicateAction(rootName, true);
                if (res.action === 'skip') return false;
                if (res.applyToAll) globalAction = res.action;

                if (res.action === 'copy') {
                    const newFolder = await API.createFolderSync(check.suggestedName, currentTargetId, context);
                    currentTargetId = newFolder.id;
                } else {
                    currentTargetId = check.existingId;
                }
            } else {
                const newFolder = await API.createFolderSync(rootName, currentTargetId, context);
                currentTargetId = newFolder.id;
            }
        }

        for (const file of files) {
            // VERIFICACIÓN PREVENTIVA: Si el usuario canceló, salimos del bucle inmediatamente
            if (signal && signal.aborted) throw new Error('Aborted');

            let finalName = file.name;
            let finalParentId = currentTargetId;

            if (isFolder && file.webkitRelativePath) {
                const parts = file.webkitRelativePath.split('/');
                parts.shift();
                finalName = parts.pop();
                if (parts.length > 0) {
                    finalParentId = await this.resolveSubfolderChain(parts, currentTargetId, context, signal); // <--- Pasar signal
                }
            }

            let action = globalAction;
            if (!action) {
                const check = await API.checkExists(finalName, finalParentId);
                if (check.exists) {
                    const res = await context.askUserForDuplicateAction(finalName, false);
                    action = res.action;
                    if (res.applyToAll) globalAction = res.action;
                }
            }

            if (action === 'skip') continue;

            if (action === 'copy') {
                const check = await API.checkExists(finalName, finalParentId, context.username);
                finalName = check.suggestedName;
            }

            // 2. Pasamos el signal al método uploadSingle
            await this.uploadSingle(file, finalParentId, finalName, (bytes) => {
                fileProgressMap.set(file, bytes);
                const total = Array.from(fileProgressMap.values()).reduce((a, b) => a + b, 0);
                context.uploadProgress = Math.min(Math.round((total / totalSize) * 100), 100);
            }, context, totalSize, signal); // <--- Signal incluido aquí
        }
        return true;
    },

    async resolveSubfolderChain(parts, startParentId, context, signal) { // <--- Recibe signal
        let currentId = startParentId;
        for (const part of parts) {
            // Si el usuario canceló, no seguimos creando carpetas
            if (signal && signal.aborted) throw new Error('Aborted');

            const folder = await API.createFolderSync(part, currentId, context);
            currentId = folder.id;
        }
        return currentId;
    },

    // 3. Actualizamos la firma para recibir el signal
    // Dentro de uploadSingle en UploadService.js
    async uploadSingle(file, parentId, fileName, onProgress, context, totalBatchSize, signal) {
            // 1. Verificar si la llave pública existe en RAM
            if (!window.userPublicKey) {
                throw new Error("Identidad criptográfica no cargada. Por favor, re-inicia sesión.");
            }

            // 2. Generar llave AES aleatoria para este archivo
            const fileKey = await window.crypto.subtle.generateKey(
                { name: "AES-GCM", length: 256 },
                true,
                ["encrypt", "decrypt"]
            );

            // 3. Cifrar el archivo localmente (devuelve un Blob con IV + datos)
            const encryptedBlob = await CryptoService.encryptFile(file, fileKey);

            // 4. Exportar la llave AES a formato 'raw' (bytes) para poder envolverla con RSA
            const rawAesKey = await window.crypto.subtle.exportKey("raw", fileKey);

            // 5. Envolver (Wrap) la llave AES con la Pública RSA del usuario
            // USAMOS window.userPublicKey que es donde AuthService la guarda
            const encryptedFileKey = await CryptoService.wrapKey(rawAesKey, window.userPublicKey);

            // 6. Preparar el FormData para enviar al servidor
            const formData = new FormData();
            formData.append("file", encryptedBlob);
            formData.append("fileName", fileName);
            formData.append("parentId", (parentId && !isNaN(parentId)) ? parentId : "");
            formData.append("totalBatchSize", totalBatchSize);
            formData.append("encryptedFileKey", encryptedFileKey);

            // 7. Lanzar la petición XHR
            return API.uploadSingle(formData, onProgress, signal);
        }
};