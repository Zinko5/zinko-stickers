package com.stickerforge.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CobaltInstanceProviderTest {

    @Test
    fun testDefaultInstancesNotEmpty() {
        val instances = CobaltInstanceProvider.getWorkingInstances()
        assertNotNull(instances)
        assertTrue(instances.isNotEmpty())
        assertTrue(instances.any { it.contains("cobalt") })
    }
}
