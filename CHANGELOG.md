# Changelog

All notable changes are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/) and the
project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

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
- `MinecraftHelios`: wiring for Forge 1.8.9, by reflection, no Mixin.
- Development harness that boots Forge 1.8.9 on LWJGL 3 through lol.trq.lazarus and checks rendering in game:
  every primitive against a software model, blur and bloom against reference images; also run in CI under Xvfb.
