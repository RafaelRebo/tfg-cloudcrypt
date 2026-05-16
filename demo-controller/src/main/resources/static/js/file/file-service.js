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

    // En file-service.js
    getDisplayFiles(allUserFiles, currentFolder, currentCategory) {
        if (currentCategory === 'trash') {
            // 1. Obtenemos todos los elementos borrados
            const deletedItems = allUserFiles.filter(f => f.deletedAt !== null);

            // 2. Si estamos en la raíz de la papelera ('/')
            if (currentFolder === '/') {
                // Solo mostramos los elementos que NO tienen a su padre también borrado.
                // Esto identifica al "elemento raíz" que el usuario eliminó originalmente.
                return deletedItems.filter(item => {
                    const parentIsAlsoDeleted = deletedItems.some(potentialParent =>
                        potentialParent.id === item.parentId
                    );
                    return !parentIsAlsoDeleted;
                });
            }

            // 3. Si estamos dentro de una carpeta en la papelera
            // (Tu API ya debería estar filtrando por folderId, pero por seguridad filtramos aquí)
            return deletedItems;
        }

        if (currentCategory === 'shared') {
            return allUserFiles;
        }

        // Vista normal (Mis Archivos / Categorías): Ocultar siempre lo borrado
        return allUserFiles.filter(f => f.deletedAt === null);
    },

    getPathSegments(currentFolder, currentCategory, trashRootPath) {
        if (!currentFolder || currentFolder === '/') return [];

        const segments = [];
        const parts = currentFolder.split('/').filter(p => p !== '');
        let pathAccumulated = '';

        if (currentCategory === 'trash' && trashRootPath) {
            const rootParts = trashRootPath.split('/').filter(p => p !== '');
            let inTrashPath = false;

            parts.forEach((name, index) => {
                pathAccumulated += '/' + name;

                // Solo empezamos a añadir al breadcrumb cuando llegamos
                // a la carpeta que marcó el inicio de la papelera
                if (name === rootParts[rootParts.length - 1] || inTrashPath) {
                    inTrashPath = true;
                    segments.push({
                        name: name,
                        path: pathAccumulated
                    });
                }
            });
            return segments;
        }

        // Ruta normal para Mis Archivos
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

    getFileIconSvg(mime) {
        // ICONO DE IMAGEN (PNG, JPG, etc.)
        if (mime && mime.startsWith('image/')) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect width="18" height="18" x="3" y="3" rx="2" ry="2"/>
                <circle cx="9" cy="9" r="2"/>
                <path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>
            </svg>`;
        }

        // ICONO DE AUDIO (MP3, WAV, etc.)
        if (mime && mime.startsWith('audio/')) {
            return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M9 18V5l12-2v13"/>
                <circle cx="6" cy="18" r="3"/>
                <circle cx="18" cy="16" r="3"/>
            </svg>`;
        }

        if (mime && mime.startsWith('video/')) {
            return `<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lucide lucide-clapperboard-icon lucide-clapperboard"><path d="m12.296 3.464 3.02 3.956"/><path d="M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z"/><path d="M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="m6.18 5.276 3.1 3.899"/></svg>`;
        }

        // ICONO POR DEFECTO / DOCUMENTOS (PDF, TXT, etc.)
        // Mantiene el diseño que te gustó de la imagen image_792770.png
        return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/>
            <path d="M14 2v4a2 2 0 0 0 2 2h4"/>
        </svg>`;
    }

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

    async handleDownload(id, name) {
        const sessionKey = sessionStorage.getItem('fileKey');
        await FileService.downloadFile(id, name, sessionKey, this);
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
        const isTrash = this.currentCategory === 'trash';
        const msg = isTrash ? `¿Eliminar permanentemente ${count} elementos?` : `¿Mover ${count} elementos a la papelera?`;

        if (await this.askConfirmation(msg)) {
            try {
                await Promise.all(this.selectedIds.map(id => API.deleteFile(id)));
                this.showInfo(`${count} elementos procesados correctamente`);
                this.selectedIds = [];
                await this.refreshAppData();
            } catch (e) {
                this.showError("Hubo un error al eliminar algunos archivos");
                await this.refreshAppData();
            }
        }
    },

    async downloadSelected() {
        this.status = "Iniciando descarga múltiple...";
        // IMPORTANTE: Recuperamos la llave de sesión real
        const sessionKey = sessionStorage.getItem('fileKey');

        for (const id of this.selectedIds) {
            const file = this.allUserFiles.find(f => f.id === id);
            if (file && file.fileType !== 'application/x-directory') {
                // Pasamos sessionKey, NO this.password (que está vacío)
                await FileService.downloadFile(id, file.fileName, sessionKey, this);
                await new Promise(r => setTimeout(r, 600));
            }
        }
        this.status = "";
    },

    formatCategory(cat) {
        return UIService.formatCategory(cat);
    },

    getFileIcon(mime) {
        return FileService.getFileIconSvg(mime);
    },

    formatSize(b) {
        return UIService.formatSize(b);
    }
};