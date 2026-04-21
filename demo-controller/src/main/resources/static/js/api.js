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
    async fetchFiles(username) {
        const url = `/api/files?username=${encodeURIComponent(username)}&_t=${Date.now()}`;
        const response = await fetch(url);
        return response.json();
    },
    async upload(file, username, password, folderPath) {
        const formData = new FormData();
        formData.append("file", file);
        formData.append("username", username);
        formData.append("password", password);
        formData.append("folderPath", folderPath || "/"); // Coincide con @RequestParam del Controller
        formData.append("fileName", file.name);
        return fetch('/api/files/upload', { method: 'POST', body: formData });
    },
    async download(fileId, password) {
        return fetch(`/api/files/download/${fileId}`, {
            method: 'GET',
            headers: {
                'X-File-Password': password
            }
        });
    }
};