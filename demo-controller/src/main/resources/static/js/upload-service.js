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
    async uploadSingle(file, parentId, fileName, onProgress, context, totalBatchSize, signal) {
        const formData = new FormData();
        formData.append("file", file);
        formData.append("password", context.password);
        formData.append("parentId", (parentId && !isNaN(parentId)) ? parentId : "");
        formData.append("fileName", fileName);
        formData.append("totalBatchSize", totalBatchSize);

        // 4. Lo pasamos finalmente a la función de la API
        return API.uploadSingle(formData, onProgress, signal);
    }
};