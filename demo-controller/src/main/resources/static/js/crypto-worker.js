let privateKey = null;
let publicKey = null;

self.onmessage = async (e) => {
    const { type, payload, id } = e.data;

    try {
        switch (type) {
            case 'SET_KEYS':
                privateKey = payload.privateKey;
                publicKey = payload.publicKey;
                self.postMessage({ id, status: 'OK' });
                break;

            case 'GENERATE_RSA_KEYS':
                const keyPair = await crypto.subtle.generateKey(
                    { name: "RSA-OAEP", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
                    true, ["encrypt", "decrypt"]
                );
                self.postMessage({ id, status: 'OK', payload: keyPair });
                break;

            case 'ENCRYPT_FILE':
                const blob = await encryptFileInternal(payload.file, payload.aesKey);
                self.postMessage({ id, status: 'OK', payload: blob });
                break;

            case 'ENCRYPT_FILE_FOR_UPLOAD':
                // 1. Generar AES internamente
                const aesKey = await crypto.subtle.generateKey(
                    { name: "AES-GCM", length: 256 }, true, ["encrypt", "decrypt"]
                );

                // 2. Cifrar el archivo
                const encryptedBlob = await encryptFileInternal(payload.file, aesKey);

                // 3. Envolver la llave AES con la Pública RSA (que ya tiene el Worker)
                const rawAesKey = await crypto.subtle.exportKey("raw", aesKey);
                const encryptedFileKey = await wrapKeyInternal(rawAesKey, publicKey);

                self.postMessage({
                    id,
                    status: 'OK',
                    payload: { encryptedBlob, encryptedFileKey }
                });
                break;

            case 'DECRYPT_FILE':
                const decryptedBuffer = await decryptFileInternal(payload.encryptedBlob, payload.aesKey);
                self.postMessage({ id, status: 'OK', payload: decryptedBuffer });
                break;

            case 'WRAP_KEY':
                const wrapped = await wrapKeyInternal(payload.rawAesKey, payload.targetPublicKey);
                self.postMessage({ id, status: 'OK', payload: wrapped });
                break;

            case 'UNWRAP_KEY':
                const unwrapped = await unwrapKeyInternal(payload.encryptedAesKeyBase64);
                self.postMessage({ id, status: 'OK', payload: unwrapped });
                break;

            case 'REWRAP_KEY':
                try {
                    console.log("DEBUG: Iniciando REWRAP_KEY");

                    if (!privateKey) {
                        console.error("DEBUG: La privateKey es NULL en el worker");
                        throw new Error("Llave privada no inicializada. Prueba a cerrar sesión y volver a entrar.");
                    }

                    // 1. Validar Base64
                    console.log("DEBUG: Decodificando AES Key Base64...");
                    let encryptedKeyBuffer;
                    try {
                        encryptedKeyBuffer = Uint8Array.from(atob(payload.encryptedAesKeyBase64), c => c.charCodeAt(0));
                    } catch(e) { throw new Error("La llave AES del servidor no es un Base64 válido"); }

                    // 2. Descifrar RSA
                    console.log("DEBUG: Intentando descifrar AES con nuestra RSA privada...");
                    let rawAesBuffer;
                    try {
                        rawAesBuffer = await crypto.subtle.decrypt({ name: "RSA-OAEP" }, privateKey, encryptedKeyBuffer);
                        console.log("DEBUG: Descifrado RSA OK");
                    } catch(e) {
                        console.error("DEBUG: Error en decrypt RSA:", e);
                        throw new Error("No se pudo descifrar la llave del archivo. ¿Eres el dueño real? " + e.message);
                    }

                    // 3. Importar Pública del otro
                    console.log("DEBUG: Importando llave pública del receptor...");
                    let targetPubKey;
                    try {
                        const jwk = typeof payload.targetPublicKeyJwk === 'string'
                                    ? JSON.parse(payload.targetPublicKeyJwk)
                                    : payload.targetPublicKeyJwk;
                        targetPubKey = await crypto.subtle.importKey(
                            "jwk", jwk, { name: "RSA-OAEP", hash: "SHA-256" }, true, ["encrypt"]
                        );
                        console.log("DEBUG: Importación pública OK");
                    } catch(e) { throw new Error("La llave pública del destinatario es inválida: " + e.message); }

                    // 4. Cifrar para el otro
                    console.log("DEBUG: Cifrando AES para el receptor...");
                    const rewrappedBuffer = await crypto.subtle.encrypt({ name: "RSA-OAEP" }, targetPubKey, rawAesBuffer);
                    const rewrappedBase64 = btoa(String.fromCharCode(...new Uint8Array(rewrappedBuffer)));

                    console.log("DEBUG: Todo el proceso REWRAP OK");
                    self.postMessage({ id, status: 'OK', payload: rewrappedBase64 });
                } catch (e) {
                    console.error("DEBUG: Error capturado:", e);
                    self.postMessage({ id, status: 'ERROR', error: "Error en re-cifrado: " + e.message });
                }
                break;

            case 'ENCRYPT_PRIVATE_KEY':
                const encPriv = await encryptPrivateKeyInternal(payload.privateKey, payload.password);
                self.postMessage({ id, status: 'OK', payload: encPriv });
                break;

            case 'DECRYPT_PRIVATE_KEY':
                const decPriv = await decryptPrivateKeyInternal(payload.encryptedBase64, payload.password);
                self.postMessage({ id, status: 'OK', payload: decPriv });
                break;

            default:
                throw new Error("Acción no soportada por el Worker");
        }
    } catch (error) {
        self.postMessage({ id, status: 'ERROR', error: error.message });
    }
};

// --- LÓGICA INTERNA (Copiada de tu CryptoService original) ---

async function encryptFileInternal(file, aesKey) {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const chunkSize = 1024 * 1024; // 1MB
    let offset = 0;
    const encryptedChunks = [iv];

    while (offset < file.size) {
        const slice = file.slice(offset, offset + chunkSize);
        const buffer = await slice.arrayBuffer();
        const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, aesKey, buffer);
        encryptedChunks.push(new Uint8Array(encrypted));
        offset += chunkSize;
    }
    return new Blob(encryptedChunks, { type: file.type });
}

async function decryptFileInternal(blob, aesKey) {
    const arrayBuffer = await blob.arrayBuffer();
    const iv = arrayBuffer.slice(0, 12);
    const data = arrayBuffer.slice(12);
    return await crypto.subtle.decrypt({ name: "AES-GCM", iv: new Uint8Array(iv) }, aesKey, data);
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

async function deriveKey(password) {
    const encoder = new TextEncoder();
    const salt = encoder.encode("user-prive-key-salt");
    const baseKey = await crypto.subtle.importKey("raw", encoder.encode(password), "PBKDF2", false, ["deriveKey"]);
    return await crypto.subtle.deriveKey(
        { name: "PBKDF2", salt, iterations: 100000, hash: "SHA-256" },
        baseKey, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]
    );
}

async function encryptPrivateKeyInternal(privKey, password) {
    const jwk = await crypto.subtle.exportKey("jwk", privKey);
    const encKey = await deriveKey(password);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, encKey, new TextEncoder().encode(JSON.stringify(jwk)));
    const combined = new Uint8Array(iv.length + encrypted.byteLength);
    combined.set(iv);
    combined.set(new Uint8Array(encrypted), iv.length);
    return btoa(String.fromCharCode(...combined));
}

async function decryptPrivateKeyInternal(encBase64, password) {
    const combined = Uint8Array.from(atob(encBase64), c => c.charCodeAt(0));
    const iv = combined.slice(0, 12);
    const data = combined.slice(12);
    const encKey = await deriveKey(password);
    const decrypted = await crypto.subtle.decrypt({ name: "AES-GCM", iv }, encKey, data);
    const jwk = JSON.parse(new TextDecoder().decode(decrypted));
    return await crypto.subtle.importKey("jwk", jwk, { name: "RSA-OAEP", hash: "SHA-256" }, true, ["decrypt"]);
}