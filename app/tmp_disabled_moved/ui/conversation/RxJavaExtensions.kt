package com.bulksms.smsmanager.ui.conversation

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Extension to await Completable in coroutines
 */
internal suspend fun Completable.awaitCompletable() {
    return suspendCoroutine { cont ->
        subscribe(
            { cont.resume(Unit) },
            { cont.resumeWithException(it) }
        )
    }
}

/**
 * Extension to await Single in coroutines
 */
internal suspend fun <T : Any> Single<T>.awaitSingle(): T {
    return suspendCoroutine { cont ->
        subscribe(
            { result -> cont.resume(result) },
            { error -> cont.resumeWithException(error) }
        )
    }
}
