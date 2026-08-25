package com.experiment.argus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleStoreTest {

    @Test
    fun normalizeTopic_acceptsBareTopicAndNtfyUrl() {
        assertEquals("drawer-abcd_1234", RoleStore.normalizeTopic(" drawer-abcd_1234 "))
        assertEquals(
            "drawer-abcd_1234",
            RoleStore.normalizeTopic("https://ntfy.sh/drawer-abcd_1234")
        )
    }

    @Test
    fun normalizeTopic_rejectsUnsafeOrTooShortValues() {
        assertNull(RoleStore.normalizeTopic("abc"))
        assertNull(RoleStore.normalizeTopic("topic/with/slashes"))
        assertNull(RoleStore.normalizeTopic("topic with spaces"))
    }

    @Test
    fun generateTopic_usesExpectedSecureTopicShape() {
        val topics = List(100) { RoleStore.generateTopic() }

        assertTrue(topics.all { it.matches(Regex("drawer-[abcdefghjkmnpqrstuvwxyz23456789]{16}")) })
        assertEquals(topics.size, topics.toSet().size)
    }
}
