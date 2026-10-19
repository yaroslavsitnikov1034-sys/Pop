package com.example.pop.game.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pop.game.model.ActiveCall
import com.example.pop.game.model.GameState
import com.example.pop.game.model.GameStatus
import com.example.pop.game.model.PhoneNumber
import com.example.pop.game.model.UpgradeType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class PhoneGameViewModel : ViewModel() {

    private val sampleCompanies = listOf(
        "Pizza Express", "Emergency 911", "Tech Helpdesk", "Taxi Dispatch",
        "Bank Hotline", "Cyber Cafe", "Space Agency", "Ali Express", "Mega Corp"
    )

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var gameLoopJob: Job? = null

    init {
        // Initial spawn to fill slots
        fillSlots()
        startGameLoop()
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (true) {
                delay(100L) // 10 ticks per second
                if (_state.value.gameStatus == GameStatus.PLAYING) {
                    onGameTick(0.1f)
                }
            }
        }
    }

    private fun onGameTick(deltaSeconds: Float) {
        _state.update { currentState ->
            if (currentState.gameStatus != GameStatus.PLAYING) return@update currentState

            // 1. Update active calls timers and auto-dialer
            val autoDialerLevel = currentState.autoDialerLevel
            val updatedCalls = mutableListOf<ActiveCall>()
            var earnedCashDelta = 0.0
            var callsCompletedDelta = 0

            for (call in currentState.activeCalls) {
                if (call.isCompleted) continue

                val newTime = call.phoneNumber.remainingTimeSeconds - deltaSeconds
                if (newTime <= 0f) {
                    // Call timed out / failed - respawn new one
                    updatedCalls.add(createNewActiveCall())
                    continue
                }

                var currentInput = call.userInput
                var isCompleted = call.isCompleted
                val targetDigits = call.phoneNumber.targetDigits

                // Auto-dialer logic: ticks digits automatically
                if (autoDialerLevel > 0 && currentInput.length < targetDigits.length) {
                    val dialProgress = autoDialerLevel * deltaSeconds
                    val charsToDial = dialProgress.toInt()
                    if (charsToDial > 0 && currentInput.length < targetDigits.length) {
                        val nextIndex = currentInput.length
                        val endIndex = minOf(targetDigits.length, nextIndex + charsToDial)
                        currentInput = targetDigits.substring(0, endIndex)
                    }
                }

                val progress = if (targetDigits.isNotEmpty()) {
                    currentInput.length.toFloat() / targetDigits.length.toFloat()
                } else {
                    1f
                }

                if (currentInput == targetDigits) {
                    isCompleted = true
                    val multiplierBonus = 1.0 + (currentState.cashMultiplierLevel * 0.25)
                    val callReward = call.phoneNumber.reward * multiplierBonus
                    earnedCashDelta += callReward
                    callsCompletedDelta++
                }

                val updatedPhoneNumber = call.phoneNumber.copy(remainingTimeSeconds = newTime)
                if (isCompleted) {
                    updatedCalls.add(createNewActiveCall())
                } else {
                    updatedCalls.add(
                        call.copy(
                            phoneNumber = updatedPhoneNumber,
                            userInput = currentInput,
                            isCompleted = isCompleted,
                            progress = progress
                        )
                    )
                }
            }

            val filledCalls = updatedCalls.toMutableList()
            while (filledCalls.size < currentState.slotCapacity) {
                filledCalls.add(createNewActiveCall())
            }

            val newCash = currentState.cash + earnedCashDelta
            val newTotalEarned = currentState.totalCashEarned + earnedCashDelta
            val newTotalCompleted = currentState.totalCallsCompleted + callsCompletedDelta

            currentState.copy(
                cash = newCash,
                totalCashEarned = newTotalEarned,
                activeCalls = filledCalls,
                totalCallsCompleted = newTotalCompleted
            )
        }
    }

    private fun fillSlots() {
        _state.update { currentState ->
            val calls = mutableListOf<ActiveCall>()
            while (calls.size < currentState.slotCapacity) {
                calls.add(createNewActiveCall())
            }
            currentState.copy(activeCalls = calls)
        }
    }

    private fun createNewActiveCall(): ActiveCall {
        val phoneNumber = generateRandomPhoneNumber()
        return ActiveCall(
            phoneNumber = phoneNumber,
            userInput = "",
            isCompleted = false,
            progress = 0f
        )
    }

    fun generateRandomPhoneNumber(): PhoneNumber {
        val length = Random.nextInt(4, 8) // 4 to 7 digits
        val sb = StringBuilder()
        for (i in 0 until length) {
            sb.append(Random.nextInt(0, 10))
        }
        val digits = sb.toString()
        val company = sampleCompanies.random()
        val reward = length * 15.0 + Random.nextDouble(5.0, 20.0)
        val timeLimit = 30f + (length * 5f)

        return PhoneNumber(
            targetDigits = digits,
            title = "$company ($digits)",
            reward = reward,
            timeLimitSeconds = timeLimit,
            remainingTimeSeconds = timeLimit
        )
    }

    /**
     * User inputs a digit on the phone keypad.
     * Evaluates against the first active call in the list.
     */
    fun onDigitInput(digit: Char) {
        _state.update { currentState ->
            if (currentState.gameStatus != GameStatus.PLAYING) return@update currentState
            val activeCalls = currentState.activeCalls.toMutableList()
            if (activeCalls.isEmpty()) return@update currentState

            val targetCallIndex = 0
            val call = activeCalls[targetCallIndex]
            if (call.isCompleted) return@update currentState

            val targetDigits = call.phoneNumber.targetDigits
            val currentInput = call.userInput

            if (currentInput.length < targetDigits.length) {
                val expectedDigit = targetDigits[currentInput.length]
                if (digit == expectedDigit) {
                    val newInput = currentInput + digit
                    val progress = newInput.length.toFloat() / targetDigits.length.toFloat()
                    val isCompleted = newInput == targetDigits

                    var earnedCash = currentState.cash
                    var totalEarned = currentState.totalCashEarned
                    var totalCompleted = currentState.totalCallsCompleted

                    val updatedCall = call.copy(
                        userInput = newInput,
                        isCompleted = isCompleted,
                        progress = progress
                    )

                    if (isCompleted) {
                        val multiplierBonus = 1.0 + (currentState.cashMultiplierLevel * 0.25)
                        val callReward = call.phoneNumber.reward * multiplierBonus
                        earnedCash += callReward
                        totalEarned += callReward
                        totalCompleted++
                        activeCalls[targetCallIndex] = createNewActiveCall()
                    } else {
                        activeCalls[targetCallIndex] = updatedCall
                    }

                    return@update currentState.copy(
                        cash = earnedCash,
                        totalCashEarned = totalEarned,
                        totalCallsCompleted = totalCompleted,
                        activeCalls = activeCalls
                    )
                }
            }
            currentState
        }
    }

    fun onBackspace() {
        _state.update { currentState ->
            val activeCalls = currentState.activeCalls.toMutableList()
            if (activeCalls.isEmpty()) return@update currentState
            val call = activeCalls[0]
            if (call.userInput.isNotEmpty() && !call.isCompleted) {
                val newInput = call.userInput.dropLast(1)
                val progress = if (call.phoneNumber.targetDigits.isNotEmpty()) {
                    newInput.length.toFloat() / call.phoneNumber.targetDigits.length.toFloat()
                } else 0f
                activeCalls[0] = call.copy(userInput = newInput, progress = progress)
                return@update currentState.copy(activeCalls = activeCalls)
            }
            currentState
        }
    }

    fun buyUpgrade(upgradeId: String) {
        _state.update { currentState ->
            val upgrades = currentState.upgrades.toMutableList()
            val index = upgrades.indexOfFirst { it.id == upgradeId }
            if (index == -1) return@update currentState

            val upgrade = upgrades[index]
            if (upgrade.level >= upgrade.maxLevel) return@update currentState

            val cost = upgrade.currentCost
            if (currentState.cash < cost) return@update currentState

            val newCash = currentState.cash - cost
            val newLevel = upgrade.level + 1
            upgrades[index] = upgrade.copy(level = newLevel)

            var newSlotCapacity = currentState.slotCapacity
            var newAutoDialerLevel = currentState.autoDialerLevel
            var newAutoClickerLevel = currentState.autoClickerLevel
            var newCashMultiplierLevel = currentState.cashMultiplierLevel

            when (upgrade.type) {
                UpgradeType.SLOT_EXPANSION -> {
                    newSlotCapacity = minOf(5, newSlotCapacity + 1)
                }
                UpgradeType.AUTO_DIALER -> {
                    newAutoDialerLevel = newLevel
                }
                UpgradeType.AUTO_CLICKER -> {
                    newAutoClickerLevel = newLevel
                }
                UpgradeType.CASH_MULTIPLIER -> {
                    newCashMultiplierLevel = newLevel
                }
            }

            val updatedCalls = currentState.activeCalls.toMutableList()
            while (updatedCalls.size < newSlotCapacity) {
                updatedCalls.add(createNewActiveCall())
            }

            currentState.copy(
                cash = newCash,
                slotCapacity = newSlotCapacity,
                autoDialerLevel = newAutoDialerLevel,
                autoClickerLevel = newAutoClickerLevel,
                cashMultiplierLevel = newCashMultiplierLevel,
                upgrades = upgrades,
                activeCalls = updatedCalls
            )
        }
    }

    fun pauseGame() {
        _state.update { it.copy(gameStatus = GameStatus.PAUSED) }
    }

    fun resumeGame() {
        _state.update { it.copy(gameStatus = GameStatus.PLAYING) }
    }

    fun resetGame() {
        gameLoopJob?.cancel()
        _state.value = GameState()
        fillSlots()
        startGameLoop()
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
    }
}
