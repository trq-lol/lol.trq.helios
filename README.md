# lol.trq.helios

A rendering library for Minecraft Forge 1.8.9 running on LWJGL 3: SDF shapes, MSDF text, shaders, framebuffers,
blur and bloom, with strict GL state discipline so vanilla and OptiFine keep a coherent state.

Status: early development (milestone H1), nothing released yet. See [CHANGELOG.md](CHANGELOG.md).

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
| `state` | `StateGuard` saves what a draw touches and restores it; `GlStateCache` routes cached state through the host; `ScissorStack` for nested clips |
| `shader` | Loader with `#include`, defines, `file:line` errors, attribute binding, a uniform location cache and reload |
| `fbo` | `FramebufferPool`, sized in framebuffer pixels so effects stay sharp on high-DPI screens |
| `batch` | `ShapeBatch`: rounded rects, outlines, circles, arcs, lines, polylines, drop shadows, inner glow and images, many per draw call, all anti-aliased over exactly one pixel; `Paint` for solid, corner, linear and radial gradients |
| `effect` | Dual Kawase blur, blur behind a shape (frosted glass), bloom, post-processing chains |
| `sdf` | The distance functions of the shape shader, in Java, for tests |
| `minecraft` | The only package that knows Minecraft: `GlStateManager` routing and the window size, by reflection |

No Mixin. Minecraft is reached only from `lol.trq.helios.minecraft`, so the rest of Helios builds and tests without
the game.

## Usage

```java
// On the render thread, once the window exists:
Helios helios = MinecraftHelios.create();

// In a GuiScreen draw, in GUI units. pixel is one framebuffer pixel in those units.
float pixel = 1f / scaledResolution.getScaleFactor();
helios.shapes().begin(pixel)
        .shadow(12, 14, 120, 48, 8, 6, 0x66000000)
        .rect(12, 12, 120, 48, 8, Paint.vertical(0xFF3B82F6, 0xFF1D4ED8))
        .outline(12, 12, 120, 48, 8, 1, Paint.solid(0x40FFFFFF))
        .arc(160, 36, 20, 4, (float) -Math.PI / 2, 4.2f, Paint.solid(0xFFF59E0B))
        .line(190, 20, 240, 50, 2, true, Paint.horizontal(0xFF22D3EE, 0xFFA855F7))
        .end(); // one draw call

// Frosted glass and bloom work on what is already drawn.
helios.effects().blurBehind(12, 70, 120, 48, 8, pixel, 3, 2, 0xFFFFFFFF);
helios.effects().bloom(() -> helios.shapes().begin(pixel).circle(60, 140, 10, Paint.solid(0xFFFF3060)).end(),
        3, 1.5f, 1f);
```

Angles are in radians, 0 along +x, growing clockwise on screen. Colours are `0xAARRGGBB`, not premultiplied.

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
`.harness-cache/`, boots the game on LWJGL 3 with sound muted, and loads a test mod. On the main menu the mod:

- draws every primitive and compares each pixel around it with a software model of the shader (within 3/255);
- checks that many shapes go out in one draw call;
- compares blur and bloom with reference images in `harness/references/` (per framebuffer size; refresh them with
  `-PupdateReferences`, require them with `-PrequireReferences`);
- runs a post-processing chain and the scissor stack offscreen;
- checks that GL state is unchanged afterwards and that the `GlStateManager` cache still matches GL;
- saves screenshots to `harness/build/run/<task>/helios/` and exits 0 only if every check passed.

It opens a game window while it runs. OptiFine cannot be redistributed: point `-Poptifine` at a local copy. CI runs
the harness on Linux under a virtual display with software OpenGL.

## For AI agents

Start with [CLAUDE.md](CLAUDE.md) for the mental model and the traps, then [llms.txt](llms.txt) for an index of
every doc and core source file. The tests under `helios/src/test` are the executable examples of the API, and
`harness-mod` shows real use inside the game.

## License

[MIT](LICENSE).
