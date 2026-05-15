const UploadService = {
    folderCache: new Map(),

    async processUpload(files, context, isFolder = false, signal = null) {
        // 1. Calculamos el tamaño REAL que se enviará (Original + 12 bytes de IV por cada archivo)
        const totalEncryptedSize = files.reduce((acc, f) => acc + f.size + 12, 0);

        const fileProgressMap = new Map();
        let currentTargetId = context.currentFolderId;

        this.folderCache.clear();
        context.uploadProgress = 1;

        const CONCURRENCY_LIMIT = 4;
        const queue = [...files];
        const workers = [];

        const task = async () => {
            while (queue.length > 0 && !(signal && signal.aborted)) {
                const file = queue.shift();
                // Pasamos totalEncryptedSize en lugar del total original
                await this.handleSingleFile(file, currentTargetId, isFolder, context, fileProgressMap, totalEncryptedSize, signal);
            }
        };

        for (let i = 0; i < Math.min(CONCURRENCY_LIMIT, files.length); i++) {
            workers.push(task());
        }

        await Promise.all(workers);

        // Al terminar el envío de bytes, fijamos en 92% (fase de espera de base de datos)
        context.uploadProgress = 92;
        return true;
    },

    async handleSingleFile(file, currentTargetId, isFolder, context, progressMap, totalEncryptedSize, signal) {
        let finalName = file.name;
        let finalParentId = currentTargetId;

        if (isFolder && file.webkitRelativePath) {
            const parts = file.webkitRelativePath.split('/');
            finalName = parts.pop();
            if (parts.length > 0) {
                finalParentId = await this.resolveSubfolderChainCached(parts, currentTargetId, context, signal);
            }
        }

        await this.uploadSingle(file, finalParentId, finalName, (bytes) => {
            // Guardamos el progreso de este archivo individual
            progressMap.set(file, bytes);

            // Sumamos el progreso de todos los archivos activos
            const totalUploaded = Array.from(progressMap.values()).reduce((a, b) => a + b, 0);

            // Calculamos el porcentaje sobre la base de 90 (reservando el 10% final para la UI)
            const calculatedPercentage = Math.floor((totalUploaded / totalEncryptedSize) * 90);

            // REGLA DE MONOTONICIDAD: El progreso solo sube, nunca baja
            // Esto evita fluctuaciones si los eventos de red llegan desordenados
            if (calculatedPercentage > context.uploadProgress && calculatedPercentage <= 90) {
                context.uploadProgress = calculatedPercentage;
            }
        }, context, totalEncryptedSize, signal);
    },

    async resolveSubfolderChainCached(parts, startParentId, context, signal) {
        let currentId = startParentId;

        for (const part of parts) {
            if (signal && signal.aborted) throw new Error('Aborted');

            const cacheKey = `${currentId}_${part}`;

            if (this.folderCache.has(cacheKey)) {
                currentId = await this.folderCache.get(cacheKey);
            } else {
                const syncPromise = (async () => {
                    try {
                        const folder = await API.createFolderSync(part, currentId, context);
                        return folder.id;
                    } catch (e) {
                        this.folderCache.delete(cacheKey);
                        throw e;
                    }
                })();

                this.folderCache.set(cacheKey, syncPromise);
                currentId = await syncPromise;
                this.folderCache.set(cacheKey, currentId);
            }
        }
        return currentId;
    },

    async uploadSingle(file, parentId, fileName, onProgress, context, totalBatchSize, signal) {
        context.status = `Cifrando: ${fileName}...`;
        const { encryptedBlob, encryptedFileKey } = await CryptoService.encryptFileForUpload(file);
        context.status = `Subiendo: ${fileName}...`;

        const formData = new FormData();
        formData.append("file", encryptedBlob);
        formData.append("fileName", fileName);
        formData.append("parentId", (parentId && !isNaN(parentId)) ? parentId : "");
        formData.append("totalBatchSize", totalBatchSize);
        formData.append("encryptedFileKey", encryptedFileKey);

        return API.uploadSingle(formData, onProgress, signal);
    }
};

const AppUploadMethods = {
    onDragOver(event) {
        const isExternalFile = event.dataTransfer.types.includes('Files');
        if (isExternalFile) {
            this.isDragging = true;
        }
    },

    onDragLeave() {
        this.isDragging = false;
    },

    async onDrop(event) {
        this.isDragging = false;
        const items = event.dataTransfer.items;
        if (!items) return;

        let filesToUpload = [];
        const scanPromises = [];
        let containsFolder = false;

        for (let i = 0; i < items.length; i++) {
            const item = items[i].webkitGetAsEntry();
            if (item) {
                if (item.isDirectory) containsFolder = true;
                scanPromises.push(this.traverseFileTree(item, "", filesToUpload));
            }
        }

        await Promise.all(scanPromises);

        if (filesToUpload.length > 0) {
            await this._handleUploadProcess(filesToUpload, containsFolder);
        }
    },

    async traverseFileTree(item, path, fileList) {
        path = path || "";

        if (item.isFile) {
            const file = await new Promise((resolve, reject) => {
                item.file(resolve, reject);
            });

            Object.defineProperty(file, 'webkitRelativePath', {
                value: path + file.name,
                writable: false
            });

            fileList.push(file);
        } else if (item.isDirectory) {
            const dirReader = item.createReader();
            const readEntries = async () => {
                const entries = await new Promise((resolve, reject) => {
                    dirReader.readEntries(resolve, reject);
                });

                if (entries.length > 0) {
                    for (const entry of entries) {
                        await this.traverseFileTree(entry, path + item.name + "/", fileList);
                    }
                    await readEntries();
                }
            };

            await readEntries();
        }
    },

    async _handleUploadProcess(files, isFolder) {
        try {
            const totalBatchSize = files.reduce((acc, f) => acc + f.size, 0);
            const availableQuota = this.stats.maxQuota - this.stats.totalSize;

            if (totalBatchSize > availableQuota) {
                this.showError(`Espacio insuficiente. Faltan ${this.formatSize(totalBatchSize - availableQuota)}`);
                return;
            }

            this.uploadController = new AbortController();
            const success = await UploadService.processUpload(files, this, isFolder, this.uploadController.signal);

            if (success) {
                this.status = "Finalizando...";
                this.uploadProgress = 95;
                await new Promise(r => setTimeout(r, 200));
                await this.refreshAppData();
                this.uploadProgress = 100;
                this.showInfo(`¡Subida completada!`);
            }
        } catch (e) {
            if (e.name === 'AbortError' || e.message === 'Aborted') {
                this.showInfo("Subida cancelada");
            } else {
                this.showError(e.message || "Error en la subida");
            }
        } finally {
            setTimeout(() => {
                this.uploadProgress = 0;
                this.uploadController = null;
                this.status = "";
            }, 1200);
        }
    },

    async onUpload() {
        await this._handleUploadProcess(Array.from(this.$refs.fileInput.files), false);
    },

    cancelUpload() {
        if (this.uploadController) {
            this.uploadController.abort();
            this.uploadController = null;
            this.uploadProgress = 0;
            this.status = "Subida cancelada por el usuario";

            setTimeout(() => this.refreshAppData(), 500);
        }
    },

    async uploadFolder() {
        await this._handleUploadProcess(Array.from(this.$refs.folderInput.files), true);
    }
};