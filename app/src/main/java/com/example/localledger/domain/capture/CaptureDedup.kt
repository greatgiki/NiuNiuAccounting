package com.example.localledger.domain.capture

import kotlin.math.abs

enum class SightPlace { DRAFT, TRANSACTION }

data class MoneySighting(
    val amountMinor: Long,
    val at: Long,
    val place: SightPlace,
    val draftId: String? = null,
)

/**
 * Same amount inside about a minute is a prompt, not a second silent record.
 * The window is inclusive.
 */
object CaptureDedup {
    const val WINDOW_MILLIS = 60_000L

    fun match(amountMinor: Long, now: Long, sightings: List<MoneySighting>): MoneySighting? {
        if (amountMinor <= 0L) return null
        return sightings
            .asSequence()
            .filter { sighting -> sighting.amountMinor == amountMinor && abs(now - sighting.at) <= WINDOW_MILLIS }
            .minByOrNull { sighting -> abs(now - sighting.at) }
    }
}
