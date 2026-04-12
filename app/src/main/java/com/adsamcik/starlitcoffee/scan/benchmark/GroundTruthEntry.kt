package com.adsamcik.starlitcoffee.scan.benchmark

import kotlinx.serialization.Serializable

@Serializable
data class GroundTruthEntry(
    val bagId: String,
    val photoPath: String?,
    val scannedAt: String,
    val bagDescription: String,
    val material: String?,
    val language: String?,
    val fields: Map<String, GroundTruthField>,
)

@Serializable
data class GroundTruthField(
    val groundTruth: String?,
    val isOnLabel: Boolean,
    val notes: String? = null,
)
