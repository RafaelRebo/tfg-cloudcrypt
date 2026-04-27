const AuthService = {
    async login(username, password) {
        const res = await API.login(username, password);
        if (res.ok) {
            localStorage.setItem('userSession', JSON.stringify({ username, password }));
            return true;
        }
        return false;
    },
    logout() {
        localStorage.removeItem('userSession');
    },
    getSavedSession() {
        const saved = localStorage.getItem('userSession');
        return saved ? JSON.parse(saved) : null;
    }
};