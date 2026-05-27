package com.robbie.platform.oem.person

import android.os.SystemClock
import kotlinx.coroutines.flow.StateFlow

enum class PersonFrameSource {
    PERSON_API
}

enum class OrionPersonGatewayStatus {
    IDLE,
    STARTING,
    CONNECTING,
    ACTIVE,
    STALE,
    RECONNECTING,
    STOPPED,
    ERROR
}

data class TrackedBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class TrackedPerson(
    val id: Int,
    val associateId: Int,
    val userId: String?,
    val name: String?,
    val distanceMeters: Double,
    val angleDegrees: Int,
    val angleInView: Double,
    val faceAngleX: Double,
    val faceAngleY: Double,
    val withFace: Boolean,
    val withBody: Boolean,
    val isOtherFace: Boolean,
    val isStaff: Boolean,
    val isNewUser: Boolean,
    val isFakeFace: Boolean,
    val live: Boolean,
    val latencyMs: Int,
    val headSpeed: Int,
    val quality: String?,
    val role: String?,
    val roleId: Int,
    val remoteFaceId: String?,
    val remoteWakeupId: String?,
    val remoteRequestId: String?,
    val mouthMoveScore: Double,
    val mouthState: Int,
    val sourceTimestampMs: Long,
    val faceBounds: TrackedBounds?,
    val bodyBounds: TrackedBounds?
)

data class PersonFrame(
    val sequence: Long,
    val producedElapsedMs: Long,
    val producedWallClockMs: Long,
    val sourceTimestampMs: Long,
    val persons: List<TrackedPerson>,
    val focusPersonId: Int?,
    val source: PersonFrameSource,
    val stale: Boolean,
    val refreshReason: String
) {
    fun ageMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        return if (producedElapsedMs <= 0L) Long.MAX_VALUE else nowElapsedMs - producedElapsedMs
    }

    companion object {
        fun empty(reason: String = "init"): PersonFrame {
            return PersonFrame(
                sequence = 0L,
                producedElapsedMs = 0L,
                producedWallClockMs = 0L,
                sourceTimestampMs = 0L,
                persons = emptyList(),
                focusPersonId = null,
                source = PersonFrameSource.PERSON_API,
                stale = true,
                refreshReason = reason
            )
        }
    }
}

data class OrionPersonGatewayHealth(
    val status: OrionPersonGatewayStatus,
    val robotApiConnected: Boolean,
    val listenerRegistered: Boolean,
    val stale: Boolean,
    val latestPersonCount: Int,
    val lastFrameElapsedMs: Long,
    val lastCallbackElapsedMs: Long,
    val lastSuccessfulRefreshElapsedMs: Long,
    val reconnectAttempt: Int,
    val consecutiveErrors: Int,
    val lastErrorMessage: String?,
    val lastUpdateReason: String
) {
    fun frameAgeMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        return if (lastFrameElapsedMs <= 0L) Long.MAX_VALUE else nowElapsedMs - lastFrameElapsedMs
    }

    companion object {
        fun initial(): OrionPersonGatewayHealth {
            return OrionPersonGatewayHealth(
                status = OrionPersonGatewayStatus.IDLE,
                robotApiConnected = false,
                listenerRegistered = false,
                stale = true,
                latestPersonCount = 0,
                lastFrameElapsedMs = 0L,
                lastCallbackElapsedMs = 0L,
                lastSuccessfulRefreshElapsedMs = 0L,
                reconnectAttempt = 0,
                consecutiveErrors = 0,
                lastErrorMessage = null,
                lastUpdateReason = "init"
            )
        }
    }
}

data class OrionPersonGatewayConfig @JvmOverloads constructor(
    val owner: String = "OrionPersonGateway",
    val threadName: String = "OrionPersonGateway",
    val maxDistanceMeters: Double = 5.0,
    val debounceMs: Long = 120L,
    val staleTimeoutMs: Long = 1600L,
    val maintenanceIntervalMs: Long = 500L,
    val staleRefreshIntervalMs: Long = 1200L,
    val listenerRecoverTimeoutMs: Long = 4500L,
    val listenerRecoverCooldownMs: Long = 2500L,
    val listenerRecoverSettleMs: Long = 100L,
    val maxConsecutiveErrorsBeforeRecover: Int = 2
)

interface OrionPersonGateway {
    val personFrames: StateFlow<PersonFrame>
    val healthState: StateFlow<OrionPersonGatewayHealth>
    val latestFrame: PersonFrame

    fun start()
    fun stop()
    fun requestImmediateRefresh(reason: String = "manual")
}
