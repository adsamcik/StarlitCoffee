package com.adsamcik.starlitcoffee.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

private const val BloomFrameCount = 45
private const val BloomFrameColumns = 9
private const val BloomFrameSizePx = 256

private val BloomSpritesheetResourceIds = intArrayOf(
    R.drawable.bloom_rose_spritesheet,
    R.drawable.bloom_lotus_spritesheet,
    R.drawable.bloom_sunflower_spritesheet,
    R.drawable.bloom_orchid_spritesheet,
    R.drawable.bloom_jasmine_spritesheet,
)

@Composable
fun BloomSpritesheetAnimation(
    bloomCountdownSeconds: Int?,
    bloomDurationSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val sheetIndex = rememberSaveable {
        Random.nextInt(BloomSpritesheetResourceIds.size)
    }
    val image = ImageBitmap.imageResource(id = BloomSpritesheetResourceIds[sheetIndex])
    val contentDescription = stringResource(R.string.cd_bloom_animation)
    val frameIndex = remember(bloomCountdownSeconds, bloomDurationSeconds) {
        resolveBloomFrameIndex(
            countdownSeconds = bloomCountdownSeconds,
            durationSeconds = bloomDurationSeconds,
        )
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val destination = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val sourceOffset = IntOffset(
            x = frameIndex % BloomFrameColumns * BloomFrameSizePx,
            y = frameIndex / BloomFrameColumns * BloomFrameSizePx,
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

private fun resolveBloomFrameIndex(
    countdownSeconds: Int?,
    durationSeconds: Int,
): Int {
    if (countdownSeconds == null || durationSeconds <= 0) return 0
    val elapsedSeconds = (durationSeconds - countdownSeconds).coerceIn(0, durationSeconds)
    val progress = elapsedSeconds / durationSeconds.toFloat()
    return (progress * (BloomFrameCount - 1)).roundToInt().coerceIn(0, BloomFrameCount - 1)
}
