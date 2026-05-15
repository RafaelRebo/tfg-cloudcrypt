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
                if (encryptedPrivKey) {
                    const privateKeyObj = await CryptoService.decryptPrivateKey(encryptedPrivKey, password);

                    const pubKeyData = await API.getUserPublicKey(username);
                    const publicKeyObj = await CryptoService.importExternalPublicKey(pubKeyData.publicKey);

                    await CryptoService.setKeys(publicKeyObj, privateKeyObj);
                }
            } catch (e) {
                console.error("Error al inicializar identidad en el Worker:", e);
            }
            return true;
        }
        return false;
    },

    async setupUserCrypto(username, password) {
        // Generar par de llaves usando el Worker
        const keyPair = await CryptoService.generateUserKeyPair();

        // Exportar pública para el servidor
        const pubKeyStr = await CryptoService.exportPublicKey(keyPair.publicKey);

        // Encriptar privada para el servidor usando el Worker
        const privKeyEnc = await CryptoService.encryptPrivateKey(keyPair.privateKey, password);

        // Cargar inmediatamente en el Worker para esta sesión
        await CryptoService.setKeys(keyPair.publicKey, keyPair.privateKey);

        return await API.registerUserKeys(pubKeyStr, privKeyEnc);
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

        if (token && password && username) {
            return { token, username, password };
        }
        return null;
    },
    async deriveMasterKey(username, password) {
        const encoder = new TextEncoder();
        const data = encoder.encode(username.toLowerCase() + password);

        // Usamos SHA-256 para generar una clave consistente a partir de user+pass
        const hashBuffer = await crypto.subtle.digest('SHA-256', data);
        const hashArray = Array.from(new Uint8Array(hashBuffer));

        // Retornamos el hash en Hexadecimal
        return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }
};

const AppAuthMethods = {
    async handleLogin() {
        try {
            const secureKey = await AuthService.deriveMasterKey(this.username, this.password);
            // AuthService.login ya se encarga de:
            // 1. Validar user/pass
            // 2. Bajar la privada cifrada
            // 3. Descifrarla con secureKey y meterla en el Worker
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
            this.status = "Generando identidad única...";
            const masterKey = await AuthService.deriveMasterKey(this.username, this.password);

            // 1. Crear usuario
            const res = await API.register(this.username, masterKey);
            if (!res.ok) throw new Error("El usuario ya existe");

            // 2. Autenticar para obtener Token
            const loginRes = await API.login(this.username, masterKey);
            const loginData = await loginRes.json();
            localStorage.setItem('jwtToken', loginData.token);
            localStorage.setItem('username', loginData.username);
            sessionStorage.setItem('fileKey', masterKey);

            // 3. GENERAR LLAVES POR ÚNICA VEZ (Aquí es donde debe ir)
            await AuthService.setupUserCrypto(this.username, masterKey);

            this.showInfo("¡Cuenta e identidad creadas con éxito!");
            this.isLoggedIn = true;
            this.password = '';
            await this.refreshAppData();
        } catch (e) {
            this.showError(e.message);
        } finally { this.status = ""; }
    },

    logout() {
        AuthService.logout();
        this.isLoggedIn = false;
        window.userPrivateKey = null;
        window.userPublicKey = null;
        Object.assign(this.$data, this.$options.data());
    }
};