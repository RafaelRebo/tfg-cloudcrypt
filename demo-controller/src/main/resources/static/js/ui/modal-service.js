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
};