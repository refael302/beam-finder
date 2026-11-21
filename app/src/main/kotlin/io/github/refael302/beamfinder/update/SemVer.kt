package io.github.refael302.beamfinder.update

/** Minimal semantic version: MAJOR.MINOR.PATCH[-prerelease]. A leading "v" is accepted. */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        // A release is greater than any pre-release of the same version.
        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    override fun toString(): String =
        "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val regex = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(text: String): SemVer? {
            val m = regex.matchEntire(text.trim()) ?: return null
            val (maj, min, pat, pre) = m.destructured
            return SemVer(
                maj.toIntOrNull() ?: return null,
                min.toIntOrNull() ?: return null,
                pat.toIntOrNull() ?: return null,
                pre.ifEmpty { null },
            )
        }

        private fun comparePreRelease(a: String, b: String): Int {
            val pa = a.split('.')
            val pb = b.split('.')
            for (i in 0 until minOf(pa.size, pb.size)) {
                val x = pa[i]
                val y = pb[i]
                val nx = x.toIntOrNull()
                val ny = y.toIntOrNull()
                val c = when {
                    nx != null && ny != null -> nx.compareTo(ny)
                    nx != null -> -1 // numeric identifiers sort before alphanumeric
                    ny != null -> 1
                    else -> x.compareTo(y)
                }
                if (c != 0) return c
            }
            return pa.size.compareTo(pb.size)
        }
    }
}
