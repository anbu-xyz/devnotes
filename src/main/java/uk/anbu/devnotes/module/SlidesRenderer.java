package uk.anbu.devnotes.module;

import j2html.tags.ContainerTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.anbu.devnotes.types.DeckMetadata;
import uk.anbu.devnotes.types.Markdown;
import uk.anbu.devnotes.types.MarkdownFile;
import uk.anbu.devnotes.types.Slide;
import uk.anbu.devnotes.types.SlidesDeck;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static j2html.TagCreator.*;

@Slf4j
@RequiredArgsConstructor
public class SlidesRenderer {

    private final MarkdownRenderer markdownRenderer;

    /**
     * Renders a {@link SlidesDeck} to an HTML string consisting of a
     * {@code <div class="slide-deck">} containing one {@code <section class="slide">}
     * per slide.
     */
    public String render(SlidesDeck deck, MarkdownFile markdownFile, Map<String, String> queryParams) {
        var sections = deck.slides().stream()
                .map(slide -> renderSlide(slide, deck.deckMetadata(), markdownFile, queryParams))
                .collect(Collectors.toList());

        var deckDiv = div().withClass("slide-deck")
                .attr("data-theme", Objects.toString(deck.deckMetadata().theme(), "dark"))
                .attr("data-paginate", String.valueOf(deck.deckMetadata().paginate()))
                .attr("data-total-slides", String.valueOf(deck.slides().size()))
                .with(sections);

        return deckDiv.render();
    }

    private ContainerTag<?> renderSlide(Slide slide, DeckMetadata deckMeta,
                                        MarkdownFile markdownFile, Map<String, String> queryParams) {
        var renderResult = markdownRenderer.convertMarkdown(
                new Markdown(slide.rawMarkdown()), markdownFile, queryParams);

        var cssClasses = Stream.of("slide", deckMeta.cssClass(), slide.metadata().cssClass())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        var bg = slide.metadata().background() != null
                ? slide.metadata().background()
                : deckMeta.background();

        ContainerTag<?> section = tag("section")
                .withClass(cssClasses)
                .attr("data-slide-index", String.valueOf(slide.slideIndex()));

        if (bg != null) {
            section = section.withStyle("background: " + bg + ";");
        }

        section = section.with(
                div().withClass("slide-content").with(rawHtml(renderResult.html()))
        );

        var notes = slide.speakerNotes();
        if (notes != null && !notes.isBlank()) {
            section = section.with(
                    tag("aside").withClass("slide-notes").withText(notes)
            );
        }

        if (deckMeta.paginate()) {
            section = section.with(
                    div().withClass("slide-page-number")
                            .withText(String.valueOf(slide.slideIndex() + 1))
            );
        }

        return section;
    }
}