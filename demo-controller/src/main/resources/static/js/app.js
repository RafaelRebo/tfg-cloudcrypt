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
        async refreshAppData() {
            this.currentPage = 0;
            this.hasMore = true;
            this.status = "Cargando...";
            try {
                // IMPORTANTE: Enviamos currentFolderId para listar lo que hay DENTRO
                const res = await API.getFiles(
                    this.username,
                    this.currentFolderId,
                    this.currentCategory,
                    0
                );

                this.allUserFiles = res.content;
                this.hasMore = !res.last;
                this.stats = await API.getStats(this.username);
                this.status = "";
            } catch (e) {
                this.showError("Error de carga de archivos");
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
                const sessionKey = sessionStorage.getItem('fileKey');

                // IMPORTANTE: No clonamos con { ...this } porque perdemos la reactividad.
                // Pasamos 'this' directamente. El UploadService debe estar preparado
                // para recibir la instancia y la clave por separado o sobreescribirla.

                // Modificamos temporalmente la propiedad de la instancia para la subida
                const originalPassword = this.password;
                this.password = sessionKey;

                const success = await UploadService.processUpload(files, this, isFolder);

                this.password = originalPassword; // Restauramos (aunque sea '')

                if (success) {
                    this.showInfo(`¡Subida completada!`);
                    if (this.$refs.folderInput) this.$refs.folderInput.value = '';
                    if (this.$refs.fileInput) this.$refs.fileInput.value = '';
                }
            } catch (e) {
                this.showError(e.message || "Error en la subida");
            } finally {
                await this.refreshAppData();
                this.uploadProgress = 0; // Al ponerlo a 0, el NotificationService cerrará el toast
            }
        },
        async onUpload() {
            await this._handleUploadProcess(Array.from(this.$refs.fileInput.files), false);
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
        // En app.js -> methods
        handleFileClick(f, event) {
            // 1. GESTIÓN DE SELECCIÓN MÚLTIPLE (CTRL o META para Mac)
            if (event.ctrlKey || event.metaKey) {
                event.preventDefault(); // Evitar comportamientos por defecto
                const index = this.selectedIds.indexOf(f.id);

                if (index > -1) {
                    // Si ya estaba seleccionado, lo quitamos
                    this.selectedIds.splice(index, 1);
                } else {
                    // Si no estaba, lo añadimos
                    this.selectedIds.push(f.id);
                }
                return; // Detenemos aquí para que NO abra la carpeta/archivo
            }

            // 2. CLICK NORMAL (Sin Control)
            // Si hay varios elementos seleccionados y haces click en uno sin CTRL, limpiamos y seleccionamos solo ese
            if (this.selectedIds.length > 0 && !this.selectedIds.includes(f.id)) {
                this.selectedIds = [f.id];
            } else if (this.selectedIds.length === 0) {
                this.selectedIds = [f.id];
            }

            // 3. DOBLE CLICK (Simulado): Si el usuario hace click rápido, se dispara la apertura
            // Nota: Si prefieres que solo abra con doble click real, usa @dblclick en el HTML
            // y deja este método solo para la selección.

            if (this.clickTimer) {
                // Es un doble click
                clearTimeout(this.clickTimer);
                this.clickTimer = null;

                if (f.fileType === 'application/x-directory') {
                    this.enterFolder(f);
                } else {
                    // Solo abrir preview si no está borrado
                    if (!f.deletedAt) this.handlePreview(f);
                }
            } else {
                // Primer click: iniciamos temporizador para esperar el segundo
                this.clickTimer = setTimeout(() => {
                    this.clickTimer = null;
                }, 300);
            }
        },
        // En app.js -> methods
        toggleSelect(f, event) {
            // 1. Caso de selección múltiple (CTRL / CMD) - Se ejecuta al instante
            if (event.ctrlKey || event.metaKey) {
                const index = this.selectedIds.indexOf(f.id);
                if (index > -1) this.selectedIds.splice(index, 1);
                else this.selectedIds.push(f.id);
                return;
            }

            // 2. Caso de click normal: Retrasamos la selección para no pisar el Doble Click
            if (this.clickTimer) clearTimeout(this.clickTimer);

            this.clickTimer = setTimeout(() => {
                // Si después de 250ms no ha habido un doble click, seleccionamos este único
                this.selectedIds = [f.id];
                this.clickTimer = null;
            }, 250);
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
            if (await this.askConfirmation(`¿Mover ${count} elementos a la papelera?`)) {
                for (const id of this.selectedIds) {
                    await API.deleteFile(id);
                }
                this.showInfo(`${count} elementos procesados`);
                this.selectedIds = [];
                await this.refreshAppData();
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
        // --- Navigation ---
        setCategory(cat) {
            this.currentCategory = cat;
            this.currentFolder = '/';
            this.currentFolderId = null; // IMPORTANTE: Resetear a la raíz de la categoría
            this.trashRootPath = null;
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