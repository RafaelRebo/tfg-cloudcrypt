const AuthService = {
    async login(username, password) {
        const res = await API.login(username, password);
        if (res.ok) {
            const data = await res.json();
            localStorage.setItem('jwtToken', data.token);
            localStorage.setItem('username', data.username);
            localStorage.setItem('fullName', data.fullName || '');
            localStorage.setItem('avatarUrl', data.avatarUrl || '');
            sessionStorage.setItem('fileKey', password);

            try {
                // Intentamos recuperar las llaves del servidor
                const encryptedPrivKey = await API.getMyPrivateKey();
                const pubKeyData = await API.getUserPublicKey(username);

                if (encryptedPrivKey && pubKeyData && pubKeyData.publicKey) {
                    // Caso normal: El llavero existe, lo cargamos en el Worker
                    await CryptoService.initializeIdentity(encryptedPrivKey, pubKeyData.publicKey, password, username);
                } else {
                    // Caso Admin inicial: Autogeneración limpia en primer login
                    console.warn("Llavero no encontrado. Inicializando auto-aprovisionamiento Zero-Knowledge...");
                    await this.setupUserCrypto(username, password);
                }
            } catch (e) {
                // Si la API da un error de recurso no encontrado (404), disparamos la creación
                try {
                    await this.setupUserCrypto(username, password);
                } catch (err) {
                    console.error("Fallo crítico al inicializar la identidad en el Worker:", err);
                }
            }
            return true;
        }
        return false;
    },

    // --- CORRECCIÓN CRÍTICA: Des-silenciar errores del servidor ---
    async setupUserCrypto(username, password) {
        const cryptoPackage = await CryptoService.generateAndPackageKeys(password, username);

        // Esperamos la respuesta del fetch
        const res = await API.registerUserKeys(cryptoPackage.publicKeyStr, cryptoPackage.encryptedPrivateKeyBase64);

        // Si el controlador de Spring Boot da un error, lo lanzamos para que vaya al catch de la UI
        if (!res.ok) {
            const serverErrorText = await res.text();
            throw new Error(`El servidor rechazó las llaves (Código ${res.status}): ${serverErrorText}`);
        }

        return res;
    },

    logout() {
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('username');
        localStorage.removeItem('fullName');
        localStorage.removeItem('avatarUrl');
        sessionStorage.removeItem('fileKey');
    },

    getSavedSession() {
        const token = localStorage.getItem('jwtToken');
        const username = localStorage.getItem('username');
        const password = sessionStorage.getItem('fileKey');
        return (token && password && username) ? { token, username, password } : null;
    },

    async deriveMasterKey(username, password) {
        const encoder = new TextEncoder();
        const data = encoder.encode(username.toLowerCase() + password);

        // ⚡ SOLUCIÓN: Usamos el hash global sintonizado dinámicamente en lugar de 'SHA-256' estático
        const targetHash = (window.CryptoSpecs && window.CryptoSpecs.hashAlgo) || 'SHA-256';
        const hashBuffer = await crypto.subtle.digest(targetHash, data);

        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }
};


const AppAuthMethods = {
    async handleLogin() {
        try {
            const secureKey = await AuthService.deriveMasterKey(this.username, this.password);
            const res = await API.login(this.username, secureKey);

            if (res.ok) {
                const data = await res.json();
                localStorage.setItem('jwtToken', data.token);
                localStorage.setItem('username', data.username);
                localStorage.setItem('fullName', data.fullName || '');
                localStorage.setItem('avatarUrl', data.avatarUrl || '');
                sessionStorage.setItem('fileKey', secureKey);

                this.userFullName = data.fullName;
                this.userAvatarUrl = data.avatarUrl;

                let hasCrypto = false;
                try {
                    const encryptedPrivKey = await API.getMyPrivateKey();
                    const pubKeyData = await API.getUserPublicKey(this.username);
                    if (encryptedPrivKey && pubKeyData && pubKeyData.publicKey) {
                        await CryptoService.initializeIdentity(encryptedPrivKey, pubKeyData.publicKey, secureKey, this.username);
                        hasCrypto = true;
                    }
                } catch (cryptoErr) {
                    console.warn("Llavero asimétrico ausente en el servidor. Preparando aprovisionamiento en caliente...");
                }

                // ⚡ INTERCEPCIÓN MAESTRA: Si no tiene llaves asignadas, las forzamos en caliente
                if (!hasCrypto) {
                    this.status = "Generando llavero criptográfico de autoridad raíz...";
                    try {
                        await AuthService.setupUserCrypto(this.username, secureKey);
                        this.showInfo("¡Llavero de Administrador aprovisionado y guardado con éxito!");
                    } catch (setupErr) {
                        console.error("Error en el auto-setup del admin:", setupErr);
                        this.showError("No se pudo firmar la gobernanza criptográfica del administrador.");
                    } finally {
                        this.status = "";
                    }
                }

                this.password = '';
                this.loginError = false;
                this.isLoggedIn = true;
                await this.refreshAppData();
            } else {
                this.loginError = true;
                const errorMsg = await API.extractErrorMessage(res);
                this.showError(errorMsg);
            }
        } catch (e) {
            this.showError("Error al iniciar la pasarela de sesión segura.");
        }
    },

    // ⚡ NUEVO MÉTODO: Gestiona el flujo de registro avanzado con avatares físicos
    async executeRegister() {
        try {
            // 1. Validaciones de interfaz
            if (!this.regFullName || !this.regEmail || !this.regUsername || !this.regPassword) {
                this.showError("Por favor, rellena todos los campos obligatorios.");
                return;
            }
            if (this.regPassword !== this.regConfirmPassword) {
                this.showError("Las contraseñas introducidas no coinciden.");
                return;
            }
            if (!this.regAcceptZk) {
                this.showError("Debes aceptar el aviso de responsabilidad criptográfica.");
                return;
            }

            this.status = "Derivando claves criptográficas de seguridad...";

            // 2. Mantenemos el blindaje Zero-Knowledge derivando la Master Key en cliente
            const masterKey = await AuthService.deriveMasterKey(this.regUsername, this.regPassword);

            // 3. Empaquetamos todo el payload en un FormData Multipart binario
            const formData = new FormData();
            formData.append("username", this.regUsername);
            formData.append("password", masterKey);
            formData.append("fullName", this.regFullName);
            formData.append("email", this.regEmail);

            // Si el usuario seleccionó un archivo físico en el input
            if (this.$refs.avatarInput && this.$refs.avatarInput.files[0]) {
                formData.append("avatar", this.$refs.avatarInput.files[0]);
            }

            // 4. Petición al backend
            const res = await API.register(formData);
            if (!res.ok) {
                const errorMsg = await API.extractErrorMessage(res);
                throw new Error(errorMsg);
            }

            // 5. Flujo Post-Registro: Autenticación instantánea igual que tenías antes
            const loginRes = await API.login(this.regUsername, masterKey);
            if (!loginRes.ok) throw new Error("Fallo de autenticación post-registro instantáneo.");

            const loginData = await loginRes.json();
            localStorage.setItem('jwtToken', loginData.token);
            localStorage.setItem('username', loginData.username);
            localStorage.setItem('fullName', loginData.fullName || '');
            localStorage.setItem('avatarUrl', loginData.avatarUrl || '');
            sessionStorage.setItem('fileKey', masterKey);

            // Guardamos datos de sesión extendidos
            this.userFullName = loginData.fullName;
            this.userAvatarUrl = loginData.avatarUrl;

            this.status = "Configurando sobres e identidad...";
            await AuthService.setupUserCrypto(this.regUsername, masterKey);

            this.showInfo("¡Identidad y monedero de claves instanciados con éxito!");

            // Purgamos variables de registro de la memoria RAM del cliente
            this.username = this.regUsername;
            this.regFullName = ''; this.regEmail = ''; this.regUsername = '';
            this.regPassword = ''; this.regConfirmPassword = ''; this.regAcceptZk = false;

            this.loginError = false;
            this.isLoggedIn = true;
            this.authMode = 'login'; // Reseteamos el modo para futuras sesiones
            await this.refreshAppData();

        } catch (e) {
            this.showError(e.message || "Fallo crítico en el proceso de registro.");
        } finally {
            this.status = "";
        }
    },

    async logout() {
        try {
            await CryptoService.wipeIdentity();
        } catch (e) {
            console.error("Error al purgar el contenedor criptográfico:", e);
        }
        AuthService.logout();
        this.isLoggedIn = false;
        // Reseteamos el estado de Vue al valor inicial por seguridad
        Object.assign(this.$data, this.$options.data());
        this.authMode = 'login';
    }
};