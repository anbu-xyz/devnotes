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