const AppShareMethods = {
    async openShareModal(file) {
        this.shareModal.fileId = file.id;
        this.shareModal.fileName = file.fileName;
        this.shareModal.isFolder = file.fileType === 'application/x-directory';
        this.shareModal.searchQuery = '';
        this.shareModal.selectedUsers = [];
        this.shareModal.searchResults = [];
        this.shareModal.active = true;

        this.onUserSearchInput();
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

        this.onUserSearchInput();
    },

    async executeShare() {
        this.shareModal.isProcessing = true;
        this.status = "Analizando dependencias y jerarquías lógicas...";

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

            this.status = `Descargando paquete masivo de llaves del propietario (${fileIdsToShare.length} unidades)...`;

            const batchKeysRes = await API.getFileKeysBatch(fileIdsToShare);
            if (!batchKeysRes.ok) throw new Error("El búnker de CloudCrypt rechazó la descarga en bloque de las llaves raíz.");
            const ownerKeysMap = await batchKeysRes.json();

            const sharePayload = [];
            const targetUsers = this.shareModal.selectedUsers;

            for (const targetUser of targetUsers) {
                const targetUsername = targetUser.username || targetUser;

                this.status = `Descargando credencial pública de @${targetUsername}...`;

                const pubKeyData = await API.getUserPublicKey(targetUsername);
                if (!pubKeyData || !pubKeyData.publicKey) {
                    throw new Error(`No se pudo obtener la clave pública del usuario @${targetUsername}`);
                }

                this.status = `Cifrando sobres digitales en Web Worker para @${targetUsername}...`;

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

            this.status = "Transmitiendo transacción masiva de gobernanza al búnker...";

            const saveRes = await API.shareFilesBatch(sharePayload);
            if (!saveRes.ok) throw new Error("El servidor rechazó el lote de compartición masivo.");

            this.showInfo("¡Recurso compartido con éxito!");
            this.closeShareModal();
            await this.refreshAppData();

        } catch (e) {
            this.showError(e.message || "Fallo crítico en la delegación de accesos compartidos.");
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
        if (!this.shareModal.selectedUsers.includes(user)) {
            this.shareModal.selectedUsers.push(user);
        }
        this.shareModal.searchQuery = '';
        this.shareModal.searchResults = [];
    }
};