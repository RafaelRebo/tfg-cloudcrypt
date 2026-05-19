const { createApp } = Vue;

createApp({
    data() {
        return {
            currentStep: 1,
            loading: false,
            dbTesting: false,

            message: 'Configure el socket TCP y las credenciales de enlace para su motor MySQL.',
            messageType: 'info',

            adminConfirmPassword: '',

            uiQuotaValue: 100,
            uiQuotaUnit: 'MB',
            uiFileSizeValue: 2,
            uiFileSizeUnit: 'GB',

            form: {
                dbHost: 'localhost',
                dbPort: '3306',
                dbName: 'cloudcrypt_db',
                dbUser: 'root',
                dbPass: 'root',
                uploadDir: 'uploads',
                maxQuotaBytes: '',
                maxFileSizeGb: '',
                hashAlgo: 'SHA-256',
                symAlgo: 'AES/GCM/NoPadding',
                asymKeySize: '2048',
                saltSuffix: '-cloudcrypt',
                adminUsername: 'admin',
                adminPassword: '',
                adminFullName: '',
                adminEmail: ''
            }
        }
    },
    methods: {
        updateStepMessage() {
            if (this.loading) return;

            this.messageType = 'info';
            switch(this.currentStep) {
                case 1:
                    this.message = 'Configure el socket TCP y las credenciales de enlace para su motor MySQL.';
                    break;
                case 2:
                    this.message = 'Defina las capacidades físicas del disco del servidor y los límites per-user.';
                    break;
                case 3:
                    this.message = 'Sintonice la gobernanza criptográfica global que blindará los Web Workers.';
                    break;
                case 4:
                    this.message = 'Establezca la identidad raíz del Administrador Maestro del ecosistema.';
                    break;
            }
        },
        setStep(step) {
            if (!this.loading) {
                this.currentStep = step;
                this.updateStepMessage();
            }
        },
        nextStep() {
            if (this.currentStep < 4) {
                this.currentStep++;
                this.updateStepMessage();
            }
        },
        prevStep() {
            if (this.currentStep > 1) {
                this.currentStep--;
                this.updateStepMessage();
            }
        },


        async testDatabase() {
            this.dbTesting = true;
            this.message = 'Validando conector JDBC y privilegios de esquema con MySQL...';
            this.messageType = 'info';

            try {
                const res = await fetch('/api/setup/test-db', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(this.form)
                });

                const text = await res.text();
                if (res.ok) {
                    this.message = text;
                    this.messageType = 'success';
                } else {
                    this.message = text;
                    this.messageType = 'error';
                }
            } catch (e) {
                this.message = 'Error de infraestructura: No hay comunicación con el backend de Spring.';
                this.messageType = 'error';
            } finally {
                this.dbTesting = false;
            }
        },

        async submitSetup() {
            if (!this.form.adminUsername.trim()) {
                this.message = 'Error: El Nombre de Usuario (User ID) es obligatorio.';
                this.messageType = 'error';
                return;
            }
            if (!this.form.adminPassword || !this.adminConfirmPassword) {
                this.message = 'Error: Debe rellenar la contraseña y su confirmación.';
                this.messageType = 'error';
                return;
            }
            if (this.form.adminPassword !== this.adminConfirmPassword) {
                this.message = 'Error: Las contraseñas del administrador no coinciden.';
                this.messageType = 'error';
                return;
            }
            if (!this.form.adminFullName.trim() || !this.form.adminEmail.trim()) {
                this.message = 'Error: El nombre completo y el correo del administrador son obligatorios.';
                this.messageType = 'error';
                return;
            }

            this.loading = true;
            this.message = 'Escribiendo propiedades persistentes y levantando entropía para JWT...';
            this.messageType = 'info';

            const bytesInMb = 1024 * 1024;
            const bytesInGb = 1024 * 1024 * 1024;

            this.form.maxQuotaBytes = String(
                this.uiQuotaUnit === 'GB' ? this.uiQuotaValue * bytesInGb : this.uiQuotaValue * bytesInMb
            );
            this.form.maxFileSizeGb = String(
                this.uiFileSizeUnit === 'MB' ? (this.uiFileSizeValue / 1024).toFixed(3) : this.uiFileSizeValue
            );

            try {
                const formData = new FormData();

                Object.keys(this.form).forEach(key => {
                    formData.append(key, this.form[key]);
                });

                const avatarInput = this.$refs.avatarInput;
                if (avatarInput && avatarInput.files[0]) {
                    formData.append("avatar", avatarInput.files[0]);
                }

                const res = await fetch('/api/setup/submit', {
                    method: 'POST',
                    body: formData
                });

                const text = await res.text();
                if (res.ok) {
                    this.message = text;
                    this.messageType = 'success';

                    let seconds = 8;
                    const interval = setInterval(() => {
                        seconds--;
                        if (seconds <= 0) {
                            clearInterval(interval);
                            window.location.href = '/';
                        } else {
                            this.message = `Aprovisionamiento completado con éxito. Reiniciando servidor físico de Spring Boot... Redirigiendo en ${seconds}s.`;
                        }
                    }, 1000);

                } else {
                    this.message = text;
                    this.messageType = 'error';
                    this.loading = false;
                }
            } catch (e) {
                this.message = 'Error crítico: Caída de la conexión de red durante la transmisión.';
                this.messageType = 'error';
                this.loading = false;
            }
        }
    }
}).mount('#app');