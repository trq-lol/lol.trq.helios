# OpenGL in Helios

Helios needs an OpenGL 4.1 core profile context. [lol.trq.lazarus](https://github.com/trq-lol/lol.trq.lazarus)
creates one with its core renderer, which is the default on LWJGL 3 from Lazarus 0.4.0. On Lazarus 0.2.x and 0.3.x,
start the game with `-Dlazarus.gl=core`. Helios has no legacy-context path, no GLSL 1.20 and no fixed-function
fallback. macOS stops at 4.1, so Helios uses nothing newer.

## The context check

`MinecraftHelios.create()` builds a `Gl41CoreBackend`, which checks that `GL_CONTEXT_PROFILE_MASK` has the core bit
and the version is 4.1 or higher. Otherwise it throws:

```text
Helios requires an OpenGL 4.1 core profile context (got <version>). Start the game through Lazarus with -Dlazarus.gl=core.
```

On Lazarus 0.4.0 or newer this means the game was started with `-Dlazarus.gl=compat`, or without Lazarus's LWJGL 3
support.

## How the Lazarus core renderer treats Helios

Lazarus rewrites the OpenGL calls of every game class, Helios included:

- Fixed-function calls (`GL11` matrices, enable and disable, immediate mode, client arrays, display lists) go to its
  emulator. The emulator keeps that state itself and never queries the driver, because Apple's driver stalls on
  queries.
- The emulator merges small consecutive draws and flushes them before any state change and before every other
  OpenGL call. Helios's draws land between the game's in order.
- `GL15.glBindBuffer`, `GL20.glUseProgram` and `GL30.glBindVertexArray` are intercepted, so the emulator's binding
  cache stays true when Helios changes them.
- Shader sources passed to `GL20.glShaderSource` are translated from legacy GLSL. Helios uploads through
  `GL20C.glShaderSource`, so its sources reach the driver as written.
- Helios reads the game's matrices, the bound program, vertex array and array buffer, and the viewport from
  Lazarus's state tracker (`GlState`) instead of asking the driver.

## Which LWJGL class for which call

| Call | Class | Why |
|---|---|---|
| Bind a buffer, program or vertex array | `GL15`, `GL20`, `GL30` (no `C`) | Lazarus intercepts these and keeps its binding cache true |
| Draw | `GL11C.glDrawArrays`, `GL11C.glDrawElements`, `GL32C.glDrawElementsBaseVertex` | `GL11.glDrawArrays` goes to the fixed-function emulator, which would replace Helios's program |
| Enable and disable, `glIsEnabled`, bind a texture, active texture | `GL11`, `GL13` | The emulator tracks these and answers without a driver query |
| Read the game's matrices | Lazarus `GlState`, or the emulator's `glGetFloat` | Core has no matrix stacks; the emulator keeps them |
| Everything else | the `C` classes | Passed to the driver after the emulator flushes |

`Gl41CoreBackend` is the only class that calls LWJGL. Everything else goes through `GlBackend`.

## Shader dialect

- Every shader starts with `#version 410 core`. The preprocessor writes that line and rejects any other `#version`.
- Vertex inputs and fragment outputs use `layout(location = N)`. Stages connect through interface blocks
  (`out Shape { ... } v;`) or variables matched by name.
- Matrices are uniforms (`u_mvp`), read from the game at each flush.
- No fixed-function built-ins: `gl_ModelViewMatrix`, `gl_ProjectionMatrix`, `gl_Vertex`, `gl_Color`,
  `gl_MultiTexCoord*`, `gl_TexCoord`, `ftransform`, `gl_FragColor`, `texture2D`.
- Alpha testing is a `discard` in the fragment shader (`u_alphaCutoff`).
- Full-screen passes build one triangle from `gl_VertexID`. They need an empty vertex array bound and no buffer.

OpenGL 4.1 has no compute shaders, SSBOs, image load and store, atomic counters, explicit uniform locations,
`glTexStorage`, persistent mapping or `KHR_debug`, so Helios uses none of them. Uniform buffers (std140), texture
buffers and fence syncs are available.

## Drawing rules

- Helios draws only from its own buffers, as indexed triangles. It never uses `GL_QUADS`, `glBegin` and `glEnd`, or
  display lists. Those go through the emulator, cost more and replace the program.
- Each stream or mesh has its own vertex array. `ShapeBatch` owns one with a static index buffer (0-1-2, 2-3-0 per
  shape). `MeshRenderer` owns one per sink format, each `StaticMesh` owns its own, and `Passes` owns an empty one.
- Shapes are batched. Uploads are the expensive part on Apple, so a frame's shapes go into one buffer.
- Streamed vertex data goes through `StreamBuffer`, a ring of three buffers. Each is written once per frame with
  `glBufferSubData` and fenced with `glFenceSync` at the end of the frame (`Helios.tick()`). A buffer is reused only
  when its fence has signalled. A frame that outgrows its buffer orphans it. `-Dhelios.stream=map` writes through an
  unsynchronized `glMapBufferRange` instead; it is slower on Apple M2 and is not the default.
- Static meshes (index buffers, `StaticMesh`) are uploaded once with `GL_STATIC_DRAW`.
- Mesh sinks transform vertices on the CPU, so meshes with different transforms share one draw call. Only a
  `StaticMesh` takes its transform as a uniform.
- Lines are screen-facing quads built in the vertex shader. Their width is in framebuffer pixels, with a one-pixel
  anti-aliased edge and clipping at the near plane. `glLineWidth` is never used.
- Each batch saves the bound program, vertex array and array buffer, and restores them when it ends (`StateGuard`).
  Blend, depth, cull and texture state go through `GlStateManager`, so the game's cache stays true.
- Core profile rules:
  - Line width is 1. Thick lines and outlines are SDF quads.
  - No `GL_LUMINANCE`, `GL_ALPHA` or `GL_INTENSITY` textures. Use `GL_R8` or `GL_RG8` and read `.r` or `.rg`, or set
    a texture swizzle.
  - No `glPushAttrib`, `glPopAttrib` or matrix stack for Helios's own state.
  - `glBlendFuncSeparate`, framebuffer objects and sRGB framebuffers are available.
- Blur and bloom render into Helios's own framebuffers, sized in framebuffer pixels rather than GUI units (Retina
  screens are 2x). The previous framebuffer and viewport are restored afterwards.

## macOS

Apple's OpenGL stops at 4.1 core. On Apple silicon it runs on Metal and reports "4.1 Metal". It has no debug output,
so Lazarus's `-Dlazarus.gl.debug=true` cannot report GL errors there. Uploads that touch memory the GPU may still
read are expensive, which is why Helios batches and ring-buffers them.

## Testing

```sh
./gradlew :harness:runHarness25 -PjvmArgs="-Dlazarus.gl.debug=true"
```

The harness downloads the Lazarus release named in `gradle.properties` and checks its SHA-256. `-PlazarusJar=<jar>`
runs a local Lazarus build instead. With debug output on, Lazarus prints every driver GL error with its Java call
site as `[lazarus] GL error: ...`, and the harness fails the run on any such line. CI runs the same check on Linux
under Xvfb with Mesa, whose llvmpipe supports core 4.5. With OptiFine (`-Poptifine=<jar>`, local only),
`-PfastRender` turns its Fast Render option on for the run.
