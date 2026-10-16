package com.example.localledger.domain.importing

import com.example.localledger.domain.model.TransactionType
import kotlin.math.abs

object ImportMatcher {
    const val WINDOW_MILLIS = 10 * 60 * 1000L

    fun match(
        source: BillSource,
        externalId: String,
        type: TransactionType,
        amountMinor: Long,
        transactionAt: Long,
        knownExternalIds: Set<ExternalKey>,
        candidates: List<MatchCandidate>,
        windowMillis: Long = WINDOW_MILLIS,
    ): MatchResult {
        val key = externalId.trim()
        if (key.isNotEmpty() && ExternalKey(source.name, key) in knownExternalIds) {
            return MatchResult(DuplicateFlag.ALREADY_IMPORTED, null)
        }
        val best = candidates.mapNotNull { candidate ->
            if (candidate.type != type || candidate.amountMinor != amountMinor) return@mapNotNull null
            val delta = abs(candidate.transactionAt - transactionAt)
            if (delta > windowMillis) return@mapNotNull null
            if (key.isNotEmpty() && candidate.externalId == key && candidate.externalSource == source.name) {
                return@mapNotNull null
            }
            candidate to delta
        }.minWithOrNull(compareBy<Pair<MatchCandidate, Long>> { it.second }.thenBy { preference(it.first) })
        return if (best == null) {
            MatchResult(DuplicateFlag.NONE, null)
        } else {
            val (candidate, delta) = best
            MatchResult(
                flag = DuplicateFlag.LIKELY_DUPLICATE,
                hit = FuzzyHit(
                    candidateId = candidate.id,
                    kind = candidate.kind,
                    origin = candidate.origin,
                    timeDeltaMillis = delta,
                ),
            )
        }
    }

    private fun preference(candidate: MatchCandidate): Int = when {
        candidate.kind == CandidateKind.DRAFT -> 0
        candidate.origin == RecordOrigin.NOTIFICATION -> 1
        else -> 2
    }
}
