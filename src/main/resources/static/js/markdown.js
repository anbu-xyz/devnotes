var easyMDE;

async function saveContent() {
    if (!easyMDE) {
        console.error('easyMDE is not defined');
        return;
    }
    const content = easyMDE.value();
    const filename = document.getElementById('md-file-path').textContent;
    const lastModifiedTime = document.getElementById('md-last-modified-time').textContent;

    console.debug(`calling save endpoint for file ${filename}`);
    const uri = '/saveMarkdown?filename=' + encodeURIComponent(filename) +
         '&lastModifiedTime=' + encodeURIComponent(lastModifiedTime);
    var result = await fetch(uri, {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
        },
        body: content
    });

    console.debug(`result of save call: ${result.statusText} ${result.status}`);
}

function downloadExcel(outputFileName, markdownFileName) {
    window.location.href = '/downloadExcel?outputFileName=' + outputFileName + '&markdownFileName=' + markdownFileName;
}

function createHomeLink(elementId) {
    const viewContent = document.getElementById(elementId);
    const h1 = viewContent.querySelector('h1');
    const editButtonSelector = "#editButton";
    const editButton = viewContent.querySelector(editButtonSelector);

    if (!h1) {
        console.info('Markdown file does not have a title. Skipping button creation at the top of the page.');
        return;
    }
    if (!editButton) {
        console.error(`Edit button not found: ${editButtonSelector}`);
        return;
    }

    // Create home link
    const folderLink = document.createElement('a');
    folderLink.href = 'renderDirectoryContents?directoryName=' +
        encodeURIComponent('${markdownFile}'.split('/').slice(0, -1).join('/'));

    folderLink.classList.add('text-blue-500', 'hover:text-blue-700', 'mr-2');
    folderLink.innerHTML = '<i class="fas fa-folder-open" style="padding-right: 0.2em"></i>';

    // Create edit link
    const editLink = document.createElement('a');
    editLink.href = '#';
    editLink.classList.add('text-blue-500', 'hover:text-blue-700');
    editLink.innerHTML = '<i class="fas fa-edit" style="padding-left: 0.2em"></i>';
    editLink.onclick = function(e) {
        e.preventDefault();
        editButton.click();
    };

    h1.insertBefore(folderLink, h1.firstChild);
    h1.appendChild(editLink);

    // hide the edit button
    editButton.style.display = 'none';
}

function attachEasyMdeOn(elementId) {
    const originalTextArea = document.getElementById(elementId);
    console.debug(`enableEasyMDE creating editor on element ${elementId}`);
    if (!originalTextArea) {
        console.error(`Editor element not found: ${elementId}`);
        return;
    }
    easyMDE = new EasyMDE({ element: originalTextArea});
}

// -----------------------------------------------------------------------------
// Event listeners
// -----------------------------------------------------------------------------
document.addEventListener('keydown', function(e) {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        console.debug("Ctrl+S pressed");
        e.preventDefault(); // Prevent the browser's save dialog

        const saveButtonSelector = '#saveButton';
        const saveButton = document.querySelector(saveButtonSelector);
        if (saveButton) {
            if (saveButton.style.display !== 'none') {
                saveButton.click();
            }
        } else {
            console.error(`Save button not found: ${saveButtonSelector}`);
        }
    }
});

document.addEventListener('keydown', function(e) {
    if ((e.ctrlKey || e.metaKey) && e.key === 'e') {
        console.debug("Ctrl+E pressed");
        e.preventDefault(); // Prevent the browser default action

        const editButtonSelector = '#markdownViewer > h1 > a:nth-child(2) > i';
        const editButton = document.querySelector(editButtonSelector);
        if (editButton) {
            editButton.click();
        } else {
            console.error(`Edit button not found: ${editButtonSelector}`);
        }
    }
});

document.body.addEventListener('htmx:afterSwap', function(evt) {
    if (evt.target.id === "markdownViewer") {
        createHomeLink("markdownViewer");
        console.debug("Markdown viewer content loaded");
    }
    if (evt.target.id === "markdownEditor") {
        attachEasyMdeOn('easyMdeEditor');
        console.debug("Markdown editor content loaded");
    }
});
// -----------------------------------------------------------------------------
// Autosave
// -----------------------------------------------------------------------------
(function() {
    setInterval(async function () {
        const autoSaveCheckbox = document.getElementById('autoSaveCheckbox');
        if (autoSaveCheckbox && autoSaveCheckbox.checked) {
            await saveContent();
        }
    }, 60000);
})();
