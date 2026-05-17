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
    formatSize(bytes) {
        if (bytes === undefined || bytes === null || isNaN(bytes)) return '-';
        if (bytes === 0) return '0 B';

        const k = 1024;
        const units = ['B', 'KB', 'MB', 'GB', 'TB'];

        const i = Math.floor(Math.log(bytes) / Math.log(k));

        const val = bytes / Math.pow(k, i);

        const decimals = (i === 0 || val % 1 === 0) ? 0 : 1;

        return val.toFixed(decimals) + ' ' + units[i];
    },
    formatCategory(cat) {
        const labels = { 'all': 'Mis archivos', 'image': 'Imágenes', 'audio': 'Audio', 'video': 'Vídeos', 'document': 'Documentos', 'shared': 'Compartidos conmigo', 'trash': 'Papelera', 'starred': 'Destacados' };
        return labels[cat] || cat;
    },

    handleContextMenu(event, item) {
        // 1. Almacenamos temporalmente las coordenadas puras del clic del ratón
        const clickX = event.clientX;
        const clickY = event.clientY;

        this.contextMenu.target = item;

        // 2. Evaluamos reactivamente el tipo de menú (Fichero, Múltiple o Vacío)
        if (item) {
            const isAlreadySelected = this.selectedIds.includes(item.id);
            if (!isAlreadySelected) {
                this.selectedIds = [item.id];
                this.contextMenu.type = 'file';
            } else {
                this.contextMenu.type = this.selectedIds.length > 1 ? 'multiple' : 'file';
            }
        } else {
            this.contextMenu.type = 'blank';
        }

        this.contextMenu.x = clickX;
        this.contextMenu.y = clickY;
        this.contextMenu.active = true;

        this.$nextTick(() => {
            const menuEl = document.querySelector('.context-menu');
            if (!menuEl) return;

            const menuWidth = menuEl.offsetWidth;
            const menuHeight = menuEl.offsetHeight;

            const screenWidth = window.innerWidth;
            const screenHeight = window.innerHeight;

            if (clickY + menuHeight > screenHeight) {
                this.contextMenu.y = clickY - menuHeight;
            }

            if (clickX + menuWidth > screenWidth) {
                this.contextMenu.x = clickX - menuWidth;
            }
        });
    },

    handleGlobalKeydown(event) {
        const targetTag = event.target.tagName.toLowerCase();
        if (targetTag === 'input' || targetTag === 'textarea') return;

        const isCtrlOrCmd = event.ctrlKey || event.metaKey;

        if (isCtrlOrCmd) {
            const key = event.key.toLowerCase();

            // 🌟 CORRECCIÓN 1: Restauramos el atajo de seleccionar todo
            if (key === 'a') {
                event.preventDefault(); // Evitamos la selección de texto nativa del navegador
                if (typeof this.selectAllFiles === 'function') {
                    this.selectAllFiles();
                }
            }

            // ✂️ Cortar
            else if (key === 'x') {
                if (this.selectedIds && this.selectedIds.length > 0) {
                    event.preventDefault();
                    this.handleCut();
                }
            }

            // 📋 Copiar
            else if (key === 'c') {
                if (this.selectedIds && this.selectedIds.length > 0) {
                    event.preventDefault();
                    this.handleCopy();
                }
            }

            // 📥 Pegar
            else if (key === 'v') {
                if (this.clipboard && this.clipboard.items.length > 0) {
                    event.preventDefault();
                    this.handlePaste();
                }
            }
        }
    },

    closeContextMenu() {
        this.contextMenu.active = false;
        this.contextMenu.target = null;
    },

    handleCut() {
        // Capturamos el mapa de objetos seleccionados actualmente en la UI
        this.clipboard.items = this.selectedIds.map(id => this.allUserFiles.find(f => f.id === id)).filter(Boolean);
        this.clipboard.action = 'cut';
        this.showInfo(`${this.clipboard.items.length} elemento(s) listos para mover.`);
    },

    handleCopy() {
        this.clipboard.items = this.selectedIds.map(id => this.allUserFiles.find(f => f.id === id)).filter(Boolean);
        this.clipboard.action = 'copy';
        this.showInfo(`${this.clipboard.items.length} elemento(s) listos para copiar.`);
    },

    async handlePaste() {
        if (this.clipboard.items.length === 0) return;
        this.status = "Procesando elementos del portapapeles...";

        let applyAllAction = null;

        try {
            for (const item of this.clipboard.items) {
                const isSameFolder = item.parentId === this.currentFolderId;

                // No-Op seguro si se corta y pega en la misma ubicación
                if (isSameFolder && this.clipboard.action === 'cut') {
                    continue;
                }

                let currentName = item.fileName;
                const isDir = item.fileType === 'application/x-directory';

                const checkRes = await API.checkExists(currentName, this.currentFolderId);
                let action = applyAllAction;

                if (isSameFolder && this.clipboard.action === 'copy') {
                    action = 'copy';
                }

                if (checkRes.exists && !action) {
                    const userChoice = await this.askUserForDuplicateAction(currentName, isDir);
                    if (userChoice.applyToAll) applyAllAction = userChoice.action;
                    action = userChoice.action;
                }

                if (action === 'skip') continue;

                if (action === 'overwrite') {
                    const delRes = await API.deleteFile(checkRes.existingId, true);
                    // ⚡ TOAST FIX: Si falla la purga por falta de permisos o integridad, extraemos el error semántico
                    if (!delRes.ok) throw new Error(await API.extractErrorMessage(delRes));
                }

                let targetName = currentName;
                if (action === 'copy') {
                    const dot = currentName.lastIndexOf('.');
                    const nameNoExt = dot !== -1 ? currentName.substring(0, dot) : currentName;
                    const ext = dot !== -1 ? currentName.substring(dot) : '';

                    let counter = 1;
                    targetName = `${nameNoExt} (Copia)${ext}`;
                    let nestedCheck = await API.checkExists(targetName, this.currentFolderId);
                    while (nestedCheck.exists) {
                        counter++;
                        targetName = `${nameNoExt} (Copia ${counter})${ext}`;
                        nestedCheck = await API.checkExists(targetName, this.currentFolderId);
                    }
                }

                // ⚡ TOAST FIXES: Validamos el .ok de cada operación de red y disparamos el extractor si el servidor rechaza la acción
                if (this.clipboard.action === 'cut') {
                    const moveRes = await API.moveFiles([item.id], this.currentFolderId);
                    if (!moveRes.ok) throw new Error(await API.extractErrorMessage(moveRes));

                    if (targetName !== currentName) {
                        const renameRes = await API.renameFile(item.id, targetName);
                        if (!renameRes.ok) throw new Error(await API.extractErrorMessage(renameRes));
                    }
                } else if (this.clipboard.action === 'copy') {
                    const copyRes = await API.copyFiles([item.id], this.currentFolderId, targetName);
                    if (!copyRes.ok) throw new Error(await API.extractErrorMessage(copyRes));
                }
            }

            this.showInfo("Portapapeles procesado con éxito.");

            if (this.clipboard.action === 'cut') {
                this.clipboard.action = null;
                this.clipboard.items = [];
            }

            this.clearSelection();
            await this.refreshAppData();

        } catch (e) {
            // Canalaea el mensaje exacto inyectado en el throw new Error()
            this.showError(e.message || "Hubo un fallo al pegar los elementos");
        } finally {
            this.status = "";
        }
    },
};

