package com.adsamcik.starlitcoffee.data.work

import android.content.Context
import android.content.SharedPreferences

object BagReviewQueue {
    @Synchronized
    fun enqueue(
        context: Context,
        workId: String,
        reviewContext: BagReviewContext? = null,
    ) {
        rememberReviewContext(context, workId, reviewContext)
        val pending = list(context)
        if (workId in pending) return
        write(context, pending + workId)
    }

    @Synchronized
    fun prioritize(
        context: Context,
        workId: String,
        reviewContext: BagReviewContext? = null,
    ) {
        rememberReviewContext(context, workId, reviewContext)
        write(context, prioritizeReviewWorkId(list(context), workId))
    }

    @Synchronized
    fun remove(context: Context, workId: String) {
        write(context, list(context).filterNot { it == workId })
        forgetReviewContextIfUnowned(context, workId)
    }

    fun list(context: Context): List<String> =
        preferences(context)
            .getString(KEY_PENDING_REVIEW_WORK_IDS, null)
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()

    @Synchronized
    fun retainForeground(
        context: Context,
        workId: String,
        reviewContext: BagReviewContext? = null,
    ) {
        rememberReviewContext(context, workId, reviewContext)
        val retained = retainedForeground(context)
        if (workId in retained) return
        write(context, KEY_RETAINED_FOREGROUND_WORK_IDS, retained + workId)
    }

    @Synchronized
    fun releaseForeground(context: Context, workId: String) {
        write(
            context,
            KEY_RETAINED_FOREGROUND_WORK_IDS,
            retainedForeground(context).filterNot { it == workId },
        )
        forgetReviewContextIfUnowned(context, workId)
    }

    @Synchronized
    fun removeEverywhere(context: Context, workId: String) {
        write(context, list(context).filterNot { it == workId })
        write(
            context,
            KEY_RETAINED_FOREGROUND_WORK_IDS,
            retainedForeground(context).filterNot { it == workId },
        )
        preferences(context).edit().remove(reviewContextKey(workId)).commit()
    }

    fun retainedForeground(context: Context): List<String> =
        preferences(context)
            .getString(KEY_RETAINED_FOREGROUND_WORK_IDS, null)
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()

    fun reviewContext(context: Context, workId: String): BagReviewContext? =
        decodeBagReviewContext(preferences(context).getString(reviewContextKey(workId), null))

    private fun write(
        context: Context,
        key: String,
        workIds: List<String>,
    ) {
        preferences(context)
            .edit()
            .putString(key, workIds.joinToString(","))
            .commit()
    }

    private fun write(context: Context, workIds: List<String>) {
        write(context, KEY_PENDING_REVIEW_WORK_IDS, workIds)
    }

    private fun rememberReviewContext(
        context: Context,
        workId: String,
        reviewContext: BagReviewContext?,
    ) {
        val encoded = encodeBagReviewContext(reviewContext) ?: return
        preferences(context).edit().putString(reviewContextKey(workId), encoded).commit()
    }

    private fun forgetReviewContextIfUnowned(context: Context, workId: String) {
        if (workId in list(context) || workId in retainedForeground(context)) return
        preferences(context).edit().remove(reviewContextKey(workId)).commit()
    }

    private fun reviewContextKey(workId: String): String = "$KEY_REVIEW_CONTEXT_PREFIX$workId"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val PREFERENCES = "bag_scan"
    const val KEY_PENDING_REVIEW_WORK_IDS = "pendingReviewWorkIds"
    private const val KEY_RETAINED_FOREGROUND_WORK_IDS = "retainedForegroundWorkIds"
    private const val KEY_REVIEW_CONTEXT_PREFIX = "reviewContext:"
}

internal fun prioritizeReviewWorkId(pending: List<String>, workId: String): List<String> =
    listOf(workId) + pending.filterNot { it == workId }
