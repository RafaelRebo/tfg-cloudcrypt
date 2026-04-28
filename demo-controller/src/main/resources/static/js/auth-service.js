const AuthService = {
    async login(username, password) {
        const res = await API.login(username, password);
        if (res.ok) {
            const data = await res.json();

            localStorage.setItem('jwtToken', data.token);
            localStorage.setItem('username', data.username);

            sessionStorage.setItem('fileKey', password);

            return true;
        }
        return false;
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
    }
};