package com.adsamcik.starlitcoffee.util

/** Ordered source photos retained with a pending bag scan for user review. */
object BagPhotoReviewUris {
    fun parse(capturedPhotoUris: String?): List<String> =
        capturedPhotoUris
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
}
