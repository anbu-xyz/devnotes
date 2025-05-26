function saveContent(redirect= true) {
    if (!easyMDE) {
        console.error('easyMDE is not defined');
        return;
    }
    const content = easyMDE.value();
    const filename = document.getElementById('md-file-path').textContent;
    const lastModifiedTime = document.getElementById('md-last-modified-time').textContent;

    // console.log("Saving content to " + filename);
    const uri = '/saveMarkdown?filename=' + encodeURIComponent(filename) +
         '&lastModifiedTime=' + encodeURIComponent(lastModifiedTime);
    fetch(uri, {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
        },
        body: content
    })
        .then(response => response.text())
        .then(result => {
            if(redirect) {
                location.href = '/markdown?filename=' + filename + '&edit=false';
            }
        })
        .catch(error => console.error('Error:', error));
}

var easyMDE;
function downloadExcel(outputFileName, markdownFileName) {
    window.location.href = '/downloadExcel?outputFileName=' + outputFileName + '&markdownFileName=' + markdownFileName;
}

function h1IsAbsent() {
    return document.getElementById('viewContent').querySelector('h1') == null;
}

document.addEventListener('htmx:afterRequest', function(evt) {
    easyMDE = new EasyMDE({ element: document.querySelector("#editor") });
})

document.addEventListener('DOMContentLoaded', function() {
    const viewContent = document.getElementById('viewContent');
    const h1 = viewContent.querySelector('h1');

    if (h1) {
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
            document.getElementById('editButton').click();
        };

        h1.insertBefore(folderLink, h1.firstChild);
        h1.appendChild(editLink);

        // hide the edit button
        document.getElementById('editButton').style.display = 'none';
    }
});

document.addEventListener('keydown', function(e) {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault(); // Prevent the browser's save dialog

        if (document.querySelector('#saveButton').style.display !== 'none') {
            document.getElementById('saveButton').click();
        }
    }
});

document.addEventListener('keydown', function(e) {
    if ((e.ctrlKey || e.metaKey) && e.key === 'e') {
        e.preventDefault(); // Prevent the browser default action

        const element = document.querySelector('#viewContent > h1 > a:nth-child(2) > i');
        if (element) {
            element.click();
        }
    }
});

(function() {
    setInterval(function() {
        if (document.getElementById('autoSaveCheckbox').checked) {
            saveContent(false);
        }
    }, 60000);
})();
