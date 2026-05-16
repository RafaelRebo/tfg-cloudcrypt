const AuthService = {
    async login(username, password) {
        const res = await API.login(username, password);
        if (res.ok) {
            const data = await res.json();
            localStorage.setItem('jwtToken', data.token);
            localStorage.setItem('username', data.username);
            sessionStorage.setItem('fileKey', password);

            try {
                const encryptedPrivKey = await API.getMyPrivateKey();
                const pubKeyData = await API.getUserPublicKey(username);

                if (encryptedPrivKey && pubKeyData) {
                    await CryptoService.initializeIdentity(encryptedPrivKey, pubKeyData.publicKey, password, username);
                }
            } catch (e) {
                console.error("Error al inicializar identidad en el Worker:", e);
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
        const hashBuffer = await crypto.subtle.digest('SHA-256', data);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }
};

const AppAuthMethods = {
    async handleLogin() {
        try {
            const secureKey = await AuthService.deriveMasterKey(this.username, this.password);
            const success = await AuthService.login(this.username, secureKey);

            if (success) {
                sessionStorage.setItem('fileKey', secureKey);
                this.password = '';
                this.loginError = false;
                this.isLoggedIn = true;
                await this.refreshAppData();
            } else {
                this.loginError = true;
                this.showError("Usuario o contraseña incorrectos");
            }
        } catch (e) {
            this.showError("Error al inicializar sesión segura");
        }
    },

    async handleRegister() {
        try {
            this.status = "Generando identidad segura...";
            const masterKey = await AuthService.deriveMasterKey(this.username, this.password);

            // 1. Crear el usuario en la base de datos
            const res = await API.register(this.username, masterKey);
            if (!res.ok) throw new Error("El usuario ya existe o los datos son inválidos");

            // 2. Login temporal e interno para obtener el JWT necesario para firmar las llaves
            const loginRes = await API.login(this.username, masterKey);
            if (!loginRes.ok) throw new Error("Fallo al autenticar tras registro");

            const loginData = await loginRes.json();
            localStorage.setItem('jwtToken', loginData.token);
            localStorage.setItem('username', loginData.username);
            sessionStorage.setItem('fileKey', masterKey);

            // 3. Generar y registrar sus llaves criptográficas por única vez
            this.status = "Configurando llaves criptográficas...";
            await AuthService.setupUserCrypto(this.username, masterKey);

            this.showInfo("¡Cuenta e identidad creadas con éxito!");

            await this.handleLogin();

        } catch (e) {
            this.showError(e.message || "Error en el proceso de registro");
        } finally {
            this.status = "";
        }
    },

    logout() {
        AuthService.logout();
        this.isLoggedIn = false;
        window.userPrivateKey = null;
        window.userPublicKey = null;
        Object.assign(this.$data, this.$options.data());
    }
};