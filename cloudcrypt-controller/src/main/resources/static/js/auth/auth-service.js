// Gestiona el login y registro del cliente, y la gestión de claves asociada
const AuthService = {
    async login(username, password) {
        const res = await API.login(username, password); // Llamamos a la función de autenticación del backend
        if (res.ok) {
            const data = await res.json();
            // Almacenamos los datos devueltos que nos permiten autenticarnos
            localStorage.setItem('jwtToken', data.token);
            localStorage.setItem('username', data.username);
            localStorage.setItem('userSalt', data.salt);
            sessionStorage.setItem('fileKey', password);

            try {
                // Una vez recibidos los datos, inicializamos el keyring del usuario (claves pública y privada) para descifrar ficheros
                const encryptedPrivKey = await API.getMyPrivateKey();
                const pubKeyData = await API.getUserPublicKey(username);

                if (encryptedPrivKey && pubKeyData && pubKeyData.publicKey) { // Si el usuario tiene las claves, se verifica su integridad a través de esta función
                    await CryptoService.initializeIdentity(encryptedPrivKey, pubKeyData.publicKey, password, data.salt);
                } else { // Si el usuario aun no tiene las claves, como podría ser en el primer login, llamamos a la función que las genera
                    await this.setupUserCrypto(username, password, data.salt);
                }
            } catch (e) {
                console.error("Fallo al inicializar la identidad:", e);
            }
            return true;
        }
        return false;
    },

    // Función que genera el par de claves RSA y las registra en el servidor
    async setupUserCrypto(username, password, userSalt) {
        const cryptoPackage = await CryptoService.generateAndPackageKeys(password, userSalt);
        const res = await API.registerUserKeys(cryptoPackage.publicKeyStr, cryptoPackage.encryptedPrivateKeyBase64); // La clave privada se registra cifrada
        if (!res.ok) throw new Error("El servidor rechazó las claves asimétricas.");
        return res;
    },

    // Al salir, se eliminan todas las credenciales de sesión del almacenamiento local del navegador
    logout() {
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('username');
        localStorage.removeItem('fullName');
        localStorage.removeItem('avatarUrl');
        localStorage.removeItem('userRole');
        localStorage.removeItem('email');
        sessionStorage.removeItem('fileKey');
    },

    // Permite recuperar las credenciales de la sesión si el almacenamiento local las contiene aun
    getSavedSession() {
        const token = localStorage.getItem('jwtToken');
        const username = localStorage.getItem('username');
        const password = sessionStorage.getItem('fileKey');
        return (token && password && username) ? { token, username, password } : null;
    },

    // Permite obtener la clave maestra del usuario a partir de su contraseña para no enviarla al servidor
    async deriveMasterKey(username, password) {
        const encoder = new TextEncoder();

        const passwordBuffer = encoder.encode(password);
        const saltBuffer = encoder.encode(username.toLowerCase());

        const baseKey = await window.crypto.subtle.importKey(
            "raw",
            passwordBuffer,
            "PBKDF2",
            false,
            ["deriveBits", "deriveKey"]
        );

        const derivedBits = await window.crypto.subtle.deriveBits(
            {
                name: "PBKDF2",
                salt: saltBuffer,
                iterations: 100000,
                hash: "SHA-256"
            },
            baseKey,
            256
        );

        const hashArray = Array.from(new Uint8Array(derivedBits));
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }
};

// Métodos de autenticación vinculados a la interfaz
const AppAuthMethods = {
    // Procesa los datos recibidos tras el login
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
                localStorage.setItem('userRole', data.role || 'USER');
                localStorage.setItem('email', data.email || '');
                localStorage.setItem('userSalt', data.salt);
                sessionStorage.setItem('fileKey', secureKey);

                this.userFullName = data.fullName;
                this.userAvatarUrl = data.avatarUrl;
                this.userRole = data.role || 'USER';
                this.userEmail = data.email || '';
                this.userSalt = data.salt || '';

                let hasCrypto = false;
                // Verificamos si el usuario tiene o no un keyring asociado
                try {
                    const encryptedPrivKey = await API.getMyPrivateKey();
                    const pubKeyData = await API.getUserPublicKey(this.username);
                    if (encryptedPrivKey && pubKeyData && pubKeyData.publicKey) {
                        await CryptoService.initializeIdentity(encryptedPrivKey, pubKeyData.publicKey, secureKey, data.salt);
                        hasCrypto = true;
                    }
                } catch (cryptoErr) {
                    console.warn("Claves asimétricas ausentes en el servidor.");
                }

                // Si no tiene keyring, generamos las claves en el momento
                if (!hasCrypto) {
                    this.status = "Generando claves personalizadas...";
                    try {
                        await AuthService.setupUserCrypto(this.username, secureKey, data.salt);
                        this.showInfo("Claves inicializadas con éxito");
                    } catch (setupErr) {
                        console.error("Error en inicialización de claves:", setupErr);
                        this.showError("No se pudieron inicializar las claves");
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
            this.showError("Error en el proceso de autenticación");
        }
    },

    // Controla el proceso de registro, validando los campos y generando el par de claves asociado al usuario
    async executeRegister() {
        try {
            if (!this.regFullName || !this.regEmail || !this.regUsername || !this.regPassword) {
                this.showError("Por favor, rellena todos los campos obligatorios.");
                return;
            }
            if (this.regPassword !== this.regConfirmPassword) {
                this.showError("Las contraseñas introducidas no coinciden.");
                return;
            }

            this.status = "Generando claves personalizadas...";

            // Generación de salt aleatorio específico de usuario para mayor seguridad
            const entropyBuffer = new Uint8Array(16);
            window.crypto.getRandomValues(entropyBuffer);
            const generatedUserSalt = Array.from(entropyBuffer).map(b => b.toString(16).padStart(2, '0')).join('');

            const masterKey = await AuthService.deriveMasterKey(this.regUsername, this.regPassword);

            const formData = new FormData();
            formData.append("username", this.regUsername);
            formData.append("password", masterKey);
            formData.append("fullName", this.regFullName);
            formData.append("email", this.regEmail);
            formData.append("salt", generatedUserSalt);

            if (this.$refs.avatarInput && this.$refs.avatarInput.files[0]) {
                formData.append("avatar", this.$refs.avatarInput.files[0]);
            }

            const res = await API.register(formData);
            if (!res.ok) throw new Error(await API.extractErrorMessage(res));

            // Una vez registrado ya hacemos login automáticamente
            const loginRes = await API.login(this.regUsername, masterKey);
            if (!loginRes.ok) throw new Error("Fallo en el proceso de autenticación");

            const loginData = await loginRes.json();

            localStorage.setItem('jwtToken', loginData.token);
            localStorage.setItem('username', loginData.username);
            localStorage.setItem('fullName', loginData.fullName || '');
            localStorage.setItem('avatarUrl', loginData.avatarUrl || '');
            localStorage.setItem('userRole', loginData.role || 'USER');
            localStorage.setItem('email', loginData.email || '');
            localStorage.setItem('userSalt', loginData.salt);
            sessionStorage.setItem('fileKey', masterKey);

            this.username = loginData.username;
            this.userFullName = loginData.fullName || '';
            this.userAvatarUrl = loginData.avatarUrl || '';
            this.userRole = loginData.role || 'USER';
            this.userEmail = loginData.email || '';
            this.userSalt = loginData.salt || '';

            this.regFullName = ''; this.regEmail = ''; this.regUsername = '';
            this.regPassword = ''; this.regConfirmPassword = ''; this.regAcceptZk = false;

            // Guardamos las claves asimétricas en el servidor, protegidas usando la clave maestra derivada de la contraseña del usuario
            this.status = "Configurando claves personales...";
            await AuthService.setupUserCrypto(this.regUsername, masterKey, loginData.salt);

            this.isLoggedIn = true;
            this.authMode = 'login';
            await this.refreshAppData();
        } catch (e) {
            this.showError(e.message || "Fallo en el proceso de registro.");
        } finally {
            this.status = "";
        }
    },

    // Se encarga de destruir el estado local de sesión del navegador cuando el usuario cierra su sesión
    async logout() {
        try {
            await CryptoService.wipeIdentity(); // Eliminamos las claves de memoria
        } catch (e) {
            console.error("Error al eliminar claves:", e);
        }
        AuthService.logout();
        this.isLoggedIn = false;
        Object.assign(this.$data, this.$options.data());
        this.authMode = 'login';
    }
};