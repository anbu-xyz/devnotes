let easyMDE;

// Core functions
async function saveContent() {
    if (!easyMDE) {
        console.error('easyMDE is not defined');
        return;
    }

    const elements = {
        filePath: document.getElementById('md-file-path'),
        lastModifiedTime: document.getElementById('md-last-modified-time')
    };

    if (!elements.filePath || !elements.lastModifiedTime) {
        console.error('Required elements not found');
        return;
    }

    const uri = '/saveMarkdown?' + new URLSearchParams({
        filename: elements.filePath.textContent,
        lastModifiedTime: elements.lastModifiedTime.textContent
    });

    const result = await fetch(uri, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: easyMDE.value()
    });

    console.debug(`Save result: ${result.statusText} ${result.status}`);
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

    const folderPath = '${markdownFile}'.split('/').slice(0, -1).join('/');
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

//-----------------------------------------------------------------------------
// Autosave
//-----------------------------------------------------------------------------
(function() {
    setInterval(async () => {
        const autoSaveCheckbox = document.getElementById('autoSaveCheckbox');
        if (autoSaveCheckbox?.checked) {
            await saveContent();
        }
    }, 60000);
})();
