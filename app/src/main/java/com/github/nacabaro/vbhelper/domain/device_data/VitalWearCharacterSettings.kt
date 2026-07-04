package com.github.nacabaro.vbhelper.domain.device_data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class VitalWearCharacterSettings(
    @PrimaryKey val characterId: Long,
    val trainingInBackground: Boolean = false,
    val allowedBattles: Int = 1,
    val accumulatedDailyInjuries: Int = 0,
    // Null means the character never came from a VitalWear watch, so the
    // exporter falls back to safe defaults instead of trusting a stale row.
    val assumedFranchise: Int? = null,
    val generation: Int? = null,
    val totalTrophies: Int? = null,
)

