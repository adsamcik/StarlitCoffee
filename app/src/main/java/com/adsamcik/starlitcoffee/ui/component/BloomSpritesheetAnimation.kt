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
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlin.math.roundToInt
import kotlin.random.Random

private const val BloomFrameSizePx = 256
private const val BloomProgressTweenMillis = 1_000

data class BloomSpritesheetOption(
    val id: String,
    @DrawableRes val drawableRes: Int,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
)

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
)

@Composable
fun BloomSpritesheetAnimation(
    bloomCountdownSeconds: Int?,
    bloomDurationSeconds: Int,
    isRunning: Boolean = true,
    spritesheetWeights: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val selectedSpritesheetId = rememberSaveable(spritesheetWeights) {
        selectWeightedBloomSpritesheetOption(spritesheetWeights)?.id.orEmpty()
    }
    val selectedOption = BloomSpritesheetOptions.firstOrNull { it.id == selectedSpritesheetId } ?: return
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
    val frameColumns = (image.width / BloomFrameSizePx).coerceAtLeast(1)
    val frameRows = (image.height / BloomFrameSizePx).coerceAtLeast(1)
    val frameCount = frameColumns * frameRows
    val frameIndex = resolveBloomFrameIndex(animatedProgress, frameCount)

    BloomSpritesheetFrame(
        image = image,
        frameIndex = frameIndex,
        frameColumns = frameColumns,
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
    val image = ImageBitmap.imageResource(id = option.drawableRes)
    val frameColumns = (image.width / BloomFrameSizePx).coerceAtLeast(1)
    val frameRows = (image.height / BloomFrameSizePx).coerceAtLeast(1)
    val frameCount = frameColumns * frameRows
    BloomSpritesheetFrame(
        image = image,
        frameIndex = frameCount - 1,
        frameColumns = frameColumns,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
private fun BloomSpritesheetFrame(
    image: ImageBitmap,
    frameIndex: Int,
    frameColumns: Int,
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
        val sourceOffset = IntOffset(
            x = frameIndex % frameColumns * BloomFrameSizePx,
            y = frameIndex / frameColumns * BloomFrameSizePx,
        )
        drawImage(
            image = image,
            srcOffset = sourceOffset,
            srcSize = IntSize(BloomFrameSizePx, BloomFrameSizePx),
            dstOffset = IntOffset.Zero,
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

private fun selectWeightedBloomSpritesheetOption(
    spritesheetWeights: Map<String, Int>,
): BloomSpritesheetOption? {
    val weightedOptions = BloomSpritesheetOptions.mapNotNull { option ->
        val weight = spritesheetWeights[option.id]?.coerceIn(0, 2) ?: 1
        if (weight <= 0) null else option to weight
    }
    val totalWeight = weightedOptions.sumOf { (_, weight) -> weight }
    if (totalWeight <= 0) return null

    var remaining = Random.nextInt(totalWeight)
    weightedOptions.forEach { (option, weight) ->
        if (remaining < weight) return option
        remaining -= weight
    }
    return weightedOptions.last().first
}
