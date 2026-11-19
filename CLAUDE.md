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
  RoundedRectRenderer  SDF + coverage ramp across one pixel; quad grown by one pixel
minecraft/             the only Minecraft-aware package: reflection with SRG names, MCP fallback
```

## The patterns that matter

- **Every state change goes through a `StateGuard`.** Cached states go through `GlStateCache`, never straight to GL:
  a change behind the back of `GlStateManager` makes a later vanilla or OptiFine call a silent no-op.
- **Baseline first.** Shaders are `#version 120`; use `gl_Vertex`, `gl_MultiTexCoord0`, `varying`, `gl_FragColor`.
  Anything newer sits behind a `GLCapabilities` check with a baseline fallback, as another `GlBackend`.
- **Sizes in framebuffer pixels.** `Minecraft.displayWidth` is framebuffer pixels on LWJGL 3; window points are not.
- **The Java SDF mirrors the GLSL.** `sdf/Sdf.java` and `shaders/lib/sdf.glsl` must behave identically; the harness
  compares rendered pixels with the Java model.
- **Prove it in the game.** A rendering change runs `./gradlew :harness:runHarness25` (and with `-Poptifine=<jar>`);
  every `HELIOS-HARNESS check` line must pass. Look at the screenshots.

## Pitfalls to avoid

- Minecraft names at run time are SRG (`func_179147_l`, `field_71443_c`). Look them up in the MCP 1.8.9 SRG and
  `stable_22` mappings, and keep the MCP name as the fallback.
- OptiFine leaves different alpha blend factors behind in GUI code; never assume a default, save and restore.
- The harness opens a game window. Do not launch it from an agent without the owner's go-ahead.
- No Mixin in core, and no Minecraft class referenced outside `lol.trq.helios.minecraft`.

## Conventions

- Commits use Conventional Commits. **You own every commit, including code an AI wrote. Never add Co-Authored-By or
  any other AI-attribution trailer.**
- Never name a consuming application in code, docs, comments or logs. No machine paths or secrets.
- Proprietary assets (third-party meshes, unlicensed fonts) never enter this repository; Helios offers generic sinks
  and hooks only.
