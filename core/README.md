# core — Fiscal Nest Core engine

This module is an unmodified copy of the sources and tests of
[skrpld/fiscal-nest-core](https://github.com/skrpld/fiscal-nest-core) (Apache-2.0),
package `fiscalnest.core`, spec v1.2.

| | |
|---|---|
| Upstream commit | `5efd00f3b98d813c4c8f077ce41833a32b44bd68` |
| Upstream paths | `src/main/kotlin/fiscalnest/core`, `src/test/kotlin/fiscalnest/core` |

The engine is pure business logic: no Android SDK, no I/O, no formatting. The Android client
lives in `:domain` (models, persistence format, mapping to the engine) and `:app` (UI).

## Updating

1. Copy `src/main/kotlin/fiscalnest/core/*.kt` and `src/test/kotlin/fiscalnest/core/*.kt` from the
   new upstream revision over this module's `src` directory.
2. Update the commit hash above.
3. Run `./gradlew :core:test :domain:test`.

Do not edit the engine here: fix it upstream and copy it back, so both stay identical.
