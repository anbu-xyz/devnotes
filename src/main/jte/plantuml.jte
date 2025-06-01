@param String title
@param String plantumlFile

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">

    <title>${title}</title>
    <style>
        body {
            background-color: black;
        }
    </style>
</head>
<body class="p-8">

<h1>${title}</h1>

<img src="/plantuml?filename=${plantumlFile}">

</body>
</html>