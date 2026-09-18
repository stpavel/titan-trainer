package com.svensson.titan.presentation.active_workout

import com.svensson.titan.domain.model.ProgramSegment
import com.svensson.titan.domain.model.WorkoutProgram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Таймер прохождения сегментов программы. Не знает про BLE и UI — только тайминг,
 * колбэки вызывает ViewModel. isPaused замораживает и продвижение по сегментам,
 * и тики (используется при ручном переопределении нагрузки — см. ActiveWorkoutViewModel).
 */
class WorkoutProgramRunner(
    private val scope: CoroutineScope,
    private val onSegmentStart: suspend (segment: ProgramSegment, index: Int) -> Unit,
    private val onTick: (segmentElapsedSeconds: Int, totalElapsedSeconds: Int) -> Unit,
    private val onFinished: () -> Unit,
) {
    private var job: Job? = null

    var isPaused: Boolean = false
        private set

    fun start(program: WorkoutProgram) {
        stop()
        isPaused = false
        job = scope.launch {
            var totalElapsed = 0
            for ((index, segment) in program.segments.withIndex()) {
                onSegmentStart(segment, index)
                var segmentElapsed = 0
                while (segmentElapsed < segment.durationSeconds) {
                    delay(1000)
                    if (isPaused) continue
                    segmentElapsed++
                    totalElapsed++
                    onTick(segmentElapsed, totalElapsed)
                }
            }
            onFinished()
        }
    }

    fun pause() { isPaused = true }

    fun resume() { isPaused = false }

    fun stop() {
        job?.cancel()
        job = null
        isPaused = false
    }
}