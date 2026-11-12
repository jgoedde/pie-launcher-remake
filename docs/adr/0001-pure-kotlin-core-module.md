# Pure-Kotlin `:core` module next to `:app`

The project has two Gradle modules: `:app` (Android, Compose UI, platform adapters, packaged by feature: `pie`, `home`, `drawer`, `widgets`, `icons`, `config`, `platform`) and `:core`, a plain Kotlin/JVM module holding the deep logic: Pie geometry and hit-testing, the Pie gesture state machine, Layer navigation, Pie Config decoding and validation, and App Drawer search ranking. We split this way so that the compiler, not code review, keeps the logic free of Android types. All code is written by agents, and they tend to pull a `Context` into anything. The split also lets the bulk of the tests run as fast JVM unit tests. We rejected a single module (purity only by convention) and one module per feature (too much build overhead for a personal app).

## Consequences

The Pie gesture runs at touch-event rate as the `:core` state machine, held locally in the Compose pointer handler. It is not routed through a `ViewModel`, which only supplies the resolved Pie Config.
