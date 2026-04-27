const API = {
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
    async createFolder(username, password, folderPath, folderName) {
        const formData = new FormData();
        formData.append("username", username);
        formData.append("password", password);
        formData.append("folderPath", folderPath);
        formData.append("folderName", folderName);
        return fetch('/api/files/folder', { method: 'POST', body: formData });
    },
    async getFiles(username, folder = null, all = false, page = 0, size = 20) {
        let url = `/api/files?username=${encodeURIComponent(username)}&page=${page}&size=${size}`;
        if (all) url += `&all=true`;
        if (folder) url += `&folder=${encodeURIComponent(folder)}`;

        const res = await fetch(url);
        if (!res.ok) throw new Error("Error API getFiles");
        return res.json();
    },
    async getStats(username) {
        const res = await fetch(`/api/files/stats?username=${encodeURIComponent(username)}&t=${Date.now()}`);
        if (!res.ok) throw new Error("Error API getStats");
        return res.json();
    },
    async deleteFile(id) {
        return fetch(`/api/files/${id}`, { method: 'DELETE' });
    },
    async download(fileId, password) {
        return fetch(`/api/files/download/${fileId}`, {
            method: 'GET',
            headers: { 'X-File-Password': password }
        });
    }
};