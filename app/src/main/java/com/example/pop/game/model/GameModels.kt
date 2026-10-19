package com.example.pop.game.model

import java.util.UUID

enum class GameStatus {
    PLAYING, PAUSED, GAME_OVER
}

enum class UpgradeType {
    SLOT_EXPANSION,
    AUTO_DIALER,
    AUTO_CLICKER,
    CASH_MULTIPLIER
}

data class UpgradeItem(
    val id: String,
    val name: String,
    val description: String,
    val baseCost: Double,
    val costMultiplier: Double = 1.15,
    val level: Int = 0,
    val maxLevel: Int = 50,
    val type: UpgradeType,
    val effectValue: Double // e.g., +1 slot, +1 digit/sec, +0.25 cash multiplier
) {
    val currentCost: Double
        get() = baseCost * Math.pow(costMultiplier, level.toDouble())

    companion object {
        fun defaultUpgrades(): List<UpgradeItem> = listOf(
            UpgradeItem(
                id = "slot_expansion",
                name = "Phone Line (Slot)",
                description = "Expand max concurrent call lines",
                baseCost = 50.0,
                costMultiplier = 2.0,
                level = 1,
                maxLevel = 5,
                type = UpgradeType.SLOT_EXPANSION,
                effectValue = 1.0
            ),
            UpgradeItem(
                id = "auto_dialer",
                name = "Auto-Dialer Bot",
                description = "Automatically dials digits on active calls",
                baseCost = 100.0,
                costMultiplier = 1.35,
                level = 0,
                maxLevel = 20,
                type = UpgradeType.AUTO_DIALER,
                effectValue = 1.0 // digits per sec per level
            ),
            UpgradeItem(
                id = "auto_clicker",
                name = "Macro Tap Booster",
                description = "Increases manual dial accuracy & bonus",
                baseCost = 75.0,
                costMultiplier = 1.25,
                level = 0,
                maxLevel = 25,
                type = UpgradeType.AUTO_CLICKER,
                effectValue = 1.0
            ),
            UpgradeItem(
                id = "cash_multiplier",
                name = "VIP Line Payout",
                description = "Increases cash earned per completed call",
                baseCost = 150.0,
                costMultiplier = 1.5,
                level = 0,
                maxLevel = 30,
                type = UpgradeType.CASH_MULTIPLIER,
                effectValue = 0.25 // +25% per level
            )
        )
    }
}

data class PhoneNumber(
    val id: String = UUID.randomUUID().toString(),
    val targetDigits: String,
    val title: String,
    val reward: Double,
    val timeLimitSeconds: Float = 45f,
    val remainingTimeSeconds: Float = 45f
) {
    val length: Int get() = targetDigits.length
}

data class ActiveCall(
    val id: String = UUID.randomUUID().toString(),
    val phoneNumber: PhoneNumber,
    val userInput: String = "",
    val isCompleted: Boolean = false,
    val progress: Float = 0f // 0.0 to 1.0
)

data class GameState(
    val cash: Double = 0.0,
    val totalCashEarned: Double = 0.0,
    val slotCapacity: Int = 1, // Max concurrent phone lines / calls
    val autoDialerLevel: Int = 0, // Auto dials digits across active calls
    val autoClickerLevel: Int = 0, // Click bonus / efficiency
    val cashMultiplierLevel: Int = 0,
    val activeCalls: List<ActiveCall> = emptyList(),
    val upgrades: List<UpgradeItem> = UpgradeItem.defaultUpgrades(),
    val gameStatus: GameStatus = GameStatus.PLAYING,
    val totalCallsCompleted: Int = 0,
    val accuracy: Float = 100f
)
