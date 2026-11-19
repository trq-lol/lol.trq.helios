# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/) and the
project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.5] - 2026-11-19

### Fixed

- `ShapeBatch` drops a shape or glyph whose position or size is NaN or infinite. The GPU drew such a shape as a thin
  sliver toward the centre of the screen.

## [0.1.4] - 2026-11-19

### Fixed

- Blur and bloom ignore a scissor test, stencil test or colour mask that the caller left on while they draw into
  Helios's own framebuffers. Any of these corrupted the intermediate images; with the colour mask off, a full-screen
  blur covered the screen with an empty image. The final composite still honours the caller's clip and stencil.

## [0.1.3] - 2026-11-19

### Fixed

- `MinecraftHelios.enableStencil()` changes nothing once the main framebuffer has a stencil buffer. Each call used to
  bind the main framebuffer again and reset the viewport, which broke drawing into another framebuffer when it was
  called every frame.

## [0.1.2] - 2026-11-19

### Added

- `QuadSink.triangle`: one triangle, for fans, strips and cones drawn through the quad sink.
- `LineSink.strip`: connected segments through a list of points, open or closed.

## [0.1.1] - 2026-11-19

### Added

- `MinecraftOptiFine`: whether OptiFine is installed, its version, and its Fast Render option, by reflection. Helios's
  drawing works with Fast Render on; the harness checks it with `-Poptifine=<jar> -PfastRender`.
- The world harness saves `world.png`: a stand-in player drawn through every mesh sink at the head, body and back
  anchors, for a visual check.

### Changed

- The harness runs on the Lazarus 0.4.2 release with Forge's splash screen on.

### Fixed

- The harness no longer exits from a client tick, which could deadlock with the game's shutdown hook and fail a run
  after every check had passed; the test mod quits through `Minecraft.shutdown()` and reports its result on a line.
- The world harness's head anchor check allows for the head's pitch.

## [0.1.0] - 2026-11-19

First release. Requires Minecraft Forge 1.8.9 on LWJGL 3 through lol.trq.lazarus 0.2.0 or newer in core mode
(`-Dlazarus.gl=core`), Java 17 or newer.

### Added

- OpenGL 4.1 core profile only: `GlBackend` seam and `Gl41CoreBackend`, which checks the context at start-up. Every
  shader is `#version 410 core`; the preprocessor rejects other versions. See `docs/opengl.md`.
- `StreamBuffer`: a fenced ring of three vertex buffers rotated by `Helios.tick()`, with `glBufferSubData` by default
  and unsynchronized mapping behind `-Dhelios.stream=map`. Program, vertex array, array buffer, matrices and viewport
  are read from Lazarus's state tracker when it has them.
- `StateGuard`: saves each GL state slot on first touch and restores newest first; cached state goes through a
  `GlStateCache`, implemented for the Minecraft `GlStateManager`. Covers blend, depth, cull, program, framebuffer,
  viewport, array buffer, vertex array, texture units, scissor, stencil and colour mask.
- Shader loader: `#include` (relative and root), injected defines, `#line` mapping so driver errors read
  `file:line`, uniform location cache, reload that keeps the last good program; hot reload from a source directory.
- `FramebufferPool` sized in framebuffer pixels, with resize handling.
- `ShapeBatch`: batched signed distance shapes (rounded rects, outlines, circles, arcs, lines, polylines, drop
  shadows, inner glow, textured images with tint) anti-aliased over exactly one pixel, drawn as indexed triangles
  from its own vertex array; `Paint` for solid, four-corner, linear and radial gradients.
- Effects: dual Kawase blur, blur behind a shape, bloom, post-processing chains of custom full-screen passes.
- `ScissorStack` for nested clipping; `StencilMask` to clip drawing to any shape; `MinecraftHelios.enableStencil()`.
- MSDF text: `MsdfFont` (`.hfnt` atlases), `TextRenderer` with kerning, `§` colour and format codes (bold, italic,
  underline, strikethrough, obfuscated), classic and soft shadows, weight and letter spacing; glyphs share the
  shape batch. Inter Regular is bundled.
- `fontgen`: a Java port of msdfgen's generator that builds `.hfnt` atlases from TrueType and OpenType fonts at build
  time.
- `MeshRenderer` (`Helios.meshes()`): 3D drawing through `QuadSink` (position and colour), `LineSink` (lines a given
  number of pixels wide, anti-aliased, clipped at the near plane) and `TexturedSink` (position, texture coordinates,
  normal and tint, with Minecraft 1.8.9's two-light entity lighting). Sinks transform on the CPU so meshes with
  different transforms share a draw call; `MeshRecorder` and `StaticMesh` upload a mesh once with `GL_STATIC_DRAW`.
- `PlayerModel` and `PlayerPose`: the 1.8.9 player transform chain with `Attachment` anchors at the head, body and
  back, following body and head yaw, pitch, sneaking, the riding limit and armour.
- `Colors`: packed ARGB maths, HSB/HSL, sRGB, linear-light and hue interpolation, rainbow and ping-pong, hex
  parsing and formatting, readable text colour.
- `Easing` curves, `Animation` that reverses mid-flight without jumping, frame-rate independent `Smooth`,
  `Stopwatch`; all driven by an injectable `Clock`.
- `Maths`, column-major `Mat4`, and `Projector` for world-to-screen projection and 2D bounding boxes.
- Minecraft wiring by reflection, no Mixin: `MinecraftHelios`, `MinecraftGlStateCache`, `MinecraftWorld` (partial
  ticks, camera, interpolated entity positions and boxes) and `MinecraftPlayerPose` (a live player's pose).
- JMH benchmarks and harness GPU timings; see `BENCHMARKS.md`.
- Development harness that boots Forge 1.8.9 on LWJGL 3 through the Lazarus release in core mode and checks rendering
  in game: every primitive against a software model, blur, bloom, text and stencil against reference images, state
  restoration, and any `[lazarus] GL error` fails the run. `-Pworld` checks projection, player anchors and mesh
  drawing in a singleplayer world. Runs in CI under Xvfb with Mesa.

[Unreleased]: https://github.com/trq-lol/lol.trq.helios/compare/v0.1.5...HEAD
[0.1.5]: https://github.com/trq-lol/lol.trq.helios/releases/tag/v0.1.5
[0.1.4]: https://github.com/trq-lol/lol.trq.helios/releases/tag/v0.1.4
[0.1.3]: https://github.com/trq-lol/lol.trq.helios/releases/tag/v0.1.3
[0.1.2]: https://github.com/trq-lol/lol.trq.helios/releases/tag/v0.1.2
[0.1.1]: https://github.com/trq-lol/lol.trq.helios/releases/tag/v0.1.1
[0.1.0]: https://github.com/trq-lol/lol.trq.helios/releases/tag/v0.1.0
