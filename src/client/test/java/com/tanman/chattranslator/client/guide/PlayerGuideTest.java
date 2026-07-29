package com.tanman.chattranslator.client.guide;

import com.tanman.chattranslator.client.config.CloudProvider;
import com.tanman.chattranslator.client.config.TranslationBackendType;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.ModelManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerGuideTest {

    @TempDir
    Path modelsDir;

    @Test
    void hasSevenPages() {
        assertEquals(7, PlayerGuide.pageCount());
    }

    @Test
    void pagesHaveTitlesAndBodies() {
        for (int i = 0; i < PlayerGuide.pageCount(); i++) {
            PlayerGuide.Page page = PlayerGuide.page(i);
            assertFalse(page.title().isBlank());
            assertFalse(page.body().isBlank());
        }
    }

    @Test
    void methodPageMentionsLangblyAndProsCons() {
        String body = PlayerGuide.page(3).body() + PlayerGuide.page(4).body();
        assertTrue(body.toLowerCase().contains("langbly"));
        assertTrue(body.contains("Good:"));
        assertTrue(body.contains("Bad:"));
        assertTrue(body.contains("DeepL") || body.contains("DEEPL"));
    }

    @Test
    void helpCheatSheetMentionsGuideAndHover() {
        String help = PlayerGuide.HELP_CHEAT_SHEET.toLowerCase();
        assertTrue(help.contains("guide"));
        assertTrue(help.contains("hover"));
        assertTrue(PlayerGuide.HELP_CHEAT_SHEET.length() < 800);
    }

    @Test
    void backendLabelsArePlainEnglish() {
        TranslatorConfig config = new TranslatorConfig();
        assertEquals("on your computer", PlayerGuide.backendLabel(config));

        config.backend = TranslationBackendType.MANAGED_CLOUD;
        config.cloudProvider = CloudProvider.DEEPL;
        assertEquals("online (DeepL)", PlayerGuide.backendLabel(config));

        config.cloudProvider = CloudProvider.GOOGLE;
        assertEquals("online (Google Translate)", PlayerGuide.backendLabel(config));

        config.backend = TranslationBackendType.CUSTOM;
        assertEquals("your own server (Ollama)", PlayerGuide.backendLabel(config));
    }

    @Test
    void formatStatusIncludesMethodLanguageAndGuide() {
        TranslatorConfig config = new TranslatorConfig();
        TranslationState state = new TranslationState();
        state.setManualTarget("ru");
        state.setLatinOutgoing(true);
        ModelManager models = new ModelManager(modelsDir);

        String status = PlayerGuide.formatStatus(state, models, config);
        assertTrue(status.contains("on your computer"));
        assertTrue(status.contains("locked to ru"));
        assertTrue(status.contains("Latin letters"));
        assertTrue(status.contains("/translate guide"));
    }

    @Test
    void enumLabelsAreBeginnerFriendly() {
        assertTrue(TranslationBackendType.ON_DEVICE.label().toLowerCase().contains("computer"));
        assertTrue(TranslationBackendType.MANAGED_CLOUD.label().toLowerCase().contains("online"));
        assertTrue(TranslationBackendType.CUSTOM.label().toLowerCase().contains("ollama"));
        assertTrue(CloudProvider.GOOGLE.label().toLowerCase().contains("langbly"));
        assertFalse(TranslationBackendType.ON_DEVICE.label().contains("OPUS"));
        assertFalse(TranslationBackendType.MANAGED_CLOUD.label().contains("BYOK"));
    }
}
