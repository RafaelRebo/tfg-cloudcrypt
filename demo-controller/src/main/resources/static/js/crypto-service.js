const CryptoService = {
    /**
     * Generates a new RSA-OAEP 2048-bit key pair for the user.
     */
    async generateUserKeyPair() {
        return await window.crypto.subtle.generateKey(
            {
                name: "RSA-OAEP",
                modulusLength: 2048,
                publicExponent: new Uint8Array([1, 0, 1]),
                hash: "SHA-256",
            },
            true, // key is extractable
            ["encrypt", "decrypt"]
        );
    },

    async wrapKey(rawAesKey, publicKey) {
        // publicKey debe ser un objeto CryptoKey. Si lo tienes como String/JWK,
        // primero impórtalo con window.crypto.subtle.importKey
        const encryptedKeyBuffer = await window.crypto.subtle.encrypt(
            { name: "RSA-OAEP" },
            publicKey,
            rawAesKey
        );
        return btoa(String.fromCharCode.apply(null, new Uint8Array(encryptedKeyBuffer)));
    },

    /**
     * FASE 2: Abre el sobre digital usando la Clave Privada RSA.
     * Recupera la llave AES original para poder descifrar el archivo.
     */
    async unwrapKey(encryptedAesKeyBase64, privateKey) {
        const encryptedBuffer = new Uint8Array(atob(encryptedAesKeyBase64).split("").map(c => c.charCodeAt(0)));

        const decryptedKeyBuffer = await window.crypto.subtle.decrypt(
            { name: "RSA-OAEP" },
            privateKey,
            encryptedBuffer
        );

        // Importamos los bytes resultantes como una llave AES válida para GCM
        return await window.crypto.subtle.importKey(
            "raw",
            decryptedKeyBuffer,
            { name: "AES-GCM", length: 256 },
            true,
            ["encrypt", "decrypt"]
        );
    },

    async encryptFile(file, aesKey) {
        const iv = window.crypto.getRandomValues(new Uint8Array(12));
        const encodedFile = await file.arrayBuffer();

        const encryptedContent = await window.crypto.subtle.encrypt(
            { name: "AES-GCM", iv: iv },
            aesKey,
            encodedFile
        );

        // Combinamos IV + Contenido para que el servidor lo guarde todo junto
        const combined = new Uint8Array(iv.length + encryptedContent.byteLength);
        combined.set(iv);
        combined.set(new Uint8Array(encryptedContent), iv.length);

        return new Blob([combined], { type: file.type });
    },

    async decryptFile(encryptedBlob, aesKey) {
        const arrayBuffer = await encryptedBlob.arrayBuffer();

        // Verificación de integridad: el archivo DEBE tener al menos el IV (12 bytes)
        if (arrayBuffer.byteLength < 12) {
            throw new Error("El archivo está corrupto o no tiene formato de cifrado válido.");
        }

        // Extraemos el IV (los primeros 12 bytes) y los datos cifrados
        const iv = arrayBuffer.slice(0, 12);
        const data = arrayBuffer.slice(12);

        try {
            return await window.crypto.subtle.decrypt(
                { name: "AES-GCM", iv: new Uint8Array(iv) },
                aesKey,
                data
            );
        } catch (e) {
            throw new Error("Error técnico al descifrar: La llave AES es incorrecta.");
        }
    },

    /**
     * Derives a symmetric key from the user's password to encrypt/decrypt the private RSA key.
     * Uses PBKDF2 with SHA-256.
     */
    async deriveEncryptionKey(password, salt) {
        const encoder = new TextEncoder();
        const passwordKey = await window.crypto.subtle.importKey(
            "raw",
            encoder.encode(password),
            "PBKDF2",
            false,
            ["deriveKey"]
        );

        return await window.crypto.subtle.deriveKey(
            {
                name: "PBKDF2",
                salt: encoder.encode(salt), // In a real app, use a unique salt per user
                iterations: 100000,
                hash: "SHA-256"
            },
            passwordKey,
            { name: "AES-GCM", length: 256 },
            false,
            ["encrypt", "decrypt"]
        );
    },

    /**
     * Encrypts the Private Key using the user's master password.
     * Returns a Base64 string safe for DB storage.
     */
    async encryptPrivateKey(privateKey, password) {
        // 1. Export the private key to JWK (JSON Web Key) format
        const jwk = await window.crypto.subtle.exportKey("jwk", privateKey);
        const jwkString = JSON.stringify(jwk);

        // 2. Derive a key from password
        const encryptionKey = await this.deriveEncryptionKey(password, "user-prive-key-salt");

        // 3. Encrypt the JWK string
        const iv = window.crypto.getRandomValues(new Uint8Array(12));
        const encryptedData = await window.crypto.subtle.encrypt(
            { name: "AES-GCM", iv: iv },
            encryptionKey,
            new TextEncoder().encode(jwkString)
        );

        // 4. Combine IV and encrypted data into a single Base64 string
        const combined = new Uint8Array(iv.length + encryptedData.byteLength);
        combined.set(iv);
        combined.set(new Uint8Array(encryptedData), iv.length);

        return btoa(String.fromCharCode.apply(null, combined));
    },

    /**
     * Decrypts a stored private key from the server using the user's password.
     */
    async decryptPrivateKey(encryptedBase64, password) {
        const combined = new Uint8Array(atob(encryptedBase64).split("").map(c => c.charCodeAt(0)));
        const iv = combined.slice(0, 12);
        const data = combined.slice(12);

        const encryptionKey = await this.deriveEncryptionKey(password, "user-prive-key-salt");

        const decrypted = await window.crypto.subtle.decrypt(
            { name: "AES-GCM", iv: iv },
            encryptionKey,
            data
        );

        const jwk = JSON.parse(new TextDecoder().decode(decrypted));
        return await window.crypto.subtle.importKey(
            "jwk",
            jwk,
            { name: "RSA-OAEP", hash: "SHA-256" },
            true,
            ["decrypt"]
        );
    },

    /**
     * Exports a public key to a string (JWK) for easy storage.
     */
    async exportPublicKey(publicKey) {
        const jwk = await window.crypto.subtle.exportKey("jwk", publicKey);
        return JSON.stringify(jwk);
    },

    // En CryptoService.js
    async importExternalPublicKey(jwkString) {
        return await window.crypto.subtle.importKey(
            "jwk",
            JSON.parse(jwkString),
            { name: "RSA-OAEP", hash: "SHA-256" },
            true,
            ["encrypt"]
        );
    }
};