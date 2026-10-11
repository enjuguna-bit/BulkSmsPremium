package com.afriserve.smsmanager.data.dao

import androidx.room.rxjava3.EmptyResultSetException
import com.afriserve.smsmanager.data.entity.ScheduledCampaignEntity
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.await

/**
 * Suspend extension functions for [ScheduledCampaignDao].
 *
 * These wrappers convert the RxJava 3 [Single] / [Completable] return types to
 * Kotlin suspend functions so that callers (e.g. [BulkSmsSendingWorker]) can
 * call them from a coroutine without resorting to blockingGet / blockingAwait,
 * which can deadlock when the RxJava IO thread pool is saturated by a bulk send.
 *
 * Requires in app/build.gradle (same version variable as coroutines):
 *   implementation "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:$coroutines_version"
 */

/** Returns null instead of throwing when no matching campaign is found. */
suspend fun ScheduledCampaignDao.getScheduledCampaignByCampaignIdSuspend(
    campaignId: Long
): ScheduledCampaignEntity? = getScheduledCampaignByCampaignId(campaignId).awaitOrNull()

suspend fun ScheduledCampaignDao.updateScheduledCampaignSuspend(
    entity: ScheduledCampaignEntity
) = updateScheduledCampaign(entity).await()

private suspend fun <T : Any> Single<T>.awaitOrNull(): T? =
    try {
        await()
    } catch (e: EmptyResultSetException) {
        null
    }
