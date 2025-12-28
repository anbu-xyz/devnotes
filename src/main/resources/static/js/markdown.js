let easyMDE;
const currentDirectoryName = document.getElementById('dl-current-directory-name').textContent;

// Core functions
async function saveContent() {
    if (!easyMDE) {
        console.error('easyMDE is not defined');
        return;
    }

    const elements = {
        filePath: document.getElementById('md-file-path'),
        timestampOfFileInEditor: document.getElementById('md-last-modified-time-editor')
    };

    if (!elements.filePath || !elements.timestampOfFileInEditor) {
        console.error('Required elements not found');
        return;
    }

    const uri = '/saveMarkdown?' + new URLSearchParams({
        filename: elements.filePath.textContent,
        timestampOfFileInEditor: elements.timestampOfFileInEditor.textContent
    });

    const result = await fetch(uri, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: easyMDE.value()
    });

    // if result was CONFLICT, don't save the file
    if (result.status === 409) {
        console.log('File was not saved, as it was modified after lastSaveTime');
        const resultBody = await result.text();

        // parse the result body to get the new filename and the file update timestamp
        const resultJson = JSON.parse(resultBody);
        const newFilename = resultJson.newFilename;
        console.error(`File was not saved, as it was modified after lastSaveTime. New filename: ${newFilename}`);

        // raise an alert to the user
        const alertMessage = `File was not saved, as it was modified after lastSaveTime. New filename: ${newFilename}`;
        alert(alertMessage);
    } else if (result.status === 200) {
        const resultBody = await result.text();
        const resultJson = JSON.parse(resultBody);
        console.debug(`File saved. New file timestamp: ${resultJson.timestampOfFileInEditor}`);

        elements.timestampOfFileInEditor.innerHTML = resultJson.timestampOfFileInEditor;
    } else {
        console.error(`Save result: ${result.statusText} ${result.status}`);
    }
}

function downloadExcel(outputFileName, markdownFileName) {
    window.location.href = '/downloadExcel?' + new URLSearchParams({
        outputFileName,
        markdownFileName
    });
}

function createHomeLink(elementId) {
    const viewContent = document.getElementById(elementId);
    const h1 = viewContent?.querySelector('h1');
    const editButton = viewContent?.querySelector("#editButton");

    if (!h1 || !editButton) {
        console.error('Required elements not found for home link creation');
        return;
    }
    const markdownFileElement = document.getElementById('md-file-path');
    const folderPath = markdownFileElement.textContent.split('/').slice(0, -1).join('/');

    const elements = {
        folder: createLinkElement('folder-open', `renderDirectoryContents?directoryName=${encodeURIComponent(folderPath)}`),
        edit: createLinkElement('edit', '#', () => editButton.click())
    };

    h1.insertBefore(elements.folder, h1.firstChild);
    h1.appendChild(elements.edit);
    editButton.style.display = 'none';
}

function createLinkElement(iconName, href, onClick) {
    const link = document.createElement('a');
    link.href = href;
    link.classList.add('text-blue-500', 'hover:text-blue-700', 'mr-2');
    link.innerHTML = `<i class="fas fa-${iconName}" style="padding-${iconName === 'edit' ? 'left' : 'right'}: 0.2em"></i>`;
    if (onClick) link.onclick = e => { e.preventDefault(); onClick(); };
    return link;
}

function attachEasyMdeOn(elementId) {
    const element = document.getElementById(elementId);
    if (!element) {
        console.error(`Editor element not found: ${elementId}`);
        return;
    }
    easyMDE = new EasyMDE({ element });
}

function invokePrismHighlighting() {
    Prism.highlightAll();
}

async function uploadFile(file) {
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    formData.append('path', currentDirectoryName);
    const response = await fetch('/uploadFile', {method: 'POST', body: formData});
    if (response.ok) {
        console.log("File uploaded successfully");
        if (navigator.clipboard) {
            try {
                await navigator.clipboard.writeText(`![](${file.name})`);
                console.log("File name copied to clipboard");
            } catch (err) {
                console.error("Failed to copy file name to clipboard", err);
            }
        }
    } else {
        alert('Failed to upload file');
    }
}

//-----------------------------------------------------------------------------
// Event handlers
//-----------------------------------------------------------------------------
const keyboardShortcuts = {
    's': () => document.querySelector('#saveButton')?.click(),
    'e': () => document.querySelector('#markdownViewer > h1 > a:nth-child(2) > i')?.click()
};

document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && keyboardShortcuts[e.key]) {
        e.preventDefault();
        keyboardShortcuts[e.key]();
    }
});

document.body.addEventListener('htmx:afterSwap', evt => {
    const actions = {
        markdownViewer: () => {
            createHomeLink("markdownViewer")
            invokePrismHighlighting()
            setupCodeBlockModals()
        },
        markdownEditor: () => {
            attachEasyMdeOn('easyMdeEditor')
        }
    };

    if (actions[evt.target.id]) {
        actions[evt.target.id]();
        console.debug(`${evt.target.id} content loaded`);
    }
});

function isElementVisible(el) {
    if (!el) return false;

    const style = window.getComputedStyle(el);
    const rect = el.getBoundingClientRect();

    return (
        style.display !== 'none' &&
        style.visibility !== 'hidden' &&
        style.opacity !== '0' &&
        rect.width > 0 &&
        rect.height > 0 &&
        rect.bottom > 0 &&
        rect.right > 0 &&
        rect.top < (window.innerHeight || document.documentElement.clientHeight) &&
        rect.left < (window.innerWidth || document.documentElement.clientWidth)
    );
}

//-----------------------------------------------------------------------------
// Modal Dialog
//-----------------------------------------------------------------------------

function setupCodeBlockModals() {
    const modal = document.getElementById('codeModal');
    const modalCode = document.getElementById('modalCode');

    // Add click handlers to all code blocks
    document.querySelectorAll('pre code').forEach(block => {
        block.addEventListener('click', function() {
            modalCode.textContent = this.textContent;
            modalCode.className = this.className; // Preserve syntax highlighting
            modal.style.display = 'block';
            Prism.highlightElement(modalCode);
        });
    });

    // Close modal when clicking outside
    if (modal) {
        modal.addEventListener('click', function (e) {
            if (e.target === modal) {
                modal.style.display = 'none';
            }
        });
    } else {
        console.error('Modal element not found');
        return;
    }

    // Close modal on escape key
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape' && modal.style.display === 'block') {
            modal.style.display = 'none';
        }
    });
}

//-----------------------------------------------------------------------------
// Autosave
//-----------------------------------------------------------------------------
(function() {
    let intervalId = setInterval(async () => {
        const autoSaveCheckbox = document.getElementById('autoSaveCheckbox');
        const markdownEditor = document.getElementById('markdownEditor');
        if (isElementVisible(autoSaveCheckbox)) {
            if (autoSaveCheckbox?.checked && markdownEditor) {
                await saveContent();
            }
        }
    }, 60000);

    console.debug(`Autosave interval ${intervalId} started`);
})();
