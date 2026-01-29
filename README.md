# Bulk SMS Manager

A premium Android SMS management application with advanced features and modern Material Design UI.

## Features

### Premium UI Design
- Material Design cards and chips
- Clean, modern layout with proper spacing
- Search bar with rounded corners
- Horizontal scrollable filter chips

### Advanced Functionality
- **Paging 3 Integration**: Infinite scrolling for large message lists
- **Pull-to-Refresh**: Manual synchronization with device SMS database
- **Real-time Search**: Search messages with debouncing
- **Multiple Filters**: All, Inbox, Sent, Unread messages
- **Statistics Display**: Real-time total and unread message counts

### User Experience Enhancements
- **Loading States**: Proper progress indicators for all operations
- **Empty State**: User-friendly empty state with illustrations
- **Error Handling**: Comprehensive error handling with user-friendly messages
- **Context Menu**: Long press actions for message operations
- **Visual Feedback**: Bold text for unread messages, opacity changes for read messages

### Performance Optimizations
- **RecyclerView Optimizations**: Fixed size, no animations for better performance
- **Cached Data Flows**: Efficient data caching with Paging 3
- **DiffUtil**: Efficient list updates with proper diffing
- **Lifecycle Management**: Proper lifecycle-aware components

## Architecture

### MVVM with Hilt
- **View Layer**: Fragments with ViewBinding
- **ViewModel**: Business logic with Kotlin Coroutines
- **Repository**: Data layer with Room database
- **Dependency Injection**: Hilt for proper DI

### Key Components

#### InboxFragment
- Main UI component for message list
- Handles user interactions and UI state
- Integrates with ViewModel for data operations

#### InboxViewModel
- Manages message data with Paging 3
- Handles search, filtering, and synchronization
- Provides reactive data streams with StateFlow

#### InboxPagingAdapter
- Efficient RecyclerView adapter with Paging 3
- Smart date formatting (Today, Yesterday, etc.)
- Message type indicators and read/unread styling

#### SmsEntity
- Room entity for SMS messages
- Supports all SMS properties (address, body, date, type, read status)

## Dependencies

### Core Android
- AndroidX libraries (Fragment, Lifecycle, RecyclerView)
- Material Design Components
- ConstraintLayout

### Modern Android
- **Paging 3**: For efficient data loading
- **Room**: Local database with paging support
- **Kotlin Coroutines**: Asynchronous operations
- **Hilt**: Dependency injection
- **ViewBinding**: Type-safe view references

## Project Structure

```
app/src/main/java/com/bulksms/smsmanager/
├── ui/inbox/
│   ├── InboxFragment.java
│   ├── InboxViewModel.java
│   ├── InboxPagingAdapter.java
│   ├── MessageLoadStateAdapter.java
│   ├── InboxUiState.java
│   └── MessageStatistics.java
├── data/
│   ├── entity/
│   │   └── SmsEntity.java
│   └── repository/
│       └── SmsRepository.java
└── databinding/
    ├── FragmentInboxBinding.java
    ├── ItemSmsMessageBinding.java
    └── ItemLoadStateFooterBinding.java
```

## Setup

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Build and run the application

## Permissions

The app requires the following permissions:
- `READ_SMS`: Read SMS messages
- `RECEIVE_SMS`: Receive incoming SMS
- `SEND_SMS`: Send SMS messages
- `READ_CONTACTS`: Access contact information

## Future Enhancements

- Contact integration with avatars
- Message threading
- SMS scheduling
- Backup and restore functionality
- Dark mode support
- Message encryption
- Multi-language support

## License

This project is licensed under the MIT License.
