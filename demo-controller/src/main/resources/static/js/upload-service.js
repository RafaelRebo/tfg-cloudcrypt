const UploadService = {
    async processUpload(files, context, isFolder = false) {
        const totalSize = files.reduce((acc, f) => acc + f.size, 0);
        const fileProgressMap = new Map();
        let globalAction = null;
        let currentTargetId = context.currentFolderId;

        // --- PASO 1: CARPETA RAÍZ ---
        if (isFolder && files.length > 0) {
            const rootName = files[0].webkitRelativePath.split('/')[0];
            const check = await API.checkExists(rootName, currentTargetId, context.username);

            if (check.exists) {
                const res = await context.askUserForDuplicateAction(rootName, true);
                if (res.action === 'skip') return false;
                if (res.applyToAll) globalAction = res.action;

                if (res.action === 'copy') {
                    // REGLA 2 (Mantener): Creamos una carpeta nueva (independiente)
                    // Usamos el nombre sugerido (ej: "a (1)")
                    const newFolder = await API.createFolderSync(check.suggestedName, currentTargetId, context);
                    currentTargetId = newFolder.id;
                } else {
                    // REGLA 2 (Reemplazar/Fusionar): Usamos el ID de la carpeta que ya existe
                    currentTargetId = check.existingId;
                }
            } else {
                const newFolder = await API.createFolderSync(rootName, currentTargetId, context);
                currentTargetId = newFolder.id;
            }
        }

        // --- PASO 2: ARCHIVOS ---
        for (const file of files) {
            let finalName = file.name;
            let finalParentId = currentTargetId;

            if (isFolder && file.webkitRelativePath) {
                const parts = file.webkitRelativePath.split('/');
                parts.shift();
                finalName = parts.pop();
                if (parts.length > 0) {
                    finalParentId = await this.resolveSubfolderChain(parts, currentTargetId, context);
                }
            }

            // REGLA 3: Lógica de duplicados para archivos
            let action = globalAction;
            if (!action) {
                const check = await API.checkExists(finalName, finalParentId, context.username);
                if (check.exists) {
                    const res = await context.askUserForDuplicateAction(finalName, false);
                    action = res.action;
                    if (res.applyToAll) globalAction = res.action;
                }
            }

            if (action === 'skip') continue;

            // Si es copia, cambiamos el nombre para que el server cree otro registro
            if (action === 'copy') {
                const check = await API.checkExists(finalName, finalParentId, context.username);
                finalName = check.suggestedName;
            }

            // Si es 'overwrite', el nombre se queda igual y el backend borrará el viejo
            await this.uploadSingle(file, finalParentId, finalName, (bytes) => {
                fileProgressMap.set(file, bytes);
                const total = Array.from(fileProgressMap.values()).reduce((a, b) => a + b, 0);
                context.uploadProgress = Math.min(Math.round((total / totalSize) * 100), 100);
            }, context);
        }
        return true;
    },

    async resolveSubfolderChain(parts, startParentId, context) {
        let currentId = startParentId;
        for (const part of parts) {
            // Importante: createFolderSync ahora debe devolver el ID de la carpeta
            const folder = await API.createFolderSync(part, currentId, context);
            currentId = folder.id;
        }
        return currentId;
    },

    // En upload-service.js
    async uploadSingle(file, parentId, fileName, onProgress, context) {
        const formData = new FormData();
        formData.append("file", file);
        formData.append("password", context.password);
        formData.append("parentId", (parentId && !isNaN(parentId)) ? parentId : "");
        formData.append("fileName", fileName);
        formData.append("authenticatedUser", context.username);

        return API.uploadSingle(formData, onProgress);
    }
};