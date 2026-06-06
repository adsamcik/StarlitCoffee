package com.adsamcik.starlitcoffee.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Runs a block of repository writes inside a single database transaction so a
 * multi-table operation (e.g. inserting a brew log AND decrementing/rotating
 * the coffee bag inventory) is applied atomically — either all writes commit or
 * none do.
 *
 * Injected into ViewModels so they can group cross-repository writes without
 * depending on Room or DAOs directly (keeping the "repositories over DAOs"
 * boundary). Production wires [room]; unit tests use [Direct], which simply
 * runs the block (fakes have no transaction semantics).
 */
interface TransactionRunner {
    suspend fun <R> runInTransaction(block: suspend () -> R): R

    /** Convenience so call sites read as `transactionRunner { ... }`. */
    suspend operator fun <R> invoke(block: suspend () -> R): R = runInTransaction(block)

    companion object {
        /** No-op runner: executes the block directly. Default for tests. */
        val Direct: TransactionRunner = object : TransactionRunner {
            override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
        }

        /** Real transaction runner backed by a Room database. */
        fun room(database: RoomDatabase): TransactionRunner = object : TransactionRunner {
            override suspend fun <R> runInTransaction(block: suspend () -> R): R =
                database.withTransaction { block() }
        }
    }
}
