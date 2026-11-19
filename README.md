# lol.trq.helios

A rendering library for Minecraft Forge 1.8.9 running on LWJGL 3: SDF shapes, MSDF text, shaders, framebuffers,
blur and bloom, with strict GL state discipline so vanilla and OptiFine keep a coherent state.

Status: early development, nothing released yet. See [CHANGELOG.md](CHANGELOG.md).

## Requirements

- Minecraft 1.8.9 with Forge 11.15.1.2318, on Java 17 or newer, running on LWJGL 3 through
  [lol.trq.lazarus](https://github.com/trq-lol/lol.trq.lazarus).
- OpenGL 2.1 with `ARB_framebuffer_object`. Every built-in shader is GLSL 1.20, so the library runs in the legacy 2.1
  context macOS gives the game.

## Building

```
./gradlew spotlessCheck build
```

## For AI agents

Start with [CLAUDE.md](CLAUDE.md) for the mental model and the traps, then [llms.txt](llms.txt) for an index of
every doc and core source file. The tests under `helios/src/test` are the executable examples of the API.

## License

[MIT](LICENSE).
