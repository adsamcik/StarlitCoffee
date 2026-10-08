package com.adsamcik.starlitcoffee.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType

@Composable
fun BrewMethod.localizedDisplayName(): String =
    stringArrayResource(R.array.brew_method_names)[ordinal]

/** Resolve legacy enum labels at presentation time; preserve recipe-authored and unknown labels. */
@Composable
fun localizedBrewMethodLabel(rawLabel: String): String =
    BrewMethod.entries.firstOrNull { it.name == rawLabel }?.localizedDisplayName() ?: rawLabel

/** OS notifications resolve the same stored enum labels without requiring a Compose host. */
internal fun Context.localizedBrewMethodLabel(rawLabel: String): String =
    BrewMethod.entries.firstOrNull { it.name == rawLabel }
        ?.let { resources.getStringArray(R.array.brew_method_names)[it.ordinal] } ?: rawLabel

@Composable
fun FilterType.localizedDisplayName(): String =
    stringArrayResource(R.array.filter_type_names)[ordinal]

@Composable
fun FilterType.localizedCupProfile(): String =
    stringArrayResource(R.array.filter_cup_profiles)[ordinal]
