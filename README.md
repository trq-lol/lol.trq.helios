# lol.trq.helios

[![CI](https://github.com/trq-lol/lol.trq.helios/actions/workflows/ci.yml/badge.svg)](https://github.com/trq-lol/lol.trq.helios/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/java-17%2B-informational)](https://github.com/trq-lol/lol.trq.helios#requirements)
[![OpenGL](https://img.shields.io/badge/OpenGL-4.1%20core-informational)](docs/opengl.md)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

A rendering library for Minecraft Forge 1.8.9 running on LWJGL 3: SDF shapes, MSDF text, shaders, framebuffers,
blur and bloom, with strict GL state discipline so vanilla and OptiFine keep a coherent state.

Status: early development (milestone H3.5), nothing released yet. See [CHANGELOG.md](CHANGELOG.md).

## Requirements

- Minecraft 1.8.9 with Forge 11.15.1.2318, on Java 17 or newer, running on LWJGL 3 through
  [lol.trq.lazarus](https://github.com/trq-lol/lol.trq.lazarus).
- Lazarus 0.2.0 or newer in core mode (`-Dlazarus.gl=core`): an OpenGL 4.1 core profile context, on Windows, Linux
  and macOS. Every shader is `#version 410 core`; there is no legacy-context path. Helios checks this at start-up
  and says how to fix it. See [docs/opengl.md](docs/opengl.md) for the dialect and the drawing rules.

## What is here

| Package | |
|---|---|
| `lol.trq.helios` | `Helios`: one instance per GL context, owning the pieces below |
| `gl` | `GlBackend`, the seam every GL call goes through; `Gl41CoreBackend`, the OpenGL 4.1 core backend; `StreamBuffer`, the per-frame upload ring |
| `state` | `StateGuard` saves what a draw touches and restores it; `GlStateCache` routes cached state through the host; `ScissorStack` for nested clips; `StencilMask` to clip to any shape |
| `shader` | Loader for GLSL 4.10 core with `#include`, defines, `file:line` errors, a uniform location cache and reload |
| `fbo` | `FramebufferPool`, sized in framebuffer pixels so effects stay sharp on high-DPI screens |
| `batch` | `ShapeBatch`: rounded rects, outlines, circles, arcs, lines, polylines, drop shadows, inner glow and images, many per draw call, all anti-aliased over exactly one pixel; `Paint` for solid, corner, linear and radial gradients |
| `mesh` | `MeshRenderer`: 3D quads, anti-aliased lines and textured quads with Minecraft's entity lighting, streamed through `QuadSink`, `LineSink` and `TexturedSink` with CPU transforms so many meshes share a draw call; `MeshRecorder` and `StaticMesh` for meshes uploaded once |
| `model` | `PlayerModel`: the 1.8.9 player renderer's transform chain as matrix maths, with `Attachment` anchors (head, body, back) that follow yaw, pitch, sneaking, riding and armour; `PlayerPose` holds one frame's inputs |
| `effect` | Dual Kawase blur, blur behind a shape (frosted glass), bloom, post-processing chains |
| `text` | MSDF text: `MsdfFont`, `TextRenderer` (kerning, `§` colour and format codes, classic and soft shadows), `TextStyle`; Inter is bundled |
| `sdf` | The distance functions of the shape shader, in Java, for tests |
| `color` | `Colors`: packed ARGB maths, HSB and HSL, sRGB, linear-light and hue interpolation, rainbow and ping-pong over time, hex parsing, readable text colour |
| `anim` | `Easing` curves, `Animation` (reversible mid-flight), `Smooth` (frame-rate independent damping), `Stopwatch` |
| `math` | `Maths` (clamp, remap, angles, rounding, Gaussian kernels), `Mat4` (column-major, allocation-free), `Projector` (world to screen, 2D boxes for ESP) |
| `minecraft` | The only package that knows Minecraft: `GlStateManager` routing, the window size, stencil, `MinecraftWorld` (partial ticks, camera, interpolated entity positions and boxes) and `MinecraftPlayerPose` (a player's pose for the frame), by reflection |

No Mixin. Minecraft is reached only from `lol.trq.helios.minecraft`, so the rest of Helios builds and tests without
the game.

## Install

Releases are on GitHub Packages, which needs a GitHub token with `read:packages`:

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/trq-lol/lol.trq.helios')
        credentials {
            username = findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = findProperty('gpr.key') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}
dependencies {
    implementation 'lol.trq:lol.trq.helios:0.1.0'
}
```

The jar and its SHA-256 are also attached to each [GitHub release](https://github.com/trq-lol/lol.trq.helios/releases).

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

### Text

```java
TextStyle title = TextStyle.builder(helios.defaultFont()).size(14).shadow(TextStyle.Shadow.SOFT).build();
TextStyle body = TextStyle.builder(helios.defaultFont()).size(9).build();

ShapeBatch b = helios.shapes().begin(pixel);
helios.text().draw(b, "Settings", 12, 12, title);
helios.text().draw(b, "§aEnabled §7(press §lR§r§7 to reset)", 12, 30, body);
b.end(); // text and shapes share the batch, in order

float w = helios.text().width("Settings", title);
```

Text is drawn from a multi-channel signed distance field atlas, so it stays sharp at any size and scale. The
bundled font is [Inter](https://github.com/rsms/inter) Regular. To use another font, build an atlas with the
`fontgen` module and load it with `MsdfFont.read`:

```sh
./gradlew :fontgen:run --args="--font MyFont.ttf --out myfont.hfnt --size 40 --range 4 --png preview.png"
```

### Animation and colour

```java
Animation open = new Animation(250, Easing.CUBIC_OUT);   // per widget, kept across frames
open.forward(menuOpen);                                  // reverses smoothly mid-flight
float scale = open.between(0.9f, 1f);
int accent = Colors.lerpHsb(0xFFFF4D8D, 0xFF4D9DFF, (float) open.value());
int stripe = Colors.rainbow(System.currentTimeMillis(), 4000, index * 0.05f, 0.7f, 1f, 1f);
```

### World to screen

```java
// In a world render hook, where the camera matrices are current:
MinecraftWorld world = new MinecraftWorld(loader);        // once
projector.capture(helios.gl(), pixel);
double[] camera = world.camera(new double[3]);
float partial = world.partialTicks();
double[] box = world.box(entity, partial, camera, new double[6]);

// Later, in the GUI:
float[] rect = new float[4];
if (projector.projectBox(box[0], box[1], box[2], box[3], box[4], box[5], rect)) {
    batch.outline(rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1], 0, 1, Paint.solid(0xFFFF4040));
}
```

### 3D meshes and player attachments

```java
// In a world render hook (RenderWorldLastEvent), where vertices are world coordinates minus the camera:
MinecraftWorld world = new MinecraftWorld(loader);                 // once
MinecraftPlayerPose poses = new MinecraftPlayerPose(loader, world); // once
PlayerPose pose = new PlayerPose();
float[] m = new float[16];

float partial = world.partialTicks();
double[] camera = world.camera(new double[3]);
MeshRenderer meshes = helios.meshes();
for (Object player : players) {
    poses.read(player, partial, camera, pose);
    // Model pixels around the anchor: x toward the player's left, y down, z toward the back.
    meshes.texture(capeTexture).textured().transform(PlayerModel.anchor(pose, Attachment.BACK, m))
            .vertex(-5, 0, 0, 0, 0, 0, 0, 1, -1).vertex(5, 0, 0, 1, 0, 0, 0, 1, -1)
            .vertex(5, 16, 0, 1, 1, 0, 0, 1, -1).vertex(-5, 16, 0, 0, 1, 0, 0, 1, -1);
}
meshes.lines().transform(null).line(0, 0, 0, 0, 2, 0, 0xFF40FF40, 2); // 2 px wide at any distance
meshes.flush(); // every player's attachment above went out in one draw call

// A mesh that never changes is recorded once and drawn with a GPU transform:
MeshRecorder rec = new MeshRecorder(MeshFormat.TEXTURED);
// rec.textured().vertex(...) ...
StaticMesh hat = rec.build(meshes);
meshes.texture(hatTexture).draw(hat, PlayerModel.anchor(pose, Attachment.HEAD, m));
```

Helios provides the sinks and the anchors only; what is drawn at them is up to the caller.

### Stencil clipping

```java
MinecraftHelios.enableStencil(); // once: gives the main framebuffer a stencil buffer
helios.stencil().clip(
        () -> helios.shapes().begin(pixel).circle(40, 40, 24, Paint.solid(-1)).end(), // the mask
        true,                                                                          // keep the inside
        () -> drawAvatar());
```

### Shader hot reload

Start the game with `-Dhelios.shaderDir=<path to helios/src/main/resources/lol/trq/helios/shaders>` and call
`helios.tick()` once per frame. Edited shaders are rebuilt within a quarter of a second; one that fails to compile
keeps its last good version and the error, with `file:line`, comes back from `tick()`.

Your own GL work goes through a guard:

```java
try (StateGuard g = helios.guard()) {
    g.blend(true).program(myProgram.id()).framebuffer(fb.id());
    // draw
} // everything touched is restored, cached state through GlStateManager
```

## Building and testing

```sh
./gradlew spotlessCheck build        # unit tests, no GL needed; also builds the font atlas
./gradlew :helios:jmh                # CPU benchmarks, see BENCHMARKS.md
./gradlew :harness:runHarness25 -PjvmArgs="-Dlazarus.gl=core -Dlazarus.gl.debug=true"
./gradlew :harness:runHarness21 -PjvmArgs="..." -Poptifine=<path to an OptiFine 1.8.9 jar>
```

The harness downloads Minecraft 1.8.9 and Forge into `.harness-cache/`, boots the game on LWJGL 3 in Lazarus core
mode with sound muted and the splash screen off, and loads a test mod. It uses the checksummed Lazarus release jar;
`-PlazarusJar=<jar>` runs a local Lazarus build instead. With `-Dlazarus.gl.debug=true` any `[lazarus] GL error` line fails the run. On the main menu the mod:

- draws every primitive and compares each pixel around it with a software model of the shader (within 3/255);
- checks that many shapes go out in one draw call;
- compares blur and bloom with reference images in `harness/references/` (per framebuffer size; refresh them with
  `-PupdateReferences`, require them with `-PrequireReferences`);
- draws one glyph and compares each pixel with a software model of the MSDF shader;
- compares text and stencil clipping with reference images;
- runs a post-processing chain, the scissor stack and stencil masks offscreen;
- projects points through the game's real GUI matrices and checks they land where they were drawn;
- checks that GL state is unchanged afterwards and that the `GlStateManager` cache still matches GL;
- saves screenshots to `harness/build/run/<task>/helios/` and exits 0 only if every check passed.

With `-Pbench` it also prints GPU timings; see [BENCHMARKS.md](BENCHMARKS.md). With `-Pworld` it then creates a
world and checks projection with the real camera matrices: a point ahead of the player's eyes must project to the
exact screen centre, and the player's interpolated position must sit on the camera. It reads the player's pose,
checks the head anchor sits 1.882 blocks above the feet, and draws through every mesh sink and a static mesh at the
anchors. The harness turns view bobbing
off for this. It opens a game window while it
runs. OptiFine cannot be redistributed: point `-Poptifine` at a local copy. CI runs the harness in core mode on Linux under a virtual display with Mesa's software OpenGL (core 4.5).

## For AI agents

Start with [CLAUDE.md](CLAUDE.md) for the mental model and the traps, then [llms.txt](llms.txt) for an index of
every doc and core source file. The tests under `helios/src/test` are the executable examples of the API, and
`harness-mod` shows real use inside the game.

## License

[MIT](LICENSE). The bundled Inter font is under the SIL Open Font License 1.1, and `fontgen` ports parts of msdfgen
(MIT); see [NOTICE.md](NOTICE.md).

## Activity

![Repobeats analytics image](https://repobeats.axiom.co/api/embed/2735f359d4225648d9919773a9f33c3ff47d820b.svg "Repobeats analytics image")
