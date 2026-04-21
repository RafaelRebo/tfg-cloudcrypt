const PreviewService = {
    async getPreviewData(file, password) {
        // Reutilizamos la lógica de descarga de la API
        const res = await API.download(file.id, password);
        if (!res.ok) throw new Error("No se pudo obtener el archivo para previsualizar");

        const blob = await res.blob();
        const mime = file.fileType;

        // Caso 1: Imágenes, Vídeos y PDFs (usamos ObjectURL)
        if (mime.startsWith('image/') || mime.startsWith('video/') || mime === 'application/pdf') {
            let type = 'image';
            if (mime.startsWith('video/')) type = 'video';
            if (mime === 'application/pdf') type = 'pdf';

            return {
                type: type,
                url: URL.createObjectURL(blob)
            };
        }

        // Caso 2: Texto plano y código
        if (mime.startsWith('text/') || mime.includes('json') || mime.includes('javascript')) {
            const text = await blob.text();
            return {
                type: 'text',
                content: text
            };
        }

        // Caso 3: No soportado
        return { type: 'unsupported' };
    }
};