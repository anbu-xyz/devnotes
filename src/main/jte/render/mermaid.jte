@param String mermaidCodeContent
@param String title

<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" type="text/css" href="/css/render.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/themes/prism-tomorrow.min.css">

    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/prism.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.25.0/components/prism-mermaid.min.js"></script>

    <title>${title}</title>
</head>
<body class="p-8">
<h1>${title}</h1>
<pre><code class="language-mermaid">$unsafe{mermaidCodeContent}</code></pre>

<script>Prism.highlightAll();</script>

</body>
</html>