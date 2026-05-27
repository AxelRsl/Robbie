package com.robbie.platform.tracking

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.ainirobot.coreservice.client.Definition
import com.ainirobot.coreservice.client.RobotApi
import com.ainirobot.coreservice.client.listener.ActionListener
import com.robbie.platform.oem.person.OrionPersonGateway
import com.robbie.platform.oem.person.PersonFrame
import com.robbie.platform.oem.person.TrackedPerson
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
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class RobbieFaceTrackManager(
    context: Context,
    private val personGateway: OrionPersonGateway,
    private val robotApiService: RobotApiService = RobotApiService.getInstance(),
    private val config: RobbieFaceTrackConfig = RobbieFaceTrackConfig()
) : FaceTrackManager {

    companion object {
        private const val TAG = "FaceTrackManager"
        private const val TAG_DIAG = "FaceTrackDiag"
    }

    private val started = AtomicBoolean(false)
    private val appContext = context.applicationContext
    private val handlerThread = HandlerThread(config.threadName).apply { start() }
    private val workerHandler = Handler(handlerThread.looper)
    private val dispatcher: CoroutineDispatcher = workerHandler.asCoroutineDispatcher(config.threadName)
    private var workerJob: Job? = null
    private var workerScope: CoroutineScope? = null

    private val selector = RobbieTargetSelector(config)

    private val _state = MutableStateFlow(RobbieFaceTrackSnapshot.initial())
    override val state: StateFlow<RobbieFaceTrackSnapshot> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(RobbieFaceTrackMetrics.initial())
    override val metrics: StateFlow<RobbieFaceTrackMetrics> = _metrics.asStateFlow()

    private val _diagnostics = MutableStateFlow(RobbieFaceTrackDiagnostics.initial())
    override val diagnostics: StateFlow<RobbieFaceTrackDiagnostics> = _diagnostics.asStateFlow()
    private val listeners = CopyOnWriteArraySet<RobbieFaceTrackListener>()

    private var personFramesJob: Job? = null
    private var latestFrame: PersonFrame = PersonFrame.empty()
    private var currentState: RobbieFaceTrackState = RobbieFaceTrackState.IDLE
    private var activeTargetId: Int? = null
    private var candidateTargetId: Int? = null
    private var lastSelection: TargetSelectionDecision? = null
    private var isFollowing = false
    private var currentFollowRequestId = -1
    private var nextFollowRequestId = 1
    private var lastFollowStartedElapsedMs = 0L
    private var lastFollowStoppedElapsedMs = 0L
    private var lastTargetSeenElapsedMs = 0L
    private var lastTargetLostElapsedMs = 0L
    private var reacquireDeadlineElapsedMs = 0L
    private var lastTransitionElapsedMs = 0L
    private var lastDecisionReason = "init"
    private var lastErrorMessage: String? = null
    private var lastFollowStatusCode: Int? = null
    private var lastFollowStatus: String? = null
    private var lastCallbackEvent: String? = null
    private var pendingEvaluationRunnable: Runnable? = null

    private val robotConnectionListener = object : RobotApiService.ConnectionListener {
        override fun onRobotApiConnected(robotApi: RobotApi) {
            workerHandler.post {
                updateRobotConnected(true, "robot_api_connected")
                scheduleEvaluation("robot_api_connected", debounce = false)
            }
        }

        override fun onRobotApiDisconnected() {
            workerHandler.post {
                updateRobotConnected(false, "robot_api_disconnected")
                handleRobotDisconnected()
            }
        }

        override fun onRobotApiDisabled() {
            workerHandler.post {
                updateRobotConnected(false, "robot_api_disabled")
                handleRobotDisconnected()
            }
        }
    }

    override fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        workerHandler.post {
            if (workerJob?.isCancelled != false) {
                workerJob = SupervisorJob()
                workerScope = CoroutineScope(workerJob!! + dispatcher)
            }
            logInfo("start owner=${config.owner}")
            robotApiService.retain(config.owner, appContext)
            robotApiService.addConnectionListener(robotConnectionListener)
            if (config.managePersonGatewayLifecycle) {
                personGateway.start()
            }
            updateRobotConnected(robotApiService.isConnected, "start")
            val scope = workerScope ?: return@post
            personFramesJob?.cancel()
            personFramesJob = scope.launch {
                personGateway.personFrames.collect { frame ->
                    workerHandler.post framePost@{
                        if (!started.get()) {
                            return@framePost
                        }
                        latestFrame = frame
                        selector.onFrame(frame)
                        scheduleEvaluation("person_frame", debounce = true)
                    }
                }
            }
            scheduleEvaluation("start", debounce = false)
        }
    }

    override fun stop() {
        if (!started.compareAndSet(true, false)) {
            return
        }
        workerHandler.post {
            pendingEvaluationRunnable?.let(workerHandler::removeCallbacks)
            pendingEvaluationRunnable = null
            personFramesJob?.cancel()
            personFramesJob = null
            stopFollowInternal("manager_stop", force = true)
            currentState = RobbieFaceTrackState.IDLE
            activeTargetId = null
            candidateTargetId = null
            reacquireDeadlineElapsedMs = 0L
            lastDecisionReason = "stop"
            if (config.managePersonGatewayLifecycle) {
                personGateway.stop()
            }
            pushSnapshot(lastErrorMessage = null)
            robotApiService.removeConnectionListener(robotConnectionListener)
            robotApiService.release(config.owner)
            workerJob?.cancel()
            workerJob = null
            workerScope = null
            logInfo("stopped")
        }
    }

    override fun requestEvaluation(reason: String) {
        if (!started.get()) {
            return
        }
        scheduleEvaluation(reason, debounce = false)
    }

    override fun currentSnapshot(): RobbieFaceTrackSnapshot = _state.value

    override fun currentMetrics(): RobbieFaceTrackMetrics = _metrics.value

    override fun currentDiagnostics(): RobbieFaceTrackDiagnostics = _diagnostics.value

    override fun addListener(listener: RobbieFaceTrackListener) {
        listeners.add(listener)
        listener.onDiagnosticsUpdated(_diagnostics.value)
    }

    override fun removeListener(listener: RobbieFaceTrackListener) {
        listeners.remove(listener)
    }

    private fun scheduleEvaluation(reason: String, debounce: Boolean) {
        val runnable = Runnable { evaluate(reason) }
        pendingEvaluationRunnable?.let(workerHandler::removeCallbacks)
        pendingEvaluationRunnable = runnable
        if (debounce) {
            workerHandler.postDelayed(runnable, config.evaluationDebounceMs)
        } else {
            workerHandler.post(runnable)
        }
    }

    private fun evaluate(reason: String) {
        pendingEvaluationRunnable = null
        if (!started.get()) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        val frame = latestFrame
        val robotConnected = robotApiService.isConnected
        if (!robotConnected) {
            transitionTo(RobbieFaceTrackState.IDLE, "robot_disconnected")
            pushSnapshot(reason = reason, lastErrorMessage = lastErrorMessage)
            return
        }

        val currentTarget = activeTargetId?.let { id -> frame.persons.firstOrNull { it.id == id } }
        val selection = selector.select(frame, activeTargetId, now)
        lastSelection = selection

        when (currentState) {
            RobbieFaceTrackState.IDLE -> handleIdle(frame, selection, now, reason)
            RobbieFaceTrackState.ACQUIRING -> handleAcquiring(frame, selection, now, reason)
            RobbieFaceTrackState.TRACKING -> handleTracking(frame, currentTarget, selection, now, reason)
            RobbieFaceTrackState.REACQUIRING -> handleReacquiring(currentTarget, selection, now, reason)
        }

        updateTrackingDuration(now)
        pushDiagnostics()
    }

    private fun handleIdle(
        frame: PersonFrame,
        selection: TargetSelectionDecision?,
        now: Long,
        reason: String
    ) {
        if (frame.stale || selection == null) {
            transitionTo(RobbieFaceTrackState.IDLE, "idle_no_candidate")
            pushSnapshot(reason = reason)
            return
        }
        candidateTargetId = selection.person.id
        lastSelection = selection
        transitionTo(RobbieFaceTrackState.ACQUIRING, "candidate:${selection.reason}")
        if (selector.isStableEnough(selection.person.id, now)) {
            startFollowIfNeeded(selection.person, selection, "idle_acquire")
        }
        pushSnapshot(reason = reason)
    }

    private fun handleAcquiring(
        frame: PersonFrame,
        selection: TargetSelectionDecision?,
        now: Long,
        reason: String
    ) {
        if (frame.stale || selection == null) {
            candidateTargetId = null
            transitionTo(RobbieFaceTrackState.IDLE, "acquiring_lost_candidate")
            pushSnapshot(reason = reason)
            return
        }
        candidateTargetId = selection.person.id
        lastSelection = selection
        if (!selector.isStableEnough(selection.person.id, now)) {
            pushSnapshot(reason = reason)
            return
        }
        startFollowIfNeeded(selection.person, selection, "acquiring_stable")
        pushSnapshot(reason = reason)
    }

    private fun handleTracking(
        frame: PersonFrame,
        currentTarget: TrackedPerson?,
        selection: TargetSelectionDecision?,
        now: Long,
        reason: String
    ) {
        if (frame.stale) {
            enterReacquiring("tracking_frame_stale", now)
            pushSnapshot(reason = reason)
            return
        }
        if (currentTarget != null) {
            lastTargetSeenElapsedMs = now
            candidateTargetId = currentTarget.id
            if (!isFollowing) {
                startFollowIfNeeded(currentTarget, lastSelection, "tracking_resume")
            }
            if (selection != null && selection.person.id != currentTarget.id) {
                maybeSwitchTarget(selection, now)
            }
            pushSnapshot(reason = reason)
            return
        }
        enterReacquiring("tracking_target_missing", now)
        pushSnapshot(reason = reason)
    }

    private fun handleReacquiring(
        currentTarget: TrackedPerson?,
        selection: TargetSelectionDecision?,
        now: Long,
        reason: String
    ) {
        if (currentTarget != null) {
            lastTargetSeenElapsedMs = now
            candidateTargetId = currentTarget.id
            transitionTo(RobbieFaceTrackState.TRACKING, "reacquired_current_target")
            if (!isFollowing) {
                startFollowIfNeeded(currentTarget, lastSelection, "reacquire_current")
            }
            pushSnapshot(reason = reason)
            return
        }
        if (selection != null && selector.isStableEnough(selection.person.id, now)) {
            val canSwitch = activeTargetId == null || now - lastFollowStartedElapsedMs >= config.switchCooldownMs
            if (canSwitch) {
                incrementTargetSwitchIfNeeded(selection.person.id)
                activeTargetId = selection.person.id
                candidateTargetId = selection.person.id
                transitionTo(RobbieFaceTrackState.TRACKING, "reacquire_new_target")
                startFollowIfNeeded(selection.person, selection, "reacquire_new")
                pushSnapshot(reason = reason)
                return
            }
        }
        if (reacquireDeadlineElapsedMs > 0L && now <= reacquireDeadlineElapsedMs) {
            pushSnapshot(reason = reason)
            return
        }
        _metrics.value = _metrics.value.copy(lostTargetCount = _metrics.value.lostTargetCount + 1L)
        stopFollowInternal("reacquire_timeout", force = true)
        transitionTo(RobbieFaceTrackState.IDLE, "reacquire_timeout")
        activeTargetId = null
        candidateTargetId = selection?.person?.id
        pushSnapshot(reason = reason)
    }

    private fun startFollowIfNeeded(
        person: TrackedPerson,
        selection: TargetSelectionDecision?,
        reason: String
    ) {
        val now = SystemClock.elapsedRealtime()
        val currentTargetId = activeTargetId
        if (isFollowing && currentTargetId == person.id) {
            transitionTo(RobbieFaceTrackState.TRACKING, "already_following:$reason")
            lastTargetSeenElapsedMs = now
            return
        }
        if (now - lastFollowStartedElapsedMs < config.followCommandCooldownMs) {
            candidateTargetId = person.id
            lastDecisionReason = "follow_cooldown:$reason"
            return
        }
        val robotApi = robotApiService.getRobotApi() ?: run {
            lastErrorMessage = "RobotApi unavailable"
            pushSnapshot(reason = reason, lastErrorMessage = lastErrorMessage)
            return
        }
        val requestId = nextFollowRequestId++
        val previousTrackingState = currentState
        activeTargetId = person.id
        candidateTargetId = person.id
        currentFollowRequestId = requestId
        lastSelection = selection
        _metrics.value = _metrics.value.copy(followStartAttemptCount = _metrics.value.followStartAttemptCount + 1L)
        try {
            val result = robotApi.startFocusFollow(
                requestId,
                person.id,
                config.oemLostTimeoutMs.toLong(),
                config.maxFollowDistanceMeters.toFloat(),
                true,
                createFollowListener(requestId, person.id)
            )
            logDiag("startFocusFollow req=$requestId target=${person.id} result=$result reason=$reason selection=${selection?.reason}")
            if (result == 0) {
                isFollowing = true
                lastFollowStartedElapsedMs = now
                lastTargetSeenElapsedMs = now
                transitionTo(RobbieFaceTrackState.TRACKING, "follow_started:$reason")
                val currentMetrics = _metrics.value
                _metrics.value = currentMetrics.copy(
                    followStartSuccessCount = currentMetrics.followStartSuccessCount + 1L,
                    lastTrackingStartedElapsedMs = if (currentMetrics.lastTrackingStartedElapsedMs == 0L || previousTrackingState != RobbieFaceTrackState.TRACKING) now else currentMetrics.lastTrackingStartedElapsedMs
                )
            } else {
                isFollowing = false
                currentFollowRequestId = -1
                lastErrorMessage = "startFocusFollow result=$result"
                transitionTo(RobbieFaceTrackState.ACQUIRING, "follow_start_failed:$reason")
            }
        } catch (t: Throwable) {
            isFollowing = false
            currentFollowRequestId = -1
            lastErrorMessage = t.message
            logError("startFocusFollow failed reason=$reason target=${person.id}", t)
            transitionTo(RobbieFaceTrackState.ACQUIRING, "follow_start_exception:$reason")
        }
    }

    private fun stopFollowInternal(reason: String, force: Boolean) {
        val requestId = currentFollowRequestId
        if (!isFollowing && !force) {
            return
        }
        try {
            val robotApi = robotApiService.getRobotApi()
            if (robotApi != null) {
                robotApi.stopFocusFollow(if (requestId >= 0) requestId else nextFollowRequestId++)
                logDiag("stopFocusFollow req=$requestId reason=$reason")
            }
        } catch (t: Throwable) {
            logError("stopFocusFollow failed reason=$reason", t)
        }
        val now = SystemClock.elapsedRealtime()
        if (_metrics.value.lastTrackingStartedElapsedMs > 0L) {
            accumulateTrackingDuration(now)
        }
        isFollowing = false
        currentFollowRequestId = -1
        lastFollowStoppedElapsedMs = now
    }

    private fun createFollowListener(requestId: Int, personId: Int): ActionListener {
        return object : ActionListener() {
            override fun onResult(status: Int, result: String?) {
                workerHandler.post {
                    if (currentFollowRequestId != requestId) {
                        return@post
                    }
                    lastCallbackEvent = "result"
                    lastFollowStatusCode = status
                    lastFollowStatus = result
                    logDiag("follow_result req=$requestId status=$status result=$result")
                    isFollowing = false
                    currentFollowRequestId = -1
                    if (started.get()) {
                        enterReacquiring("callback_result", SystemClock.elapsedRealtime())
                        workerHandler.postDelayed(
                            { scheduleEvaluation("follow_result", debounce = false) },
                            config.callbackReevaluateDelayMs
                        )
                    }
                }
            }

            override fun onError(errorCode: Int, error: String?) {
                workerHandler.post {
                    if (currentFollowRequestId != requestId) {
                        return@post
                    }
                    lastCallbackEvent = "error"
                    lastFollowStatusCode = errorCode
                    lastFollowStatus = error
                    logDiag("follow_error req=$requestId errorCode=$errorCode error=$error")
                    if (errorCode == Definition.ACTION_RESPONSE_ALREADY_RUN) {
                        isFollowing = true
                        activeTargetId = personId
                        transitionTo(RobbieFaceTrackState.TRACKING, "already_running")
                        scheduleEvaluation("follow_already_running", debounce = true)
                        return@post
                    }
                    isFollowing = false
                    currentFollowRequestId = -1
                    lastErrorMessage = error
                    enterReacquiring("callback_error:$errorCode", SystemClock.elapsedRealtime())
                    workerHandler.postDelayed(
                        { scheduleEvaluation("follow_error", debounce = false) },
                        config.callbackReevaluateDelayMs
                    )
                }
            }

            override fun onStatusUpdate(statusCode: Int, status: String?) {
                workerHandler.post {
                    if (currentFollowRequestId != requestId) {
                        return@post
                    }
                    lastCallbackEvent = "status"
                    lastFollowStatusCode = statusCode
                    lastFollowStatus = status
                    when (statusCode) {
                        Definition.STATUS_TRACK_TARGET_SUCCEED,
                        Definition.STATUS_GUEST_APPEAR -> {
                            isFollowing = true
                            lastTargetSeenElapsedMs = SystemClock.elapsedRealtime()
                            transitionTo(RobbieFaceTrackState.TRACKING, "status_visible:$statusCode")
                        }

                        Definition.STATUS_GUEST_LOST,
                        Definition.STATUS_GUEST_FARAWAY -> {
                            enterReacquiring("status_lost:$statusCode", SystemClock.elapsedRealtime())
                        }
                    }
                    workerHandler.postDelayed(
                        { scheduleEvaluation("follow_status:$statusCode", debounce = false) },
                        config.statusReevaluateDelayMs
                    )
                }
            }
        }
    }

    private fun enterReacquiring(reason: String, now: Long) {
        if (currentState == RobbieFaceTrackState.TRACKING) {
            accumulateTrackingDuration(now)
        }
        if (currentState != RobbieFaceTrackState.REACQUIRING) {
            _metrics.value = _metrics.value.copy(reacquireCount = _metrics.value.reacquireCount + 1L)
        }
        isFollowing = false
        lastTargetLostElapsedMs = now
        reacquireDeadlineElapsedMs = now + config.reacquireGraceMs
        transitionTo(RobbieFaceTrackState.REACQUIRING, reason)
    }

    private fun maybeSwitchTarget(selection: TargetSelectionDecision, now: Long) {
        val currentTargetId = activeTargetId ?: return
        if (selection.person.id == currentTargetId) {
            return
        }
        if (now - lastFollowStartedElapsedMs < config.switchCooldownMs) {
            return
        }
        if (!selector.isStableEnough(selection.person.id, now)) {
            return
        }
        incrementTargetSwitchIfNeeded(selection.person.id)
        stopFollowInternal("switch_target", force = true)
        activeTargetId = selection.person.id
        candidateTargetId = selection.person.id
        startFollowIfNeeded(selection.person, selection, "switch_target")
    }

    private fun incrementTargetSwitchIfNeeded(newTargetId: Int) {
        val oldTargetId = activeTargetId
        if (oldTargetId != null && oldTargetId != newTargetId) {
            _metrics.value = _metrics.value.copy(targetSwitchCount = _metrics.value.targetSwitchCount + 1L)
        }
    }

    private fun transitionTo(newState: RobbieFaceTrackState, reason: String) {
        if (currentState == newState && lastDecisionReason == reason) {
            return
        }
        currentState = newState
        lastDecisionReason = reason
        lastTransitionElapsedMs = SystemClock.elapsedRealtime()
        logDiag("transition state=$newState reason=$reason activeTarget=$activeTargetId candidateTarget=$candidateTargetId following=$isFollowing")
    }

    private fun handleRobotDisconnected() {
        stopFollowInternal("robot_disconnected", force = true)
        activeTargetId = null
        candidateTargetId = null
        reacquireDeadlineElapsedMs = 0L
        transitionTo(RobbieFaceTrackState.IDLE, "robot_disconnected")
        pushSnapshot(reason = "robot_disconnected")
    }

    private fun updateRobotConnected(connected: Boolean, reason: String) {
        val snapshot = _state.value
        _state.value = snapshot.copy(
            robotApiConnected = connected,
            lastDecisionReason = reason,
            enabled = started.get()
        )
    }

    private fun pushSnapshot(reason: String = lastDecisionReason, lastErrorMessage: String? = this.lastErrorMessage) {
        _state.value = RobbieFaceTrackSnapshot(
            enabled = started.get(),
            state = currentState,
            robotApiConnected = robotApiService.isConnected,
            frameSequence = latestFrame.sequence,
            frameStale = latestFrame.stale,
            activeTargetId = activeTargetId,
            candidateTargetId = candidateTargetId,
            following = isFollowing,
            followRequestId = currentFollowRequestId,
            reacquireDeadlineElapsedMs = reacquireDeadlineElapsedMs,
            lastTargetSeenElapsedMs = lastTargetSeenElapsedMs,
            lastTargetLostElapsedMs = lastTargetLostElapsedMs,
            lastTransitionElapsedMs = lastTransitionElapsedMs,
            lastFollowStartElapsedMs = lastFollowStartedElapsedMs,
            lastFollowStopElapsedMs = lastFollowStoppedElapsedMs,
            lastDecisionReason = reason,
            lastErrorMessage = lastErrorMessage,
            lastSelectionScore = lastSelection?.score
        )
        pushDiagnostics()
    }

    private fun pushDiagnostics() {
        val updatedDiagnostics = RobbieFaceTrackDiagnostics(
            snapshot = _state.value,
            metrics = _metrics.value,
            latestFrame = latestFrame,
            lastSelection = lastSelection,
            lastFollowStatusCode = lastFollowStatusCode,
            lastFollowStatus = lastFollowStatus,
            lastCallbackEvent = lastCallbackEvent
        )
        _diagnostics.value = updatedDiagnostics
        listeners.forEach { listener ->
            try {
                listener.onDiagnosticsUpdated(updatedDiagnostics)
            } catch (t: Throwable) {
                logError("diagnostics listener failed", t)
            }
        }
    }

    private fun updateTrackingDuration(now: Long) {
        val metrics = _metrics.value
        _metrics.value = metrics.copy(
            currentTrackingDurationMs = if (currentState == RobbieFaceTrackState.TRACKING && metrics.lastTrackingStartedElapsedMs > 0L) {
                (now - metrics.lastTrackingStartedElapsedMs).coerceAtLeast(0L)
            } else {
                0L
            }
        )
    }

    private fun accumulateTrackingDuration(now: Long) {
        val metrics = _metrics.value
        val startedAt = metrics.lastTrackingStartedElapsedMs
        if (startedAt <= 0L) {
            return
        }
        _metrics.value = metrics.copy(
            totalTrackingDurationMs = metrics.totalTrackingDurationMs + (now - startedAt).coerceAtLeast(0L),
            currentTrackingDurationMs = 0L,
            lastTrackingStoppedElapsedMs = now,
            lastTrackingStartedElapsedMs = 0L
        )
    }

    private fun logInfo(message: String) {
        Log.i(TAG, message)
    }

    private fun logDiag(message: String) {
        Log.i(TAG_DIAG, message)
    }

    private fun logError(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
    }
}
