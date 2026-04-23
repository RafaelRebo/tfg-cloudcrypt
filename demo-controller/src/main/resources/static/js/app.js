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
            currentPage: 0,
            isLoadingMore: false,
            hasMore: true
        }
    },
    mounted() {
        const saved = localStorage.getItem('userSession');
        if (saved) {
            const session = JSON.parse(saved);
            this.username = session.username;
            this.password = session.password;
            this.isLoggedIn = true;
            this.refreshAppData();
        }

        window.addEventListener('scroll', () => {
                if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight - 100) {
                    this.loadNextPage();
                }
        });
    },
    computed: {
        quotaPercentage() { return Math.min((this.stats.totalSize / this.stats.maxQuota) * 100, 100); },
        subFolders() {
            const folders = new Set();
            const current = this.currentFolder.endsWith('/') ? this.currentFolder : this.currentFolder + '/';
            this.allUserFiles.forEach(f => {
                let path = (f.folderPath || '/') + (f.folderPath?.endsWith('/') ? '' : '/');
                if (path.startsWith(current) && path !== current) {
                    const nextLevel = path.substring(current.length).split('/')[0];
                    if (nextLevel) folders.add(nextLevel);
                }
            });
            return Array.from(folders).sort();
        },
        pathSegments() {
            if (this.currentFolder === '/') return [];
            let path = '';
            return this.currentFolder.split('/').filter(p => p !== '').map(p => {
                path += '/' + p;
                return { name: p, path: path };
            });
        }
    },
    methods: {
        async refreshAppData() {
            this.currentPage = 0;
            this.hasMore = true;
            try {
                const [folderRes, allRes, stats] = await Promise.all([
                    API.getFiles(this.username, this.currentFolder, false, 0),
                    API.getFiles(this.username, null, true, 0, 1000), // Todos para el árbol lateral
                    API.getStats(this.username)
                ]);

                this.filesInCurrentFolder = folderRes.content;
                this.allUserFiles = allRes.content;
                this.stats = stats;

                // Si el total de páginas es 1 o menos, ya no hay más que cargar
                if (folderRes.last) this.hasMore = false;
            } catch (e) { console.error(e); }
        },
        async loadNextPage() {
            if (this.isLoadingMore || !this.hasMore) return;

            this.isLoadingMore = true;
            this.currentPage++;

            try {
                const res = await API.getFiles(this.username, this.currentFolder, false, this.currentPage);

                // AÑADIMOS los nuevos archivos a los que ya teníamos
                this.filesInCurrentFolder.push(...res.content);

                if (res.last) this.hasMore = false;
            } catch (e) {
                console.error("Error cargando más archivos", e);
            } finally {
                this.isLoadingMore = false;
            }
        },
        async handleLogin() {
            const res = await API.login(this.username, this.password);
            if (res.ok) {
                this.isLoggedIn = true;
                localStorage.setItem('userSession', JSON.stringify({username: this.username, password: this.password}));
                this.refreshAppData();
            } else alert("Acceso denegado");
        },
        async handleRegister() {
            if ((await API.register(this.username, this.password)).ok) alert("Registrado");
        },
        async onUpload() {
            const files = Array.from(this.$refs.fileInput.files);
            if (files.length === 0) return;

            try {
                const success = await UploadService.processUpload(files, this, false);
                if (success) {
                    this.status = "¡Archivos subidos con éxito!";
                    this.$refs.fileInput.value = '';
                }
            } catch (e) {
                this.status = "Error en la subida: " + e.message;
                this.uploadProgress = 0;
                alert(`Se ha detenido la subida por un error en: ${e.fileName}\n${e.message}`);
            } finally {
                await this.refreshAppData();
                setTimeout(() => this.uploadProgress = 0, 2000);
            }
        },
        async uploadFolder() {
            const files = Array.from(this.$refs.folderInput.files);
            if (files.length === 0) return;

            try {
                const success = await UploadService.processUpload(files, this, true);
                if (success) {
                    this.status = "Carpeta subida con éxito.";
                    this.$refs.folderInput.value = '';
                }
            } catch (e) {
                this.status = "SUBIDA ABORTADA: " + e.message;
                this.uploadProgress = 0;
                alert(`Error crítico: ${e.message}. Se ha detenido la operación para evitar una subida incompleta.`);
            } finally {
                await this.refreshAppData();
                setTimeout(() => this.uploadProgress = 0, 2000);
            }
        },
        async handlePreview(file) {
            this.status = "Descifrando para vista previa...";
            try {
                const data = await PreviewService.getPreviewData(file, this.password);
                this.preview = {
                    active: true,
                    name: file.fileName,
                    ...data
                };
                this.status = "Vista previa cargada.";
            } catch (e) {
                alert("Error en preview: " + e.message);
                this.status = "Error al previsualizar.";
            }
        },
        closePreview() {
            if (this.preview.url) URL.revokeObjectURL(this.preview.url);
            this.preview.active = false;
            this.preview.url = null;
            this.preview.content = '';
        },
        async handleDownload(id, name) { await FileService.downloadFile(id, name, this.password, this); },
        async handleDelete(id) { await FileService.deleteFile(id, this); },
        enterFolder(n) { this.currentFolder = (this.currentFolder==='/'?'':this.currentFolder)+'/'+n; this.refreshAppData(); },
        goBack() { this.currentFolder = this.currentFolder.substring(0, this.currentFolder.lastIndexOf('/')) || '/'; this.refreshAppData(); },
        goToFolder(p) { this.currentFolder = p; this.refreshAppData(); },
        logout() { this.isLoggedIn = false; localStorage.removeItem('userSession'); Object.assign(this.$data, this.$options.data()); },
        formatSize(b) { return (b / (1024 * 1024)).toFixed(2) + ' MB'; }
    }
}).mount('#app');

window.app = appInstance