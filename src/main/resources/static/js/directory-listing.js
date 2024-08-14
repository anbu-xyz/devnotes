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