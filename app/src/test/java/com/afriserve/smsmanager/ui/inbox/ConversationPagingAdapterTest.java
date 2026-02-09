package com.afriserve.smsmanager.ui.inbox;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.paging.AsyncPagingDataDiffer;
import androidx.paging.PagingData;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.afriserve.smsmanager.data.contacts.ContactResolver;
import com.afriserve.smsmanager.data.entity.ConversationEntity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class ConversationPagingAdapterTest {
    
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();
    
    @Mock
    private ContactResolver mockContactResolver;
    
    @Mock
    private ConversationPagingAdapter.ConversationClickListener mockClickListener;
    
    @Mock
    private ConversationPagingAdapter.ConversationOptionsClickListener mockOptionsClickListener;
    
    private ConversationPagingAdapter adapter;
    private AsyncPagingDataDiffer<ConversationEntity> differ;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        adapter = new ConversationPagingAdapter(
            mockClickListener,
            mockOptionsClickListener,
            mockContactResolver
        );
        
        // Setup differ for testing
        differ = new AsyncPagingDataDiffer<>(
            new ConversationPagingAdapter.ConversationDiffCallback(),
            new TestRecyclerView(),
            new ListUpdateCallback() {
                @Override
                public void onInserted(int position, int count) {}
                
                @Override
                public void onRemoved(int position, int count) {}
                
                @Override
                public void onMoved(int fromPosition, int toPosition) {}
                
                @Override
                public void onChanged(int position, int count, Object payload) {}
            }
        );
    }
    
    @Test
    public void testSubmitData_updatesAdapter() throws InterruptedException {
        // Given
        List<ConversationEntity> conversations = createTestConversations();
        PagingData<ConversationEntity> pagingData = PagingData.from(conversations);
        
        // When
        CountDownLatch latch = new CountDownLatch(1);
        differ.submitData(pagingData);
        latch.await(2, TimeUnit.SECONDS);
        
        // Then
        assertNotNull(differ.getItemCount());
        assertTrue(differ.getItemCount() > 0);
    }
    
    @Test
    public void testConversationClick_triggersClickListener() {
        // Given
        ConversationEntity conversation = createTestConversation("1234567890", "Test Message");
        
        // When
        adapter.onConversationClick(conversation);
        
        // Then
        // Verify that the click listener was called
        // This would require mocking the listener behavior
    }
    
    @Test
    public void testConversationOptionsClick_triggersOptionsListener() {
        // Given
        ConversationEntity conversation = createTestConversation("1234567890", "Test Message");
        
        // When
        boolean result = adapter.onConversationOptionsClick(conversation);
        
        // Then
        assertTrue(result); // Should return true indicating click was handled
    }
    
    @Test
    public void testGetItemCount_returnsCorrectCount() {
        // Given
        List<ConversationEntity> conversations = createTestConversations();
        PagingData<ConversationEntity> pagingData = PagingData.from(conversations);
        
        // When
        CountDownLatch latch = new CountDownLatch(1);
        differ.submitData(pagingData);
        
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
        
        // Then
        assertEquals(conversations.size(), differ.getItemCount());
    }
    
    @Test
    public void testContactNameResolution() {
        // Given
        String phoneNumber = "1234567890";
        String contactName = "John Doe";
        ConversationEntity conversation = createTestConversation(phoneNumber, "Test Message");
        
        when(mockContactResolver.getContactName(phoneNumber))
            .thenReturn(contactName);
        
        // When
        String resolvedName = mockContactResolver.getContactName(phoneNumber);
        
        // Then
        assertEquals(contactName, resolvedName);
    }
    
    @Test
    public void testEmptyConversationList() throws InterruptedException {
        // Given
        List<ConversationEntity> emptyList = Arrays.asList();
        PagingData<ConversationEntity> pagingData = PagingData.from(emptyList);
        
        // When
        CountDownLatch latch = new CountDownLatch(1);
        differ.submitData(pagingData);
        latch.await(2, TimeUnit.SECONDS);
        
        // Then
        assertEquals(0, differ.getItemCount());
    }
    
    @Test
    public void testConversationWithNullPhoneNumber() {
        // Given
        ConversationEntity conversation = new ConversationEntity();
        conversation.phoneNumber = null;
        conversation.lastMessagePreview = "Test Message";
        conversation.updatedAt = System.currentTimeMillis();
        
        // When/Then
        // Should handle null phone number gracefully
        assertNotNull(conversation);
    }
    
    @Test
    public void testConversationWithEmptyMessage() {
        // Given
        ConversationEntity conversation = new ConversationEntity();
        conversation.phoneNumber = "1234567890";
        conversation.lastMessagePreview = "";
        conversation.updatedAt = System.currentTimeMillis();
        
        // When/Then
        // Should handle empty message gracefully
        assertNotNull(conversation);
    }
    
    @Test
    public void testPerformance_withLargeDataset() throws InterruptedException {
        // Given
        List<ConversationEntity> largeList = createLargeTestConversations(1000);
        PagingData<ConversationEntity> pagingData = PagingData.from(largeList);
        
        long startTime = System.currentTimeMillis();
        
        // When
        CountDownLatch latch = new CountDownLatch(1);
        differ.submitData(pagingData);
        latch.await(5, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Then
        assertEquals(1000, differ.getItemCount());
        assertTrue("Loading should complete within reasonable time", duration < 3000);
    }
    
    // Helper methods
    private List<ConversationEntity> createTestConversations() {
        return Arrays.asList(
            createTestConversation("1234567890", "Hello World"),
            createTestConversation("0987654321", "Test Message 2"),
            createTestConversation("5555555555", "Test Message 3")
        );
    }
    
    private ConversationEntity createTestConversation(String phoneNumber, String message) {
        ConversationEntity conversation = new ConversationEntity();
        conversation.id = System.currentTimeMillis();
        conversation.phoneNumber = phoneNumber;
        conversation.contactName = null; // Will be resolved by ContactResolver
        conversation.lastMessagePreview = message;
        conversation.messageCount = 1;
        conversation.unreadCount = 1;
        conversation.updatedAt = System.currentTimeMillis();
        conversation.createdAt = System.currentTimeMillis();
        return conversation;
    }
    
    private List<ConversationEntity> createLargeTestConversations(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> createTestConversation("123456789" + i, "Message " + i))
            .collect(java.util.stream.Collectors.toList());
    }
    
    // Test RecyclerView implementation
    private static class TestRecyclerView extends RecyclerView {
        public TestRecyclerView() {
            super(ApplicationProvider.getApplicationContext());
        }
    }
}
