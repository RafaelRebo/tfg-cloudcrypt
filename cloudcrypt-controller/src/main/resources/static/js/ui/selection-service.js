const AppSelectionMethods = {
    isSelected(id) {
        return this.selectedIds.includes(id);
    },

    handleFileClick(f, event) {
        event.preventDefault();
    },

    clearSelection() {
        this.selectedIds = [];
        this.isTrueAllSelected = false;
    },

    startDragSelect(e) {
        if (e.button !== 0 || e.target.closest('.file-row') || e.target.closest('button')) return;

        this.selectionBox.active = true;
        this.selectionBox.wasDragging = false;
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

        if (!this.selectionBox.wasDragging) {
            const dist = Math.hypot(e.clientX - this.selectionBox.startX, e.clientY - this.selectionBox.startY);
            if (dist > 5) {
                this.selectionBox.wasDragging = true;
                this.selectedIds = [];
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
        const fileElements = document.querySelectorAll('.file-row');

        fileElements.forEach((el) => {
            const rect = el.getBoundingClientRect();
            const isOverlap = !(
                rect.left > boxRight ||
                rect.right < boxLeft ||
                rect.top > boxBottom ||
                rect.bottom < boxTop
            );

            if (isOverlap) {
                const id = this.getIdFromElement(el);
                if (id) newSelection.push(id);
            }
        });

        this.selectedIds = newSelection;
    },

    stopDragSelect() {
        this.selectionBox.active = false;
    },

    handleBackgroundClick(e) {
        if (this.selectionBox.wasDragging) {
            this.selectionBox.wasDragging = false;
            return;
        }
        this.selectedIds = [];
    },

    getIdFromElement(el) {
        return parseInt(el.getAttribute('data-id'));
    },

    async selectAllFiles() {
        if (!this.displayFiles || this.displayFiles.length === 0) return;
        this.selectedIds = this.displayFiles.map(f => f.id);

        if (this.allUserFiles.length < this.totalElements) {
            this.status = "Seleccionando la totalidad de los archivos del directorio...";
            try {
                let pageToFetch = this.currentPage + 1;
                let gathering = true;

                while (gathering) {
                    const res = await API.getFiles(this.currentFolderId, this.currentCategory, pageToFetch);

                    if (res.content && res.content.length > 0) {
                        this.allUserFiles.push(...res.content);
                        this.currentPage = pageToFetch;
                    }

                    if (!res.page || res.page.number >= res.page.totalPages - 1) {
                        gathering = false;
                    } else {
                        pageToFetch++;
                    }
                }

                this.selectedIds = this.displayFiles.map(f => f.id);
                this.hasMore = false;

            } catch (responseError) {
                this.status = "";
                // ⚡ TOAST FIX: Extrae el motivo real si el servidor corta la sincronización masiva
                const msg = await API.extractErrorMessage(responseError);
                this.showError(msg);
            } finally {
                this.status = "";
            }
        }
    },

    onFileDragStart(file, event) {
        if (this.selectionTimer) {
            clearTimeout(this.selectionTimer);
            this.selectionTimer = null;
            if (!this.isSelected(file.id)) {
                this.selectedIds = [file.id];
            }
        }

        if (!this.isSelected(file.id)) {
            this.selectedIds = [file.id];
        }

        this.draggingId = file.id;
        event.dataTransfer.setData("text/plain", JSON.stringify(this.selectedIds));
        event.dataTransfer.dropEffect = "move";
        event.dataTransfer.effectAllowed = "move";
    },

    onFileDragOver(file, event) {
        if (file.fileType === 'application/x-directory' && !this.isSelected(file.id)) {
            event.currentTarget.classList.add('drag-target');
        }
    },

    onFileDragEnd() {
        this.draggingId = null;
        this.status = "";
    },

    onFileDragLeave(file, event) {
        event.currentTarget.classList.remove('drag-target');
    },

    handleFileMouseDown(file, event) {
        const now = Date.now();
        const isDoubleClick = (now - this.lastClickTime) < this.clickThreshold;
        this.lastClickTime = now;

        if (isDoubleClick) {
            if (this.selectionTimer) {
                clearTimeout(this.selectionTimer);
                this.selectionTimer = null;
            }
            this.openFileOrFolder(file);
            return;
        }

        if (this.isSelected(file.id)) return;

        const isControlPressed = event.ctrlKey || event.metaKey;

        if (isControlPressed) {
            const index = this.selectedIds.indexOf(file.id);
            if (index > -1) {
                this.selectedIds.splice(index, 1);
            } else {
                this.selectedIds.push(file.id);
            }
        } else {
            if (this.selectionTimer) clearTimeout(this.selectionTimer);
            this.selectionTimer = setTimeout(() => {
                this.selectedIds = [file.id];
                this.selectionTimer = null;
            }, 200);
        }
    },

    openFileOrFolder(f) {
        if (this.currentCategory === 'starred') {
            const targetCategory = (f.ownerUsername !== this.username) ? 'shared' : 'all';

            if (f.fileType === 'application/x-directory') {
                this.currentCategory = targetCategory;
                this.currentFolderId = f.id;
                this.currentFolder = f.folderPath === '/' ? `/${f.fileName}` : `${f.folderPath}/${f.fileName}`;
                this.refreshAppData();
            } else {
                this.currentCategory = targetCategory;
                this.currentFolderId = f.parentId;
                this.currentFolder = f.folderPath;
                this.selectedIds = [f.id];
                this.refreshAppData();
            }
            return;
        }

        if (f.fileType === 'application/x-directory') {
            if (this.currentCategory === 'trash' && !this.trashRootPath) {
                this.trashRootPath = f.folderPath;
            }
            this.enterFolder(f);
        } else {
            if (!f.deletedAt) this.handlePreview(f);
            else this.showInfo("Restaura el archivo para previsualizarlo");
        }
    },

    async onFileDrop(targetFolder, event) {
        event.currentTarget.classList.remove('drag-target');
        if (targetFolder.fileType !== 'application/x-directory') return;

        try {
            const idsToMove = JSON.parse(event.dataTransfer.getData("text/plain"));
            if (idsToMove.includes(targetFolder.id)) return;

            const res = await API.moveFiles(idsToMove, targetFolder.id);
            if (res.ok) {
                this.showInfo("Elementos movidos con éxito.");
                await this.refreshAppData();
            } else {
                // ⚡ TOAST FIX: Si el arrastre es ilegal o denegado, alertamos al usuario con un globo explícito
                const errorMsg = await API.extractErrorMessage(res);
                this.showError(errorMsg);
            }
        } catch (e) {
            this.showError("No se ha podido procesar la reubicación por arrastre.");
        }
    },
};