package io.github.johndoe6345789.storeforge.catalog

/**
 * Compares version names such as "1.2.10", "v2.0.0-beta.1" or "2024.05.1".
 * Numeric parts compare as numbers; a pre-release sorts before its final release.
 * Returns null when either side has no leading number, since such names can't be ordered.
 */
fun compareVersionNames(a: String, b: String): Int? {
    val left = ParsedVersion.parse(a) ?: return null
    val right = ParsedVersion.parse(b) ?: return null
    return left.compareTo(right)
}

fun isNewerVersion(candidate: String, current: String): Boolean =
    (compareVersionNames(candidate, current) ?: 0) > 0

private data class ParsedVersion(val numbers: List<Long>, val preRelease: List<String>) : Comparable<ParsedVersion> {

    override fun compareTo(other: ParsedVersion): Int {
        for (i in 0 until maxOf(numbers.size, other.numbers.size)) {
            val cmp = numbers.getOrElse(i) { 0 }.compareTo(other.numbers.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return other.preRelease.size.coerceAtMost(1) - preRelease.size.coerceAtMost(1)
        }
        for (i in 0 until maxOf(preRelease.size, other.preRelease.size)) {
            val mine = preRelease.getOrNull(i) ?: return -1
            val theirs = other.preRelease.getOrNull(i) ?: return 1
            val mineNum = mine.toLongOrNull()
            val theirsNum = theirs.toLongOrNull()
            val cmp = when {
                mineNum != null && theirsNum != null -> mineNum.compareTo(theirsNum)
                mineNum != null -> -1
                theirsNum != null -> 1
                else -> mine.compareTo(theirs)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    companion object {
        private val PATTERN = Regex("^[vV]?(\\d+(?:\\.\\d+)*)(?:[-_]?([0-9A-Za-z.-]+))?(?:\\+.*)?$")

        fun parse(text: String): ParsedVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val numbers = match.groupValues[1].split('.').map { it.toLongOrNull() ?: return null }
            val preRelease = match.groupValues[2].split('.').filter { it.isNotEmpty() }
            return ParsedVersion(numbers, preRelease)
        }
    }
}
