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
        quotaPercentage() { return Math.min((this.stats.totalSize / this.stats.maxQuota) * 100, 100).toFixed(1); },
        pathSegments() {
            if (this.currentFolder === '/') return [];
            let path = '';
            return this.currentFolder.split('/').filter(p => p !== '').map(p => {
                path += '/' + p;
                return { name: p, path: path };
            });
        },
        displayFiles() {
            const isDeleted = f => !!f.deletedAt;
            const viewingTrash = this.currentCategory === 'trash';

            // 1. Filtrar por estado de borrado (Sincronizado con el backend)
            let filtered = this.allUserFiles.filter(f => isDeleted(f) === viewingTrash);

            // 2. Filtro de ruta (Solo lo que está en la carpeta actual)
            if (this.currentCategory === 'all' || viewingTrash) {
                filtered = filtered.filter(f => f.folderPath === this.currentFolder);
            }
            // 3. Filtro de categorías (Solo archivos activos)
            else {
                filtered = filtered.filter(f => f.fileType !== 'application/x-directory');
                const cat = this.currentCategory;
                filtered = filtered.filter(f => {
                    const mime = (f.fileType || '').toLowerCase();
                    if (cat === 'image') return mime.startsWith('image/');
                    if (cat === 'audio') return mime.startsWith('audio/');
                    if (cat === 'video') return mime.startsWith('video/');
                    if (cat === 'document') return mime.includes('pdf') || mime.includes('text') || mime.includes('officedocument');
                    return false;
                });
            }
            return filtered;
        },
    },
    methods: {
        // --- Core Data ---
        async refreshAppData() {
            this.currentPage = 0;
            this.hasMore = true;
            try {
                const [folderRes, allRes, stats] = await Promise.all([
                    API.getFiles(this.username, this.currentFolder, false, 0),
                    API.getFiles(this.username, null, true, 0, 1000),
                    API.getStats(this.username)
                ]);
                this.filesInCurrentFolder = folderRes.content;
                this.allUserFiles = allRes.content;
                this.stats = stats;
                if (folderRes.last) this.hasMore = false;
            } catch (e) { console.error(e); }
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
                const res = await API.getFiles(this.username, this.currentFolder, false, this.currentPage);
                this.filesInCurrentFolder.push(...res.content);
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
                    // FIN EXITOSO: El servicio UploadService ya ha puesto context.uploadProgress = 100
                    // La notificación de subida desaparecerá automáticamente por el updateUploadProgress
                    // Y mostramos el Toast de éxito final
                    this.showInfo(`¡${isFolder ? 'Carpeta' : 'Archivos'} subidos con éxito!`);
                    this.$refs[isFolder ? 'folderInput' : 'fileInput'].value = '';
                }
            } catch (e) {
                // FIN CON ERROR: El servicio ha puesto progress = 0
                this.showError(`SUBIDA ABORTADA: ${e.message}`);
            } finally {
                // Limpieza
                await this.refreshAppData();
                // Ponemos el progreso a 0 para que la notificación de subida se vaya si quedaba algo
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
                console.error(e);
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
        setCategory(cat) { this.currentCategory = cat; if (cat === 'all' || cat === 'trash') {this.currentFolder = '/';} this.refreshAppData(); },
        enterFolder(n) { this.currentFolder = (this.currentFolder === '/' ? '' : this.currentFolder) + '/' + n; this.refreshAppData(); },
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