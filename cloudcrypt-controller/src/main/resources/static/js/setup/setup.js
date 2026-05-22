const { createApp } = Vue;

createApp({
    data() {
        return {
            currentStep: 1,
            loading: false,
            dbTesting: false,

            message: 'Configura las credenciales de acceso a tu servidor de MySQL',
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
                    this.message = 'Configura las credenciales de acceso a tu servidor de MySQL';
                    break;
                case 2:
                    this.message = 'Define el directorio de almacenamiento físico y cuotas por usuario.';
                    break;
                case 3:
                    this.message = 'Configura los algoritmos de cifrado y verificación de integridad.';
                    break;
                case 4:
                    this.message = 'Configura las credenciales del usuario administrador predeterminado.';
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
            this.message = 'Validando conexión con MySQL...';
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
                this.message = 'Error de comunicación con el servidor';
                this.messageType = 'error';
            } finally {
                this.dbTesting = false;
            }
        },

        async submitSetup() {
            if (!this.form.adminUsername.trim()) {
                this.message = 'El ID de usuario es obligatorio';
                this.messageType = 'error';
                return;
            }
            if (!this.form.adminPassword || !this.adminConfirmPassword) {
                this.message = 'Debes rellenar y confirmar la contraseña.';
                this.messageType = 'error';
                return;
            }
            if (this.form.adminPassword !== this.adminConfirmPassword) {
                this.message = 'Las contraseñas no coinciden';
                this.messageType = 'error';
                return;
            }
            if (!this.form.adminFullName.trim() || !this.form.adminEmail.trim()) {
                this.message = 'Debes rellenar el nombre completo y el correo';
                this.messageType = 'error';
                return;
            }

            this.loading = true;
            this.message = 'Escribiendo parámetros...';
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

                    let seconds = 5;
                    const interval = setInterval(() => {
                        seconds--;
                        if (seconds <= 0) {
                            clearInterval(interval);
                            window.location.href = '/';
                        } else {
                            this.message = `Instalación finalizada... Apagando en ${seconds}s.`;
                        }
                    }, 1000);

                } else {
                    this.message = text;
                    this.messageType = 'error';
                    this.loading = false;
                }
            } catch (e) {
                this.message = 'Error de conexión de red.';
                this.messageType = 'error';
                this.loading = false;
            }
        }
    }
}).mount('#app');