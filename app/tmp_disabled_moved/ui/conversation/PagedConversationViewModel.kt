package com.bulksms.smsmanager.ui.conversation

import androidx.lifecycle.ViewModel
import com.bulksms.smsmanager.data.repository.SmsRepository
import com.bulksms.smsmanager.data.repository.ConversationRepository
import com.bulksms.smsmanager.data.conversation.ConversationCacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PagedConversationViewModel @Inject constructor(
    private val smsRepository: SmsRepository,
    private val conversationRepository: ConversationRepository,
    private val cacheManager: ConversationCacheManager
) : ViewModel() {
    // Empty for now to isolate build issue
}
