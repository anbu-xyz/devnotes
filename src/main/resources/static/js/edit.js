let simplemde;
let isEditing = false;

function toggleEdit() {
    const viewContent = document.getElementById('viewContent');
    const editContent = document.getElementById('editContent');
    const editButton = document.getElementById('editButton');

    if (!isEditing) {
        viewContent.style.display = 'none';
        editContent.style.display = 'block';
        editButton.innerHTML = '<i class="fas fa-save"></i>Save';
        if (!simplemde) {
            simplemde = new SimpleMDE({ element: document.getElementById("editor") });
        }
    } else {
        saveContent();
        viewContent.style.display = 'block';
        editContent.style.display = 'none';
        editButton.innerHTML = '<i class="fas fa-edit"></i>Edit';
    }

    isEditing = !isEditing;
}

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