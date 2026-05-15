const UIService = {
    // Resaltado SEGURO contra XSS
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

    // Cálculos del cuadro de selección azul
    getSelectionBoxStyle(startX, startY, currentX, currentY) {
        return {
            left: Math.min(startX, currentX) + 'px',
            top: Math.min(startY, currentY) + 'px',
            width: Math.abs(currentX - startX) + 'px',
            height: Math.abs(currentY - startY) + 'px'
        };
    },

    isColliding(el, boxStyle) {
        const rect = el.getBoundingClientRect();
        const box = {
            left: parseInt(boxStyle.left),
            top: parseInt(boxStyle.top),
            right: parseInt(boxStyle.left) + parseInt(boxStyle.width),
            bottom: parseInt(boxStyle.top) + parseInt(boxStyle.height)
        };
        return !(rect.left > box.right || rect.right < box.left || rect.top > box.bottom || rect.bottom < box.top);
    },

    getFileIcon(mime) { return FileService.getFileIconSvg(mime); },
    formatSize(b) { return (b / (1024 * 1024)).toFixed(1) + ' MB'; },
    formatCategory(cat) {
        const labels = { 'all': 'Mis archivos', 'image': 'Imágenes', 'audio': 'Audio', 'video': 'Vídeos', 'document': 'Documentos', 'shared': 'Compartidos conmigo', 'trash': 'Papelera', 'starred': 'Destacados' };
        return labels[cat] || cat;
    }
};

const AppModalMethods = {
    closeModal(resolve, result) {
        this.confirmModal.active = false;
        setTimeout(() => {
            this.confirmModal.isDuplicateMode = false;
            this.confirmModal.isInput = false;
            this.confirmModal.applyToAll = false;
            this.confirmModal.title = '';
            this.confirmModal.message = '';
        }, 300);
        resolve(result);
    },

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
                    const check = await API.checkExists(name, targetId);

                    if (check.exists) {
                        this.confirmModal.active = false;
                        await new Promise(r => setTimeout(r, 100));

                        const proceed = await this.askConfirmation(
                            `Ya existe una carpeta llamada "${name}". ¿Deseas crear otra con el mismo nombre?`
                        );

                        if (!proceed) return;
                    }

                    await this.handleCreateFolder(name, targetId);
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
    }
};

const AppSelectionMethods = {
    isSelected(id) {
        return this.selectedIds.includes(id);
    },

    handleFileClick(f, event) {
        event.preventDefault();
    },

    clearSelection() {
        this.selectedIds = [];
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

    handleGlobalKeydown(e) {
        const isInput = e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA';
        if (isInput) return;

        if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'a') {
            e.preventDefault();
            this.selectAllFiles();
        }
    },

    selectAllFiles() {
        if (this.displayFiles && this.displayFiles.length > 0) {
            this.selectedIds = this.displayFiles.map(f => f.id);
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
            if (f.fileType === 'application/x-directory') {
                this.currentCategory = 'all';
                this.currentFolderId = f.id;
                this.currentFolder = f.folderPath === '/' ? `/${f.fileName}` : `${f.folderPath}/${f.fileName}`;
                this.refreshAppData();
            } else {
                this.currentCategory = 'all';
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
                this.showInfo("Elementos movidos.");
                await this.refreshAppData();
            }
        } catch (e) {
            console.error("Error al mover:", e);
        }
    }
};