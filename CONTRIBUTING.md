# Contributing

Issues and pull requests are welcome.

## Setup

See the [Fabric documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up)
for IDE setup, then:

```bash
./gradlew build
```

## Before opening a PR

- Run `./gradlew clientTest` and make sure it passes.
- Run `./gradlew runClient` and manually verify any chat-related change in
  a real (single-player or LAN) world — chat behaves differently across
  contexts, and automated tests don't cover live Fabric event wiring or
  in-game chat rendering.
- Keep new pure logic (state transitions, cache/path logic, decision logic)
  unit-tested; IO-heavy code (network, DJL inference, Minecraft chat HUD)
  can rely on manual verification per the existing pattern in this repo.

## Code style

Match the existing package structure under
`src/client/java/com/tanman/chattranslator/client/` — one class, one clear
responsibility, small files.
