package io.github.tonygnk.cataloglens.releases

/**
 * Best-effort version extraction/matching. Tags and headings come in many shapes
 * (`v1.2.3`, `core-1.2.3`, `compose-bom-2024.01.00`, `Version 2.8.4`); we pull the trailing
 * semver-ish token and compare those. The extracted token is also what gets written to the catalog,
 * so a raw tag like `v1.2.3` pins as a clean `1.2.3`.
 */
object VersionMatcher {

    private val VERSION_TOKEN = Regex("""\d+(?:\.\d+)+(?:[-.+][0-9A-Za-z.-]+)?""")

    fun extractPinnable(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return VERSION_TOKEN.findAll(raw).lastOrNull()?.value
    }

    fun matches(current: String?, raw: String?): Boolean {
        val a = extractPinnable(current) ?: return false
        val b = extractPinnable(raw) ?: return false
        return a == b
    }
}
