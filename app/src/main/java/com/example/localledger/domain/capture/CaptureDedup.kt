package com.example.localledger.domain.capture

import com.example.localledger.domain.importing.BillSource
import com.example.localledger.domain.model.TransactionType
import kotlin.math.abs

enum class SightPlace { DRAFT, TRANSACTION }

data class MoneySighting(
    val amountMinor: Long,
    val at: Long,
    val place: SightPlace,
    val draftId: String? = null,
    val direction: TransactionType = TransactionType.EXPENSE,
    val transactionId: String? = null,
)

data class FillCandidate(
    val id: String,
    val direction: TransactionType,
    val at: Long,
    val source: BillSource? = null,
    val merchant: String = "",
)

/**
 * Same amount and direction inside about three minutes is one payment.
 * Another source fills a pending draft or prompts, instead of writing a second row.
 * The window is inclusive.
 */
object CaptureDedup {
    const val WINDOW_MILLIS = 3 * 60 * 1000L

    fun match(
        amountMinor: Long,
        direction: TransactionType,
        now: Long,
        sightings: List<MoneySighting>,
    ): MoneySighting? {
        if (amountMinor <= 0L) return null
        return sightings
            .asSequence()
            .filter { sighting ->
                sighting.amountMinor == amountMinor &&
                    sighting.direction == direction &&
                    abs(now - sighting.at) <= WINDOW_MILLIS
            }
            .minByOrNull { sighting -> abs(now - sighting.at) }
    }

    /**
     * Pending draft whose amount is still missing. Same direction and inside the window.
     * An explicit source wins when it is the only one. Several unnamed drafts are left alone.
     */
    fun fillTarget(
        direction: TransactionType,
        at: Long,
        candidates: List<FillCandidate>,
        source: BillSource? = null,
        alsoAt: Long? = null,
        merchant: String = "",
    ): FillCandidate? {
        val grouped = candidates
            .filter { candidate -> candidate.direction == direction && near(candidate.at, at, alsoAt) }
            .groupBy { candidate -> candidate.id }
        if (grouped.isEmpty()) return null
        val distinct = grouped.map { (_, rows) -> rows.minBy { row -> distance(row.at, at, alsoAt) } }
        val sameSource = if (source == null) emptyList() else distinct.filter { candidate -> candidate.source == source }
        val pool = when {
            sameSource.size == 1 -> sameSource
            sameSource.size > 1 -> uniqueMerchant(sameSource, merchant)
            distinct.size == 1 -> distinct
            else -> uniqueMerchant(distinct, merchant)
        }
        return pool.minByOrNull { candidate -> distance(candidate.at, at, alsoAt) }
    }

    private fun uniqueMerchant(rows: List<FillCandidate>, merchant: String): List<FillCandidate> {
        val hits = rows.filter { candidate -> merchantsMatch(candidate.merchant, merchant) }
        return if (hits.size == 1) hits else emptyList()
    }

    private fun near(candidateAt: Long, at: Long, alsoAt: Long?): Boolean {
        if (abs(candidateAt - at) <= WINDOW_MILLIS) return true
        return alsoAt != null && abs(candidateAt - alsoAt) <= WINDOW_MILLIS
    }

    private fun distance(candidateAt: Long, at: Long, alsoAt: Long?): Long {
        val primary = abs(candidateAt - at)
        val secondary = if (alsoAt == null) Long.MAX_VALUE else abs(candidateAt - alsoAt)
        return minOf(primary, secondary)
    }

    private fun merchantsMatch(left: String, right: String): Boolean {
        val a = left.trim()
        val b = right.trim()
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b || a.contains(b) || b.contains(a)
    }
}
