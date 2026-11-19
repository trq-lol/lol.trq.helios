# lol.trq.helios

A rendering library for Minecraft Forge 1.8.9 running on LWJGL 3: SDF shapes, MSDF text, shaders, framebuffers,
blur and bloom, with strict GL state discipline so vanilla and OptiFine keep a coherent state.

Status: early development (milestone H0), nothing released yet. See [CHANGELOG.md](CHANGELOG.md).

## Requirements

- Minecraft 1.8.9 with Forge 11.15.1.2318, on Java 17 or newer, running on LWJGL 3 through
  [lol.trq.lazarus](https://github.com/trq-lol/lol.trq.lazarus).
- OpenGL 2.1 with `ARB_framebuffer_object`. Every built-in shader is GLSL 1.20, so Helios runs in the legacy 2.1
  context macOS gives the game.

## What is here

| Package | |
|---|---|
| `lol.trq.helios` | `Helios`: one instance per GL context, owning the pieces below |
| `gl` | `GlBackend`, the seam every GL call goes through; `Gl21Backend`, the OpenGL 2.1 baseline |
| `state` | `StateGuard` saves what a draw touches and restores it; `GlStateCache` routes cached state through the host |
| `shader` | Loader with `#include`, defines, `file:line` errors, a uniform location cache and reload |
| `fbo` | `FramebufferPool`, sized in framebuffer pixels so effects stay sharp on high-DPI screens |
| `sdf` | `RoundedRectRenderer`: signed distance rounded rectangles with one-pixel analytic anti-aliasing |
| `minecraft` | The only package that knows Minecraft: `GlStateManager` routing and the window size, by reflection |

No Mixin. Minecraft is reached only from `lol.trq.helios.minecraft`, so the rest of Helios builds and tests without
the game.

## Usage

```java
// On the render thread, once the window exists:
Helios helios = MinecraftHelios.create();

// In a GuiScreen draw, in GUI units; the last argument is one framebuffer pixel in those units.
float pixel = 1f / scaledResolution.getScaleFactor();
helios.rects().fill(12, 12, 120, 48, 8, 0xFF3B82F6, pixel);
```

Your own GL work goes through a guard:

```java
try (StateGuard g = helios.guard()) {
    g.blend(true).program(myProgram.id()).framebuffer(fb.id());
    // draw
} // everything touched is restored, cached state through GlStateManager
```

## Building and testing

```
./gradlew spotlessCheck build        # unit tests, no GL needed
./gradlew :harness:runHarness25      # boots the game, checks pixels and GL state on the main menu, exits
./gradlew :harness:runHarness21 -Poptifine=<path to an OptiFine 1.8.9 jar>
```

The harness downloads Minecraft 1.8.9, Forge and the lol.trq.lazarus release jar (checking its SHA-256) into
`.harness-cache/`, boots the game on LWJGL 3 with sound muted, and loads a test mod. On the main menu the mod draws a
rounded rect, compares every pixel around it with the Java model of the shader, checks that GL state is unchanged
afterwards and that the `GlStateManager` cache still matches GL, exercises the framebuffer pool, saves screenshots to
`harness/build/run/<task>/helios/` and exits 0 only if every check passed. It opens a game window while it runs.
OptiFine cannot be redistributed: point `-Poptifine` at a local copy.

## For AI agents

Start with [CLAUDE.md](CLAUDE.md) for the mental model and the traps, then [llms.txt](llms.txt) for an index of
every doc and core source file. The tests under `helios/src/test` are the executable examples of the API, and
`harness-mod` shows real use inside the game.

## License

[MIT](LICENSE). The development harness launcher is LGPL-3.0-or-later; see [NOTICE.md](NOTICE.md).
