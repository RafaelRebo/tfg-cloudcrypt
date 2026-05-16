const NavigationService = {
    // Gestiona el historial sin duplicados
    updateHistory(ctx) {
        if (ctx.isHistoryMoving || ctx.currentCategory !== 'all') return;
        const currentPosition = { folder: ctx.currentFolder, folderId: ctx.currentFolderId };

        ctx.navigationHistory = ctx.navigationHistory.slice(0, ctx.historyIndex + 1);
        const lastState = ctx.navigationHistory[ctx.navigationHistory.length - 1];

        if (!lastState || lastState.folderId !== currentPosition.folderId) {
            ctx.navigationHistory.push(currentPosition);
            ctx.historyIndex = ctx.navigationHistory.length - 1;
        }
    },

    handleMove(ctx, direction) {
        const newIndex = ctx.historyIndex + direction;
        if (newIndex < 0 || newIndex >= ctx.navigationHistory.length) return;

        ctx.isHistoryMoving = true;
        ctx.historyIndex = newIndex;
        const state = ctx.navigationHistory[newIndex];
        ctx.currentCategory = 'all';
        ctx.currentFolder = state.folder;
        ctx.currentFolderId = state.folderId;
        ctx.refreshAppData().then(() => ctx.isHistoryMoving = false);
    }
};

const AppNavigationMethods = {
    async refreshAppData() {
        this.currentPage = 0;
        this.hasMore = true;
        this.status = "Actualizando...";
        this.selectedIds = [];

        try {
            const res = await API.getFiles(
                this.currentFolderId,
                this.currentCategory,
                0
            );

            this.allUserFiles = [];
            this.allUserFiles = res.content;
            if (res.page) {
                this.hasMore = res.page.number < res.page.totalPages - 1;
                this.totalElements = res.page.totalElements;
            } else {
                this.hasMore = false;
                this.totalElements = res.content.length;
            }
            this.stats = await API.getStats(this.username);
            this.status = "";

            // --- GESTIÓN DE HISTORIAL CORREGIDA (SIN DUPLICADOS) ---
            if (!this.isHistoryMoving && this.currentCategory === 'all') {
                const currentPosition = {
                    folder: this.currentFolder,
                    folderId: this.currentFolderId
                };

                if (this.navigationHistory.length == 0) {
                    this.navigationHistory.push(currentPosition);
                    this.historyIndex = 0;
                } else {
                    this.navigationHistory = this.navigationHistory.slice(0, this.historyIndex + 1);
                    const lastState = this.navigationHistory[this.navigationHistory.length - 1];

                    if (lastState.folderId !== currentPosition.folderId || lastState.folder !== currentPosition.folder) {
                        this.navigationHistory.push(currentPosition);
                        this.historyIndex = this.navigationHistory.length - 1;
                    }
                }
            }
        } catch (e) {
            this.showError("Error al actualizar la vista");
        }
    },

    navigateBack() {
        if (this.historyIndex > 0) {
            this.isHistoryMoving = true;
            this.historyIndex--;
            const previousState = this.navigationHistory[this.historyIndex];

            this.currentCategory = 'all';
            this.currentFolder = previousState.folder;
            this.currentFolderId = previousState.folderId;

            this.refreshAppData().then(() => { this.isHistoryMoving = false; });
        }
    },

    navigateForward() {
        if (this.historyIndex < this.navigationHistory.length - 1) {
            this.isHistoryMoving = true;
            this.historyIndex++;
            const nextState = this.navigationHistory[this.historyIndex];

            this.currentCategory = 'all';
            this.currentFolder = nextState.folder;
            this.currentFolderId = nextState.folderId;

            this.refreshAppData().then(() => { this.isHistoryMoving = false; });
        }
    },

    handleInfiniteScroll(event) {
        // Capturamos el contenedor exacto que está haciendo scroll
        const el = event.target;

        // Alerta de depuración opcional: console.log(el.scrollTop + el.clientHeight, el.scrollHeight);

        // Si la suma de lo scrollado más la altura visible llega al fondo (-100px de margen)
        if (el.scrollTop + el.clientHeight >= el.scrollHeight - 100) {
            this.loadNextPage();
        }
    },

    async loadNextPage() {
        if (this.isLoadingMore || !this.hasMore) return;
        this.isLoadingMore = true;
        this.currentPage++;

        try {
            const res = await API.getFiles(this.currentFolderId, this.currentCategory, this.currentPage);

            if (res.content && res.content.length > 0) {
                this.allUserFiles.push(...res.content);
            }

            if (res.page) {
                this.totalElements = res.page.totalElements;
                if (res.page.number >= res.page.totalPages - 1) {
                    this.hasMore = false;
                }
            } else {
                // Fallback por si la respuesta viniera plana en algún endpoint
                this.hasMore = false;
            }
        } catch (e) {
            console.error("Error cargando la siguiente página:", e);
        } finally {
            this.isLoadingMore = false;
        }
    },

    setCategory(cat) {
        this.currentCategory = cat;
        this.currentFolder = '/';
        this.currentFolderId = null;
        this.folderIdMap.clear();
        this.folderIdMap.set('/', null);
        this.trashRootPath = null;
        this.searchQuery = '';
        this.isSearching = false;
        this.clearSelection();
        this.refreshAppData();
    },

    enterFolder(f) {
        this.currentFolderId = f.id;

        let newPath;
        if (this.currentCategory === 'shared') {
            newPath = this.currentFolder === '/' ? '/' + f.fileName : this.currentFolder + '/' + f.fileName;
        } else {
            newPath = FileService.normalizePath(f.folderPath + '/' + f.fileName);
        }

        this.currentFolder = newPath;
        this.folderIdMap.set(newPath, f.id);

        this.refreshAppData();
    },

    isTrashRoot(f) {
        return this.currentCategory === 'trash';
    },

    goBack() {
        this.currentFolder = this.currentFolder.substring(0, this.currentFolder.lastIndexOf('/')) || '/';
        this.refreshAppData();
    },

    goToFolder(path, id = null) {
        this.currentFolder = path;

        if (id === null) {
            this.currentFolderId = this.folderIdMap.get(path) || null;
        } else {
            this.currentFolderId = id;
        }

        this.refreshAppData();
    },

    onCrumbDragOver(event) {
        event.currentTarget.classList.add('drag-target');
    },

    onCrumbDragLeave(event) {
        event.currentTarget.classList.remove('drag-target');
    },

    // Dentro de AppNavigationMethods en js/ui/navigation-service.js

    async onCrumbDrop(targetPath, targetFolderId, event) {
        event.currentTarget.classList.remove('drag-target');

        try {
            const idsToMove = JSON.parse(event.dataTransfer.getData("text/plain"));
            if (!idsToMove || idsToMove.length === 0) return;

            this.status = "Relocalizando elementos...";
            let targetId = targetFolderId;

            if (targetPath === '/') {
                targetId = null;
            }
            else if (!targetId) {
                targetId = this.folderIdMap.get(targetPath) || null;
            }

            // 3. Enviamos la petición con el ID real recuperado del mapa
            const res = await API.moveFiles(idsToMove, targetId);
            if (res.ok) {
                this.showInfo("Elementos relocalizados con éxito.");
                await this.refreshAppData();
            } else {
                this.showError("No se pudieron mover los elementos a ese nivel.");
            }
        } catch (e) {
            console.error("Error al soltar en breadcrumbs:", e);
            this.showError("Operación de arrastre no válida.");
        } finally {
            this.status = "";
        }
    }
};