package com.adsamcik.starlitcoffee.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.domain.BloomSpritesheetIds
import kotlin.math.roundToInt

private const val BloomFrameSizePx = 256
private const val BloomProgressTweenMillis = 1_000
private const val BloomAnchorAlphaThreshold = 24
private const val BloomAnchorBandHeightPx = 42
private const val BloomMaxFrameCorrectionPx = 18

/**
 * UI-side metadata for a bloom spritesheet.
 *
 * `frameSizePx`, `frameColumns`, `frameRows` lock the expected atlas geometry
 * so the renderer can reject silently-resized exports instead of slicing
 * them into garbage. The defaults match the project's standard 5×5 256 px
 * layout produced by `tools/align_bloom_spritesheets.py`.
 */
data class BloomSpritesheetOption(
    val id: String,
    @DrawableRes val drawableRes: Int,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val frameSizePx: Int = BloomFrameSizePx,
    val frameColumns: Int = DefaultBloomFrameColumns,
    val frameRows: Int = DefaultBloomFrameRows,
)

private const val DefaultBloomFrameColumns = 5
private const val DefaultBloomFrameRows = 5

val BloomSpritesheetOptions = listOf(
    BloomSpritesheetOption(
        id = "coffee_flower",
        drawableRes = R.drawable.bloom_coffee_flower_spritesheet,
        labelRes = R.string.label_bloom_sprite_coffee_flower,
        descriptionRes = R.string.desc_bloom_sprite_coffee_flower,
    ),
    BloomSpritesheetOption(
        id = "coffee_starlit",
        drawableRes = R.drawable.bloom_coffee_starlit_spritesheet,
        labelRes = R.string.label_bloom_sprite_coffee_starlit,
        descriptionRes = R.string.desc_bloom_sprite_coffee_starlit,
    ),
    BloomSpritesheetOption(
        id = "coffee_latte",
        drawableRes = R.drawable.bloom_coffee_latte_spritesheet,
        labelRes = R.string.label_bloom_sprite_coffee_latte,
        descriptionRes = R.string.desc_bloom_sprite_coffee_latte,
    ),
    BloomSpritesheetOption(
        id = "coffee_plant",
        drawableRes = R.drawable.bloom_coffee_plant_spritesheet,
        labelRes = R.string.label_bloom_sprite_coffee_plant,
        descriptionRes = R.string.desc_bloom_sprite_coffee_plant,
    ),
    BloomSpritesheetOption(
        id = "coffee_brew",
        drawableRes = R.drawable.bloom_coffee_brew_spritesheet,
        labelRes = R.string.label_bloom_sprite_coffee_brew,
        descriptionRes = R.string.desc_bloom_sprite_coffee_brew,
    ),
    BloomSpritesheetOption(
        id = "rose",
        drawableRes = R.drawable.bloom_rose_spritesheet,
        labelRes = R.string.label_bloom_sprite_rose,
        descriptionRes = R.string.desc_bloom_sprite_rose,
    ),
    BloomSpritesheetOption(
        id = "lotus",
        drawableRes = R.drawable.bloom_lotus_spritesheet,
        labelRes = R.string.label_bloom_sprite_lotus,
        descriptionRes = R.string.desc_bloom_sprite_lotus,
    ),
    BloomSpritesheetOption(
        id = "sunflower",
        drawableRes = R.drawable.bloom_sunflower_spritesheet,
        labelRes = R.string.label_bloom_sprite_sunflower,
        descriptionRes = R.string.desc_bloom_sprite_sunflower,
    ),
    BloomSpritesheetOption(
        id = "orchid",
        drawableRes = R.drawable.bloom_orchid_spritesheet,
        labelRes = R.string.label_bloom_sprite_orchid,
        descriptionRes = R.string.desc_bloom_sprite_orchid,
    ),
    BloomSpritesheetOption(
        id = "jasmine",
        drawableRes = R.drawable.bloom_jasmine_spritesheet,
        labelRes = R.string.label_bloom_sprite_jasmine,
        descriptionRes = R.string.desc_bloom_sprite_jasmine,
    ),
).also { options ->
    // Guard against the option metadata drifting from the domain ID list:
    // both must be in lockstep so the picker can return any visible flower.
    require(options.map { it.id } == BloomSpritesheetIds) {
        "BloomSpritesheetOptions IDs must match BloomSpritesheetIds"
    }
}

@Composable
fun BloomSpritesheetAnimation(
    bloomCountdownSeconds: Int?,
    bloomDurationSeconds: Int,
    selectedSpritesheetId: String?,
    isRunning: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (selectedSpritesheetId.isNullOrEmpty()) return
    val selectedOption = BloomSpritesheetOptions.firstOrNull { it.id == selectedSpritesheetId } ?: return
    // ImageBitmap.imageResource is itself @Composable and internally remembers
    // the decoded bitmap per resource id, so calling it directly is both the
    // cheap path and the only legal one — wrapping it in remember { } would
    // call a @Composable from a non-@Composable lambda.
    val image = ImageBitmap.imageResource(id = selectedOption.drawableRes)
    val contentDescription = stringResource(R.string.cd_bloom_animation)
    val targetProgress = resolveBloomProgress(
        countdownSeconds = bloomCountdownSeconds,
        durationSeconds = bloomDurationSeconds,
    )
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = if (isRunning && bloomCountdownSeconds != null && targetProgress < 1f) {
            tween(durationMillis = BloomProgressTweenMillis, easing = LinearEasing)
        } else {
            snap()
        },
        label = "BloomSpritesheetProgress",
    )
    // Strict grid validation — bail (rendering nothing) if the image doesn't
    // match the option's declared geometry rather than slicing garbage. This
    // makes mis-imported atlases visible during QA instead of producing a
    // silently broken animation.
    val grid = remember(image, selectedOption) {
        resolveBloomGridOrLog(
            spritesheetId = selectedOption.id,
            imageWidth = image.width,
            imageHeight = image.height,
            frameSizePx = selectedOption.frameSizePx,
            expectedColumns = selectedOption.frameColumns,
            expectedRows = selectedOption.frameRows,
        )
    } ?: return
    val frameCorrections = remember(image, grid) {
        resolveBloomFrameCorrections(image = image, grid = grid)
    }
    val frameIndex = resolveBloomFrameIndex(animatedProgress, grid.frameCount)

    BloomSpritesheetFrame(
        image = image,
        grid = grid,
        frameIndex = frameIndex,
        frameCorrection = frameCorrections.getOrElse(frameIndex) { IntOffset.Zero },
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
fun BloomSpritesheetFinalFramePreview(
    option: BloomSpritesheetOption,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // See BloomSpritesheetAnimation: ImageBitmap.imageResource is @Composable
    // and self-remembers — calling it directly is required here.
    val image = ImageBitmap.imageResource(id = option.drawableRes)
    val grid = remember(image, option) {
        resolveBloomGridOrLog(
            spritesheetId = option.id,
            imageWidth = image.width,
            imageHeight = image.height,
            frameSizePx = option.frameSizePx,
            expectedColumns = option.frameColumns,
            expectedRows = option.frameRows,
        )
    } ?: return
    BloomSpritesheetFrame(
        image = image,
        grid = grid,
        frameIndex = grid.frameCount - 1,
        frameCorrection = IntOffset.Zero,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
private fun BloomSpritesheetFrame(
    image: ImageBitmap,
    grid: BloomGrid,
    frameIndex: Int,
    frameCorrection: IntOffset,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        val destination = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val correction = IntOffset(
            x = (frameCorrection.x * (size.width / grid.frameSizePx)).roundToInt(),
            y = (frameCorrection.y * (size.height / grid.frameSizePx)).roundToInt(),
        )
        val sourceOffset = IntOffset(
            x = grid.sourceLeftOf(frameIndex),
            y = grid.sourceTopOf(frameIndex),
        )
        drawImage(
            image = image,
            srcOffset = sourceOffset,
            srcSize = IntSize(grid.frameSizePx, grid.frameSizePx),
            dstOffset = correction,
            dstSize = destination,
            filterQuality = FilterQuality.Medium,
        )
    }
}

private fun resolveBloomProgress(
    countdownSeconds: Int?,
    durationSeconds: Int,
): Float {
    if (countdownSeconds == null || durationSeconds <= 0) return 0f
    val elapsedSeconds = (durationSeconds - countdownSeconds).coerceIn(0, durationSeconds)
    return elapsedSeconds / durationSeconds.toFloat()
}

private fun resolveBloomFrameIndex(progress: Float, frameCount: Int): Int {
    return (progress * (frameCount - 1)).roundToInt().coerceIn(0, frameCount - 1)
}

private data class BloomFrameAnchor(
    val hasContent: Boolean,
    val anchorX: Float,
    val baselineY: Int,
)

private fun resolveBloomFrameCorrections(
    image: ImageBitmap,
    grid: BloomGrid,
): List<IntOffset> {
    val frameSizePx = grid.frameSizePx
    if (grid.frameCount <= 1) return List(grid.frameCount) { IntOffset.Zero }

    val pixels = IntArray(image.width * image.height)
    image.readPixels(buffer = pixels)

    val anchors = MutableList(grid.frameCount) {
        BloomFrameAnchor(
            hasContent = false,
            anchorX = frameSizePx / 2f,
            baselineY = frameSizePx - 1,
        )
    }
    val validAnchorXs = mutableListOf<Float>()
    val validBaselines = mutableListOf<Int>()
    repeat(grid.frameCount) { frameIndex ->
        val frameX = grid.sourceLeftOf(frameIndex)
        val frameY = grid.sourceTopOf(frameIndex)
        val anchor = sampleBloomFrameAnchor(
            pixels = pixels,
            imageWidth = image.width,
            frameLeft = frameX,
            frameTop = frameY,
            frameSizePx = frameSizePx,
        )
        anchors[frameIndex] = anchor
        if (anchor.hasContent) {
            validAnchorXs += anchor.anchorX
            validBaselines += anchor.baselineY
        }
    }

    val targetAnchorX = medianFloat(validAnchorXs) ?: (frameSizePx / 2f)
    val targetBaseline = medianInt(validBaselines) ?: (frameSizePx - 1)
    return anchors.map { anchor ->
        if (!anchor.hasContent) {
            IntOffset.Zero
        } else {
            IntOffset(
                x = (targetAnchorX - anchor.anchorX)
                    .roundToInt()
                    .coerceIn(-BloomMaxFrameCorrectionPx, BloomMaxFrameCorrectionPx),
                y = (targetBaseline - anchor.baselineY)
                    .coerceIn(-BloomMaxFrameCorrectionPx, BloomMaxFrameCorrectionPx),
            )
        }
    }
}

private fun sampleBloomFrameAnchor(
    pixels: IntArray,
    imageWidth: Int,
    frameLeft: Int,
    frameTop: Int,
    frameSizePx: Int,
): BloomFrameAnchor {
    var minX = frameSizePx
    var minY = frameSizePx
    var maxX = -1
    var maxY = -1
    for (localY in 0 until frameSizePx) {
        val rowStart = (frameTop + localY) * imageWidth + frameLeft
        for (localX in 0 until frameSizePx) {
            val alpha = pixels[rowStart + localX].ushr(24)
            if (alpha >= BloomAnchorAlphaThreshold) {
                minX = minOf(minX, localX)
                minY = minOf(minY, localY)
                maxX = maxOf(maxX, localX)
                maxY = maxOf(maxY, localY)
            }
        }
    }
    if (maxX < 0 || maxY < 0) {
        return BloomFrameAnchor(
            hasContent = false,
            anchorX = frameSizePx / 2f,
            baselineY = frameSizePx - 1,
        )
    }

    val bandTop = maxOf(minY, maxY - BloomAnchorBandHeightPx + 1)
    var total = 0f
    var weightedX = 0f
    for (localY in bandTop..maxY) {
        val rowStart = (frameTop + localY) * imageWidth + frameLeft
        for (localX in minX..maxX) {
            val alpha = pixels[rowStart + localX].ushr(24)
            if (alpha >= BloomAnchorAlphaThreshold) {
                total += alpha.toFloat()
                weightedX += localX * alpha.toFloat()
            }
        }
    }
    val anchorX = if (total > 0f) weightedX / total else (minX + maxX) / 2f
    return BloomFrameAnchor(
        hasContent = true,
        anchorX = anchorX,
        baselineY = maxY,
    )
}

private fun medianFloat(values: List<Float>): Float? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2f
    } else {
        sorted[mid]
    }
}

private fun medianInt(values: List<Int>): Int? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        ((sorted[mid - 1] + sorted[mid]) / 2f).roundToInt()
    } else {
        sorted[mid]
    }
}
