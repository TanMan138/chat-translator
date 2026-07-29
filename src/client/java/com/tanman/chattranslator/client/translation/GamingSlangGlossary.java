package com.tanman.chattranslator.client.translation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-protects common Minecraft/gaming slang so it survives translation
 * literally, the same way a player-typed {@code {{Steve}}} does.
 *
 * <p>Prompt-based "keep this slang untranslated" instructions are unreliable
 * across small local models (tested on qwen2.5 1.5b/3b/7b) and unnecessary for
 * hosted backends that don't take a free-form system prompt at all, so this
 * wraps known terms deterministically before {@link ProtectedSpans#mask}
 * ever runs.
 */
public final class GamingSlangGlossary {

    /** Extend freely; matching is whole-word and case-insensitive. */
    private static final List<String> TERMS = List.of(
            "bedwars", "skywars", "hcf", "pvp", "pve", "afk", "brb", "gg", "glhf", "gl", "hf",
            "noob", "rekt", "ez", "pog", "poggers", "sus",
            "gank", "aggro", "kite", "griefing", "grief", "griefer",
            "tnt", "creeper", "enderman", "ender", "nether", "overworld",
            "respawn", "spawn", "mob", "mobs", "loot", "grind", "grinding",
            "kd", "kda", "dps", "op", "lag", "ping"
    );

    private static final List<Pattern> PATTERNS = TERMS.stream()
            .map(term -> Pattern.compile("(?i)\\b(" + Pattern.quote(term) + ")\\b"))
            .toList();

    private GamingSlangGlossary() {
    }

    /**
     * Wraps every glossary term found outside existing {@code {{...}}} spans
     * in its own {@code {{...}}}, preserving the original casing. Text
     * already inside a protected span (including any {@code {{...}}} the
     * player typed themselves) is left untouched.
     */
    public static String autoWrap(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        Matcher marker = ProtectedSpans.MARKER.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (marker.find()) {
            out.append(wrapTerms(text.substring(last, marker.start())));
            out.append(marker.group());
            last = marker.end();
        }
        out.append(wrapTerms(text.substring(last)));
        return out.toString();
    }

    private static String wrapTerms(String segment) {
        if (segment.isEmpty()) {
            return segment;
        }
        String result = segment;
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(result);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("{{" + matcher.group(1) + "}}"));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }
}
