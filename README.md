# CatalogLens

IntelliJ / Android Studio plugin that makes Gradle version catalogs (`libs.versions.toml`) navigable.

## Features

- **Artifact links** — Ctrl/Cmd-click any `[libraries]` entry to open its Maven Central (or MvnRepository) page. Supports all catalog syntaxes: `group`/`name`, `module`, string notation, long-form dotted keys, literal versions and `version.ref`.
- **Upstream links** — gutter icons on `[versions]` entries open release notes / changelogs for the libraries and plugins referencing that version. Driven by a bundled map of popular artifacts (AndroidX, Kotlin(x), Square, Koin, Ktor, Coil, Dagger/Hilt, …) with group-prefix fallback.
- **Sort groups A→Z** — a floating editor icon (also in the editor context menu) sorts entries alphabetically within each group. A group is a contiguous run of entry lines; blank lines, comments and table headers act as delimiters and stay in place. A diff preview shows the result before anything is written — revert individual changes with the gutter arrows, then apply or cancel. Multi-line entries (e.g. `[bundles]` arrays) move as a single unit.
- **Configurable** — `Settings → Tools → CatalogLens`: toggle the bundled map, switch the artifact link target, and add custom mappings. Project-scoped mappings (`.idea/cataloglens.xml`, shareable) shadow global ones, which shadow the bundled map.

All URLs are built locally from the file contents — the plugin performs zero network calls while editing. A browser opens only when you click a link.

Mapping keys accept three forms: exact `group:artifact`, a Gradle plugin ID, or a group prefix (matched on dot boundaries, longest prefix wins). Mapping values are one or more URLs separated by commas or spaces.

## Installation

Until the plugin is live on JetBrains Marketplace: download (or build) the ZIP and install via `Settings → Plugins → ⚙ → Install Plugin from Disk…`. Requires IntelliJ-based IDE 2025.1+ (Android Studio Narwhal or newer).

## Contributing

Contributions are welcome — the easiest and most valuable one is **extending the bundled artifact map**.

The map lives in a single file:

```
src/main/resources/cataloglens/artifact-map.json
```

If a library you use has no changelog link (no gutter icon on its `[versions]` entry), add it there and open a PR:

- `artifacts` — exact keys: `"group:artifact"` or a Gradle plugin ID, mapped to a list of URLs (releases page, changelog, …).
- `groupPrefixes` — fallback keys matched against the artifact's group on dot boundaries; the longest matching prefix wins.

Example:

```json
"artifacts": {
  "com.squareup.okhttp3:okhttp": [
    "https://square.github.io/okhttp/changelogs/changelog/",
    "https://github.com/square/okhttp/releases"
  ]
}
```

Prefer official release-notes pages over generic repository roots. Bug reports and feature PRs are equally welcome — please keep the zero-network-at-edit-time guarantee intact.

## Development

- Build: `./gradlew build`
- Tests: `./gradlew test`
- Run in sandbox IntelliJ IDEA CE: `./gradlew runIde`
- Run in sandbox Android Studio: `./gradlew runAndroidStudio` — uses `/Applications/Android Studio.app` by default; override with `-PandroidStudioPath=/path/to/Android Studio.app`
- Verify compatibility: `./gradlew verifyPlugin` (downloads target IDEs on first run)

A sample catalog for manual testing lives at `sample/gradle/libs.versions.toml`.

### Publishing

First Marketplace upload is manual: `./gradlew buildPlugin`, then upload `build/distributions/CatalogLens-<version>.zip` at [plugins.jetbrains.com](https://plugins.jetbrains.com) → Add new plugin. Subsequent releases:

```bash
PUBLISH_TOKEN=… CERTIFICATE_CHAIN=… PRIVATE_KEY=… PRIVATE_KEY_PASSWORD=… ./gradlew publishPlugin
```

Signing keys are generated with:

```bash
openssl genpkey -aes-256-cbc -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:4096
openssl req -key private.pem -new -x509 -days 365 -out chain.crt
```

## License

[Apache License 2.0](LICENSE)
