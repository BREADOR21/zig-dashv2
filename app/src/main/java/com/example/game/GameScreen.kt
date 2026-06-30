package com.example.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.animation.animateColorAsState
import android.graphics.Paint
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import com.example.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.isActive
import android.net.Uri
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

// Helper function for color interpolation
fun lerpColor(c1: Color, c2: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = c1.red + (c2.red - c1.red) * f,
        green = c1.green + (c2.green - c1.green) * f,
        blue = c1.blue + (c2.blue - c1.blue) * f,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * f
    )
}

// Reusable coin balance pill shown across menu / HUD / shop / game-over screens
@Composable
fun CoinPill(amount: Int, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFFFD700).copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(text = "\uD83D\uDCB0", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$amount",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Black
                )
            )
        }
    }
}

class BackgroundParticle(
    var x: Float,
    var y: Float,
    val speed: Float,
    val baseSize: Float,
    val angle: Float,
    val colorType: Int
)

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen = viewModel.currentScreen
    val currentScore = viewModel.currentScore
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle(initialValue = 0)
    val isSoundEnabled = viewModel.isSoundEnabled
    val isMusicEnabled = viewModel.isMusicEnabled
    val isNewHighScore = viewModel.isNewHighScore
    val coinsCount = viewModel.coinsCount
    
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    
    // Lifecycle observer to pause background music on app background and resume on foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.soundManager.startMusic()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.soundManager.stopMusic()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.soundManager.stopMusic()
        }
    }
    
    val engine = viewModel.gameEngine
    
    // Smooth transition for the active neon styling color
    val activeColorTarget = engine.getActiveNeonColor()
    val activeNeonColor by animateColorAsState(
        targetValue = activeColorTarget,
        animationSpec = tween(1000),
        label = "active_neon"
    )
    
    // Visual Progression background base colors based on current score (0 to 1000+)
    val scoreVal = currentScore.toFloat()
    val stageValue = remember(currentScore) {
        when {
            scoreVal < 200f -> scoreVal / 200f // Stage 0 -> Stage 1 (0.0 to 1.0)
            scoreVal < 500f -> 1.0f + (scoreVal - 200f) / 300f // Stage 1 -> Stage 2 (1.0 to 2.0)
            scoreVal < 1000f -> 2.0f + (scoreVal - 500f) / 500f // Stage 2 -> Stage 3 (2.0 to 3.0)
            else -> 3.0f + ((scoreVal - 1000f) / 1000f).coerceAtMost(1.0f) // Stage 3+ (3.0 to 4.0)
        }
    }

    // Dynamic background base color calculated via lerping for smooth, seamless transitions
    val targetBgColor = remember(stageValue, currentScore) {
        when {
            stageValue < 1f -> { // Score: 0 to 200 (Stage 0 -> Stage 1)
                // From Clean Minimal Slate (0xFF020617) to Deep Indigo (0xFF0B0116)
                lerpColor(Color(0xFF020617), Color(0xFF0B0116), stageValue)
            }
            stageValue < 2f -> { // Score: 200 to 500 (Stage 1 -> Stage 2)
                // From Deep Indigo (0xFF0B0116) to Dark Cosmic Violet (0xFF140224)
                lerpColor(Color(0xFF0B0116), Color(0xFF140224), stageValue - 1f)
            }
            stageValue < 3f -> { // Score: 500 to 1000 (Stage 2 -> Stage 3)
                // From Dark Cosmic Violet (0xFF140224) to Intense Cosmic Red (0xFF1F0013)
                lerpColor(Color(0xFF140224), Color(0xFF1F0013), stageValue - 2f)
            }
            else -> { // Score: 1000+ (Stage 3+)
                // Maximum visual high quality Synthwave crimson
                Color(0xFF1F0013)
            }
        }
    }

    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(1200, easing = LinearEasing),
        label = "bg_color"
    )

    // Pre-allocated space background particles
    val random = remember { java.util.Random(42L) }
    val bgParticles = remember {
        List(60) {
            BackgroundParticle(
                x = random.nextFloat(),
                y = random.nextFloat(),
                speed = 0.015f + random.nextFloat() * 0.035f,
                baseSize = 2.5f + random.nextFloat() * 4.5f,
                angle = random.nextFloat() * 2f * Math.PI.toFloat(),
                colorType = random.nextInt(3)
            )
        }
    }
    
    // Virtual resolution tick state to force Canvas redrawing
    val frameState = remember { mutableStateOf(0L) }
    
    // Persistent frame update loop
    LaunchedEffect(currentScreen) {
        isPaused = false
        var lastTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { time ->
                val delta = (time - lastTime) / 1000f
                lastTime = time
                
                if (currentScreen == GameScreen.PLAYING) {
                    if (!isPaused) {
                        viewModel.updateFrame(delta)
                    }
                } else {
                    // Update particles and camera physics even when not playing to finish explosions
                    engine.update(delta, isPlaying = false)
                }
                frameState.value = time
            }
        }
    }

    // Dynamic background visual element animations (pulsing subtle neon glow)
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    val pulseGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_glow_pulse"
    )

    val playButtonScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "play_btn_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBgColor) // Dynamic progression visual background color
            .drawBehind {
                // Draw dynamic subtle linear gradients to elevate the dark background depth
                val gradient = Brush.radialGradient(
                    colors = listOf(
                        activeNeonColor.copy(alpha = pulseGlowAlpha * 0.6f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = size.width * 0.8f
                )
                drawRect(brush = gradient)
            }
    ) {
        // Read composition-local config to feed virtual coordinate engine
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp
        val screenHeightDp = configuration.screenHeightDp.dp
        
        val random = remember { java.util.Random() }
        
        // 1. GAMEPLAY CANVAS LAYER
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentScreen, isPaused) {
                    if (currentScreen == GameScreen.PLAYING && !isPaused) {
                        coroutineScope {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    viewModel.handleTap()
                                    down.consume()
                                    
                                    do {
                                        val event = awaitPointerEvent()
                                        val anyDown = event.changes.any { it.pressed }
                                    } while (anyDown)
                                }
                            }
                        }
                    }
                }
        ) {
            // Read frameState to force Canvas redraw every frame tick
            val tick = frameState.value
            
            // Determine skin colors dynamically
            val activeSkinId = viewModel.activeSkin
            val activeSkinConfig = skinsList.find { it.id == activeSkinId }
            val skinPrimaryColor = activeSkinConfig?.primaryColor ?: activeNeonColor
            val skinSecondaryColor = activeSkinConfig?.secondaryColor ?: activeNeonColor
            
            val baseScale = size.width / engine.vw
            val scale = baseScale * engine.cameraPulseScale
            val virtualHeight = size.height / scale
            if (engine.vh != virtualHeight) {
                engine.vh = virtualHeight
            }
            
            // Screen shake offsets ONLY when screenShakeTime > 0f (on crash/death)
            val shakeX = if (engine.screenShakeTime > 0f) {
                (engine.screenShakeTime / 0.5f) * engine.screenShakeIntensity * (random.nextFloat() * 2f - 1f)
            } else {
                0f
            }
            val shakeY = if (engine.screenShakeTime > 0f) {
                (engine.screenShakeTime / 0.5f) * engine.screenShakeIntensity * (random.nextFloat() * 2f - 1f)
            } else {
                0f
            }
            
            // Camera drawing helper: centers ball horizontally around 35% from the left,
            // and vertically centered around 55% from the top to give a great view ahead.
            val originX = size.width * 0.35f
            val originY = size.height * 0.55f
            
            fun gameToScreenX(gx: Float): Float = (gx - engine.cameraX) * scale + originX + shakeX
            fun gameToScreenY(gy: Float): Float = (gy - engine.cameraY) * scale + originY + shakeY
            
            // --- DRAW AMBIENT BACKGROUND DETAILS (GRID) ---
            val gridSpacing = 160f * scale
            val gridOffsetHashX = (-engine.cameraX * scale) % gridSpacing
            val gridOffsetHashY = (-engine.cameraY * scale) % gridSpacing
            
            var gx = gridOffsetHashX
            while (gx < size.width) {
                drawLine(
                    color = Color(0xFF13151D).copy(alpha = 0.5f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, size.height),
                    strokeWidth = 1.5f * scale
                )
                gx += gridSpacing
            }
            var gy = gridOffsetHashY
            while (gy < size.height) {
                drawLine(
                    color = Color(0xFF13151D).copy(alpha = 0.5f),
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 1.5f * scale
                )
                gy += gridSpacing
            }

            // --- DRAW DYNAMIC PROGRESSIVE BACKGROUND ---
            // 1. Draw Space Floating Particles based on stageValue
            val activeBgParticlesCount = when {
                stageValue < 1f -> 15 + (stageValue * 15).toInt()
                stageValue < 2f -> 30 + ((stageValue - 1f) * 15).toInt()
                else -> 45 + ((stageValue - 2f) * 15).toInt().coerceAtMost(15)
            }
            val bgParticleScale = 1f + (stageValue * 0.4f)
            val bgParticleSpeedFactor = 1f + (stageValue * 0.5f)
            
            for (i in 0 until activeBgParticlesCount) {
                val bp = bgParticles[i]
                // Drift particles vertically slowly
                bp.y -= bp.speed * bgParticleSpeedFactor * 0.005f
                if (bp.y < 0f) bp.y += 1f
                if (bp.y > 1f) bp.y -= 1f
                
                val px = bp.x * size.width
                val py = bp.y * size.height
                val pSize = bp.baseSize * bgParticleScale * scale
                
                val twinkle = 0.4f + 0.6f * kotlin.math.sin((tick * 0.005f) + i)
                val bpColor = when (bp.colorType) {
                    0 -> activeNeonColor.copy(alpha = twinkle * 0.4f)
                    1 -> Color.White.copy(alpha = twinkle * 0.5f)
                    else -> Color(0xFFFF007F).copy(alpha = twinkle * 0.3f)
                }
                
                drawCircle(
                    color = bpColor,
                    radius = pSize,
                    center = Offset(px, py)
                )
                
                if (stageValue >= 2f && bp.baseSize > 4f) {
                    drawCircle(
                        color = bpColor.copy(alpha = bpColor.alpha * 0.3f),
                        radius = pSize * 2.5f,
                        center = Offset(px, py)
                    )
                }
            }

            // 2. Draw Slow-Moving Glowing Light Streaks (Stage 1+)
            if (stageValue >= 1f) {
                val streakTime = tick * 0.00015f
                val opacity = if (stageValue < 2f) (stageValue - 1f) * 0.12f else 0.12f
                val count = if (stageValue >= 3f) 4 else 2
                for (idx in 0 until count) {
                    val yPos = ((streakTime + idx * 0.35f) % 1.0f) * size.height
                    drawLine(
                        color = activeNeonColor.copy(alpha = opacity),
                        start = Offset(0f, yPos),
                        end = Offset(size.width, yPos - size.height * 0.25f),
                        strokeWidth = (14f + idx * 10f) * scale,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. Draw Pulsating Ambient Glow (Stage 2+)
            if (stageValue >= 2f) {
                val pulseSize = 1f + 0.15f * kotlin.math.sin(tick * 0.002f)
                val pulseGlowAlphaCalculated = pulseGlowAlpha * (1f + (stageValue - 1f) * 0.5f)
                val radialGradient = Brush.radialGradient(
                    colors = listOf(
                        activeNeonColor.copy(alpha = pulseGlowAlphaCalculated * 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = size.width * 1.1f * pulseSize
                )
                drawRect(brush = radialGradient)
            }

            // 4. Draw Cinematic Smooth Moving Light Beams (Stage 3+)
            if (stageValue >= 3f) {
                val beamTime = tick * 0.0003f
                for (b in 0 until 3) {
                    val angleRad = (Math.PI * 0.25 + kotlin.math.sin(beamTime + b * 1.5f) * Math.PI * 0.15).toFloat()
                    val origin = if (b % 2 == 0) Offset(0f, size.height) else Offset(size.width, size.height)
                    val beamLength = size.height * 1.5f
                    val endX = origin.x + kotlin.math.cos(angleRad) * beamLength * (if (b % 2 == 0) 1f else -1f)
                    val endY = origin.y - kotlin.math.sin(angleRad) * beamLength
                    
                    drawLine(
                        color = if (b % 2 == 0) activeNeonColor.copy(alpha = 0.08f) else Color(0xFFFF007F).copy(alpha = 0.06f),
                        start = origin,
                        end = Offset(endX, endY),
                        strokeWidth = 50f * scale,
                        cap = StrokeCap.Round
                    )
                }
            }
            
            // --- DRAW THE ZIG-ZAG ROAD ---
            if (engine.points.size >= 2) {
                val path = androidx.compose.ui.graphics.Path()
                val startPt = engine.points.first()
                path.moveTo(gameToScreenX(startPt.x), gameToScreenY(startPt.y))
                for (i in 1 until engine.points.size) {
                    val pt = engine.points[i]
                    path.lineTo(gameToScreenX(pt.x), gameToScreenY(pt.y))
                }
                
                // Layer A: Drop Shadow
                drawPath(
                    path = path,
                    color = Color.Black.copy(alpha = 0.5f),
                    style = Stroke(
                        width = engine.pathWidth * scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                
                // Layer B: Neon Glow Outline Border
                drawPath(
                    path = path,
                    color = skinPrimaryColor.copy(alpha = 0.25f),
                    style = Stroke(
                        width = (engine.pathWidth + 12f) * scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                
                // Layer C: Core Slate Road
                drawPath(
                    path = path,
                    color = Color(0xFF10121A),
                    style = Stroke(
                        width = engine.pathWidth * scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
                
                // Layer D: Beautiful Center Neon Guideline
                drawPath(
                    path = path,
                    color = skinPrimaryColor.copy(alpha = 0.75f),
                    style = Stroke(
                        width = 4f * scale,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            
            // --- DRAW PROCEDURAL OBSTACLES ---
            for (obstacle in engine.obstacles) {
                val obsScreenX = gameToScreenX(obstacle.position.x)
                val obsScreenY = gameToScreenY(obstacle.position.y)
                val obsRadius = obstacle.radius * scale
                
                // Obstacle Outer Aura
                drawCircle(
                    color = Color(0xFFFF0055).copy(alpha = 0.25f),
                    radius = obsRadius * 2f,
                    center = Offset(obsScreenX, obsScreenY)
                )
                
                // Obstacle Diamond Sharp Core (Vector Path drawing)
                val obsPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(obsScreenX, obsScreenY - obsRadius)
                    lineTo(obsScreenX + obsRadius, obsScreenY)
                    lineTo(obsScreenX, obsScreenY + obsRadius)
                    lineTo(obsScreenX - obsRadius, obsScreenY)
                    close()
                }
                drawPath(path = obsPath, color = Color(0xFFFF0055))
                
                // Core Highlight
                drawCircle(
                    color = Color.White,
                    radius = obsRadius * 0.4f,
                    center = Offset(obsScreenX, obsScreenY)
                )
            }
            
            // --- DRAW COINS ---
            for (coin in engine.coins) {
                if (coin.collected) continue
                
                val coinScreenX = gameToScreenX(coin.position.x)
                val coinScreenY = gameToScreenY(coin.position.y)
                val coinRadius = coin.radius * scale
                
                // Pulsing animation over time
                val pulseScale = 1f + 0.12f * kotlin.math.sin((System.currentTimeMillis() % 1000) / 1000f * 2f * Math.PI.toFloat())
                val drawRadius = coinRadius * pulseScale
                
                // Color mapping: gold for safe/risky, red-orange for danger
                val coinPrimaryColor = if (coin.isDanger) Color(0xFFFF5722) else if (coin.isRisky) Color(0xFFFFC107) else Color(0xFFFFD700)
                val coinSecondaryColor = if (coin.isDanger) Color(0xFFFF9800) else if (coin.isRisky) Color(0xFFFFE082) else Color(0xFFFFF9C4)
                
                // 1. Outer Glow Aura
                drawCircle(
                    color = coinPrimaryColor.copy(alpha = 0.25f),
                    radius = drawRadius * 2.0f,
                    center = Offset(coinScreenX, coinScreenY)
                )
                
                // 2. Main Gold Body
                drawCircle(
                    color = coinPrimaryColor,
                    radius = drawRadius,
                    center = Offset(coinScreenX, coinScreenY)
                )
                
                // 3. Inner Shinier Ring (Outlined style)
                drawCircle(
                    color = coinSecondaryColor,
                    radius = drawRadius * 0.6f,
                    center = Offset(coinScreenX, coinScreenY),
                    style = Stroke(width = 1.8f * scale)
                )
                
                // 4. White Center Star Sparkle Highlight
                drawCircle(
                    color = Color.White,
                    radius = drawRadius * 0.25f,
                    center = Offset(coinScreenX - drawRadius * 0.15f, coinScreenY - drawRadius * 0.15f)
                )
            }
            
            // --- DRAW BALL TRAIL ---
            val maxTrailToDraw = if (stageValue >= 2f) engine.trail.size else (engine.trail.size * 0.7f).toInt().coerceAtLeast(5)
            engine.trail.takeLast(maxTrailToDraw).forEachIndexed { idx, pt ->
                val trX = gameToScreenX(pt.x)
                val trY = gameToScreenY(pt.y)
                val pct = idx.toFloat() / maxTrailToDraw
                val trailSizeMultiplier = if (stageValue >= 2f) 1.25f else 0.85f
                val trRadius = engine.ballRadius * pct * scale * trailSizeMultiplier * 0.8f
                val trAlpha = pct * (if (stageValue >= 2f) 0.65f else 0.45f)
                
                drawCircle(
                    color = skinPrimaryColor.copy(alpha = trAlpha),
                    radius = trRadius,
                    center = Offset(trX, trY)
                )
            }
            
            // --- DRAW PLAYER BALL ---
            if (engine.playState != PlayState.CRASHED) {
                val ballScreenX = gameToScreenX(engine.ballX)
                val ballScreenY = gameToScreenY(engine.ballY)
                val ballRadiusSc = engine.ballRadius * scale * engine.ballScale
                val ballAl = engine.ballAlpha
                
                // 1. Shadow Drop
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f * ballAl),
                    radius = ballRadiusSc * 1.5f,
                    center = Offset(ballScreenX, ballScreenY + 8f * scale)
                )
                
                // 2. Large Neon Bloom
                drawCircle(
                    color = skinPrimaryColor.copy(alpha = 0.35f * ballAl),
                    radius = ballRadiusSc * 2.5f,
                    center = Offset(ballScreenX, ballScreenY)
                )
                
                // 3. Medium Neon Color
                drawCircle(
                    color = skinSecondaryColor.copy(alpha = ballAl),
                    radius = ballRadiusSc * 1.2f,
                    center = Offset(ballScreenX, ballScreenY)
                )
                
                // 4. White High-Energy core
                drawCircle(
                    color = Color.White.copy(alpha = ballAl),
                    radius = ballRadiusSc * 0.7f,
                    center = Offset(ballScreenX, ballScreenY)
                )

                // 5. Perfect Neon Pulse Around Player
                if (engine.perfectPulseTime > 0f) {
                    val pulseProgress = 1f - (engine.perfectPulseTime / 0.50f)
                    val pulseRadius = (engine.ballRadius * (1f + pulseProgress * 4.5f)) * scale
                    val pulseAlpha = (1f - pulseProgress) * 0.7f
                    drawCircle(
                        color = Color(0xFFFFD700).copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = Offset(ballScreenX, ballScreenY),
                        style = Stroke(width = 3.5f * scale)
                    )
                }
            }
            
            // --- DRAW COLLISION PARTICLES ---
            for (p in engine.particles) {
                val pX = gameToScreenX(p.x)
                val pY = gameToScreenY(p.y)
                val pSize = p.size * scale
                
                if (p.isSparkle) {
                    // Draw custom 4-point golden diamond-cross star for perfect sparkle
                    val starPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(pX, pY - pSize * 1.8f)
                        quadraticTo(pX, pY, pX + pSize * 1.8f, pY)
                        quadraticTo(pX, pY, pX, pY + pSize * 1.8f)
                        quadraticTo(pX, pY, pX - pSize * 1.8f, pY)
                        quadraticTo(pX, pY, pX, pY - pSize * 1.8f)
                        close()
                    }
                    drawPath(
                        path = starPath,
                        color = p.color.copy(alpha = p.alpha)
                    )
                } else if (p.isConfetti) {
                    // Draw colorful confetti square
                    val half = pSize * 1.2f
                    val angleOffset = (tick * p.decay * 0.05f) % 360f
                    rotate(angleOffset, pivot = Offset(pX, pY)) {
                        drawRect(
                            color = p.color.copy(alpha = p.alpha),
                            topLeft = Offset(pX - half, pY - half),
                            size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f)
                        )
                    }
                } else {
                    // Standard circle particle
                    drawCircle(
                        color = p.color.copy(alpha = p.alpha),
                        radius = pSize,
                        center = Offset(pX, pY)
                    )
                }
            }
            
            // --- DRAW TAP FEEDBACK RINGS ---
            for (r in engine.tapRings) {
                val rX = gameToScreenX(r.x)
                val rY = gameToScreenY(r.y)
                val rRadius = r.radius * scale
                drawCircle(
                    color = r.color.copy(alpha = r.alpha * 0.6f),
                    radius = rRadius,
                    center = Offset(rX, rY),
                    style = Stroke(width = 4f * scale)
                )
            }
            
            // --- DRAW FLOATING SCORE AND COMBO TEXTS ---
            drawContext.canvas.nativeCanvas.apply {
                for (t in engine.floatingTexts) {
                    val tX = gameToScreenX(t.x)
                    val tY = gameToScreenY(t.y)
                    
                    if (t.isPerfect) {
                        // Premium PERFECT! layout with bouncy scaling
                        val scaleMult = (1.4f + 0.25f * kotlin.math.sin(t.age * 12f)) * scale
                        save()
                        translate(tX, tY)
                        scale(scaleMult, scaleMult)
                        
                        // Text Drop Shadow
                        val paintShadow = Paint().apply {
                            color = Color.Black.copy(alpha = t.alpha * 0.85f).toArgb()
                            textSize = 22f
                            textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                        }
                        drawText(t.text, 2f, 2f, paintShadow)
                        
                        val paintText = Paint().apply {
                            color = t.color.toArgb()
                            textSize = 22f
                            textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                            alpha = (t.alpha * 255).toInt()
                        }
                        drawText(t.text, 0f, 0f, paintText)
                        restore()
                    } else if (t.isNewBest) {
                        // Premium NEW BEST! layout with grand bouncy scaling and golden outline glow
                        val scaleMult = (1.8f + 0.35f * kotlin.math.sin(t.age * 10f)) * scale
                        save()
                        translate(tX, tY)
                        scale(scaleMult, scaleMult)
                        
                        val paintGlow = Paint().apply {
                            color = Color(0xFFFFD700).copy(alpha = t.alpha * 0.6f).toArgb()
                            textSize = 24f
                            textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
                        }
                        drawText(t.text, -1.5f, -1.5f, paintGlow)
                        drawText(t.text, 1.5f, 1.5f, paintGlow)
                        
                        val paintText = Paint().apply {
                            color = t.color.toArgb()
                            textSize = 24f
                            textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
                            alpha = (t.alpha * 255).toInt()
                        }
                        drawText(t.text, 0f, 0f, paintText)
                        restore()
                    } else if (t.isCombo) {
                        // Premium COMBO! layout with funny rotational bounce and gorgeous outline glow
                        val scaleMult = (1.5f + 0.3f * kotlin.math.sin(t.age * 14f)) * scale
                        val rotation = 8f * kotlin.math.sin(t.age * 8f) // oscillating rotation
                        save()
                        translate(tX, tY)
                        scale(scaleMult, scaleMult)
                        rotate(rotation)
                        
                        val paintShadow = Paint().apply {
                            color = Color.Black.copy(alpha = t.alpha * 0.9f).toArgb()
                            textSize = 21f
                            textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                        }
                        drawText(t.text, 2f, 2f, paintShadow)
                        
                        val paintText = Paint().apply {
                            color = t.color.toArgb()
                            textSize = 21f
                            textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                            alpha = (t.alpha * 255).toInt()
                        }
                        drawText(t.text, 0f, 0f, paintText)
                        restore()
                    } else {
                        // Standard text drawing
                        val paint = Paint().apply {
                            color = t.color.toArgb()
                            textSize = 20f * scale
                            textAlign = Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                            alpha = (t.alpha * 255).toInt()
                        }
                        drawText(t.text, tX, tY, paint)
                    }
                }
            }

            // --- DRAW PERFECT MOVE BRIEF SCREEN GLOW ---
            if (engine.perfectGlowTime > 0f) {
                val glowAlpha = (engine.perfectGlowTime / 0.40f) * 0.16f
                drawRect(
                    color = activeNeonColor.copy(alpha = glowAlpha),
                    size = size
                )
            }
        }
        
        // 2. MENU SCREEN OVERLAY
        AnimatedVisibility(
            visible = currentScreen == GameScreen.MENU,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                // Background decoration details (glowing abstract diagonals mimicking the Immersive UI design)
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val darkStripPaint = Color(0xFF111111).copy(alpha = 0.25f)
                    
                    // Top-left rotated block
                    rotate(-45f, pivot = Offset(-40f, -80f)) {
                        drawRoundRect(
                            color = darkStripPaint,
                            topLeft = Offset(-40f, -80f),
                            size = androidx.compose.ui.geometry.Size(96.dp.toPx(), 320.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx(), 24.dp.toPx())
                        )
                    }

                    // Mid-left rotated block
                    rotate(45f, pivot = Offset(48f, 128f)) {
                        drawRoundRect(
                            color = darkStripPaint,
                            topLeft = Offset(48f, 128f),
                            size = androidx.compose.ui.geometry.Size(96.dp.toPx(), 320.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx(), 24.dp.toPx())
                        )
                    }
                    
                    // Small cyan glowing star particle in background
                    drawCircle(
                        color = Color(0xFF22D3EE),
                        radius = 8f,
                        center = Offset(size.width * 0.25f, size.height * 0.22f)
                    )
                    drawCircle(
                        color = Color(0xFF22D3EE).copy(alpha = 0.3f),
                        radius = 24f,
                        center = Offset(size.width * 0.25f, size.height * 0.22f)
                    )
                }

                // Coin balance, always visible top-left on the main menu
                CoinPill(
                    amount = coinsCount,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = 4.dp)
                        .testTag("menu_coin_pill")
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Header Area of Immersive Theme
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ZIG\nDASH",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 76.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                lineHeight = 72.sp,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.SansSerif
                            ),
                            modifier = Modifier.drawBehind {
                                val gradientBrush = Brush.verticalGradient(
                                    colors = listOf(Color.White, Color.White.copy(alpha = 0.4f))
                                )
                                // Highlight / subtle glow accent line below
                                drawRoundRect(
                                    color = Color(0xFF22D3EE),
                                    topLeft = Offset(size.width * 0.5f - 24.dp.toPx(), size.height + 20.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(48.dp.toPx(), 4.dp.toPx()),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                )
                            }
                        )
                    }

                    // Main Interactive Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Play Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .testTag("play_button")
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF22D3EE), Color(0xFF00F5FF))
                                    )
                                )
                                .clickable {
                                    val virtualHeightCalculated = (1000f * screenHeightDp.value / screenWidthDp.value)
                                    viewModel.startGame(virtualHeightCalculated)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.getString("play_button"),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    letterSpacing = 4.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Secondary Options Grid - Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Leaderboard Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .drawBehind {
                                        // Subtle border stroke to make it look premium
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.1f),
                                            size = size,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = viewModel.getString("best_label"),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$bestScore ${viewModel.getString("pts_label")}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            // Language Selector Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .testTag("language_button")
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .drawBehind {
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.1f),
                                            size = size,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                    .clickable { showLanguageDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = viewModel.getString("language_label"),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${viewModel.currentLanguage.flag} ${viewModel.currentLanguage.code.uppercase()}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFFFF0055),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Secondary Options Grid - Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Sound Toggle Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .testTag("sound_toggle_button")
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .drawBehind {
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.1f),
                                            size = size,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                    .clickable { viewModel.toggleSound() },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = viewModel.getString("sound_label"),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isSoundEnabled) viewModel.getString("on_label") else viewModel.getString("off_label"),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFF22D3EE),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            // Music Toggle Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .testTag("music_toggle_button")
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .drawBehind {
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = 0.1f),
                                            size = size,
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                    .clickable { viewModel.toggleMusic() },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = viewModel.getString("music_label"),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isMusicEnabled) viewModel.getString("on_label") else viewModel.getString("off_label"),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFF00F5FF),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Shop Entry Button - takes player to the skin market
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("shop_button")
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFBBF24).copy(alpha = 0.18f), Color(0xFFFF0055).copy(alpha = 0.18f))
                                    )
                                )
                                .drawBehind {
                                    drawRoundRect(
                                        color = Color(0xFFFBBF24).copy(alpha = 0.35f),
                                        size = size,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                                .clickable { viewModel.goToShop() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "\uD83D\uDED2", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = viewModel.getString("shop_title"),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp
                                    )
                                )
                            }
                        }
                    }

                    // Footer of Immersive Theme
                    Text(
                        text = viewModel.getString("footer_text"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 2.5.sp
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
        
        // 3. PLAYING SCREEN HUD OVERLAY
        AnimatedVisibility(
            visible = currentScreen == GameScreen.PLAYING,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp)
            ) {
                // Pause Button (Top-Left during gameplay)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.TopStart)
                        .testTag("pause_button")
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.35f)),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            viewModel.soundManager.playTapSound()
                            isPaused = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val barWidth = 3.5.dp.toPx()
                        val barHeight = 13.dp.toPx()
                        val barSpacing = 4.5.dp.toPx()
                        val cornerRadius = 1.5.dp.toPx()
                        
                        drawRoundRect(
                            color = Color(0xFF22D3EE),
                            topLeft = Offset(size.width / 2f - barWidth - barSpacing / 2f, size.height / 2f - barHeight / 2f),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                        )
                        drawRoundRect(
                            color = Color(0xFF22D3EE),
                            topLeft = Offset(size.width / 2f + barSpacing / 2f, size.height / 2f - barHeight / 2f),
                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                        )
                    }
                }

                // Coin balance pill (Top-Right during gameplay, below the settings gear icon)
                CoinPill(
                    amount = coinsCount,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 56.dp)
                        .testTag("hud_coin_pill")
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = viewModel.getString("current_score"),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFF22D3EE).copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentScore.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            lineHeight = 64.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Floating Pill container from Immersive UI theme
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(50),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.Transparent))
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = viewModel.getString("best_label"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = bestScore.toString(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF22D3EE),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
                
                // Small responsive instructions helper near the bottom
                if (currentScore < 3) {
                    Text(
                        text = viewModel.getString("tap_anywhere"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.35f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp)
                    )
                }
            }
        }
        
        // 4. GAME OVER SCREEN OVERLAY
        AnimatedVisibility(
            visible = currentScreen == GameScreen.GAME_OVER,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .background(Color.Black.copy(alpha = 0.85f)) // frosted dim overlay
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Heading
                Text(
                    text = viewModel.getString("game_over"),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFF0055), // bold neon hazard color
                        letterSpacing = 2.sp
                    )
                )
                
                Spacer(modifier = Modifier.height(36.dp))
                
                // High score celebration card
                Surface(
                    color = Color(0xFF13151E),
                    shape = RoundedCornerShape(24.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(
                            listOf(
                                if (isNewHighScore) activeNeonColor else Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(bottom = 36.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isNewHighScore) {
                            Text(
                                text = viewModel.getString("new_best"),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = activeNeonColor,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Text(
                            text = viewModel.getString("score_label"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        
                        Text(
                            text = currentScore.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.getString("best_score_label"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = bestScore.toString(),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = activeNeonColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        if (viewModel.coinsEarnedThisRun > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color.White.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(16.dp))

                            if (viewModel.wasPerfectRunBonus) {
                                Text(
                                    text = viewModel.getString("perfect_run_bonus"),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "\uD83D\uDCB0", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "+${viewModel.coinsEarnedThisRun}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.Black
                                    )
                                )
                            }
                        }
                    }
                }
                
                // Action Buttons
                if (!viewModel.hasRevivedThisRun) {
                    val context = LocalContext.current
                    val activity = context as? android.app.Activity
                    
                    val infiniteTransition = rememberInfiniteTransition(label = "ad_button_pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "ad_scale"
                    )
                    
                    val remaining = (3 - viewModel.reviveCount).coerceAtLeast(0)
                    
                    Button(
                        onClick = {
                            if (activity != null) {
                                viewModel.showRewardedAd(
                                    activity = activity,
                                    onAdNotReady = {
                                        android.widget.Toast.makeText(
                                            context,
                                            viewModel.getString("ad_preparing"),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onRewardEarned = {
                                        viewModel.revivePlayer()
                                    }
                                )
                            }
                        },
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(50),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp,
                            hoveredElevation = 10.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(58.dp)
                            .scale(pulseScale)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFBBF24), // Vibrant Yellow-Gold
                                        Color(0xFFF59E0B), // Glowing Gold Amber
                                        Color(0xFFEF4444)  // Radiant Coral/Red
                                    )
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .testTag("revive_ad_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = viewModel.getString("watch_ad_button"),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 13.5.sp,
                                        color = Color.Black,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // High contrast glowing badge for remaining lives
                            Box(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$remaining/3",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        val virtualHeightCalculated = (1000f * screenHeightDp.value / screenWidthDp.value)
                        viewModel.startGame(virtualHeightCalculated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeNeonColor),
                    shape = RoundedCornerShape(50),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 2.dp
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(56.dp)
                        .testTag("restart_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = viewModel.getString("play_again"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.Black,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = { viewModel.goToShop() },
                    modifier = Modifier.testTag("gameover_shop_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "\uD83D\uDED2", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = viewModel.getString("shop_title"),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
                
                TextButton(
                    onClick = { viewModel.returnToMenu() },
                    modifier = Modifier.testTag("menu_button")
                ) {
                    Text(
                        text = viewModel.getString("main_menu"),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }

        // 4.5 SHOP SCREEN OVERLAY (Skin Market)
        AnimatedVisibility(
            visible = currentScreen == GameScreen.SHOP,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            val shopContext = LocalContext.current
            val unlockedSkins = viewModel.unlockedSkins
            val activeSkin = viewModel.activeSkin

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .background(Color(0xFF05070D))
            ) {
                // Top bar: back, title, coin balance
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("shop_back_button")
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                viewModel.soundManager.playTapSound()
                                viewModel.returnToMenu()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "←", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = viewModel.getString("shop_title"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    )

                    CoinPill(amount = coinsCount, modifier = Modifier.testTag("shop_coin_pill"))
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("shop_skin_grid")
                ) {
                    items(skinsList) { skin ->
                        val isUnlocked = unlockedSkins.contains(skin.id)
                        val isActive = activeSkin == skin.id
                        val canAfford = coinsCount >= skin.cost

                        val emoji = when (skin.effectType) {
                            "neon" -> "\uD83D\uDFE6"
                            "lava" -> "\uD83D\uDD25"
                            "ice" -> "\u2744\uFE0F"
                            "galaxy" -> "\uD83C\uDF0C"
                            "gold" -> "\u2728"
                            "emerald" -> "\uD83D\uDFE2"
                            "diamond" -> "\uD83D\uDC8E"
                            else -> "\u26AA"
                        }

                        val buttonBrush = when {
                            isActive -> Brush.horizontalGradient(
                                listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))
                            )
                            isUnlocked -> Brush.horizontalGradient(listOf(skin.primaryColor, skin.secondaryColor))
                            canAfford -> Brush.horizontalGradient(listOf(Color(0xFF22D3EE), Color(0xFF00F5FF)))
                            else -> Brush.horizontalGradient(
                                listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))
                            )
                        }
                        val buttonLabel = when {
                            isActive -> viewModel.getString("selected_button")
                            isUnlocked -> viewModel.getString("select_button")
                            canAfford -> viewModel.getString("buy_button")
                            else -> "\uD83D\uDD12"
                        }
                        val buttonTextColor = when {
                            isActive -> Color.White.copy(alpha = 0.7f)
                            isUnlocked || canAfford -> Color.Black
                            else -> Color.White.copy(alpha = 0.35f)
                        }

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(
                                    width = if (isActive) 2.dp else 1.dp,
                                    brush = if (isActive) {
                                        Brush.linearGradient(listOf(skin.primaryColor, skin.secondaryColor))
                                    } else {
                                        Brush.linearGradient(
                                            listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.05f))
                                        )
                                    },
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(16.dp)
                                .testTag("skin_card_${skin.id}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Skin preview circle with primary/secondary skin colors
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(skin.primaryColor, skin.secondaryColor.copy(alpha = 0.55f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isUnlocked) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.55f))
                                    )
                                }
                                Text(text = emoji, fontSize = 26.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = viewModel.getString(skin.nameKey),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (!isUnlocked) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "\uD83D\uDCB0", fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${skin.cost}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (canAfford) Color(0xFFFFD700) else Color.White.copy(alpha = 0.4f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(17.dp))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(buttonBrush)
                                    .testTag("skin_action_${skin.id}")
                                    .clickable(enabled = !isActive) {
                                        when {
                                            isUnlocked -> {
                                                viewModel.selectSkin(skin.id)
                                            }
                                            canAfford -> {
                                                viewModel.buySkin(skin.id, skin.cost)
                                            }
                                            else -> {
                                                viewModel.soundManager.playTapSound()
                                                android.widget.Toast.makeText(
                                                    shopContext,
                                                    viewModel.getString("not_enough_coins"),
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = buttonLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = buttonTextColor,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Language Selector Dialog
        if (showLanguageDialog) {
            Dialog(onDismissRequest = { showLanguageDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF0B0F19), // Match the deep cosmic slate blue
                    border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.3f)) // Subtle cyan glowing border
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = viewModel.getString("language_label"),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        com.example.utils.Language.values().forEach { lang ->
                            val isSelected = viewModel.currentLanguage == lang
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) Color(0xFF22D3EE).copy(alpha = 0.15f)
                                        else Color.White.copy(alpha = 0.03f)
                                    )
                                    .clickable {
                                        viewModel.selectLanguage(lang)
                                        showLanguageDialog = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = lang.flag,
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(end = 16.dp)
                                    )
                                    Text(
                                        text = lang.displayName,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = if (isSelected) Color(0xFF22D3EE) else Color.White,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Settings Selector Dialog
        if (showSettingsDialog) {
            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF0B0F19), // Match the deep cosmic slate blue
                    border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.3f)) // Subtle cyan glowing border
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = viewModel.getString("settings_title"),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // --- BACKGROUND MUSIC SLIDER ---
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = viewModel.getString("music_volume_label"),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    text = "${(viewModel.musicVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF22D3EE),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = viewModel.musicVolume,
                                onValueChange = { viewModel.updateMusicVolume(it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFF22D3EE),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                                    thumbColor = Color(0xFF22D3EE)
                                ),
                                modifier = Modifier.testTag("music_volume_slider")
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // --- SOUND EFFECTS SLIDER ---
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = viewModel.getString("sfx_volume_label"),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    text = "${(viewModel.sfxVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFFFF0055),
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = viewModel.sfxVolume,
                                onValueChange = { viewModel.updateSfxVolume(it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color(0xFFFF0055),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                                    thumbColor = Color(0xFFFF0055)
                                ),
                                modifier = Modifier.testTag("sfx_volume_slider")
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // --- LANGUAGE ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    viewModel.soundManager.playTapSound()
                                    showLanguageDialog = true
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.getString("language_label"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${viewModel.currentLanguage.flag} ${viewModel.currentLanguage.displayName}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF22D3EE),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "▶",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // --- VIBRATION ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    viewModel.soundManager.playTapSound()
                                    viewModel.toggleVibration()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = viewModel.getString("vibration_label"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = if (viewModel.isVibrationEnabled) viewModel.getString("on_label") else viewModel.getString("off_label"),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (viewModel.isVibrationEnabled) Color(0xFF22D3EE) else Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))
                        
                        // Close button
                        Button(
                            onClick = { showSettingsDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22D3EE)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("close_settings_button")
                        ) {
                            Text(
                                text = viewModel.getString("close_button"),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. FLOATING SETTINGS BUTTON (Top-Right, always visible during gameplay)
        if (!viewModel.isIntroPlaying && currentScreen != GameScreen.SHOP) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.TopEnd)
                        .testTag("settings_button")
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .drawBehind {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.15f),
                                radius = size.minDimension / 2f,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        .clickable {
                            viewModel.soundManager.playTapSound()
                            showSettingsDialog = true
                            if (currentScreen == GameScreen.PLAYING) {
                                isPaused = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚙️",
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }



        // 6. PAUSE MENU OVERLAY
        if (isPaused) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .background(Color.Black.copy(alpha = 0.85f)) // frosted dim overlay
                    .padding(24.dp)
                    .pointerInput(Unit) {}, // prevent clicks from reaching game canvas
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Heading
                Text(
                    text = viewModel.getString("pause_title"),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF22D3EE),
                        letterSpacing = 2.sp
                    )
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Continue Button
                Button(
                    onClick = {
                        viewModel.soundManager.playTapSound()
                        isPaused = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(56.dp)
                        .testTag("pause_continue_button")
                ) {
                    Text(
                        text = viewModel.getString("continue_button"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Restart Button
                Button(
                    onClick = {
                        viewModel.soundManager.playTapSound()
                        isPaused = false
                        val virtualHeightCalculated = (1000f * screenHeightDp.value / screenWidthDp.value)
                        viewModel.startGame(virtualHeightCalculated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0055)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(56.dp)
                        .testTag("pause_restart_button")
                ) {
                    Text(
                        text = viewModel.getString("restart_button"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Main Menu Button
                TextButton(
                    onClick = {
                        viewModel.soundManager.playTapSound()
                        isPaused = false
                        viewModel.returnToMenu()
                    },
                    modifier = Modifier.testTag("pause_menu_button")
                ) {
                    Text(
                        text = viewModel.getString("main_menu"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}


