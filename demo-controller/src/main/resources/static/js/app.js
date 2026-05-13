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
            selectionBox: {
                active: false,
                wasDragging: false,
                startX: 0,
                startY: 0,
                style: { top: 0, left: 0, width: 0, height: 0 }
            },
            lastClickTime: 0,
            clickThreshold: 300,
            selectionTimer: null,
            draggingId: null,
            shareModal: {
                active: false,
                fileId: null,
                fileName: '',
                isFolder: false,
                searchQuery: '',
                searchResults: [], // Para la lista desplegable
                selectedUsers: [],
                isProcessing: false
            },
        }
    },
    async mounted() {
        const session = AuthService.getSavedSession();
        if (session) {
            this.username = session.username;
            this.isLoggedIn = true;

            // Si no hay llaves en RAM, las recuperamos silenciosamente
            if (!window.userPublicKey || !window.userPrivateKey) {
                try {
                    await AuthService.login(session.username, session.password);
                } catch (e) {
                    console.error("No se pudieron precargar las llaves");
                }
            }
            this.refreshAppData();
        }
        window.addEventListener('keydown', this.handleGlobalKeydown);
        window.addEventListener('scroll', this.handleInfiniteScroll);
    },
    unmounted() {
        window.removeEventListener('keydown', this.handleGlobalKeydown);
        window.removeEventListener('scroll', this.handleInfiniteScroll);
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
                const res = await API.getFiles(this.currentFolder, this.currentCategory, this.currentPage);
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
                    if (!window.userPublicKey) {
                            await AuthService.setupUserCrypto(this.username, secureKey);
                            // Volvemos a llamar al login para cargar las llaves recién creadas
                            await AuthService.login(this.username, secureKey);
                        }
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
        // En app.js -> methods -> handleRegister
        async handleRegister() {
            try {
                this.status = "Generando identidad segura...";
                const masterKey = await AuthService.deriveMasterKey(this.username, this.password);

                // 1. Crear el usuario en la BD
                const res = await API.register(this.username, masterKey);
                if (!res.ok) throw new Error("Fallo al crear usuario");

                // 2. Login para obtener el Token JWT
                const loginRes = await API.login(this.username, masterKey);
                if (!loginRes.ok) throw new Error("Fallo al autenticar tras registro");

                const loginData = await loginRes.json();
                localStorage.setItem('jwtToken', loginData.token);
                localStorage.setItem('username', loginData.username);

                // IMPORTANTE: Guardar la llave de sesión para que esté disponible sin re-loguear
                sessionStorage.setItem('fileKey', masterKey);

                // 3. Generar, cargar en RAM y registrar llaves
                const cryptoRes = await AuthService.setupUserCrypto(this.username, masterKey);

                if (cryptoRes.ok) {
                    this.showInfo("Registro e identidad completados.");
                    this.isLoggedIn = true;
                    this.password = ''; // Limpiar contraseña por seguridad
                    await this.refreshAppData();
                } else {
                    throw new Error("Fallo al registrar llaves criptográficas");
                }
            } catch (e) {
                this.showError(e.message);
            } finally {
                this.status = "";
            }
        },
        logout() {
            AuthService.logout();
            this.isLoggedIn = false;

            // LIMPIEZA CRÍTICA:
            window.userPrivateKey = null;
            window.userPublicKey = null;

            Object.assign(this.$data, this.$options.data());
        },
        onDragOver() {

            const isExternalFile = event.dataTransfer.types.includes('Files');

            if (isExternalFile) {
                this.isDragging = true;
            }

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
                    name,
                    parentId,
                    sessionKey
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
            event.preventDefault();
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
            const numericId = parseInt(this.currentFolderId);
            const targetId = (!isNaN(numericId) && numericId > 0) ? numericId : null;
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
                        const check = await API.checkExists(name, targetId);

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
                'shared': 'Compartidos conmigo',
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

            // Si el archivo está marcado como compartido o estamos en la pestaña shared
            if (this.currentCategory === 'shared') {
                this.currentFolder = f.fileName; // Solo para el breadcrumb
            } else {
                this.currentFolder = FileService.normalizePath(f.folderPath + '/' + f.fileName);
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
        formatSize(b) { return (b / (1024 * 1024)).toFixed(1) + ' MB'; },
        startDragSelect(e) {
            if (e.button !== 0 || e.target.closest('.file-row') || e.target.closest('button')) return;

            this.selectionBox.active = true;
            this.selectionBox.wasDragging = false; // Reiniciamos
            this.selectionBox.startX = e.clientX;
            this.selectionBox.startY = e.clientY;

            this.selectionBox.style = {
                left: `${e.clientX}px`,
                top: `${e.clientY}px`,
                width: '0px',
                height: '0px'
            };
        },

        onDragSelect(e) {
            if (!this.selectionBox.active) return;

            // Si el ratón se ha movido más de 5px, consideramos que es un arrastre
            if (!this.selectionBox.wasDragging) {
                const dist = Math.hypot(e.clientX - this.selectionBox.startX, e.clientY - this.selectionBox.startY);
                if (dist > 5) {
                    this.selectionBox.wasDragging = true;
                    this.selectedIds = []; // Limpiamos solo al empezar a arrastrar de verdad
                }
            }

            if (!this.selectionBox.wasDragging) return;

            const currentX = e.clientX;
            const currentY = e.clientY;
            const startX = this.selectionBox.startX;
            const startY = this.selectionBox.startY;

            const left = Math.min(startX, currentX);
            const top = Math.min(startY, currentY);
            const width = Math.abs(currentX - startX);
            const height = Math.abs(currentY - startY);

            this.selectionBox.style = {
                left: `${left}px`,
                top: `${top}px`,
                width: `${width}px`,
                height: `${height}px`
            };

            this.calculateSelection(left, top, width, height);
        },

        calculateSelection(boxLeft, boxTop, boxWidth, boxHeight) {
            const boxRight = boxLeft + boxWidth;
            const boxBottom = boxTop + boxHeight;

            const newSelection = [];
            // Buscamos todos los elementos de archivo en el DOM
            const fileElements = document.querySelectorAll('.file-row');

            fileElements.forEach((el) => {
                const rect = el.getBoundingClientRect();

                // Verificamos si el rectángulo de selección se solapa con el archivo
                const isOverlap = !(
                    rect.left > boxRight ||
                    rect.right < boxLeft ||
                    rect.top > boxBottom ||
                    rect.bottom < boxTop
                );

                if (isOverlap) {
                    // Obtenemos el ID que Vue tiene asociado (puedes usar un data-id en el HTML)
                    // O buscar en tu array allUserFiles basándote en el índice o texto
                    const id = this.getIdFromElement(el);
                    if (id) newSelection.push(id);
                }
            });

            this.selectedIds = newSelection;
        },

        stopDragSelect() {
            this.selectionBox.active = false;
            // No reseteamos wasDragging aquí, lo haremos en el manejador del clic
        },

        handleBackgroundClick(e) {
            // Si acabamos de terminar un arrastre, NO limpiamos la selección
            if (this.selectionBox.wasDragging) {
                this.selectionBox.wasDragging = false; // Consumimos la bandera
                return;
            }
            // Si fue un clic limpio en el fondo, entonces sí borramos
            this.selectedIds = [];
        },

        // Función auxiliar para sacar el ID del elemento DOM
        getIdFromElement(el) {
            // Asumiendo que en el v-for de displayFiles pusiste :data-id="f.id"
            return parseInt(el.getAttribute('data-id'));
        },
        handleGlobalKeydown(e) {
            // 1. Evitar que se active si el usuario está escribiendo en un input o textarea
            const isInput = e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA';
            if (isInput) return;

            // 2. Detectar Ctrl + A (o Cmd + A)
            if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'a') {
                e.preventDefault(); // Evita que el navegador seleccione el texto de toda la página
                this.selectAllFiles();
            }
        },

        selectAllFiles() {
            // Seleccionamos los IDs de los ficheros que se muestran actualmente en pantalla
            if (this.displayFiles && this.displayFiles.length > 0) {
                this.selectedIds = this.displayFiles.map(f => f.id);
            }
        },
        onFileDragStart(file, event) {
            // Si el usuario empieza a arrastrar y el timer de selección aún no ha terminado
            if (this.selectionTimer) {
                clearTimeout(this.selectionTimer);
                this.selectionTimer = null;
                if (!this.isSelected(file.id)) {
                    this.selectedIds = [file.id];
                }
            }

            // 2. Forzamos la selección si no lo estaba
            if (!this.isSelected(file.id)) {
                this.selectedIds = [file.id];
            }

            // 3. Activamos el estado de arrastre para el cursor CSS
            this.draggingId = file.id;

            // 4. Configuramos los datos del drag
            event.dataTransfer.setData("text/plain", JSON.stringify(this.selectedIds));
            event.dataTransfer.dropEffect = "move";

            // Cambiamos el cursor del sistema a 'grabbing' (algunos navegadores lo requieren)
            event.dataTransfer.effectAllowed = "move";
        },

        onFileDragOver(file, event) {
            if (file.fileType === 'application/x-directory' && !this.isSelected(file.id)) {
                event.currentTarget.classList.add('drag-target');
            }
        },

        onFileDragEnd() {
            // Limpiamos el estado al soltar el archivo (sea donde sea)
            this.draggingId = null;
            this.status = "";
        },

        onFileDragLeave(file, event) {
            event.currentTarget.classList.remove('drag-target');
        },

        // En app.js -> methods
        handleFileMouseDown(file, event) {
            const now = Date.now();
            const isDoubleClick = (now - this.lastClickTime) < this.clickThreshold;
            this.lastClickTime = now;

            // 1. Si es doble clic, abortamos cualquier selección pendiente y abrimos
            if (isDoubleClick) {
                if (this.selectionTimer) {
                    clearTimeout(this.selectionTimer);
                    this.selectionTimer = null;
                }
                this.openFileOrFolder(file);
                return;
            }

            // 2. Si ya está seleccionado, no hacemos nada (permitimos arrastre de grupo)
            if (this.isSelected(file.id)) return;

            // 3. Manejo de selección (con retraso para esperar al posible doble clic)
            const isControlPressed = event.ctrlKey || event.metaKey;

            if (isControlPressed) {
                // Con Control la selección suele ser instantánea en Windows, pero si quieres
                // evitar el parpadeo también aquí, podemos usar el mismo timer.
                const index = this.selectedIds.indexOf(file.id);
                if (index > -1) {
                    this.selectedIds.splice(index, 1);
                } else {
                    this.selectedIds.push(file.id);
                }
            } else {
                // LIMPIEZA DE TIMERS PREVIOS
                if (this.selectionTimer) clearTimeout(this.selectionTimer);

                // RETRASAMOS LA SELECCIÓN
                this.selectionTimer = setTimeout(() => {
                    // Solo seleccionamos si no hemos hecho un segundo clic entre medias
                    this.selectedIds = [file.id];
                    this.selectionTimer = null;
                }, 200); // 200ms es suficiente para que no se sienta lento pero evite el parpadeo
            }
        },

        openFileOrFolder(f) {
            this.selectedIds = []; // Limpiamos al entrar
            if (f.fileType === 'application/x-directory') {
                this.enterFolder(f);
            } else {
                if (!f.deletedAt) this.handlePreview(f);
            }
        },

        async onFileDrop(targetFolder, event) {
            event.currentTarget.classList.remove('drag-target');
            if (targetFolder.fileType !== 'application/x-directory') return;

            try {
                const idsToMove = JSON.parse(event.dataTransfer.getData("text/plain"));
                // Evitar moverse a sí mismo
                if (idsToMove.includes(targetFolder.id)) return;

                const res = await API.moveFiles(idsToMove, targetFolder.id);
                if (res.ok) {
                    this.showInfo("Elementos movidos.");
                    await this.refreshAppData();
                }
            } catch (e) {
                console.error("Error al mover:", e);
            }
        },
        openShareModal(f) {
            // Primero reseteamos el objeto para asegurar que Vue detecte el cambio de estado
            this.shareModal.searchResults = [];
            this.shareModal.selectedUsers = [];
            this.shareModal.searchQuery = '';
            this.shareModal.isProcessing = false;

            // Ahora asignamos los valores del archivo
            this.shareModal.fileId = f.id;
            this.shareModal.fileName = f.fileName;
            this.shareModal.isFolder = f.fileType === 'application/x-directory';
            this.shareModal.active = true;
        },
        closeShareModal() {
            this.shareModal.active = false;
        },
        addUserToShare() {
            const user = this.shareModal.searchQuery.trim();
            if (user && user !== this.username && !this.shareModal.selectedUsers.includes(user)) {
                this.shareModal.selectedUsers.push(user);
                this.shareModal.searchQuery = '';
            }
        },
        removeUserFromShare(user) {
            this.shareModal.selectedUsers = this.shareModal.selectedUsers.filter(u => u !== user);
        },

        async executeShare() {
            this.shareModal.isProcessing = true;
            try {
                let itemsToShare = [];
                if (this.shareModal.isFolder) {
                    const res = await fetch(`/api/files/folder-content-recursive/${this.shareModal.fileId}`, {
                        headers: API.getAuthHeader()
                    });
                    itemsToShare = await res.json();
                } else {
                    itemsToShare = [{ id: this.shareModal.fileId, fileType: 'archivo' }];
                }

                for (const item of itemsToShare) {
                    const shareRequests = [];

                    if (item.fileType === 'application/x-directory') {
                        // PARA CARPETAS: Enviamos una clave vacía solo para crear el registro de acceso
                        for (const targetUser of this.shareModal.selectedUsers) {
                            shareRequests.push({ targetUsername: targetUser, encryptedKey: "FOLDER_PERMISSION" });
                        }
                    } else {
                        // PARA ARCHIVOS: Lógica AES + RSA que ya tenemos
                        const keyRes = await fetch(`/api/files/${item.id}/key`, { headers: API.getAuthHeader() });
                        const { encryptedFileKey } = await keyRes.json();
                        const aesKeyObj = await CryptoService.unwrapKey(encryptedFileKey, window.userPrivateKey);
                        const rawAesKey = await window.crypto.subtle.exportKey("raw", aesKeyObj);

                        for (const targetUser of this.shareModal.selectedUsers) {
                            const userData = await API.getUserPublicKey(targetUser);
                            const targetPubKey = await CryptoService.importExternalPublicKey(userData.publicKey);
                            const wrappedKey = await CryptoService.wrapKey(rawAesKey, targetPubKey);
                            shareRequests.push({ targetUsername: targetUser, encryptedKey: wrappedKey });
                        }
                    }

                    await fetch(`/api/files/${item.id}/share`, {
                        method: 'POST',
                        headers: { ...API.getAuthHeader(), 'Content-Type': 'application/json' },
                        body: JSON.stringify(shareRequests)
                    });
                }
                this.showInfo("Estructura compartida correctamente");
                this.closeShareModal();
            } catch (e) {
                this.showError("Fallo en jerarquía: " + e.message);
            } finally {
                this.shareModal.isProcessing = false;
            }
        },
        async onUserSearchInput() {
            const query = this.shareModal.searchQuery.trim();

            // Cambiamos a 1 para que con una sola letra ya busque
            if (query.length < 1) {
                this.shareModal.searchResults = [];
                return;
            }

            try {
                // Llamada a la API
                const results = await API.searchUsers(query);

                // Filtramos para no mostrarnos a nosotros mismos y no mostrar
                // a alguien que ya hayamos seleccionado en los chips
                this.shareModal.searchResults = results.filter(u =>
                    u !== this.username && !this.shareModal.selectedUsers.includes(u)
                );
            } catch (e) {
                console.error("Error buscando usuarios:", e);
            }
        },

        selectUser(user) {
            if (!this.shareModal.selectedUsers.includes(user)) {
                this.shareModal.selectedUsers.push(user);
            }
            this.shareModal.searchQuery = '';
            this.shareModal.searchResults = [];
        },
    },
    watch: {
        uploadProgress(newVal) {
            NotificationService.updateUploadProgress(this);
        }
    }
}).mount('#app');