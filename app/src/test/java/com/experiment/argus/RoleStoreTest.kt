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

    @Test
    fun eventTitles_recognizeCurrentAndLegacyPowerEvents() {
        assertTrue(EventTitles.isPower(EventTitles.POWER_LOST))
        assertTrue(EventTitles.isPower(EventTitles.POWER_BACK))
        assertTrue(EventTitles.isPower("[Power LOST]"))
        assertTrue(EventTitles.isReboot(EventTitles.REBOOTED))
        assertTrue(EventTitles.isReboot("[Rebooted]"))
        assertTrue(EventTitles.isVisibleInLog(EventTitles.OFFLINE))
        assertTrue(!EventTitles.isVisibleInLog(EventTitles.HEARTBEAT))
    }

    @Test
    fun watchdogTiming_usesHourlyHeartbeatWithThirtyMinuteGracePeriod() {
        assertEquals(60L, WatchdogTiming.HEARTBEAT_INTERVAL_MINUTES)
        assertEquals(90L, WatchdogTiming.OFFLINE_AFTER_MINUTES)
        assertEquals(
            30L * 60_000L,
            WatchdogTiming.OFFLINE_AFTER_MS - WatchdogTiming.HEARTBEAT_INTERVAL_MS
        )
    }
}
