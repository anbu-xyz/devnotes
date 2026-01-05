const currentDirectoryName = document.getElementById('dl-current-directory-name').textContent;
let hoverTimer;
let currentVisibleActions = null;

// Action display functions
function showActions(entry, event) {
    if (currentVisibleActions) hideActions();
    const actions = document.getElementById(`actions-${entry}`);
    actions.style.display = 'block';
    const rect = event.target.getBoundingClientRect();
    actions.style.left = `${event.clientX - rect.left + 10}px`;
    actions.style.top = `${event.clientY - rect.top}px`;
    currentVisibleActions = actions;
}

function hideActions() {
    if (currentVisibleActions) {
        currentVisibleActions.style.display = 'none';
        currentVisibleActions = null;
    }
}

// API helpers
async function makeRequest(endpoint, data) {
    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({path: currentDirectoryName, ...data})
        });
        if (!response.ok) {
            throw new Error(`Failed to ${endpoint.slice(1)}`);
        }
        return response;
    } catch (error) {
        console.error(error);
        alert(error.message);
        return null;
    }
}

// Action handlers
async function createSubdirectory() {
    hideActions();
    const name = prompt("Enter subdirectory name:");
    if (name) {
        const response = await makeRequest('/createSubdirectory', {name});
        if (response) window.location.reload();
    }
}

async function searchSubdirectory(subdirName) {
    hideActions();
    const searchText = prompt("Enter text to search:");
    if (searchText) {
        // redirect to search results page
        window.location.href = `/search?path=${encodeURIComponent(subdirName)}&q=${encodeURIComponent(searchText)}`;
    }
}

async function recentlyModified() {
    hideActions();
    window.location.href = `/recent`;
}

async function createMarkdown() {
    hideActions();
    let fileName = prompt("Enter markdown file name (without .md extension):");
    if (!fileName) return;
    if (fileName.endsWith('.md')) fileName = fileName.slice(0, -3);
    const response = await makeRequest('/createMarkdown', {name: `${fileName}.md`});
    if (response) window.location.href = `/markdown?filename=${encodeURIComponent(currentDirectoryName)}/${encodeURIComponent(fileName)}.md`;
}

async function renameEntry(oldName) {
    hideActions();
    const newName = prompt(`Enter new name for ${oldName}:`, oldName);
    if (newName) {
        const response = await makeRequest('/renameEntry', {oldName, newName});
        if (response) window.location.reload();
    }
}

async function copyFileEntry(oldName) {
    hideActions();
    const namePart = oldName.split('.').slice(0, -1).join('.') || oldName;
    const extensionPart = oldName.includes('.') ? '.' + oldName.split('.').pop() : '';
    const newName = prompt(`Copy ${oldName} to:`, namePart + '_copy' + extensionPart);
    if (newName) {
        const response = await makeRequest('/copyFileEntry', {oldName, newName});
        if (response) window.location.reload();
    }
}

async function deleteEntry(name) {
    hideActions();
    if (confirm(`Are you sure you want to delete ${name}?`)) {
        const response = await makeRequest('/deleteEntry', {name});
        if (response) window.location.reload();
    }
}

function renderPlantUml(name) {
    hideActions();
    window.location.href = `/plantuml?filename=${encodeURIComponent(currentDirectoryName)}/${encodeURIComponent(name)}`;
}

async function uploadFile(file) {
    if (!file) return;
    const formData = new FormData();
    formData.append('file', file);
    formData.append('path', currentDirectoryName);
    const response = await fetch('/uploadFile', {method: 'POST', body: formData});
    if (response.ok) window.location.reload();
    else alert('Failed to upload file');
}

function writeToClipboard(text) {
    if (!text) return;
    const clipboardItem = new ClipboardItem({"text/plain": text});
    navigator.clipboard.write([clipboardItem])
        .then(() => alert("location copied to clipboard"));
}

// Event listeners
document.addEventListener('DOMContentLoaded', () => {
    // Entry link hover
    document.querySelectorAll('.entry-link').forEach(link => {
        link.addEventListener('mouseenter', (e) => {
            clearTimeout(hoverTimer);
            hoverTimer = setTimeout(() => showActions(e.target.dataset.entry, e), 2000);
        });
        link.addEventListener('mouseleave', () => clearTimeout(hoverTimer));
    });

    // Hide actions when clicking outside
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.entry-link') && !e.target.closest('.entry-actions')) hideActions();
    });

    // Prevent action button propagation
    document.querySelectorAll('.entry-actions').forEach(actionDiv => {
        actionDiv.addEventListener('click', e => e.stopPropagation());
    });

    // File upload handlers
    const pasteArea = document.getElementById('paste-area');

    pasteArea.addEventListener('dragover', e => {
        e.preventDefault();
        e.stopPropagation();
        e.target.style.background = '#e1e1e1';
    });

    pasteArea.addEventListener('dragleave', e => {
        e.preventDefault();
        e.stopPropagation();
        e.target.style.background = '';
    });

    pasteArea.addEventListener('drop', e => {
        e.preventDefault();
        e.stopPropagation();
        e.target.style.background = '';
        if (e.dataTransfer.files.length > 0) uploadFile(e.dataTransfer.files[0]);
    });

    // Set entry widths
    const entryElements = document.querySelectorAll('.entry-main');
    let maxWidth = Math.max(...Array.from(entryElements).map(el => {
        el.style.width = 'auto';
        const width = el.scrollWidth;
        el.style.width = '';
        return width;
    }));
    entryElements.forEach(div => div.style.width = `${maxWidth + 30}px`);
});
