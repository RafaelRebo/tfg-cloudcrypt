const AppShareMethods = {
    async openShareModal(f) {
        this.shareModal.fileId = f.id;
        this.shareModal.fileName = f.fileName;
        this.shareModal.isFolder = f.fileType === 'application/x-directory';
        this.shareModal.selectedUsers = [];
        this.shareModal.searchQuery = '';

        try {
            const res = await fetch(`/api/files/${f.id}/shared-users`, { headers: API.getAuthHeader() });
            if (res.ok) {
                this.shareModal.selectedUsers = await res.json();
            } else {
                const errorMsg = await API.extractErrorMessage(res);
                this.showError(errorMsg);
                return;
            }
        } catch (e) {
            this.showError("Error al conectar con la lista de control de acceso.");
            return;
        }
        this.shareModal.active = true;
    },

    closeShareModal() {
        this.shareModal.active = false;
    },

    removeUserFromShare(user) {
        this.shareModal.selectedUsers = this.shareModal.selectedUsers.filter(u => u !== user);
    },

    openMassShareModal() {
        if (this.selectedIds.length === 0) return;
        this.shareModal.fileId = null;
        this.shareModal.fileName = `${this.selectedIds.length} elementos seleccionados`;
        this.shareModal.isFolder = false;
        this.shareModal.selectedUsers = [];
        this.shareModal.searchQuery = '';
        this.shareModal.searchResults = [];
        this.shareModal.active = true;
    },

    async executeShare() {
        this.shareModal.isProcessing = true;
        this.status = "Calculando árbol de claves masivo...";

        try {
            const targetsWorklist = this.shareModal.fileId ? [this.shareModal.fileId] : [...this.selectedIds];
            let flatItemsToShare = [];

            for (const id of targetsWorklist) {
                const currentFile = this.allUserFiles.find(f => f.id === id);
                if (!currentFile) continue;

                if (currentFile.fileType === 'application/x-directory') {
                    const res = await fetch(`/api/files/folder-content-recursive/${id}`, { headers: API.getAuthHeader() });
                    if (!res.ok) {
                        const errorMsg = await API.extractErrorMessage(res);
                        throw new Error(errorMsg);
                    }
                    const children = await res.json();
                    flatItemsToShare.push({ id, fileType: 'application/x-directory' });
                    flatItemsToShare.push(...children);
                } else {
                    flatItemsToShare.push({ id, fileType: currentFile.fileType });
                }
            }

            this.status = "Homogeneizando listas de control de acceso...";
            for (const itemId of targetsWorklist) {
                const currentRes = await fetch(`/api/files/${itemId}/shared-users`, { headers: API.getAuthHeader() });
                if (!currentRes.ok) {
                    const errorMsg = await API.extractErrorMessage(currentRes);
                    throw new Error(errorMsg);
                }
                const originalUsers = await currentRes.json();

                for (const userToken of originalUsers) {
                    const targetName = typeof userToken === 'object' ? userToken.username : userToken;
                    const revokeRes = await fetch(`/api/files/${itemId}/share/revoke?target=${encodeURIComponent(targetName)}`, {
                        method: 'DELETE', headers: API.getAuthHeader()
                    });
                    if (!revokeRes.ok) {
                        const errorMsg = await API.extractErrorMessage(revokeRes);
                        throw new Error(errorMsg);
                    }
                }
            }

            if (this.shareModal.selectedUsers.length > 0) {
                this.status = "Descifrando y re-envolviendo sobres digitales...";

                const recipientKeys = {};
                for (const user of this.shareModal.selectedUsers) {
                    const data = await API.getUserPublicKey(user);
                    if (data) recipientKeys[user] = data.publicKey;
                }

                const batchRequests = [];

                for (const item of flatItemsToShare) {
                    if (item.fileType === 'application/x-directory') {
                        for (const targetUser of this.shareModal.selectedUsers) {
                            batchRequests.push({
                                fileId: item.id, targetUsername: targetUser, encryptedKey: "FOLDER_PERMISSION"
                            });
                        }
                    } else {
                        const keyRes = await fetch(`/api/files/${item.id}/key`, { headers: API.getAuthHeader() });
                        if (!keyRes.ok) {
                            const errorMsg = await API.extractErrorMessage(keyRes);
                            throw new Error(errorMsg);
                        }
                        const { encryptedFileKey } = await keyRes.json();

                        for (const targetUser of this.shareModal.selectedUsers) {
                            const pubKeyJwk = recipientKeys[targetUser];
                            if (!pubKeyJwk) continue;

                            const wrappedKey = await CryptoService.reWrapKeyForUser(encryptedFileKey, pubKeyJwk);
                            batchRequests.push({
                                fileId: item.id, targetUsername: targetUser, encryptedKey: wrappedKey
                            });
                        }
                    }
                }

                if (batchRequests.length > 0) {
                    this.status = "Confirmando transacciones en el servidor...";
                    const batchRes = await fetch('/api/files/share/batch', {
                        method: 'POST',
                        headers: { ...API.getAuthHeader(), 'Content-Type': 'application/json' },
                        body: JSON.stringify(batchRequests)
                    });
                    if (!batchRes.ok) {
                        const errorMsg = await API.extractErrorMessage(batchRes);
                        throw new Error(errorMsg);
                    }
                }
            }

            this.showInfo("Permisos y sobres criptográficos actualizados.");
            this.closeShareModal();
            this.clearSelection();
            await this.refreshAppData();
        } catch (e) {
            this.showError(e.message);
        } finally {
            this.shareModal.isProcessing = false;
            this.status = "";
        }
    },

    async onUserSearchInput() {
        const query = this.shareModal.searchQuery.trim();
        if (query.length < 1) {
            this.shareModal.searchResults = [];
            return;
        }
        try {
            const results = await API.searchUsers(query);
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
    }
};