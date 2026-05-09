const AuthService = {
    // En AuthService.js -> login
    // En AuthService.js
    async login(username, password) {
        const res = await API.login(username, password);
        if (res.ok) {
            const data = await res.json();
            localStorage.setItem('jwtToken', data.token);
            localStorage.setItem('username', data.username);
            sessionStorage.setItem('fileKey', password);

            try {
                // 1. Recuperar Clave Privada (Ya lo hacías)
                const encryptedPrivKey = await API.getMyPrivateKey();
                if (encryptedPrivKey) {
                    window.userPrivateKey = await CryptoService.decryptPrivateKey(encryptedPrivKey, password);
                }

                // 2. NUEVO: Recuperar Clave Pública y convertirla en objeto CryptoKey
                // Busca esta parte en AuthService.js y cámbiala:
                const pubKeyData = await API.getUserPublicKey(username);
                if (pubKeyData) {
                    let jwk;
                    try {
                        // El servidor devuelve un Map con el campo "publicKey" que es un String JSON
                        jwk = (typeof pubKeyData.publicKey === 'string')
                              ? JSON.parse(pubKeyData.publicKey)
                              : pubKeyData.publicKey;
                    } catch (e) {
                        console.error("Error al parsear JWK:", e);
                        return true;
                    }

                    // IMPORTANTE: Guardamos el objeto CryptoKey real en la variable global
                    window.userPublicKey = await window.crypto.subtle.importKey(
                        "jwk",
                        jwk,
                        { name: "RSA-OAEP", hash: "SHA-256" },
                        true,
                        ["encrypt"]
                    );
                }
            } catch (e) {
                console.error("Error al reconstruir identidad criptográfica:", e);
            }
            return true;
        }
        return false;
    },

    async setupUserCrypto(username, password) {
        // 1. Generar par de llaves nuevo
        const keyPair = await CryptoService.generateUserKeyPair();

        // 2. Exportar pública a String (JWK)
        const pubKeyStr = await CryptoService.exportPublicKey(keyPair.publicKey);

        // 3. Encriptar privada con la password master
        const privKeyEnc = await CryptoService.encryptPrivateKey(keyPair.privateKey, password);

        // --- NUEVO: Cargar las llaves en RAM inmediatamente ---
        window.userPublicKey = keyPair.publicKey;
        window.userPrivateKey = keyPair.privateKey;
        // -----------------------------------------------------

        // 4. Registrar en el servidor
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