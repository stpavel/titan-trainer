package com.svensson.titan.domain.usecase

import com.svensson.titan.domain.repository.BleRepository
import javax.inject.Inject

class SetResistanceLevelUseCase @Inject constructor(
    private val bleRepository: BleRepository,
) {
    suspend operator fun invoke(level: Int, quiet: Boolean = false): Boolean {
        val range = bleRepository.resistanceLevelRange.value
        return bleRepository.setTargetResistanceLevel(level.coerceIn(range.min, range.max), quiet)
    }
}