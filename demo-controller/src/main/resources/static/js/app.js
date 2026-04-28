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
            if (this.currentFolder === '/') return [];
            let path = '';
            return this.currentFolder.split('/').filter(p => p !== '').map(p => {
                path += '/' + p;
                return {name: p, path: path};
            });
        },
        displayFiles() {
            const isDeleted = f => !!f.deletedAt;
            const viewingTrash = this.currentCategory === 'trash';

            // 1. Filtro maestro: Borrados vs Activos
            let filtered = this.allUserFiles.filter(f => isDeleted(f) === viewingTrash);

            // --- FUNCIÓN DE NORMALIZACIÓN (Elimina errores por barras extra) ---
            const normalize = (path) => {
                if (!path) return '/';
                let p = path.replace(/\/+/g, '/'); // Une dobles barras // -> /
                if (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1); // Quita barra final
                return p;
            };

            const currentNormalized = normalize(this.currentFolder);

            if (viewingTrash) {
                // --- LÓGICA DE PAPELERA ---
                if (currentNormalized === '/') {
                    // Mostrar solo "Raíces de borrado"
                    return filtered.filter(f => {
                        const fPath = normalize(f.folderPath);
                        if (fPath === '/') return true;

                        const parts = fPath.split('/').filter(p => p);
                        const parentName = parts[parts.length - 1];
                        const grandparentPath = normalize('/' + parts.slice(0, -1).join('/'));

                        const isParentDeleted = this.allUserFiles.some(p =>
                            p.fileName === parentName &&
                            normalize(p.folderPath) === grandparentPath &&
                            isDeleted(p)
                        );
                        return !isParentDeleted;
                    });
                } else {
                    // Dentro de una carpeta borrada: Comparación estricta normalizada
                    return filtered.filter(f => {
                        const fPathNormalized = normalize(f.folderPath);
                        // Solo logueamos si el nombre del archivo parece estar cerca de lo que buscamos
                        if (f.folderPath.includes("Carp")) {
                            console.log(`Comparando archivo '${f.fileName}':`);
                            console.log(`   Path archivo: '${fPathNormalized}'`);
                            console.log(`   ¿Coincide?:`, fPathNormalized === currentNormalized);
                        }
                        return fPathNormalized === currentNormalized;
                    });
                }
            } else {
                // --- LÓGICA VISTA NORMAL ---
                if (this.currentCategory === 'all') {
                    return filtered.filter(f => normalize(f.folderPath) === currentNormalized);
                } else {
                    // Filtros por categorías
                    const cat = this.currentCategory;
                    return filtered.filter(f => {
                        if (f.fileType === 'application/x-directory') return false;
                        const mime = (f.fileType || '').toLowerCase();
                        if (cat === 'image') return mime.startsWith('image/');
                        if (cat === 'audio') return mime.startsWith('audio/');
                        if (cat === 'video') return mime.startsWith('video/');
                        if (cat === 'document') return mime.includes('pdf') || mime.includes('text') || mime.includes('officedocument');
                        return false;
                    });
                }
            }
        }
    },
    methods: {
        // --- Core Data ---
        async refreshAppData() {
            this.currentPage = 0;
            this.hasMore = true;
            this.status = "Sincronizando...";
            try {
                // 1. Pedimos primero las estadísticas (rápido)
                this.stats = await API.getStats(this.username);

                // 2. Pedimos la lista GLOBAL (fundamental para la papelera)
                // Forzamos un tamaño grande para asegurar que traemos TODO lo borrado
                const allRes = await API.getFiles(this.username, null, true, 0, 2000);
                this.allUserFiles = allRes.content;

                // 3. Pedimos lo de la carpeta actual
                const folderRes = await API.getFiles(this.username, this.currentFolder, false, 0);
                this.filesInCurrentFolder = folderRes.content;

                if (folderRes.last) this.hasMore = false;
                this.status = "";
                console.log("Sincronización completa. Archivos en memoria:", this.allUserFiles.length);
            } catch (e) {
                console.error("Error fatal en sincronización:", e);
                this.showError("Error al sincronizar con el servidor");
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
                    this.showInfo(`¡${isFolder ? 'Carpeta' : 'Archivos'} subidos con éxito!`);
                    this.$refs[isFolder ? 'folderInput' : 'fileInput'].value = '';
                }
            } catch (e) {
                console.error("Fallo en subida:", e);
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
        enterFolder(f) {
            // 1. Calculamos la ruta base: si es raíz usamos vacío, si no su path
            const base = f.folderPath === '/' ? '' : f.folderPath;

            // 2. Construimos la nueva ruta completa
            this.currentFolder = base + '/' + f.fileName;

            // 3. Limpiamos posibles dobles barras (//) por seguridad
            this.currentFolder = this.currentFolder.replace(/\/+/g, '/');

            console.log("Navegando a:", this.currentFolder);
            this.refreshAppData();
        },
        isTrashRoot(f) {
            if (this.currentFolder !== '/') return false; // Si estoy dentro de una carpeta en papelera, no restauro archivos sueltos
            return true; // Solo permito restaurar lo que veo en la pantalla principal de la papelera
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