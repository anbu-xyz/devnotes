@param String javascriptContent
@param String title

<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" type="text/css" href="/css/render.css">
    <link rel="stylesheet" href="/generated/css/prism-tomorrow.min.css">

    <script src="/generated/js/prism-bundle.js"></script>

    <title>${title}</title>
</head>
<body class="p-8">
<h1>${title}</h1>
<pre><code class="language-javascript">$unsafe{javascriptContent}</code></pre>

<script>Prism.highlightAll();</script>

</body>
</html>