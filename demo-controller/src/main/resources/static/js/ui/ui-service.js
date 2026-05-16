const UIService = {
    // Resaltado SEGURO contra XSS
    highlight(text) {
        if (!this.searchQuery || !this.isSearching) return text;

        // 1. Escapar HTML para evitar XSS (Nivel Pro TFG)
        const div = document.createElement('div');
        div.textContent = text;
        const safeText = div.innerHTML;

        // 2. Aplicar el resaltado sobre el texto ya seguro
        const regex = new RegExp(`(${this.searchQuery})`, 'gi');
        return safeText.replace(regex, '<span class="highlight">$1</span>');
    },

    // Cálculos del cuadro de selección azul
    getSelectionBoxStyle(startX, startY, currentX, currentY) {
        return {
            left: Math.min(startX, currentX) + 'px',
            top: Math.min(startY, currentY) + 'px',
            width: Math.abs(currentX - startX) + 'px',
            height: Math.abs(currentY - startY) + 'px'
        };
    },

    isColliding(el, boxStyle) {
        const rect = el.getBoundingClientRect();
        const box = {
            left: parseInt(boxStyle.left),
            top: parseInt(boxStyle.top),
            right: parseInt(boxStyle.left) + parseInt(boxStyle.width),
            bottom: parseInt(boxStyle.top) + parseInt(boxStyle.height)
        };
        return !(rect.left > box.right || rect.right < box.left || rect.top > box.bottom || rect.bottom < box.top);
    },

    getFileIcon(mime) { return FileService.getFileIconSvg(mime); },
    formatSize(b) { return (b / (1024 * 1024)).toFixed(1) + ' MB'; },
    formatCategory(cat) {
        const labels = { 'all': 'Mis archivos', 'image': 'Imágenes', 'audio': 'Audio', 'video': 'Vídeos', 'document': 'Documentos', 'shared': 'Compartidos conmigo', 'trash': 'Papelera', 'starred': 'Destacados' };
        return labels[cat] || cat;
    }
};

