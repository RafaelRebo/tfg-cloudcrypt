const DragDropService = {
    async scanEntries(dataTransferItems) {
        let filesToUpload = [];
        const scanPromises = [];
        let containsFolder = false;

        for (let i = 0; i < dataTransferItems.length; i++) {
            const item = dataTransferItems[i].webkitGetAsEntry();
            if (item) {
                if (item.isDirectory) containsFolder = true;
                scanPromises.push(this.traverse(item, "", filesToUpload));
            }
        }
        await Promise.all(scanPromises);
        return { filesToUpload, containsFolder };
    },

    async traverse(item, path, fileList) {
        if (item.isFile) {
            const file = await new Promise((res, rej) => item.file(res, rej));
            Object.defineProperty(file, 'webkitRelativePath', {
                value: path + file.name,
                writable: false
            });
            fileList.push(file);
        } else if (item.isDirectory) {
            const dirReader = item.createReader();
            const entries = await new Promise((res, rej) => dirReader.readEntries(res, rej));
            for (const entry of entries) {
                await this.traverse(entry, path + item.name + "/", fileList);
            }
        }
    }
};