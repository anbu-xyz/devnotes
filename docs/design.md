# Design

## Main Classes

### MarkdownRenderer

The `MarkdownRenderer` class is responsible for converting Markdown text into HTML. 

`convertMarkdown()`: This method takes a string of Markdown text as input and returns rendered HTML string.

## Types

- `MarkdownFile(Path docsDirectory, String fileName)`
- `Markdown(String text)`
- `ImageFile(String path, String filename, String docsDirectory)`