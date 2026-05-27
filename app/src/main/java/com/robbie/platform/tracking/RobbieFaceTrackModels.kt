package com.robbie.platform.tracking

import android.os.SystemClock
import com.robbie.platform.oem.person.PersonFrame
import com.robbie.platform.oem.person.TrackedPerson
import kotlinx.coroutines.flow.StateFlow

enum class RobbieFaceTrackState {
    IDLE,
    ACQUIRING,
    TRACKING,
    REACQUIRING
}

data class RobbieFaceTrackConfig @JvmOverloads constructor(
    val owner: String = "RobbieFaceTrackManager",
    val threadName: String = "RobbieFaceTrackManager",
    val managePersonGatewayLifecycle: Boolean = true,
    val oemLostTimeoutMs: Int = 2200,
    val maxFollowDistanceMeters: Double = 3.5,
    val evaluationDebounceMs: Long = 80L,
    val reacquireGraceMs: Long = 1500L,
    val minVisibleTargetFrames: Int = 2,
    val minVisibleTargetStableMs: Long = 240L,
    val followCommandCooldownMs: Long = 900L,
    val switchCooldownMs: Long = 1500L,
    val statusReevaluateDelayMs: Long = 120L,
    val callbackReevaluateDelayMs: Long = 180L,
    val observationRetentionMs: Long = 8000L,
    val observationContinuityGapMs: Long = 700L,
    val selectorDistanceWeight: Int = 120,
    val selectorFaceBonus: Int = 180,
    val selectorBodyBonus: Int = 50,
    val selectorCurrentTargetBonus: Int = 1000,
    val selectorOemFocusBonus: Int = 220,
    val selectorOtherFacePenalty: Int = 120,
    val selectorStabilityFrameWeight: Int = 30,
    val selectorStableMsWeight: Int = 1,
    val selectorStableMsDivisor: Long = 25L,
    val selectorMaxStableMsBonus: Int = 160,
    val selectorSwitchMargin: Int = 180
)

data class TargetSelectionDecision(
    val person: TrackedPerson,
    val score: Int,
    val stableFrames: Int,
    val stableMs: Long,
    val continuityMatch: Boolean,
    val oemFocusMatch: Boolean,
    val reason: String
)

data class RobbieFaceTrackSnapshot(
    val enabled: Boolean,
    val state: RobbieFaceTrackState,
    val robotApiConnected: Boolean,
    val frameSequence: Long,
    val frameStale: Boolean,
    val activeTargetId: Int?,
    val candidateTargetId: Int?,
    val following: Boolean,
    val followRequestId: Int,
    val reacquireDeadlineElapsedMs: Long,
    val lastTargetSeenElapsedMs: Long,
    val lastTargetLostElapsedMs: Long,
    val lastTransitionElapsedMs: Long,
    val lastFollowStartElapsedMs: Long,
    val lastFollowStopElapsedMs: Long,
    val lastDecisionReason: String,
    val lastErrorMessage: String?,
    val lastSelectionScore: Int?
) {
    fun reacquireRemainingMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        if (reacquireDeadlineElapsedMs <= 0L) {
            return 0L
        }
        return (reacquireDeadlineElapsedMs - nowElapsedMs).coerceAtLeast(0L)
    }

    companion object {
        fun initial(): RobbieFaceTrackSnapshot {
            return RobbieFaceTrackSnapshot(
                enabled = false,
                state = RobbieFaceTrackState.IDLE,
                robotApiConnected = false,
                frameSequence = 0L,
                frameStale = true,
                activeTargetId = null,
                candidateTargetId = null,
                following = false,
                followRequestId = -1,
                reacquireDeadlineElapsedMs = 0L,
                lastTargetSeenElapsedMs = 0L,
                lastTargetLostElapsedMs = 0L,
                lastTransitionElapsedMs = 0L,
                lastFollowStartElapsedMs = 0L,
                lastFollowStopElapsedMs = 0L,
                lastDecisionReason = "init",
                lastErrorMessage = null,
                lastSelectionScore = null
            )
        }
    }
}

data class RobbieFaceTrackMetrics(
    val targetSwitchCount: Long,
    val reacquireCount: Long,
    val followStartAttemptCount: Long,
    val followStartSuccessCount: Long,
    val lostTargetCount: Long,
    val totalTrackingDurationMs: Long,
    val currentTrackingDurationMs: Long,
    val lastTrackingStartedElapsedMs: Long,
    val lastTrackingStoppedElapsedMs: Long
) {
    companion object {
        fun initial(): RobbieFaceTrackMetrics {
            return RobbieFaceTrackMetrics(
                targetSwitchCount = 0L,
                reacquireCount = 0L,
                followStartAttemptCount = 0L,
                followStartSuccessCount = 0L,
                lostTargetCount = 0L,
                totalTrackingDurationMs = 0L,
                currentTrackingDurationMs = 0L,
                lastTrackingStartedElapsedMs = 0L,
                lastTrackingStoppedElapsedMs = 0L
            )
        }
    }
}

data class RobbieFaceTrackDiagnostics(
    val snapshot: RobbieFaceTrackSnapshot,
    val metrics: RobbieFaceTrackMetrics,
    val latestFrame: PersonFrame,
    val lastSelection: TargetSelectionDecision?,
    val lastFollowStatusCode: Int?,
    val lastFollowStatus: String?,
    val lastCallbackEvent: String?
) {
    companion object {
        fun initial(): RobbieFaceTrackDiagnostics {
            return RobbieFaceTrackDiagnostics(
                snapshot = RobbieFaceTrackSnapshot.initial(),
                metrics = RobbieFaceTrackMetrics.initial(),
                latestFrame = PersonFrame.empty(),
                lastSelection = null,
                lastFollowStatusCode = null,
                lastFollowStatus = null,
                lastCallbackEvent = null
            )
        }
    }
}

fun interface RobbieFaceTrackListener {
    fun onDiagnosticsUpdated(diagnostics: RobbieFaceTrackDiagnostics)
}

interface FaceTrackManager {
    val state: StateFlow<RobbieFaceTrackSnapshot>
    val metrics: StateFlow<RobbieFaceTrackMetrics>
    val diagnostics: StateFlow<RobbieFaceTrackDiagnostics>

    fun currentSnapshot(): RobbieFaceTrackSnapshot
    fun currentMetrics(): RobbieFaceTrackMetrics
    fun currentDiagnostics(): RobbieFaceTrackDiagnostics
    fun addListener(listener: RobbieFaceTrackListener)
    fun removeListener(listener: RobbieFaceTrackListener)
    fun start()
    fun stop()
    fun requestEvaluation(reason: String = "manual")
}
