const { createApp } = Vue;

const appInstance = createApp({
    data() {
        return {
            isLoggedIn: false,
            username: '', password: '',
            currentFolder: '/',
            authMode: 'login',
            userFullName: '',
            userAvatarUrl: '',
            userRole: 'USER',
            userEmail: '',
            regFullName: '',
            regEmail: '',
            regUsername: '',
            regPassword: '',
            regConfirmPassword: '',
            regAcceptZk: false,
            allUserFiles: [], filesInCurrentFolder: [],
            status: '', uploadProgress: 0,
            stats: { totalSize: 0, fileCount: 0, maxQuota: 0 },
            preview: { active: false, url: null, name: '', type: '', content: '' },
            currentPage: 0, isLoadingMore: false, hasMore: true,
            notifications: [], loginError: false, currentCategory: 'all',
            confirmModal: { active: false, title: '', isInput: false, inputValue: '', message: '', onConfirm: null, onCancel: null },
            searchQuery: '',
            searchTimeout: null,
            isSearching: false,
            isDragging: false,
            selectedIds: [],
            clickTimer: null,
            trashRootPath: null,
            currentFolderId: null,
            uploadController: null,
            selectionBox: {
                active: false,
                wasDragging: false,
                startX: 0,
                startY: 0,
                style: { top: 0, left: 0, width: 0, height: 0 }
            },
            lastClickTime: 0,
            clickThreshold: 300,
            selectionTimer: null,
            draggingId: null,
            shareModal: {
                active: false,
                fileId: null,
                fileName: '',
                isFolder: false,
                searchQuery: '',
                searchResults: [],
                selectedUsers: [],
                isProcessing: false
            },
            navigationHistory: [],
            historyIndex: 0,
            isHistoryMoving: false,
            folderIdMap: new Map(),
            totalElements: 0,
            sortKey: 'fileName',
            sortOrder: 'asc',
            contextMenu: {
                active: false,
                x: 0,
                y: 0,
                type: 'blank',
                target: null
            },
            clipboard: {
                action: null,
                items: []
            },
            profileModal: {
                active: false,
                activeTab: 1,
                isProcessing: false,
                fullName: '',
                email: '',
                newUsername: '',
                newPassword: '',
                confirmNewPassword: '',
                currentPassword: '',
                removeAvatar: false,
                adminSearchQuery: '',
                adminStats: { globalUsedBytes: 0, users: [] }
            },
        }
    },
    async mounted() {
         try {
             const specsRes = await fetch('/api/setup/crypto-specs');
             if (specsRes.ok) {
                 const specs = await specsRes.json();
                 window.CryptoSpecs = {
                     hashAlgo: specs.hashAlgo,
                     symAlgo: specs.symAlgo.includes("GCM") ? "AES-GCM" : "AES-CBC",
                     asymAlgo: "RSA-OAEP",
                     saltSuffix: specs.saltSuffix || "-cloudcrypt"
                 };

                 await CryptoService.configureRuntimeAlgorithms(specs);
             }
         } catch(e) {
             console.error("No se han podido sincronizar las políticas criptográficas del servidor:", e);
         }
        const session = AuthService.getSavedSession();
        if (session) {
            this.username = session.username;
            this.isLoggedIn = true;

            this.userFullName = localStorage.getItem('fullName') || '';
            this.userAvatarUrl = localStorage.getItem('avatarUrl') || '';
            this.userRole = localStorage.getItem('userRole') || 'USER';
            this.userEmail = localStorage.getItem('email') || '';

            try {
                await AuthService.login(session.username, session.password);

                this.userFullName = localStorage.getItem('fullName') || '';
                this.userAvatarUrl = localStorage.getItem('avatarUrl') || '';
                this.userRole = localStorage.getItem('userRole') || 'USER';
                this.userEmail = localStorage.getItem('email') || '';

                await this.refreshAppData();
            } catch (e) {
                console.error("Sesión expirada o llaves corruptas");
                this.logout();
            }
        }
        window.addEventListener('keydown', this.handleGlobalKeydown);
        window.addEventListener('click', this.closeContextMenu);
    },
    unmounted() {
        window.removeEventListener('keydown', this.handleGlobalKeydown);
        window.removeEventListener('click', this.closeContextMenu);
    },
    computed: {
        quotaPercentage() {
            return Math.min((this.stats.totalSize / this.stats.maxQuota) * 100, 100).toFixed(1);
        },
        pathSegments() {
            return FileService.getPathSegments(
                this.currentFolder,
                this.currentCategory,
                this.trashRootPath
            );
        },
        displayFiles() {
            return FileService.getDisplayFiles(this.allUserFiles, this.currentFolder, this.currentCategory, this.sortKey, this.sortOrder);
        },
    },
    methods: {
        ...AppNavigationMethods,
        ...AppAuthMethods,
        ...AppUploadMethods,
        ...AppNotificationMethods,
        ...AppFileMethods,
        ...AppModalMethods,
        ...AppSelectionMethods,
        ...AppShareMethods,
        ...AppUserMethods,
        ...UIService,
    },
    watch: {
        uploadProgress(newVal) {
            NotificationService.updateUploadProgress(this);
        }
    }
}).mount('#app');