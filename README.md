# Chat Translator

A Fabric mod that helps you **read** foreign chat in English and optionally **send** translated replies.

## Getting Started

### Read chat (everyone)

1. Press **T** to open chat.
2. **Hover** your mouse over a message you want in English.
3. Wait a moment — English appears in the tooltip.

No setup needed for most players. Leave the default **“On your computer”** method in settings.

### Send translated chat (optional)

1. Pick a language: `/translate ru` (Russian example) or turn on **auto** in Config.
2. Type your message in **English**.
3. Press Enter — the mod translates it before sending.

**Tip:** Keep **Latin letters** on (default) on public servers so AntiSpam plugins do not block messages (`Privet` instead of `Привет`).

### Pick how translation runs

| Method | Best for | Good | Not so good |
|---|---|---|---|
| **On your computer** (default) | Most players | Free after download, works offline | First use downloads files |
| **DeepL** (your API key) | Quality translations | Very accurate | Needs deepl.com account + internet |
| **Google Translate** (your API key) | Many languages, Langbly keys | Wide language support | Needs Google/Langbly key + internet |
| **Your own server (Ollama)** | Tech-savvy / self-hosting | You control it | You must run Ollama yourself |

**Langbly users:** In Config, pick **Online service** → **Google Translate**, and paste the API key Langbly gave you.

**Other services:** ChatGPT websites and LibreTranslate are **not** built-in buttons. See `/translate guide` in-game for details.

### Open the full guide in-game

- `/translate guide` — step-by-step guide with all options
- `/translate help` — short cheat sheet
- **Mods → Chat Translator → Config → Start here** (needs Mod Menu + YACL)

### Quick commands

| Command | What it does |
|---|---|
| `/translate guide` | Open the full beginner guide |
| `/translate help` | Short cheat sheet |
| `/translate status` | See your current settings |
| `/translate ru` | Send outgoing chat in Russian (example) |
| `/translate auto` | Follow the last language you read |
| `/translate latin` | Send romanized letters (default, safest) |
| `/translate native` | Send real foreign letters |

### Common language codes

`fr` French · `de` German · `es` Spanish · `ru` Russian · `ja` Japanese · `zh` Chinese · `ko` Korean · `pt` Portuguese

### Tips

- Protect names: `hello {{Steve}}` keeps `Steve` untranslated.
- Blocked by server? Try `/translate latin`.
- API keys are stored in `config/chat-translator.json` — **do not share that file**.

---

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.3+
- Fabric API 0.155.2+26.1.2
- Java 25
- **[Yet Another Config Lib (YACL)](https://modrinth.com/mod/yacl) 3.9.1+** for MC 26.1
- (optional) [Mod Menu](https://modrinth.com/mod/modmenu) 18.x for a config button

## Advanced (for builders)

### How it works under the hood

- Language detection: [Lingua](https://github.com/pemistahl/lingua) (local).
- **On your computer:** [DJL](https://djl.ai/) + ONNX Runtime, [Helsinki-NLP OPUS-MT](https://github.com/Helsinki-NLP/Opus-MT) via Xenova Hugging Face repos.
- **Online:** DeepL REST or Google Cloud Translation API v2.
- **Ollama:** `POST {base}/api/generate` with `stream: false`.
- Model cache: `.minecraft/chattranslator/models/` (on-device mode only).

### All commands

| Command | What it does |
|---|---|
| `/translate` or `/translate help` | Short cheat sheet |
| `/translate guide` | Full in-game guide |
| `/translate <langcode>` | Lock outgoing target |
| `/translate auto` | Follow detected language |
| `/translate status` | Mode, method, script, cache |
| `/translate latin` / `native` | Outgoing script style |
| `/translate models` | List cached language pairs |
| `/translate clear <en-ru\|ru\|all>` | Delete cached models |

Not every language code has a published on-device model. Browse [Xenova OPUS-MT models](https://huggingface.co/models?search=Xenova/opus-mt) if download fails.

### Config categories (YACL)

- **Start here** — summary + open full guide
- **How to translate** — on computer / online / your server
- **Online services** — DeepL or Google keys
- **Your own server** — Ollama URL and model
- **What you send** — Latin letters, auto language, locked code
- **Saved downloads** — clear on-device cache

## Building from source

```bash
./gradlew build
```

Output jar: `build/libs/chat-translator-1.0.0.jar`

```bash
./gradlew runClient    # dev client
./gradlew clientTest   # unit tests
```

## License

MIT — see [LICENSE](LICENSE).

Runtime dependencies (not bundled): OPUS-MT models (Apache-2.0), DJL/ONNX (Apache-2.0), Lingua (Apache-2.0). NLLB-200 excluded (CC-BY-NC-4.0).
