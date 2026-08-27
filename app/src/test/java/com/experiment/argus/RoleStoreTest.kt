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
    fun normalizeDeviceName_acceptsUsefulNamesAndRejectsUnsafeOnes() {
        assertEquals("S10 Plus", RoleStore.normalizeDeviceName("  S10 Plus  "))
        assertNull(RoleStore.normalizeDeviceName(""))
        assertNull(RoleStore.normalizeDeviceName("phone\nname"))
        assertNull(RoleStore.normalizeDeviceName("x".repeat(41)))
    }

    @Test
    fun deviceMessage_roundTripsIdentityAndBodyWithSeparators() {
        val encoded = DeviceMessage.encode(
            deviceId = "device-1234",
            deviceName = "S10 Plus / Home",
            body = "battery 80% | charging=true"
        )

        assertEquals(
            DeviceMessage.Decoded(
                deviceId = "device-1234",
                deviceName = "S10 Plus / Home",
                body = "battery 80% | charging=true"
            ),
            DeviceMessage.decode(encoded)
        )
        assertNull(DeviceMessage.decode("legacy message"))
        assertEquals(
            DeviceMessage.LEGACY_DEVICE_NAME,
            DeviceMessage.decodeOrLegacy("legacy message").deviceName
        )
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
        assertTrue(!EventTitles.isVisibleInLog(EventTitles.PAUSED))
    }

    @Test
    fun watchdogTiming_warnsOnlyAfterTwoMissedThirtyMinuteHeartbeats() {
        assertEquals(30L, WatchdogTiming.HEARTBEAT_INTERVAL_MINUTES)
        assertEquals(2L, WatchdogTiming.MISSED_HEARTBEATS_BEFORE_OFFLINE)
        assertEquals(60L, WatchdogTiming.OFFLINE_AFTER_MINUTES)
        assertEquals(
            WatchdogTiming.HEARTBEAT_INTERVAL_MS *
                WatchdogTiming.MISSED_HEARTBEATS_BEFORE_OFFLINE,
            WatchdogTiming.OFFLINE_AFTER_MS
        )
    }
}
