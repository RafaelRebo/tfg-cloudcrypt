const FileService = {
   async downloadFile(fileId, fileName, _, context) {
       context.status = "Descargando y descifrando...";
       try {
           const res = await API.download(fileId);
           if (!res.ok) throw new Error("Acceso denegado");
           const encryptedBlob = await res.blob();

           const keyRes = await fetch(`/api/files/${fileId}/key`, { headers: API.getAuthHeader() });
           const { encryptedFileKey } = await keyRes.json();

           // EL WORKER se encarga de usar la llave privada interna
           const aesKeyObj = await CryptoService.unwrapKey(encryptedFileKey);
           const decryptedBuffer = await CryptoService.decryptFile(encryptedBlob, aesKeyObj);

           const url = window.URL.createObjectURL(new Blob([decryptedBuffer]));
           const a = document.createElement('a');
           a.href = url;
           a.download = fileName;
           a.click();
           window.URL.revokeObjectURL(url);

           context.status = "";
           context.showInfo("Archivo descargado correctamente.");
       } catch (err) {
           context.showError("Error al descifrar: " + err.message);
       }
   },

   async downloadFolder(folderId, folderName, context) {
       context.status = `Calculando árbol de archivos para: ${folderName}...`;
       try {
           // 1. Recuperamos la estructura recursiva completa de hijos
           const res = await fetch(`/api/files/folder-content-recursive/${folderId}`, { headers: API.getAuthHeader() });
           if (!res.ok) throw new Error("No se pudo leer la estructura de directorios");
           const items = await res.json();

           const filesToProcess = items.filter(item => item.fileType !== 'application/x-directory');

           if (filesToProcess.length === 0) {
               context.showInfo(`La carpeta "${folderName}" está vacía.`);
               return;
           }

           // 2. Preparar el mapa de rutas para el ZIP
           const rootFolder = context.allUserFiles.find(f => f.id === folderId);
           const rootPath = rootFolder ? this.normalizePath(rootFolder.folderPath + '/' + rootFolder.fileName) : '';

           const zip = new JSZip();

           // 3. CONFIGURACIÓN DEL MOTOR DE CONCURRENCIA
           // Creamos una copia de la lista de archivos para usarla como una cola de tareas compartida
           const queue = [...filesToProcess];
           let completedCount = 0;

           // Un límite de 6 a 8 conexiones paralelas exprime el canal HTTP/2 sin bloquear el navegador
           const CONCURRENCY_LIMIT = 6;

           // Definimos el ciclo de vida asíncrono que ejecutará cada hilo "trabajador"
           const workerTask = async () => {
               while (queue.length > 0) {
                   // Extraemos el siguiente archivo de la cola de forma segura
                   const file = queue.shift();
                   if (!file) continue;

                   try {
                       // Descarga del bloque binario físico
                       const fileRes = await API.download(file.id);
                       if (!fileRes.ok) throw new Error("Fallo de descarga");
                       const encryptedBlob = await fileRes.blob();

                       // Descarga del sobre digital RSA
                       const keyRes = await fetch(`/api/files/${file.id}/key`, { headers: API.getAuthHeader() });
                       const { encryptedFileKey } = await keyRes.json();

                       // Descifrado asíncrono delegando en el Web Worker criptográfico
                       const aesKeyObj = await CryptoService.unwrapKey(encryptedFileKey);
                       const decryptedBuffer = await CryptoService.decryptFile(encryptedBlob, aesKeyObj);

                       // Reconstrucción de la ruta interna dentro del ZIP
                       let relativeFolder = '';
                       if (rootPath && file.folderPath.startsWith(rootPath)) {
                           relativeFolder = file.folderPath.substring(rootPath.length);
                       } else {
                           relativeFolder = file.folderPath;
                       }
                       relativeFolder = relativeFolder.replace(/^\/+|\/+$/g, '');
                       const zipPath = relativeFolder ? `${relativeFolder}/${file.fileName}` : file.fileName;

                       // Inyección síncrona en caliente en la estructura del ZIP en RAM
                       zip.file(zipPath, decryptedBuffer);

                   } catch (fileError) {
                       console.error(`Error procesando archivo ${file.fileName}:`, fileError);
                   } finally {
                       // Incrementamos el contador y actualizamos el estado visual global en tiempo real
                       completedCount++;
                       context.status = `Procesando elementos en paralelo (${completedCount}/${filesToProcess.length})...`;
                   }
               }
           };

           // 4. DISPARO MULTIHILO CONTENIDO
           // Creamos tantos hilos trabajadores en paralelo como marque el límite de concurrencia
           const workers = Array(Math.min(CONCURRENCY_LIMIT, filesToProcess.length))
               .fill(null)
               .map(() => workerTask());

           // Esperamos a que todos los hilos terminen de vaciar la cola de tareas
           await Promise.all(workers);

           // 5. Compresión final masiva
           context.status = "Empaquetando estructura en archivo ZIP...";
           const zipBlob = await zip.generateAsync({ type: "blob" });

           const url = window.URL.createObjectURL(zipBlob);
           const a = document.createElement('a');
           a.href = url;
           a.download = `${folderName}.zip`;
           a.click();
           window.URL.revokeObjectURL(url);

           context.status = "";
           context.showInfo(`Carpeta "${folderName}" descargada a máxima velocidad.`);

       } catch (err) {
           console.error(err);
           context.showError("Error al descargar la estructura: " + err.message);
       }
   },

    async deleteFile(file, context) {
        const isTrashed = !!file.deletedAt;
        const isShared = context.currentCategory === 'shared';
        let proceed = false;

        if (isTrashed) {
            proceed = await context.askConfirmation(`¿Eliminar "${file.fileName}" permanentemente?`);
        } else if (isShared) {
            proceed = await context.askConfirmation(`¿Deseas quitar tu acceso a "${file.fileName}"? No podrás volver a verlo a menos que te lo compartan de nuevo.`);
        } else {
            proceed = await context.askConfirmation(`¿Mover "${file.fileName}" a la papelera?`);
        }

        if (!proceed) return;

        try {
            const res = await API.deleteFile(file.id);
            if (res.ok) {
                context.showInfo(isShared ? "Acceso revocado" : (isTrashed ? "Eliminado" : "Papelera"));
                await context.refreshAppData();
            } else {
                throw new Error();
            }
        } catch (error) {
            context.showError("No se pudo eliminar el elemento.");
        }
    },

    async restoreFile(file, context) {
        try {
            const res = await fetch(`/api/files/${file.id}/restore`, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${localStorage.getItem('jwtToken')}`
                }
            });
            if (res.ok) {
                context.showInfo("Restaurado correctamente");
                await context.refreshAppData();
            }
        } catch (e) {
            context.showError("Error al restaurar");
        }
    },

    getDisplayFiles(allUserFiles, currentFolder, currentCategory, sortKey = 'fileName', sortOrder = 'asc') {
        let filtered = [];

        if (currentCategory === 'trash') {
            const deletedItems = allUserFiles.filter(f => f.deletedAt !== null);
            if (currentFolder === '/') {
                filtered = deletedItems.filter(item => {
                    const parentIsAlsoDeleted = deletedItems.some(p => p.id === item.parentId);
                    return !parentIsAlsoDeleted;
                });
            } else {
                filtered = deletedItems;
            }
        } else if (currentCategory === 'shared') {
            filtered = allUserFiles;
        } else {
            filtered = allUserFiles.filter(f => f.deletedAt === null);
        }

        const folders = filtered.filter(f => f.fileType === 'application/x-directory');
        const files = filtered.filter(f => f.fileType !== 'application/x-directory');

        const compareElements = (a, b, key, order) => {
            let valA = a[key];
            let valB = b[key];

            if (typeof valA === 'string') {
                return order === 'asc'
                    ? valA.localeCompare(valB, 'es', { sensitivity: 'base' })
                    : valB.localeCompare(valA, 'es', { sensitivity: 'base' });
            }
            if (key === 'updatedAt') {
                valA = new Date(valA || 0);
                valB = new Date(valB || 0);
            }
            if (valA < valB) return order === 'asc' ? -1 : 1;
            if (valA > valB) return order === 'asc' ? 1 : -1;
            return 0;
        };

        if (sortKey === 'fileSize') {
            folders.sort((a, b) => a.fileName.localeCompare(b.fileName, 'es', { sensitivity: 'base' }));
        } else {
            folders.sort((a, b) => compareElements(a, b, sortKey, sortOrder));
        }

        files.sort((a, b) => compareElements(a, b, sortKey, sortOrder));

        return [...folders, ...files];
    },

    getPathSegments(currentFolder, currentCategory, trashRootPath) {
        if (!currentFolder || currentFolder === '/') return [];

        const segments = [];
        const parts = currentFolder.split('/').filter(p => p !== '');
        let pathAccumulated = '';

        parts.forEach((name) => {
            pathAccumulated += '/' + name;
            segments.push({
                name: name,
                path: pathAccumulated
            });
        });

        return segments;
    },

    normalizePath(path) {
        if (!path || path === '/') return '/';
        let p = path.replace(/\/+/g, '/');
        if (p.endsWith('/') && p.length > 1) p = p.slice(0, -1);
        if (!p.startsWith('/')) p = '/' + p;
        return p;
    },

    getFileIconSvg(mime, fileName) {
        const m = (mime || '').toLowerCase();
        const name = (fileName || '').toLowerCase();
        const ext = name.split('.').pop(); // Extrae la extensión (ej: 'zip', 'pdf')

        // 1. PDFs
        if (m === 'application/pdf' || ext === 'pdf') {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="3" y="3" width="18" height="18" rx="3" fill="none" />
                        <text x="12" y="12.5" font-family="system-ui, -apple-system, sans-serif" font-size="6.5" font-weight="900" fill="#ffffff" stroke="none" text-anchor="middle" dominant-baseline="central">PDF</text>
                    </svg>`;
        }
        // 2. Archivos Comprimidos (ZIP, RAR, 7Z, TAR)
        if (m.includes('zip') || m.includes('rar') || m.includes('7z') || m.includes('tar') || ['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><path d="M12 2v4M12 8v2M12 12v2"/></svg>`;
        }

        // 3. Hojas de Cálculo (Excel, CSV)
        if (m.includes('excel') || m.includes('spreadsheetml') || m.includes('csv') || ['xls', 'xlsx', 'csv'].includes(ext)) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><rect width="8" height="8" x="8" y="10" rx="1"/></svg>`;
        }

        // 4. Documentos de Texto o Código (TXT, Markdown, JS, JSON, HTML, etc.)
        if (m.startsWith('text/') || m.includes('json') || m.includes('javascript') || ['txt', 'md', 'json', 'js', 'html', 'css', 'xml'].includes(ext)) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="14" y2="17"/></svg>`;
        }

        // 5. Presentaciones (PowerPoint)
        if (m.includes('powerpoint') || m.includes('presentationml') || ['ppt', 'pptx'].includes(ext)) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polygon points="12 10 16 14 12 18 8 14"/></svg>`;
        }

        // 6. Filtros nativos multimedia que ya tenías (Imágenes, Audio, Vídeo)
        if (m.startsWith('image/')) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>`;
        }
        if (m.startsWith('audio/')) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>`;
        }
        if (m.startsWith('video/')) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m12.296 3.464 3.02 3.956"/><path d="M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z"/><path d="M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="m6.18 5.276 3.1 3.899"/></svg>`;
        }

        // 7. Icono genérico por defecto (Documento blanco)
        return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/></svg>`;
    },

    formatModDate(dateStr) {
        if (!dateStr) return '-';

        const date = new Date(dateStr);

        // 1. Formateamos la parte de la fecha (igual que antes)
        let datePart = date.toLocaleDateString('es-ES', {
            day: 'numeric',
            month: 'short',
            year: 'numeric'
        }).replace('.', '').replace(',', '');

        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');

        return `${datePart} ${hours}:${minutes}`;
    },

};

const AppFileMethods = {
    async handleCreateFolder(name, parentId) {
        try {
            const sessionKey = sessionStorage.getItem('fileKey');
            const res = await API.createFolder(name, parentId, sessionKey);

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
            } catch (e) {
                this.showError("No se pudo descifrar el archivo.");
            }
        }
    },

    closePreview() {
        if (this.preview.url) URL.revokeObjectURL(this.preview.url);
        this.preview.active = false;
        this.preview.url = null;
        this.preview.content = '';
    },

    async handleDownload(fileOrId, name = null) {
        if (typeof fileOrId === 'object' && fileOrId !== null) {
            // Firma moderna: pasamos el objeto archivo/carpeta completo de la fila
            if (fileOrId.fileType === 'application/x-directory') {
                await FileService.downloadFolder(fileOrId.id, fileOrId.fileName, this);
            } else {
                const sessionKey = sessionStorage.getItem('fileKey');
                await FileService.downloadFile(fileOrId.id, fileOrId.fileName, sessionKey, this);
            }
        } else {
            // Firma heredada por compatibilidad (ej: desde la ventana de previsualización)
            const sessionKey = sessionStorage.getItem('fileKey');
            await FileService.downloadFile(fileOrId, name, sessionKey, this);
        }
    },

    async handleRestore(f) {
        await FileService.restoreFile(f, this);
    },

    async handleDelete(f) {
        await FileService.deleteFile(f, this);
    },

    async handleToggleStar(f) {
        try {
            await API.toggleStar(f.id);
            f.starred = !f.starred; // Actualización optimista en UI
            if (this.currentCategory === 'starred' && !f.starred) {
                this.refreshAppData(); // Si dejamos de destacar estando en la sección, refrescamos
            }
        } catch (e) {
            this.showError("Error al destacar");
        }
    },

    async toggleStarSelected() {
        const count = this.selectedIds.length;
        if (count === 0) return;

        this.status = "Actualizando destacados...";
        try {
            // Ejecutamos las peticiones HTTP en ráfaga paralela
            await Promise.all(this.selectedIds.map(id => API.toggleStar(id)));

            this.showInfo(`Metadatos actualizados para ${count} elementos.`);
            this.clearSelection();
            await this.refreshAppData();
        } catch (e) {
            this.showError("Error al procesar la actualización masiva de destacados");
        } finally {
            this.status = "";
        }
    },

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

        // 1. Escapar HTML para evitar XSS (Nivel Pro TFG)
        const div = document.createElement('div');
        div.textContent = text;
        const safeText = div.innerHTML;

        // 2. Aplicar el resaltado sobre el texto ya seguro
        const regex = new RegExp(`(${this.searchQuery})`, 'gi');
        return safeText.replace(regex, '<span class="highlight">$1</span>');
    },

    async deleteSelected() {
        const count = this.selectedIds.length;
        if (count === 0) return;

        const isTrash = this.currentCategory === 'trash';
        const msg = isTrash ? `¿Eliminar permanentemente ${count} elementos?` : `¿Mover ${count} elementos a la papelera?`;

        if (await this.askConfirmation(msg)) {
            this.status = "Eliminando elementos...";
            try {
                // Borrado masivo y simultáneo mediante los IDs recolectados previamente
                await Promise.all(this.selectedIds.map(id => API.deleteFile(id)));

                this.showInfo(`${count} elementos procesados y eliminados.`);
                this.selectedIds = [];
                await this.refreshAppData(); // Limpia y resetea la vista a la página 0 limpia
            } catch (e) {
                this.showError("Hubo un error al eliminar algunos archivos");
                await this.refreshAppData();
            } finally {
                this.status = "";
            }
        }
    },

    async downloadSelected() {
        const count = this.selectedIds.length;
        if (count === 0) return;

        const sessionKey = sessionStorage.getItem('fileKey');

        // 1. Mapeamos las llaves primarias a los objetos de metadatos cargados en la UI
        const selectedItems = this.selectedIds.map(id => this.allUserFiles.find(f => f.id === id)).filter(Boolean);

        // 2. Evaluamos la regla por decreto: ¿Hay carpetas o más de un único elemento seleccionado?
        const hasFolder = selectedItems.some(item => item.fileType === 'application/x-directory');
        const shouldZip = hasFolder || selectedItems.length > 1;

        // --- 📄 CASO EXCEPCIÓN: Un único fichero individual -> Descarga directa nativa ---
        if (!shouldZip) {
            const singleFile = selectedItems[0];
            await FileService.downloadFile(singleFile.id, singleFile.fileName, sessionKey, this);
            this.clearSelection();
            return;
        }

        // --- 🗜️ CASO DECRETO: Crear un único ZIP combinado de alto rendimiento ---
        this.status = "Preparando descarga unificada en ZIP...";
        try {
            const zip = new JSZip();
            const tasks = []; // Cola aplanada de tareas criptográficas: { id, zipPath }

            // FASE 1: Construcción del árbol jerárquico de tareas en memoria
            for (const item of selectedItems) {
                if (item.fileType === 'application/x-directory') {
                    // Si es carpeta, traemos recursivamente su estructura completa desde tu API
                    const res = await fetch(`/api/files/folder-content-recursive/${item.id}`, { headers: API.getAuthHeader() });
                    if (!res.ok) continue;
                    const children = await res.json();

                    const rootPath = FileService.normalizePath(item.folderPath + '/' + item.fileName);

                    children.forEach(child => {
                        // Saltamos los directorios vacíos (JSZip autocrea las rutas dinámicamente)
                        if (child.fileType !== 'application/x-directory') {
                            let relativeFolder = child.folderPath.substring(rootPath.length).replace(/^\/+|\/+$/g, '');
                            // Estructura interna: NombreCarpetaBase/SubcarpetasOpcionales/archivo.ext
                            const zipPath = item.fileName + (relativeFolder ? '/' + relativeFolder : '') + '/' + child.fileName;
                            tasks.push({ id: child.id, zipPath });
                        }
                    });
                } else {
                    // Si es un fichero suelto seleccionado en el nivel actual, viaja directo a la raíz del ZIP
                    tasks.push({ id: item.id, zipPath: item.fileName });
                }
            }

            if (tasks.length === 0) {
                this.showError("La selección no contiene elementos válidos para empaquetar.");
                this.status = "";
                return;
            }

            // FASE 2: Ráfaga de descifrado asíncrono con límite de concurrencia
            const queue = [...tasks];
            let completedCount = 0;
            const CONCURRENCY_LIMIT = 6; // Balance perfecto de descargas HTTP/2 paralelas

            const workerTask = async () => {
                while (queue.length > 0) {
                    const task = queue.shift();
                    if (!task) continue;

                    try {
                        // Descarga paralela del binario cifrado opaco del servidor
                        const fileRes = await API.download(task.id);
                        if (!fileRes.ok) continue;
                        const encryptedBlob = await fileRes.blob();

                        // Descarga del sobre digital RSA-OAEP
                        const keyRes = await fetch(`/api/files/${task.id}/key`, { headers: API.getAuthHeader() });
                        const { encryptedFileKey } = await keyRes.json();

                        // Apertura de clave y descifrado AES-GCM en el Web Worker secundario
                        const aesKeyObj = await CryptoService.unwrapKey(encryptedFileKey);
                        const decryptedBuffer = await CryptoService.decryptFile(encryptedBlob, aesKeyObj);

                        // Inyección inmediata del ArrayBuffer descifrado puro dentro del ZIP
                        zip.file(task.zipPath, decryptedBuffer);
                    } catch (err) {
                        console.error(`Error procesando elemento del lote (ID: ${task.id}):`, err);
                    } finally {
                        completedCount++;
                        this.status = `Descifrando y empaquetando lote (${completedCount}/${tasks.length})...`;
                    }
                }
            };

            // Disparamos la piscina de hilos concurrentes
            const workers = Array(Math.min(CONCURRENCY_LIMIT, tasks.length))
                .fill(null)
                .map(() => workerTask());

            await Promise.all(workers);

            // FASE 3: Compilación, compresión y descarga final del contenedor unificado
            this.status = "Generando archivo comprimido final...";
            const zipBlob = await zip.generateAsync({ type: "blob" });

            const url = window.URL.createObjectURL(zipBlob);
            const a = document.createElement('a');
            a.href = url;

            // Estética: Si seleccionaron un único elemento (que por fuerza era carpeta), usamos su nombre.
            // Si es una selección mixta/múltiple de varios archivos, usamos un nombre de exportación genérico.
            a.download = (selectedItems.length === 1) ? `${selectedItems[0].fileName}.zip` : 'cloud_crypt_export.zip';

            a.click();
            window.URL.revokeObjectURL(url);

            this.showInfo(`¡Descarga masiva de ${tasks.length} elementos completada!`);
        } catch (e) {
            console.error(e);
            this.showError("Fallo crítico en el empaquetado del lote comprimido: " + e.message);
        } finally {
            this.status = "";
            this.clearSelection(); // Limpiamos la barra de selección y las banderas al terminar
        }
    },

    formatCategory(cat) {
        return UIService.formatCategory(cat);
    },

    getFileIcon(mime, fileName = '') {
        return FileService.getFileIconSvg(mime, fileName);
    },

    formatModDate(dateStr) {
        return FileService.formatModDate(dateStr);
    },

    changeSort(key) {
        if (this.sortKey === key) {
            this.sortOrder = this.sortOrder === 'asc' ? 'desc' : 'asc';
        } else {
            this.sortKey = key;
            this.sortOrder = 'asc';
        }
    },

    formatSize(b) {
        return UIService.formatSize(b);
    }
};