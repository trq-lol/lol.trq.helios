# CLAUDE.md: working in lol.trq.helios

A rendering library for Minecraft Forge 1.8.9 on LWJGL 3 (through lol.trq.lazarus). Java 17 bytecode, OpenGL 2.1 +
GLSL 1.20 + `ARB_framebuffer_object` baseline, MIT. File index: [llms.txt](llms.txt).

## The 30-second mental model

```
Helios (one per GL context)
  GlBackend            every GL call; Gl21Backend = baseline, fakes in tests, faster backends later
  GlStateCache         states the host caches (GlStateManager): blend, depth, cull, alpha, texture2D, colour
  StateGuard           first touch of a slot saves it from GL; close() restores newest first, cached slots via the cache
  ShaderLoader         ShaderPreprocessor (#include, defines, #line) -> compile/link -> ShaderProgram (uniform cache)
  FramebufferPool      sized from SurfaceSize = framebuffer pixels; a resize retires everything
  ShapeBatch           72-byte vertices in a streamed VBO, one quad per shape, GL_QUADS; kind + params per vertex;
                       flushes when full, on a texture change, at end(); each flush is one guarded draw
  Passes / Effects     screen capture, dual Kawase blur, composite; blurBehind, bloom; PostChain; ScissorStack
  StencilMask          mask drawn with alpha cutoff 0.5 into the stencil, then content with EQUAL / NOTEQUAL
  TextRenderer         MsdfFont (.hfnt: metrics, kerning, deflated RGB atlas) -> layout -> ShapeBatch.glyph (kind 5)
  ShaderHotReload      dev only: polls source mtimes, ShaderLoader.reloadAll(); Helios.tick() drives it
fontgen/               build-time msdfgen port: AWT outline -> GlyphShape -> EdgeColoring -> MsdfGenerator -> .hfnt
minecraft/             the only Minecraft-aware package: reflection with SRG names, MCP fallback
```

## The patterns that matter

- **Every state change goes through a `StateGuard`.** Cached states go through `GlStateCache`, never straight to GL:
  a change behind the back of `GlStateManager` makes a later vanilla or OptiFine call a silent no-op.
- **Baseline first.** Shaders are `#version 120`; use `gl_Vertex`, `gl_MultiTexCoord0`, `varying`, `gl_FragColor`.
  Anything newer sits behind a `GLCapabilities` check with a baseline fallback, as another `GlBackend`.
- **Sizes in framebuffer pixels.** `Minecraft.displayWidth` is framebuffer pixels on LWJGL 3; window points are not.
- **The Java SDF mirrors the GLSL.** `sdf/Sdf.java` and `shaders/lib/sdf.glsl` must behave identically. The harness
  model (`harness-mod/.../Model.java`) mirrors `shape.fsh` and is compared with every rendered pixel.
- **Coverage uses the pixel size of the local coordinates** (`length(dFdx(p))`), never derivatives of the distance:
  those are wrong at tight curves and corners and cost 10+/255 at round caps.
- **The vertex layout lives in three places**: `ShapeBatch` (writer and attribute list), `shape.vsh`, and
  `ShapeBatchTest` (decoder). Change them together. Attribute 0 must be the position: GL 2.1 draws nothing without it.
- **The `.hfnt` format has two sides**: `AtlasBuilder.write` (fontgen) and `MsdfFont` (helios). Bump
  `VERSION` in both when it changes.
- **Measure, then optimize.** Hot paths (batch emit, text layout, guards) are allocation-light; run
  `./gradlew :helios:jmh` before and after a change there and update `BENCHMARKS.md`.
- **Prove it in the game.** A rendering change runs `./gradlew :harness:runHarness25` (and with `-Poptifine=<jar>`);
  every `HELIOS-HARNESS check` line must pass. Look at the screenshots.

## Pitfalls to avoid

- Minecraft names at run time are SRG (`func_179147_l`, `field_71443_c`). Look them up in the MCP 1.8.9 SRG and
  `stable_22` mappings, and keep the MCP name as the fallback.
- OptiFine leaves different alpha blend factors behind in GUI code; never assume a default, save and restore.
- Effects that capture the screen assume drawing units are screen space from the top left (a GUI screen).
- A fresh pooled depth-stencil framebuffer holds garbage depth; GUI code leaves the depth test on. Clear depth or
  turn the test off before drawing into one.
- Forge `Framebuffer.enableStencil()` recreates the framebuffer and leaves nothing bound;
  `MinecraftHelios.enableStencil()` binds it again.
- Vertex attribute pointers are not restored (code using generic attribute arrays sets its pointers before drawing);
  their enable flags are.
- The harness opens a game window. Do not launch it from an agent without the owner's go-ahead.
- No Mixin in core, and no Minecraft class referenced outside `lol.trq.helios.minecraft`.

## Conventions

- Commits use Conventional Commits. **You own every commit, including code an AI wrote. Never add Co-Authored-By or
  any other AI-attribution trailer.**
- Never name a consuming application in code, docs, comments or logs. No machine paths or secrets.
- Proprietary assets (third-party meshes, unlicensed fonts) never enter this repository; Helios offers generic sinks
  and hooks only.
