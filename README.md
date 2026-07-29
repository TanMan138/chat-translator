# Chat Translator

A Fabric mod for Minecraft that automatically translates chat both ways:

- **Incoming:** any non-English chat message is translated to English, with
  the original shown on hover.
- **Outgoing:** what you type in English is translated into your current
  target language before it's sent — other players only see the translated
  version.

Runs fully offline after a one-time per-language model download. No cloud
APIs, no API keys, no content filtering.

## How it works

- Language detection runs locally via [Lingua](https://github.com/pemistahl/lingua).
- Translation runs locally via [DJL](https://djl.ai/) + ONNX Runtime, using
  quantized [Helsinki-NLP OPUS-MT](https://github.com/Helsinki-NLP/Opus-MT)
  models (ONNX builds via the `Xenova/opus-mt-*` Hugging Face repos).
- Models are downloaded on first use per language pair and cached under
  `.minecraft/chattranslator/models/`, so nothing is bundled in the mod jar
  and repeat launches don't re-download anything already fetched.

## Commands

- `/translate <langcode>` — lock your outgoing target language (e.g.
  `/translate fr`).
- `/translate auto` — (default) automatically target whichever language was
  last detected from an incoming message.
- `/translate status` — show current mode and target language.

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.3+
- Fabric API 0.155.2+26.1.2
- Java 25

## Building from source

```bash
./gradlew build
```

Output jar: `build/libs/`.

## Running in development

```bash
./gradlew runClient
```

## Testing

```bash
./gradlew clientTest
```

Unit tests cover language-state transitions, model cache logic, and
translation-outcome decision logic (the parts of the pipeline that don't
require live Minecraft events, real network access, or real ONNX inference
to exercise meaningfully). Everything else — chat event wiring, model
downloads, and translation quality — is verified manually in-game.

## License

This mod's code is [MIT licensed](LICENSE).

Runtime dependencies fetched separately (not bundled in the mod jar):
- [Helsinki-NLP OPUS-MT](https://github.com/Helsinki-NLP/Opus-MT) models —
  Apache-2.0.
- [DJL](https://github.com/deepjavalibrary/djl) / ONNX Runtime — Apache-2.0.
- [Lingua](https://github.com/pemistahl/lingua) — Apache-2.0.

NLLB-200 is intentionally not used, as it's CC-BY-NC-4.0 (non-commercial
only) and incompatible with free public redistribution on CurseForge.
