package io.github.johndoe6345789.storeforge

import io.github.johndoe6345789.storeforge.catalog.compareVersionNames
import io.github.johndoe6345789.storeforge.catalog.isNewerVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun numericPartsCompareAsNumbers() {
        assertTrue(isNewerVersion("1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("2.0", "1.99.99"))
        assertEquals(0, compareVersionNames("1.2", "1.2.0"))
    }

    @Test
    fun leadingVIsIgnored() {
        assertEquals(0, compareVersionNames("v1.2.3", "1.2.3"))
    }

    @Test
    fun preReleaseSortsBeforeRelease() {
        assertTrue(isNewerVersion("1.0.0", "1.0.0-beta.2"))
        assertTrue(isNewerVersion("1.0.0-beta.10", "1.0.0-beta.2"))
        assertTrue(isNewerVersion("1.0.0-rc.1", "1.0.0-beta.9"))
        assertFalse(isNewerVersion("1.0.0-beta", "1.0.0"))
    }

    @Test
    fun unorderableNamesAreNotUpdates() {
        assertNull(compareVersionNames("nightly", "1.0"))
        assertFalse(isNewerVersion("nightly", "1.0"))
    }
}
