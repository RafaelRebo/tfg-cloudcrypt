const { createApp } = Vue;

const appInstance = createApp({
    data() {
        return {
            isLoggedIn: false,
            username: '', password: '',
            currentFolder: '/',
            allUserFiles: [], filesInCurrentFolder: [],
            status: '', uploadProgress: 0,
            stats: { totalSize: 0, fileCount: 0, maxQuota: 104857600 },
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
                searchResults: [], // Para la lista desplegable
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
                type: 'blank', // Puede ser: 'file' (uno solo), 'multiple' (selección masiva) o 'blank' (fondo vacío)
                target: null   // Guardará el objeto f de la fila pulsada
            },
            clipboard: {
                action: null, // Puede ser 'cut', 'copy' o null
                items: []     // Almacenará los objetos de los archivos/carpetas en tránsito
            },
        }
    },
    async mounted() {
        const session = AuthService.getSavedSession();
        if (session) {
            this.username = session.username;
            this.isLoggedIn = true;
            // AuthService.login se encarga de re-hidratar el Worker con la llave privada
            try {
                await AuthService.login(session.username, session.password);
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
        ...UIService,
    },
    watch: {
        uploadProgress(newVal) {
            NotificationService.updateUploadProgress(this);
        }
    }
}).mount('#app');