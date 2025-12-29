package uk.anbu.devnotes.markdown;

import org.commonmark.node.BulletList;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.commonmark.renderer.text.TextContentRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class TodoModifier {

    public static void addFleetingItem(Path todoFile, String contentToAdd) {
        try {
            contentToAdd = contentToAdd.trim();
            if (!Files.exists(todoFile)) {
                createNewTodoFile(todoFile, contentToAdd);
                return;
            }

            String markdown = Files.readString(todoFile);
            Parser parser = Parser.builder().build();
            Node document = parser.parse(markdown);
            boolean fleetingHeadingFound = false;

            Node node = document.getFirstChild();
            while (node != null) {
                if (node instanceof Heading fleetingHeading && fleetingHeading.getLevel() == 2) {
                    String headingText = getSimpleText(node);
                    if (headingText.startsWith("Fleeting")) {
                        fleetingHeadingFound = true;
                        node = node.getNext();
                        if (node != null) {
                            if (node instanceof BulletList bulletList) {
                                addToBulletList(bulletList, contentToAdd);
                                break;
                            }
                        }
                        addNewListUnderHeading(fleetingHeading, contentToAdd);
                        break;
                    }
                }

                node = node.getNext();
            }
            if (!fleetingHeadingFound) {
                insertNewHeading(document, contentToAdd);
            }

            Files.writeString(todoFile, nodeToMarkdownText(document), StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            throw new RuntimeException("Error processing markdown file: " + e.getMessage(), e);
        }
    }

    private static void addToBulletList(BulletList bulletList, String contentToAdd) {
        var newListItem = new ListItem();
        var htmlBlock = new HtmlBlock();
        htmlBlock.setLiteral(contentToAdd);
        newListItem.appendChild(htmlBlock);
        bulletList.appendChild(newListItem);
    }

    private static void addNewListUnderHeading(Heading fleetingHeading, String contentToAdd) {
        var newBulletList = new BulletList();
        addToBulletList(newBulletList, contentToAdd);
        fleetingHeading.insertAfter(newBulletList);
    }

    private static void insertNewHeading(Node document, String contentToAdd) {
        var fleetingHeading = new Heading();
        fleetingHeading.setLevel(2);
        fleetingHeading.appendChild(new Text("Fleeting"));
        document.appendChild(fleetingHeading);
        addNewListUnderHeading(fleetingHeading, contentToAdd);
    }

    private static void createNewTodoFile(Path todoFile, String contentToAdd) throws IOException {
        Files.createDirectories(todoFile.getParent());
        Files.createDirectories(todoFile.getParent());
        Files.createFile(todoFile);
        String content = """
                # Todo
                
                ## Fleeting
                
                """;

        if (!contentToAdd.isEmpty()) {
            content += "- " + contentToAdd;
        }
        Files.writeString(todoFile, content);
    }

    private static String nodeToMarkdownText(Node node) {
        MarkdownRenderer textRenderer = MarkdownRenderer.builder().build();
        return textRenderer.render(node);
    }

    private static String getSimpleText(Node node) {
        TextContentRenderer textRenderer = TextContentRenderer.builder().build();
        return textRenderer.render(node);
    }

}