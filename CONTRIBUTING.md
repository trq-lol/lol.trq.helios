# Contributing

- Conventional Commits, imperative, lowercase subject, no trailing period. No AI-attribution trailers.
- `./gradlew spotlessApply` then `./gradlew spotlessCheck build` before pushing.
- Bytecode stays at release 17. GL code targets the OpenGL 4.1 core profile only and follows
  [docs/opengl.md](docs/opengl.md): `#version 410 core`, nothing past 4.1, binds through `GL15`/`GL20`/`GL30`,
  draws through the `C` classes.
- Every GL state change goes through a `StateGuard`, so it is restored when the draw ends.
- Logic that does not need a GL context ships with a unit test in `helios/src/test`. Rendering changes are checked in
  the harness (see the README) and the result is described in the pull request.
- A change to a hot path (batch emit, text layout, state guard) comes with a `./gradlew :helios:jmh` run before and
  after, and updated numbers in `BENCHMARKS.md` if they moved.
- Never name a consuming application in code, docs, comments or logs. No machine paths or secrets.
- OptiFine cannot be redistributed: test with a local copy, never commit it.
