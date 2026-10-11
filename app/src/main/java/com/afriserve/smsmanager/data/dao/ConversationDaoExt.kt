package com.afriserve.smsmanager.data.dao

import androidx.room.rxjava3.EmptyResultSetException
import com.afriserve.smsmanager.data.entity.ConversationEntity
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.await

/**
 * Suspend extension functions for [ConversationDao].
 *
 * Part of the incremental RxJava -> Coroutines migration.
 * Used by receivers, workers, and repository methods that need non-blocking DB access.
 */

suspend fun ConversationDao.getConversationByPhoneNumberSuspend(phone: String): ConversationEntity? =
    getConversationByPhoneNumber(phone).awaitOrNull()

suspend fun ConversationDao.getConversationByThreadIdSuspend(threadId: Long): ConversationEntity? =
    getConversationByThreadId(threadId).awaitOrNull()

suspend fun ConversationDao.insertConversationSuspend(entity: ConversationEntity) =
    insertConversation(entity).await()

suspend fun ConversationDao.updateConversationSuspend(entity: ConversationEntity) =
    updateConversation(entity).await()

suspend fun ConversationDao.deleteConversationSuspend(entity: ConversationEntity) =
    deleteConversation(entity).await()

suspend fun ConversationDao.getUnreadCountSuspend(): Int =
    getUnreadConversationsCount().await()

suspend fun ConversationDao.getTotalCountSuspend(): Int =
    getTotalConversationsCount().await()

private suspend fun <T : Any> Single<T>.awaitOrNull(): T? =
    try {
        await()
    } catch (e: EmptyResultSetException) {
        null
    }
