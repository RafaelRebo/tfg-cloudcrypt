const FileService = {
   async downloadFile(fileId, fileName, _, context) {
       context.status = "Descargando y descifrando...";
       try {
           const res = await API.download(fileId);
           if (!res.ok) {
               const errorMsg = await API.extractErrorMessage(res);
               throw new Error(errorMsg);
           }
           const encryptedBlob = await res.blob();

           const keyRes = await fetch(`/api/files/${fileId}/key`, { headers: API.getAuthHeader() });
           if (!keyRes.ok) {
               const errorMsg = await API.extractErrorMessage(keyRes);
               throw new Error(errorMsg);
           }
           const { encryptedFileKey } = await keyRes.json();

           const aesKeyObj = await CryptoService.unwrapKey(encryptedFileKey);
           const decryptedBuffer = await CryptoService.decryptFile(encryptedBlob, aesKeyObj);

           const url = window.URL.createObjectURL(new Blob([decryptedBuffer]));
           const a = document.createElement('a');
           a.href = url;
           a.download = fileName;
           a.click();
           window.URL.revokeObjectURL(url);

           context.status = "";
           context.showInfo("Archivo descargado correctamente.");
       } catch (err) {
           context.status = "";
           context.showError(err.message);
       }
   },

   async downloadFolder(folderId, folderName, context) {
       context.status = `Calculando árbol de archivos para: ${folderName}...`;
       try {
           const res = await fetch(`/api/files/folder-content-recursive/${folderId}`, { headers: API.getAuthHeader() });
           if (!res.ok) {
               const errorMsg = await API.extractErrorMessage(res);
               throw new Error(errorMsg);
           }
           const items = await res.json();
           const filesToProcess = items.filter(item => item.fileType !== 'application/x-directory');

           if (filesToProcess.length === 0) {
               context.showInfo(`La carpeta "${folderName}" está vacía.`);
               context.status = "";
               return;
           }

           const rootFolder = context.allUserFiles.find(f => f.id === folderId);
           const rootPath = rootFolder ? this.normalizePath(rootFolder.folderPath + '/' + rootFolder.fileName) : '';
           const zip = new JSZip();

           const queue = [...filesToProcess];
           let completedCount = 0;
           const CONCURRENCY_LIMIT = 6;

           const workerTask = async () => {
               while (queue.length > 0) {
                   const file = queue.shift();
                   if (!file) continue;

                   try {
                       const fileRes = await API.download(file.id);
                       if (!fileRes.ok) {
                           const errorMsg = await API.extractErrorMessage(fileRes);
                           throw new Error(errorMsg);
                       }
                       const encryptedBlob = await fileRes.blob();

                       const keyRes = await fetch(`/api/files/${file.id}/key`, { headers: API.getAuthHeader() });
                       if (!keyRes.ok) {
                           const errorMsg = await API.extractErrorMessage(keyRes);
                           throw new Error(errorMsg);
                       }
                       const { encryptedFileKey } = await keyRes.json();

                       const aesKeyObj = await CryptoService.unwrapKey(encryptedFileKey);
                       const decryptedBuffer = await CryptoService.decryptFile(encryptedBlob, aesKeyObj);

                       let relativeFolder = '';
                       if (rootPath && file.folderPath.startsWith(rootPath)) {
                           relativeFolder = file.folderPath.substring(rootPath.length);
                       } else {
                           relativeFolder = file.folderPath;
                       }
                       relativeFolder = relativeFolder.replace(/^\/+|\/+$/g, '');
                       const zipPath = relativeFolder ? `${relativeFolder}/${file.fileName}` : file.fileName;

                       zip.file(zipPath, decryptedBuffer);

                   } catch (fileError) {
                       throw fileError;
                   } finally {
                       completedCount++;
                       context.status = `Procesando elementos en paralelo (${completedCount}/${filesToProcess.length})...`;
                   }
               }
           };

           const workers = Array(Math.min(CONCURRENCY_LIMIT, filesToProcess.length))
               .fill(null)
               .map(() => workerTask());

           await Promise.all(workers);

           context.status = "Empaquetando estructura en archivo ZIP...";
           const zipBlob = await zip.generateAsync({ type: "blob" });

           const url = window.URL.createObjectURL(zipBlob);
           const a = document.createElement('a');
           a.href = url;
           a.download = `${folderName}.zip`;
           a.click();
           window.URL.revokeObjectURL(url);

           context.status = "";
           context.showInfo(`Carpeta "${folderName}" descargada con éxito.`);
       } catch (err) {
           context.status = "";
           context.showError(err.message);
       }
   },

    async deleteFile(file, context) {
        const isTrashed = !!file.deletedAt;
        const isShared = context.currentCategory === 'shared';
        let proceed = false;

        if (isTrashed) {
            proceed = await context.askConfirmation(`¿Eliminar "${file.fileName}" permanentemente del disco duro?`, true);
        } else if (isShared) {
            proceed = await context.askConfirmation(`¿Quitar tu acceso a "${file.fileName}"?`, true);
        } else {
            proceed = await context.askConfirmation(`¿Mover "${file.fileName}" a la papelera?`, true);
        }

        if (!proceed) return;

        try {
            const res = await API.deleteFile(file.id);
            if (res.ok) {
                context.showInfo(isShared ? "Acceso revocado" : (isTrashed ? "Eliminado permanentemente" : "Elemento enviado a la papelera"));
                await context.refreshAppData();
            } else {
                const errorMsg = await API.extractErrorMessage(res);
                throw new Error(errorMsg);
            }
        } catch (error) {
            context.showError(error.message);
        }
    },

    async restoreFile(file, context) {
        try {
            const res = await API.restoreFile(file.id);
            if (res.ok) {
                context.showInfo(`"${file.fileName}" se ha restaurado correctamente.`);
                await context.refreshAppData();
            } else {
                const errorMsg = await API.extractErrorMessage(res);
                throw new Error(errorMsg);
            }
        } catch (e) {
            context.showError(e.message);
        }
    },

    getDisplayFiles(allUserFiles, currentFolder, currentCategory, sortKey = 'fileName', sortOrder = 'asc') {
        let filtered = [];

        if (currentCategory === 'trash') {
            const deletedItems = allUserFiles.filter(f => f.deletedAt !== null);
            if (currentFolder === '/') {
                filtered = deletedItems.filter(item => {
                    const parentIsAlsoDeleted = deletedItems.some(p => p.id === item.parentId);
                    return !parentIsAlsoDeleted;
                });
            } else {
                filtered = deletedItems;
            }
        } else if (currentCategory === 'shared') {
            filtered = allUserFiles;
        } else {
            filtered = allUserFiles.filter(f => f.deletedAt === null);
        }

        const folders = filtered.filter(f => f.fileType === 'application/x-directory');
        const files = filtered.filter(f => f.fileType !== 'application/x-directory');

        const compareElements = (a, b, key, order) => {
            let valA = a[key];
            let valB = b[key];

            if (typeof valA === 'string') {
                return order === 'asc'
                    ? valA.localeCompare(valB, 'es', { sensitivity: 'base' })
                    : valB.localeCompare(valA, 'es', { sensitivity: 'base' });
            }
            if (key === 'updatedAt') {
                valA = new Date(valA || 0);
                valB = new Date(valB || 0);
            }
            if (valA < valB) return order === 'asc' ? -1 : 1;
            if (valA > valB) return order === 'asc' ? 1 : -1;
            return 0;
        };

        if (sortKey === 'fileSize') {
            folders.sort((a, b) => a.fileName.localeCompare(b.fileName, 'es', { sensitivity: 'base' }));
        } else {
            folders.sort((a, b) => compareElements(a, b, sortKey, sortOrder));
        }

        files.sort((a, b) => compareElements(a, b, sortKey, sortOrder));

        return [...folders, ...files];
    },

    getPathSegments(currentFolder, currentCategory, trashRootPath) {
        if (!currentFolder || currentFolder === '/') return [];

        const segments = [];
        const parts = currentFolder.split('/').filter(p => p !== '');
        let pathAccumulated = '';

        parts.forEach((name) => {
            pathAccumulated += '/' + name;
            segments.push({
                name: name,
                path: pathAccumulated
            });
        });

        return segments;
    },

    normalizePath(path) {
        if (!path || path === '/') return '/';
        let p = path.replace(/\/+/g, '/');
        if (p.endsWith('/') && p.length > 1) p = p.slice(0, -1);
        if (!p.startsWith('/')) p = '/' + p;
        return p;
    },

    getFileIconSvg(mime, fileName) {
        const m = (mime || '').toLowerCase();
        const ext = (fileName || '').toLowerCase().split('.').pop();

        const iconTemplates = {
            pdf: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="3" fill="none" /><text x="12" y="12.5" font-family="system-ui, -apple-system, sans-serif" font-size="6.5" font-weight="900" fill="#ffffff" stroke="none" text-anchor="middle" dominant-baseline="central">PDF</text></svg>`,
            compressed: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><path d="M12 2v4M12 8v2M12 12v2"/></svg>`,
            spreadsheet: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><rect width="8" height="8" x="8" y="10" rx="1"/></svg>`,
            text: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="14" y2="17"/></svg>`,
            presentation: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polygon points="12 10 16 14 12 18 8 14"/></svg>`,
            image: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>`,
            audio: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>`,
            video: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m12.296 3.464 3.02 3.956"/><path d="M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z"/><path d="M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="m6.18 5.276 3.1 3.899"/></svg>`,
            generic: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/></svg>`
        };

        if (m === 'application/pdf' || ext === 'pdf') return iconTemplates.pdf;
        if (m.includes('zip') || m.includes('rar') || m.includes('7z') || m.includes('tar') || ['zip','rar','7z','tar','gz'].includes(ext)) return iconTemplates.compressed;
        if (m.includes('excel') || m.includes('spreadsheetml') || m.includes('csv') || ['xls','xlsx','csv'].includes(ext)) return iconTemplates.spreadsheet;
        if (m.startsWith('text/') || m.includes('json') || m.includes('javascript') || ['txt','md','json','js','html','css','xml'].includes(ext)) return iconTemplates.text;
        if (m.includes('powerpoint') || m.includes('presentationml') || ['ppt','pptx'].includes(ext)) return iconTemplates.presentation;
        if (m.startsWith('image/')) return iconTemplates.image;
        if (m.startsWith('audio/')) return iconTemplates.audio;
        if (m.startsWith('video/')) return iconTemplates.video;

        return iconTemplates.generic;
    },

    formatModDate(dateStr) {
        if (!dateStr) return '-';
        const date = new Date(dateStr);
        let datePart = date.toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' }).replace('.', '').replace(',', '');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${datePart} ${hours}:${minutes}`;
    },
};

const AppFileMethods = {
    async handleCreateFolder(name, parentId) {
        try {
            const sessionKey = sessionStorage.getItem('fileKey');
            const res = await API.createFolder(name, parentId, sessionKey);

            if (res.ok) {
                this.showInfo(`Carpeta "${name}" creada.`);
                await this.refreshAppData();
            } else {
                const errorMsg = await API.extractErrorMessage(res);
                this.showError(errorMsg);
            }
        } catch (e) {
            this.showError("Error inesperado al instanciar el directorio.");
        }
    },

    async handlePreview(f) {
        if (this.clickTimer) clearTimeout(this.clickTimer);
        if (this.selectedIds.length <= 1) {
            this.closePreview();
            this.status = "Descifrando recurso...";
            try {
                const sessionKey = sessionStorage.getItem('fileKey');
                const data = await PreviewService.getPreviewData(f, sessionKey);
                this.preview = { active: true, id: f.id, name: f.fileName, mime: f.fileType, ...data };
                this.status = "Vista previa cargada.";
            } catch (e) {
                this.status = "";
                this.showError(e.message || "No se ha podido descifrar o renderizar el archivo.");
            }
        }
    },

    closePreview() {
        if (this.preview.url) URL.revokeObjectURL(this.preview.url);
        this.preview.active = false;
        this.preview.url = null;
        this.preview.content = '';
    },

    async handleDownload(fileOrId, name = null) {
        if (typeof fileOrId === 'object' && fileOrId !== null) {
            if (fileOrId.fileType === 'application/x-directory') {
                await FileService.downloadFolder(fileOrId.id, fileOrId.fileName, this);
            } else {
                const sessionKey = sessionStorage.getItem('fileKey');
                await FileService.downloadFile(fileOrId.id, fileOrId.fileName, sessionKey, this);
            }
        } else {
            const sessionKey = sessionStorage.getItem('fileKey');
            await FileService.downloadFile(fileOrId, name, sessionKey, this);
        }
    },

    async handleRestore(f) {
        await FileService.restoreFile(f, this);
    },

    async handleDelete(f) {
        await FileService.deleteFile(f, this);
    },

    async handleToggleStar(f) {
        try {
            const res = await API.toggleStar(f.id);
            if (!res.ok) {
                const errorMsg = await API.extractErrorMessage(res);
                throw new Error(errorMsg);
            }
            f.starred = !f.starred;
            if (this.currentCategory === 'starred' && !f.starred) {
                await this.refreshAppData();
            }
        } catch (e) {
            this.showError(e.message);
        }
    },

    async toggleStarSelected() {
        const count = this.selectedIds.length;
        if (count === 0) return;

        this.status = "Actualizando destacados...";
        try {
            await Promise.all(this.selectedIds.map(async id => {
                const res = await API.toggleStar(id);
                if (!res.ok) {
                    const errorMsg = await API.extractErrorMessage(res);
                    throw new Error(errorMsg);
                }
            }));

            this.showInfo(`Metadatos actualizados para ${count} elementos.`);
            this.clearSelection();
            await this.refreshAppData();
        } catch (e) {
            this.showError(e.message);
        } finally {
            this.status = "";
        }
    },

    async handleSearch() {
        if (this.searchTimeout) clearTimeout(this.searchTimeout);

        if (!this.searchQuery.trim()) {
            this.isSearching = false;
            this.refreshAppData();
            return;
        }

        this.searchTimeout = setTimeout(async () => {
            this.isSearching = true;
            this.status = "Buscando en la base de datos...";
            try {
                const res = await API.searchFiles(this.searchQuery, 0);
                this.allUserFiles = res.content;
                this.hasMore = !res.last;
                this.status = "";
            } catch (e) {
                this.isSearching = false;
                this.status = "";
                this.showError("No se ha podido procesar la búsqueda en el servidor.");
            }
        }, 400);
    },

    async deleteSelected() {
        const count = this.selectedIds.length;
        if (count === 0) return;

        const isTrash = this.currentCategory === 'trash';
        const msg = isTrash ? `¿Eliminar permanentemente ${count} elementos del almacenamiento físico?` : `¿Mover ${count} elementos a la papelera?`;

        if (await this.askConfirmation(msg, true)) {
            this.status = "Eliminando elementos...";
            try {
                await Promise.all(this.selectedIds.map(async id => {
                    const res = await API.deleteFile(id);
                    if (!res.ok) {
                        const errorMsg = await API.extractErrorMessage(res);
                        throw new Error(errorMsg);
                    }
                }));

                this.showInfo(`${count} elementos purgados.`);
                this.selectedIds = [];
                await this.refreshAppData();
            } catch (e) {
                this.showError(e.message);
                await this.refreshAppData();
            } finally {
                this.status = "";
            }
        }
    },

    openRenameModal(f) {
        this.confirmModal = {
            active: true,
            isDuplicateMode: false,
            isInput: true,
            title: 'Cambiar nombre',
            message: `Introduce el nuevo nombre para "${f.fileName}":`,
            buttonText: 'Renombrar',
            inputValue: f.fileName,
            onConfirm: async () => {
                const newName = this.confirmModal.inputValue.trim();

                if (!newName || newName === f.fileName) {
                    this.confirmModal.active = false;
                    this.confirmModal.isInput = false;
                    return;
                }

                try {
                    const res = await API.renameFile(f.id, newName);
                    if (res.ok) {
                        this.showInfo("Nombre actualizado con éxito.");
                        await this.refreshAppData();
                    } else {
                        const errorMsg = await API.extractErrorMessage(res);
                        this.showError(errorMsg);
                    }
                } catch (e) {
                    this.showError("Fallo crítico de comunicación.");
                } finally {
                    this.confirmModal.active = false;
                    this.confirmModal.isInput = false;
                }
            },
            onCancel: () => {
                this.confirmModal.active = false;
                this.confirmModal.isInput = false;
            }
        };
    },

    async downloadSelected() {
        const count = this.selectedIds.length;
        if (count === 0) return;

        const sessionKey = sessionStorage.getItem('fileKey');
        const selectedItems = this.selectedIds.map(id => this.allUserFiles.find(f => f.id === id)).filter(Boolean);
        const hasFolder = selectedItems.some(item => item.fileType === 'application/x-directory');
        const shouldZip = hasFolder || selectedItems.length > 1;

        if (!shouldZip) {
            const singleFile = selectedItems[0];
            await FileService.downloadFile(singleFile.id, singleFile.fileName, sessionKey, this);
            this.clearSelection();
            return;
        }

        this.status = "Preparando descarga unificada en ZIP...";
        try {
            const zip = new JSZip();
            const tasks = [];

            for (const item of selectedItems) {
                if (item.fileType === 'application/x-directory') {
                    const res = await fetch(`/api/files/folder-content-recursive/${item.id}`, { headers: API.getAuthHeader() });
                    if (!res.ok) {
                        const errorMsg = await API.extractErrorMessage(res);
                        throw new Error(errorMsg);
                    }
                    const children = await res.json();
                    const rootPath = FileService.normalizePath(item.folderPath + '/' + item.fileName);

                    children.forEach(child => {
                        if (child.fileType !== 'application/x-directory') {
                            let relativeFolder = child.folderPath.substring(rootPath.length).replace(/^\/+|\/+$/g, '');
                            const zipPath = item.fileName + (relativeFolder ? '/' + relativeFolder : '') + '/' + child.fileName;
                            tasks.push({ id: child.id, zipPath });
                        }
                    });
                } else {
                    tasks.push({ id: item.id, zipPath: item.fileName });
                }
            }

            if (tasks.length === 0) {
                this.showError("La selección está completamente vacía.");
                this.status = "";
                return;
            }

            const queue = [...tasks];
            let completedCount = 0;
            const CONCURRENCY_LIMIT = 6;

            const workerTask = async () => {
                while (queue.length > 0) {
                    const task = queue.shift();
                    if (!task) continue;

                    try {
                        const fileRes = await API.download(task.id);
                        if (!fileRes.ok) {
                            const errorMsg = await API.extractErrorMessage(fileRes);
                            throw new Error(errorMsg);
                        }
                        const encryptedBlob = await fileRes.blob();

                        const keyRes = await fetch(`/api/files/${task.id}/key`, { headers: API.getAuthHeader() });
                        if (!keyRes.ok) {
                            const errorMsg = await API.extractErrorMessage(keyRes);
                            throw new Error(errorMsg);
                        }
                        const { encryptedFileKey } = await keyRes.json();

                        const aesKeyObj = await CryptoService.unwrapKey(encryptedFileKey);
                        const decryptedBuffer = await CryptoService.decryptFile(encryptedBlob, aesKeyObj);

                        zip.file(task.zipPath, decryptedBuffer);
                    } catch (err) {
                        throw err;
                    } finally {
                        completedCount++;
                        this.status = `Descifrando y empaquetando lote (${completedCount}/${tasks.length})...`;
                    }
                }
            };

            const workers = Array(Math.min(CONCURRENCY_LIMIT, tasks.length)).fill(null).map(() => workerTask());
            await Promise.all(workers);

            this.status = "Generando archivo comprimido final...";
            const zipBlob = await zip.generateAsync({ type: "blob" });

            const url = window.URL.createObjectURL(zipBlob);
            const a = document.createElement('a');
            a.href = url;
            a.download = (selectedItems.length === 1) ? `${selectedItems[0].fileName}.zip` : 'cloud_crypt_export.zip';
            a.click();
            window.URL.revokeObjectURL(url);

            this.showInfo(`¡Descarga masiva de ${tasks.length} elementos completada!`);
        } catch (e) {
            this.showError(e.message);
        } finally {
            this.status = "";
            this.clearSelection();
        }
    },

    formatModDate(dateStr) {
        return FileService.formatModDate(dateStr);
    },

    changeSort(key) {
        if (this.sortKey === key) {
            this.sortOrder = this.sortOrder === 'asc' ? 'desc' : 'asc';
        } else {
            this.sortKey = key;
            this.sortOrder = 'asc';
        }
    }
};