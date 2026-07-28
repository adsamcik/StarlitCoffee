package com.adsamcik.starlitcoffee.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetGeometry
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord

/**
 * Renders a locally packaged instructional illustration after its review gate
 * has passed. Draft, pending, rejected, and retired records intentionally
 * produce no visual output.
 *
 * The manifest requires a 4:3 source composition. Reserving the same 4:3
 * viewport keeps the safe crop region stable while [ContentScale.Crop] fills
 * the available card width. The asset's localized alt text remains the sole
 * accessibility description for the illustration.
 */
@Composable
fun ApprovedInstructionAssetImage(
    asset: InstructionAssetRecord,
    modifier: Modifier = Modifier,
) {
    if (!asset.review.isApproved) return

    Image(
        painter = painterResource(asset.drawableRes),
        contentDescription = stringResource(asset.altTextRes),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(
                InstructionAssetGeometry.ASPECT_WIDTH.toFloat() /
                    InstructionAssetGeometry.ASPECT_HEIGHT,
            )
            .clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Crop,
    )
}
