@param String title
@param String plantumlFile

<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" type="text/css" href="/css/render.css">
    <link rel="stylesheet" href="/generated/css/font-awesome.min.css">
    <link rel="stylesheet" href="/generated/css/prism-tomorrow.min.css">

    <script src="/generated/js/prism-bundle.js"></script>
    <!-- TODO: add support for plantuml -->
<%--    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-plant-uml.min.js"></script>--%>
    <title>${title}</title>
</head>
<body class="p-8">

<h1>${title}</h1>

<img src="/plantuml?filename=${plantumlFile}">

</body>
</html>