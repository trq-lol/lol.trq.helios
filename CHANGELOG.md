# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/) and the
project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- Helios now requires Lazarus core mode (`-Dlazarus.gl=core`): an OpenGL 4.1 core profile context. `Gl41CoreBackend`
  replaces `Gl21Backend` and checks the context at start-up. Every shader is `#version 410 core`; the preprocessor
  rejects other versions. See `docs/opengl.md`.
- `ShapeBatch` draws indexed triangles from its own vertex array and a static index buffer, with the game's matrices
  as a `u_mvp` uniform; vertex data streams through `StreamBuffer`, a fenced ring of buffers rotated by
  `Helios.tick()`. Full-screen passes draw one triangle from `gl_VertexID`.
- The stencil mask's alpha cutoff is a shader `discard` (`u_alphaCutoff`) instead of the fixed-function alpha test.
- `StateGuard` saves the vertex array binding instead of vertex attribute flags; the alpha test, alpha function,
  `GL_TEXTURE_2D` and current colour slots are gone, as are the matching `GlStateCache` and `GlBackend` methods.
- The harness runs in core mode only, on the Lazarus 0.2.0 release (`-PlazarusJar` for a local build), with the
  splash screen off, and fails on any `[lazarus] GL error`. The test mod quits through `Minecraft.shutdown()` and
  reports its result on a line, instead of exiting from a client tick, which could deadlock at shutdown.

### Added

- `MeshRenderer` (`Helios.meshes()`): 3D drawing through `QuadSink` (position and colour), `LineSink` (lines a given
  number of pixels wide, anti-aliased, clipped at the near plane) and `TexturedSink` (position, texture coordinates,
  normal and tint, with Minecraft 1.8.9's two-light entity lighting). Sinks transform on the CPU so meshes with
  different transforms share a draw call; `MeshRecorder` and `StaticMesh` upload a mesh once with `GL_STATIC_DRAW`.
- `PlayerModel` and `PlayerPose`: the 1.8.9 player transform chain with `Attachment` anchors at the head, body and
  back, following body and head yaw, pitch, sneaking, the riding limit and armour. `MinecraftPlayerPose` reads a live
  player's pose for the frame.
- The world harness checks the head anchor height and draws through every mesh sink and a static mesh.

- `GlBackend` seam with an OpenGL 2.1 + `ARB_framebuffer_object` backend on LWJGL 3.
- `StateGuard`: saves each GL state slot on first touch and restores newest first; cached state goes through a
  `GlStateCache`, implemented for the Minecraft `GlStateManager`.
- Shader loader: `#include` (relative and root), injected defines, `#line` mapping so driver errors read
  `file:line`, uniform location cache, reload that keeps the last good program.
- `FramebufferPool` sized in framebuffer pixels, with resize handling.
- `ShapeBatch`: batched signed distance shapes (rounded rects, outlines, circles, arcs, lines, polylines, drop
  shadows, inner glow, textured images with tint) with a one-pixel anti-aliasing ramp measured from the local
  coordinates; `Paint` for solid, four-corner, linear and radial gradients.
- Effects: dual Kawase blur, blur behind a shape, bloom, post-processing chains of custom full-screen passes.
- `ScissorStack` for nested clipping; `StateGuard` covers texture units, the array buffer, vertex attribute arrays
  and scissor state.
- MSDF text: `MsdfFont` (`.hfnt` atlases), `TextRenderer` with kerning, `§` colour and format codes (bold, italic,
  underline, strikethrough, obfuscated), classic and soft shadows, weight and letter spacing; glyphs share the
  shape batch. Inter Regular is bundled.
- `fontgen`: a Java port of msdfgen's generator that builds `.hfnt` atlases from TrueType and OpenType fonts at build
  time.
- `StencilMask` to clip drawing to any shape; `MinecraftHelios.enableStencil()`.
- Shader hot reload from a source directory (`-Dhelios.shaderDir`, `Helios.tick()`).
- JMH benchmarks and harness GPU timings; see `BENCHMARKS.md`.
- `Colors`: packed ARGB maths, HSB/HSL, sRGB, linear-light and hue interpolation, rainbow and ping-pong, hex
  parsing and formatting, readable text colour.
- `Easing` curves, `Animation` that reverses mid-flight without jumping, frame-rate independent `Smooth`,
  `Stopwatch`; all driven by an injectable `Clock`.
- `Maths`, column-major `Mat4`, and `Projector` for world-to-screen projection and 2D bounding boxes.
- `MinecraftWorld`: partial ticks, camera position, interpolated entity positions and boxes.
- `MinecraftHelios`: wiring for Forge 1.8.9, by reflection, no Mixin.
- Development harness that boots Forge 1.8.9 on LWJGL 3 through lol.trq.lazarus and checks rendering in game:
  every primitive against a software model, blur and bloom against reference images; also run in CI under Xvfb.
  `-Pworld` checks projection with the real camera matrices in a singleplayer world.
