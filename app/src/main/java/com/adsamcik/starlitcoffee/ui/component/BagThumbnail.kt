package com.adsamcik.starlitcoffee.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.util.ThumbnailLoader

/**
 * Async, downsampled bag-photo thumbnail.
 *
 * Decodes the JPEG and applies EXIF rotation off the main thread (via
 * [ThumbnailLoader] → [androidx.compose.runtime.produceState]) and reserves
 * the layout slot up-front using a [Spacer] while the bitmap loads, so the
 * surrounding layout doesn't reflow when the bitmap arrives.
 *
 * Replaces 3× duplicated `remember { BitmapFactory.decodeFile(...) }` blocks
 * in `BagCard`, `BagDetailSheet`, and `AddBagSheet`. New thumbnail call sites
 * should prefer this composable.
 *
 * Two overloads:
 *  * Square form — [BagThumbnail(uri, size, modifier, …)] — pass a single
 *    [Dp] for both layout sizing and the downsample target.
 *  * Free-form — pass any [modifier] (e.g. `fillMaxWidth().height(168.dp)`)
 *    plus a [downsampleTarget] [Dp] hint that drives `BitmapFactory.inSampleSize`
 *    so the loader doesn't decode a full 12 MP camera frame for a small slot.
 */
@Composable
fun BagThumbnail(
    uri: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    contentDescription: String? = null,
) {
    BagThumbnail(
        uri = uri,
        modifier = modifier.size(size),
        downsampleTarget = size,
        shape = shape,
        contentDescription = contentDescription,
    )
}

@Composable
fun BagThumbnail(
    uri: String,
    modifier: Modifier,
    downsampleTarget: Dp,
    shape: Shape = MaterialTheme.shapes.medium,
    contentDescription: String? = null,
) {
    val resolvedDescription = contentDescription
        ?: stringResource(R.string.cd_bag_photo)
    val targetPx = with(LocalDensity.current) { downsampleTarget.roundToPx() }

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = uri,
        key2 = targetPx,
    ) {
        value = ThumbnailLoader.loadThumbnailFromUri(uri, targetPx)
    }

    val slotModifier = modifier.clip(shape)

    bitmap?.let { resolved ->
        Image(
            bitmap = resolved.asImageBitmap(),
            contentDescription = resolvedDescription,
            modifier = slotModifier,
            contentScale = ContentScale.Crop,
        )
    } ?: Spacer(modifier = slotModifier)
}
