package io.github.refael302.beamfinder.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test
    fun parsesWithAndWithoutPrefix() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer(1, 2, 3, "beta.1"), SemVer.parse("1.2.3-beta.1"))
        assertNull(SemVer.parse("1.2"))
        assertNull(SemVer.parse("abc"))
    }

    @Test
    fun ordersVersions() {
        assertTrue(SemVer.parse("0.2.0")!! > SemVer.parse("0.1.9")!!)
        assertTrue(SemVer.parse("1.0.0")!! > SemVer.parse("1.0.0-rc.1")!!)
        assertTrue(SemVer.parse("1.0.0-rc.2")!! > SemVer.parse("1.0.0-rc.1")!!)
        assertTrue(SemVer.parse("1.0.0-alpha")!! < SemVer.parse("1.0.0-beta")!!)
        assertNotNull(SemVer.parse("1.0.0+build5"))
    }
}
