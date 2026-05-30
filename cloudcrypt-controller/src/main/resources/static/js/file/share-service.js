const AppShareMethods = {
    async openShareModal(file) {
        this.shareModal.fileId = file.id;
        this.shareModal.fileName = file.fileName;
        this.shareModal.isFolder = file.fileType === 'application/x-directory';
        this.shareModal.searchQuery = '';
        this.shareModal.selectedUsers = []; // Inicializamos como array de cadenas (Strings) plano
        this.shareModal.searchResults = [];
        this.shareModal.active = true;

        try {
            const sharedUsernames = await API.getSharedUsers(file.id);
            this.shareModal.selectedUsers = sharedUsernames;
        } catch (e) {
            console.error("Error al recuperar los accesos iniciales:", e);
        }

        this.onUserSearchInput();
    },

    async handleCheckboxToggle(username) {
        const isChecked = this.shareModal.selectedUsers.includes(username);

        if (!isChecked) {
            try {
                this.status = `Revocando acceso a @${username}...`;

                const res = await fetch(`/api/files/${this.shareModal.fileId}/share/revoke?target=${username}`, {
                    method: 'DELETE',
                    headers: API.getAuthHeader()
                });

                if (res.ok) {
                    this.showInfo(`Acceso revocado correctamente a ${username}`);
                } else {
                    this.shareModal.selectedUsers.push(username);
                    this.showError("El servidor denegó la revocación del acceso.");
                }
            } catch (e) {
                this.shareModal.selectedUsers.push(username);
                this.showError("Error de comunicación al revocar privilegios.");
            } finally {
                this.status = "";
            }
        }
    },

    closeShareModal() {
        this.shareModal.active = false;
    },

    removeUserFromShare(user) {
        this.shareModal.selectedUsers = this.shareModal.selectedUsers.filter(u => u.username != user.username);
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

        this.onUserSearchInput();
    },

    async executeShare() {
        if (this.shareModal.selectedUsers.length === 0) {
            this.closeShareModal();
            await this.refreshAppData();
            return;
        }

        this.shareModal.isProcessing = true;
        this.status = "Analizando dependencias...";

        try {
            let fileIdsToShare = [];

            if (this.shareModal.isFolder) {
                const recursiveRes = await API.getRecursiveContent(this.shareModal.fileId);
                if (!recursiveRes.ok) throw new Error("Fallo al escanear la estructura de la carpeta.");
                const children = await recursiveRes.json();
                fileIdsToShare = children.map(f => f.id);
                fileIdsToShare.push(this.shareModal.fileId);
            } else {
                fileIdsToShare = [this.shareModal.fileId];
            }

            fileIdsToShare = [...new Set(fileIdsToShare)];

            this.status = `Descargando claves del propietario (${fileIdsToShare.length} unidades)...`;

            const batchKeysRes = await API.getFileKeysBatch(fileIdsToShare);
            if (!batchKeysRes.ok) throw new Error("El búnker de CloudCrypt rechazó la descarga en bloque de las llaves raíz.");
            const ownerKeysMap = await batchKeysRes.json();

            const sharePayload = [];
            const targetUsers = this.shareModal.selectedUsers;

            for (const targetUser of targetUsers) {
                const targetUsername = targetUser.username || targetUser;

                this.status = `Descargando clave de de @${targetUsername}...`;

                const pubKeyData = await API.getUserPublicKey(targetUsername);
                if (!pubKeyData || !pubKeyData.publicKey) {
                    throw new Error(`No se pudo obtener la clave pública del usuario @${targetUsername}`);
                }

                this.status = `Cifrando archivos para @${targetUsername}...`;

                for (const fileId of fileIdsToShare) {
                    const ownerEncryptedKey = ownerKeysMap[fileId];
                    if (!ownerEncryptedKey) continue;

                    let rewrappedKeyBase64 = ownerEncryptedKey;
                    if (ownerEncryptedKey !== "FOLDER_PERMISSION") {
                        rewrappedKeyBase64 = await CryptoService.reWrapKeyForUser(ownerEncryptedKey, pubKeyData.publicKey);
                    }

                    sharePayload.push({
                        fileId: fileId,
                        targetUsername: targetUsername,
                        encryptedKey: rewrappedKeyBase64
                    });
                }
            }

            if (sharePayload.length === 0) {
                throw new Error("No hay llaves válidas para procesar en la transacción.");
            }

            this.status = "Transmitiendo compartición al servidor...";

            const saveRes = await API.shareFilesBatch(sharePayload);
            if (!saveRes.ok) throw new Error("El servidor rechazó el lote de compartición masivo.");

            this.showInfo("¡Recurso compartido con éxito!");
            this.closeShareModal();
            await this.refreshAppData();

        } catch (e) {
            this.showError(e.message || "Fallo en la gestión de accesos compartidos.");
        } finally {
            this.shareModal.isProcessing = false;
            this.status = "";
        }
    },

    async onUserSearchInput() {
        try {
            const users = await API.searchUsers(this.shareModal.searchQuery || '');
            this.shareModal.searchResults = users;
        } catch (e) {
            console.error("Error en el filtro dinámico de usuarios:", e);
        }
    },

    selectUser(user) {
        if (!this.shareModal.selectedUsers.includes(user.username)) {
            this.shareModal.selectedUsers.push(user.username);
        }
        this.shareModal.searchQuery = '';
        this.shareModal.searchResults = [];
    },

    async revokeUserAccessImmediately(username) {
        if (!this.shareModal.fileId) return;

        this.shareModal.isProcessing = true;
        this.status = `Revocando acceso a ${username}...`;

        try {
            const res = await fetch(`/api/files/${this.shareModal.fileId}/share/revoke?target=${username}`, {
                method: 'DELETE',
                headers: API.getAuthHeader()
            });

            if (res.ok) {
                this.shareModal.selectedUsers = this.shareModal.selectedUsers.filter(u => u.username !== username);
                this.showInfo(`Acceso revocado a ${username}`);
            } else {
                throw new Error("El servidor rechazó la revocación.");
            }
        } catch (e) {
            this.showError(e.message || "No se pudo revocar el acceso.");
        } finally {
            this.shareModal.isProcessing = false;
            this.status = "";
        }
    }
};