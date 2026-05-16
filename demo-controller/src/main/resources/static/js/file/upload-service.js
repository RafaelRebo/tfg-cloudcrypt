const UploadService = {
    folderCache: new Map(),

    async processUpload(files, context, isFolder = false, signal = null) {
        this.folderCache.clear();
        context.uploadProgress = 1;
        let currentTargetId = context.currentFolderId;

        // FASE 1: 🛡️ CRIBADO SECUENCIAL DE CONFLICTOS
        const finalFilesToUpload = [];
        let applyAllAction = null;

        for (const file of files) {
            if (signal && signal.aborted) throw new Error('Aborted');

            let checkName = file.name;
            let isDirectoryElement = false;

            if (isFolder && file.webkitRelativePath) {
                checkName = file.webkitRelativePath.split('/')[0];
                isDirectoryElement = true;
            }

            // Para archivos individuales o el nodo raíz de la carpeta en su primera comprobación
            if (!isFolder || (isFolder && finalFilesToUpload.length === 0)) {
                const checkRes = await API.checkExists(checkName, currentTargetId);

                if (checkRes.exists) {
                    let currentAction = applyAllAction;

                    if (!currentAction) {
                        context.status = `Esperando resolución de conflicto: ${checkName}`;
                        const userChoice = await context.askUserForDuplicateAction(checkName, isDirectoryElement);

                        if (userChoice.applyToAll) {
                            applyAllAction = userChoice.action;
                        }
                        currentAction = userChoice.action;
                    }

                    // 💥 EJECUCIÓN TRIPARTITA DEL CRITERIO DE CONFLICTO
                    if (currentAction === 'skip') {
                        if (isFolder) return false; // Si el usuario omite la carpeta, cancelamos la subida completa
                        continue;
                    }

                    if (currentAction === 'copy') {
                        if (!isFolder) {
                            const dot = file.name.lastIndexOf('.');
                            const nameNoExt = dot !== -1 ? file.name.substring(0, dot) : file.name;
                            const ext = dot !== -1 ? file.name.substring(dot) : '';

                            let counter = 1;
                            let candidateName = `${nameNoExt} (Copia)${ext}`;

                            // 🔄 Bucle reactivo: preguntamos al servidor si la copia ya existe
                            let nestedCheck = await API.checkExists(candidateName, currentTargetId);
                            while (nestedCheck.exists) {
                                counter++;
                                candidateName = `${nameNoExt} (Copia ${counter})${ext}`;
                                nestedCheck = await API.checkExists(candidateName, currentTargetId);
                            }

                            // Asignamos el nombre final libre (ej: "foto (Copia 3).png")
                            file.customName = candidateName;
                        } else {
                            let counter = 1;
                            let candidateRootName = `${checkName} (Copia)`;

                            // Mismo algoritmo incremental aplicado al nodo raíz de la carpeta
                            let nestedCheck = await API.checkExists(candidateRootName, currentTargetId);
                            while (nestedCheck.exists) {
                                counter++;
                                candidateRootName = `${checkName} (Copia ${counter})`;
                                nestedCheck = await API.checkExists(candidateRootName, currentTargetId);
                            }

                            file.customRootName = candidateRootName;
                        }
                    }

                    if (currentAction === 'overwrite') {
                        // ⚡ EL FIX CRÍTICO: Eliminamos el elemento antiguo (fichero o carpeta raíz)
                        // antes de lanzar la ráfaga de subida para limpiar el espacio por completo.
                        context.status = `Reemplazando versión anterior de: ${checkName}...`;

                        // NOTA: Asegúrate de que el JSON de respuesta de tu endpoint check-exists incluya el ID (checkRes.id)
                        await API.deleteFile(checkRes.existingId);
                    }
                }
            } else {
                // Herencia de nombres para sub-ficheros dentro de carpetas renombradas como copia
                const rootItem = files[0];
                if (applyAllAction === 'skip') continue;
                if (rootItem.customRootName) {
                    file.customRootName = rootItem.customRootName;
                }
            }

            finalFilesToUpload.push(file);
        }

        if (finalFilesToUpload.length === 0) return false;

        // FASE 2: 🚀 ARRANQUE MULTIHILO CONCURRENTE (Con el espacio ya saneado)
        const totalEncryptedSize = finalFilesToUpload.reduce((acc, f) => acc + f.size + 12, 0);
        const fileProgressMap = new Map();

        const CONCURRENCY_LIMIT = 4;
        const queue = [...finalFilesToUpload];
        const workers = [];

        const task = async () => {
            while (queue.length > 0 && !(signal && signal.aborted)) {
                const file = queue.shift();
                await this.handleSingleFile(file, currentTargetId, isFolder, context, fileProgressMap, totalEncryptedSize, signal);
            }
        };

        for (let i = 0; i < Math.min(CONCURRENCY_LIMIT, finalFilesToUpload.length); i++) {
            workers.push(task());
        }

        await Promise.all(workers);
        context.uploadProgress = 92;
        return true;
    },

    async handleSingleFile(file, currentTargetId, isFolder, context, progressMap, totalEncryptedSize, signal) {
            let finalName = file.customName || file.name;
            let finalParentId = currentTargetId;

            if (isFolder && file.webkitRelativePath) {
                const parts = file.webkitRelativePath.split('/');
                finalName = parts.pop();

                // Si la raíz de la carpeta se renombró a "Carpeta (Copia)", inyectamos la mutación en la ruta
                if (file.customRootName && parts.length > 0) {
                    parts[0] = file.customRootName;
                }

                if (parts.length > 0) {
                    finalParentId = await this.resolveSubfolderChainCached(parts, currentTargetId, context, signal);
                }
            }

            await this.uploadSingle(file, finalParentId, finalName, (bytes) => {
                progressMap.set(file, bytes);
                const totalUploaded = Array.from(progressMap.values()).reduce((a, b) => a + b, 0);
                const calculatedPercentage = Math.floor((totalUploaded / totalEncryptedSize) * 90);
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