# Contributing

- Conventional Commits, imperative, lowercase subject, no trailing period. No AI-attribution trailers.
- `./gradlew spotlessApply` then `./gradlew spotlessCheck build` before pushing.
- Bytecode stays at release 17. GL code stays inside OpenGL 2.1, GLSL 1.20 and `ARB_framebuffer_object` unless it
  sits behind a capability check with a baseline fallback.
- Every GL state change goes through a `StateGuard`, so it is restored when the draw ends.
- Logic that does not need a GL context ships with a unit test in `helios/src/test`. Rendering changes are checked in
  the harness (see the README) and the result is described in the pull request.
- Never name a consuming application in code, docs, comments or logs. No machine paths or secrets.
- OptiFine cannot be redistributed: test with a local copy, never commit it.
