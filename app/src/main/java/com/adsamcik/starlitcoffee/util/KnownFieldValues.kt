package com.adsamcik.starlitcoffee.util

data class KnownFieldValues(
    val names: List<String> = emptyList(),
    val roasters: List<String> = emptyList(),
    val origins: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val varieties: List<String> = emptyList(),
    val processTypes: List<String> = emptyList(),
    val roastLevels: List<String> = emptyList(),
    val farms: List<String> = emptyList(),
) {
    companion object {
        val EMPTY = KnownFieldValues()
    }
}
