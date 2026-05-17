const NotificationService = {
    // Toasts normales (Error/Info)
    create(msg, notifications, type = 'error') {
        const id = Date.now();
        const icon = type === 'error' ? '✕' : 'ℹ';

        // Evitamos saturar la pantalla
        if (notifications.filter(n => !n.isUpload).length >= 3) {
            const oldest = notifications.find(n => !n.leaving && !n.isUpload);
            if (oldest) this.animateOut(oldest.id, notifications);
        }

        notifications.push({
            id, message: msg, type: type, icon: icon, isUpload: false, leaving: false
        });

        setTimeout(() => this.animateOut(id, notifications), 4500);
    },

    // --- NUEVO: Crea y actualiza la notificación de subida ---
    updateUploadProgress(context) {
        let uploadToast = context.notifications.find(n => n.isUpload);

        // CAMBIO: Permitimos que se cree desde el 1% para cubrir la fase de cifrado
        if (!uploadToast && context.uploadProgress > 0 && context.uploadProgress < 100) {
            uploadToast = {
                id: 'upload-process',
                isUpload: true,
                type: 'upload',
                leaving: false
            };
            context.notifications.push(uploadToast);
        }

        if (context.uploadProgress >= 100) {
            // Damos un segundo para que el usuario vea el "100%" antes de borrar
            setTimeout(() => this.animateOut('upload-process', context.notifications, true), 1000);
        }

        if (context.uploadProgress === 0) {
            this.animateOut('upload-process', context.notifications, true);
        }
    },

    animateOut(id, notifications, immediate = false) {
        const toast = notifications.find(n => n.id === id);
        if (toast && !toast.leaving) {
            toast.leaving = true;
            setTimeout(() => {
                const index = notifications.findIndex(n => n.id === id);
                if (index > -1) notifications.splice(index, 1);
            }, immediate ? 0 : 500); // Borrado inmediato o con animación
        }
    }
};

const AppNotificationMethods = {
    showInfo(msg) {
        NotificationService.create(msg, this.notifications, 'info');
    },

    showError(msg) {
        NotificationService.create(msg, this.notifications, 'error');
    },

    removeNotification(id) {
        NotificationService.animateOut(id, this.notifications);
    },

    askConfirmation(msg, isDestructive = false) {
        return new Promise((resolve) => {
            this.confirmModal = {
                active: true,
                isDuplicateMode: false,
                isInput: false,
                isDestructive: isDestructive,
                message: msg,
                onConfirm: () => { this.confirmModal.active = false; resolve(true); },
                onCancel: () => { this.confirmModal.active = false; resolve(false); }
            };
        });
    }
};