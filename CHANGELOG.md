# Changelog

All notable changes to Chat Translator. Versions follow [semantic versioning](https://semver.org/).

## [1.1.0]

### Added

- **Automatic chat reading.** Foreign chat now shows its English on the same line
  (`<Alice> bonjour → hello`) without hovering. Turn it off with
  `/translate read hover`, or in Config → What you read.
- `/translate download <code>` — save a language ahead of time so the first foreign
  message translates instantly.
- `/translate backend <ondevice|deepl|google|langbly|ollama>` — switch translation
  method without a config screen.
- `/translate read auto|hover` — choose how incoming chat is handled.
- Translation cache: repeated lines are not translated twice, which also stops
  repeat charges on DeepL / Google / Langbly keys.

### Changed

- **Yet Another Config Lib is now optional.** The mod loads and works on its own;
  install YACL (and Mod Menu) only if you want the settings screen. Everything it
  configures is reachable through `/translate` commands.
- Auto reading never starts a download by itself — an unsaved language still waits
  for a hover or `/translate download`, so joining a server cannot trigger a
  surprise ~100 MB fetch.
- Join/leave lines, advancement announcements, your own messages, and lines too
  short to identify are left alone instead of being hooked for translation.
- On-device model downloads no longer time out after 60 seconds — a slow but healthy
  download used to be reported as a failure and could disable a language for the
  session.

## [1.0.0]

- First release: hover-to-read incoming chat, optional outgoing translation,
  on-device OPUS-MT models, BYOK DeepL / Google / Langbly, self-hosted Ollama,
  Latin romanization for AntiSpam-heavy servers, gaming slang protection.
