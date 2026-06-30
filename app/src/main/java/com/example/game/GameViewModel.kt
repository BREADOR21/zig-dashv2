package com.example.game

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameDatabase
import com.example.data.ScoreRepository
import com.example.utils.SoundManager
import com.example.utils.VibrationManager
import com.example.utils.Language
import com.example.utils.Localization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdError

enum class GameScreen {
    MENU,
    PLAYING,
    GAME_OVER,
    SHOP
}

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val database = GameDatabase.getDatabase(application)
    private val repository = ScoreRepository(database.scoreDao())
    
    val soundManager = SoundManager(application)
    val vibrationManager = VibrationManager(application)
    
    // Game Wallet and Skins State
    var coinsCount by mutableStateOf(0)
        private set
        
    var activeSkin by mutableStateOf("default")
        private set

    var unlockedSkins by mutableStateOf(setOf("default"))
        private set
    
    // UI Screens state
    var currentScreen by mutableStateOf(GameScreen.MENU)
        private set
        
    // Intro Playing State
    var isIntroPlaying by mutableStateOf(false)
        private set
        
    // Sound setting
    var isSoundEnabled by mutableStateOf(true)
        private set

    // Vibration setting
    var isVibrationEnabled by mutableStateOf(true)
        private set

    // Music setting
    var isMusicEnabled by mutableStateOf(true)
        private set

    // Volume levels (0.0f to 1.0f)
    var musicVolume by mutableStateOf(0.60f)
        private set
        
    var sfxVolume by mutableStateOf(0.80f)
        private set

    // Language setting
    var currentLanguage by mutableStateOf(Language.ENGLISH)
        private set
        
    // Scores
    var currentScore by mutableStateOf(0)
        private set
        
    private val _bestScore = MutableStateFlow(0)
    val bestScore: StateFlow<Int> = _bestScore.asStateFlow()
    
    var isNewHighScore by mutableStateOf(false)
        private set

    // Coins earned during the most recently finished run (shown on Game Over screen)
    var coinsEarnedThisRun by mutableStateOf(0)
        private set

    // Whether the last run qualified as a "perfect run" (no revives used) for bonus coins
    var wasPerfectRunBonus by mutableStateOf(false)
        private set

    // AdMob state fields
    var isAdLoaded by mutableStateOf(false)
        private set

    var isAdLoading by mutableStateOf(false)
        private set

    var hasRevivedThisRun by mutableStateOf(false)
        private set

    var reviveCount by mutableStateOf(0)
        private set

    private var rewardedAd: RewardedAd? = null

    fun loadAd() {
        if (isAdLoading || isAdLoaded) return
        isAdLoading = true
        
        val context = getApplication<Application>()
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            context,
            "ca-app-pub-3983211351328239/7379860780",
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isAdLoaded = false
                    isAdLoading = false
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isAdLoaded = true
                    isAdLoading = false
                    
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            rewardedAd = null
                            isAdLoaded = false
                            loadAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            rewardedAd = null
                            isAdLoaded = false
                            loadAd()
                        }
                    }
                }
            }
        )
    }

    fun showRewardedAd(activity: android.app.Activity, onAdNotReady: () -> Unit, onRewardEarned: () -> Unit) {
        val ad = rewardedAd
        if (ad != null) {
            ad.show(activity) { rewardItem ->
                onRewardEarned()
            }
        } else {
            loadAd()
            onAdNotReady()
        }
    }

    fun revivePlayer() {
        if (reviveCount >= 3) return
        reviveCount++
        hasRevivedThisRun = (reviveCount >= 3)
        gameEngine.revive()
        currentScreen = GameScreen.PLAYING
    }
        
    // Game Engine instance
    val gameEngine = GameEngine(
        soundManager = soundManager,
        vibrationManager = vibrationManager,
        onGameOver = { finalScore ->
            handleGameOver(finalScore)
        }
    )
    
    init {
        gameEngine.currentLanguageProvider = { currentLanguage }
        val prefs = application.getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
        
        // Load coins count
        coinsCount = prefs.getInt("coins_count", 0)
        
        // First launch welcome bonus of 200 coins
        val isFirstLaunch = prefs.getBoolean("is_first_launch_v2", true)
        if (isFirstLaunch) {
            coinsCount += 200
            prefs.edit().putBoolean("is_first_launch_v2", false).putInt("coins_count", coinsCount).apply()
        }
        
        // Load skins state
        activeSkin = prefs.getString("active_skin", "default") ?: "default"
        unlockedSkins = prefs.getStringSet("unlocked_skins", setOf("default")) ?: setOf("default")
        
        // Connect GameEngine to ViewModel
        gameEngine.onCoinCollected = { earnedCoins ->
            addCoins(earnedCoins)
        }
        gameEngine.activeSkinProvider = {
            activeSkin
        }

        // Load sound preference
        isSoundEnabled = prefs.getBoolean("sound_enabled", true)
        soundManager.setSoundEnabled(isSoundEnabled)
        
        isMusicEnabled = prefs.getBoolean("music_enabled", true)
        soundManager.setMusicEnabled(isMusicEnabled)
        
        // Load volume preferences
        musicVolume = prefs.getFloat("music_volume", 0.60f)
        sfxVolume = prefs.getFloat("sfx_volume", 0.80f)
        soundManager.setMusicVolume(musicVolume)
        soundManager.setSfxVolume(sfxVolume)
        
        // Load vibration preference
        isVibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        vibrationManager.isEnabled = isVibrationEnabled

        // Directly start on the main menu with music unblocked
        isIntroPlaying = false
        soundManager.isMusicBlocked = false
        soundManager.startMusic()

        // Load language preference (detect device language on first launch)
        val savedLangCode = prefs.getString("language_code", null)
        if (savedLangCode != null) {
            currentLanguage = Language.fromCode(savedLangCode)
        } else {
            val detected = Language.detectDeviceLanguage()
            currentLanguage = detected
            prefs.edit().putString("language_code", detected.code).apply()
        }
        
        // Load best score from Room repository and sync with gameEngine
        viewModelScope.launch {
            repository.bestScoreFlow.collect { score ->
                _bestScore.value = score
                gameEngine.bestScore = score
            }
        }
        
        // Initial AdMob preloading
        loadAd()
    }
    
    fun toggleSound() {
        isSoundEnabled = !isSoundEnabled
        soundManager.setSoundEnabled(isSoundEnabled)
        
        val prefs = getApplication<Application>().getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sound_enabled", isSoundEnabled).apply()
    }
    
    fun toggleMusic() {
        isMusicEnabled = !isMusicEnabled
        soundManager.setMusicEnabled(isMusicEnabled)
        
        val prefs = getApplication<Application>().getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("music_enabled", isMusicEnabled).apply()
    }

    fun updateMusicVolume(volume: Float) {
        musicVolume = volume.coerceIn(0.0f, 1.0f)
        soundManager.setMusicVolume(musicVolume)
        val prefs = getApplication<Application>().getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("music_volume", musicVolume).apply()
    }

    fun updateSfxVolume(volume: Float) {
        sfxVolume = volume.coerceIn(0.0f, 1.0f)
        soundManager.setSfxVolume(sfxVolume)
        val prefs = getApplication<Application>().getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("sfx_volume", sfxVolume).apply()
    }

    fun selectLanguage(language: Language) {
        currentLanguage = language
        val prefs = getApplication<Application>().getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("language_code", language.code).apply()
    }

    fun getString(key: String): String {
        return Localization.getString(currentLanguage, key)
    }
    
    fun startGame(virtualHeight: Float) {
        currentScore = 0
        isNewHighScore = false
        reviveCount = 0
        hasRevivedThisRun = false
        gameEngine.reset(virtualHeight)
        currentScreen = GameScreen.PLAYING
        loadAd() // Pre-load ad in the background while player is actively playing
    }
    
    fun handleTap() {
        gameEngine.tap()
    }
    
    fun updateFrame(deltaTime: Float) {
        gameEngine.update(deltaTime)
        currentScore = gameEngine.score
    }
    
    private fun handleGameOver(finalScore: Int) {
        viewModelScope.launch {
            val previousBest = repository.getBestScore()
            if (finalScore > previousBest) {
                isNewHighScore = true
                repository.updateBestScore(finalScore)
            } else {
                isNewHighScore = false
            }

            // Reward run coins on top of coins already collected mid-run.
            // Scales up as the run gets harder (difficulty/score increases) so longer,
            // riskier runs are always worth more.
            val difficultyMultiplier = 1.0 + (finalScore / 150.0).coerceAtMost(2.0)
            var runCoins = (finalScore * 0.6 * difficultyMultiplier).toInt()

            // "Perfect run" bonus: player never needed a revive this run
            val isPerfectRun = hasRevivedThisRun.not() && reviveCount == 0 && finalScore >= 15
            if (isPerfectRun) {
                runCoins += (runCoins * 0.5).toInt().coerceAtLeast(20)
            }
            runCoins = runCoins.coerceAtLeast(0)

            wasPerfectRunBonus = isPerfectRun
            coinsEarnedThisRun = runCoins
            if (runCoins > 0) addCoins(runCoins)

            currentScreen = GameScreen.GAME_OVER
        }
    }
    
    fun returnToMenu() {
        currentScreen = GameScreen.MENU
    }
    
    fun goToShop() {
        currentScreen = GameScreen.SHOP
        soundManager.playSwitchSound()
        vibrationManager.vibrateTap()
    }
    
    fun addCoins(amount: Int) {
        coinsCount += amount
        val prefs = getApplication<Application>().getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("coins_count", coinsCount).apply()
    }
    
    fun buySkin(skinId: String, cost: Int): Boolean {
        if (coinsCount >= cost && !unlockedSkins.contains(skinId)) {
            coinsCount -= skinIdCost(skinId)
            unlockedSkins = unlockedSkins + skinId
            activeSkin = skinId
            
            val prefs = getApplication<Application>().getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("coins_count", coinsCount)
                .putStringSet("unlocked_skins", unlockedSkins)
                .putString("active_skin", activeSkin)
                .apply()
            
            soundManager.playPurchaseSound()
            vibrationManager.vibrateNewBest()
            return true
        }
        return false
    }
    
    fun selectSkin(skinId: String) {
        if (unlockedSkins.contains(skinId)) {
            activeSkin = skinId
            val prefs = getApplication<Application>().getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("active_skin", activeSkin).apply()
            soundManager.playSwitchSound()
            vibrationManager.vibrateTap()
        }
    }
    
    private fun skinIdCost(skinId: String): Int {
        return skinsList.find { it.id == skinId }?.cost ?: 0
    }

    fun toggleVibration() {
        isVibrationEnabled = !isVibrationEnabled
        vibrationManager.isEnabled = isVibrationEnabled
        val prefs = getApplication<Application>().getSharedPreferences("zig_dash_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("vibration_enabled", isVibrationEnabled).apply()
    }

    fun finishIntro() {
        isIntroPlaying = false
        soundManager.isMusicBlocked = false
        soundManager.startMusic()
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
    }
}

// Data models for shop skins
data class SkinInfo(
    val id: String,
    val nameKey: String,
    val cost: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val effectType: String
)

val skinsList = listOf(
    SkinInfo("default", "skin_default", 0, Color(0xFF00F5FF), Color(0xFF00BFFF), "none"),
    SkinInfo("neon_blue", "skin_neon", 500, Color(0xFF00F5FF), Color(0xFF39FF14), "neon"),
    SkinInfo("lava_fire", "skin_lava", 1000, Color(0xFFEF4444), Color(0xFFF59E0B), "lava"),
    SkinInfo("ice_crystal", "skin_ice", 1500, Color(0xFF38BDF8), Color(0xFFE2F1FF), "ice"),
    SkinInfo("galaxy_space", "skin_galaxy", 2500, Color(0xFF8B5CF6), Color(0xFFEC4899), "galaxy"),
    SkinInfo("golden_shine", "skin_gold", 1200, Color(0xFFFBBF24), Color(0xFFFFFBEB), "gold"),
    SkinInfo("emerald_green", "skin_emerald", 800, Color(0xFF10B981), Color(0xFF34D399), "emerald"),
    SkinInfo("diamond_ultra", "skin_diamond", 4000, Color(0xFFE2E8F0), Color(0xFF93C5FD), "diamond")
)
