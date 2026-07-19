package com.adsamcik.starlitcoffee.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import com.adsamcik.starlitcoffee.R
import com.adsamcik.starlitcoffee.data.model.BrewMethod
import com.adsamcik.starlitcoffee.data.model.FilterType

@Composable
fun BrewMethod.localizedDisplayName(): String =
    stringArrayResource(R.array.brew_method_names)[ordinal]

@Composable
fun FilterType.localizedDisplayName(): String =
    stringArrayResource(R.array.filter_type_names)[ordinal]

@Composable
fun FilterType.localizedCupProfile(): String =
    stringArrayResource(R.array.filter_cup_profiles)[ordinal]
