@param String javascriptContent
@param String title

<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.11.1/build/styles/default.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.11.1/build/styles/github-dark.min.css">

    <script src="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.11.1/build/highlight.min.js"></script>
    <script src="https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@11.11.1/build/languages/javascript.min.js"></script>

    <title>${title}</title>
    <style>
        body {
            background-color: black;
        }
    </style>
</head>
<body class="p-8">
<pre><code class="language-javascript">$unsafe{javascriptContent}</code></pre>

<script>hljs.highlightAll();</script>

</body>
</html>