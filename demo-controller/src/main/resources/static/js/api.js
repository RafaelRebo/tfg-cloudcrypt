const API = {
    /**
     * Recupera el token del almacenamiento local y prepara la cabecera Bearer.
     */
    getAuthHeader() {
        const token = localStorage.getItem('jwtToken');
        return token ? { 'Authorization': `Bearer ${token}` } : {};
    },

    // --- AUTENTICACIÓN ---

    async login(username, password) {
        const formData = new FormData();
        formData.append("username", username);
        formData.append("password", password);
        return fetch('/api/users/login', { method: 'POST', body: formData });
    },

    async register(username, password) {
        const formData = new FormData();
        formData.append("username", username);
        formData.append("password", password);
        return fetch('/api/users/register', { method: 'POST', body: formData });
    },

    // --- GESTIÓN DE ARCHIVOS ---

    async getFiles(folderId = null, category = 'all', page = 0, size = 20) {
        const fId = (folderId && !isNaN(folderId)) ? folderId : '';
        // Ya no enviamos el usuario, Spring Security lo sabe por el token
        const url = `/api/files?page=${page}&size=${size}&category=${category}&folderId=${fId}`;

        const res = await fetch(url, { headers: this.getAuthHeader() });
        if (!res.ok) throw new Error("Error al obtener archivos");
        return res.json();
    },

    async uploadSingle(formData, onProgress, signal) {
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            xhr.open("POST", "/api/files/upload", true);

            if (signal) {
                signal.addEventListener('abort', () => {
                    xhr.abort();
                    reject(new Error('Aborted'));
                });
            }

            const auth = this.getAuthHeader();
            if (auth.Authorization) {
                xhr.setRequestHeader('Authorization', auth.Authorization);
            }

            xhr.upload.onprogress = (e) => {
                if (e.lengthComputable) onProgress(e.loaded);
            };

            xhr.onload = () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    try { resolve(JSON.parse(xhr.response)); } catch(e) { resolve(xhr.response); }
                } else {
                    reject(new Error(xhr.responseText || `Error ${xhr.status}`));
                }
            };

            xhr.onerror = () => reject(new Error("Error de red"));
            xhr.send(formData);
        });
    },

    // En API.js
    async download(fileId) {
        return fetch(`/api/files/download/${fileId}`, {
            method: 'GET',
            headers: this.getAuthHeader()
        });
    },

    async moveFiles(fileIds, targetParentId) {
        const formData = new FormData();
        formData.append("targetParentId", targetParentId || "");
        fileIds.forEach(id => formData.append("fileIds", id));

        return fetch('/api/files/move', {
            method: 'POST',
            body: formData,
            headers: this.getAuthHeader()
        });
    },

    async deleteFile(id) {
        return fetch(`/api/files/${id}`, {
            method: 'DELETE',
            headers: this.getAuthHeader()
        });
    },

    async restoreFile(id) {
        return fetch(`/api/files/${id}/restore`, {
            method: 'POST',
            headers: this.getAuthHeader()
        });
    },

    // --- CARPETAS ---

    async createFolder(folderName, parentId, sessionKey) {
        const formData = new FormData();
        formData.append("folderName", folderName);
        formData.append("parentId", (parentId && !isNaN(parentId)) ? parentId : "");
        formData.append("password", sessionKey);

        return fetch('/api/files/folder', {
            method: 'POST',
            body: formData,
            headers: this.getAuthHeader()
        });
    },

    async createFolderSync(folderName, parentId, context) {
        const formData = new FormData();
        formData.append("folderName", folderName);
        formData.append("parentId", (parentId && !isNaN(parentId)) ? parentId : "");
        formData.append("password", context.password);

        const res = await fetch('/api/files/folder/sync', {
            method: 'POST',
            body: formData,
            headers: this.getAuthHeader()
        });

        if (!res.ok) throw new Error("Error en sincronización de carpetas");
        return res.json();
    },

    async checkExists(fileName, parentId) {
        const pId = (parentId && !isNaN(parentId)) ? parentId : '';
        // Ya NO incluimos &username=...
        let url = `/api/files/check-exists?fileName=${encodeURIComponent(fileName)}&parentId=${pId}`;

        const res = await fetch(url, { headers: this.getAuthHeader() });
        if (!res.ok) throw new Error("Error al verificar existencia en el servidor");
        return res.json();
    },

    // --- UTILIDADES ---

    async getStats() {
        const res = await fetch(`/api/files/stats?t=${Date.now()}`, {
            headers: this.getAuthHeader()
        });
        if (!res.ok) throw new Error("Error al obtener estadísticas");
        return res.json();
    },

    async searchFiles(query, page = 0, size = 20) {
        const url = `/api/files/search?q=${encodeURIComponent(query)}&page=${page}&size=${size}`;
        const res = await fetch(url, { headers: this.getAuthHeader() });
        if (!res.ok) throw new Error("Error en la búsqueda");
        return res.json();
    },

    async toggleStar(fileId) {
        return fetch(`/api/files/${fileId}/star`, {
            method: 'POST',
            headers: this.getAuthHeader()
        });
    },


    // --- INFRAESTRUCTURA DE CLAVES (PKI) ---

    /**
     * Registra las llaves del usuario.
     * @param {string} publicKeyStr - El String JSON de la llave pública.
     * @param {string} encryptedPrivateKey - La llave privada en Base64.
     */
    async registerUserKeys(publicKeyStr, encryptedPrivateKey) {
        const payload = {
            // Parseamos el String JSON a Objeto para evitar el doble escapado
            publicKey: publicKeyStr,
            encryptedPrivateKey: encryptedPrivateKey
        };

        return fetch('/api/keys/register', {
            method: 'POST',
            headers: {
                ...this.getAuthHeader(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload)
        });
    },

    async getMyPrivateKey() {
        const res = await fetch('/api/keys/my-private', {
            headers: this.getAuthHeader()
        });
        if (!res.ok) return null;
        return res.text();
    },

   async getUserPublicKey(username) {
       const res = await fetch(`/api/keys/public/${username}`, {
           headers: this.getAuthHeader()
       });
       if (!res.ok) return null;
       return res.json();
   },

   // Buscar usuarios por nombre (para el buscador del modal)
   async searchUsers(query) {
       const res = await fetch(`/api/users/search?q=${encodeURIComponent(query)}`, {
           headers: this.getAuthHeader()
       });
       if (!res.ok) return [];
       return res.json();
   },
};