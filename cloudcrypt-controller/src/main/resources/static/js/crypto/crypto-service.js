const CryptoService = {
    worker: new Worker('js/crypto/crypto-worker.js'),
    msgId: 0,
    pendingPromises: {},

    init() {
        this.worker.onmessage = (e) => {
            const { id, status, payload, error } = e.data;
            if (this.pendingPromises[id]) {
                if (status === 'OK') this.pendingPromises[id].resolve(payload);
                else this.pendingPromises[id].reject(new Error(error));
                delete this.pendingPromises[id];
            }
        };
    },

    _send(type, payload) {
        const id = this.msgId++;
        return new Promise((resolve, reject) => {
            this.pendingPromises[id] = { resolve, reject };

            if (payload instanceof ReadableStream) {
                this.worker.postMessage({ type, payload, id }, [payload]);
            } else {
                this.worker.postMessage({ type, payload, id });
            }
        });
    },

    // --- PASARELA DE CONTROL MULTI-MÓDULO ---

    async generateAndPackageKeys(password, username) {
        return this._send('GENERATE_AND_PACKAGE_KEYS', { password, username });
    },

    async initializeIdentity(encryptedBase64, publicKeyStr, password, username) {
        return this._send('INITIALIZE_IDENTITY', { encryptedBase64, publicKeyStr, password, username });
    },

    async setKeys(publicKey, privateKey) {
        return this._send('SET_KEYS', { publicKey, privateKey });
    },

    async generateUserKeyPair() {
        return this._send('GENERATE_RSA_KEYS', {});
    },

    async encryptFile(file, aesKey) {
        return this._send('ENCRYPT_FILE', { file, aesKey });
    },

    async encryptFileForUpload(file) {
        return this._send('ENCRYPT_FILE_FOR_UPLOAD', { file });
    },

    async decryptFile(encryptedBlob, aesKey) {
        return this._send('DECRYPT_FILE', { encryptedBlob, aesKey });
    },

    async wrapKey(rawAesKey, targetPublicKey) {
        return this._send('WRAP_KEY', { rawAesKey, targetPublicKey });
    },

    async unwrapKey(encryptedAesKeyBase64) {
        return this._send('UNWRAP_KEY', { encryptedAesKeyBase64 });
    },

    async reWrapKeyForUser(encryptedAesKeyBase64, targetPublicKeyJwk) {
        return this._send('REWRAP_KEY', { encryptedAesKeyBase64, targetPublicKeyJwk });
    },

    async encryptPrivateKey(privateKey, password) {
        return this._send('ENCRYPT_PRIVATE_KEY', { privateKey, password });
    },

    async decryptPrivateKey(encryptedBase64, password) {
        return this._send('DECRYPT_PRIVATE_KEY', { encryptedBase64, password });
    },

    async rotateIdentityKeys(newPassword, newUsername) {
        return this._send('ROTATE_IDENTITY_KEYS', { newPassword, newUsername });
    },

    async wipeIdentity() {
        return this._send('WIPE_IDENTITY', {});
    },

    async configureRuntimeAlgorithms(specs) {
        return this._send('CONFIGURE_RUNTIME_SPECS', specs);
    },

    // Utilidades directas síncronas de la ventana principal
    async exportPublicKey(publicKey) {
        const jwk = await window.crypto.subtle.exportKey("jwk", publicKey);
        return JSON.stringify(jwk);
    },

    async importExternalPublicKey(jwkString) {
        const targetHash = (window.CryptoSpecs && window.CryptoSpecs.hashAlgo) || 'SHA-256';
        return await window.crypto.subtle.importKey(
            "jwk",
            JSON.parse(jwkString),
            { name: "RSA-OAEP", hash: targetHash },
            true,
            ["encrypt"]
        );
    }
};

CryptoService.init();