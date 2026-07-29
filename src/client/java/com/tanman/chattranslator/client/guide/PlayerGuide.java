package com.tanman.chattranslator.client.guide;

import com.tanman.chattranslator.client.config.CloudProvider;
import com.tanman.chattranslator.client.config.TranslationBackendType;
import com.tanman.chattranslator.client.config.TranslatorConfig;
import com.tanman.chattranslator.client.state.TranslationState;
import com.tanman.chattranslator.client.translation.ModelManager;

/**
 * Shared plain-English copy for the Guide screen, YACL, commands, and README.
 */
public final class PlayerGuide {

    public record Page(String title, String body) {
    }

    private static final Page[] PAGES = {
            new Page("What Chat Translator does",
                    """
                    This mod helps you read and send chat in other languages.
                    
                    Reading: foreign chat stays on screen. Press T, hover a line, and English \
                    appears in the tooltip.
                    
                    Sending (optional): type in English and the mod can translate your message \
                    before it is sent — if you turn that on in settings.
                    
                    Open this guide anytime: /translate guide
                    Or: Mods → Chat Translator → Config → Start here."""),
            new Page("How to read chat",
                    """
                    Step 1: Press T to open chat.
                    Step 2: Move your mouse over a message you want in English.
                    Step 3: Wait a moment — English shows in the hover tooltip.
                    
                    First time for a language may download a small language pack \
                    (only if you use "On your computer" mode).
                    
                    Clicking the line also works, but hovering is enough."""),
            new Page("How to send translated chat",
                    """
                    Step 1: Pick who you are talking to.
                      • /translate ru  (Russian example)
                      • Or turn on "auto" so the mod follows the last language you read.
                    
                    Step 2: Type your message in English as usual.
                    Step 3: Press Enter — the mod translates it before sending.
                    
                    On public servers, keep "Latin letters" on (default) so AntiSpam \
                    plugins do not block Cyrillic or other scripts. Example: Privet instead of Привет."""),
            new Page("Pick how translation runs",
                    """
                    ON YOUR COMPUTER (recommended for most players)
                      Good: free after download, works offline, no account.
                      Bad: first use downloads files; uses disk space.
                      Set in Config → How to translate.
                    
                    DEEPL (you bring your own API key)
                      Good: high quality; easy signup at deepl.com.
                      Bad: needs internet and a key you pay for or get free tier.
                      Set in Config → Online services → DeepL.
                    
                    GOOGLE TRANSLATE (you bring your own API key)
                      Good: many languages; also works with Langbly-style keys.
                      Bad: needs internet and a Google Cloud key.
                      Set in Config → Online services → Google.
                    
                    YOUR OWN SERVER (Ollama)
                      Good: you control everything.
                      Bad: you must install and run Ollama yourself.
                      Set in Config → Your own server."""),
            new Page("Other services (not built-in buttons)",
                    """
                    Langbly or similar "give me a Google key" services:
                      → In Config, pick "Online service" and Google Translate.
                      → Paste the API key they gave you. No separate Langbly button needed.
                    
                    LibreTranslate or other self-hosted translate sites:
                      → Only if you can run something like Ollama on your own PC or VPS.
                      → This mod does not plug into LibreTranslate directly today.
                    
                    ChatGPT, Gemini, or other chat websites:
                      → Not wired into this mod. Use On your computer, DeepL, or Google instead."""),
            new Page("Commands (quick list)",
                    """
                    /translate guide     — open this full guide
                    /translate help      — short cheat sheet in chat
                    /translate status    — what mode you are in now
                    /translate ru        — send outgoing chat in Russian (example)
                    /translate auto      — follow last language you read
                    /translate latin     — send romanized letters (default, safest)
                    /translate native    — send real foreign letters
                    /translate models    — list downloaded language packs
                    /translate clear all — delete saved downloads
                    
                    Protect a word from translation: hello {{Steve}}"""),
            new Page("Tips",
                    """
                    • New player? Leave "On your computer" and Latin letters on. \
                    Hover chat to read; use /translate <code> only if you want to reply.
                    
                    • Messages blocked by the server? Try /translate latin.
                    
                    • API keys live in config/chat-translator.json — do not share that file.
                    
                    • Full settings: Mods → Chat Translator → Config (needs Mod Menu + YACL).""")
    };

    public static final String START_HERE_BLURB = """
            Hover chat (press T) to read English. Optional: translate what you send.
            Most players: leave "On your computer" selected. Open the full guide below \
            for step-by-step help and how to use DeepL, Google, or your own server.""";

    public static final String HELP_CHEAT_SHEET = """
            Chat Translator — quick help
            Read: press T, hover a line → English on tooltip
            Send: /translate <code> then type English (e.g. /translate ru)
            /translate guide — full guide  |  /translate status — current settings
            /translate latin — safe letters (default)  |  /translate native — real script
            Config: Mods → Chat Translator → Config
            Common codes: fr French, de German, es Spanish, ru Russian, ja Japanese""";

    private PlayerGuide() {
    }

    public static int pageCount() {
        return PAGES.length;
    }

    public static Page page(int index) {
        return PAGES[index];
    }

    public static String backendLabel(TranslatorConfig config) {
        config.normalize();
        return switch (config.backend) {
            case ON_DEVICE -> "on your computer";
            case MANAGED_CLOUD -> config.cloudProvider == CloudProvider.GOOGLE
                    ? "online (Google Translate)"
                    : "online (DeepL)";
            case CUSTOM -> "your own server (Ollama)";
        };
    }

    public static String formatStatus(
            TranslationState state,
            ModelManager modelManager,
            TranslatorConfig config
    ) {
        String language = state.isAuto()
                ? "auto — replies follow the last language you read (currently: "
                + state.getCurrentTargetLanguage().orElse("none yet") + ")"
                : "locked to " + state.getCurrentTargetLanguage().orElse("not set");
        String script = state.isLatinOutgoing()
                ? "Latin letters (e.g. Privet — safest on public servers)"
                : "real foreign letters (may be blocked on some servers)";
        return "Translation method: " + backendLabel(config)
                + "\nOutgoing language: " + language
                + "\nOutgoing style: " + script
                + "\nSaved downloads: " + modelManager.formatTotalSize()
                + " — /translate models for details"
                + "\nFull guide: /translate guide";
    }
}
