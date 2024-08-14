let hoverTimer;
let currentVisibleActions = null;

function showActions(entry, event) {
    if (currentVisibleActions) {
        currentVisibleActions.style.display = 'none';
    }
    const actions = document.getElementById(`actions-${entry}`);
    actions.style.display = 'block';
    positionActions(actions, event);
    currentVisibleActions = actions;
}

function positionActions(actions, event) {
    const rect = event.target.getBoundingClientRect();
    actions.style.left = `${event.clientX - rect.left + 10}px`;
    actions.style.top = `${event.clientY - rect.top}px`;
}

function hideActions() {
    if (currentVisibleActions) {
        currentVisibleActions.style.display = 'none';
        currentVisibleActions = null;
    }
}

document.querySelectorAll('.entry-link').forEach(link => {
    link.addEventListener('mouseenter', (e) => {
        clearTimeout(hoverTimer);
        const entry = e.target.dataset.entry;
        hoverTimer = setTimeout(() => showActions(entry, e), 2000);
    });

    link.addEventListener('mouseleave', () => {
        clearTimeout(hoverTimer);
    });
});

// Add event listener to the document to hide actions when clicking outside
document.addEventListener('click', (e) => {
    if (!e.target.closest('.entry-link') && !e.target.closest('.entry-actions')) {
        hideActions();
    }
});

// Prevent immediate hiding when interacting with action buttons
document.querySelectorAll('.entry-actions').forEach(actionDiv => {
    actionDiv.addEventListener('click', (e) => {
        e.stopPropagation();
    });
});

function createSubdirectory() {
    hideActions();
    const subdirName = prompt("Enter subdirectory name:");
    if (subdirName) {
        fetch('/createSubdirectory', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `path=${encodeURIComponent(currentDirectoryName)}&name=${encodeURIComponent(subdirName)}`
        }).then(response => {
            if (response.ok) {
                window.location.reload();
            } else {
                alert('Failed to create subdirectory');
            }
        });
    }
}

function createMarkdown() {
    hideActions();
    const fileName = prompt("Enter markdown file name (without .md extension):");
    if (fileName) {
        fetch('/createMarkdown', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `path=${encodeURIComponent(currentDirectoryName)}&name=${encodeURIComponent(fileName)}.md`
        }).then(response => {
            if (response.ok) {
                window.location.href = `/markdown?filename=${encodeURIComponent(currentDirectoryName)}/${encodeURIComponent(fileName)}.md`;
            } else {
                alert('Failed to create markdown file');
            }
        });
    }
}

function renameEntry(oldName) {
    hideActions();
    const newName = prompt(`Enter new name for ${oldName}:`);
    if (newName) {
        fetch('/renameEntry', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `path=${encodeURIComponent(currentDirectoryName)}&oldName=${encodeURIComponent(oldName)}&newName=${encodeURIComponent(newName)}`
        }).then(response => {
            if (response.ok) {
                window.location.reload();
            } else {
                alert('Failed to rename entry');
            }
        });
    }
}

function deleteEntry(name) {
    hideActions();
    if (confirm(`Are you sure you want to delete ${name}?`)) {
        fetch('/deleteEntry', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `path=${encodeURIComponent(currentDirectoryName)}&name=${encodeURIComponent(name)}`
        }).then(response => {
            if (response.ok) {
                window.location.reload();
            } else {
                alert('Failed to delete entry');
            }
        });
    }
}