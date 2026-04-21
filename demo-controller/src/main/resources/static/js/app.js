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
            preview: { active: false, url: null, name: '', type: '', content: '' }
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
            if (!this.username) return;
            try {
                const [folderFiles, allFiles, stats] = await Promise.all([
                    API.getFiles(this.username, this.currentFolder),
                    API.getFiles(this.username, null, true),
                    API.getStats(this.username)
                ]);
                this.filesInCurrentFolder = folderFiles;
                this.allUserFiles = allFiles;
                this.stats = stats;
            } catch (e) { this.status = "Error de sincronización"; }
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
            const file = this.$refs.fileInput.files[0];
            if (!file) return;
            try {
                await UploadService.uploadSingle(file, this.currentFolder, 0, file.size, this);
                this.status = "¡Subida con éxito!";
                await this.refreshAppData();
            } catch (e) { this.status = "Error: " + e; this.uploadProgress = 0; }
            setTimeout(() => this.uploadProgress = 0, 2000);
        },
        async uploadFolder() {
            const files = this.$refs.folderInput.files;
            if (files.length === 0) return;
            let total = Array.from(files).reduce((a, f) => a + f.size, 0), current = 0;
            let base = this.currentFolder === '/' ? '' : this.currentFolder;
            for (let f of files) {
                const rel = f.webkitRelativePath.substring(0, f.webkitRelativePath.lastIndexOf('/'));
                const target = (base + '/' + rel).replace(/\/+/g, '/');
                try {
                    await UploadService.uploadSingle(f, target, current, total, this);
                    current += f.size;
                } catch (e) { current += f.size; }
            }
            this.status = "Carpeta subida.";
            await this.refreshAppData();
            setTimeout(() => this.uploadProgress = 0, 2000);
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