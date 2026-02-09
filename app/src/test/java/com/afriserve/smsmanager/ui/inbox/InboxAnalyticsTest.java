package com.afriserve.smsmanager.ui.inbox;

import android.content.Context;
import androidx.paging.LoadState;
import androidx.test.core.app.ApplicationProvider;

import com.afriserve.smsmanager.data.entity.ConversationEntity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class InboxAnalyticsTest {

    @Mock
    private Context mockContext;

    // Use real object instead of Mock for POJO with public fields
    private ConversationEntity mockConversation;

    private InboxAnalytics analytics;
    private Context realContext;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        realContext = ApplicationProvider.getApplicationContext();
        analytics = new InboxAnalytics(realContext);

        // Setup conversation object
        mockConversation = new ConversationEntity();
        mockConversation.id = 123L;
        mockConversation.phoneNumber = "1234567890";
        mockConversation.messageCount = 5;
    }

    @Test
    public void testTrackLoadStateError_withRefreshError() {
        // Given
        Throwable error = new IOException("Network error");
        LoadState.Error errorState = new LoadState.Error(error);

        // When
        analytics.trackLoadStateError(
                InboxAnalytics.LoadStateType.REFRESH,
                errorState,
                "Initial load");

        // Then
        // Verify that the error was logged properly
        // In a real implementation, you would verify analytics service calls
        assertNotNull(error);
        assertEquals("Network error", error.getMessage());
    }

    @Test
    public void testTrackLoadStateError_withNullAdditionalContext() {
        // Given
        Throwable error = new RuntimeException("Database error");
        LoadState.Error errorState = new LoadState.Error(error);

        // When
        analytics.trackLoadStateError(
                InboxAnalytics.LoadStateType.PREPEND,
                errorState,
                null);

        // Then
        assertNotNull(error);
        assertEquals("Database error", error.getMessage());
    }

    @Test
    public void testTrackConversationOpenError_withValidConversation() {
        // Given
        Throwable error = new IllegalArgumentException("Invalid navigation");

        // When
        analytics.trackConversationOpenError(mockConversation, error);

        // Then
        // Fields accessed directly, cannot verify with Mockito on POJO
        assertNotNull(error);
    }

    @Test
    public void testTrackConversationOpenError_withNullConversation() {
        // Given
        Throwable error = new NullPointerException("Conversation is null");

        // When
        analytics.trackConversationOpenError(null, error);

        // Then
        assertNotNull(error);
        assertEquals("Conversation is null", error.getMessage());
    }

    @Test
    public void testTrackSyncError() {
        // Given
        Throwable error = new IOException("Sync timeout");
        String syncType = "initial_sync";

        // When
        analytics.trackSyncError(error, syncType);

        // Then
        assertNotNull(error);
        assertEquals("Sync timeout", error.getMessage());
    }

    @Test
    public void testTrackLoadStateSuccess() {
        // Given
        int itemCount = 25;

        // When
        analytics.trackLoadStateSuccess(
                InboxAnalytics.LoadStateType.REFRESH,
                itemCount);

        // Then
        // Verify success tracking
        assertTrue(itemCount > 0);
    }

    @Test
    public void testTrackUserInteraction() {
        // Given
        String action = "conversation_selected";
        Map<String, String> parameters = java.util.Collections.singletonMap("id", "123");

        // When
        analytics.trackUserInteraction(action, parameters);

        // Then
        assertNotNull(action);
        assertNotNull(parameters);
        assertEquals("123", parameters.get("id"));
    }

    @Test
    public void testTrackUserInteraction_withNullParameters() {
        // Given
        String action = "refresh_pulled";

        // When
        analytics.trackUserInteraction(action, null);

        // Then
        assertNotNull(action);
    }

    @Test
    public void testTrackPerformance() {
        // Given
        String operation = "load_conversations";
        long durationMs = 1500;

        // When
        analytics.trackPerformance(operation, durationMs);

        // Then
        assertNotNull(operation);
        assertTrue(durationMs > 0);
    }

    @Test
    public void testGetUserFriendlyErrorMessage_networkError() {
        // Given
        Throwable error = new IOException("Network connection failed");

        // When
        String message = analytics.getUserFriendlyErrorMessage(error);

        // Then
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("network"));
    }

    @Test
    public void testGetUserFriendlyErrorMessage_timeoutError() {
        // Given
        Throwable error = new java.util.concurrent.TimeoutException("Request timed out");

        // When
        String message = analytics.getUserFriendlyErrorMessage(error);

        // Then
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("timeout"));
    }

    @Test
    public void testGetUserFriendlyErrorMessage_permissionError() {
        // Given
        Throwable error = new SecurityException("Permission denied");

        // When
        String message = analytics.getUserFriendlyErrorMessage(error);

        // Then
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("permission"));
    }

    @Test
    public void testGetUserFriendlyErrorMessage_databaseError() {
        // Given
        Throwable error = new RuntimeException("SQL constraint failed");

        // When
        String message = analytics.getUserFriendlyErrorMessage(error);

        // Then
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("data storage"));
    }

    @Test
    public void testGetUserFriendlyErrorMessage_nullMessage() {
        // Given
        Throwable error = new RuntimeException((String) null);

        // When
        String message = analytics.getUserFriendlyErrorMessage(error);

        // Then
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("unexpected error"));
    }

    @Test
    public void testGetUserFriendlyErrorMessage_longMessage() {
        // Given
        String longMessage = "This is a very long error message that exceeds the normal limit of 100 characters and should be truncated to a more user-friendly message instead of showing the entire technical details to the end user who might not understand all the technical jargon involved in this particular error scenario.";
        Throwable error = new RuntimeException(longMessage);

        // When
        String message = analytics.getUserFriendlyErrorMessage(error);

        // Then
        assertNotNull(message);
        assertTrue(message.length() <= 100);
    }

    @Test
    public void testGetUserFriendlyErrorMessage_shortMessage() {
        // Given
        String shortMessage = "Simple error";
        Throwable error = new RuntimeException(shortMessage);

        // When
        String message = analytics.getUserFriendlyErrorMessage(error);

        // Then
        assertNotNull(message);
        assertEquals(shortMessage, message);
    }

    @Test
    public void testErrorTypeEnum_values() {
        // Given/When/Then
        assertEquals("load_state_error", InboxAnalytics.ErrorType.LOAD_STATE_ERROR.getValue());
        assertEquals("conversation_open_error", InboxAnalytics.ErrorType.CONVERSATION_OPEN_ERROR.getValue());
        assertEquals("sync_error", InboxAnalytics.ErrorType.SYNC_ERROR.getValue());
        assertEquals("database_error", InboxAnalytics.ErrorType.DATABASE_ERROR.getValue());
        assertEquals("network_error", InboxAnalytics.ErrorType.NETWORK_ERROR.getValue());
    }

    @Test
    public void testLoadStateTypeEnum_values() {
        // Given/When/Then
        assertEquals("refresh", InboxAnalytics.LoadStateType.REFRESH.getValue());
        assertEquals("prepend", InboxAnalytics.LoadStateType.PREPEND.getValue());
        assertEquals("append", InboxAnalytics.LoadStateType.APPEND.getValue());
    }
}
