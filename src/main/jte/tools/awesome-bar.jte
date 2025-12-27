@import java.util.List

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Awesome bar</title>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <script src="https://unpkg.com/htmx.org@1.9.12"></script>
</head>
<body>
<div class="container">
    <h1>Awesome bar</h1>

    <div class="awesome-bar-form" id="todo-form">
        <form hx-post="/awesome-bar/todo" hx-swap="none">
            <div class="form-group">
                <label for="todoText">Add item to fleeting todo list</label>
                <input type="text" id="todoText" name="todoText" required
                       placeholder="todo item" class="form-input" autocomplete="off">
                <button type="submit" class="btn-blue-glow">Add</button>
                <span id="flash-message" style="display:none;"></span>
            </div>
        </form>
    </div>

    <div class="awesome-bar-form" id="search-form">
        <form action="/search" method="get">
            <div class="form-group">
                <label for="searchText">Search for text</label>
                <input type="text" id="searchText" name="q" required
                       placeholder="search text" class="form-input" autocomplete="off">
                <button type="submit" class="btn-blue-glow">Search</button>
            </div>
        </form>
    </div>

    <div class="awesome-bar-form" id="search-location-form">
        <form action="/searchLocation" method="get">
            <div class="form-group">
                <label for="searchText">Search for location</label>
                <input type="text" id="searchText" name="q" required
                       placeholder="location" class="form-input" autocomplete="off">
                <button type="submit" class="btn-blue-glow">Search</button>
            </div>
        </form>
    </div>

</div>

<script>
    document.body.addEventListener('htmx:afterRequest', function(evt) {
        var flash = document.getElementById('flash-message');
        var form = evt.detail.requestConfig.elt;
        if (evt.detail.xhr.status === 201) {
            flash.textContent = 'success';
            flash.style.display = 'inline';
            setTimeout(function() {
                flash.style.display = 'none';
            }, 5000);
            form.reset();
        } else {
            flash.textContent = 'Error!';
            flash.style.display = 'inline';
        }
    });
</script>

</body>
</html>