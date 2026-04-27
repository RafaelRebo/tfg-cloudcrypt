const PreviewService = {
    async getPreviewData(file, password) {
        const res = await API.download(file.id, password);
        if (!res.ok) throw new Error("No se pudo obtener el archivo");

        const blob = await res.blob();
        const mime = file.fileType.toLowerCase();
        const fileName = file.fileName.toLowerCase();

        // Creamos el Blob seguro con su MIME original
        const safeBlob = new Blob([blob], { type: mime });
        const url = URL.createObjectURL(safeBlob);

        // --- GRUPO 1: MULTIMEDIA DIRECTA ---
        if (mime.startsWith('image/') || mime.startsWith('video/') || mime.startsWith('audio/')) {
            let type = 'image';
            if (mime.startsWith('video/')) type = 'video';
            if (mime.startsWith('audio/')) type = 'audio';
            return { type, url };
        }

        // --- GRUPO 2: DOCUMENTOS E ESTÁNDAR ---
        if (mime === 'application/pdf') return { type: 'pdf', url };

        // --- GRUPO 3: TEXTO, CÓDIGO Y CONFIGURACIÓN ---
        // Añadimos detecciones por extensión para archivos que a veces vienen como octet-stream
        const isText = mime.startsWith('text/') ||
                       mime.includes('json') ||
                       mime.includes('javascript') ||
                       mime.includes('xml') ||
                       fileName.endsWith('.py') ||
                       fileName.endsWith('.java') ||
                       fileName.endsWith('.cpp') ||
                       fileName.endsWith('.sh') ||
                       fileName.endsWith('.md') ||
                       fileName.endsWith('.log');

        if (isText) {
            const text = await blob.text();
            return { type: 'text', content: text };
        }

        // --- GRUPO 5: ARCHIVOS COMPRIMIDOS ---
        if (mime.includes('zip') || mime.includes('rar') || mime.includes('tar') || mime.includes('gzip')) {
            return { type: 'archive', url };
        }

        return { type: 'unsupported', url };
    }
};