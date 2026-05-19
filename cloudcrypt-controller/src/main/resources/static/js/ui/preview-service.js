const PreviewService = {
    async getPreviewData(file, password) {
        const res = await API.download(file.id, password);
        if (!res.ok) throw new Error("No se pudo obtener el archivo");
        const encryptedBlob = await res.blob();

        const keyRes = await fetch(`/api/files/${file.id}/key`, {
            headers: API.getAuthHeader()
        });
        if (!keyRes.ok) throw new Error("No tienes permiso para ver la llave");
        const { encryptedFileKey } = await keyRes.json();

        const aesKey = await CryptoService.unwrapKey(encryptedFileKey);
        const decryptedBuffer = await CryptoService.decryptFile(encryptedBlob, aesKey);

        const mime = file.fileType.toLowerCase();

        const decryptedBlob = new Blob([decryptedBuffer], { type: mime });
        const url = URL.createObjectURL(decryptedBlob);

        if (mime.startsWith('image/') || mime.startsWith('video/') || mime.startsWith('audio/')) {
            let type = 'image';
            if (mime.startsWith('video/')) type = 'video';
            if (mime.startsWith('audio/')) type = 'audio';
            return { type, url };
        }

        if (mime === 'application/pdf') return { type: 'pdf', url };

        const fileName = file.fileName.toLowerCase();
        const isText = mime.startsWith('text/') ||
                       mime.includes('json') ||
                       fileName.endsWith('.txt') ||
                       fileName.endsWith('.log') ||
                       fileName.endsWith('.md');

        if (isText) {
            const text = await decryptedBlob.text();
            return { type: 'text', content: text };
        }

        return { type: 'unsupported', url };
    }
};