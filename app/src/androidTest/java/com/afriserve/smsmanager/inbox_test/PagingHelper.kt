package com.afriserve.smsmanager.ui.inbox

import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.afriserve.smsmanager.data.entity.SmsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Helper class for Paging 3 interop with Java
 * Converts PagingSource to Flow<PagingData> and then to LiveData
 */
object PagingHelper {
    
    private const val PAGE_SIZE = 50
    
    /**
     * Convert PagingSource to Flow<PagingData>
     */
    fun <T : Any> pagingSourceToFlow(pagingSource: () -> PagingSource<Int, T>): Flow<PagingData<T>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false,
                prefetchDistance = PAGE_SIZE / 2
            ),
            pagingSourceFactory = pagingSource
        ).flow
    }
    
    /**
     * Convert PagingSource to LiveData<PagingData>
     * For use in Java ViewModels
     */
    fun <T : Any> pagingSourceToLiveData(pagingSource: () -> PagingSource<Int, T>): LiveData<PagingData<T>> {
        return pagingSourceToFlow(pagingSource).asLiveData()
    }
}
