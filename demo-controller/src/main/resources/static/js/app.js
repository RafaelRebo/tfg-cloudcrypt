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
            this.closePreview();
            this.status = "Descifrando...";
            try {
                const data = await PreviewService.getPreviewData(f, this.password);
                this.preview = { active: true, id: f.id, name: f.fileName, mime: f.fileType, ...data };
                this.status = "Vista previa cargada.";
            } catch (e) { this.showError("No se pudo descifrar el archivo."); }
        },
        closePreview() {
            if (this.preview.url) URL.revokeObjectURL(this.preview.url);
            this.preview.active = false; this.preview.url = null; this.preview.content = '';
        },
        async handleDownload(id, name) { await FileService.downloadFile(id, name, this.password, this); },
        async handleRestore(f) { await FileService.restoreFile(f, this); },
        async handleDelete(f) { await FileService.deleteFile(f, this); },

        // --- Navigation ---
        setCategory(cat) {
            this.currentCategory = cat;
            // Siempre que cambiamos de sección, volvemos a la raíz lógica
            this.currentFolder = '/';
            this.refreshAppData();
        },
        // En app.js -> methods
        enterFolder(f) {
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