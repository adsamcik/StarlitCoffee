package com.adsamcik.starlitcoffee.data.repository

/** The idempotent result of completing a session-backed brew log write. */
data class SessionLogWriteResult(
    val logId: Long,
    val wasNew: Boolean,
)
