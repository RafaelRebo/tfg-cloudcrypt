const { createApp } = Vue;

const appInstance = createApp({
    data() {
        return {
            isLoggedIn: false,
            username: '',
            password: '',
            currentFolder: '/',
            allUserFiles: [], 
            filesInCurrentFolder: [],
            files: [],
            status: '',
            stats: { totalSize: 0, fileCount: 0, maxQuota: 104857600 }
        }
    },
    mounted() {
        const savedSession = localStorage.getItem('userSession');
        if (savedSession) {
            const session = JSON.parse(savedSession);
            this.username = session.username;
            this.password = session.password;
            this.isLoggedIn = true;
            this.loadFiles();
        }
    },
    computed: {
        quotaPercentage() {
            return Math.min((this.stats.totalSize / this.stats.maxQuota) * 100, 100);
        },
        subFolders() {
            const folders = new Set();
            const current = this.currentFolder.endsWith('/') ? this.currentFolder : this.currentFolder + '/';

            this.allUserFiles.forEach(f => {
                let path = f.folderPath || '/';
                if (!path.endsWith('/')) path += '/';

                if (path.startsWith(current) && path !== current) {
                    const relative = path.substring(current.length);
                    const nextLevel = relative.split('/')[0];
                    if (nextLevel) folders.add(nextLevel);
                }
            });
            return Array.from(folders).sort();
        },
        pathSegments() {
            if (this.currentFolder === '/') return [];
            const parts = this.currentFolder.split('/').filter(p => p !== '');
            let path = '';
            return parts.map(p => {
                path += '/' + p;
                return { name: p, path: path };
            });
        }
    },
    methods: {
        async handleLogin() {
            const res = await API.login(this.username, this.password);
            if (res.ok) {
                this.isLoggedIn = true;
                localStorage.setItem('userSession', JSON.stringify({
                    username: this.username,
                    password: this.password
                }));
                this.loadFiles();
            } else alert("Error de acceso");
        },
        async handleRegister() {
            const res = await API.register(this.username, this.password);
            if (res.ok) alert("Registrado con éxito");
            else alert("Error al registrar");
        },
        async loadStats() {
            const res = await fetch(`/api/files/stats?username=${this.username}`);
            if (res.ok) this.stats = await res.json();
        },
        formatSize(bytes) {
            return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
        },
        async loadFiles() {
            const timestamp = Date.now();
            try {
                // 1. Archivos en la carpeta actual (para la tabla)
                const res = await fetch(`/api/files?username=${this.username}&folder=${encodeURIComponent(this.currentFolder)}&t=${timestamp}`);
                if (res.ok) this.filesInCurrentFolder = await res.json();

                // 2. TODOS los archivos (añadimos &all=true)
                const resAll = await fetch(`/api/files?username=${this.username}&all=true&t=${timestamp}`); 
                if (resAll.ok) {
                    const data = await resAll.json();
                    console.log("Datos globales cargados:", data);
                    this.allUserFiles = data; // Ahora sí tendrá todos los archivos
                }
            } catch (e) {
                console.error("Fallo:", e);
            }
        },
        enterFolder(name) {
            // Navegamos: "/" + "barbanegra" -> "/barbanegra"
            this.currentFolder = (this.currentFolder === '/' ? '' : this.currentFolder) + '/' + name;
            this.loadFiles();
        },
        goBack() {
            if (this.currentFolder === '/') return;
            const lastSlash = this.currentFolder.lastIndexOf('/');
            this.currentFolder = this.currentFolder.substring(0, lastSlash) || '/';
            this.loadFiles();
        },
        async onUpload() {
            const file = this.$refs.fileInput.files[0];
            if (!file) return;
            
            this.status = "Cifrando y subiendo...";
            const formData = new FormData();
            formData.append("file", file);
            formData.append("username", this.username);
            formData.append("password", this.password);
            formData.append("folderPath", this.currentFolder); // Subir a la carpeta donde estoy parado
            formData.append("fileName", file.name);

            // Usa fetch directamente o actualiza tu objeto API
            const res = await fetch('/api/files/upload', { method: 'POST', body: formData });
            
            if (res.ok) {
                this.status = "¡Listo!";
                this.loadFiles();
                this.loadStats();
            } else {
                const errorMsg = await res.text();
                this.status = errorMsg || "Error en la subida";
            }
        },
        async handleDownload(fileId, fileName) {
            this.status = "Descifrando y preparando descarga...";
            try {
                const res = await API.download(fileId, this.password);

                if (!res.ok) {
                    throw new Error("Error al descargar: Contraseña incorrecta o archivo no encontrado");
                }

                // Proceso para descargar el archivo desde memoria (Blob)
                const blob = await res.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = fileName; // Forzamos el nombre original
                document.body.appendChild(a);
                a.click();

                // Limpieza
                window.URL.revokeObjectURL(url);
                a.remove();
                this.status = "Descarga completada.";
            } catch (err) {
                alert(err.message);
                this.status = "Error en la descarga.";
            }
        },
        async handleDelete(id) {
            if (confirm("¿Estás seguro de que quieres borrar este archivo para siempre?")) {
                try {
                    const res = await fetch(`/api/files/${id}`, { method: 'DELETE' });
                    if (res.ok) {
                        this.status = "Archivo eliminado correctamente.";
                        // Recargamos archivos y estadísticas (la barra de cuota bajará)
                        await this.loadFiles();
                        await this.loadStats();
                    } else {
                        alert("Error al intentar borrar el archivo.");
                    }
                } catch (error) {
                    console.error("Error:", error);
                }
            }
        },
        async uploadFolder() {
            const files = this.$refs.folderInput.files;
            if (files.length === 0) return;

            this.status = `Subiendo ${files.length} archivos...`;
            let successCount = 0;

            // Preparamos la base de la ruta actual
            // Si estamos en "/", la base es vacía. Si estamos en "/barbanegra", la base es "/barbanegra"
            let baseFolder = this.currentFolder === '/' ? '' : this.currentFolder;

            for (let file of files) {
                try {
                    const formData = new FormData();
                    formData.append("file", file);
                    formData.append("username", this.username);
                    formData.append("password", this.password);
                    
                    // ANTES: solo cogía la ruta del PC
                    // AHORA: currentFolder + ruta del PC
                    const fullPath = file.webkitRelativePath;
                    const relativePathOnPC = fullPath.substring(0, fullPath.lastIndexOf('/'));
                    
                    // Combinamos ambas. Ejemplo: 
                    // Web: /barbanegra + PC: /fotos -> /barbanegra/fotos
                    const finalFolderPath = baseFolder + '/' + relativePathOnPC;
                    const fileName = file.name;

                    // Limpiamos posibles dobles barras "//" por si acaso
                    const cleanPath = finalFolderPath.replace(/\/+/g, '/');

                    formData.append("folderPath", cleanPath);
                    formData.append("fileName", fileName);

                    const res = await fetch('/api/files/upload', { method: 'POST', body: formData });
                    if (res.ok) successCount++;
                } catch (err) {
                    console.error("Error subiendo archivo:", file.name, err);
                }
            }
            
            this.status = `Subida finalizada: ${successCount} de ${files.length} archivos.`;
            await this.loadFiles();
            await this.loadStats();
        },
        goToFolder(path) {
            this.currentFolder = path;
            this.loadFiles();
        },
        logout() {
            this.isLoggedIn = false;
            this.files = [];
            this.username = '';
            this.password = '';
            // LIMPIAR SESIÓN
            localStorage.removeItem('userSession');
        },
        getDownloadUrl(id) {
            return `/api/files/download/${id}?password=${encodeURIComponent(this.password)}`;
        }
    }
}).mount('#app');

window.app = appInstance;