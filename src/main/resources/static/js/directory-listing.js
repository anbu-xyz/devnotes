let hoverTimer;

document.querySelectorAll('.entry-link').forEach(link => {
    link.addEventListener('mouseenter', (e) => {
        const entry = e.target.dataset.entry;
        hoverTimer = setTimeout(() => {
            document.getElementById(`actions-${entry}`).style.display = 'block';
        }, 5000);
    });

    link.addEventListener('mouseleave', () => {
        clearTimeout(hoverTimer);
        document.querySelectorAll('.entry-actions').forEach(actions => {
            actions.style.display = 'none';
        });
    });
});

function createSubdirectory() {
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