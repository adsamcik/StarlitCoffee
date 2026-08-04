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
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetGeometry
import com.adsamcik.starlitcoffee.ui.guidance.InstructionAssetRecord

/**
 * Renders a locally packaged instructional illustration after its review gate
 * has passed. Draft, pending, rejected, and retired records intentionally
 * produce no visual output.
 *
 * The manifest requires a 4:3 source composition. Reserving the same 4:3
 * viewport while using [ContentScale.Fit] keeps the complete instructional
 * geometry visible even if a source has unexpected edge content. The asset's
 * localized alt text remains the sole accessibility description.
 */
@Composable
fun ApprovedInstructionAssetImage(
    asset: InstructionAssetRecord,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (!asset.review.isApproved) return
    require(contentDescription.isNotBlank()) {
        "Approved instructional art requires localized accessibility text"
    }

    Image(
        painter = painterResource(asset.drawableRes),
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(
                InstructionAssetGeometry.ASPECT_WIDTH.toFloat() /
                    InstructionAssetGeometry.ASPECT_HEIGHT,
            )
            .clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Fit,
    )
}
