package com.github.nacabaro.vbhelper.source

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vitalwear.protos.Character
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalWearCharacterSettings
import com.github.nacabaro.vbhelper.utils.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class VitalWearCharacterExporterTest {
    @Test
    fun buildVitalWearSettingsProto_defaultsWhenNoStoredSettings() {
        val proto = buildVitalWearSettingsProto(null)

        assertEquals(false, proto.trainingInBackground)
        assertEquals(Character.Settings.AllowedBattles.CARD_ONLY, proto.allowedBattles)
        assertEquals(false, proto.hasAssumedFranchise())
    }

    @Test
    fun buildVitalWearSettingsProto_restoresStoredWatchSettings() {
        val proto = buildVitalWearSettingsProto(
            VitalWearCharacterSettings(
                characterId = 7L,
                trainingInBackground = true,
                allowedBattles = Character.Settings.AllowedBattles.ALL.number,
                accumulatedDailyInjuries = 2,
                assumedFranchise = 1,
            )
        )

        assertEquals(true, proto.trainingInBackground)
        assertEquals(Character.Settings.AllowedBattles.ALL, proto.allowedBattles)
        assertEquals(true, proto.hasAssumedFranchise())
        assertEquals(1, proto.assumedFranchise)
    }

    @Test
    fun buildVitalWearSettingsProto_fallsBackToCardOnlyForUnknownAllowedBattles() {
        val proto = buildVitalWearSettingsProto(
            VitalWearCharacterSettings(characterId = 7L, allowedBattles = 99)
        )

        assertEquals(Character.Settings.AllowedBattles.CARD_ONLY, proto.allowedBattles)
    }

    @Test
    fun resolveTrainingSeconds_preservesBeTrainingMinutesAsSeconds() {
        val beData = testBeData(remainingTrainingTimeInMinutes = 37)

        assertEquals(37L * 60L, resolveTrainingSeconds(DeviceType.BEDevice, beData))
    }

    @Test
    fun resolveTrainingSeconds_defaultsNonBeCharactersToVitalWearTrainingWindow() {
        assertEquals(
            DEFAULT_VITALWEAR_TRAINING_TIME_SECONDS,
            resolveTrainingSeconds(DeviceType.VBDevice, beData = null)
        )
    }

    @Test
    fun resolveTrainingSeconds_keepsZeroForBeCharactersWithoutStoredBeData() {
        assertEquals(0L, resolveTrainingSeconds(DeviceType.BEDevice, beData = null))
    }

    private fun testBeData(remainingTrainingTimeInMinutes: Int): BECharacterData {
        return BECharacterData(
            id = 1L,
            trainingHp = 0,
            trainingAp = 0,
            trainingBp = 0,
            remainingTrainingTimeInMinutes = remainingTrainingTimeInMinutes,
            itemEffectMentalStateValue = 0,
            itemEffectMentalStateMinutesRemaining = 0,
            itemEffectActivityLevelValue = 0,
            itemEffectActivityLevelMinutesRemaining = 0,
            itemEffectVitalPointsChangeValue = 0,
            itemEffectVitalPointsChangeMinutesRemaining = 0,
            abilityRarity = NfcCharacter.AbilityRarity.entries.first(),
            abilityType = 0,
            abilityBranch = 0,
            abilityReset = 0,
            rank = 0,
            itemType = 0,
            itemMultiplier = 0,
            itemRemainingTime = 0,
            otp0 = "",
            otp1 = "",
            minorVersion = 0,
            majorVersion = 0,
        )
    }
}

