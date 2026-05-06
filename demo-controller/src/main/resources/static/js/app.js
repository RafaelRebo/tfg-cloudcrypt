const { createApp } = Vue;

const appInstance = createApp({
    data() {
        return {
            isLoggedIn: false,
            username: '', password: '',
            currentFolder: '/',
            allUserFiles: [], filesInCurrentFolder: [],
            status: '', uploadProgress: 0,
            stats: { totalSize: 0, fileCount: 0, maxQuota: 104857600 },
            preview: { active: false, url: null, name: '', type: '', content: '' },
            currentPage: 0, isLoadingMore: false, hasMore: true,
            notifications: [], loginError: false, currentCategory: 'all',
            confirmModal: { active: false, title: '', isInput: false, inputValue: '', message: '', onConfirm: null, onCancel: null },
            searchQuery: '',
            searchTimeout: null,
            isSearching: false,
            isDragging: false,
            selectedIds: [],
            clickTimer: null,
            trashRootPath: null,
            currentFolderId: null,
            uploadController: null,
        }
    },
    mounted() {
        const session = AuthService.getSavedSession();
        if (session) {
            this.username = session.username;
            this.isLoggedIn = true;
            this.refreshAppData();
        }
        window.addEventListener('scroll', this.handleInfiniteScroll);
    },
    computed: {
        quotaPercentage() {
            return Math.min((this.stats.totalSize / this.stats.maxQuota) * 100, 100).toFixed(1);
        },
        pathSegments() {
            return FileService.getPathSegments(
                this.currentFolder,
                this.currentCategory,
                this.trashRootPath
            );
        },
        displayFiles() {
            return FileService.getDisplayFiles(this.allUserFiles, this.currentFolder, this.currentCategory);
        },
    },
    methods: {
        // --- Core Data ---
        // En app.js -> methods
        async refreshAppData() {
            this.currentPage = 0;
            this.hasMore = true;
            this.status = "Actualizando...";
            this.selectedIds = []; // <--- IMPORTANTE: Limpiar selección al refrescar

            try {
                const res = await API.getFiles(
                    this.username,
                    this.currentFolderId,
                    this.currentCategory,
                    0
                );

                // Forzamos la limpieza del array antes de asignar los nuevos datos
                this.allUserFiles = [];
                this.allUserFiles = res.content;

                this.hasMore = !res.last;
                this.stats = await API.getStats(this.username);
                this.status = "";
            } catch (e) {
                this.showError("Error al actualizar la vista");
            }
        },
        handleInfiniteScroll() {
            if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight - 100) {
                this.loadNextPage();
            }
        },
        async loadNextPage() {
            if (this.isLoadingMore || !this.hasMore) return;
            this.isLoadingMore = true;
            this.currentPage++;
            try {
                const res = await API.getFiles(this.username, this.currentFolder, this.currentCategory, this.currentPage);
                this.allUserFiles.push(...res.content);
                if (res.last) this.hasMore = false;
            } catch (e) { console.error(e); } finally { this.isLoadingMore = false; }
        },

        // --- Auth ---
        async handleLogin() {
            try {
                const secureKey = await AuthService.deriveMasterKey(this.username, this.password);
                const success = await AuthService.login(this.username, secureKey);

                if (success) {
                    sessionStorage.setItem('fileKey', secureKey);
                    this.password = '';
                    this.loginError = false;
                    this.isLoggedIn = true;
                    this.refreshAppData();
                } else {
                    this.loginError = true;
                    this.showError("Usuario o contraseña incorrectos");
                }
            } catch (e) {
                this.showError("Error en el proceso de autenticación");
            }
        },
        // En app.js -> methods
        async handleRegister() {
            if (!this.username || !this.password) {
                this.showError("Usuario y contraseña requeridos");
                return;
            }

            try {
                // 1. Derivamos la clave igual que en el Login
                const secureKey = await AuthService.deriveMasterKey(this.username, this.password);

                // 2. Enviamos la clave derivada al servidor
                const res = await API.register(this.username, secureKey);

                if (res.ok) {
                    this.showInfo("Registro completado. Ya puedes iniciar sesión.");
                } else {
                    const errorText = await res.text();
                    this.showError("Error en registro: " + errorText);
                }
            } catch (e) {
                this.showError("Error de conexión");
            }
        },
        logout() {
            AuthService.logout();
            this.isLoggedIn = false;
            Object.assign(this.$data, this.$options.data());
        },
        onDragOver() {
            this.isDragging = true;
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
            let containsFolder = false; // Flag para detectar si hay carpetas

            for (let i = 0; i < items.length; i++) {
                const item = items[i].webkitGetAsEntry();
                if (item) {
                    if (item.isDirectory) containsFolder = true;
                    scanPromises.push(this.traverseFileTree(item, "", filesToUpload));
                }
            }

            await Promise.all(scanPromises);

            if (filesToUpload.length > 0) {
                // Si solo soltamos archivos, pasamos false. Si hay carpetas, pasamos true.
                await this._handleUploadProcess(filesToUpload, containsFolder);
            }
        },

        async traverseFileTree(item, path, fileList) {
            path = path || "";

            if (item.isFile) {
                // Es un archivo: obtenemos el objeto File real
                const file = await new Promise((resolve, reject) => {
                    item.file(resolve, reject);
                });

                // Simulamos la propiedad webkitRelativePath para que UploadService
                // mantenga la estructura de carpetas
                Object.defineProperty(file, 'webkitRelativePath', {
                    value: path + file.name,
                    writable: false
                });

                fileList.push(file);
            } else if (item.isDirectory) {
                // Es una carpeta: leemos su contenido de forma recursiva
                const dirReader = item.createReader();

                // Función interna para leer todas las entradas (por si hay más de 100)
                const readEntries = async () => {
                    const entries = await new Promise((resolve, reject) => {
                        dirReader.readEntries(resolve, reject);
                    });

                    if (entries.length > 0) {
                        for (const entry of entries) {
                            await this.traverseFileTree(entry, path + item.name + "/", fileList);
                        }
                        // Continuar leyendo por si el navegador fragmentó la lista de archivos
                        await readEntries();
                    }
                };

                await readEntries();
            }
        },

        // --- Notifications ---
        showInfo(msg) { NotificationService.create(msg, this.notifications, 'info'); },
        showError(msg) { NotificationService.create(msg, this.notifications, 'error'); },
        removeNotification(id) { NotificationService.animateOut(id, this.notifications); },
        askConfirmation(msg) {
            return new Promise((resolve) => {
                this.confirmModal = {
                    active: true,
                    message: msg,
                    onConfirm: () => { this.confirmModal.active = false; resolve(true); },
                    onCancel: () => { this.confirmModal.active = false; resolve(false); }
                };
            });
        },

        // --- File Operations ---
        async _handleUploadProcess(files, isFolder) {
            try {
                const totalBatchSize = files.reduce((acc, f) => acc + f.size, 0);
                const availableQuota = this.stats.maxQuota - this.stats.totalSize;

                if (totalBatchSize > availableQuota) {
                    // Error inmediato: No se activa la barra de progreso ni se llama al servicio
                    this.showError(`Espacio insuficiente. Faltan ${this.formatSize(totalBatchSize - availableQuota)}`);
                    return; // Cortamos aquí
                }

                this.uploadController = new AbortController();

                const sessionKey = sessionStorage.getItem('fileKey');
                const originalPassword = this.password;
                this.password = sessionKey;

                const success = await UploadService.processUpload(files, this, isFolder, this.uploadController.signal);

                this.password = originalPassword; // Restauramos (aunque sea '')

                if (success) {
                    this.showInfo(`¡Subida completada!`);
                    if (this.$refs.folderInput) this.$refs.folderInput.value = '';
                    if (this.$refs.fileInput) this.$refs.fileInput.value = '';
                }
            } catch (e) {
                if (e.name === 'AbortError' || e.message === 'Aborted') {
                    this.showInfo("Subida cancelada");
                } else {
                    this.showError(e.message || "Error en la subida");
                }
            } finally {
                await this.refreshAppData();
                this.uploadController = null;
                this.uploadProgress = 0; // Al ponerlo a 0, el NotificationService cerrará el toast
            }
        },
        async onUpload() {
            await this._handleUploadProcess(Array.from(this.$refs.fileInput.files), false);
        },
        cancelUpload() {
            if (this.uploadController) {
                this.uploadController.abort(); // Corta la subida actual
                this.uploadController = null;
                this.uploadProgress = 0;
                this.status = "Subida cancelada por el usuario";

                // Opcional: Refrescar para limpiar posibles carpetas vacías creadas
                setTimeout(() => this.refreshAppData(), 500);
            }
        },
        async uploadFolder() {
            await this._handleUploadProcess(Array.from(this.$refs.folderInput.files), true);
        },
        // En app.js -> methods
        async handleCreateFolder(name, parentId) {
            try {
                // Obtenemos la llave derivada de la sesión, NO de this.password
                const sessionKey = sessionStorage.getItem('fileKey');

                const res = await API.createFolder(
                    this.username,
                    sessionKey, // Usamos la llave derivada
                    parentId,
                    name
                );

                if (res.ok) {
                    this.showInfo(`Carpeta "${name}" creada.`);
                    await this.refreshAppData();
                } else {
                    const errorText = await res.text();
                    this.showError(errorText);
                }
            } catch (e) {
                this.showError("Error al crear carpeta");
            }
        },
        async handlePreview(f) {
            if (this.clickTimer) clearTimeout(this.clickTimer);
            if (this.selectedIds.length <= 1) {
                this.closePreview();
                this.status = "Descifrando...";
                try {
                    const sessionKey = sessionStorage.getItem('fileKey');
                    const data = await PreviewService.getPreviewData(f, sessionKey);
                    this.preview = { active: true, id: f.id, name: f.fileName, mime: f.fileType, ...data };
                    this.status = "Vista previa cargada.";
                } catch (e) { this.showError("No se pudo descifrar el archivo."); }
            }
        },
        closePreview() {
            if (this.preview.url) URL.revokeObjectURL(this.preview.url);
            this.preview.active = false; this.preview.url = null; this.preview.content = '';
        },
        async handleDownload(id, name) {
            const sessionKey = sessionStorage.getItem('fileKey');
            await FileService.downloadFile(id, name, sessionKey, this);
        },
        async handleRestore(f) { await FileService.restoreFile(f, this); },
        async handleDelete(f) { await FileService.deleteFile(f, this); },
        async handleSearch() {
            if (this.searchTimeout) clearTimeout(this.searchTimeout);

            if (!this.searchQuery.trim()) {
                this.isSearching = false;
                this.refreshAppData();
                return;
            }

            this.searchTimeout = setTimeout(async () => {
                this.isSearching = true;
                this.status = "Buscando...";
                try {
                    const res = await API.searchFiles(this.searchQuery, 0);
                    this.allUserFiles = res.content;
                    this.hasMore = !res.last;
                    this.status = "";
                } catch (e) {
                    this.showError("Error en la búsqueda");
                    this.isSearching = false;
                }
            }, 400);
        },
        highlight(text) {
            if (!this.searchQuery || !this.isSearching) return text;

            // Creamos una expresión regular que ignore mayúsculas/minúsculas
            const regex = new RegExp(`(${this.searchQuery})`, 'gi');

            // Sustituimos el texto coincidente por el mismo texto envuelto en un span con clase CSS
            return text.replace(regex, '<span class="highlight">$1</span>');
        },
        handleFileClick(f, event) {
            const isControlPressed = event.ctrlKey || event.metaKey;

            if (isControlPressed) {
                // --- MODO SELECCIÓN (Solo con Control) ---
                event.preventDefault();
                const index = this.selectedIds.indexOf(f.id);
                if (index > -1) {
                    this.selectedIds.splice(index, 1); // Deseleccionar
                } else {
                    this.selectedIds.push(f.id); // Seleccionar
                }
                // Aquí NO hay apertura, solo gestión de la lista
            } else {
                // --- MODO ABRIR (Clic normal) ---
                // 1. Limpiamos la selección para que no se quede nada marcado
                this.selectedIds = [];

                // 2. Ejecutamos la acción de abrir directamente
                if (f.fileType === 'application/x-directory') {
                    this.enterFolder(f);
                } else {
                    // Si no es papelera, abrimos la preview
                    if (!f.deletedAt) {
                        this.handlePreview(f);
                    }
                }
            }
        },


        isSelected(id) {
            return this.selectedIds.includes(id);
        },
        clearSelection() {
            this.selectedIds = [];
        },
        async askUserForDuplicateAction(name, isFolder) {
            return new Promise((resolve) => {
                this.confirmModal = {
                    active: true,
                    isDuplicateMode: true,
                    isInput: false,
                    applyToAll: false,
                    title: isFolder ? '📁 Carpeta duplicada' : '📄 Archivo duplicado',
                    message: `"${name}" ya existe. ¿Qué deseas hacer?`,
                    onOverwrite: () => {
                        const res = { action: 'overwrite', applyToAll: this.confirmModal.applyToAll };
                        this.closeModal(resolve, res);
                    },
                    onCopy: () => {
                        const res = { action: 'copy', applyToAll: this.confirmModal.applyToAll };
                        this.closeModal(resolve, res);
                    },
                    onSkip: () => {
                        const res = { action: 'skip', applyToAll: this.confirmModal.applyToAll };
                        this.closeModal(resolve, res);
                    },
                    onCancel: () => {
                        this.closeModal(resolve, { action: 'skip', applyToAll: false });
                    }
                };
            });
        },

        // En app.js -> methods
        closeModal(resolve, result) {
            this.confirmModal.active = false;
            // IMPORTANTE: Resetear todos los flags
            setTimeout(() => {
                this.confirmModal.isDuplicateMode = false;
                this.confirmModal.isInput = false;
                this.confirmModal.applyToAll = false;
                this.confirmModal.title = '';
                this.confirmModal.message = '';
            }, 300); // Pequeño delay para que no se vea el cambio mientras cierra la animación
            resolve(result);
        },

        // En app.js -> methods
        // En app.js -> methods
        async openNewFolderModal() {
            const targetId = this.currentCategory === 'all' ? this.currentFolderId : null;
            const targetName = this.currentFolder;

            this.confirmModal = {
                active: true,
                isDuplicateMode: false,
                isInput: true,
                title: '📁 Nueva carpeta',
                message: `Crear en: ${targetName}`,
                inputValue: 'Carpeta sin título',
                onConfirm: async () => {
                    const name = this.confirmModal.inputValue.trim();
                    if (!name) return;

                    try {
                        // 1. Verificar existencia
                        const check = await API.checkExists(name, targetId, this.username);

                        if (check.exists) {
                            // Si existe, cerramos el modal de input para abrir el de confirmación
                            this.confirmModal.active = false;

                            // Pequeño delay para que Vue procese el cierre antes de abrir el siguiente
                            await new Promise(r => setTimeout(r, 100));

                            const proceed = await this.askConfirmation(
                                `Ya existe una carpeta llamada "${name}". ¿Deseas crear otra con el mismo nombre?`
                            );

                            if (!proceed) return;
                        }

                        // 2. Ejecutar creación
                        await this.handleCreateFolder(name, targetId);

                        // 3. Limpiar y CERRAR de forma segura
                        this.confirmModal.active = false;
                        this.confirmModal.isInput = false;

                    } catch (e) {
                        console.error(e);
                        this.showError("Error al procesar la carpeta");
                    }
                },
                onCancel: () => {
                    this.confirmModal.active = false;
                    this.confirmModal.isInput = false;
                }
            };
        },
        async deleteSelected() {
            const count = this.selectedIds.length;
            const isTrash = this.currentCategory === 'trash';
            const msg = isTrash ? `¿Eliminar permanentemente ${count} elementos?` : `¿Mover ${count} elementos a la papelera?`;

            if (await this.askConfirmation(msg)) {
                try {
                    // Usamos Promise.all para esperar a que todas las peticiones terminen
                    await Promise.all(this.selectedIds.map(id => API.deleteFile(id)));

                    this.showInfo(`${count} elementos procesados correctamente`);
                    this.selectedIds = [];
                    // Refrescamos DESPUÉS de que todas las promesas se hayan cumplido
                    await this.refreshAppData();
                } catch (e) {
                    this.showError("Hubo un error al eliminar algunos archivos");
                    await this.refreshAppData(); // Refrescamos igual para ver qué quedó
                }
            }
        },
        async downloadSelected() {
            this.status = "Iniciando descarga múltiple...";
            for (const id of this.selectedIds) {
                const file = this.allUserFiles.find(f => f.id === id);
                if (file && file.fileType !== 'application/x-directory') {
                    await FileService.downloadFile(id, file.fileName, this.password, this);
                    // Esperamos un poco entre descargas para no bloquear el navegador
                    await new Promise(r => setTimeout(r, 600));
                }
            }
            this.status = "";
        },
        formatCategory(cat) {
            const labels = {
                'all': 'Mis archivos',
                'image': 'Imágenes',
                'audio': 'Música',
                'video': 'Vídeos',
                'document': 'Documentos',
                'trash': 'Papelera'
            };
            return labels[cat] || cat;
        },
        // --- Navigation ---
        setCategory(cat) {
            this.currentCategory = cat;
            this.currentFolder = '/';
            this.currentFolderId = null;
            this.trashRootPath = null;
            this.searchQuery = '';
            this.isSearching = false;
            this.clearSelection();
            this.refreshAppData();
        },

        enterFolder(f) {
            this.currentFolderId = f.id;
            this.currentFolder = FileService.normalizePath(f.folderPath + '/' + f.fileName);

            // Si f.deletedAt existe, nos aseguramos de que la categoría siga siendo trash
            if (f.deletedAt) {
                this.currentCategory = 'trash';
            }

            this.refreshAppData();
        },
        isTrashRoot(f) {
            return this.currentCategory === 'trash';
        },
        goBack() { this.currentFolder = this.currentFolder.substring(0, this.currentFolder.lastIndexOf('/')) || '/'; this.refreshAppData(); },
        goToFolder(path, id = null) {
            if (path === '/') {
                this.currentFolder = '/';
                this.currentFolderId = null;
                // No cambiamos currentCategory para que si estás en trash, vuelvas a la raíz de trash
            } else {
                this.currentFolder = path;
                this.currentFolderId = id;
            }
            this.refreshAppData();
        },
        getFileIcon(mime) { return FileService.getFileIcon(mime); },
        formatSize(b) { return (b / (1024 * 1024)).toFixed(1) + ' MB'; }
    },
    watch: {
        uploadProgress(newVal) {
            NotificationService.updateUploadProgress(this);
        }
    }
}).mount('#app');