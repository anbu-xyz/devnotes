function saveContent(redirect= true) {
    if (!easyMDE) {
        console.error('easyMDE is not defined');
        return;
    }
    const content = easyMDE.value();
    const filename = window.location.search.split('filename=')[1].split('&')[0];

    // console.log("Saving content to " + filename);
    fetch('/saveMarkdown?filename=' + encodeURIComponent(filename), {
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