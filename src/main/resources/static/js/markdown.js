const currentDirectoryName = document.getElementById('dl-current-directory-name').textContent;

//-----------------------------------------------------------------------------
// Edit-lock management
// Each page load gets a unique token. The server tracks which token holds the
// exclusive edit lock for each file. Open editors must send a heartbeat every
// 20 s; locks with no heartbeat for 60 s are released automatically.
//-----------------------------------------------------------------------------
const pageLockToken = crypto.randomUUID();
let _editLockHeartbeatId = null;

function _editLockFilename() {
    return document.getElementById('md-file-path').textContent.trim();
}

async function acquireEditLock(force = false) {
    try {
        const resp = await fetch('/edit-lock/acquire', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ filename: _editLockFilename(), lockToken: pageLockToken, force })
        });
        if (!resp.ok) return false;
        const data = await resp.json();
        return data.acquired === true;
    } catch (err) {
        console.error('Edit-lock acquire failed', err);
        return false;
    }
}

async function releaseEditLock() {
    stopEditLockHeartbeat();
    try {
        navigator.sendBeacon('/edit-lock/release',
            new URLSearchParams({ filename: _editLockFilename(), lockToken: pageLockToken }));
    } catch (err) {
        console.warn('Edit-lock release failed', err);
    }
}

async function _sendEditLockHeartbeat() {
    try {
        const resp = await fetch('/edit-lock/heartbeat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ filename: _editLockFilename(), lockToken: pageLockToken })
        });
        if (resp.ok) {
            const data = await resp.json();
            if (!data.alive) {
                console.warn('Edit-lock heartbeat rejected - lock lost');
                stopEditLockHeartbeat();
            }
        }
    } catch (err) {
        console.warn('Edit-lock heartbeat error', err);
    }
}

function startEditLockHeartbeat() {
    stopEditLockHeartbeat();
    _editLockHeartbeatId = setInterval(_sendEditLockHeartbeat, 20000);
    console.debug('Edit-lock heartbeat started');
}

function stopEditLockHeartbeat() {
    if (_editLockHeartbeatId !== null) {
        clearInterval(_editLockHeartbeatId);
        _editLockHeartbeatId = null;
        console.debug('Edit-lock heartbeat stopped');
    }
}

/**
 * Updates the browser URL to reflect the current edit/view mode without
 * triggering a page reload. This ensures that a browser refresh keeps the
 * user in the same mode they were in.
 */
function setEditModeUrl(editing) {
    const url = new URL(window.location.href);
    if (editing) {
        url.searchParams.set('edit', 'true');
    } else {
        url.searchParams.delete('edit');
    }
    history.replaceState(null, '', url.toString());
}

/** Called by the "Force Edit" button in the lock dialog. */
async function openEditModeForce() {
    const acquired = await acquireEditLock(true);
    if (acquired) {
        // Locate the Alpine component and set editMode = true
        const body = document.querySelector('[x-data]');
        if (body && body._x_dataStack && body._x_dataStack[0]) {
            body._x_dataStack[0].editMode = true;
        }
        setEditModeUrl(true);
        htmx.trigger('#hiddenEditButton', 'click');
    } else {
        alert('Could not acquire edit lock even with force. Please try again.');
    }
}

// Release lock if user navigates away while editor is open
window.addEventListener('beforeunload', () => {
    if (_editLockHeartbeatId !== null) {
        navigator.sendBeacon('/edit-lock/release',
            new URLSearchParams({ filename: _editLockFilename(), lockToken: pageLockToken }));
    }
});

// Core functions
async function saveContent() {
    if (!window.editorView) {
        console.error('CodeMirror editor is not initialised');
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
        body: window.editorView.state.doc.toString()
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

function attachCodeMirrorEditor() {
    const contentEl = document.getElementById('cmInitialContent');
    const initialContent = contentEl ? contentEl.value : '';
    const doInit = () => window.initCodeMirrorEditor('codeMirrorEditor', initialContent);
    if (typeof window.initCodeMirrorEditor === 'function') {
        doInit();
    } else {
        window.addEventListener('cm6ready', doInit, { once: true });
    }
}

//-----------------------------------------------------------------------------
// Editor font size  (preference kept in server memory; localStorage is used
// as an instant cache to avoid a flash-of-wrong-size while the fetch resolves)
//-----------------------------------------------------------------------------
const EDITOR_FONT_SIZE_KEY = 'devnotes.editorFontSize';
const EDITOR_FONT_SIZE_MIN = 10;
const EDITOR_FONT_SIZE_MAX = 28;
const EDITOR_FONT_SIZE_DEFAULT = 14;

function _applyEditorFontSize(px) {
    // Set on :root so the value survives htmx swaps of #markdownEditor.
    // The CSS rule  #codeMirrorEditor { font-size: var(--editor-font-size, 0.9em) }
    // picks this up automatically, even when the element is freshly rendered.
    document.documentElement.style.setProperty('--editor-font-size', px + 'px');
    const displayEl = document.getElementById('editorFontSizeDisplay');
    if (displayEl) displayEl.textContent = px + 'px';
}

async function _saveEditorFontSizeToServer(px) {
    try {
        await fetch('/editor/font-size', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ fontSize: px })
        });
        localStorage.setItem(EDITOR_FONT_SIZE_KEY, px);
    } catch (err) {
        console.warn('Failed to save editor font size to server:', err);
    }
}

async function _loadEditorFontSizeFromServer() {
    try {
        const resp = await fetch('/editor/font-size');
        if (!resp.ok) return null;
        const data = await resp.json();
        return (Number.isInteger(data.fontSize) &&
                data.fontSize >= EDITOR_FONT_SIZE_MIN &&
                data.fontSize <= EDITOR_FONT_SIZE_MAX)
            ? data.fontSize : null;
    } catch (err) {
        console.warn('Failed to load editor font size from server:', err);
        return null;
    }
}

function initEditorFontSize() {
    // Apply cached value immediately so the editor shows the right size with no delay
    const cached = parseInt(localStorage.getItem(EDITOR_FONT_SIZE_KEY), 10);
    const immediate = (cached >= EDITOR_FONT_SIZE_MIN && cached <= EDITOR_FONT_SIZE_MAX)
        ? cached : EDITOR_FONT_SIZE_DEFAULT;
    _applyEditorFontSize(immediate);

    // Then fetch the authoritative value from the server and reconcile
    _loadEditorFontSizeFromServer().then(serverPx => {
        if (serverPx !== null && serverPx !== immediate) {
            _applyEditorFontSize(serverPx);
            localStorage.setItem(EDITOR_FONT_SIZE_KEY, serverPx);
        }
    });

    const decreaseBtn = document.getElementById('fontSizeDecrease');
    const increaseBtn = document.getElementById('fontSizeIncrease');

    if (decreaseBtn) {
        decreaseBtn.addEventListener('click', () => {
            const current = parseInt(document.documentElement.style.getPropertyValue('--editor-font-size'), 10) || EDITOR_FONT_SIZE_DEFAULT;
            const next = Math.max(EDITOR_FONT_SIZE_MIN, current - 1);
            _applyEditorFontSize(next);
            _saveEditorFontSizeToServer(next);
        });
    }
    if (increaseBtn) {
        increaseBtn.addEventListener('click', () => {
            const current = parseInt(document.documentElement.style.getPropertyValue('--editor-font-size'), 10) || EDITOR_FONT_SIZE_DEFAULT;
            const next = Math.min(EDITOR_FONT_SIZE_MAX, current + 1);
            _applyEditorFontSize(next);
            _saveEditorFontSizeToServer(next);
        });
    }
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
        const markdownLink = `![](${file.name})`;
        if (window.editorView) {
            const pos = window.editorView.state.selection.main.from;
            window.editorView.dispatch({
                changes: { from: pos, insert: markdownLink }
            });
        } else if (navigator.clipboard) {
            try {
                await navigator.clipboard.writeText(markdownLink);
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
            stopEditLockHeartbeat();
            createHomeLink("markdownViewer")
            invokePrismHighlighting()
            setupCodeBlockModals()
            setupDataBlockControls()
            setupDataBlockMenuToggle()
            setupDataBlockSourceToggle()
            setupDataBlockErrorRetry()
            setupGroovyBlockControls()
            setupGroovyBlockMenuToggle()
            setupGroovyBlockActionHandler()
            setupRestBlockControls()
            setupRestBlockMenuToggle()
            setupRestBlockActionHandler()
            setupTodoBlockControls()
            setupTodoStatusHandler()
            setupTodoItemDialogHandler()
            document.querySelectorAll('.todo-widget').forEach(w => applyTodoFilters(w))
            setupBlockEditorDialog()
        },
        markdownEditor: () => {
            attachCodeMirrorEditor()
            initEditorFontSize()
            startEditLockHeartbeat()
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

// Dynamically create and attach controls to each .data-block that doesn't already have them
function setupDataBlockControls() {
    document.querySelectorAll('.data-block').forEach(block => {
        if (block.querySelector('.data-block-controls')) return; // already attached

        const btn = document.createElement('button');
        btn.className = 'data-block-more-btn';
        btn.type = 'button';
        btn.textContent = '\u22EE'; // '⋮'

        const menu = document.createElement('ul');
        menu.className = 'data-block-menu';
        menu.innerHTML =
            '<li><a data-action="source">Source</a></li>' +
            '<li><a data-action="refresh">Refresh</a></li>' +
            '<li><a data-action="export-excel">Export Excel</a></li>' +
            '<li><a data-action="edit">Edit</a></li>';

        const controls = document.createElement('div');
        controls.className = 'data-block-controls';
        controls.appendChild(btn);
        controls.appendChild(menu);

        block.insertBefore(controls, block.firstChild);
    });
}

// Toggle the ⋮ dropdown menu when the more button is clicked
function setupDataBlockMenuToggle() {
    if (window._dataBlockMenuToggleAttached) return;
    window._dataBlockMenuToggleAttached = true;

    document.body.addEventListener('click', function (e) {
        const btn = e.target.closest('.data-block-more-btn');
        if (btn) {
            const menu = btn.nextElementSibling;
            if (menu) {
                menu.style.display = menu.style.display === 'block' ? 'none' : 'block';
            }
            return;
        }

        // Close any open menu when clicking outside
        if (!e.target.closest('.data-block-controls')) {
            document.querySelectorAll('.data-block-menu').forEach(m => {
                m.style.display = 'none';
            });
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

            // Nothing found - log for debugging
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
            const datablockParams = table.getAttribute('data-datablock-params') || '{}';
            const mdEl = document.getElementById('md-file-path');
            const markdownFile = mdEl ? mdEl.textContent.trim() : '';
            if (!datablockId || !markdownFile) {
                showInlineError(dataBlock, 'Missing datablock id or markdown file path');
                return;
            }

            // Validate and parse params safely. If invalid JSON, show an inline error and abort.
            let paramsObj = {};
            if (datablockParams && datablockParams.trim() !== '') {
                try {
                    paramsObj = JSON.parse(datablockParams);
                } catch (e) {
                    console.error('Invalid datablock params JSON', e, datablockParams);
                    showInlineError(dataBlock, 'Invalid datablock parameters (malformed JSON)');
                    return;
                }
            }

            // Set busy state and disable the link
            setDataBlockBusy(dataBlock, true);
            link.setAttribute('aria-disabled', 'true');

            // perform POST to /datablock/fragment
            fetch('/datablock/fragment', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ markdownFile, datablockId, params: paramsObj })
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
                setupDataBlockControls();
            }).catch((err) => {
                console.error('Data block refresh failed', err);
                showInlineError(dataBlock, 'Refresh failed: ' + (err.message || 'unknown error'));
            }).finally(() => {
                setDataBlockBusy(dataBlock, false);
                link.removeAttribute('aria-disabled');
            });
        }

        if (action === 'export-excel') {
            const table = dataBlock.querySelector('table[data-datablock-id]');
            if (!table) {
                showInlineError(dataBlock, 'Cannot find table to export');
                return;
            }
            const datablockId = table.getAttribute('data-datablock-id');
            const datablockParams = table.getAttribute('data-datablock-params') || '{}';
            const mdEl = document.getElementById('md-file-path');
            const markdownFile = mdEl ? mdEl.textContent.trim() : '';
            if (!datablockId || !markdownFile) {
                showInlineError(dataBlock, 'Missing datablock id or markdown file path');
                return;
            }

            let paramsObj = {};
            if (datablockParams && datablockParams.trim() !== '') {
                try {
                    paramsObj = JSON.parse(datablockParams);
                } catch (e) {
                    console.error('Invalid datablock params JSON', e, datablockParams);
                    showInlineError(dataBlock, 'Invalid datablock parameters (malformed JSON)');
                    return;
                }
            }

            setDataBlockBusy(dataBlock, true);
            link.setAttribute('aria-disabled', 'true');

            fetch('/datablock/export-excel', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ markdownFile, datablockId, params: paramsObj })
            }).then(async (resp) => {
                if (!resp.ok) {
                    const txt = await resp.text().catch(() => resp.statusText);
                    throw new Error(txt || resp.statusText);
                }
                // Extract suggested filename from Content-Disposition if present
                const disposition = resp.headers.get('Content-Disposition') || '';
                const match = disposition.match(/filename="?([^";\n]+)"?/);
                const filename = match ? match[1] : 'export.xlsx';
                return resp.blob().then(blob => ({ blob, filename }));
            }).then(({ blob, filename }) => {
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                a.remove();
                URL.revokeObjectURL(url);
            }).catch((err) => {
                console.error('Data block Excel export failed', err);
                showInlineError(dataBlock, 'Export failed: ' + (err.message || 'unknown error'));
            }).finally(() => {
                setDataBlockBusy(dataBlock, false);
                link.removeAttribute('aria-disabled');
            });
        }
    });
}

function setupDataBlockErrorRetry() {
    if (window._dataBlockErrorRetryAttached) return;
    window._dataBlockErrorRetryAttached = true;

    document.body.addEventListener('click', function (e) {
        const btn = e.target.closest('.data-block-retry-btn');
        if (!btn) return;

        const dataBlock = btn.closest('.data-block');
        if (!dataBlock) return;

        const datablockId = dataBlock.getAttribute('data-datablock-id');
        const datablockParams = dataBlock.getAttribute('data-datablock-params') || '{}';
        const mdEl = document.getElementById('md-file-path');
        const markdownFile = mdEl ? mdEl.textContent.trim() : '';

        if (!datablockId || !markdownFile) {
            console.error('Missing datablockId or markdownFile for retry');
            return;
        }

        let paramsObj = {};
        try {
            paramsObj = JSON.parse(datablockParams);
        } catch (ex) {
            console.error('Invalid datablock params JSON', ex);
        }

        btn.disabled = true;
        btn.textContent = '⏳';

        fetch('/datablock/fragment', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ markdownFile, datablockId, params: paramsObj })
        }).then(async (resp) => {
            if (!resp.ok) {
                const txt = await resp.text().catch(() => resp.statusText);
                throw new Error(txt || resp.statusText);
            }
            return resp.text();
        }).then((html) => {
            const container = document.createElement('div');
            container.innerHTML = html;
            const newBlock = container.querySelector('.data-block') || container.firstElementChild;
            if (newBlock) {
                dataBlock.replaceWith(newBlock);
                setupDataBlockControls();
                setupDataBlockErrorRetry();
            }
        }).catch((err) => {
            btn.disabled = false;
            btn.textContent = '\u21BB Retry';
            console.error('Data block retry failed', err);
        });
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
            if (controls) {
                controls.appendChild(spinner);
            } else {
                dataBlock.appendChild(spinner);
            }
        }
    } else {
        dataBlock.removeAttribute('aria-busy');
        const spinnerEl = dataBlock.querySelector('.data-block-spinner');
        if (spinnerEl) spinnerEl.remove();
    }
}

//-----------------------------------------------------------------------------
// Groovy Block Controls
//-----------------------------------------------------------------------------

// Dynamically create and attach controls to each .groovy-block that doesn't already have them
function setupGroovyBlockControls() {
    document.querySelectorAll('.groovy-block').forEach(block => {
        if (block.querySelector('.groovy-block-controls')) return; // already attached
        if (block.dataset.groovyControls === 'false') return;      // disabled via controlsEnabled:false

        const btn = document.createElement('button');
        btn.className = 'groovy-block-more-btn';
        btn.type = 'button';
        btn.textContent = '\u22EE'; // '⋮'

        const menu = document.createElement('ul');
        menu.className = 'groovy-block-menu';
        menu.innerHTML =
            '<li><a data-action="refresh">Refresh</a></li>' +
            '<li><a data-action="source">Source</a></li>' +
            '<li><a data-action="edit">Edit</a></li>';

        const controls = document.createElement('div');
        controls.className = 'groovy-block-controls';
        controls.appendChild(btn);
        controls.appendChild(menu);

        block.insertBefore(controls, block.firstChild);
    });
}

// Toggle the ⋮ dropdown menu for groovy blocks
function setupGroovyBlockMenuToggle() {
    if (window._groovyBlockMenuToggleAttached) return;
    window._groovyBlockMenuToggleAttached = true;

    document.body.addEventListener('click', function (e) {
        const btn = e.target.closest('.groovy-block-more-btn');
        if (btn) {
            const menu = btn.nextElementSibling;
            if (menu) {
                menu.style.display = menu.style.display === 'block' ? 'none' : 'block';
            }
            return;
        }

        // Close any open groovy menu when clicking outside
        if (!e.target.closest('.groovy-block-controls')) {
            document.querySelectorAll('.groovy-block-menu').forEach(m => {
                m.style.display = 'none';
            });
        }
    });
}

// Handle Source and Refresh actions for groovy blocks
function setupGroovyBlockActionHandler() {
    if (window._groovyBlockActionHandlerAttached) return;
    window._groovyBlockActionHandlerAttached = true;

    document.body.addEventListener('click', function (e) {
        const link = e.target.closest('.groovy-block-menu a');
        if (!link) return;

        const action = link.dataset.action ? link.dataset.action.trim() : '';
        if (!action) return;

        e.preventDefault();

        const groovyBlock = link.closest('.groovy-block');
        const menu = link.closest('.groovy-block-menu');
        if (menu) menu.style.display = 'none';
        if (!groovyBlock) return;

        if (action === 'source') {
            const togglePre = (pre) => {
                if (!pre) return;
                const style = window.getComputedStyle(pre);
                pre.style.display = (style.display === 'none' ? 'block' : 'none');
            };

            // The hidden source <pre> is a previous sibling of the groovy-block div
            let sibling = groovyBlock.previousElementSibling;
            while (sibling) {
                if (sibling.tagName === 'PRE' && sibling.querySelector('code.language-hidden-groovy-exec')) {
                    togglePre(sibling);
                    return;
                }
                sibling = sibling.previousElementSibling;
            }
            console.debug('No hidden-groovy-exec <pre> found for source toggle');
            return;
        }

        if (action === 'refresh') {
            const groovyId = groovyBlock.getAttribute('data-groovy-id');
            const mdEl = document.getElementById('md-file-path');
            const markdownFile = mdEl ? mdEl.textContent.trim() : '';

            if (!groovyId || !markdownFile) {
                showGroovyError(groovyBlock, 'Missing groovy ID or markdown file path');
                return;
            }

            setGroovyBlockBusy(groovyBlock, true);
            link.setAttribute('aria-disabled', 'true');

            fetch('/groovy/fragment', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ markdownFile, groovyId })
            }).then(async (resp) => {
                if (!resp.ok) {
                    const txt = await resp.text().catch(() => resp.statusText);
                    throw new Error(txt || resp.statusText);
                }
                return resp.text();
            }).then((html) => {
                const container = document.createElement('div');
                container.innerHTML = html;
                const newBlock = container.querySelector('.groovy-block') || container.firstElementChild;
                if (newBlock) {
                    groovyBlock.replaceWith(newBlock);
                    setupGroovyBlockControls();
                }
            }).catch((err) => {
                console.error('Groovy block refresh failed', err);
                showGroovyError(groovyBlock, 'Refresh failed: ' + (err.message || 'unknown error'));
            }).finally(() => {
                setGroovyBlockBusy(groovyBlock, false);
                link.removeAttribute('aria-disabled');
            });
        }
    });
}

function showGroovyError(groovyBlock, message) {
    if (!groovyBlock) return;
    let err = groovyBlock.querySelector('.groovy-block-error');
    if (!err) {
        err = document.createElement('div');
        err.className = 'groovy-block-error';
        err.style.color = 'red';
        err.style.marginTop = '8px';
        groovyBlock.appendChild(err);
    }
    err.textContent = message;
    err.style.display = 'block';
    setTimeout(() => { if (err) err.style.display = 'none'; }, 10000);
}

function setGroovyBlockBusy(groovyBlock, busy) {
    if (!groovyBlock) return;
    if (busy) {
        groovyBlock.setAttribute('aria-busy', 'true');
        let spinner = groovyBlock.querySelector('.groovy-block-spinner');
        if (!spinner) {
            spinner = document.createElement('span');
            spinner.className = 'groovy-block-spinner';
            spinner.style.marginLeft = '8px';
            spinner.textContent = '⏳';
            const controls = groovyBlock.querySelector('.groovy-block-controls');
            if (controls) {
                controls.appendChild(spinner);
            } else {
                groovyBlock.appendChild(spinner);
            }
        }
    } else {
        groovyBlock.removeAttribute('aria-busy');
        const spinnerEl = groovyBlock.querySelector('.groovy-block-spinner');
        if (spinnerEl) spinnerEl.remove();
    }
}

//-----------------------------------------------------------------------------
// Block Editor Dialog (inline editing of data / groovy-exec blocks)
//-----------------------------------------------------------------------------

function setupBlockEditorDialog() {
    if (window._blockEditorAttached) return;
    window._blockEditorAttached = true;

    const dialog = document.getElementById('block-editor-dialog');
    const mountEl = document.getElementById('block-editor-cm-mount');
    const errorEl = document.getElementById('block-editor-error');
    const saveBtn = document.getElementById('block-editor-save-btn');
    const cancelBtn = document.getElementById('block-editor-cancel-btn');
    if (!dialog || !mountEl || !saveBtn || !cancelBtn) return;

    function showError(msg) {
        if (!errorEl) return;
        errorEl.textContent = msg;
        errorEl.style.display = 'block';
    }
    function clearError() {
        if (!errorEl) return;
        errorEl.textContent = '';
        errorEl.style.display = 'none';
    }
    function destroyEditor() {
        if (window._blockEditorView) {
            window._blockEditorView.destroy();
            window._blockEditorView = null;
        }
    }

    // Delegate edit-action clicks from both data and groovy menus
    document.body.addEventListener('click', function (e) {
        const link = e.target.closest('.data-block-menu a[data-action="edit"], .groovy-block-menu a[data-action="edit"]');
        if (!link) return;
        e.preventDefault();

        // Close the containing dropdown menu
        const menu = link.closest('.data-block-menu, .groovy-block-menu');
        if (menu) menu.style.display = 'none';

        const isData = !!link.closest('.data-block-menu');
        let sourceContent = '';
        let blockId = '';
        let targetBlock = null;

        if (isData) {
            const dataBlock = link.closest('.data-block');
            if (!dataBlock) return;
            targetBlock = dataBlock;
            // Find source in a following sibling <pre><code class="language-hidden-data">
            let sib = dataBlock.nextElementSibling;
            while (sib) {
                const code = sib.querySelector('code.language-hidden-data');
                if (code) { sourceContent = code.textContent; break; }
                sib = sib.nextElementSibling;
            }
            const table = dataBlock.querySelector('table[data-datablock-id]');
            blockId = table ? table.getAttribute('data-datablock-id') : '';
            console.log('[Block Editor] Data block - extracted blockId:', blockId);
            dialog.dataset.blockType = 'data';
        } else {
            const groovyBlock = link.closest('.groovy-block');
            if (!groovyBlock) return;
            targetBlock = groovyBlock;
            // Find source in a preceding sibling <pre><code class="language-hidden-groovy-exec">
            let sib = groovyBlock.previousElementSibling;
            while (sib) {
                const code = sib.querySelector('code.language-hidden-groovy-exec');
                if (code) { sourceContent = code.textContent; break; }
                sib = sib.previousElementSibling;
            }
            blockId = groovyBlock.getAttribute('data-groovy-id') || '';
            console.log('[Block Editor] Groovy block - extracted blockId:', blockId);
            dialog.dataset.blockType = 'groovy';
        }

        dialog.dataset.blockId = blockId;
        dialog._targetBlock = targetBlock;
        console.log('[Block Editor] Opening editor for block type:', dialog.dataset.blockType, 'blockId:', blockId);
        clearError();

        const doOpen = () => {
            const lang = isData ? 'yaml' : 'plain';
            window.initBlockEditorCM(mountEl, sourceContent, lang);
            dialog.showModal();
        };

        if (typeof window.initBlockEditorCM === 'function') {
            doOpen();
        } else {
            window.addEventListener('cm6ready', doOpen, { once: true });
        }
    });

    cancelBtn.addEventListener('click', function () {
        destroyEditor();
        clearError();
        dialog.close();
    });

    saveBtn.addEventListener('click', async function () {
        if (!window._blockEditorView) return;
        const newContent = window._blockEditorView.state.doc.toString();
        const markdownFile = (document.getElementById('md-file-path') || {}).textContent?.trim() || '';
        const blockId = dialog.dataset.blockId || '';
        const blockType = dialog.dataset.blockType || '';
        const fileLastModified = (document.getElementById('md-last-modified-time-viewer') || {}).textContent?.trim() || '';

        const endpoint = blockType === 'groovy' ? '/groovy/edit' : '/datablock/edit';
        clearError();
        saveBtn.disabled = true;
        saveBtn.textContent = 'Saving…';

        console.log('[Block Editor] Saving block:', {
            endpoint,
            markdownFile,
            blockId,
            blockType,
            fileLastModified,
            contentLength: newContent.length
        });

        try {
            const resp = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ markdownFile, blockId, newContent, fileLastModified })
            });

            if (resp.status === 409) {
                showError('File was modified externally since this page loaded. Reload the page and try again.');
                return;
            }
            if (!resp.ok) {
                const txt = await resp.text().catch(() => resp.statusText);
                showError('Save failed: ' + (txt || resp.statusText));
                return;
            }

            // Success
            const newLastModified = resp.headers.get('X-File-Last-Modified');
            if (newLastModified) {
                const ts = document.getElementById('md-last-modified-time-viewer');
                if (ts) ts.textContent = newLastModified;
            }
            const html = await resp.text();
            const container = document.createElement('div');
            container.innerHTML = html;
            const newBlock = container.firstElementChild;
            if (newBlock && dialog._targetBlock) {
                dialog._targetBlock.replaceWith(newBlock);
            }
            destroyEditor();
            dialog.close();
            clearError();
            // Re-attach controls to the newly inserted block
            setupDataBlockControls();
            setupGroovyBlockControls();
        } catch (err) {
            showError('Save failed: ' + (err.message || 'unknown error'));
        } finally {
            saveBtn.disabled = false;
            saveBtn.textContent = 'Save';
        }
    });
}

//-----------------------------------------------------------------------------
// REST Block Controls
//-----------------------------------------------------------------------------

function setupRestBlockControls() {
    document.querySelectorAll('.rest-block').forEach(block => {
        if (block.querySelector('.rest-block-controls')) return;

        const btn = document.createElement('button');
        btn.className = 'rest-block-more-btn';
        btn.type = 'button';
        btn.textContent = '\u22EE'; // ⋮

        const menu = document.createElement('ul');
        menu.className = 'rest-block-menu';
        menu.innerHTML =
            '<li><a data-action="source">Source</a></li>' +
            '<li><a data-action="refresh">Refresh</a></li>';

        const controls = document.createElement('div');
        controls.className = 'rest-block-controls';
        controls.appendChild(btn);
        controls.appendChild(menu);
        block.insertBefore(controls, block.firstChild);
    });
}

function setupRestBlockMenuToggle() {
    if (window._restBlockMenuToggleAttached) return;
    window._restBlockMenuToggleAttached = true;

    document.body.addEventListener('click', function (e) {
        const btn = e.target.closest('.rest-block-more-btn');
        if (btn) {
            const menu = btn.nextElementSibling;
            if (menu) {
                menu.style.display = menu.style.display === 'block' ? 'none' : 'block';
            }
            return;
        }
        if (!e.target.closest('.rest-block-controls')) {
            document.querySelectorAll('.rest-block-menu').forEach(m => {
                m.style.display = 'none';
            });
        }
    });
}

function setupRestBlockActionHandler() {
    if (window._restBlockActionHandlerAttached) return;
    window._restBlockActionHandlerAttached = true;

    document.body.addEventListener('click', function (e) {
        const link = e.target.closest('.rest-block-menu a');
        if (!link) return;

        const action = link.dataset.action ? link.dataset.action.trim() : '';
        if (!action) return;

        e.preventDefault();

        const restBlock = link.closest('.rest-block');
        const menu = link.closest('.rest-block-menu');
        if (menu) menu.style.display = 'none';
        if (!restBlock) return;

        if (action === 'source') {
            const togglePre = (pre) => {
                if (!pre) return;
                const style = window.getComputedStyle(pre);
                pre.style.display = (style.display === 'none' ? 'block' : 'none');
            };

            let sibling = restBlock.nextElementSibling;
            while (sibling) {
                if (sibling.tagName === 'PRE' && sibling.querySelector('code.language-hidden-rest')) {
                    togglePre(sibling);
                    return;
                }
                sibling = sibling.nextElementSibling;
            }
            console.debug('No hidden-rest <pre> found for source toggle');
            return;
        }

        if (action === 'refresh') {
            const table = restBlock.querySelector('table[data-restblock-id]');
            if (!table) {
                showRestBlockError(restBlock, 'Cannot find table to refresh');
                return;
            }
            const restblockId = table.getAttribute('data-restblock-id');
            const mdEl = document.getElementById('md-file-path');
            const markdownFile = mdEl ? mdEl.textContent.trim() : '';
            if (!restblockId || !markdownFile) {
                showRestBlockError(restBlock, 'Missing restblock id or markdown file path');
                return;
            }

            setRestBlockBusy(restBlock, true);
            link.setAttribute('aria-disabled', 'true');

            fetch('/restblock/fragment', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ markdownFile, restblockId })
            }).then(async (resp) => {
                if (!resp.ok) {
                    const txt = await resp.text().catch(() => resp.statusText);
                    throw new Error(txt || resp.statusText);
                }
                return resp.text();
            }).then((html) => {
                const container = document.createElement('div');
                container.innerHTML = html;
                const newBlock = container.querySelector('.rest-block') || container.firstElementChild;
                if (newBlock) {
                    restBlock.replaceWith(newBlock);
                    setupRestBlockControls();
                }
            }).catch((err) => {
                console.error('REST block refresh failed', err);
                showRestBlockError(restBlock, 'Refresh failed: ' + (err.message || 'unknown error'));
            }).finally(() => {
                setRestBlockBusy(restBlock, false);
                link.removeAttribute('aria-disabled');
            });
        }
    });
}

function showRestBlockError(restBlock, message) {
    if (!restBlock) return;
    let err = restBlock.querySelector('.rest-block-inline-error');
    if (!err) {
        err = document.createElement('div');
        err.className = 'rest-block-inline-error';
        err.style.color = 'red';
        err.style.marginTop = '8px';
        restBlock.appendChild(err);
    }
    err.textContent = message;
    err.style.display = 'block';
    setTimeout(() => { if (err) err.style.display = 'none'; }, 10000);
}

function setRestBlockBusy(restBlock, busy) {
    if (!restBlock) return;
    if (busy) {
        restBlock.setAttribute('aria-busy', 'true');
        let spinner = restBlock.querySelector('.rest-block-spinner');
        if (!spinner) {
            spinner = document.createElement('span');
            spinner.className = 'rest-block-spinner';
            spinner.style.marginLeft = '8px';
            spinner.textContent = '⏳';
            const controls = restBlock.querySelector('.rest-block-controls');
            if (controls) controls.appendChild(spinner);
            else restBlock.appendChild(spinner);
        }
    } else {
        restBlock.removeAttribute('aria-busy');
        const spinnerEl = restBlock.querySelector('.rest-block-spinner');
        if (spinnerEl) spinnerEl.remove();
    }
}

//-----------------------------------------------------------------------------
// Todo Block Controls  (filter checkboxes + next-state button)
//-----------------------------------------------------------------------------

/**
 * Wire up the filter checkboxes inside every .todo-widget on the page.
 * Uses event delegation so it only needs to be called once per page load.
 */
function setupTodoBlockControls() {
    if (window._todoBlockControlsAttached) return;
    window._todoBlockControlsAttached = true;

    document.body.addEventListener('change', function (e) {
        const cb = e.target.closest('.todo-filter-cb');
        if (!cb) return;
        const widget = cb.closest('.todo-widget');
        if (widget) {
            applyTodoFilters(widget);   // immediate local update
            saveTodoFilters(widget);    // persist to YAML
        }
    });
}

function applyTodoFilters(widget) {
    const activeStatuses = new Set();
    widget.querySelectorAll('.todo-filter-cb').forEach(cb => {
        if (cb.checked) activeStatuses.add(cb.dataset.filterStatus);
    });
    widget.querySelectorAll('.todo-item').forEach(item => {
        const status = item.dataset.status || '';
        // Items with no status are always visible
        item.style.display = (status === '' || activeStatuses.has(status)) ? '' : 'none';
    });
}

/**
 * Persists the current filter-checkbox state of {@code widget} to the YAML file.
 * The server returns the new todo-id hash (because YAML content changed), which
 * we write back to {@code widget.dataset.todoId} so subsequent clicks still match.
 */
function saveTodoFilters(widget) {
    const todoId = widget.dataset.todoId;
    const mdEl = document.getElementById('md-file-path');
    const markdownFile = mdEl ? mdEl.textContent.trim() : '';
    if (!todoId || !markdownFile) return;

    const filters = {};
    widget.querySelectorAll('.todo-filter-cb').forEach(cb => {
        filters[cb.dataset.filterStatus] = cb.checked;
    });

    fetch('/todo/save-filters', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({
            markdownFile,
            todoId,
            'not-started': filters['not-started'] !== false,
            'in-progress': filters['in-progress'] !== false,
            'completed':   filters['completed']   !== false
        })
    }).then(async (resp) => {
        if (resp.ok) {
            // Update the widget's hash so the next status-advance (or filter save)
            // uses the new hash that reflects the saved YAML content.
            const newTodoId = (await resp.text()).trim();
            if (newTodoId) widget.dataset.todoId = newTodoId;
        } else {
            console.warn('Failed to save todo filters:', resp.status, await resp.text().catch(() => ''));
        }
    }).catch(err => console.error('saveTodoFilters error', err));
}

/**
 * Handle clicks on .todo-next-state-btn - POST to /todo/advance-status and
 * replace the whole .todo-widget with the returned HTML fragment.
 * Uses event delegation so it only needs to be called once per page load.
 */
function setupTodoStatusHandler() {
    if (window._todoStatusHandlerAttached) return;
    window._todoStatusHandlerAttached = true;

    document.body.addEventListener('click', function (e) {
        const btn = e.target.closest('.todo-next-state-btn');
        if (!btn) return;

        const item = btn.closest('.todo-item');
        const widget = btn.closest('.todo-widget');
        if (!item || !widget) return;

        const todoId = widget.dataset.todoId;
        const itemIndex = item.dataset.itemIndex;
        const mdEl = document.getElementById('md-file-path');
        const markdownFile = mdEl ? mdEl.textContent.trim() : '';

        if (!todoId || itemIndex === undefined || !markdownFile) {
            console.error('Todo advance-status: missing data', { todoId, itemIndex, markdownFile });
            return;
        }

        btn.disabled = true;
        btn.textContent = '⏳';

        fetch('/todo/advance-status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ markdownFile, todoId, itemIndex: parseInt(itemIndex, 10) })
        }).then(async (resp) => {
            if (!resp.ok) {
                const txt = await resp.text().catch(() => resp.statusText);
                throw new Error(txt || resp.statusText);
            }
            return resp.text();
        }).then((html) => {
            const container = document.createElement('div');
            container.innerHTML = html;
            const newWidget = container.querySelector('.todo-widget') || container.firstElementChild;
            if (newWidget) {
                widget.replaceWith(newWidget);
                // Apply filter visibility - the checkboxes already reflect the persisted
                // YAML state, so applyTodoFilters will hide the right items.
                applyTodoFilters(newWidget);
            }
        }).catch((err) => {
            console.error('Todo status advance failed', err);
            btn.disabled = false;
            btn.textContent = '→';
        });
    });
}

//-----------------------------------------------------------------------------
// Todo Add / Edit Item Dialog
//-----------------------------------------------------------------------------

/**
 * Creates a single shared <dialog> used for both adding and editing todo items.
 * The dialog's dataset.mode ('add' | 'edit') drives which endpoint is called and
 * whether the form fields are blank (add) or pre-filled (edit).
 * Safe to call multiple times - setup runs only once per page load.
 */
function setupTodoItemDialogHandler() {
    if (window._todoItemDialogAttached) return;
    window._todoItemDialogAttached = true;

    // ── create the shared dialog once ────────────────────────────────────────
    if (!document.getElementById('todo-item-dialog')) {
        const dlg = document.createElement('dialog');
        dlg.id = 'todo-item-dialog';
        dlg.innerHTML = `
            <h3 class="todo-add-dialog-title" id="todo-item-dialog-title">Add Task</h3>
            <form id="todo-item-form" novalidate>
                <div class="todo-add-dialog-field">
                    <label for="todo-item-summary">Summary <span style="color:#f85149">*</span></label>
                    <input type="text" id="todo-item-summary" name="summary" required
                           placeholder="Task summary..." autocomplete="off">
                </div>
                <div class="todo-add-dialog-field">
                    <label for="todo-item-status">Status</label>
                    <select id="todo-item-status" name="status">
                        <option value="not-started">Not started</option>
                        <option value="in-progress">In progress</option>
                        <option value="completed">Completed</option>
                    </select>
                </div>
                <div class="todo-add-dialog-field">
                    <label for="todo-item-due">Due date (optional)</label>
                    <input type="date" id="todo-item-due" name="due">
                </div>
                <div class="todo-add-dialog-field">
                    <label for="todo-item-description">Description (optional - markdown)</label>
                    <textarea id="todo-item-description" name="description"
                              placeholder="Additional details..."></textarea>
                </div>
                <div class="todo-add-dialog-actions">
                    <button type="button" class="btn-blue-glow" id="todo-item-cancel">Cancel</button>
                    <button type="submit" class="btn-blue-glow" id="todo-item-submit">Add Task</button>
                </div>
            </form>
        `;
        document.body.appendChild(dlg);

        document.getElementById('todo-item-cancel').addEventListener('click', () => dlg.close());

        document.getElementById('todo-item-form').addEventListener('submit', async function (ev) {
            ev.preventDefault();
            const dialog    = document.getElementById('todo-item-dialog');
            const summaryEl = document.getElementById('todo-item-summary');
            const summary   = summaryEl.value.trim();
            if (!summary) { summaryEl.focus(); return; }

            const due         = document.getElementById('todo-item-due').value.trim();
            const status      = document.getElementById('todo-item-status').value;
            const description = document.getElementById('todo-item-description').value.trim();

            const mdEl         = document.getElementById('md-file-path');
            const markdownFile = mdEl ? mdEl.textContent.trim() : '';
            const todoId       = dialog.dataset.todoId;
            const mode         = dialog.dataset.mode;  // 'add' | 'edit'

            const submitBtn = document.getElementById('todo-item-submit');
            const originalLabel = submitBtn.textContent;
            submitBtn.disabled  = true;
            submitBtn.textContent = '⏳';

            try {
                const endpoint = mode === 'edit' ? '/todo/edit-item' : '/todo/add-item';
                const payload  = { markdownFile, todoId, summary, status };
                if (due)         payload.due         = due;
                if (description) payload.description = description;
                if (mode === 'edit') payload.itemIndex = parseInt(dialog.dataset.itemIndex, 10);

                const resp = await fetch(endpoint, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'same-origin',
                    body: JSON.stringify(payload)
                });

                if (!resp.ok) {
                    const txt = await resp.text().catch(() => resp.statusText);
                    throw new Error(txt || resp.statusText);
                }

                const html         = await resp.text();
                const activeWidget = document.querySelector('.todo-widget[data-todo-acting]');
                if (activeWidget) {
                    const container = document.createElement('div');
                    container.innerHTML = html;
                    const newWidget = container.querySelector('.todo-widget') || container.firstElementChild;
                    if (newWidget) {
                        activeWidget.replaceWith(newWidget);
                        applyTodoFilters(newWidget);
                    }
                }
                dialog.close();
            } catch (err) {
                console.error(`Todo ${mode}-item failed`, err);
                alert(`Failed to ${mode === 'edit' ? 'save' : 'add'} task: ` + err.message);
            } finally {
                submitBtn.disabled    = false;
                submitBtn.textContent = originalLabel;
            }
        });
    }

    // ── + Add button ─────────────────────────────────────────────────────────
    document.body.addEventListener('click', function (e) {
        const btn = e.target.closest('.todo-add-btn');
        if (!btn) return;
        const widget = btn.closest('.todo-widget');
        if (!widget) return;

        document.querySelectorAll('.todo-widget[data-todo-acting]')
                .forEach(el => delete el.dataset.todoActing);
        widget.dataset.todoActing = 'true';

        const dlg = document.getElementById('todo-item-dialog');
        dlg.dataset.mode   = 'add';
        dlg.dataset.todoId = widget.dataset.todoId;
        document.getElementById('todo-item-dialog-title').textContent = 'Add Task';
        document.getElementById('todo-item-submit').textContent = 'Add Task';
        document.getElementById('todo-item-form').reset();
        dlg.showModal();
        document.getElementById('todo-item-summary').focus();
    });

    // ── Edit button ──────────────────────────────────────────────────────────
    document.body.addEventListener('click', function (e) {
        const btn = e.target.closest('.todo-edit-btn');
        if (!btn) return;
        const item   = btn.closest('.todo-item');
        const widget = btn.closest('.todo-widget');
        if (!item || !widget) return;

        document.querySelectorAll('.todo-widget[data-todo-acting]')
                .forEach(el => delete el.dataset.todoActing);
        widget.dataset.todoActing = 'true';

        const dlg = document.getElementById('todo-item-dialog');
        dlg.dataset.mode      = 'edit';
        dlg.dataset.todoId    = widget.dataset.todoId;
        dlg.dataset.itemIndex = item.dataset.itemIndex;
        document.getElementById('todo-item-dialog-title').textContent = 'Edit Task';
        document.getElementById('todo-item-submit').textContent = 'Save Changes';

        // Pre-fill from data-item JSON
        try {
            const data = JSON.parse(item.dataset.item || '{}');
            document.getElementById('todo-item-form').reset();
            document.getElementById('todo-item-summary').value     = data.summary     || '';
            document.getElementById('todo-item-status').value      = data.status      || 'not-started';
            document.getElementById('todo-item-due').value         = data.due         || '';
            document.getElementById('todo-item-description').value = data.description || '';
        } catch (err) {
            console.warn('Could not parse todo item data', err);
            document.getElementById('todo-item-form').reset();
        }

        dlg.showModal();
        document.getElementById('todo-item-summary').focus();
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
