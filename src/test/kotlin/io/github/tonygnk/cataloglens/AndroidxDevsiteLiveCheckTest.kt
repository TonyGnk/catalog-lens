package io.github.tonygnk.cataloglens

import com.google.gson.Gson
import io.github.tonygnk.cataloglens.releases.DevsiteChangelogConverter
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Hits every developer.android.com URL in artifact-map.json and checks the converter can find at
 * least one versioned section. Catches devsite landing pages (no per-version headings) and DOM
 * reworks that [DevsiteChangelogConverterTest]'s fixtures can't — those are handwritten HTML, so
 * they can't detect a mapping pointing at the wrong live page.
 *
 * Fetches via the `curl` binary rather than [java.net.http.HttpClient]: devsite's bot-detection
 * WAF 429s the JDK HttpClient's TLS/HTTP fingerprint even with a browser User-Agent, while curl
 * (and the plugin's own IntelliJ `HttpRequests`-based fetch) sail through unaffected.
 *
 * Opt-in only (real network calls, slow, and devsite's own flakiness would make CI red for
 * reasons unrelated to this repo): run with `CATALOGLENS_LIVE_CHECK=true`.
 */
class AndroidxDevsiteLiveCheckTest {

    private class MapData(
        val artifacts: Map<String, List<String>> = emptyMap(),
        val groupPrefixes: Map<String, List<String>> = emptyMap(),
    )

    @Test
    fun everyDevsiteUrlYieldsVersionedSections() {
        assumeTrue(
            "opt-in only: rerun with CATALOGLENS_LIVE_CHECK=true",
            System.getenv("CATALOGLENS_LIVE_CHECK") == "true",
        )

        val stream = javaClass.getResourceAsStream("/cataloglens/artifact-map.json")
        val data = stream!!.use { Gson().fromJson(InputStreamReader(it, Charsets.UTF_8), MapData::class.java) }

        val urls = (data.artifacts.values + data.groupPrefixes.values)
            .flatten()
            .filter { it.startsWith("https://developer.android.com/jetpack/androidx/releases/") }
            .distinct()
            .sorted()

        val failures = mutableListOf<String>()
        for ((index, url) in urls.withIndex()) {
            if (index > 0) Thread.sleep(500)
            val outcome = check(url)
            if (outcome != null) failures += "$url -> $outcome"
        }

        assertTrue(
            "${failures.size}/${urls.size} devsite URL(s) did not resolve to a versioned changelog:\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /** Returns a failure description, or null if [url] resolves to at least one versioned section. */
    private fun check(url: String): String? {
        val tmpFile = Files.createTempFile("cataloglens-livecheck-", ".html")
        try {
            val process = ProcessBuilder(
                "curl", "-sS", "-L", "--max-time", "20",
                "-A", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "-w", "%{http_code}",
                "-o", tmpFile.toString(),
                url,
            ).redirectErrorStream(true).start()

            val stdout = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(25, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "curl timed out"
            }
            if (process.exitValue() != 0) {
                return "curl failed (exit ${process.exitValue()}): $stdout"
            }

            val statusCode = stdout.trim().takeLast(3).toIntOrNull()
            if (statusCode != 200) {
                return "HTTP $statusCode"
            }

            val html = Files.readString(tmpFile)
            val sections = DevsiteChangelogConverter.toSections(html, url)
            if (sections.isEmpty()) {
                return "no article body / no headings found"
            }
            if (sections.none { it.version != null }) {
                return "page has no \"Version X\" headings — likely a landing/index page, not a changelog"
            }
            return null
        } finally {
            Files.deleteIfExists(tmpFile)
        }
    }
}
