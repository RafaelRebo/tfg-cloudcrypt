const PreviewService = {
    async getPreviewData(file, password) {
        const res = await API.download(file.id, password);
        if (!res.ok) throw new Error("No se pudo obtener el archivo para previsualizar");

        const blob = await res.blob();
        const mime = file.fileType;

        // Caso 1: Multimedia y PDFs (usamos ObjectURL)
        // Añadimos mime.startsWith('audio/')
        if (mime.startsWith('image/') || mime.startsWith('video/') ||
            mime.startsWith('audio/') || mime === 'application/pdf') {

            let type = 'image';
            if (mime.startsWith('video/')) type = 'video';
            if (mime.startsWith('audio/')) type = 'audio'; // Identificador para el frontend
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