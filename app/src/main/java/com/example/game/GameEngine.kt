package com.example.game

import androidx.compose.ui.graphics.Color
import com.example.utils.SoundManager
import com.example.utils.VibrationManager
import java.util.Random

enum class PlayState {
    RUNNING,
    FALLING,
    CRASHED
}

class GameEngine(
    private val soundManager: SoundManager,
    private val vibrationManager: VibrationManager,
    private val onGameOver: (Int) -> Unit
) {
    private val random = Random()
    
    var currentLanguageProvider: () -> com.example.utils.Language = { com.example.utils.Language.ENGLISH }

    private fun getString(key: String): String {
        return com.example.utils.Localization.getString(currentLanguageProvider(), key)
    }
    
    // Virtual dimensions
    val vw = 1000f
    var vh = 1600f // dynamically updated based on ratio
    
    // Gameplay variables
    var playState = PlayState.RUNNING
    var ballX = 200f
    var ballY = 0f
    var ballRadius = 18f
    var ballDir = 1 // 1 for Up-Right, -1 for Down-Right
    var speed = 360f
    
    // Path variables
    val points = mutableListOf<GamePoint>()
    var pathWidth = 140f
    val baseStepLength = 180f
    
    // Obstacles
    val obstacles = mutableListOf<Obstacle>()
    
    // Camera
    var cameraX = 200f
    var cameraY = 0f
    
    // Trail
    val trail = mutableListOf<GamePoint>()
    val maxTrailSize = 25
    
    // Particles
    val particles = mutableListOf<GameParticle>()
    
    // Screen Shake juice
    var screenShakeTime = 0f
    var screenShakeIntensity = 0f
    
    // Perfect & Score milestone feedback systems
    var perfectGlowTime = 0f
    var perfectPulseTime = 0f
    var cameraPulseScale = 1.0f
    var bestScore = 0
    var hasTriggeredNewBestThisRun = false
    
    // Juice feedback objects
    val floatingTexts = mutableListOf<FloatingText>()
    val tapRings = mutableListOf<TapRing>()
    
    val coins = mutableListOf<Coin>()
    var onCoinCollected: ((Int) -> Unit)? = null
    var activeSkinProvider: () -> String = { "default" }
    
    // Combo system and Score tracking
    var combo = 0
    var lastScoreX = 200f
    
    // Falling animation
    var fallTime = 0f
    var ballScale = 1f
    var ballAlpha = 1f
    var safeTimeRemaining = 2.0f
    
    // Score
    var score = 0
    var scoreXStart = 200f
    
    // Colors cycle
    val neonColors = listOf(
        Color(0xFF00F5FF), // Neon Cyan
        Color(0xFFFF007F), // Neon Magenta
        Color(0xFF39FF14), // Neon Lime
        Color(0xFFFFF000), // Neon Yellow
        Color(0xFFBD00FF)  // Neon Purple
    )
    fun getActiveNeonColor(): Color {
        val index = (score / 15) % neonColors.size
        return neonColors[index]
    }
    
    init {
        reset(1600f)
    }
    
    fun reset(virtualHeight: Float) {
        vh = virtualHeight
        playState = PlayState.RUNNING
        ballX = 200f
        ballY = vh / 2f
        ballRadius = 18f
        ballDir = 1
        speed = 380f
        pathWidth = 140f
        score = 0
        scoreXStart = ballX
        lastScoreX = ballX
        combo = 0
        safeTimeRemaining = 2.0f
        screenShakeTime = 0f
        screenShakeIntensity = 0f
        
        perfectGlowTime = 0f
        perfectPulseTime = 0f
        cameraPulseScale = 1.0f
        hasTriggeredNewBestThisRun = false
        
        fallTime = 0f
        ballScale = 1f
        ballAlpha = 1f
        
        cameraX = ballX
        cameraY = ballY
        
        trail.clear()
        particles.clear()
        floatingTexts.clear()
        tapRings.clear()
        obstacles.clear()
        coins.clear()
        points.clear()
        
        // Initial path nodes (Starts directly under the ball and goes Down-Right parallel to ballDir)
        val startLength = 5 * baseStepLength // 900f
        points.add(GamePoint(200f, vh / 2f))
        points.add(GamePoint(200f + startLength, vh / 2f + startLength))
        
        // Generate several initial nodes
        for (i in 0 until 12) {
            generateNextSegment()
        }
    }
    
    fun revive() {
        if (points.size < 2) return
        
        // Find a safe segment that has been generated and is around/behind the player's crash location
        var targetPoint = points.lastOrNull { it.x < ballX }
        if (targetPoint == null) {
            targetPoint = points.firstOrNull() ?: GamePoint(ballX, ballY)
        }
        
        // Set ball position to this safe path node
        ballX = targetPoint.x
        ballY = targetPoint.y
        
        // Reset playState to RUNNING
        playState = PlayState.RUNNING
        
        // Give safe time to get bearings and avoid instant crash/fall
        safeTimeRemaining = 2.5f
        
        // Reset visual scale/alpha
        ballScale = 1f
        ballAlpha = 1f
        fallTime = 0f
        
        // Clear trail so it doesn't look weird, and start trail at the new position
        trail.clear()
        trail.add(GamePoint(ballX, ballY))
        
        // Position camera instantly to avoid rapid panning
        cameraX = ballX
        cameraY = ballY
        
        // Clear particles and floating texts to clean up the screen
        particles.clear()
        floatingTexts.clear()
        
        // Clear any obstacles near the respawn point to ensure safe respawn
        obstacles.removeAll { kotlin.math.abs(it.position.x - ballX) < 250f }
        
        // Reset ball direction
        ballDir = 1
    }
    
    fun tap() {
        if (playState != PlayState.RUNNING) return
        
        // Toggle direction
        ballDir = -ballDir
        soundManager.playTapSound()
        soundManager.playMovementSound()
        vibrationManager.vibrateTap()
        
        // Spawn a juicy expanding visual ring for tap feedback
        val activeColor = getActiveNeonColor()
        tapRings.add(
            TapRing(
                x = ballX,
                y = ballY,
                radius = ballRadius,
                maxRadius = ballRadius * 3.5f,
                color = activeColor
            )
        )

        // Every successful move should create a small, clean particle burst
        for (i in 0 until 6) {
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 60f + random.nextFloat() * 80f
            val vx = kotlin.math.cos(angle) * speed
            val vy = kotlin.math.sin(angle) * speed
            val size = 3f + random.nextFloat() * 3f
            val decay = 1.6f + random.nextFloat() * 1.4f
            particles.add(
                GameParticle(
                    x = ballX,
                    y = ballY,
                    vx = vx,
                    vy = vy,
                    color = activeColor.copy(alpha = 0.85f),
                    size = size,
                    alpha = 1.0f,
                    decay = decay
                )
            )
        }
    }
    
    private fun generateNextSegment() {
        if (points.isEmpty()) return
        val lastPoint = points.last()
        
        // Find direction of last segment
        val lastDir = if (points.size >= 2) {
            val prevPoint = points[points.size - 2]
            if (lastPoint.y < prevPoint.y) 1 else -1 // 1 for Up-Right, -1 for Down-Right
        } else {
            1
        }
        
        // Next direction is the opposite of the previous
        val nextDir = -lastDir
        
        // Decide segment length (steps of baseStepLength)
        // As score increases, make turns more frequent (steps bias towards 1 or 2)
        val maxSteps = if (score > 100) 2 else 3
        val steps = random.nextInt(maxSteps) + 1
        val segmentLength = steps * baseStepLength
        
        val nextPoint = if (nextDir == 1) {
            // Up-Right: dx = segmentLength * cos(-45), dy = segmentLength * sin(-45)
            GamePoint(lastPoint.x + segmentLength * 0.7071f, lastPoint.y - segmentLength * 0.7071f)
        } else {
            // Down-Right: dx = segmentLength * cos(45), dy = segmentLength * sin(45)
            GamePoint(lastPoint.x + segmentLength * 0.7071f, lastPoint.y + segmentLength * 0.7071f)
        }
        
        points.add(nextPoint)
        
        // Perpendicular vector for offsets
        val dx = nextPoint.x - lastPoint.x
        val dy = nextPoint.y - lastPoint.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        val nx = if (len > 0f) -dy / len else 0f
        val ny = if (len > 0f) dx / len else 0f

        // Obstacle generation (probability increases with score, start after initial runs and safe zone)
        // Only generate obstacles if after index 8 and beyond the start region, so player has a completely clean start
        var hasObstacle = false
        if (points.size > 8 && lastPoint.x > 1500f && random.nextFloat() < getObstacleProbability()) {
            val midPoint = GamePoint(
                lastPoint.x + dx * 0.5f,
                lastPoint.y + dy * 0.5f
            )
            if (len > 0f) {
                val side = if (random.nextBoolean()) 1f else -1f
                val offsetDist = 55f * side
                val obsX = midPoint.x + nx * offsetDist
                val obsY = midPoint.y + ny * offsetDist
                obstacles.add(Obstacle(GamePoint(obsX, obsY)))
                hasObstacle = true
            }
        }

        // Coin generation
        // Give a high chance of spawning coins on each segment to keep things rewarding
        if (points.size > 2) {
            val numCoinsToSpawn = if (random.nextFloat() < 0.75f) {
                if (steps > 1) random.nextInt(2) + 1 else 1
            } else {
                0
            }
            
            for (cIdx in 0 until numCoinsToSpawn) {
                // Position along the segment
                val t = if (numCoinsToSpawn == 1) 0.5f else (cIdx + 1) * (1.0f / (numCoinsToSpawn + 1))
                val coinBaseX = lastPoint.x + dx * t
                val coinBaseY = lastPoint.y + dy * t
                
                // Risk / Reward selection
                val roll = random.nextFloat()
                var coinX = coinBaseX
                var coinY = coinBaseY
                var isRisky = false
                var isDanger = false
                var coinValue = 10
                
                if (roll < 0.40f) {
                    // Safe coin: directly in the center of the path
                    coinValue = 10
                } else if (roll < 0.80f) {
                    // Risky edge coin: close to the edge of the path (±55f offset)
                    val side = if (random.nextBoolean()) 1f else -1f
                    coinX += nx * 55f * side
                    coinY += ny * 55f * side
                    isRisky = true
                    coinValue = 15 // Higher reward for risk
                } else {
                    // Danger coin: near an obstacle or extreme edge (±65f offset)
                    val side = if (random.nextBoolean()) 1f else -1f
                    coinX += nx * 65f * side
                    coinY += ny * 65f * side
                    isDanger = true
                    coinValue = 25 // Maximum reward
                }
                
                // Check if this coin is too close to any obstacles we spawned
                val tooCloseToObstacle = obstacles.any {
                    val obsDx = coinX - it.position.x
                    val obsDy = coinY - it.position.y
                    kotlin.math.sqrt(obsDx * obsDx + obsDy * obsDy) < 38f
                }
                
                if (!tooCloseToObstacle) {
                    coins.add(Coin(GamePoint(coinX, coinY), value = coinValue, isRisky = isRisky, isDanger = isDanger))
                }
            }
        }
    }
    
    private fun getObstacleProbability(): Float {
        // Starts at 15% and caps at 40% at score 100
        return (0.15f + (score / 100f) * 0.25f).coerceAtMost(0.40f)
    }
    
    fun update(deltaTime: Float, isPlaying: Boolean = true) {
        val dt = deltaTime.coerceAtMost(0.033f) // Limit large dt to prevent tunneling
        
        if (safeTimeRemaining > 0f && isPlaying && playState == PlayState.RUNNING) {
            safeTimeRemaining -= dt
        }
        
        if (screenShakeTime > 0f) {
            screenShakeTime -= dt
        }

        if (perfectGlowTime > 0f) {
            perfectGlowTime = (perfectGlowTime - dt).coerceAtLeast(0f)
        }
        if (perfectPulseTime > 0f) {
            perfectPulseTime = (perfectPulseTime - dt).coerceAtLeast(0f)
        }
        if (cameraPulseScale > 1.0f) {
            cameraPulseScale = (cameraPulseScale - dt * 1.5f).coerceAtLeast(1.0f)
        }
        
        if (playState == PlayState.RUNNING) {
            if (isPlaying) {
                // Speed increases gradually after score 300
                val speedIncrease = if (score > 300) {
                    ((score - 300) * 0.45f).coerceAtMost(220f)
                } else {
                    0f
                }
                speed = 380f + speedIncrease
                pathWidth = 140f
                
                // Move ball
                val dx = speed * 0.7071f * dt
                val dy = speed * ballDir * 0.7071f * dt
                
                ballX += dx
                ballY += dy
                
                // Continuous active skin trail effect
                val skinEffect = activeSkinProvider()
                if (skinEffect != "default" && skinEffect != "none" && random.nextFloat() < 0.35f) {
                    val pColor = when (skinEffect) {
                        "neon_blue" -> Color(0xFF00F5FF)
                        "lava_fire" -> if (random.nextBoolean()) Color(0xFFFF5722) else Color(0xFFFF9800)
                        "ice_crystal" -> if (random.nextBoolean()) Color(0xFF38BDF8) else Color(0xFFE2F1FF)
                        "galaxy_space" -> if (random.nextBoolean()) Color(0xFF8B5CF6) else Color(0xFFEC4899)
                        "golden_shine" -> Color(0xFFFBBF24)
                        "emerald_green" -> Color(0xFF10B981)
                        "diamond_ultra" -> if (random.nextBoolean()) Color(0xFFF1F5F9) else Color(0xFF93C5FD)
                        else -> Color(0xFF00F5FF)
                    }
                    val isSpark = skinEffect == "golden_shine" || skinEffect == "emerald_green" || skinEffect == "diamond_ultra" || skinEffect == "galaxy_space"
                    val pSize = if (isSpark) 3f + random.nextFloat() * 2.5f else 4f + random.nextFloat() * 4f
                    
                    particles.add(
                        GameParticle(
                            x = ballX - 5f * ballDir,
                            y = ballY,
                            vx = -speed * 0.2f * 0.7071f + (random.nextFloat() - 0.5f) * 45f,
                            vy = (random.nextFloat() - 0.5f) * 45f,
                            color = pColor,
                            size = pSize,
                            alpha = 0.9f,
                            decay = 1.8f,
                            isSparkle = isSpark
                        )
                    )
                }

                // Check coin collision
                val coinIterator = coins.iterator()
                while (coinIterator.hasNext()) {
                    val coin = coinIterator.next()
                    val coinDx = ballX - coin.position.x
                    val coinDy = ballY - coin.position.y
                    val coinDist = kotlin.math.sqrt(coinDx * coinDx + coinDy * coinDy)
                    
                    if (coinDist < (ballRadius + coin.radius + 14f)) { // Generous trigger radius for maximum game feel
                        coin.collected = true
                        onCoinCollected?.invoke(coin.value)
                        soundManager.playCoinSound()
                        vibrationManager.vibrateTap()
                        
                        // Particle burst
                        val coinColor = if (coin.isDanger) Color(0xFFFF5722) else if (coin.isRisky) Color(0xFFFFC107) else Color(0xFFFFE082)
                        for (i in 0 until 10) {
                            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
                            val pSpeed = 80f + random.nextFloat() * 140f
                            val pvx = kotlin.math.cos(angle) * pSpeed
                            val pvy = kotlin.math.sin(angle) * pSpeed
                            particles.add(
                                GameParticle(
                                    x = coin.position.x,
                                    y = coin.position.y,
                                    vx = pvx,
                                    vy = pvy,
                                    color = coinColor,
                                    size = 4f + random.nextFloat() * 4f,
                                    alpha = 1.0f,
                                    decay = 2.2f,
                                    isSparkle = true
                                )
                            )
                        }
                        
                        // Floating "+10" text popup
                        floatingTexts.add(
                            FloatingText(
                                x = coin.position.x,
                                y = coin.position.y - 25f,
                                text = "+${coin.value}",
                                color = Color(0xFFFFD700),
                                vy = -160f,
                                maxAge = 0.9f,
                                isPerfect = coin.isDanger // bouncy scaling for danger coins
                            )
                        )
                        
                        coinIterator.remove()
                    }
                }
                
                // Score progression based on distance (every 100 virtual units)
                if (ballX - lastScoreX >= 100f) {
                    val stepsPassed = ((ballX - lastScoreX) / 100f).toInt()
                    lastScoreX += stepsPassed * 100f
                    
                    // Combo numerical multiplier bonus
                    val comboBonus = if (combo >= 5) 2 else if (combo >= 3) 1 else 0
                    val basePoints = stepsPassed + comboBonus
                    score += basePoints
                    checkHighScoreMilestone()
                    
                    val textLabel = if (comboBonus > 0) "+$basePoints " + getString("combo_tag") + "!" else "+$basePoints"
                    triggerFloatingText(ballX, ballY - 45f, textLabel, Color.White.copy(alpha = 0.85f))
                }
                
                // Check obstacle avoidance for success indicators
                for (obstacle in obstacles) {
                    if (!obstacle.passed && ballX > obstacle.position.x + 35f) {
                        obstacle.passed = true
                        
                        // Calculate closeness for Perfect Timing trigger
                        val distY = kotlin.math.abs(ballY - obstacle.position.y)
                        val isPerfect = distY < 50f
                        
                        // Play switch/avoidance feedback
                        soundManager.playSwitchSound()
                        
                        combo++
                        val bonus = if (isPerfect) 2 else 1
                        score += bonus
                        checkHighScoreMilestone()
                        
                        // Trigger particles at the obstacle
                        triggerAvoidanceEffect(obstacle)
                        
                        // Display floating texts
                        if (isPerfect) {
                            perfectGlowTime = 0.40f
                            perfectPulseTime = 0.50f
                            cameraPulseScale = 1.08f
                            screenShakeTime = 0.15f
                            screenShakeIntensity = 6f
                            
                            // Golden sparkle particles
                            for (i in 0 until 18) {
                                val angle = random.nextFloat() * 2f * Math.PI.toFloat()
                                val speed = 100f + random.nextFloat() * 200f
                                val vx = kotlin.math.cos(angle) * speed
                                val vy = kotlin.math.sin(angle) * speed
                                val size = 4f + random.nextFloat() * 6f
                                val decay = 1.0f + random.nextFloat() * 1.5f
                                particles.add(
                                    GameParticle(
                                        x = ballX,
                                        y = ballY,
                                        vx = vx,
                                        vy = vy,
                                        color = Color(0xFFFFD700),
                                        size = size,
                                        alpha = 1.0f,
                                        decay = decay,
                                        isSparkle = true
                                    )
                                )
                            }
                            
                            // Add an extra big, golden visual feedback tap ring at the obstacle position
                            tapRings.add(
                                TapRing(
                                    x = obstacle.position.x,
                                    y = obstacle.position.y,
                                    radius = 20f,
                                    maxRadius = 140f,
                                    color = Color(0xFFFFD700)
                                )
                            )
                            
                            floatingTexts.add(
                                FloatingText(
                                    x = obstacle.position.x,
                                    y = obstacle.position.y - 30f,
                                    text = getString("perfect_label") + " +$bonus",
                                    color = Color(0xFFFFD700),
                                    vy = -160f,
                                    maxAge = 1.2f,
                                    isPerfect = true
                                )
                            )
                            soundManager.playScoreSound() // Play extra reward chime
                            vibrationManager.vibrateTap() // double haptic tap feedback feel
                        } else {
                            triggerFloatingText(obstacle.position.x, obstacle.position.y - 30f, "+$bonus", Color(0xFF00F5FF))
                            soundManager.playScoreSound()
                        }
                        
                        // High combo streak callouts
                        if (combo >= 3) {
                            val comboColor = if (combo >= 5) Color(0xFFFF007F) else Color(0xFFBD00FF)
                            cameraPulseScale = 1.06f
                            
                            triggerFloatingText(
                                x = ballX,
                                y = ballY - 80f,
                                text = getString("combo_tag") + " x$combo!",
                                color = comboColor,
                                isCombo = true
                            )
                            
                            // Beautiful particles for high combo!
                            val isMega = combo >= 5
                            val numParticles = if (isMega) 18 else 10
                            for (i in 0 until numParticles) {
                                val angle = random.nextFloat() * 2f * Math.PI.toFloat()
                                val speed = 120f + random.nextFloat() * 220f
                                val vx = kotlin.math.cos(angle) * speed
                                val vy = kotlin.math.sin(angle) * speed
                                val size = 3f + random.nextFloat() * 5f
                                val decay = 1.0f + random.nextFloat() * 1.5f
                                particles.add(
                                    GameParticle(
                                        x = ballX,
                                        y = ballY,
                                        vx = vx,
                                        vy = vy,
                                        color = if (random.nextBoolean()) comboColor else Color.White,
                                        size = size,
                                        alpha = 1.0f,
                                        decay = decay,
                                        isConfetti = isMega,
                                        isSparkle = !isMega
                                    )
                                )
                            }
                        }
                    }
                }
                
                // Add to trail
                trail.add(GamePoint(ballX, ballY))
                if (trail.size > maxTrailSize) {
                    trail.removeAt(0)
                }
                
                // Procedural generation: check if we need more segments ahead
                if (points.isNotEmpty() && points.last().x < ballX + 1500f) {
                    generateNextSegment()
                }
                
                // Procedural cleanup: remove segments too far behind
                if (points.size > 15 && points[5].x < ballX - 600f) {
                    points.removeAt(0)
                }
                // Cleanup obstacles and coins too far behind
                obstacles.removeAll { it.position.x < ballX - 600f }
                coins.removeAll { it.position.x < ballX - 600f }
                
                // Check path collision
                val isOnPath = checkBallOnPath()
                if (!isOnPath) {
                    playState = PlayState.FALLING
                    fallTime = 0f
                    combo = 0 // reset combo
                    screenShakeTime = 0.5f // trigger screen shake
                    screenShakeIntensity = 16f
                    soundManager.playCrashSound()
                    vibrationManager.vibrateCollision()
                } else {
                    // Check obstacle collision
                    checkObstacleCollision()
                }
            }
        } else if (playState == PlayState.FALLING) {
            // Ball falls into the abyss
            fallTime += dt
            
            // Continue moving slightly forward
            val dx = speed * 0.7071f * dt * 0.4f
            val dy = speed * ballDir * 0.7071f * dt * 0.4f
            ballX += dx
            ballY += dy
            
            // Visually scale down and fade out
            ballScale = (1f - fallTime / 0.6f).coerceAtLeast(0f)
            ballAlpha = (1f - fallTime / 0.6f).coerceAtLeast(0f)
            
            // Slowly rotate or drift Y downwards due to "gravity"
            ballY += 280f * dt * (fallTime * 2f)
            
            if (fallTime >= 0.6f) {
                triggerExplosion()
                playState = PlayState.CRASHED
                onGameOver(score)
            }
        }
        
        // Update particles
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.alpha -= p.decay * dt
            if (p.alpha <= 0f) {
                iterator.remove()
            }
        }
        
        // Update floating texts
        val textIterator = floatingTexts.iterator()
        while (textIterator.hasNext()) {
            val t = textIterator.next()
            t.y += t.vy * dt
            t.age += dt
            t.alpha = (1f - t.age / t.maxAge).coerceIn(0f, 1f)
            if (t.age >= t.maxAge) {
                textIterator.remove()
            }
        }
        
        // Update tap rings
        val ringIterator = tapRings.iterator()
        while (ringIterator.hasNext()) {
            val r = ringIterator.next()
            r.radius += 240f * dt
            r.alpha = (1f - (r.radius - ballRadius) / (r.maxRadius - ballRadius)).coerceIn(0f, 1f)
            if (r.radius >= r.maxRadius) {
                ringIterator.remove()
            }
        }
        
        // Smooth camera follow
        // Center vertically, but place the ball 35% from the left horizontally
        val targetCamX = ballX
        val targetCamY = ballY
        
        cameraX += (targetCamX - cameraX) * 8f * dt
        cameraY += (targetCamY - cameraY) * 8f * dt
    }
    
    private fun checkBallOnPath(): Boolean {
        if (safeTimeRemaining > 0f) return true
        if (points.size < 2) return true
        
        val ballPoint = GamePoint(ballX, ballY)
        
        // We only check segments that are near the ball horizontally to be super fast
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            
            // Skip segments too far ahead or behind
            if (b.x < ballX - 200f || a.x > ballX + 200f) continue
            
            val dist = distanceToSegment(ballPoint, a, b)
            if (dist <= pathWidth / 2f) {
                return true
            }
        }
        
        return false
    }
    
    private fun checkObstacleCollision() {
        if (safeTimeRemaining > 0f) return
        val ballPoint = GamePoint(ballX, ballY)
        for (obstacle in obstacles) {
            val dx = ballPoint.x - obstacle.position.x
            val dy = ballPoint.y - obstacle.position.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist < (ballRadius + obstacle.radius)) {
                // Hit obstacle! Explode instantly
                triggerExplosion()
                playState = PlayState.CRASHED
                combo = 0 // reset combo
                screenShakeTime = 0.5f // trigger screen shake
                screenShakeIntensity = 18f
                soundManager.playCrashSound()
                vibrationManager.vibrateCollision()
                onGameOver(score)
                break
            }
        }
    }
    
    private fun triggerExplosion() {
        particles.clear()
        val activeColor = getActiveNeonColor()
        
        // Burst 40 particles in random directions
        for (i in 0 until 40) {
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 150f + random.nextFloat() * 300f
            val vx = kotlin.math.cos(angle) * speed
            val vy = kotlin.math.sin(angle) * speed
            val size = 5f + random.nextFloat() * 12f
            val decay = 1f + random.nextFloat() * 2f
            
            particles.add(
                GameParticle(
                    x = ballX,
                    y = ballY,
                    vx = vx,
                    vy = vy,
                    color = if (random.nextBoolean()) activeColor else Color.White,
                    size = size,
                    alpha = 1f,
                    decay = decay
                )
            )
        }
    }

    fun triggerFloatingText(
        x: Float,
        y: Float,
        text: String,
        color: Color,
        isPerfect: Boolean = false,
        isNewBest: Boolean = false,
        isCombo: Boolean = false
    ) {
        floatingTexts.add(
            FloatingText(
                x = x,
                y = y,
                text = text,
                color = color,
                isPerfect = isPerfect,
                isNewBest = isNewBest,
                isCombo = isCombo
            )
        )
    }

    private fun triggerAvoidanceEffect(obstacle: Obstacle) {
        val activeColor = getActiveNeonColor()
        for (i in 0 until 12) {
            val angle = -Math.PI.toFloat() * 0.25f - random.nextFloat() * Math.PI.toFloat() * 0.5f
            val speed = 90f + random.nextFloat() * 140f
            val vx = kotlin.math.cos(angle) * speed
            val vy = kotlin.math.sin(angle) * speed
            val size = 3f + random.nextFloat() * 6f
            val decay = 1.2f + random.nextFloat() * 1.5f
            particles.add(
                GameParticle(
                    x = obstacle.position.x,
                    y = obstacle.position.y,
                    vx = vx,
                    vy = vy,
                    color = if (random.nextBoolean()) activeColor else Color.White,
                    size = size,
                    alpha = 1.0f,
                    decay = decay
                )
            )
        }
    }

    private fun checkHighScoreMilestone() {
        if (score > bestScore && bestScore > 0 && !hasTriggeredNewBestThisRun) {
            hasTriggeredNewBestThisRun = true
            triggerNewBestEffect()
        }
    }

    private fun triggerNewBestEffect() {
        cameraPulseScale = 1.15f
        
        // Spawn 40 colorful confetti particles
        val colors = listOf(
            Color(0xFFFF007F), Color(0xFF39FF14), Color(0xFF00F5FF),
            Color(0xFFFFF000), Color(0xFFBD00FF), Color(0xFFFF9900)
        )
        for (i in 0 until 40) {
            val angle = random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 90f + random.nextFloat() * 200f
            val vx = kotlin.math.cos(angle) * speed
            val vy = kotlin.math.sin(angle) * speed - 80f // slightly upwards gravity bias
            val size = 5f + random.nextFloat() * 8f
            val decay = 0.6f + random.nextFloat() * 0.6f
            particles.add(
                GameParticle(
                    x = ballX + (random.nextFloat() * 80f - 40f),
                    y = ballY + (random.nextFloat() * 80f - 40f),
                    vx = vx,
                    vy = vy,
                    color = colors.random(),
                    size = size,
                    alpha = 1.0f,
                    decay = decay,
                    isConfetti = true
                )
            )
        }
        
        // Stylish "NEW BEST!" floating text
        floatingTexts.add(
            FloatingText(
                x = ballX,
                y = ballY - 100f,
                text = "NEW BEST!",
                color = Color(0xFF00FFCC),
                vy = -120f,
                maxAge = 1.8f,
                isNewBest = true
            )
        )
        
        // Sound and Vibration
        soundManager.playNewBestSound()
        vibrationManager.vibrateNewBest()
    }
}

// Model Classes and Helper Math Functions
data class GamePoint(val x: Float, val y: Float)

data class Coin(
    val position: GamePoint,
    val radius: Float = 14f,
    var collected: Boolean = false,
    val value: Int = 10,
    val isRisky: Boolean = false,
    val isDanger: Boolean = false,
    var animOffset: Float = 0f
)

data class Obstacle(val position: GamePoint, val radius: Float = 22f, var passed: Boolean = false)

data class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Color,
    var alpha: Float = 1f,
    val vy: Float = -130f,
    var age: Float = 0f,
    val maxAge: Float = 1.0f,
    val isPerfect: Boolean = false,
    val isNewBest: Boolean = false,
    val isCombo: Boolean = false
)

data class TapRing(
    val x: Float,
    val y: Float,
    var radius: Float,
    val maxRadius: Float,
    var alpha: Float = 1f,
    val color: Color
)

data class GameParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    var alpha: Float,
    val decay: Float,
    val isSparkle: Boolean = false,
    val isConfetti: Boolean = false
)

fun distanceToSegment(p: GamePoint, a: GamePoint, b: GamePoint): Float {
    val abX = b.x - a.x
    val abY = b.y - a.y
    val apX = p.x - a.x
    val apY = p.y - a.y
    
    val abLenSq = abX * abX + abY * abY
    if (abLenSq == 0f) {
        val dx = p.x - a.x
        val dy = p.y - a.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    
    var t = (apX * abX + apY * abY) / abLenSq
    t = t.coerceIn(0f, 1f)
    
    val projX = a.x + t * abX
    val projY = a.y + t * abY
    
    val dx = p.x - projX
    val dy = p.y - projY
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

