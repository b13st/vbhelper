package com.github.nacabaro.vbhelper.source

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.github.cfogrady.vitalwear.protos.Character
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalWearCharacterSettings
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

internal const val DEFAULT_VITALWEAR_TRAINING_TIME_SECONDS = 100L * 60L * 60L

internal fun resolveTrainingSeconds(
    deviceType: DeviceType,
    beData: BECharacterData?,
): Long {
    val remainingTrainingTimeInMinutes = beData?.remainingTrainingTimeInMinutes
    if (remainingTrainingTimeInMinutes != null) {
        return remainingTrainingTimeInMinutes.toLong() * 60L
    }

    // VBHelper only stores the explicit training timer on BE payloads. When exporting non-BE
    // characters to VitalWear, preserve their ability to train by seeding the same default
    // window VitalWear uses for freshly created partners instead of sending an immediate zero.
    return if (deviceType == DeviceType.BEDevice) {
        0L
    } else {
        DEFAULT_VITALWEAR_TRAINING_TIME_SECONDS
    }
}

internal fun resolveAllowedBattles(rawValue: Int?): Character.Settings.AllowedBattles {
    if (rawValue == null) return Character.Settings.AllowedBattles.CARD_ONLY
    return Character.Settings.AllowedBattles.forNumber(rawValue)
        ?: Character.Settings.AllowedBattles.CARD_ONLY
}

internal fun buildVitalWearSettingsProto(settings: VitalWearCharacterSettings?): Character.Settings {
    val builder = Character.Settings.newBuilder()
        .setTrainingInBackground(settings?.trainingInBackground ?: false)
        .setAllowedBattles(resolveAllowedBattles(settings?.allowedBattles))
    val assumedFranchise = settings?.assumedFranchise
    if (assumedFranchise != null) {
        builder.setAssumedFranchise(assumedFranchise)
    }
    return builder.build()
}

class VitalWearCharacterExporter(
    private val context: Context,
    private val database: AppDatabase
) {
    fun buildCharacterProto(characterId: Long): Character = runBlocking {
        val characterWithSprites = database.userCharacterDao().getCharacterWithSprites(characterId)
        val userCharacter = database.userCharacterDao().getCharacter(characterId)
        val vwSettings = database.vitalWearSettingsDao().getByCharacterId(characterId)
        val card = database.cardDao().getCardByCharacterIdSync(characterId)
            ?: error("Card not found for character $characterId")
        val cardProgress = database.cardProgressDao().getCardProgressSync(card.id) ?: 0
        val beData = database.userCharacterDao().getBeDataOrNull(characterId)
        val vbData = database.userCharacterDao().getVbDataOrNull(characterId)
        val normalizedTransformationCountdownMinutes = normalizeTransformationCountdownMinutes(
            transformationCountdownMinutes = userCharacter.transformationCountdown,
            hasPossibleTransformations = hasPossibleTransformations(userCharacter.charId),
        )
        Character.newBuilder()
            .setCardId(card.cardId)
            .setCardName(card.name)
            .setCharacterStats(
                Character.CharacterStats.newBuilder()
                    .setSlotId(database.userCharacterDao().getCharacterInfo(characterId).charaIndex)
                    .setVitals(characterWithSprites.vitalPoints)
                    .setTrainingTimeRemainingInSeconds(resolveTrainingSeconds(userCharacter.characterType, beData))
                    .setTimeUntilNextTransformation(normalizedTransformationCountdownMinutes.toLong() * 60L)
                    .setTrainedBp(beData?.trainingBp ?: 0)
                    .setTrainedHp(beData?.trainingHp ?: 0)
                    .setTrainedAp(beData?.trainingAp ?: 0)
                    .setTrainedPp(characterWithSprites.trophies)
                    .setInjured(characterWithSprites.injuryStatus.name.lowercase().contains("inj"))
                    .setAccumulatedDailyInjuries(vwSettings?.accumulatedDailyInjuries ?: 0)
                    .setTotalBattles(characterWithSprites.totalBattlesWon + characterWithSprites.totalBattlesLost)
                    .setCurrentPhaseBattles(characterWithSprites.currentPhaseBattlesWon + characterWithSprites.currentPhaseBattlesLost)
                    .setTotalWins(characterWithSprites.totalBattlesWon)
                    .setCurrentPhaseWins(characterWithSprites.currentPhaseBattlesWon)
                    .setMood(characterWithSprites.mood)
                    .setAgeInDays(userCharacter.ageInDays.coerceAtLeast(0))
                    .setActivityLevel(userCharacter.activityLevel.coerceAtLeast(0))
                    .setHeartRateCurrent(userCharacter.heartRateCurrent.coerceAtLeast(0))
                    .setGeneration(vwSettings?.generation ?: vbData?.generation ?: 0)
                    .setTotalTrophies(
                        vwSettings?.totalTrophies
                            ?: vbData?.totalTrophies
                            ?: userCharacter.trophies.coerceAtLeast(0)
                    )
                    .setItemEffectMentalStateValue(beData?.itemEffectMentalStateValue ?: 0)
                    .setItemEffectMentalStateMinutesRemaining(beData?.itemEffectMentalStateMinutesRemaining ?: 0)
                    .setItemEffectActivityLevelValue(beData?.itemEffectActivityLevelValue ?: 0)
                    .setItemEffectActivityLevelMinutesRemaining(beData?.itemEffectActivityLevelMinutesRemaining ?: 0)
                    .setItemEffectVitalPointsChangeValue(beData?.itemEffectVitalPointsChangeValue ?: 0)
                    .setItemEffectVitalPointsChangeMinutesRemaining(beData?.itemEffectVitalPointsChangeMinutesRemaining ?: 0)
                    .setAbilityRarity(beData?.abilityRarity?.ordinal ?: 0)
                    .setAbilityType(beData?.abilityType ?: 0)
                    .setAbilityBranch(beData?.abilityBranch ?: 0)
                    .setAbilityReset(beData?.abilityReset ?: 0)
                    .setRank(beData?.rank ?: 0)
                    .setItemType(beData?.itemType ?: 0)
                    .setItemMultiplier(beData?.itemMultiplier ?: 0)
                    .setItemRemainingTime(beData?.itemRemainingTime ?: 0)
                    .setFirmwareMinorVersion(beData?.minorVersion ?: 0)
                    .setFirmwareMajorVersion(beData?.majorVersion ?: 0)
                    .build()
            )
            .setSettings(buildVitalWearSettingsProto(vwSettings))
            .putMaxAdventureCompletedByCard(card.name, (cardProgress - 1).coerceAtLeast(0))
            .addAllSpecialMissions(buildSpecialMissionsProto(characterId))
            .addAllTransformationHistory(
                database.userCharacterDao().getTransformationHistoryForExport(characterId).map {
                    Character.TransformationEvent.newBuilder()
                        .setCardName(it.cardName)
                        .setPhase(0)
                        .setSlotId(it.monIndex)
                        .build()
                }
            )
            .build()
    }

    private suspend fun buildSpecialMissionsProto(characterId: Long): List<Character.SpecialMission> {
        val rows = database.userCharacterDao().getSpecialMissions(characterId).first().take(4)
        if (rows.isEmpty()) {
            // Legacy character imported before missions existed: emit nothing.
            return emptyList()
        }
        val missions = rows.map { row ->
            Character.SpecialMission.newBuilder()
                .setTypeValue(row.missionType.ordinal)
                .setStatusValue(row.status.ordinal)
                .setWatchId(row.watchId)
                .setGoal(row.goal)
                .setProgress(row.progress)
                .setTimeLimitInMinutes(row.timeLimitInMinutes)
                .setTimeElapsedInMinutes(row.timeElapsedInMinutes)
                .build()
        }.toMutableList()
        // Pad to exactly 4 slots so positional slot indexing survives the round trip.
        while (missions.size < 4) {
            missions.add(Character.SpecialMission.getDefaultInstance())
        }
        return missions
    }

    fun buildShareIntent(characterId: Long): Intent {
        return runBlocking {
            val proto = buildCharacterProto(characterId)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val exportFile = File(exportDir, "vbhelper_character_$characterId.vitalwear")
            exportFile.writeBytes(proto.toByteArray())
            val exportUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", exportFile)

            Intent(Intent.ACTION_SEND).apply {
                `package` = "com.github.cfogrady.vitalwear"
                type = VITALWEAR_CHARACTER_MIME
                putExtra(Intent.EXTRA_STREAM, exportUri)
                clipData = ClipData.newRawUri("", exportUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private suspend fun hasPossibleTransformations(cardCharacterId: Long): Boolean {
        return database.characterDao().getEvolutionRequirementsForCard(cardCharacterId).firstOrNull()?.isNotEmpty() == true
    }

    companion object {
        const val VITALWEAR_CHARACTER_MIME = "application/x-vitalwear-character"
    }
}
