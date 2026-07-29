package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GamingSlangGlossaryTest {

    @Test
    void wrapsKnownTermWholeWord() {
        assertEquals(
                "that was a good {{bedwars}} game",
                GamingSlangGlossary.autoWrap("that was a good bedwars game"));
    }

    @Test
    void wrapsMultipleTermsPreservingCase() {
        String result = GamingSlangGlossary.autoWrap("GG well played, watch that AGGRO");
        assertEquals("{{GG}} well played, watch that {{AGGRO}}", result);
    }

    @Test
    void doesNotMatchPartialWords() {
        assertEquals("aggressive", GamingSlangGlossary.autoWrap("aggressive"));
    }

    @Test
    void leavesExistingProtectedSpansUntouched() {
        assertEquals(
                "hi {{Steve}}, {{gg}} on that {{bedwars}} win",
                GamingSlangGlossary.autoWrap("hi {{Steve}}, gg on that bedwars win"));
    }

    @Test
    void doesNotDoubleWrapTermInsideExistingSpan() {
        assertEquals(
                "say {{gg well played}} now",
                GamingSlangGlossary.autoWrap("say {{gg well played}} now"));
    }

    @Test
    void handlesNullAndEmpty() {
        assertEquals("", GamingSlangGlossary.autoWrap(null));
        assertEquals("", GamingSlangGlossary.autoWrap(""));
    }
}
