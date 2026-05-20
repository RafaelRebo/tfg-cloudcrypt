const AuthService = {
    async login(username, password) {
        const res = await API.login(username, password);
        if (res.ok) {
            const data = await res.json();
            localStorage.setItem('jwtToken', data.token);
            localStorage.setItem('username', data.username);
            localStorage.setItem('userSalt', data.salt);
            sessionStorage.setItem('fileKey', password);

            try {
                const encryptedPrivKey = await API.getMyPrivateKey();
                const pubKeyData = await API.getUserPublicKey(username);

                if (encryptedPrivKey && pubKeyData && pubKeyData.publicKey) {
                    await CryptoService.initializeIdentity(encryptedPrivKey, pubKeyData.publicKey, password, data.salt);
                } else {
                    await this.setupUserCrypto(username, password, data.salt);
                }
            } catch (e) {
                console.error("Fallo al inicializar la identidad en el Worker:", e);
            }
            return true;
        }
        return false;
    },

    async setupUserCrypto(username, password, userSalt) {
        const cryptoPackage = await CryptoService.generateAndPackageKeys(password, userSalt);
        const res = await API.registerUserKeys(cryptoPackage.publicKeyStr, cryptoPackage.encryptedPrivateKeyBase64);
        if (!res.ok) throw new Error("El servidor rechazó las llaves asimétricas.");
        return res;
    },

    logout() {
        localStorage.removeItem('jwtToken');
        localStorage.removeItem('username');
        localStorage.removeItem('fullName');
        localStorage.removeItem('avatarUrl');
        localStorage.removeItem('userRole');
        localStorage.removeItem('email');
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
                try {
                    const encryptedPrivKey = await API.getMyPrivateKey();
                    const pubKeyData = await API.getUserPublicKey(this.username);
                    if (encryptedPrivKey && pubKeyData && pubKeyData.publicKey) {
                        await CryptoService.initializeIdentity(encryptedPrivKey, pubKeyData.publicKey, secureKey, data.salt);
                        hasCrypto = true;
                    }
                } catch (cryptoErr) {
                    console.warn("Llavero asimétrico ausente en el servidor. Preparando aprovisionamiento en caliente...");
                }

                if (!hasCrypto) {
                    this.status = "Generando llavero criptográfico de autoridad raíz...";
                    try {
                        await AuthService.setupUserCrypto(this.username, secureKey, data.salt);
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

            this.status = "Generando entropía y sal de seguridad única...";

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

            const loginRes = await API.login(this.regUsername, masterKey);
            if (!loginRes.ok) throw new Error("Fallo de autenticación post-registro.");

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

            this.status = "Configurando sobres e identidad criptográfica...";
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

    async logout() {
        try {
            await CryptoService.wipeIdentity();
        } catch (e) {
            console.error("Error al purgar el contenedor criptográfico:", e);
        }
        AuthService.logout();
        this.isLoggedIn = false;
        Object.assign(this.$data, this.$options.data());
        this.authMode = 'login';
    }
};