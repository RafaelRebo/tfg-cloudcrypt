const AppShareMethods = {
    async openShareModal(f) {
        this.shareModal.fileId = f.id;
        this.shareModal.fileName = f.fileName;
        this.shareModal.isFolder = f.fileType === 'application/x-directory';
        this.shareModal.selectedUsers = [];
        this.shareModal.searchQuery = '';

        try {
            const res = await fetch(`/api/files/${f.id}/shared-users`, {
                headers: API.getAuthHeader()
            });

            if (res.ok) {
                this.shareModal.selectedUsers = await res.json();
            }
        } catch (e) {
            console.error("Error al recuperar usuarios compartidos:", e);
        }

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
            const currentRes = await fetch(`/api/files/${this.shareModal.fileId}/shared-users`, { headers: API.getAuthHeader() });
            const originalUsers = await currentRes.json();

            const usersToRemove = originalUsers.filter(u => !this.shareModal.selectedUsers.includes(u));
            const usersToAdd = this.shareModal.selectedUsers.filter(u => !originalUsers.includes(u));

            // Revocaciones
            for (const userToken of usersToRemove) {
                const targetName = typeof userToken === 'object' ? userToken.username : userToken;
                await fetch(`/api/files/${this.shareModal.fileId}/share/revoke?target=${encodeURIComponent(targetName)}`, {
                    method: 'DELETE',
                    headers: API.getAuthHeader()
                });
            }

            if (usersToAdd.length > 0) {
                let itemsToShare = [];
                if (this.shareModal.isFolder) {
                    const res = await fetch(`/api/files/folder-content-recursive/${this.shareModal.fileId}`, { headers: API.getAuthHeader() });
                    itemsToShare = await res.json();
                } else {
                    itemsToShare = [{ id: this.shareModal.fileId, fileType: 'archivo' }];
                }

                const recipientKeys = {};
                for (const user of usersToAdd) {
                    const data = await API.getUserPublicKey(user);
                    if (data) recipientKeys[user] = data.publicKey;
                }

                // --- AQUÍ ESTÁ EL CAMBIO: EL BATCH ---
                const batchRequests = [];
                const CONCURRENCY_LIMIT = 8;
                const queue = [...itemsToShare];

                const processQueue = async () => {
                    while (queue.length > 0) {
                        const item = queue.shift();

                        if (item.fileType === 'application/x-directory') {
                            for (const targetUser of usersToAdd) {
                                batchRequests.push({
                                    fileId: item.id,
                                    targetUsername: targetUser,
                                    encryptedKey: "FOLDER_PERMISSION"
                                });
                            }
                        }
                        else {
                            const keyRes = await fetch(`/api/files/${item.id}/key`, { headers: API.getAuthHeader() });
                            const { encryptedFileKey } = await keyRes.json();

                            for (const targetUser of usersToAdd) {
                                const pubKeyJwk = recipientKeys[targetUser];
                                if (!pubKeyJwk) continue;

                                const wrappedKey = await CryptoService.reWrapKeyForUser(encryptedFileKey, pubKeyJwk);

                                batchRequests.push({
                                    fileId: item.id,
                                    targetUsername: targetUser,
                                    encryptedKey: wrappedKey
                                });
                            }
                        }
                    }
                };

                const workers = Array(Math.min(CONCURRENCY_LIMIT, queue.length)).fill(null).map(() => processQueue());
                await Promise.all(workers);

                // --- ENVÍO ÚNICO ---
                if (batchRequests.length > 0) {
                    this.status = "Enviando lote al servidor...";
                    await fetch('/api/files/share/batch', {
                        method: 'POST',
                        headers: { ...API.getAuthHeader(), 'Content-Type': 'application/json' },
                        body: JSON.stringify(batchRequests)
                    });
                }
            }

            this.showInfo("Permisos actualizados correctamente");
            this.closeShareModal();
            await this.refreshAppData();
        } catch (e) {
            console.error(e);
            this.showError("Error al compartir: " + e.message);
        } finally {
            this.shareModal.isProcessing = false;
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