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
        }
    },
    mounted() {
        const session = AuthService.getSavedSession();
        if (session) {
            this.username = session.username;
            this.password = session.password;
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
            return FileService.getPathSegments(this.currentFolder, this.currentCategory);
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
                const res = await API.getFiles(
                    this.username,
                    this.currentFolder,
                    this.currentCategory,
                    0
                );
                this.allUserFiles = res.content;
                this.hasMore = !res.last;

                this.stats = await API.getStats(this.username);
                this.status = "";
            } catch (e) {
                this.showError("Error de carga");
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
            const success = await AuthService.login(this.username, this.password);
            if (success) {
                this.loginError = false;
                this.isLoggedIn = true;
                this.refreshAppData();
            } else {
                this.loginError = true;
                this.showError("Usuario o contraseña incorrectos");
            }
        },
        async handleRegister() {
            if ((await API.register(this.username, this.password)).ok) alert("Registrado");
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

            // Obtenemos los items del evento
            const items = event.dataTransfer.items;
            if (!items) return;

            let filesToUpload = [];
            const scanPromises = [];

            // 1. Recolectamos todas las promesas de escaneo de archivos/carpetas
            for (let i = 0; i < items.length; i++) {
                const item = items[i].webkitGetAsEntry();
                if (item) {
                    scanPromises.push(this.traverseFileTree(item, "", filesToUpload));
                }
            }

            // 2. ESPERAMOS a que todo el árbol de archivos se haya leído en memoria
            await Promise.all(scanPromises);

            if (filesToUpload.length > 0) {
                // 3. Enviamos la lista completa al proceso de subida
                // Usamos 'true' porque el drop siempre debe ser tratado con lógica de rutas relativas
                await this._handleUploadProcess(filesToUpload, true);
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
                const success = await UploadService.processUpload(files, this, isFolder);
                if (success) {
                    this.showInfo(`¡${isFolder ? 'Carpeta' : 'Archivos'} subidos con éxito!`);
                    this.$refs[isFolder ? 'folderInput' : 'fileInput'].value = '';
                }
            } catch (e) {
                this.showError(e.message || e);
            } finally {
                await this.refreshAppData();
                this.uploadProgress = 0;
            }
        },
        async onUpload() {
            await this._handleUploadProcess(Array.from(this.$refs.fileInput.files), false);
        },

        async uploadFolder() {
            await this._handleUploadProcess(Array.from(this.$refs.folderInput.files), true);
        },
        async openNewFolderModal() {
            const targetPath = this.currentCategory === 'all' ? this.currentFolder : '/';
            this.confirmModal = {
                active: true,
                title: '📁 Nueva carpeta',
                message: `Crear en: ${this.currentCategory === 'all' ? targetPath : 'Raíz (/)'}`,
                isInput: true, // Activamos el modo input
                inputValue: 'Carpeta sin título', // Nombre por defecto
                onConfirm: () => {
                    if (this.confirmModal.inputValue.trim()) {
                        this.handleCreateFolder(this.confirmModal.inputValue.trim(), targetPath);
                        this.confirmModal.active = false;
                    } else {
                        this.showError("El nombre no puede estar vacío.");
                    }
                },
                onCancel: () => { this.confirmModal.active = false; }
            };
        },
        async handleCreateFolder(name, path) {
            try {
                const res = await API.createFolder(this.username, this.password, path, name);
                if (res.ok) {
                    this.showInfo(`Carpeta "${name}" creada correctamente.`);
                    await this.refreshAppData(); // Refrescamos para que aparezca
                } else {
                    const error = await res.text();
                    this.showError(`No se pudo crear la carpeta: ${error}`);
                }
            } catch (e) {
                this.showError("Error de conexión al crear la carpeta.");
            }
        },
        async handlePreview(f) {
            if (this.clickTimer) clearTimeout(this.clickTimer);
            if (this.selectedIds.length <= 1) {
                this.closePreview();
                this.status = "Descifrando...";
                try {
                    const data = await PreviewService.getPreviewData(f, this.password);
                    this.preview = { active: true, id: f.id, name: f.fileName, mime: f.fileType, ...data };
                    this.status = "Vista previa cargada.";
                } catch (e) { this.showError("No se pudo descifrar el archivo."); }
            }
        },
        closePreview() {
            if (this.preview.url) URL.revokeObjectURL(this.preview.url);
            this.preview.active = false; this.preview.url = null; this.preview.content = '';
        },
        async handleDownload(id, name) { await FileService.downloadFile(id, name, this.password, this); },
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
        handleFileClick(f, event) {
            // 1. SI SE PULSA CTRL O CMD: Gestionamos la selección múltiple
            if (event.ctrlKey || event.metaKey) {
                event.preventDefault(); // Evitamos comportamientos raros
                const index = this.selectedIds.indexOf(f.id);
                if (index > -1) {
                    this.selectedIds.splice(index, 1);
                } else {
                    this.selectedIds.push(f.id);
                }
                return; // Salimos del método para que NO abra la carpeta/archivo
            }

            // 2. CLICK NORMAL (Sin Control): Comportamiento de toda la vida
            // Si hay cosas seleccionadas y haces un click normal, limpiamos la selección
            if (this.selectedIds.length > 0) {
                this.clearSelection();
            }

            // Entrar en carpeta o abrir preview
            if (f.fileType === 'application/x-directory') {
                this.enterFolder(f);
            } else {
                this.handlePreview(f);
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
            this.searchQuery = '';
            this.isSearching = false;
            this.refreshAppData();
        },
        // En app.js -> methods
        // En js/app.js -> methods
        enterFolder(f) {
            this.clearSelection();
            const base = f.folderPath === '/' ? '' : f.folderPath;
            this.currentFolder = FileService.normalizePath(base + '/' + f.fileName);
            this.refreshAppData();
        },
        isTrashRoot(f) {
            return this.currentCategory === 'trash';
        },
        goBack() { this.currentFolder = this.currentFolder.substring(0, this.currentFolder.lastIndexOf('/')) || '/'; this.refreshAppData(); },
        goToFolder(p) { this.currentFolder = p; this.refreshAppData(); },
        getFileIcon(mime) { return FileService.getFileIcon(mime); },
        formatSize(b) { return (b / (1024 * 1024)).toFixed(1) + ' MB'; }
    },
    watch: {
        uploadProgress(newVal) {
            NotificationService.updateUploadProgress(this);
        }
    }
}).mount('#app');