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
            new Page("What Lingo does",
                    """
                    This mod helps you read and send chat in other languages.

                    Reading: foreign chat gets its English added to the same line, \
                    as it arrives. Nothing to press.

                    Sending (optional): type in English and the mod can translate your message \
                    before it is sent — if you turn that on in settings.

                    Open this guide anytime: /translate guide
                    Or: Mods → Lingo → Config → Start here."""),
            new Page("How to read chat",
                    """
                    Normally you do nothing: a foreign line shows up as
                      <Alice> bonjour tout le monde → hello everyone

                    A language you have never used needs its files once. Press T, hover \
                    that line, and the download starts (progress shows in chat). After \
                    that the language is instant and works offline.

                    Want it ready in advance? /translate download fr
                    Want the old hover-only behaviour? /translate read hover

                    (Downloads only happen in "On your computer" mode.)"""),
            new Page("How to send translated chat",
                    """
                    Step 1: Pick who you are talking to.
                      • /translate ru  (Russian example)
                      • Or turn on "auto" so the mod follows the last language you read.
                    
                    Step 2: Type your message in English as usual.
                    Step 3: Press Enter — the mod translates it before sending.
                    
                    On public servers, keep "Latin letters" on (default) so AntiSpam \
                    plugins do not block Cyrillic or other scripts. Example: Privet instead of Привет.
                    Russian and Ukrainian use familiar chat-style spelling; other languages use \
                    a general romanizer. Romanization always happens on your computer."""),
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
                      Good: many languages.
                      Bad: needs internet and a Google Cloud key (starts with AIza).
                      Set in Config → Online services → Google.
                    
                    LANGBLY (you bring your own API key)
                      Good: free tier, 130+ languages, 30-second signup, optional EU-only servers.
                      Bad: needs internet and a langbly.com account.
                      Set in Config → Online services → Langbly.
                    
                    YOUR OWN SERVER (Ollama)
                      Good: you control everything.
                      Bad: you must install and run Ollama yourself.
                      Set in Config → Your own server."""),
            new Page("Other services (not built-in buttons)",
                    """
                    Langbly:
                      → Config → Online services → pick Langbly, paste your langbly.com key.
                      → Do not pick Google for a Langbly key — Google will reject it.
                    
                    LibreTranslate or other self-hosted translate sites:
                      → Only if you can run something like Ollama on your own PC or VPS.
                      → This mod does not plug into LibreTranslate directly today.
                    
                    ChatGPT, Gemini, or other chat websites:
                      → Not wired into this mod. Use On your computer, DeepL, or Google instead."""),
            new Page("Commands (quick list)",
                    """
                    /translate guide       — open this full guide
                    /translate help        — short cheat sheet in chat
                    /translate status      — what mode you are in now
                    /translate ru          — send outgoing chat in Russian (example)
                    /translate auto        — follow last language you read
                    /translate read auto   — translate incoming chat as it arrives
                    /translate read hover  — translate only lines you hover
                    /translate download ru — save a language now
                    /translate backend deepl — switch translation method
                    /translate latin       — send romanized letters (default, safest)
                    /translate native      — send real foreign letters
                    /translate models      — list downloaded language packs
                    /translate clear all   — delete saved downloads

                    Protect a word from translation: hello {{Steve}}"""),
            new Page("Tips",
                    """
                    • New player? Leave everything on default. Chat translates itself; \
                    use /translate <code> only if you want to reply in another language.

                    • Messages blocked by the server? Try /translate latin.

                    • API keys live in config/chat-translator.json — do not share that file.

                    • Mod Menu + YACL are optional. Without them, every setting still \
                    has a /translate command.""")
    };

    public static final String START_HERE_BLURB = """
            Foreign chat translates itself into English on the same line. Optional: \
            translate what you send.
            Most players: leave "On your computer" selected. Open the full guide below \
            for step-by-step help and how to use DeepL, Google, or your own server.""";

    public static final String HELP_CHEAT_SHEET = """
            Lingo — quick help
            Read: foreign chat shows English automatically; hover a new language once to save it
            Send: /translate <code> then type English (e.g. /translate ru)
            /translate guide — full guide  |  /translate status — current settings
            /translate read hover — only translate what you hover
            /translate download ru — save a language now
            /translate latin — safe letters (default)  |  /translate native — real script
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
            case MANAGED_CLOUD -> "online (" + config.cloudProvider.label() + ")";
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
                + "\nReading chat: " + (config.autoTranslateIncoming
                        ? "automatic — English is added to foreign lines"
                        : "hover only — /translate read auto to change")
                + "\nOutgoing language: " + language
                + "\nOutgoing style: " + script
                + "\nSaved downloads: " + modelManager.formatTotalSize()
                + " — /translate models for details"
                + "\nFull guide: /translate guide";
    }
}
