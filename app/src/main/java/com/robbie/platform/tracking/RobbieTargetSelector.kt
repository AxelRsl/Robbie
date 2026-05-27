package com.robbie.platform.tracking

import android.os.SystemClock
import com.robbie.platform.oem.person.PersonFrame
import com.robbie.platform.oem.person.TrackedPerson
import kotlin.math.abs
import kotlin.math.roundToInt

internal class RobbieTargetSelector(
    private val config: RobbieFaceTrackConfig
) {

    private data class Observation(
        val personId: Int,
        var firstSeenElapsedMs: Long,
        var lastSeenElapsedMs: Long,
        var streakStartedElapsedMs: Long,
        var consecutiveFrames: Int
    )

    private val observations = linkedMapOf<Int, Observation>()

    fun onFrame(frame: PersonFrame, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        val seenIds = HashSet<Int>(frame.persons.size)
        frame.persons.forEach { person ->
            seenIds.add(person.id)
            val observation = observations[person.id]
            if (observation == null) {
                observations[person.id] = Observation(
                    personId = person.id,
                    firstSeenElapsedMs = nowElapsedMs,
                    lastSeenElapsedMs = nowElapsedMs,
                    streakStartedElapsedMs = nowElapsedMs,
                    consecutiveFrames = 1
                )
            } else {
                val continuous = nowElapsedMs - observation.lastSeenElapsedMs <= config.observationContinuityGapMs
                observation.lastSeenElapsedMs = nowElapsedMs
                if (continuous) {
                    observation.consecutiveFrames += 1
                } else {
                    observation.streakStartedElapsedMs = nowElapsedMs
                    observation.consecutiveFrames = 1
                }
            }
        }
        val iterator = observations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val observation = entry.value
            if (!seenIds.contains(entry.key) && nowElapsedMs - observation.lastSeenElapsedMs > config.observationRetentionMs) {
                iterator.remove()
            }
        }
    }

    fun select(
        frame: PersonFrame,
        activeTargetId: Int?,
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ): TargetSelectionDecision? {
        if (frame.persons.isEmpty()) {
            return null
        }
        val scored = frame.persons.map { candidate ->
            val observation = observations[candidate.id]
            val stableFrames = observation?.consecutiveFrames ?: 1
            val stableMs = if (observation == null) 0L else (nowElapsedMs - observation.streakStartedElapsedMs).coerceAtLeast(0L)
            val continuityMatch = activeTargetId != null && candidate.id == activeTargetId
            val oemFocusMatch = frame.focusPersonId != null && candidate.id == frame.focusPersonId
            val decision = TargetSelectionDecision(
                person = candidate,
                score = computeScore(candidate, stableFrames, stableMs, continuityMatch, oemFocusMatch),
                stableFrames = stableFrames,
                stableMs = stableMs,
                continuityMatch = continuityMatch,
                oemFocusMatch = oemFocusMatch,
                reason = buildReason(candidate, stableFrames, stableMs, continuityMatch, oemFocusMatch)
            )
            decision
        }.sortedWith(
            compareByDescending<TargetSelectionDecision> { it.score }
                .thenByDescending { it.stableFrames }
                .thenByDescending { it.stableMs }
                .thenBy { it.person.distanceMeters }
                .thenBy { abs(it.person.faceAngleX) }
        )

        val best = scored.firstOrNull() ?: return null
        val current = activeTargetId?.let { id -> scored.firstOrNull { it.person.id == id } }
        if (current != null && best.person.id != current.person.id) {
            val strongEnoughToSwitch = best.score >= current.score + config.selectorSwitchMargin
            if (!strongEnoughToSwitch) {
                return current.copy(reason = "continuity_hold:${current.reason}")
            }
        }
        return best
    }

    fun isStableEnough(personId: Int, nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        val observation = observations[personId] ?: return false
        val stableMs = (nowElapsedMs - observation.streakStartedElapsedMs).coerceAtLeast(0L)
        return observation.consecutiveFrames >= config.minVisibleTargetFrames || stableMs >= config.minVisibleTargetStableMs
    }

    private fun computeScore(
        person: TrackedPerson,
        stableFrames: Int,
        stableMs: Long,
        continuityMatch: Boolean,
        oemFocusMatch: Boolean
    ): Int {
        var score = 0
        val boundedDistance = person.distanceMeters.coerceIn(0.0, config.maxFollowDistanceMeters)
        val distanceBonus = ((config.maxFollowDistanceMeters - boundedDistance) * config.selectorDistanceWeight).roundToInt()
        score += distanceBonus
        if (person.withFace) {
            score += config.selectorFaceBonus
        }
        if (person.withBody) {
            score += config.selectorBodyBonus
        }
        if (person.isOtherFace) {
            score -= config.selectorOtherFacePenalty
        }
        if (continuityMatch) {
            score += config.selectorCurrentTargetBonus
        }
        if (oemFocusMatch) {
            score += config.selectorOemFocusBonus
        }
        score += stableFrames * config.selectorStabilityFrameWeight
        val stableMsBonus = (stableMs / config.selectorStableMsDivisor).toInt().coerceAtMost(config.selectorMaxStableMsBonus)
        score += stableMsBonus * config.selectorStableMsWeight
        return score
    }

    private fun buildReason(
        person: TrackedPerson,
        stableFrames: Int,
        stableMs: Long,
        continuityMatch: Boolean,
        oemFocusMatch: Boolean
    ): String {
        return buildString {
            append("id=")
            append(person.id)
            append(",face=")
            append(person.withFace)
            append(",body=")
            append(person.withBody)
            append(",stableFrames=")
            append(stableFrames)
            append(",stableMs=")
            append(stableMs)
            append(",focus=")
            append(oemFocusMatch)
            append(",continuity=")
            append(continuityMatch)
            append(",distance=")
            append(person.distanceMeters)
        }
    }
}
