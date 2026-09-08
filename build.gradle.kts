import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("org.toml.lang")
        bundledPlugin("org.jetbrains.plugins.gradle")
        testFramework(TestFrameworkType.Platform)
        zipSigner()
    }
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion").get()
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
        changeNotes = """
            <ul>
                <li>0.7.7: the Releases tool window now renders Markdown changelogs as well. A link to a repository's CHANGELOG.md — OkHttp, Retrofit, Coil, Moshi, Picasso, desugar_jdk_libs, the Facebook Android SDK — opens inline with the same per-version cards you already get for GitHub Releases and developer.android.com: the entry matching your catalog is marked "current", and every other one offers a one-click "Use this version" that pins it into libs.versions.toml. Which heading starts a release is worked out per file, so Keep a Changelog (<code>## [3.6.2] - September 4, 2026</code>), <code>## Version 5.5.0</code> and underlined <code>Version 2.1.5 *(2018-03-07)*</code> all split correctly while <code>### Added</code> subsections stay inside the release they belong to. Reference-style links are resolved into real links and their definition block is dropped, so the notes read cleanly, and the file is fetched from raw.githubusercontent.com — no GitHub API rate limit, and repository renames keep working. Fixed: developer.android.com pages no longer show an empty "Version 1.3 — No release notes" card with a meaningless "Use 1.3" button. Those headings only group a release series (DataStore, Room, Media3, Compose, Navigation and most other AndroidX pages), so they are now dropped, or kept as a plain divider with no version to pin when they carry a compatibility note of their own; a stable release that groups its own pre-releases keeps both its notes and its pin. Also fixed: the "Open in browser" link no longer draws a focus ring when the tool window opens, <code>_2026-08-16_</code>-style dates now render as emphasis instead of raw underscores, and a page that loads but holds no recognisable version notes now says exactly that instead of blaming your connection. Bundled artifact map refreshed: OkHttp and Retrofit have moved to the lysine-dev organisation and Square's old hosted changelog is gone, so both now point at the live repositories. Added Navigation3, ProfileInstaller, Baseline Profile, LocalBroadcastManager, the Facebook Android SDK and the Koin compiler plugin, plus per-library group prefixes for Compose animation, compiler, foundation, material, material3 and runtime so those resolve to their own release notes instead of the umbrella Compose page.</li>
                <li>0.7.5: new "Resolved Dependencies" surface — see what your version catalog actually resolves to, not just what it declares. End-of-line inlay hints in libs.versions.toml show the resolved version whenever it differs from the declared one. Click a hint (or right-click an entry → "Why this version?") to resolve the artifact through Gradle and see why that version was selected — selection reasons, the requested-by chain, and one-click re-resolution in another configuration — in a new "CatalogLens Resolved" tool window. Capture a resolved baseline, then "Compute Resolved Delta" to diff the catalog against it: what you changed, transitive ripples, catalog pins a transitive overrode, and rejected/excluded artifacts. Plus a new Alt+Enter intention on any [libraries], [bundles] or [plugins] entry to copy its Gradle dependency declaration — implementation(libs.…) / alias(libs.plugins.…) — to the clipboard. Requires the bundled Gradle plugin.</li>
                <li>0.6.0: changelogs now open inline in a new "CatalogLens Releases" tool window instead of the browser. GitHub release pages are fetched via the REST API (tag, date, formatted notes, clickable links; cached 30 minutes for the unauthenticated rate limit) and developer.android.com pages (androidx, AGP, Play Core, …) are parsed and rendered the same way, split into per-version cards. The viewer knows your catalog version: the matching entry is marked "current", and every other version shows a "Use this version" link that pins it straight into libs.versions.toml (undoable). Theme-aware colors, selectable headers, soft-wrapping. Pages that can't be parsed fall back to the browser. Also expanded the bundled artifact map with many more AndroidX and third-party release links.</li>
                <li>0.5.4: expanded bundled artifact map — added changelog links for Paparazzi, detekt, ktlint, Turbine, Dokka, ML Kit, OpenTelemetry, mockk, Mockito, Hamcrest, assertk, Compose Hot Reload, koin-annotations, flow-preferences, JWTDecode, compose-stable-marker, and others; new group-prefix fallbacks for GMS, Google Maps, ZXing, Salesforce Marketing Cloud, and more.</li>
                <li>0.5.3: sort preview now lists each unsorted group with a checkbox — pick exactly which groups to sort, with a live diff preview. Safer apply: keeps caret and scroll position, aborts if the document changed underneath, dialog size is remembered. Quoted keys now sort by their unquoted name.</li>
                <li>0.5.2: sort preview result pane editable — revert individual hunks before applying.</li>
                <li>0.5.1: maintenance release — links always open in the system browser.</li>
                <li>0.5.0: new editor action — sort catalog entries A→Z within each group (blank lines, comments and table headers act as delimiters and stay in place), with a diff preview where individual changes can be reverted before applying. Available from a floating editor icon and the editor context menu.</li>
                <li>0.4.0: Maven Central links open the Versions tab directly; maven.google.com links open the artifact's full version list (no stale pre-selected version).</li>
                <li>0.3.0: Google-Maven-only artifacts (androidx, com.android, Firebase, GMS, ML Kit, …) now link to maven.google.com instead of a dead Maven Central page.</li>
                <li>0.2.0: per-file cached catalog index (faster highlighting on large catalogs), long-form [plugins] entries now resolved, settings UI fixes (table editing no longer interrupted, robust bindings), global settings now roam via Settings Sync.</li>
                <li>0.1.0: artifact links on [libraries] entries, upstream changelog gutter icons on [versions] entries, bundled artifact map with project/global overrides.</li>
            </ul>
        """.trimIndent()
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    pluginVerification {
        ides {
            recommended()
        }
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
        )
    }
}

val runAndroidStudio by intellijPlatformTesting.runIde.registering {
    localPath = file(
        providers.gradleProperty("androidStudioPath").getOrElse("/Applications/Android Studio.app")
    )
}
