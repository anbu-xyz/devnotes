document.addEventListener('DOMContentLoaded', () => {
    document.addEventListener('paste', e => {
        const items = e.clipboardData.items;
        for (let item of items) {
            if (item.kind === 'file') {
                const blob = item.getAsFile();
                let fileName = prompt('Enter filename for the pasted image (with extension, e.g., image.png):', `images/pasted_image_${Date.now()}.png`);
                if (!fileName) {
                    // User cancelled or entered empty filename
                    return;
                }
                // Optionally, ensure the filename has an extension
                if (!/\.[a-zA-Z0-9]+$/.test(fileName)) {
                    fileName += '.png';
                }
                const file = new File([blob], fileName, {type: blob.type});
                uploadFile(file);
            }
        }
    });
});