let privateKey = null;
let publicKey = null;

self.onmessage = async (e) => {
    const { type, payload, id } = e.data;

    try {
        switch (type) {
            case 'GENERATE_AND_PACKAGE_KEYS':
                const pair = await crypto.subtle.generateKey(
                    { name: "RSA-OAEP", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
                    true, ["encrypt", "decrypt"]
                );
                privateKey = pair.privateKey;
                publicKey = pair.publicKey;

                const pubJwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
                const publicKeyStr = JSON.stringify(pubJwk);

                // ENVIAMOS EL USERNAME COMO PARÁMETRO DE SALT
                const encryptedPrivateKeyBase64 = await encryptPrivateKeyInternal(pair.privateKey, payload.password, payload.username);

                self.postMessage({ id, status: 'OK', payload: { publicKeyStr, encryptedPrivateKeyBase64 } });
                break;

            case 'INITIALIZE_IDENTITY':
                // ENVIAMOS EL USERNAME COMO PARÁMETRO DE SALT
                privateKey = await decryptPrivateKeyInternal(payload.encryptedBase64, payload.password, payload.username);

                const jwk = typeof payload.publicKeyStr === 'string'
                            ? JSON.parse(payload.publicKeyStr)
                            : payload.publicKeyStr;

                publicKey = await crypto.subtle.importKey(
                    "jwk", jwk, { name: "RSA-OAEP", hash: "SHA-256" }, true, ["encrypt"]
                );

                self.postMessage({ id, status: 'OK' });
                break;

            case 'ENCRYPT_FILE_FOR_UPLOAD':
                const aesKey = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"]);
                const encryptedBlob = await encryptFileInternal(payload.file, aesKey);
                const rawAesKey = await crypto.subtle.exportKey("raw", aesKey);
                const encryptedFileKey = await wrapKeyInternal(rawAesKey, publicKey);
                self.postMessage({ id, status: 'OK', payload: { encryptedBlob, encryptedFileKey } });
                break;

            case 'REWRAP_KEY':
                const encryptedKeyBuffer = Uint8Array.from(atob(payload.encryptedAesKeyBase64), c => c.charCodeAt(0));
                const rawAesBuffer = await crypto.subtle.decrypt({ name: "RSA-OAEP" }, privateKey, encryptedKeyBuffer);
                const targetPubKey = await crypto.subtle.importKey("jwk", JSON.parse(payload.targetPublicKeyJwk), { name: "RSA-OAEP", hash: "SHA-256" }, true, ["encrypt"]);
                const rewrappedBuffer = await crypto.subtle.encrypt({ name: "RSA-OAEP" }, targetPubKey, rawAesBuffer);
                const rewrappedBase64 = btoa(String.fromCharCode(...new Uint8Array(rewrappedBuffer)));
                self.postMessage({ id, status: 'OK', payload: rewrappedBase64 });
                break;

            case 'UNWRAP_KEY':
                const unwrapped = await unwrapKeyInternal(payload.encryptedAesKeyBase64);
                // Para download/preview, exportamos la AES temporal a RAW para que la use el método del worker
                const exportedAes = await crypto.subtle.exportKey("raw", unwrapped);
                self.postMessage({ id, status: 'OK', payload: exportedAes });
                break;

            case 'DECRYPT_FILE':
                // Importamos la llave AES raw que vino del paso anterior
                const importedAes = await crypto.subtle.importKey("raw", payload.aesKey, { name: "AES-GCM" }, true, ["decrypt"]);
                const decryptedBuffer = await decryptFileInternal(payload.encryptedBlob, importedAes);
                self.postMessage({ id, status: 'OK', payload: decryptedBuffer });
                break;

            case 'WIPE_IDENTITY':
                privateKey = null;
                publicKey = null;
                self.postMessage({ id, status: 'OK' });
                break;

            default:
                self.postMessage({ id, status: 'OK' }); // Mantener compatibilidad pasiva
        }
    } catch (error) {
        self.postMessage({ id, status: 'ERROR', error: error.message });
    }
};

// --- LÓGICA INTERNA (Copiada de tu CryptoService original) ---

async function encryptFileInternal(file, aesKey) {
    const chunkSize = 1024 * 1024; // 1MB
    let offset = 0;
    const encryptedChunks = [];

    while (offset < file.size) {
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const slice = file.slice(offset, offset + chunkSize);
        const buffer = await slice.arrayBuffer();

        const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, aesKey, buffer);

        encryptedChunks.push(iv);
        encryptedChunks.push(new Uint8Array(encrypted));

        offset += chunkSize;
    }
    return new Blob(encryptedChunks, { type: file.type });
}

async function decryptFileInternal(blob, aesKey) {
    const arrayBuffer = await blob.arrayBuffer();
    const decryptedChunks = [];
    let offset = 0;

    // Cada bloque cifrado mide exactamente: 1MB de datos + 16 bytes del Tag de autenticación
    const maxCiphertextWithTag = 1024 * 1024 + 16;

    while (offset < arrayBuffer.byteLength) {
        // 1. Leer los 12 bytes del IV correspondientes a este bloque específico
        if (offset + 12 > arrayBuffer.byteLength) break;
        const iv = new Uint8Array(arrayBuffer.slice(offset, offset + 12));
        offset += 12;

        // 2. Extraer el bloque de ciphertext + Tag de autenticación
        const end = Math.min(offset + maxCiphertextWithTag, arrayBuffer.byteLength);
        const encryptedData = arrayBuffer.slice(offset, end);
        offset = end;

        // 3. Descifrar el bloque aislado de forma segura
        const decrypted = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, aesKey, encryptedData);
        decryptedChunks.push(new Uint8Array(decrypted));
    }

    // 4. Reconstruir todos los fragmentos descifrados en un único ArrayBuffer continuo
    const totalLength = decryptedChunks.reduce((acc, chunk) => acc + chunk.byteLength, 0);
    const resultArray = new Uint8Array(totalLength);
    let currentOffset = 0;
    for (const chunk of decryptedChunks) {
        resultArray.set(chunk, currentOffset);
        currentOffset += chunk.byteLength;
    }
    return resultArray.buffer;
}

async function wrapKeyInternal(rawAesKey, pubKey) {
    const encrypted = await crypto.subtle.encrypt({ name: "RSA-OAEP" }, pubKey, rawAesKey);
    return btoa(String.fromCharCode(...new Uint8Array(encrypted)));
}

async function unwrapKeyInternal(encryptedBase64) {
    const encryptedBuffer = Uint8Array.from(atob(encryptedBase64), c => c.charCodeAt(0));
    const decryptedKeyBuffer = await crypto.subtle.decrypt({ name: "RSA-OAEP" }, privateKey, encryptedBuffer);
    return await crypto.subtle.importKey("raw", decryptedKeyBuffer, { name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"]);
}

async function deriveKey(password, usernameSalt) {
    const encoder = new TextEncoder();
    const salt = encoder.encode(usernameSalt.toLowerCase() + "-frivacloud");

    const baseKey = await crypto.subtle.importKey("raw", encoder.encode(password), "PBKDF2", false, ["deriveKey"]);
    return await crypto.subtle.deriveKey(
        { name: "PBKDF2", salt, iterations: 100000, hash: "SHA-256" },
        baseKey, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]
    );
}

async function encryptPrivateKeyInternal(privKey, password, usernameSalt) {
    const jwk = await crypto.subtle.exportKey("jwk", privKey);
    const encKey = await deriveKey(password, usernameSalt);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, encKey, new TextEncoder().encode(JSON.stringify(jwk)));
    const combined = new Uint8Array(iv.length + encrypted.byteLength);
    combined.set(iv);
    combined.set(new Uint8Array(encrypted), iv.length);
    return btoa(String.fromCharCode(...combined));
}

async function decryptPrivateKeyInternal(encBase64, password, usernameSalt) {
    const combined = Uint8Array.from(atob(encBase64), c => c.charCodeAt(0));
    const iv = combined.slice(0, 12);
    const data = combined.slice(12);
    const encKey = await deriveKey(password, usernameSalt);
    const decrypted = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, encKey, data);
    const jwk = JSON.parse(new TextDecoder().decode(decrypted));
    return await crypto.subtle.importKey("jwk", jwk, { name: "RSA-OAEP", hash: "SHA-256" }, true, ["decrypt"]);
}