let simplemde= new SimpleMDE({ autofocus: true, element: document.getElementById("editor") });


function saveContent() {
    const content = simplemde.value();
    const filename = window.location.search.split('filename=')[1];

    fetch('/saveMarkdown?filename=' + encodeURIComponent(filename), {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
        },
        body: content
    })
        .then(response => response.text())
        .then(result => {
            console.log(result);
            location.reload(); // Reload the page to show updated content
        })
        .catch(error => console.error('Error:', error));
}