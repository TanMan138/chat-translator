package com.tanman.chattranslator.client.translation;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class LanguageDetector {

    private static final double CONFIDENCE_THRESHOLD = 0.5;

    // Lingua normalizes confidence values relative to the best-scoring language for
    // the given input, so the top entry is effectively always ~1.0 regardless of how
    // short or ambiguous the text is (verified empirically against lingua 1.2.2: a
    // 2-character input like "ok" still yields a top confidence of 1.0). The
    // CONFIDENCE_THRESHOLD check therefore cannot catch "too short to trust" inputs
    // on its own, so we additionally require a minimum amount of signal (trimmed
    // character length) before trusting the result at all.
    //
    // A naive cutoff of 3 was tried first but proven insufficient by empirical testing
    // against common English chat slang: single common words and short slang up to
    // ~13 characters are routinely misdetected as a confident non-English match, e.g.
    // (as observed against lingua 1.2.2, all with top confidence 1.0):
    //   "lol" -> TSWANA, "omg" -> ZULU, "wtf" -> NYNORSK, "brb" -> WELSH,
    //   "cool" -> GANDA, "amazing" -> ZULU, "goodbye" -> TAGALOG,
    //   "important" -> FRENCH, "brb gotta go" -> HUNGARIAN, "afk for a sec" -> DUTCH.
    // Reliable detection (correctly English for English input, correctly foreign for
    // foreign input) only consistently emerged around 15+ characters of real text in
    // that testing. 15 is therefore a deliberately conservative threshold: it trades
    // away the ability to detect very short foreign phrases in exchange for not
    // spamming translation attempts on common English chat slang, which is the
    // dominant failure mode in a Minecraft chat context.
    private static final int MIN_TEXT_LENGTH = 15;

    private final com.github.pemistahl.lingua.api.LanguageDetector delegate;

    public LanguageDetector() {
        this.delegate = LanguageDetectorBuilder.fromAllLanguages().build();
    }

    public Optional<String> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        if (text.trim().length() < MIN_TEXT_LENGTH) {
            return Optional.empty();
        }

        // computeLanguageConfidenceValues returns a SortedMap ordered by descending
        // confidence (verified against lingua 1.2.2 bytecode: the method's declared
        // return type is java.util.SortedMap, built via a compareByDescending
        // comparator), so the first entry is always the top result.
        Map<Language, Double> confidences = delegate.computeLanguageConfidenceValues(text);
        if (confidences.isEmpty()) {
            return Optional.empty();
        }

        Map.Entry<Language, Double> top = confidences.entrySet().iterator().next();
        Language topLanguage = top.getKey();
        double topConfidence = top.getValue();

        if (topLanguage == Language.ENGLISH || topLanguage == Language.UNKNOWN) {
            return Optional.empty();
        }
        if (topConfidence < CONFIDENCE_THRESHOLD) {
            return Optional.empty();
        }

        return Optional.of(topLanguage.getIsoCode639_1().toString().toLowerCase(Locale.ROOT));
    }
}
