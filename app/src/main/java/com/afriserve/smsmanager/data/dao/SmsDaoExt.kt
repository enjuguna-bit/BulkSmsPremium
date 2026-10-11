package com.afriserve.smsmanager.data.dao

import androidx.room.rxjava3.EmptyResultSetException
import com.afriserve.smsmanager.data.entity.SmsEntity
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.await

/**
 * Suspend extension functions for [SmsDao].
 *
 * Replaces blockingGet() / blockingAwait() call sites in receivers and
 * service classes. Callers must already be on a background dispatcher
 * (e.g. Dispatchers.IO or a WorkManager coroutine worker).
 *
 * Add new wrappers here as the RxJava -> Coroutines migration progresses.
 */

suspend fun SmsDao.getSmsEntityByIdSuspend(id: Long): SmsEntity? =
    getSmsById(id).awaitOrNull()

suspend fun SmsDao.getSmsByDeviceSmsIdSuspend(deviceSmsId: Long): SmsEntity? =
    getSmsByDeviceSmsId(deviceSmsId).awaitOrNull()

suspend fun SmsDao.insertSmsSuspend(entity: SmsEntity): Long =
    insertSms(entity).await()

suspend fun SmsDao.getTotalSentCountSuspend(): Int =
    getTotalSentCount().await()

suspend fun SmsDao.getTotalDeliveredCountSuspend(): Int =
    getTotalDeliveredCount().await()

suspend fun SmsDao.getTotalFailedCountSuspend(): Int =
    getTotalFailedCount().await()

suspend fun SmsDao.getOutgoingCountInRangeSuspend(startTime: Long, endTime: Long): Int =
    getOutgoingCountInRange(startTime, endTime).await()

private suspend fun <T : Any> Single<T>.awaitOrNull(): T? =
    try {
        await()
    } catch (e: EmptyResultSetException) {
        null
    }
