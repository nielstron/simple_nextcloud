package de.nielstron.simplenextcloud

import de.nielstron.simplenextcloud.data.CloudFile
import de.nielstron.simplenextcloud.data.RecentAction
import de.nielstron.simplenextcloud.data.RecentEntry
import de.nielstron.simplenextcloud.ui.recentSubtitle
import de.nielstron.simplenextcloud.ui.relativeTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val now = ZonedDateTime.of(2026, 11, 17, 15, 0, 0, 0, zone)
    private fun ago(minutes: Long) = now.minusMinutes(minutes).toInstant().toEpochMilli()
    private fun format(at: Long) = relativeTime(at, now.toInstant().toEpochMilli(), zone)

    @Test
    fun `recent times read naturally`() {
        assertEquals("just now", format(ago(0)))
        assertEquals("5 min ago", format(ago(5)))
        assertEquals("2 h ago", format(ago(125)))
        assertEquals("yesterday", format(ago(16 * 60)))
        assertEquals("14 Nov", format(ago(3 * 24 * 60)))
        assertEquals("17 Nov 2025", format(ago(365 * 24 * 60)))
    }

    @Test
    fun `an hour ago before midnight is yesterday, not hours ago`() {
        val earlyMorning = ZonedDateTime.of(2026, 11, 17, 0, 30, 0, 0, zone)
        val lateEvening = earlyMorning.minusHours(1).toInstant().toEpochMilli()

        assertEquals("yesterday", relativeTime(lateEvening, earlyMorning.toInstant().toEpochMilli(), zone))
    }

    @Test
    fun `subtitles say where and how an entry was used`() {
        val folder = CloudFile("Taxes", "Documents/Taxes", true, 0, null, null, null)
        val file = CloudFile("Plan.pdf", "Plan.pdf", false, 1, "application/pdf", null, null)
        val nowMillis = now.toInstant().toEpochMilli()

        assertEquals(
            "Documents · uploaded here 5 min ago",
            recentSubtitle(RecentEntry(folder, RecentAction.UPLOADED, ago(5)), nowMillis, zone),
        )
        assertEquals(
            "All files · opened just now",
            recentSubtitle(RecentEntry(file, RecentAction.OPENED, ago(0)), nowMillis, zone),
        )
    }
}
