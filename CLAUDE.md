# CLAUDE.md: working in lol.trq.helios

A rendering library for Minecraft Forge 1.8.9 on LWJGL 3 (through lol.trq.lazarus). Java 17 bytecode, OpenGL 4.1
core profile only (Lazarus `-Dlazarus.gl=core`), GLSL `410 core`, MIT. Rules: [docs/opengl.md](docs/opengl.md). File index: [llms.txt](llms.txt).

## The 30-second mental model

```
Helios (one per GL context)
  GlBackend            every GL call; Gl41CoreBackend checks the context, binds via GL15/GL20/GL30, draws via *C
  StreamBuffer         ring of 3 VBOs, appended per frame with glBufferSubData, fenced at Helios.tick()
  GlStateCache         states the host caches (GlStateManager): blend, depth, cull, active texture, texture, mask
  StateGuard           first touch of a slot saves it from GL; close() restores newest first, cached slots via the cache
  ShaderLoader         ShaderPreprocessor (#include, defines, #line) -> compile/link -> ShaderProgram (uniform cache)
  FramebufferPool      sized from SurfaceSize = framebuffer pixels; a resize retires everything
  ShapeBatch           72-byte vertices via StreamBuffer, own VAO + static IBO, indexed triangles, u_mvp from the
                       game's matrices; flushes when full, on a texture change, at end(); one guarded draw each
  Passes / Effects     screen capture, dual Kawase blur, composite; blurBehind, bloom; PostChain; ScissorStack
  StencilMask          mask drawn with u_alphaCutoff 0.5 (shader discard) into the stencil, then EQUAL / NOTEQUAL
  TextRenderer         MsdfFont (.hfnt: metrics, kerning, deflated RGB atlas) -> layout -> ShapeBatch.glyph (kind 5)
  ShaderHotReload      dev only: polls source mtimes, ShaderLoader.reloadAll(); Helios.tick() drives it
color/ anim/ math/     pure Java, allocation-free: Colors (packed ARGB), Easing/Animation/Smooth/Stopwatch on a
                       Clock, Maths, Mat4 (column-major float[16]), Projector (gluProject without GLU)
fontgen/               build-time msdfgen port: AWT outline -> GlyphShape -> EdgeColoring -> MsdfGenerator -> .hfnt
minecraft/             the only Minecraft-aware package: reflection with SRG names, MCP fallback
```

## The patterns that matter

- **Every state change goes through a `StateGuard`.** Cached states go through `GlStateCache`, never straight to GL:
  a change behind the back of `GlStateManager` makes a later vanilla or OptiFine call a silent no-op.
- **Core 4.1, nothing else.** Shaders are `#version 410 core` with `layout(location)` inputs and outputs, interface
  blocks, matrices as uniforms. No fixed-function built-ins, nothing past 4.1 (macOS). The preprocessor rejects any
  other `#version`.
- **Binds through `GL15`/`GL20`/`GL30`, draws through `GL11C`/`GL32C`.** Lazarus intercepts the binds to keep its
  cache true and sends `GL11.glDrawArrays` to its fixed-function emulator. Only `Gl41CoreBackend` calls LWJGL.
- **Own buffers, indexed triangles, one VAO per mesh.** Never `GL_QUADS`, immediate mode or display lists in Helios.
  Batch: uploads are what costs on Apple.
- **Sizes in framebuffer pixels.** `Minecraft.displayWidth` is framebuffer pixels on LWJGL 3; window points are not.
- **The Java SDF mirrors the GLSL.** `sdf/Sdf.java` and `shaders/lib/sdf.glsl` must behave identically. The harness
  model (`harness-mod/.../Model.java`) mirrors `shape.fsh` and is compared with every rendered pixel.
- **Coverage uses the pixel size of the local coordinates** (`length(dFdx(p))`), never derivatives of the distance:
  those are wrong at tight curves and corners and cost 10+/255 at round caps.
- **The vertex layout lives in three places**: `ShapeBatch` (writer and attribute list), `shape.vsh`, and
  `ShapeBatchTest` (decoder). Change them together; locations in `shape.vsh` follow `ShapeBatch.ATTRIBUTES`.
- **The `.hfnt` format has two sides**: `AtlasBuilder.write` (fontgen) and `MsdfFont` (helios). Bump
  `VERSION` in both when it changes.
- **Measure, then optimize.** Hot paths (batch emit, text layout, guards) are allocation-light; run
  `./gradlew :helios:jmh` before and after a change there and update `BENCHMARKS.md`.
- **Time goes through `Clock`.** Animation, Smooth and Stopwatch take a clock so tests drive them; never call
  `System.nanoTime()` inside them.
- **Projection is in the space it was captured in.** Capture in a world hook and Minecraft's matrices are
  camera-relative: positions are world minus `MinecraftWorld.camera()`. View bobbing is part of the camera and is
  included, which is right for overlays.
- **Prove it in the game.** A rendering change runs `./gradlew :harness:runHarness25 -PlazarusJar=<jar>
  -PjvmArgs="-Dlazarus.gl=core -Dlazarus.gl.debug=true"` (and with `-Poptifine=<jar>`); every `HELIOS-HARNESS check`
  line must pass and no `[lazarus] GL error` may appear. Look at the screenshots.

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
