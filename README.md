# Chat Translator

A Fabric mod for Minecraft that translates chat both ways:

- **Incoming:** chat stays as-is. Open chat (`T`), **hover** a line — translation runs, then English appears in the hover tooltip. (Click also works.)
- **Outgoing:** what you type in English is translated into your target language before it's sent (optional; many public servers block non-English scripts).

Three translation backends (Mod Menu config):

1. **On-Device (default)** — OPUS-MT/ONNX via DJL, fully offline after per-language download
2. **Managed Cloud (BYOK)** — DeepL or Google Cloud Translation API v2 with your API key
3. **Custom / Self-Hosted** — Ollama `/api/generate` (default model `qwen2.5:1.5b`, configurable)

## How it works

- Language detection runs locally via [Lingua](https://github.com/pemistahl/lingua).
- **On-device:** translation via [DJL](https://djl.ai/) + ONNX Runtime and quantized [Helsinki-NLP OPUS-MT](https://github.com/Helsinki-NLP/Opus-MT) models (Xenova HF repos).
- **Cloud:** DeepL or Google Translate v2 REST with keys you provide in config.
- **Ollama:** HTTP POST to your endpoint with a Minecraft-aware translation system prompt.
- On-device models cache under `.minecraft/chattranslator/models/`. You'll see a local chat notice when a download starts and when that model is ready.

## Commands

| Command | What it does |
|---|---|
| `/translate` or `/translate help` | Show common language codes |
| `/translate <langcode>` | Lock outgoing target (e.g. `/translate ru`) |
| `/translate auto` | Follow last click-detected incoming language |
| `/translate status` | Mode, script, cache size |
| `/translate latin` | Outgoing romanized Latin (default) |
| `/translate native` | Outgoing real script |
| `/translate models` | List cached pairs + sizes |
| `/translate clear <en-ru\|ru\|all>` | Delete cached models |

Models path: `.minecraft/chattranslator/models/` (or your CurseForge instance folder).
You can also delete that folder in Finder while Minecraft is closed.

### Language codes

Codes are [ISO 639-1](https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes)
two-letter tags. Common ones that usually have both `en→X` and `X→en` models:

| Code | Language | Code | Language |
|---|---|---|---|
| `fr` | French | `de` | German |
| `es` | Spanish | `ru` | Russian |
| `ja` | Japanese | `zh` | Chinese |
| `ko` | Korean | `pt` | Portuguese |
| `it` | Italian | `nl` | Dutch |
| `pl` | Polish | `uk` | Ukrainian |
| `ar` | Arabic | `tr` | Turkish |
| `sv` | Swedish | `cs` | Czech |
| `fi` | Finnish | `hu` | Hungarian |
| `ro` | Romanian | `el` | Greek |
| `hi` | Hindi | `id` | Indonesian |
| `vi` | Vietnamese | `th` | Thai |

Not every code has a published Xenova model. If download fails, that pair
isn't available — try another code, or browse
[Xenova models on Hugging Face](https://huggingface.co/models?search=Xenova/opus-mt)
(`opus-mt-en-ru`, `opus-mt-ru-en`, etc.).

## Usage tips

1. **Incoming:** open chat (`T`), hover a foreign line. First hover starts
   download/translate; keep hovering (or re-hover) to read English. Click works too.
2. **Outgoing:** `/translate ru`, then type in English. First message for a new
   language may send in English while the model downloads — wait for
   `en->ru loaded`, then send again.
3. **Auto mode:** after you click-translate an incoming line, auto can target
   that language for outgoing replies (`/translate auto`).
4. **Outgoing script:** default `/translate latin` sends romanized Latin
   (`Privet` not `Привет`) so English-only / AntiSpam servers accept it. Works
   for Cyrillic, Greek, CJK, Arabic, etc. Use `/translate native` for real
   letters on servers that allow them.
5. **Server chat filters:** some networks still reject odd punctuation even in
   Latin form — if blocked, fall back to `/translate en`.
6. **Protect words:** wrap text in `{{double braces}}` so it is not translated.
   Example: `hello {{Steve}} welcome` keeps `Steve` as-is (braces removed when
   sent / shown). Works for outgoing and incoming.

## Mod Menu + YACL

Requires [Yet Another Config Lib (YACL)](https://modrinth.com/mod/yacl) **3.9.1+** for MC 26.1.

Optional: [Mod Menu](https://modrinth.com/mod/modmenu) (v18 for MC 26.1).
Then **Mods → Chat Translator → Config** to pick backend tier, enter API keys / Ollama URL, toggle Latin/Native, lock language, and clear the on-device model cache. Settings save to `config/chat-translator.json`.

**Note:** API keys are stored in plaintext in `config/chat-translator.json`. Do not share that file.

### Backend tiers

| Tier | Use when |
|---|---|
| **On-Device** | Offline play, no API keys, OPUS-MT quality |
| **Managed Cloud** | You have a DeepL or Google Translate v2 API key |
| **Custom / Ollama** | Self-hosted Ollama (e.g. Oracle OCI) with `qwen2.5:1.5b` or similar |

### Latin vs native (outgoing)

| Mode | Example | Use when |
|---|---|---|
| **Latin** (default) | `Privet` | Public / AntiSpam servers that ban non-Latin scripts |
| **Native** | `Привет` | Servers that allow the real target alphabet |

Same toggle: `/translate latin` or `/translate native`.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.3+
- Fabric API 0.155.2+26.1.2
- Java 25
- **Yet Another Config Lib (YACL) 3.9.1+26.1**
- (optional) Mod Menu 18.x for a config button

## Building from source

```bash
./gradlew build
```

Output jar: `build/libs/chat-translator-1.0.0.jar` — drop into `.minecraft/mods/`.

## Running in development

```bash
./gradlew runClient
```

## Testing

```bash
./gradlew clientTest
```

Unit tests cover language-state transitions, model cache logic, and
translation-outcome decision logic. Chat event wiring, model downloads, and
translation quality are verified manually in-game.

## License

This mod's code is [MIT licensed](LICENSE).

Runtime dependencies fetched separately (not bundled in the mod jar):
- [Helsinki-NLP OPUS-MT](https://github.com/Helsinki-NLP/Opus-MT) models —
  Apache-2.0.
- [DJL](https://github.com/deepjavalibrary/djl) / ONNX Runtime — Apache-2.0.
- [Lingua](https://github.com/pemistahl/lingua) — Apache-2.0.

NLLB-200 is intentionally not used, as it's CC-BY-NC-4.0 (non-commercial
only) and incompatible with free public redistribution on CurseForge.
