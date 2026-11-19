# lol.trq.helios

[![CI](https://github.com/trq-lol/lol.trq.helios/actions/workflows/ci.yml/badge.svg)](https://github.com/trq-lol/lol.trq.helios/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/java-17%2B-informational)](https://github.com/trq-lol/lol.trq.helios#requirements)
[![OpenGL](https://img.shields.io/badge/OpenGL-4.1%20core-informational)](docs/opengl.md)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

A rendering library for Minecraft Forge 1.8.9 on modern OpenGL.

It gives a mod or client author anti-aliased shapes, sharp text at any size, blur and bloom, clipping, 3D meshes and
player-model anchors, drawn in few draw calls on the OpenGL 4.1 core profile. Every draw restores the GL state it
touched, so vanilla and OptiFine rendering are unaffected.

## Requirements

| | |
|---|---|
| Minecraft | 1.8.9 with Forge 11.15.1.2318 |
| Java | 17, 21 or 25 |
| Runtime | [lol.trq.lazarus](https://github.com/trq-lol/lol.trq.lazarus) 0.2.0 or newer with LWJGL 3 and its core renderer |
| OpenGL | 4.1 core profile. Legacy (compatibility) contexts are not supported. |
| Systems | Windows, Linux and macOS, including Apple silicon |
| OptiFine | Optional. HD U M5 is tested, with Fast Render on or off. |

The core renderer is the default on LWJGL 3 from Lazarus 0.4.0. On Lazarus 0.2.x and 0.3.x, start the game with
`-Dlazarus.gl=core`. Helios checks the context when it starts and throws with instructions if it is wrong. See
[docs/opengl.md](docs/opengl.md) for the shader dialect and the drawing rules.

## Install

Releases are on GitHub Packages. Gradle needs a GitHub token with `read:packages`:

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
    implementation 'lol.trq:lol.trq.helios:0.1.5'
}
```

The jar, its sources and their SHA-256 sums are also on each
[GitHub release](https://github.com/trq-lol/lol.trq.helios/releases). Helios has no dependencies beyond LWJGL 3 and
Minecraft, which the game provides.

## Usage

```java
public final class Card {
    private Helios helios;

    // Call from a GuiScreen draw on the render thread. guiScale is ScaledResolution.getScaleFactor().
    public void draw(int guiScale) {
        if (helios == null) {
            helios = MinecraftHelios.create();
        }
        float pixel = 1f / guiScale;
        TextStyle title = TextStyle.builder(helios.defaultFont()).size(14).build();

        helios.effects().blurBehind(12, 12, 160, 64, 8, pixel, 3, 2, 0xFFFFFFFF);

        ShapeBatch batch = helios.shapes().begin(pixel);
        batch.shadow(12, 14, 160, 64, 8, 6, 0x66000000)
                .rect(12, 12, 160, 64, 8, Paint.vertical(0xCC3B82F6, 0xCC1D4ED8))
                .outline(12, 12, 160, 64, 8, 1, Paint.solid(0x40FFFFFF));
        helios.text().draw(batch, "Settings", 24, 24, title);
        batch.end(); // shapes and text go out in one draw call

        helios.tick(); // once per frame
    }
}
```

Coordinates are GUI units. `pixel` is the size of one framebuffer pixel in those units; every edge is anti-aliased
over exactly one pixel. Colours are `0xAARRGGBB`, not premultiplied. Angles are radians, 0 along +x, growing clockwise
on screen.

### Text

```java
TextStyle body = TextStyle.builder(helios.defaultFont()).size(9).shadow(TextStyle.Shadow.SOFT).build();
helios.text().draw(batch, "§aEnabled §7(press §lR§r§7 to reset)", 12, 30, body);
float width = helios.text().width("Settings", body);
```

Text uses a multi-channel signed distance field atlas, so it stays sharp at any size. The bundled font is
[Inter](https://github.com/rsms/inter) Regular. For another font, build an atlas with the `fontgen` module and load it
with `MsdfFont.read`:

```sh
./gradlew :fontgen:run --args="--font MyFont.ttf --out myfont.hfnt --size 40 --range 4 --png preview.png"
```

### Animation and colour

```java
Animation open = new Animation(250, Easing.CUBIC_OUT); // one per widget, kept across frames
open.forward(menuOpen);                                // reverses smoothly mid-flight
float scale = open.between(0.9f, 1f);
int accent = Colors.lerpHsb(0xFFFF4D8D, 0xFF4D9DFF, (float) open.value());
```

### World to screen

```java
// In a world render hook, where the camera matrices are current:
MinecraftWorld world = new MinecraftWorld(loader); // once
projector.capture(helios.gl(), pixel);
double[] box = world.box(entity, world.partialTicks(), world.camera(new double[3]), new double[6]);

// Later, in the GUI:
float[] rect = new float[4];
if (projector.projectBox(box[0], box[1], box[2], box[3], box[4], box[5], rect)) {
    batch.outline(rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1], 0, 1, Paint.solid(0xFFFF4040));
}
```

### 3D meshes and player anchors

```java
// In RenderWorldLastEvent, where vertices are world coordinates minus the camera:
MinecraftPlayerPose poses = new MinecraftPlayerPose(loader, world); // once
PlayerPose pose = new PlayerPose();
float[] m = new float[16];

MeshRenderer meshes = helios.meshes();
for (Object player : players) {
    poses.read(player, partial, camera, pose);
    // Model pixels around the anchor: x toward the player's left, y down, z toward the back.
    meshes.texture(capeTexture).textured().transform(PlayerModel.anchor(pose, Attachment.BACK, m))
            .vertex(-5, 0, 0, 0, 0, 0, 0, 1, -1).vertex(5, 0, 0, 1, 0, 0, 0, 1, -1)
            .vertex(5, 16, 0, 1, 1, 0, 0, 1, -1).vertex(-5, 16, 0, 0, 1, 0, 0, 1, -1);
}
meshes.flush(); // every player above in one draw call
```

`QuadSink.triangle` draws fans, strips and cones one triangle at a time, and `LineSink.strip` draws a path or a
loop. Meshes that never change are recorded once with `MeshRecorder` and drawn from a `StaticMesh`. Helios provides the
sinks and the anchors; what is drawn at them is up to the caller.

### Clipping and your own GL work

```java
MinecraftHelios.enableStencil(); // gives the main framebuffer a stencil buffer; later calls do nothing
helios.stencil().clip(
        () -> helios.shapes().begin(pixel).circle(40, 40, 24, Paint.solid(-1)).end(), // the mask
        true,                                                                          // keep the inside
        () -> drawAvatar());

try (StateGuard g = helios.guard()) {
    g.blend(true).program(myProgram.id()).framebuffer(fb.id());
    // draw
} // everything touched is restored, cached state through GlStateManager
```

Start the game with `-Dhelios.shaderDir=<path to the shader sources>` to reload edited shaders while it runs. A
shader that fails to compile keeps its last good version, and `tick()` returns the error as `file:line`.

## How it works

- **One instance per context.** `MinecraftHelios.create()` wires Helios to the game by reflection: the GL backend,
  the window size, and `GlStateManager`. Nothing outside the `minecraft` package refers to Minecraft, and there is no
  Mixin.
- **State.** Every draw opens a `StateGuard`. It saves each piece of GL state the first time the draw changes it and
  restores it at the end. Blend, depth, cull and texture state go through `GlStateManager`, so its cache stays true
  for vanilla and OptiFine.
- **Batching.** Shapes and glyphs share one vertex format and one shader. A batch flushes when it is full, when an
  image needs another texture, and at `end()`. Vertex data goes into a ring of three buffers, fenced per frame, which
  `tick()` advances.
- **Shapes.** Each shape is a quad whose fragment shader computes a signed distance to the edge. Coverage falls off
  over exactly one framebuffer pixel.
- **Text.** Glyphs come from an MSDF atlas built at compile time by `fontgen`, a Java port of msdfgen.
- **Effects.** Blur is a dual Kawase blur on Helios's own framebuffers, sized in framebuffer pixels. Bloom blurs a
  drawing and adds it back.
- **Meshes.** Sinks transform vertices on the CPU, so meshes with different transforms share a draw call. Lines are
  screen-facing quads a fixed number of pixels wide.
- **Player anchors.** `PlayerModel` reproduces the 1.8.9 player renderer's transforms, so an attachment follows body
  and head yaw, pitch, sneaking, riding and armour exactly.

| Package | Contents |
|---|---|
| `lol.trq.helios` | `Helios`, which owns the parts below |
| `gl` | `GlBackend` and the OpenGL 4.1 core backend; `StreamBuffer`, the per-frame upload ring |
| `state` | `StateGuard`, `GlStateCache`, `ScissorStack`, `StencilMask` |
| `shader` | Shader loader with `#include`, defines, `file:line` errors and reload |
| `fbo` | `FramebufferPool`, sized in framebuffer pixels |
| `batch` | `ShapeBatch`: rounded rects, outlines, circles, arcs, lines, polylines, shadows, inner glow, images; `Paint` for solid, corner, linear and radial colour |
| `effect` | Blur, blur behind a shape, bloom, post-processing chains |
| `text` | `MsdfFont`, `TextRenderer` (kerning, `§` codes, shadows), `TextStyle` |
| `mesh` | `MeshRenderer` with `QuadSink`, `LineSink` and `TexturedSink`; `MeshRecorder`, `StaticMesh` |
| `model` | `PlayerModel`, `PlayerPose`, `Attachment` |
| `color`, `anim`, `math` | `Colors`, `Easing`, `Animation`, `Smooth`, `Mat4`, `Projector` |
| `minecraft` | `MinecraftHelios`, `MinecraftWorld`, `MinecraftPlayerPose`, `MinecraftOptiFine` |

## Limitations

- OpenGL 4.1 core profile only. Helios does not run on a legacy context, including Lazarus with
  `-Dlazarus.gl=compat`.
- Forge 1.8.9 only, on LWJGL 3 through Lazarus. It does not run on the stock LWJGL 2 game.
- Call Helios on the render thread only. It is not thread-safe.
- Screen-capturing effects (blur behind, bloom) expect GUI coordinates from the top left of the screen.
- Player anchors cover the head, body and back. Arms and legs have no anchors, and sleeping and death poses are not
  modelled.
- OptiFine shader packs are untested.
- On macOS the driver reports no GL errors, so the harness cannot check for them there.

## Verification

The test harness boots Minecraft 1.8.9 with Forge on Lazarus, loads a test mod and fails on any wrong pixel, any
leaked GL state or any GL error the driver reports. It checks:

- every primitive against a software model of its shader, pixel by pixel, within 3/255;
- one glyph against a software model of the MSDF shader;
- blur, bloom, text and stencil clipping against reference images;
- the scissor stack, stencil masks and a post-processing chain;
- that many shapes go out in one draw call;
- that GL state and the `GlStateManager` cache are unchanged afterwards;
- in a world (`-Pworld`): projection through the real camera matrices, the head anchor's height, and drawing through
  every mesh sink.

Results for Helios 0.1.1 on Lazarus 0.4.2 with the splash screen on:

| System | GPU | Java | OptiFine HD U M5 | Result |
|---|---|---|---|---|
| Windows 10 | NVIDIA RTX 4070 | 17, 21, 25 | without on 17, 21 and 25; with on 21 and 25; with Fast Render on 25 | All checks pass, 0 GL errors |
| macOS | Apple M2 | 25 | without | All checks pass |
| Linux (CI) | Mesa llvmpipe | 17, 25 | without | All checks pass, 0 GL errors |

Unit tests cover everything that does not need a GL context. CPU and GPU timings are in
[BENCHMARKS.md](BENCHMARKS.md). A few from the RTX 4070 at 1281x720:

| Workload | GPU time (median) |
|---|---|
| 10000 rounded rects | 4.27 ms |
| 2000 glyphs of 9 px text | 0.28 ms |
| Full-screen blur behind, 4 iterations | 0.27 ms |
| Full-screen bloom, 3 iterations | 0.23 ms |

## Building and testing

```sh
./gradlew spotlessCheck build                            # unit tests, no GL needed
./gradlew :helios:jmh                                    # CPU benchmarks
./gradlew :harness:runHarness25 -PjvmArgs="-Dlazarus.gl.debug=true"
./gradlew :harness:runHarness21 -Pworld -Poptifine=<path to an OptiFine 1.8.9 jar> -PfastRender
```

The harness downloads Minecraft and Forge into `.harness-cache/` and opens a game window with the sound muted.
`-Pworld` adds the world checks, `-Pbench` prints GPU timings, `-PupdateReferences` rewrites the reference images for
the current framebuffer size, and `-PlazarusJar=<jar>` runs a local Lazarus build. OptiFine cannot be redistributed,
so `-Poptifine` points at a local copy. Screenshots go to `harness/build/run/<task>/helios/`.

## For AI agents

Start with [CLAUDE.md](CLAUDE.md) for the mental model and the traps, then [llms.txt](llms.txt) for an index of
every doc and core source file. The tests under `helios/src/test` are the executable examples of the API, and
`harness-mod` shows real use inside the game.

## License

[MIT](LICENSE). The bundled Inter font is under the SIL Open Font License 1.1, and `fontgen` ports parts of msdfgen
(MIT); see [NOTICE.md](NOTICE.md).

## Activity

![Repobeats analytics image](https://repobeats.axiom.co/api/embed/2735f359d4225648d9919773a9f33c3ff47d820b.svg "Repobeats analytics image")
