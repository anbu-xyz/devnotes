@param String title
@param String plantumlFile

<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" type="text/css" href="/css/render.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/themes/prism-tomorrow.min.css">

    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/prism.min.js"></script>
    <!-- TODO: add support for plantuml -->
<%--    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-plant-uml.min.js"></script>--%>
    <title>${title}</title>
</head>
<body class="p-8">

<h1>${title}</h1>

<img src="/plantuml?filename=${plantumlFile}">

</body>
</html>