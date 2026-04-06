package uk.anbu.devnotes.types;

import java.util.List;

public record SlidesDeck(DeckMetadata deckMetadata, List<Slide> slides) {
}