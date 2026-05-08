const AuthService = {
    // En AuthService.js -> login
    async login(username, password) {
        const res = await API.login(username, password);
        if (res.ok) {
            const data = await res.json();
            localStorage.setItem('jwtToken', data.token);
            localStorage.setItem('username', data.username);
            sessionStorage.setItem('fileKey', password);

            try {
                const encryptedPrivKey = await API.getMyPrivateKey();
                // Solo intentamos descifrar si el servidor realmente nos devolvió algo
                if (encryptedPrivKey && encryptedPrivKey.trim() !== "" && !encryptedPrivKey.includes("No tienes llaves")) {
                    const privateKeyObject = await CryptoService.decryptPrivateKey(encryptedPrivKey, password);
                    window.userPrivateKey = privateKeyObject;
                }
            } catch (e) {
                console.warn("El usuario aún no tiene llaves o hubo un error al recuperarlas");
            }
            return true;
        }
        return false;
    },

    async setupUserCrypto(username, password) {
        // Generar par de llaves nuevo
        const keyPair = await CryptoService.generateUserKeyPair();

        // Exportar pública a String (JWK)
        const pubKeyStr = await CryptoService.exportPublicKey(keyPair.publicKey);

        // Encriptar privada con la password master
        const privKeyEnc = await CryptoService.encryptPrivateKey(keyPair.privateKey, password);

        // Registrar en el servidor
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