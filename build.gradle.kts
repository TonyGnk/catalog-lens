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
        testFramework(TestFrameworkType.Platform)
        zipSigner()
    }
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
