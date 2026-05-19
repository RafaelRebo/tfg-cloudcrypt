const AppUserMethods = {
    openProfileModal() {
        this.profileModal = {
            active: true,
            activeTab: 1,
            isProcessing: false,
            fullName: this.userFullName,
            email: localStorage.getItem('email') || '',
            newUsername: this.username,
            newPassword: '',
            confirmNewPassword: '',
            currentPassword: '',
            removeAvatar: false,
            adminSearchQuery: '',
            adminStats: { globalUsedBytes: 0, users: [] }
        };
    },

    async setProfileTab(tab) {
        this.profileModal.activeTab = tab;

        if (tab === 3) {
            this.status = "Recuperando auditoría de almacenamiento global...";
            try {
                const stats = await API.getAdminStorageStats();

                // ⚡ SINTONIZACIÓN INTELIGENTE: Evaluamos la unidad óptima por defecto
                stats.users.forEach(u => {
                    const baseBytes = u.quotaBytes || this.stats.maxQuota;
                    const bytesInGb = 1024 * 1024 * 1024;
                    const bytesInMb = 1024 * 1024;

                    // Si es un número redondo divisible por un Gigabyte, lo ponemos en GB
                    if (baseBytes >= bytesInGb && baseBytes % bytesInGb === 0) {
                        u.uiQuotaValue = Math.floor(baseBytes / bytesInGb);
                        u.uiQuotaUnit = 'GB';
                    } else {
                        u.uiQuotaValue = Math.floor(baseBytes / bytesInMb);
                        u.uiQuotaUnit = 'MB';
                    }
                });

                this.profileModal.adminStats = stats;
            } catch (e) {
                this.showError("No tienes privilegios suficientes para inspeccionar el almacenamiento.");
                this.profileModal.activeTab = 1;
            } finally {
                this.status = "";
            }
        }
    },

    // ⚡ NUEVO: Procesador masivo unificado (Batch Process) abajo a la derecha
    async executeBatchAdminUpdate() {
        this.profileModal.isProcessing = true;
        this.status = "Iniciando transacciones de gobernanza en bloque...";

        try {
            // Excluimos la fila propia del administrador para evitar la auto-revocación accidental
            const filterUsers = this.profileModal.adminStats.users.filter(u => u.username !== this.username);
            const taskWorklist = [];

            for (const userStat of filterUsers) {
                if (userStat.uiQuotaValue <= 0) {
                    throw new Error(`La cuota para el usuario ${userStat.username} debe ser mayor que 0.`);
                }

                // Traducimos el par elástico (Valor + Unidad) a bytes puros antes de mandarlo a Spring
                const multiplier = userStat.uiQuotaUnit === 'GB' ? (1024 * 1024 * 1024) : (1024 * 1024);
                const totalBytes = userStat.uiQuotaValue * multiplier;

                // Empaquetamos la promesa en caliente en nuestro pool concurrente
                taskWorklist.push((async () => {
                    const res = await API.updateUserParameters(userStat.userId, totalBytes, userStat.role);
                    if (!res.ok) {
                        throw new Error(`Error al procesar a @${userStat.username}: ` + await API.extractErrorMessage(res));
                    }
                })());
            }

            // Disparamos todas las peticiones HTTP en paralelo sobre el conector de Tomcat
            if (taskWorklist.length > 0) {
                await Promise.all(taskWorklist);
            }

            this.showInfo("¡Gobernanza global actualizada e instalada con éxito!");
            this.profileModal.active = false;
            await this.refreshAppData();

        } catch (e) {
            this.showError(e.message || "Fallo al consolidar el lote de políticas.");
        } finally {
            this.profileModal.isProcessing = false;
            this.status = "";
        }
    },

    async executeProfileUpdate() {
        if (!this.profileModal.fullName.trim() || !this.profileModal.newUsername.trim()) {
            this.showError("El nombre y tu ID de usuario son obligatorios.");
            return;
        }

        const passwordAreaTouched = this.profileModal.newPassword || this.profileModal.confirmNewPassword;
        const credentialsChanged = this.profileModal.newUsername !== this.username || passwordAreaTouched;

        if (credentialsChanged) {
            if (passwordAreaTouched && this.profileModal.newPassword !== this.profileModal.confirmNewPassword) {
                this.showError("Las nuevas contraseñas introducidas no coinciden.");
                return;
            }
            if (!this.profileModal.currentPassword) {
                this.showError("Introduce tu contraseña actual para autorizar la rotación de claves.");
                return;
            }
        }

        this.profileModal.isProcessing = true;
        this.status = "Iniciando protocolo de actualización segura...";

        try {
            const formData = new FormData();
            formData.append("fullName", this.profileModal.fullName);
            formData.append("email", this.profileModal.email);
            formData.append("removeAvatar", this.profileModal.removeAvatar);

            const avatarFile = this.$refs.profileAvatarInput ? this.$refs.profileAvatarInput.files[0] : null;
            if (avatarFile) formData.append("avatar", avatarFile);

            let newMasterKey = sessionStorage.getItem('fileKey');

            if (credentialsChanged) {
                this.status = "Derivando parámetros de seguridad actuales...";
                const currentValidationKey = await AuthService.deriveMasterKey(this.username, this.profileModal.currentPassword);

                if (currentValidationKey !== sessionStorage.getItem('fileKey')) {
                    throw new Error("La contraseña actual introducida es incorrecta.");
                }

                this.status = "Calculando nueva Master Key corporativa...";
                const updatedClearPassword = this.profileModal.newPassword || this.profileModal.currentPassword;
                newMasterKey = await AuthService.deriveMasterKey(this.profileModal.newUsername, updatedClearPassword);

                this.status = "Recifrando clave privada RSA en el Web Worker...";
                const { reEncryptedPrivateKeyBase64 } = await CryptoService.rotateIdentityKeys(newMasterKey, this.profileModal.newUsername);

                formData.append("newUsername", this.profileModal.newUsername);
                formData.append("newPassword", newMasterKey);
                formData.append("newEncryptedPrivateKey", reEncryptedPrivateKeyBase64);
            }

            this.status = "Transmitiendo cambios estructurales al búnker...";
            const res = await API.updateProfile(formData);

            if (res.ok) {
                const data = await res.json();

                this.username = data.username;
                this.userFullName = data.fullName;
                this.userAvatarUrl = data.avatarUrl || '';
                this.userEmail = data.email || '';

                localStorage.setItem('jwtToken', data.token);
                localStorage.setItem('username', data.username);
                localStorage.setItem('fullName', data.fullName || '');
                localStorage.setItem('avatarUrl', data.avatarUrl || '');
                localStorage.setItem('email', data.email || '');
                sessionStorage.setItem('fileKey', newMasterKey);

                this.showInfo("¡Identidad y credenciales actualizadas con éxito!");
                this.profileModal.active = false;
                await this.refreshAppData();
            } else {
                throw new Error(await API.extractErrorMessage(res));
            }

        } catch (e) {
            this.showError(e.message || "Fallo crítico en la rotación de identidades.");
        } finally {
            this.profileModal.isProcessing = false;
            this.status = "";
        }
    },

    filteredAdminUsers() {
        const query = this.profileModal.adminSearchQuery ? this.profileModal.adminSearchQuery.trim().toLowerCase() : '';
        if (!this.profileModal.adminStats || !this.profileModal.adminStats.users) {
            return [];
        }
        if (!query) {
            return this.profileModal.adminStats.users;
        }

        // Filtra reactivamente en caliente buscando coincidencias por Nombre Completo o Username
        return this.profileModal.adminStats.users.filter(u => {
            const nameMatch = u.fullName ? u.fullName.toLowerCase().includes(query) : false;
            const userMatch = u.username ? u.username.toLowerCase().includes(query) : false;
            return nameMatch || userMatch;
        });
    },

    promptDeleteUser(userStat) {
        this.confirmModal = {
            active: true,
            title: `¿Purgar al usuario ${userStat.username}?`,
            message: `Esta acción es irreversible y destructiva. Se eliminarán de forma física todos sus ficheros cifrados del disco del servidor, su historial relacional, su avatar corporativo y su llavero de claves asimétricas. El usuario dejará de existir.`,
            isInput: false,
            buttonText: "Eliminar Todo",
            isDestructive: true,
            onConfirm: async () => {
                this.confirmModal.active = false;
                await this.executeDeleteUser(userStat.userId, userStat.username);
            },
            onCancel: () => {
                this.confirmModal.active = false;
            }
        };
    },

    async executeDeleteUser(userId, targetUsername) {
        this.status = `Ejecutando purga criptográfica y física de ${targetUsername}...`;
        try {
            const res = await API.deleteUser(userId);
            if (res.ok) {
                this.showInfo(`El usuario @${targetUsername} y toda su infraestructura han sido eliminados.`);
                // Recargamos la pestaña de administración para refrescar la lista y el tamaño global ocupado
                await this.setProfileTab(3);
            } else {
                throw new Error(await API.extractErrorMessage(res));
            }
        } catch (e) {
            this.showError(e.message || "Fallo al purgar el usuario del sistema.");
        } finally {
            this.status = "";
        }
    },
};