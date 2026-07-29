package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedSpansTest {

    @Test
    void maskReplacesDoubleBraceSpans() {
        ProtectedSpans.Masked masked = ProtectedSpans.mask("hi {{Steve}} and {{Alex}}");
        assertEquals("hi __CTPROT_0__ and __CTPROT_1__", masked.text());
        assertEquals(List.of("Steve", "Alex"), masked.originals());
    }

    @Test
    void restorePutsOriginalsBack() {
        ProtectedSpans.Masked masked = ProtectedSpans.mask("say {{hello}} please");
        String fakeTranslated = "diga __CTPROT_0__ por favor";
        assertEquals("diga hello por favor", masked.restore(fakeTranslated));
    }

    @Test
    void unwrapStripsMarkersWithoutTranslate() {
        assertEquals("keep Steve name", ProtectedSpans.unwrap("keep {{Steve}} name"));
    }

    @Test
    void hasMarkersDetectsPresence() {
        assertTrue(ProtectedSpans.hasMarkers("a {{b}} c"));
        assertFalse(ProtectedSpans.hasMarkers("a b c"));
        assertFalse(ProtectedSpans.hasMarkers("{{"));
    }

    @Test
    void emptyAndNestedBraceEdgeCases() {
        assertEquals("nothing", ProtectedSpans.mask("nothing").text());
        // No nesting support — inner {{ is not a valid open for our pattern
        ProtectedSpans.Masked nested = ProtectedSpans.mask("x {{a b}} y");
        assertEquals("x __CTPROT_0__ y", nested.text());
        assertEquals("a b", nested.originals().getFirst());
    }

    @Test
    void restoreToleratesSpacedPlaceholder() {
        ProtectedSpans.Masked masked = ProtectedSpans.mask("{{TAG}}");
        assertEquals("TAG", masked.restore("__ CTPROT _ 0 __"));
    }

    @Test
    void maskAutoProtectsGamingSlang() {
        ProtectedSpans.Masked masked = ProtectedSpans.mask("gg nice bedwars game");
        assertEquals(List.of("gg", "bedwars"), masked.originals());
        assertEquals("__CTPROT_0__ nice __CTPROT_1__ game", masked.text());
    }
}
