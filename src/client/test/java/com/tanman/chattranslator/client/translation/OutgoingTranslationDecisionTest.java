package com.tanman.chattranslator.client.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutgoingTranslationDecisionTest {

    @Test
    void englishTargetSendsUnmodified() {
        var outcome = OutgoingTranslationDecision.decide("en", true, false, null);
        assertInstanceOf(OutgoingTranslationDecision.SendUnmodified.class, outcome);
    }

    @Test
    void knownMissingReverseModelReturnsNoReverseModel() {
        var outcome = OutgoingTranslationDecision.decide("zu", false, true, null);
        assertInstanceOf(OutgoingTranslationDecision.NoReverseModel.class, outcome);
        assertEquals("zu", ((OutgoingTranslationDecision.NoReverseModel) outcome).targetLanguage());
    }

    @Test
    void uncachedModelReturnsAwaitingDownload() {
        var outcome = OutgoingTranslationDecision.decide("fr", false, false, null);
        assertInstanceOf(OutgoingTranslationDecision.AwaitingDownload.class, outcome);
        assertEquals("fr", ((OutgoingTranslationDecision.AwaitingDownload) outcome).targetLanguage());
    }

    @Test
    void cachedModelWithSuccessfulInferenceReturnsSendTranslated() {
        var result = TranslationResult.success("Bonjour", "Hello");
        var outcome = OutgoingTranslationDecision.decide("fr", true, false, result);
        assertInstanceOf(OutgoingTranslationDecision.SendTranslated.class, outcome);
        assertEquals("Bonjour", ((OutgoingTranslationDecision.SendTranslated) outcome).translatedText());
    }

    @Test
    void cachedModelWithFailedInferenceReturnsNoReverseModel() {
        var result = TranslationResult.failure("Hello");
        var outcome = OutgoingTranslationDecision.decide("fr", true, false, result);
        assertInstanceOf(OutgoingTranslationDecision.NoReverseModel.class, outcome);
    }
}
