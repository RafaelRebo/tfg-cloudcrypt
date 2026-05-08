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
    }
};