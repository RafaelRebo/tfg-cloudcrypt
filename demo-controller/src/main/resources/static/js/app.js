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
            
            // Normalizamos la carpeta actual: que siempre empiece por / y NUNCA termine en /
            let current = this.currentFolder.replace(/\/+$/, ""); // Quita barras al final
            if (!current.startsWith('/')) current = '/' + current;
            if (current === "") current = "/";

            this.allUserFiles.forEach(f => {
                let path = f.folderPath || '/';
                
                // Normalizamos el path del archivo igual
                path = path.replace(/\/+$/, "");
                if (!path.startsWith('/')) path = '/' + path;

                // Si el archivo vive "dentro" de donde estamos
                if (path.startsWith(current) && path !== current) {
                    // Sacamos lo que sobra: "/Fotos/Verano" -> "Verano" (si estamos en /Fotos)
                    let relative = path.substring(current.length);
                    if (relative.startsWith('/')) relative = relative.substring(1);
                    
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
                // 1. Archivos en la carpeta actual
                const res = await fetch(`/api/files?username=${this.username}&folder=${encodeURIComponent(this.currentFolder)}&t=${timestamp}`);
                if (res.ok) {
                    this.filesInCurrentFolder = await res.json();
                }

                // 2. Todos los archivos para reconstruir el árbol de carpetas
                // ¡IMPORTANTE! Asegúrate de que este endpoint en el Controller NO filtre por carpeta
                const resAll = await fetch(`/api/files?username=${this.username}&t=${timestamp}`); 
                if (resAll.ok) {
                    const data = await resAll.json();
                    console.log("Datos globales cargados:", data); // Mira la consola (F12) para ver qué llega
                    this.allUserFiles = data;
                }
            } catch (e) {
                console.error("Fallo en la integración REST:", e);
                this.status = "Error de red";
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

            for (let file of files) {
                try {
                    const formData = new FormData();
                    formData.append("file", file);
                    formData.append("username", this.username);
                    formData.append("password", this.password);
                    
                    // Reconstrucción de la ruta jerárquica para el Backend
                    const fullPath = file.webkitRelativePath;
                    const folderPath = '/' + fullPath.substring(0, fullPath.lastIndexOf('/'));
                    const fileName = file.name;

                    formData.append("folderPath", folderPath);
                    formData.append("fileName", fileName);

                    const res = await fetch('/api/files/upload', { method: 'POST', body: formData });
                    if (res.ok) successCount++;
                } catch (err) {
                    console.error("Error subiendo archivo:", file.name, err);
                }
            }
            
            this.status = `Subida finalizada: ${successCount} de ${files.length} archivos.`;
            // RECARGA CRÍTICA: Esto actualiza la tabla
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