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

    openMassShareModal() {
        if (this.selectedIds.length === 0) return;

        // Configuramos los metadatos visuales del modal para modo masivo
        this.shareModal.fileId = null; // Ponemos null para indicar al ejecutor que use selectedIds
        this.shareModal.fileName = `${this.selectedIds.length} elementos seleccionados`;
        this.shareModal.isFolder = false; // El tratamiento batch procesará cada item de forma independiente
        this.shareModal.selectedUsers = [];
        this.shareModal.searchQuery = '';
        this.shareModal.searchResults = [];

        this.shareModal.active = true;
    },

    async executeShare() {
        this.shareModal.isProcessing = true;
        this.status = "Calculando árbol de claves masivo...";

        try {
            // Determinamos la lista de trabajo: o el archivo individual del modal, o la selección de la barra
            const targetsWorklist = this.shareModal.fileId ? [this.shareModal.fileId] : [...this.selectedIds];
            let flatItemsToShare = [];

            // FASE 1: Recolección y aplanado estructural (Aplanamos carpetas a ficheros si los hubiera)
            for (const id of targetsWorklist) {
                const currentFile = this.allUserFiles.find(f => f.id === id);
                if (!currentFile) continue;

                if (currentFile.fileType === 'application/x-directory') {
                    // Si es carpeta, traemos recursivamente sus hijos de la API
                    const res = await fetch(`/api/files/folder-content-recursive/${id}`, { headers: API.getAuthHeader() });
                    const children = await res.json();
                    flatItemsToShare.push({ id, fileType: 'application/x-directory' });
                    flatItemsToShare.push(...children);
                } else {
                    flatItemsToShare.push({ id, fileType: currentFile.fileType });
                }
            }

            // FASE 2: Homogeneización (Revocación total previa sobre la lista de trabajo)
            this.status = "Homogeneizando listas de control de acceso...";
            for (const itemId of targetsWorklist) {
                const currentRes = await fetch(`/api/files/${itemId}/shared-users`, { headers: API.getAuthHeader() });
                const originalUsers = await currentRes.json();

                // Revocamos absolutamente a todos los que estuvieran antes para aplicar la "Lista de Oro" unificada
                for (const userToken of originalUsers) {
                    const targetName = typeof userToken === 'object' ? userToken.username : userToken;
                    await fetch(`/api/files/${itemId}/share/revoke?target=${encodeURIComponent(targetName)}`, {
                        method: 'DELETE', headers: API.getAuthHeader()
                    });
                }
            }

            // FASE 3: Re-encriptación asimétrica y empaquetado masivo
            if (this.shareModal.selectedUsers.length > 0) {
                this.status = "Descifrando y re-envolviendo sobres digitales...";

                // Descargamos las claves públicas RSA de los destinatarios elegidos
                const recipientKeys = {};
                for (const user of this.shareModal.selectedUsers) {
                    const data = await API.getUserPublicKey(user);
                    if (data) recipientKeys[user] = data.publicKey;
                }

                const batchRequests = [];

                // Procesamos criptográficamente cada archivo aplanado
                for (const item of flatItemsToShare) {
                    if (item.fileType === 'application/x-directory') {
                        // Las carpetas no llevan clave real en tu arquitectura, solo banderas de visibilidad
                        for (const targetUser of this.shareModal.selectedUsers) {
                            batchRequests.push({
                                fileId: item.id, targetUsername: targetUser, encryptedKey: "FOLDER_PERMISSION"
                            });
                        }
                    } else {
                        // Fichero: traemos su sobre criptográfico actual
                        const keyRes = await fetch(`/api/files/${item.id}/key`, { headers: API.getAuthHeader() });
                        const { encryptedFileKey } = await keyRes.json();

                        for (const targetUser of this.shareModal.selectedUsers) {
                            const pubKeyJwk = recipientKeys[targetUser];
                            if (!pubKeyJwk) continue;

                            // El Worker re-envuelve la clave simétrica AES con la RSA pública del nuevo dueño
                            const wrappedKey = await CryptoService.reWrapKeyForUser(encryptedFileKey, pubKeyJwk);

                            batchRequests.push({
                                fileId: item.id, targetUsername: targetUser, encryptedKey: wrappedKey
                            });
                        }
                    }
                }

                // FASE 4: Envío unificado al backend en un solo lote masivo
                if (batchRequests.length > 0) {
                    this.status = "Confirmando transacciones en el servidor...";
                    await fetch('/api/files/share/batch', {
                        method: 'POST',
                        headers: { ...API.getAuthHeader(), 'Content-Type': 'application/json' },
                        body: JSON.stringify(batchRequests)
                    });
                }
            }

            this.showInfo("Permisos y sobres criptográficos actualizados en masa.");
            this.closeShareModal();
            this.clearSelection();
            await this.refreshAppData();

        } catch (e) {
            console.error(e);
            this.showError("Fallo en la operación masiva: " + e.message);
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