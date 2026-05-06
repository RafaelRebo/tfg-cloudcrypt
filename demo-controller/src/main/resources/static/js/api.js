const API = {
    getAuthHeader() {
        const token = localStorage.getItem('jwtToken');
        return token ? { 'Authorization': `Bearer ${token}` } : {};
    },

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

    async uploadSingle(formData, onProgress, signal) { // <--- Añadimos signal
        return new Promise((resolve, reject) => {
            const xhr = new XMLHttpRequest();
            xhr.open("POST", "/api/files/upload", true);

            // Adjuntamos el signal al evento abort
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

    // --- Actualizar en api.js ---

    async checkExists(fileName, parentId, username) {
        const pId = (parentId && !isNaN(parentId)) ? parentId : '';

        const url = `/api/files/check-exists?fileName=${encodeURIComponent(fileName)}&parentId=${pId}&username=${encodeURIComponent(username)}`;
        const res = await fetch(url, { headers: this.getAuthHeader() });
        if (!res.ok) throw new Error("Error en check-exists");
        return res.json();
    },

    // En api.js (Asegúrate de que esté así)
    // En api.js
    async createFolderSync(folderName, parentId, context) {
        const formData = new FormData();
        formData.append("folderName", folderName);
        formData.append("parentId", (parentId && !isNaN(parentId)) ? parentId : "");
        // context.password viene del objeto que pasamos en UploadService
        formData.append("password", context.password);
        formData.append("authenticatedUser", context.username);

        const res = await fetch('/api/files/folder/sync', {
            method: 'POST',
            body: formData,
            headers: this.getAuthHeader()
        });

        if (!res.ok) throw new Error("Error en sincronización de carpetas");
        return res.json();
    },

    async getFiles(username, folderId = null, category = 'all', page = 0, size = 20) {
        // Si folderId es nulo o '/' (raíz), mandamos cadena vacía para que Spring reciba null
        const fId = (folderId && !isNaN(folderId)) ? folderId : '';

        let url = `/api/files?authenticatedUser=${encodeURIComponent(username)}&page=${page}&size=${size}&category=${category}&folderId=${fId}`;

        const res = await fetch(url, { headers: this.getAuthHeader() });
        if (!res.ok) throw new Error("Error API getFiles");
        return res.json();
    },

    // Asegúrate de que el orden sea consistente: user, key, parentId, name
    async createFolder(username, sessionKey, parentId, folderName) {
        const formData = new FormData();
        formData.append("authenticatedUser", username);
        formData.append("password", sessionKey);
        // Aseguramos que si es null mande vacío para evitar errores de conversión en Java
        formData.append("parentId", (parentId && !isNaN(parentId)) ? parentId : "");
        formData.append("folderName", folderName);

        const res = await fetch('/api/files/folder', {
            method: 'POST',
            body: formData,
            headers: this.getAuthHeader()
        });
        return res;
    },

    async getStats(username) {
        const res = await fetch(`/api/files/stats?t=${Date.now()}`, {
            headers: this.getAuthHeader() // Inyectamos JWT
        });
        if (!res.ok) throw new Error("Error API getStats");
        return res.json();
    },

    async deleteFile(id) {
        return fetch(`/api/files/${id}`, {
            method: 'DELETE',
            headers: this.getAuthHeader()
        });
    },

    async download(fileId, password) {
        return fetch(`/api/files/download/${fileId}`, {
            method: 'GET',
            headers: {
                ...this.getAuthHeader(),
                'X-File-Password': password
            }
        });
    },

    async searchFiles(query, page = 0, size = 20) {
        const url = `/api/files/search?q=${encodeURIComponent(query)}&page=${page}&size=${size}`;
        const res = await fetch(url, { headers: this.getAuthHeader() });
        if (!res.ok) throw new Error("Error en la búsqueda");
        return res.json();
    },
};