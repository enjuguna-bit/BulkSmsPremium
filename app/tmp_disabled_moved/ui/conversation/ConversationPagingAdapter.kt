package com.bulksms.smsmanager.ui.conversation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.AsyncListDiffer

import com.bulksms.smsmanager.data.entity.SmsEntity
import com.bulksms.smsmanager.databinding.ItemConversationMessageBinding

import java.text.SimpleDateFormat
import java.util.*

/**
 * Paging adapter for conversation messages with performance optimization
 * Handles large conversation lists efficiently with AsyncListDiffer
 */
class ConversationPagingAdapter(
    private val onMessageClick: (SmsEntity) -> Unit,
    private val onMessageLongClick: (SmsEntity) -> Boolean
) : PagingDataAdapter<SmsEntity, ConversationPagingAdapter.MessageViewHolder>(MESSAGE_COMPARATOR) {
    
    // AsyncListDiffer for better performance
    private val differ = AsyncListDiffer(this, MESSAGE_COMPARATOR)
    
    // Date formatting
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemConversationMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        if (message != null) {
            holder.bind(message)
        }
    }
    
    /**
     * ViewHolder for conversation messages
     */
    inner class MessageViewHolder(
        private val binding: ItemConversationMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: SmsEntity) {
            val isSent = message.getType() == "SENT"
            
            if (isSent) {
                binding.layoutSent.visibility = android.view.View.VISIBLE
                binding.layoutReceived.visibility = android.view.View.GONE
                
                binding.tvSentMessage.text = message.getBody() ?: ""
                binding.tvSentTime.text = formatMessageTime(message.getDate())
                
                if (message.isUnread()) {
                     binding.tvSentMessage.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                     binding.tvSentMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            } else {
                binding.layoutSent.visibility = android.view.View.GONE
                binding.layoutReceived.visibility = android.view.View.VISIBLE
                
                binding.tvReceivedMessage.text = message.getBody() ?: ""
                binding.tvReceivedTime.text = formatMessageTime(message.getDate())
                
                if (message.isUnread()) {
                     binding.tvReceivedMessage.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                     binding.tvReceivedMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
            }
            
            // Set click listeners
            binding.root.setOnClickListener {
                onMessageClick(message)
            }
            
            binding.root.setOnLongClickListener {
                onMessageLongClick(message)
            }
        }
        
        private fun formatMessageTime(timestamp: Long): String {
            val messageDate = Date(timestamp)
            val today = Calendar.getInstance()
            val messageCalendar = Calendar.getInstance().apply { time = messageDate }
            
            return when {
                isSameDay(messageCalendar, today) -> {
                    "Today " + timeFormat.format(messageDate)
                }
                isYesterday(messageCalendar, today) -> {
                    "Yesterday " + timeFormat.format(messageDate)
                }
                isThisWeek(messageCalendar, today) -> {
                    val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
                    dayFormat.format(messageDate) + " " + timeFormat.format(messageDate)
                }
                else -> {
                    dateFormat.format(messageDate)
                }
            }
        }
        
        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                   cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }
        
        private fun isYesterday(cal: Calendar, today: Calendar): Boolean {
            val yesterday = Calendar.getInstance().apply {
                time = today.time
                add(Calendar.DAY_OF_YEAR, -1)
            }
            return isSameDay(cal, yesterday)
        }
        
        private fun isThisWeek(cal: Calendar, today: Calendar): Boolean {
            val weekAgo = Calendar.getInstance().apply {
                time = today.time
                add(Calendar.DAY_OF_YEAR, -7)
            }
            return cal.after(weekAgo) && cal.before(today)
        }
    }
    
    companion object {
        private val MESSAGE_COMPARATOR = object : DiffUtil.ItemCallback<SmsEntity>() {
            override fun areItemsTheSame(oldItem: SmsEntity, newItem: SmsEntity): Boolean {
                return oldItem.id == newItem.id
            }
            
            override fun areContentsTheSame(oldItem: SmsEntity, newItem: SmsEntity): Boolean {
                return oldItem == newItem
            }
            
            override fun getChangePayload(oldItem: SmsEntity, newItem: SmsEntity): Any? {
                return when {
                    oldItem.getBody() != newItem.getBody() -> "content"
                    oldItem.isUnread() != newItem.isUnread() -> "read_status"
                    oldItem.getType() != newItem.getType() -> "message_type"
                    else -> null
                }
            }
        }
    }
}
