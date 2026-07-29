package com.tanman.chattranslator.client.translation;

public final class OutgoingTranslationDecision {

    private OutgoingTranslationDecision() {
    }

    public sealed interface Outcome
            permits SendUnmodified, SendTranslated, AwaitingDownload, NoReverseModel {
    }

    public record SendUnmodified() implements Outcome {
    }

    public record SendTranslated(String translatedText) implements Outcome {
    }

    public record AwaitingDownload(String targetLanguage) implements Outcome {
    }

    public record NoReverseModel(String targetLanguage) implements Outcome {
    }

    public static Outcome decide(
            String targetLanguage,
            boolean isCached,
            boolean reverseModelKnownMissing,
            TranslationResult inferenceResultIfRun
    ) {
        if (targetLanguage.equals("en")) {
            return new SendUnmodified();
        }
        if (reverseModelKnownMissing) {
            return new NoReverseModel(targetLanguage);
        }
        if (!isCached) {
            return new AwaitingDownload(targetLanguage);
        }
        if (inferenceResultIfRun != null && inferenceResultIfRun.success()) {
            return new SendTranslated(inferenceResultIfRun.translatedText());
        }
        return new NoReverseModel(targetLanguage);
    }
}
