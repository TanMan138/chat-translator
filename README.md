# Lingo

A Fabric mod that helps you **read** foreign chat in English and optionally **send** translated replies.

## Download

Grab the latest jar from the [**Releases**](https://github.com/TanMan138/lingo/releases/latest) page and drop it in your `mods/` folder. That is the whole install — no other mods required.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

## Getting Started

### Read chat (everyone)

Foreign chat is translated as it arrives — the English is added to the end of the line:

```
<Alice> bonjour tout le monde → hello everyone
```

A language you have never used yet needs its files once. Hover the line (or run `/translate download fr`) and it downloads in the background; after that it is instant and offline.

Prefer the old behaviour? `/translate read hover` translates only lines you hover.

No setup needed for most players. Leave the default **“On your computer”** method in settings.

### Send translated chat (optional)

1. Pick a language: `/translate ru` (Russian example) or turn on **auto** in Config.
2. Type your message in **English**.
3. Press Enter — the mod translates it before sending.

**Tip:** Keep **Latin letters** on (default) on public servers so AntiSpam plugins do not block messages (`Privet` instead of `Привет`). Russian and Ukrainian use familiar chat-style spelling; other scripts use a general local romanizer. This step always runs on your computer, even with an online translation service.

### Pick how translation runs

| Method | Best for | Good | Not so good |
|---|---|---|---|
| **On your computer** (default) | Most players | Free after download, works offline | First use downloads files |
| **DeepL** (your API key) | Best quality | Very accurate | Paid — no free monthly plan for new signups |
| **Google Translate** (your API key) | Many languages | 500k characters/month included | Bills your card past that, silently |
| **Langbly** (your API key) | Easy signup, EU data residency | 500k characters/month included, 130+ languages | Bills automatically past that ($5/1M) |
| **Your own server (Ollama)** | Tech-savvy / self-hosting | You control it | You must run Ollama yourself |

**Langbly users:** In Config, pick **Online service** → **Langbly**, paste your [langbly.com](https://langbly.com/docs/) key, and optionally turn on **Keep my data in the EU**. Do not select **Google Translate** for a Langbly key — Google rejects it with `API key not valid`.

**Spending:** Google and Langbly both include 500,000 characters a month and then charge your card automatically — neither stops on its own. The mod keeps a running estimate and switches back to translating on your computer once you hit the budget in Config (500,000 by default). Check it with `/translate usage`. It counts only what this mod sent, so treat your provider's dashboard as the real number.

**Other services:** ChatGPT websites and LibreTranslate are **not** built-in buttons. See `/translate guide` in-game for details.

### Open the full guide in-game

- `/translate guide` — step-by-step guide with all options
- `/translate help` — short cheat sheet
- **Mods → Lingo → Config → Start here** (needs Mod Menu + YACL)

### Quick commands

| Command | What it does |
|---|---|
| `/translate guide` | Open the full beginner guide |
| `/translate help` | Short cheat sheet |
| `/translate status` | See your current settings |
| `/translate ru` | Send outgoing chat in Russian (example) |
| `/translate auto` | Follow the last language you read |
| `/translate read auto` \| `hover` | Translate chat as it arrives, or only on hover |
| `/translate download ru` | Save a language now instead of waiting |
| `/translate backend deepl` | Switch translation method |
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
- (optional) [Yet Another Config Lib (YACL)](https://modrinth.com/mod/yacl) 3.9.1+ and [Mod Menu](https://modrinth.com/mod/modmenu) 18.x for the settings screen

Without YACL and Mod Menu the mod still works fully — every setting has a `/translate` command, and API keys can be edited in `config/chat-translator.json`.

## Advanced (for builders)

### How it works under the hood

- Language detection: [Lingua](https://github.com/pemistahl/lingua) (local).
- **On your computer:** [DJL](https://djl.ai/) + ONNX Runtime, [Helsinki-NLP OPUS-MT](https://github.com/Helsinki-NLP/Opus-MT) via Xenova Hugging Face repos.
- **Online:** DeepL REST, Google Cloud Translation API v2, or Langbly. Langbly uses the same v2 request/response shape as Google but authenticates with `Authorization: Bearer <key>` against `api.langbly.com` / `eu.langbly.com`, so it needs its own backend rather than a shared one.
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
| `/translate read auto\|hover` | Incoming chat: translate on arrival, or on hover |
| `/translate download <langcode>` | Fetch both directions for a language now |
| `/translate backend <name>` | `ondevice`, `deepl`, `google`, `langbly`, `ollama` |
| `/translate usage` | Estimated paid characters used this month |
| `/translate models` | List cached language pairs |
| `/translate clear <en-ru\|ru\|all>` | Delete cached models |

Not every language code has a published on-device model. Missing pairs return HTTP 401/404 from Hugging Face and the mod tells you the language isn't supported offline (passthrough). Browse [Xenova OPUS-MT models](https://huggingface.co/models?search=Xenova/opus-mt) or switch to DeepL / Langbly for wider coverage.

### Config categories (YACL)

- **Start here** — summary + open full guide
- **What you read** — translate incoming chat automatically or on hover
- **How to translate** — on computer / online / your server
- **Online services** — DeepL, Google, or Langbly keys
- **Your own server** — Ollama URL and model
- **What you send** — Latin letters, auto language, locked code
- **Saved downloads** — clear on-device cache

## Building from source

```bash
./gradlew build
```

Output jar: `build/libs/chat-translator-1.1.5.jar`

```bash
./gradlew runClient    # dev client
./gradlew clientTest   # unit tests
```

### Cutting a release

Releases are built by [`.github/workflows/release.yml`](.github/workflows/release.yml): pushing a `v*` tag builds the jar with that version and publishes a GitHub Release with it attached.

```bash
# 1. add a "## [1.2.0]" section to CHANGELOG.md and commit it
# 2. tag and push
git tag v1.2.0
git push origin v1.2.0
```

The tag supplies `-Pmod_version`, so `gradle.properties` does not have to match. Release notes are taken from the matching `CHANGELOG.md` section. `workflow_dispatch` can also build a version manually.

The same tag also uploads to CurseForge and Modrinth. That step needs four repository settings — secrets `CURSEFORGE_TOKEN` and `MODRINTH_TOKEN`, variables `CURSEFORGE_PROJECT_ID` and `MODRINTH_PROJECT_ID`. A platform whose token is missing is skipped rather than failing the run.

## License

MIT — see [LICENSE](LICENSE).

Runtime dependencies (not bundled): OPUS-MT models (Apache-2.0), DJL/ONNX (Apache-2.0), Lingua (Apache-2.0). NLLB-200 excluded (CC-BY-NC-4.0).
