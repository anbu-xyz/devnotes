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
            setupDataBlockSourceToggle()
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

// Toggle visibility of the hidden data "Source" pre blocks when the Source menu item is clicked
function setupDataBlockSourceToggle() {
    // Ensure this is attached only once
    if (window._dataBlockSourceToggleAttached) return;
    window._dataBlockSourceToggleAttached = true;

    document.body.addEventListener('click', function (e) {
        const link = e.target.closest('.data-block-menu a');
        if (!link) return;

        // Prefer data-action attribute for robust behavior
        const action = link.dataset.action ? link.dataset.action.trim() : (link.textContent ? link.textContent.trim() : '');
        if (!action) return;

        e.preventDefault();

        // Find the nearest data-block container
        const dataBlock = link.closest('.data-block');
        const menu = link.closest('.data-block-menu');
        if (menu) menu.style.display = 'none'; // Hide menu after click
        if (!dataBlock) return;

        if (action === 'source') {
            // Helper to toggle a <pre> element's display
            const togglePre = (pre) => {
                if (!pre) return;
                const style = window.getComputedStyle(pre);
                pre.style.display = (style.display === 'none' ? 'block' : 'none');
            };

            // Strategy: First look in the immediate following siblings of the data-block
            let sibling = dataBlock.nextElementSibling;
            while (sibling) {
                if (sibling.tagName === 'PRE' && sibling.querySelector('code.language-hidden-data')) {
                    togglePre(sibling);
                    return;
                }
                // Also consider if a descendant contains the hidden pre
                const innerCode = sibling.querySelector && sibling.querySelector('pre > code.language-hidden-data');
                if (innerCode) {
                    togglePre(innerCode.closest('pre'));
                    return;
                }
                sibling = sibling.nextElementSibling;
            }

            // Fallback: search inside the data-block itself
            const inside = dataBlock.querySelector('pre > code.language-hidden-data');
            if (inside) {
                togglePre(inside.closest('pre'));
                return;
            }

            // Nothing found — log for debugging
            console.debug('No <pre> with code.language-hidden-data found for Source toggle');
            return;
        }

        if (action === 'refresh') {
            // Refresh handler: find table and datablock id
            const table = dataBlock.querySelector('table[data-datablock-id]');
            if (!table) {
                showInlineError(dataBlock, 'Cannot find table to refresh');
                return;
            }
            const datablockId = table.getAttribute('data-datablock-id');
            const mdEl = document.getElementById('md-file-path');
            const markdownFile = mdEl ? mdEl.textContent.trim() : '';
            if (!datablockId || !markdownFile) {
                showInlineError(dataBlock, 'Missing datablock id or markdown file path');
                return;
            }

            // Set busy state and disable the link
            setDataBlockBusy(dataBlock, true);
            link.setAttribute('aria-disabled', 'true');

            // perform POST to /datablock/fragment
            fetch('/datablock/fragment', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ markdownFile, datablockId })
            }).then(async (resp) => {
                if (!resp.ok) {
                    const txt = await resp.text().catch(() => resp.statusText);
                    throw new Error(txt || resp.statusText);
                }
                return resp.text();
            }).then((html) => {
                // replace the table with returned HTML
                const container = document.createElement('div');
                container.innerHTML = html;
                // prefer to find a table within returned HTML; otherwise replace whole container
                const newTable = container.querySelector('table[data-datablock-id]') || container.firstElementChild;
                if (newTable) {
                    table.replaceWith(newTable);
                } else {
                    // fallback: replace the dataBlock's inner table area
                    const oldTable = dataBlock.querySelector('table');
                    if (oldTable) oldTable.outerHTML = html;
                }

                // Dispatch a custom event for any additional initialization
                document.dispatchEvent(new CustomEvent('data-block:refreshed', { detail: { datablockId } }));
            }).catch((err) => {
                console.error('Data block refresh failed', err);
                showInlineError(dataBlock, 'Refresh failed: ' + (err.message || 'unknown error'));
            }).finally(() => {
                setDataBlockBusy(dataBlock, false);
                link.removeAttribute('aria-disabled');
            });
        }
    });
}

function showInlineError(dataBlock, message) {
    if (!dataBlock) return;
    let err = dataBlock.querySelector('.data-block-error');
    if (!err) {
        err = document.createElement('div');
        err.className = 'data-block-error';
        err.style.color = 'red';
        err.style.marginTop = '8px';
        dataBlock.appendChild(err);
    }
    err.textContent = message;
    err.style.display = 'block';
    // auto-hide after 10 seconds
    setTimeout(() => { if (err) err.style.display = 'none'; }, 10000);
}

function setDataBlockBusy(dataBlock, busy) {
    if (!dataBlock) return;
    if (busy) {
        dataBlock.setAttribute('aria-busy', 'true');
        // add small spinner if not present
        let spinner = dataBlock.querySelector('.data-block-spinner');
        if (!spinner) {
            spinner = document.createElement('span');
            spinner.className = 'data-block-spinner';
            spinner.style.marginLeft = '8px';
            spinner.textContent = '⏳';
            const controls = dataBlock.querySelector('.data-block-controls');
            if (controls) controls.appendChild(spinner);
        }
    } else {
        dataBlock.removeAttribute('aria-busy');
        const spinner = dataBlock.querySelector('.data-block-spinner');
        if (spinner) spinner.remove();
    }
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
