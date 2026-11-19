# OpenGL in Helios

Helios targets one thing: an **OpenGL 4.1 core profile** context, as
[lol.trq.lazarus](https://github.com/trq-lol/lol.trq.lazarus) creates when the game starts with
`-Dlazarus.gl=core`. There is no legacy-context path, no GLSL 1.20, no second shader dialect and no fixed-function
fallback. macOS stops at 4.1, so nothing newer is used anywhere.

## The context check

`Gl41CoreBackend` (created by `MinecraftHelios.create()`) checks, once the game's context exists, that
`GL_CONTEXT_PROFILE_MASK` has the core bit and the version is 4.1 or higher, and that Lazarus's emulator is loaded.
If not, it throws:

```text
Helios requires an OpenGL 4.1 core profile context (got <version>). Start the game through Lazarus with -Dlazarus.gl=core.
```

## How Lazarus core mode affects Helios

Lazarus rewrites the OpenGL calls of every game class, Helios included:

- Fixed-function calls (`GL11` matrices, enable/disable, immediate mode, client arrays, display lists) go to its
  emulator, which keeps the old state itself and never queries the driver. Apple's driver stalls on queries.
- The emulator merges consecutive small draws into one, and flushes that batch before any state change and before
  every other OpenGL call. Helios's own calls therefore land between the game's draws in order; Helios does nothing
  for this.
- `GL15.glBindBuffer`, `GL20.glUseProgram` and `GL30.glBindVertexArray` are intercepted so the emulator's cache of
  the bound buffer, program and vertex array stays true when Helios changes them.
- Shader sources passed to `GL20.glShaderSource` are translated from legacy GLSL. Helios uploads through
  `GL20C.glShaderSource`, so its 4.10 sources reach the driver untouched.

## Which LWJGL class for which call

| Call | Class | Why |
|---|---|---|
| Bind a buffer, program or vertex array | `GL15`, `GL20`, `GL30` (no `C`) | Lazarus intercepts these and keeps its binding cache true |
| Draw | `GL11C.glDrawArrays`, `GL11C.glDrawElements`, `GL32C.glDrawElementsBaseVertex` | `GL11.glDrawArrays` is sent to the fixed-function emulator, which would replace Helios's program |
| Enable/disable, `glIsEnabled`, bind a texture, active texture | `GL11`, `GL13` | The emulator tracks these, so `glIsEnabled` is answered without a driver query |
| Read the game's matrices | the emulator's `glGetFloat(GL_MODELVIEW_MATRIX / GL_PROJECTION_MATRIX)` | Answered from the emulated matrix stacks; there are no matrix stacks in core |
| Everything else | the `C` classes | Passed to the driver after the emulator flushes |

`Gl41CoreBackend` is the only class that makes these calls; everything else goes through `GlBackend`.

## Shader dialect

- Every shader starts with `#version 410 core`. The preprocessor emits it and rejects any other `#version`.
- Vertex inputs and fragment outputs use `layout(location = N)`. Stages connect through interface blocks
  (`out Shape { ... } v;`) or location-matched variables.
- Matrices are uniforms (`u_mvp`), read from the game per flush.
- None of the fixed-function built-ins: `gl_ModelViewMatrix`, `gl_ProjectionMatrix`, `gl_Vertex`, `gl_Color`,
  `gl_MultiTexCoord*`, `gl_TexCoord`, `ftransform`, `gl_FragColor`, `texture2D`.
- Alpha testing is a `discard` in the fragment shader (`u_alphaCutoff` in `shape.fsh`).
- Full-screen passes build one triangle from `gl_VertexID`; they need an (empty) vertex array bound, no buffer.

Not available on 4.1, so never used: compute shaders, SSBOs, image load/store, atomic counters, explicit uniform
locations, `glTexStorage`, `ARB_buffer_storage` (persistent or coherent mapping), `KHR_debug`. Uniform buffers
(std140), texture buffers and fence syncs are fine.

## Drawing rules

- Helios draws only from its own buffers: indexed triangles from VBOs and IBOs. Never `GL_QUADS`, `glBegin`/`glEnd`
  or display lists; those go through the emulator, are slower and replace the program.
- Each mesh or stream has its own vertex array object. `ShapeBatch` owns one with a static index buffer
  (0-1-2, 2-3-0 per shape); `MeshRenderer` owns one per sink format and each `StaticMesh` its own; `Passes` owns an
  empty one.
- Batch aggressively. Uploads are the expensive part on Apple. A frame's shapes are appended into one buffer.
- Streamed vertex data goes through `StreamBuffer`: a ring of three buffers, each written once per frame with
  `glBufferSubData` and fenced with `glFenceSync` at the frame's end (`Helios.tick()`). A buffer is reused only once
  its fence from three frames ago has signalled. A frame that outgrows its buffer orphans it. `StreamBuffer` is the
  one place to change the upload strategy once Lazarus has measured sub-data, orphaning and unsynchronized mapping on
  Apple.
- Static meshes (index buffers, `StaticMesh`) are uploaded once with `GL_STATIC_DRAW`.
- Mesh sinks transform vertices on the CPU, so meshes with different transforms (every player's attachments) share
  one draw call. Only a `StaticMesh` takes its transform as a uniform.
- Lines are screen-facing quads expanded in the vertex shader, their width in framebuffer pixels, with a one-pixel
  anti-aliased edge and clipping at the near plane. They never use `glLineWidth`.
- Each Helios batch saves the bound program, vertex array and array buffer, and restores them at the end
  (`StateGuard`). Blend, depth, cull and texture state go through `GlStateManager` so the game's cache stays true.
- Core forward-compatible rules:
  - Line width is 1. Thick lines and outlines are SDF quads.
  - No `GL_LUMINANCE`, `GL_ALPHA` or `GL_INTENSITY` textures. Use `GL_R8`/`GL_RG8` and read `.r`/`.rg`, or set a
    texture swizzle.
  - No `glPushAttrib`/`glPopAttrib` or matrix stack for Helios's own state.
  - `glBlendFuncSeparate`, framebuffer objects and sRGB framebuffers are available.
- Blur and bloom render into Helios's own framebuffers, sized in framebuffer pixels, not GUI-scaled units (Retina
  screens are 2x). The previous framebuffer and viewport are restored afterwards.

## What macOS can't do

Apple's OpenGL stops at 4.1 core (on Apple silicon it runs on Metal, reported as "4.1 Metal"). Beyond the list
above: no debug output callbacks, so Lazarus's `-Dlazarus.gl.debug=true` reports nothing there; and buffer uploads
that touch memory the GPU may still read are expensive, which is why uploads are batched and ring-buffered.

## Testing

The harness runs only in core mode:

```sh
./gradlew :harness:runHarness25 -PjvmArgs="-Dlazarus.gl=core -Dlazarus.gl.debug=true"
```

Core mode ships in Lazarus 0.2.0, the release the harness downloads; `-PlazarusJar=<jar>` runs a local build
instead. With debug on, Lazarus prints every
driver GL error with its Java call site as `[lazarus] GL error: ...`; the harness fails the run on any such line.
CI does the same under Xvfb with Mesa, whose llvmpipe supports core 4.5.
