package com.adsamcik.starlitcoffee.util

import android.annotation.SuppressLint
import android.content.SharedPreferences

/**
 * Commits preference changes synchronously when durable state must be visible before continuing.
 *
 * AndroidX's [androidx.core.content.edit] intentionally discards the boolean returned by
 * [SharedPreferences.Editor.commit]. Callers that need to detect persistence failure therefore
 * cross this single, documented platform boundary instead of duplicating suppressed editor code.
 */
@SuppressLint("ApplySharedPref", "UseKtx")
inline fun SharedPreferences.commitSynchronously(
    changes: SharedPreferences.Editor.() -> Unit,
): Boolean = edit().apply(changes).commit()
