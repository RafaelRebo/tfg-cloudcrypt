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
        // Buscamos si ya existe la notificación de subida
        let uploadToast = context.notifications.find(n => n.isUpload);

        // Si no existe y hay progreso, la creamos (Persistente, sin auto-dismiss)
        if (!uploadToast && context.uploadProgress > 0 && context.uploadProgress < 100) {
            uploadToast = {
                id: 'upload-process', // ID fijo
                isUpload: true, // Marca especial
                type: 'upload', // Clase CSS
                leaving: false
            };
            // Usamos unshift para que aparezca arriba/primero si quieres
            context.notifications.push(uploadToast);
        }

        // Si la subida ha terminado (o se ha abortado), la eliminamos inmediatamente
        if (context.uploadProgress === 0 || context.uploadProgress === 100) {
            this.animateOut('upload-process', context.notifications, true); // true = sin delay
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