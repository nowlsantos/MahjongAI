package com.nowlsantos.mahjongai.ui.components.dice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nowlsantos.mahjongai.util.DiceSoundManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// --- 3D Vector & Math Primitives (Zero-Allocation Pipeline) ---

private class FaceData(
    val number: Int,
    val nx: Float, val ny: Float, val nz: Float,
    val ux: Float, val uy: Float, val uz: Float,
    val vx: Float, val vy: Float, val vz: Float
)

// Standard opposite faces sum to 7 (1-6, 2-5, 3-4)
private val DICE_FACES = arrayOf(
    FaceData(1, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f),
    FaceData(6, 0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f),
    FaceData(2, 1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f),
    FaceData(5, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f),
    FaceData(3, 0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
    FaceData(4, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f)
)

// Normalized [-1, 1] pip coordinates stored in flat primitive FloatArrays
private val PIP_OFFSETS: Map<Int, FloatArray> = mapOf(
    1 to floatArrayOf(0f, 0f),
    2 to floatArrayOf(-0.5f, -0.5f, 0.5f, 0.5f),
    3 to floatArrayOf(-0.55f, -0.55f, 0f, 0f, 0.55f, 0.55f),
    4 to floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f),
    5 to floatArrayOf(-0.55f, -0.55f, 0.55f, -0.55f, 0f, 0f, -0.55f, 0.55f, 0.55f, 0.55f),
    6 to floatArrayOf(
        -0.5f, -0.55f, 0.5f, -0.55f,
        -0.5f, 0f, 0.5f, 0f,
        -0.5f, 0.55f, 0.5f, 0.55f
    )
)

// Precomputed 8-point unit circle lookup table for zero-allocation pip drawing
private val COS_8 = FloatArray(8) { cos((2.0 * PI * it) / 8.0).toFloat() }
private val SIN_8 = FloatArray(8) { sin((2.0 * PI * it) / 8.0).toFloat() }

/**
 * Returns the base Euler angles in degrees (X, Y) that orient [faceNumber] directly forward (+Z normal).
 */
fun baseAnglesForFace(faceNumber: Int): Pair<Float, Float> = when (faceNumber) {
    1 -> 0f to 0f
    6 -> 0f to 180f
    2 -> 0f to -90f
    5 -> 0f to 90f
    3 -> -90f to 0f
    4 -> 90f to 0f
    else -> 0f to 0f
}

/**
 * Multi-bounce damping physics curve: high initial toss (0..72%) followed by a secondary bounce (72..100%).
 */
private fun bounceArc(progress: Float): Float {
    return if (progress < 0.72f) {
        val t = progress / 0.72f
        sin(t * PI.toFloat()) * (1f - 0.22f * t)
    } else {
        val t = (progress - 0.72f) / 0.28f
        sin(t * PI.toFloat()) * 0.16f
    }
}

/**
 * High-performance, zero-allocation 3D Dual-Dice Composable for Mahjong.
 * Simulates two independent 3D dice tumbling and landing on their target values.
 */
@Composable
fun Dice3DPair(
    die1Value: Int,
    die2Value: Int,
    isRolling: Boolean,
    modifier: Modifier = Modifier,
    diceSize: Dp = 64.dp,
    rollDurationMillis: Int = 900,
    onRollFinished: (() -> Unit)? = null
) {
    // Resting tabletop angles preserve 3D perspective at rest (showing top, front, and side faces)
    val rest1Pitch = 22f
    val rest1Yaw = -18f
    val rest2Pitch = 25f
    val rest2Yaw = 18f

    val rot1X = remember { Animatable(rest1Pitch) }
    val rot1Y = remember { Animatable(rest1Yaw) }
    val rot1Z = remember { Animatable(0f) }
    val jump1 = remember { Animatable(0f) }

    val rot2X = remember { Animatable(rest2Pitch) }
    val rot2Y = remember { Animatable(rest2Yaw) }
    val rot2Z = remember { Animatable(-8f) }
    val jump2 = remember { Animatable(0f) }

    val context = LocalContext.current
    val soundManager = remember { DiceSoundManager(context) }

    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
        }
    }

    // Pre-allocated reusable paths to guarantee zero heap allocations during animation frames
    val facePath = remember { Path() }
    val pipPath = remember { Path() }

    LaunchedEffect(isRolling, die1Value, die2Value) {
        val (base1X, base1Y) = baseAnglesForFace(die1Value)
        val (base2X, base2Y) = baseAnglesForFace(die2Value)

        if (isRolling) {
            soundManager.playDiceRoll()

            val next1X = (rot1X.value - (rot1X.value % 360f)) + 360f * Random.nextInt(3, 5) + base1X + rest1Pitch
            val next1Y = (rot1Y.value - (rot1Y.value % 360f)) + 360f * Random.nextInt(3, 5) + base1Y + rest1Yaw
            val next1Z = (rot1Z.value - (rot1Z.value % 360f)) + 360f * Random.nextInt(2, 4)

            val next2X = (rot2X.value - (rot2X.value % 360f)) + 360f * Random.nextInt(3, 5) + base2X + rest2Pitch
            val next2Y = (rot2Y.value - (rot2Y.value % 360f)) + 360f * Random.nextInt(3, 5) + base2Y + rest2Yaw
            val next2Z = (rot2Z.value - (rot2Z.value % 360f)) + 360f * Random.nextInt(2, 4) - 8f

            val rollEasing = CubicBezierEasing(0.12f, 0.82f, 0.28f, 1f)

            coroutineScope {
                launch {
                    jump1.snapTo(0f)
                    jump1.animateTo(1f, tween(rollDurationMillis, easing = rollEasing))
                }
                launch {
                    jump2.snapTo(0f)
                    jump2.animateTo(1f, tween((rollDurationMillis * 1.04f).toInt(), easing = rollEasing))
                }
                launch {
                    rot1X.animateTo(next1X, tween(rollDurationMillis, easing = rollEasing))
                }
                launch {
                    rot1Y.animateTo(next1Y, tween(rollDurationMillis, easing = rollEasing))
                }
                launch {
                    rot1Z.animateTo(next1Z, tween(rollDurationMillis, easing = rollEasing))
                }
                launch {
                    rot2X.animateTo(next2X, tween((rollDurationMillis * 1.04f).toInt(), easing = rollEasing))
                }
                launch {
                    rot2Y.animateTo(next2Y, tween((rollDurationMillis * 1.04f).toInt(), easing = rollEasing))
                }
                launch {
                    rot2Z.animateTo(next2Z, tween((rollDurationMillis * 1.04f).toInt(), easing = rollEasing))
                }
            }
            onRollFinished?.invoke()
        } else {
            rot1X.snapTo(base1X + rest1Pitch)
            rot1Y.snapTo(base1Y + rest1Yaw)
            rot1Z.snapTo(0f)
            jump1.snapTo(0f)

            rot2X.snapTo(base2X + rest2Pitch)
            rot2Y.snapTo(base2Y + rest2Yaw)
            rot2Z.snapTo(-8f)
            jump2.snapTo(0f)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val halfSize = diceSize.toPx() * 0.38f

        // Physics bounce arcs
        val arc1 = bounceArc(jump1.value)
        val scale1 = 1f + 0.24f * arc1
        val lift1 = -halfSize * 1.15f * arc1
        val drift1 = sin(jump1.value * PI.toFloat()) * (halfSize * 0.16f)

        val arc2 = bounceArc(jump2.value)
        val scale2 = 1f + 0.24f * arc2
        val lift2 = -halfSize * 1.15f * arc2
        val drift2 = -sin(jump2.value * PI.toFloat()) * (halfSize * 0.16f)

        // Well-balanced spacing centered around the canvas midpoint
        val halfSpacing = halfSize * 2.0f
        val center1 = Offset(size.width * 0.5f - halfSpacing + drift1, size.height * 0.50f + lift1)
        val center2 = Offset(size.width * 0.5f + halfSpacing + drift2, size.height * 0.50f + lift2)

        // Dynamic table contact shadows
        drawDieShadow(center = center1, halfSize = halfSize, jumpArc = arc1)
        drawDieShadow(center = center2, halfSize = halfSize, jumpArc = arc2)

        // Draw Die 1
        drawOptimizedCube(
            rx = rot1X.value,
            ry = rot1Y.value,
            rz = rot1Z.value,
            scale = scale1,
            centerOffset = center1,
            halfSize = halfSize,
            facePath = facePath,
            pipPath = pipPath
        )

        // Draw Die 2
        drawOptimizedCube(
            rx = rot2X.value,
            ry = rot2Y.value,
            rz = rot2Z.value,
            scale = scale2,
            centerOffset = center2,
            halfSize = halfSize,
            facePath = facePath,
            pipPath = pipPath
        )
    }
}

/**
 * Draws a soft elliptical table shadow under a die.
 */
private fun DrawScope.drawDieShadow(
    center: Offset,
    halfSize: Float,
    jumpArc: Float
) {
    val shadowY = center.y + halfSize * (1.18f - 0.2f * jumpArc)
    val shadowRadiusX = halfSize * 1.6f * (1f - 0.2f * jumpArc)
    val shadowRadiusY = halfSize * 0.68f * (1f - 0.2f * jumpArc)
    val shadowAlpha = 0.24f * (1f - 0.45f * jumpArc)

    drawOval(
        color = Color.Black.copy(alpha = shadowAlpha),
        topLeft = Offset(center.x - shadowRadiusX, shadowY - shadowRadiusY),
        size = Size(shadowRadiusX * 2f, shadowRadiusY * 2f)
    )
}

/**
 * Renders a single 3D cube with zero allocations inside the Compose DrawScope.
 */
private fun DrawScope.drawOptimizedCube(
    rx: Float,
    ry: Float,
    rz: Float,
    scale: Float,
    centerOffset: Offset,
    halfSize: Float,
    facePath: Path,
    pipPath: Path
) {
    val actualHalfSize = halfSize * scale
    val cameraDistance = 6.5f

    // Directional lighting vector (-0.35, -0.55, 0.75) normalized
    val lightX = -0.356f
    val lightY = -0.559f
    val lightZ = 0.749f

    val radX = rx * (PI.toFloat() / 180f)
    val radY = ry * (PI.toFloat() / 180f)
    val radZ = rz * (PI.toFloat() / 180f)

    val cosX = cos(radX)
    val sinX = sin(radX)
    val cosY = cos(radY)
    val sinY = sin(radY)
    val cosZ = cos(radZ)
    val sinZ = sin(radZ)

    // Inline rotation function for coordinates: X -> Y -> Z
    fun rotX(x: Float, y: Float, z: Float): Float {
        val x1 = x
        val y1 = y * cosX - z * sinX
        val z1 = y * sinX + z * cosX
        val x2 = x1 * cosY + z1 * sinY
        return x2 * cosZ - y1 * sinZ
    }

    fun rotY(x: Float, y: Float, z: Float): Float {
        val x1 = x
        val y1 = y * cosX - z * sinX
        val z1 = y * sinX + z * cosX
        val x2 = x1 * cosY + z1 * sinY
        return x2 * sinZ + y1 * cosZ
    }

    fun rotZ(x: Float, y: Float, z: Float): Float {
        val x1 = x
        val z1 = y * sinX + z * cosX
        return -x1 * sinY + z1 * cosY
    }

    // Identify visible faces with back-face culling (at most 3 visible simultaneously)
    var visCount = 0
    val visIndices = IntArray(3)
    val visZ = FloatArray(3)

    for (i in 0 until 6) {
        val face = DICE_FACES[i]
        val rnz = rotZ(face.nx, face.ny, face.nz)
        if (rnz > 0.02f) {
            visIndices[visCount] = i
            visZ[visCount] = rnz
            visCount++
            if (visCount >= 3) break
        }
    }

    // Painter's algorithm: sort visible faces back-to-front (smallest normal.z drawn first)
    if (visCount == 2 && visZ[0] > visZ[1]) {
        val tmpI = visIndices[0]; visIndices[0] = visIndices[1]; visIndices[1] = tmpI
        val tmpZ = visZ[0]; visZ[0] = visZ[1]; visZ[1] = tmpZ
    } else if (visCount == 3) {
        if (visZ[0] > visZ[1]) {
            val tmpI = visIndices[0]; visIndices[0] = visIndices[1]; visIndices[1] = tmpI
            val tmpZ = visZ[0]; visZ[0] = visZ[1]; visZ[1] = tmpZ
        }
        if (visZ[1] > visZ[2]) {
            val tmpI = visIndices[1]; visIndices[1] = visIndices[2]; visIndices[2] = tmpI
            val tmpZ = visZ[1]; visZ[1] = visZ[2]; visZ[2] = tmpZ
        }
        if (visZ[0] > visZ[1]) {
            val tmpI = visIndices[0]; visIndices[0] = visIndices[1]; visIndices[1] = tmpI
            val tmpZ = visZ[0]; visZ[0] = visZ[1]; visZ[1] = tmpZ
        }
    }

    // Draw visible faces
    for (v in 0 until visCount) {
        val face = DICE_FACES[visIndices[v]]

        // Rotated face normal
        val rnx = rotX(face.nx, face.ny, face.nz)
        val rny = rotY(face.nx, face.ny, face.nz)
        val rnz = rotZ(face.nx, face.ny, face.nz)

        // Rotated local axes U and V
        val rux = rotX(face.ux, face.uy, face.uz)
        val ruy = rotY(face.ux, face.uy, face.uz)
        val ruz = rotZ(face.ux, face.uy, face.uz)

        val rvx = rotX(face.vx, face.vy, face.vz)
        val rvy = rotY(face.vx, face.vy, face.vz)
        val rvz = rotZ(face.vx, face.vy, face.vz)

        // 4 corners of the face polygon
        facePath.reset()
        for (c in 0 until 4) {
            val cu = if (c == 1 || c == 2) 1f else -1f
            val cv = if (c == 2 || c == 3) 1f else -1f

            val cx = rnx + rux * cu + rvx * cv
            val cy = rny + ruy * cu + rvy * cv
            val cz = rnz + ruz * cu + rvz * cv

            val psp = cameraDistance / (cameraDistance - cz).coerceAtLeast(1f)
            val px = centerOffset.x + cx * actualHalfSize * psp
            val py = centerOffset.y + cy * actualHalfSize * psp

            if (c == 0) facePath.moveTo(px, py) else facePath.lineTo(px, py)
        }
        facePath.close()

        // Directional diffuse lighting
        val brightness = (rnx * lightX + rny * lightY + rnz * lightZ).coerceIn(0f, 1f)
        val shade = 0.80f + 0.20f * brightness
        val faceColor = Color(red = shade, green = shade, blue = (shade * 0.96f).coerceAtMost(1f))

        drawPath(path = facePath, color = faceColor, style = Fill)
        drawPath(
            path = facePath,
            color = Color(0x33000000),
            style = Stroke(
                width = 1.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Render pips with traditional Mahjong coloring: Face 1 and Face 4 are red; others are charcoal
        // Dynamically scale pip size based on face geometry so dots are prominent yet maintain clean negative space:
        // - Face 1: Classic large center red sun
        // - Faces 2 & 3: Diagonal layouts with generous clearance
        // - Face 4: 2x2 grid (red pips)
        // - Face 5: 4 corners + center dot
        // - Face 6: 2x3 column grid (tighter vertical spacing)
        val pipRadius = when (face.number) {
            1 -> 0.35f
            2 -> 0.23f
            3 -> 0.21f
            4 -> 0.22f
            5 -> 0.19f
            6 -> 0.185f
            else -> 0.20f
        }
        val pipColor = if (face.number == 1 || face.number == 4) Color(0xFFC62828) else Color(0xFF263238)

        val offsets = PIP_OFFSETS[face.number] ?: continue
        val pipCount = offsets.size / 2

        for (p in 0 until pipCount) {
            val px = offsets[p * 2]
            val py = offsets[p * 2 + 1]

            val pcx = rnx + rux * px + rvx * py
            val pcy = rny + ruy * px + rvy * py
            val pcz = rnz + ruz * px + rvz * py

            pipPath.reset()
            for (s in 0 until 8) {
                val cosVal = COS_8[s]
                val sinVal = SIN_8[s]

                val ptx = pcx + (rux * cosVal + rvx * sinVal) * pipRadius
                val pty = pcy + (ruy * cosVal + rvy * sinVal) * pipRadius
                val ptz = pcz + (ruz * cosVal + rvz * sinVal) * pipRadius

                val psp = cameraDistance / (cameraDistance - ptz).coerceAtLeast(1f)
                val sx = centerOffset.x + ptx * actualHalfSize * psp
                val sy = centerOffset.y + pty * actualHalfSize * psp

                if (s == 0) pipPath.moveTo(sx, sy) else pipPath.lineTo(sx, sy)
            }
            pipPath.close()
            drawPath(path = pipPath, color = pipColor, style = Fill)
        }
    }
}

/**
 * Standalone single-die 3D composable for individual use.
 */
@Composable
fun Dice3D(
    modifier: Modifier = Modifier,
    diceSize: Dp = 100.dp,
    rollDurationMillis: Int = 900,
    targetValue: Int? = null,
    onRollFinished: ((Int) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var isRolling by remember { mutableStateOf(false) }
    var currentTarget by remember { mutableStateOf(targetValue ?: 1) }

    val rotX = remember { Animatable(22f) }
    val rotY = remember { Animatable(-18f) }
    val rotZ = remember { Animatable(0f) }
    val jump = remember { Animatable(0f) }

    val facePath = remember { Path() }
    val pipPath = remember { Path() }

    val context = LocalContext.current
    val soundManager = remember { DiceSoundManager(context) }

    DisposableEffect(soundManager) {
        onDispose {
            soundManager.release()
        }
    }

    fun triggerRoll(specifiedTarget: Int = Random.nextInt(1, 7)) {
        if (isRolling) return
        isRolling = true
        soundManager.playDiceRoll()
        currentTarget = specifiedTarget

        val (baseX, baseY) = baseAnglesForFace(specifiedTarget)
        val nextX = (rotX.value - (rotX.value % 360f)) + 360f * Random.nextInt(3, 5) + baseX + 22f
        val nextY = (rotY.value - (rotY.value % 360f)) + 360f * Random.nextInt(3, 5) + baseY - 18f
        val nextZ = (rotZ.value - (rotZ.value % 360f)) + 360f * Random.nextInt(2, 4)

        val rollEasing = CubicBezierEasing(0.12f, 0.82f, 0.28f, 1f)

        scope.launch {
            coroutineScope {
                launch {
                    jump.snapTo(0f)
                    jump.animateTo(1f, tween(rollDurationMillis, easing = rollEasing))
                }
                launch { rotX.animateTo(nextX, tween(rollDurationMillis, easing = rollEasing)) }
                launch { rotY.animateTo(nextY, tween(rollDurationMillis, easing = rollEasing)) }
                launch { rotZ.animateTo(nextZ, tween(rollDurationMillis, easing = rollEasing)) }
            }
            isRolling = false
            onRollFinished?.invoke(specifiedTarget)
        }
    }

    LaunchedEffect(targetValue) {
        targetValue?.let { triggerRoll(it) }
    }

    Canvas(
        modifier = modifier
            .size(diceSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !isRolling,
                onClick = { triggerRoll() }
            )
    ) {
        val halfSize = diceSize.toPx() * 0.38f
        val arc = bounceArc(jump.value)
        val scale = 1f + 0.24f * arc
        val lift = -halfSize * 1.15f * arc
        val center = Offset(size.width / 2f, size.height / 2f + lift)

        drawDieShadow(center = center, halfSize = halfSize, jumpArc = arc)
        drawOptimizedCube(
            rx = rotX.value,
            ry = rotY.value,
            rz = rotZ.value,
            scale = scale,
            centerOffset = center,
            halfSize = halfSize,
            facePath = facePath,
            pipPath = pipPath
        )
    }
}
