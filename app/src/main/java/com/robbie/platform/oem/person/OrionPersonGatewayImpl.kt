package com.robbie.platform.oem.person

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.ainirobot.coreservice.client.RobotApi
import com.ainirobot.coreservice.client.listener.Person
import com.ainirobot.coreservice.client.person.PersonApi
import com.ainirobot.coreservice.client.person.PersonListener
import com.robbie.platform.robot.RobotApiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.android.asCoroutineDispatcher
import java.util.concurrent.atomic.AtomicBoolean

class OrionPersonGatewayImpl(
    context: Context,
    private val robotApiService: RobotApiService = RobotApiService.getInstance(),
    private val config: OrionPersonGatewayConfig = OrionPersonGatewayConfig()
) : OrionPersonGateway {

    companion object {
        private const val TAG = "OrionPersonGateway"
        private const val TAG_DIAG = "OrionPersonDiag"
    }

    private val appContext = context.applicationContext
    private val started = AtomicBoolean(false)
    private val handlerThread = HandlerThread(config.threadName).apply { start() }
    private val workerHandler = Handler(handlerThread.looper)
    private val dispatcher: CoroutineDispatcher = workerHandler.asCoroutineDispatcher(config.threadName)
    private val internalPersonApi = PersonApi.getInstance()
    private var gatewayJob: Job? = null
    private var gatewayScope: CoroutineScope? = null

    private val _personFrames = MutableStateFlow(PersonFrame.empty())
    override val personFrames: StateFlow<PersonFrame> = _personFrames.asStateFlow()

    private val _healthState = MutableStateFlow(OrionPersonGatewayHealth.initial())
    override val healthState: StateFlow<OrionPersonGatewayHealth> = _healthState.asStateFlow()

    override val latestFrame: PersonFrame
        get() = _personFrames.value

    private var sequence = 0L
    private var listenerRegistered = false
    private var robotApiConnected = false
    private var lastCallbackElapsedMs = 0L
    private var lastRefreshElapsedMs = 0L
    private var lastRecoverElapsedMs = 0L
    private var lastEmittedElapsedMs = 0L
    private var reconnectAttempt = 0
    private var consecutiveErrors = 0
    private var stopped = false
    private var pendingRefreshRunnable: Runnable? = null

    private val maintenanceRunnable = object : Runnable {
        override fun run() {
            if (stopped) {
                return
            }
            runMaintenanceTick()
            workerHandler.postDelayed(this, config.maintenanceIntervalMs)
        }
    }

    private val personListener = object : PersonListener() {
        override fun personChanged() {
            lastCallbackElapsedMs = SystemClock.elapsedRealtime()
            scheduleRefresh("listener_callback", debounce = true)
        }
    }

    private val robotConnectionListener = object : RobotApiService.ConnectionListener {
        override fun onRobotApiConnected(robotApi: RobotApi) {
            workerHandler.post {
                robotApiConnected = true
                reconnectAttempt = 0
                logInfo("robotApi connected")
                updateHealth(
                    status = if (listenerRegistered) OrionPersonGatewayStatus.ACTIVE else OrionPersonGatewayStatus.CONNECTING,
                    reason = "robot_api_connected",
                    clearError = true
                )
                ensureListenerRegistered("robot_api_connected")
                scheduleRefresh("robot_api_connected", debounce = false)
            }
        }

        override fun onRobotApiDisconnected() {
            workerHandler.post {
                robotApiConnected = false
                listenerRegistered = false
                logWarn("robotApi disconnected")
                updateHealth(
                    status = OrionPersonGatewayStatus.RECONNECTING,
                    reason = "robot_api_disconnected"
                )
                markFrameStale("robot_api_disconnected")
            }
        }

        override fun onRobotApiDisabled() {
            workerHandler.post {
                robotApiConnected = false
                listenerRegistered = false
                logWarn("robotApi disabled")
                updateHealth(
                    status = OrionPersonGatewayStatus.ERROR,
                    reason = "robot_api_disabled",
                    lastErrorMessage = "RobotApi disabled"
                )
                markFrameStale("robot_api_disabled")
            }
        }
    }

    override fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        workerHandler.post {
            stopped = false
            if (gatewayJob?.isCancelled != false) {
                gatewayJob = SupervisorJob()
                gatewayScope = CoroutineScope(gatewayJob!! + dispatcher)
            }
            logInfo("start owner=${config.owner}")
            updateHealth(status = OrionPersonGatewayStatus.STARTING, reason = "start")
            robotApiService.retain(config.owner, appContext)
            robotApiService.addConnectionListener(robotConnectionListener)
            robotApiConnected = robotApiService.isConnected
            if (robotApiConnected) {
                updateHealth(status = OrionPersonGatewayStatus.CONNECTING, reason = "start_connected")
                ensureListenerRegistered("start_connected")
                scheduleRefresh("start_connected", debounce = false)
            } else {
                updateHealth(status = OrionPersonGatewayStatus.CONNECTING, reason = "start_wait_robot_api")
                robotApiService.connect(appContext, null)
            }
            workerHandler.removeCallbacks(maintenanceRunnable)
            workerHandler.postDelayed(maintenanceRunnable, config.maintenanceIntervalMs)
        }
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) {
            return
        }
        workerHandler.post {
            stopped = true
            pendingRefreshRunnable?.let(workerHandler::removeCallbacks)
            pendingRefreshRunnable = null
            workerHandler.removeCallbacksAndMessages(null)
            unregisterListener("stop")
            robotApiService.removeConnectionListener(robotConnectionListener)
            robotApiService.release(config.owner)
            markFrameStale("stop")
            updateHealth(status = OrionPersonGatewayStatus.STOPPED, reason = "stop", clearError = true)
            gatewayJob?.cancel()
            gatewayJob = null
            gatewayScope = null
            logInfo("stopped")
        }
    }

    override fun requestImmediateRefresh(reason: String) {
        if (!started.get()) {
            return
        }
        scheduleRefresh(reason, debounce = false)
    }

    private fun scheduleRefresh(reason: String, debounce: Boolean) {
        if (stopped) {
            return
        }
        val runnable = Runnable { refreshFrame(reason) }
        pendingRefreshRunnable?.let(workerHandler::removeCallbacks)
        pendingRefreshRunnable = runnable
        if (debounce && config.debounceMs > 0L) {
            workerHandler.postDelayed(runnable, config.debounceMs)
        } else {
            workerHandler.post(runnable)
        }
    }

    private fun refreshFrame(reason: String) {
        pendingRefreshRunnable = null
        if (stopped) {
            return
        }
        if (!robotApiConnected) {
            updateHealth(status = OrionPersonGatewayStatus.RECONNECTING, reason = "refresh_without_robot_api")
            markFrameStale("refresh_without_robot_api")
            return
        }
        ensureListenerRegistered("refresh:$reason")
        val scope = gatewayScope ?: return
        scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                val completeFaces = safeGetPersons { internalPersonApi.getCompleteFaceList(config.maxDistanceMeters) }
                val faces = if (completeFaces.isNullOrEmpty()) {
                    safeGetPersons { internalPersonApi.getAllFaceList(config.maxDistanceMeters) }
                } else {
                    completeFaces
                }
                val focusPerson = safeGetFocusPerson()
                val normalized = normalizePersons(faces ?: emptyList())
                val focusPersonId = focusPerson?.id?.takeIf { focus -> normalized.any { it.id == focus } }
                    ?: normalized.firstOrNull { it.withFace && !it.isOtherFace }?.id
                val nowElapsed = SystemClock.elapsedRealtime()
                val nowWall = System.currentTimeMillis()
                val sourceTimestamp = normalized.maxOfOrNull { it.sourceTimestampMs } ?: 0L
                sequence += 1L
                val frame = PersonFrame(
                    sequence = sequence,
                    producedElapsedMs = nowElapsed,
                    producedWallClockMs = nowWall,
                    sourceTimestampMs = sourceTimestamp,
                    persons = normalized,
                    focusPersonId = focusPersonId,
                    source = PersonFrameSource.PERSON_API,
                    stale = false,
                    refreshReason = reason
                )
                _personFrames.value = frame
                lastRefreshElapsedMs = nowElapsed
                lastEmittedElapsedMs = nowElapsed
                consecutiveErrors = 0
                updateHealth(
                    status = OrionPersonGatewayStatus.ACTIVE,
                    reason = reason,
                    clearError = true
                )
                logFrame(frame, startedAt)
            } catch (t: Throwable) {
                consecutiveErrors += 1
                logError("refresh failed reason=$reason", t)
                updateHealth(
                    status = OrionPersonGatewayStatus.ERROR,
                    reason = reason,
                    lastErrorMessage = t.message
                )
                if (consecutiveErrors >= config.maxConsecutiveErrorsBeforeRecover) {
                    recoverListener("consecutive_errors")
                }
            }
        }
    }

    private fun safeGetPersons(block: () -> List<Person>?): List<Person>? {
        return try {
            block()
        } catch (t: Throwable) {
            consecutiveErrors += 1
            logWarn("person fetch failed: ${t.message}")
            null
        }
    }

    private fun safeGetFocusPerson(): Person? {
        return try {
            internalPersonApi.focusPerson
        } catch (t: Throwable) {
            logWarn("focus person fetch failed: ${t.message}")
            null
        }
    }

    private fun ensureListenerRegistered(reason: String) {
        if (stopped || listenerRegistered || !robotApiConnected) {
            return
        }
        try {
            val success = internalPersonApi.registerPersonListener(personListener)
            listenerRegistered = success
            if (success) {
                lastRecoverElapsedMs = SystemClock.elapsedRealtime()
                logInfo("listener registered reason=$reason")
                updateHealth(
                    status = OrionPersonGatewayStatus.CONNECTING,
                    reason = "listener_registered:$reason",
                    clearError = true
                )
            } else {
                reconnectAttempt += 1
                logWarn("listener register failed reason=$reason")
                updateHealth(
                    status = OrionPersonGatewayStatus.RECONNECTING,
                    reason = "listener_register_failed:$reason",
                    lastErrorMessage = "registerPersonListener returned false"
                )
            }
        } catch (t: Throwable) {
            reconnectAttempt += 1
            listenerRegistered = false
            logError("listener register exception reason=$reason", t)
            updateHealth(
                status = OrionPersonGatewayStatus.ERROR,
                reason = "listener_register_exception:$reason",
                lastErrorMessage = t.message
            )
        }
    }

    private fun unregisterListener(reason: String) {
        if (!listenerRegistered) {
            return
        }
        try {
            internalPersonApi.unregisterPersonListener(personListener)
            logInfo("listener unregistered reason=$reason")
        } catch (t: Throwable) {
            logWarn("listener unregister failed reason=$reason error=${t.message}")
        } finally {
            listenerRegistered = false
        }
    }

    private fun recoverListener(reason: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoverElapsedMs < config.listenerRecoverCooldownMs) {
            return
        }
        reconnectAttempt += 1
        lastRecoverElapsedMs = now
        logWarn("recover listener reason=$reason attempt=$reconnectAttempt")
        unregisterListener("recover:$reason")
        updateHealth(
            status = OrionPersonGatewayStatus.RECONNECTING,
            reason = "recover:$reason"
        )
        workerHandler.postDelayed(
            { ensureListenerRegistered("recover:$reason") },
            config.listenerRecoverSettleMs
        )
        workerHandler.postDelayed(
            { scheduleRefresh("recover_refresh:$reason", debounce = false) },
            config.listenerRecoverSettleMs + 50L
        )
    }

    private fun runMaintenanceTick() {
        val now = SystemClock.elapsedRealtime()
        val health = _healthState.value
        val frame = _personFrames.value
        val frameAgeMs = frame.ageMs(now)
        if (frame.producedElapsedMs > 0L && frameAgeMs >= config.staleTimeoutMs && !frame.stale) {
            markFrameStale("maintenance_stale")
            updateHealth(
                status = OrionPersonGatewayStatus.STALE,
                reason = "maintenance_stale"
            )
        }
        if (robotApiConnected && listenerRegistered && lastCallbackElapsedMs > 0L && now - lastCallbackElapsedMs > config.listenerRecoverTimeoutMs) {
            recoverListener("listener_silence")
        } else if (robotApiConnected && !listenerRegistered) {
            ensureListenerRegistered("maintenance_missing_listener")
        }
        if (robotApiConnected && health.stale && now - lastRefreshElapsedMs >= config.staleRefreshIntervalMs) {
            scheduleRefresh("stale_refresh", debounce = false)
        }
    }

    private fun markFrameStale(reason: String) {
        val current = _personFrames.value
        if (current.stale && current.refreshReason == reason) {
            return
        }
        _personFrames.value = current.copy(stale = true, refreshReason = reason)
    }

    private fun normalizePersons(persons: List<Person>): List<TrackedPerson> {
        if (persons.isEmpty()) {
            return emptyList()
        }
        return persons.map { person ->
            TrackedPerson(
                id = person.id,
                associateId = readAssociateId(person),
                userId = person.userId,
                name = person.name,
                distanceMeters = person.distance,
                angleDegrees = person.angle,
                angleInView = person.angleInView,
                faceAngleX = person.faceAngleX,
                faceAngleY = person.faceAngleY,
                withFace = person.isWithFace,
                withBody = person.isWithBody,
                isOtherFace = person.isOtherFace,
                isStaff = person.isStaff,
                isNewUser = person.isNewUser,
                isFakeFace = person.isFakeFace,
                live = person.isLiveNess,
                latencyMs = person.latency,
                headSpeed = person.headSpeed,
                quality = person.quality,
                role = person.role,
                roleId = person.roleId,
                remoteFaceId = person.remoteFaceId,
                remoteWakeupId = person.remoteWakeupId,
                remoteRequestId = person.remoteReqId,
                mouthMoveScore = person.mouthMoveScore,
                mouthState = person.mouthState,
                sourceTimestampMs = person.timestamp,
                faceBounds = if (person.faceWidth > 0 && person.faceHeight > 0) {
                    TrackedBounds(person.faceX, person.faceY, person.faceWidth, person.faceHeight)
                } else {
                    null
                },
                bodyBounds = if (person.bodyWidth > 0 && person.bodyHeight > 0) {
                    TrackedBounds(person.bodyX, person.bodyY, person.bodyWidth, person.bodyHeight)
                } else {
                    null
                }
            )
        }.sortedWith(
            compareBy<TrackedPerson> { !it.withFace }
                .thenBy { it.isOtherFace }
                .thenBy { it.distanceMeters }
                .thenBy { kotlin.math.abs(it.faceAngleX) }
        )
    }

    private fun readAssociateId(person: Person): Int {
        return try {
            val method = person.javaClass.methods.firstOrNull { it.name == "getAssociateId" && it.parameterTypes.isEmpty() }
            val value = method?.invoke(person)
            (value as? Number)?.toInt() ?: person.id
        } catch (t: Throwable) {
            person.id
        }
    }

    private fun updateHealth(
        status: OrionPersonGatewayStatus,
        reason: String,
        lastErrorMessage: String? = _healthState.value.lastErrorMessage,
        clearError: Boolean = false
    ) {
        val currentFrame = _personFrames.value
        _healthState.value = _healthState.value.copy(
            status = status,
            robotApiConnected = robotApiConnected,
            listenerRegistered = listenerRegistered,
            stale = currentFrame.stale,
            latestPersonCount = currentFrame.persons.size,
            lastFrameElapsedMs = currentFrame.producedElapsedMs,
            lastCallbackElapsedMs = lastCallbackElapsedMs,
            lastSuccessfulRefreshElapsedMs = lastRefreshElapsedMs,
            reconnectAttempt = reconnectAttempt,
            consecutiveErrors = consecutiveErrors,
            lastErrorMessage = if (clearError) null else lastErrorMessage,
            lastUpdateReason = reason
        )
    }

    private fun logFrame(frame: PersonFrame, startedAt: Long) {
        val latencyMs = SystemClock.elapsedRealtime() - startedAt
        val summary = buildString {
            append("frame seq=")
            append(frame.sequence)
            append(" count=")
            append(frame.persons.size)
            append(" focus=")
            append(frame.focusPersonId)
            append(" stale=")
            append(frame.stale)
            append(" reason=")
            append(frame.refreshReason)
            append(" latencyMs=")
            append(latencyMs)
            frame.persons.take(3).forEachIndexed { index, person ->
                append(" [#")
                append(index)
                append(" id=")
                append(person.id)
                append(" assoc=")
                append(person.associateId)
                append(" face=")
                append(person.withFace)
                append(" body=")
                append(person.withBody)
                append(" other=")
                append(person.isOtherFace)
                append(" dist=")
                append(person.distanceMeters)
                append(" angle=")
                append(person.angleDegrees)
                append(']')
            }
        }
        Log.i(TAG_DIAG, summary)
    }

    private fun logInfo(message: String) {
        Log.i(TAG, message)
    }

    private fun logWarn(message: String) {
        Log.w(TAG, message)
    }

    private fun logError(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
    }
}
