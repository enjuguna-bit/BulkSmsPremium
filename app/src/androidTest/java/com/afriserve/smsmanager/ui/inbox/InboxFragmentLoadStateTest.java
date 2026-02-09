package com.afriserve.smsmanager.ui.inbox;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.Lifecycle;
import androidx.paging.LoadState;
import androidx.paging.PagingData;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import com.afriserve.smsmanager.R;
import com.afriserve.smsmanager.data.entity.ConversationEntity;
import com.afriserve.smsmanager.ui.inbox.SimpleInboxViewModel;

import org.junit.After;
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

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.CoreMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class InboxFragmentLoadStateTest {
    
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();
    
    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.RECEIVE_SMS
    );
    
    @Mock
    private SimpleInboxViewModel mockViewModel;
    
    private BehaviorSubject<PagingData<ConversationEntity>> pagingDataSubject;
    private CountingIdlingResource idlingResource;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        pagingDataSubject = BehaviorSubject.create();
        idlingResource = new CountingIdlingResource("LoadStateTest");
        
        // Setup mock ViewModel behavior
        when(mockViewModel.getMessages()).thenReturn(pagingDataSubject);
        when(mockViewModel.getUnreadCount()).thenReturn(Observable.just(0));
        when(mockViewModel.getTotalCount()).thenReturn(Observable.just(0));
        when(mockViewModel.getUiState()).thenReturn(Observable.empty());
    }
    
    @After
    public void tearDown() {
        if (idlingResource != null) {
            IdlingRegistry.getInstance().unregister(idlingResource);
        }
    }
    
    @Test
    public void testLoadStateLoading_showsProgressBar() {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            // Simulate loading state
            idlingResource.increment();
            
            // Trigger loading state
            fragment.showLoadingState();
            
            // Then
            onView(withId(R.id.progressBar))
                .check(ViewAssertions.matches(isDisplayed()));
            
            idlingResource.decrement();
        });
    }
    
    @Test
    public void testLoadStateSuccess_hidesProgressBar() {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            // Simulate success state
            fragment.hideLoadingState();
            
            // Then
            onView(withId(R.id.progressBar))
                .check(ViewAssertions.matches(not(isDisplayed())));
        });
    }
    
    @Test
    public void testLoadStateError_showsErrorMessage() {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            // Simulate error state
            String errorMessage = "Network error occurred";
            fragment.showErrorState(errorMessage);
            
            // Then
            // Check that error message is displayed (you may need to add error TextView to layout)
            onView(withId(R.id.emptyView))
                .check(ViewAssertions.matches(isDisplayed()));
        });
    }
    
    @Test
    public void testSwipeRefresh_triggersLoadState() {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        onView(withId(R.id.swipeRefresh))
            .perform(swipeDown());
        
        // Then
        // Verify that swipe refresh is shown
        onView(withId(R.id.swipeRefresh))
            .check(ViewAssertions.matches(isDisplayed()));
    }
    
    @Test
    public void testEmptyState_showsEmptyView() {
        // Given
        List<ConversationEntity> emptyList = Arrays.asList();
        PagingData<ConversationEntity> emptyPagingData = PagingData.from(emptyList);
        
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            // Submit empty data
            fragment.submitTestData(emptyPagingData);
            
            // Then
            onView(withId(R.id.emptyView))
                .check(ViewAssertions.matches(isDisplayed()));
        });
    }
    
    @Test
    public void testLoadStateTransitions_loadingToSuccess() throws InterruptedException {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            idlingResource.increment();
            
            // Start with loading state
            fragment.showLoadingState();
            
            // Verify loading is shown
            onView(withId(R.id.progressBar))
                .check(ViewAssertions.matches(isDisplayed()));
            
            // Transition to success state
            List<ConversationEntity> conversations = createTestConversations();
            PagingData<ConversationEntity> pagingData = PagingData.from(conversations);
            fragment.submitTestData(pagingData);
            fragment.hideLoadingState();
            
            // Verify loading is hidden
            onView(withId(R.id.progressBar))
                .check(ViewAssertions.matches(not(isDisplayed())));
            
            idlingResource.decrement();
        });
    }
    
    @Test
    public void testLoadStateTransitions_loadingToError() {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            idlingResource.increment();
            
            // Start with loading state
            fragment.showLoadingState();
            
            // Transition to error state
            fragment.showErrorState("Connection failed");
            
            // Verify error handling
            onView(withId(R.id.progressBar))
                .check(ViewAssertions.matches(not(isDisplayed())));
            
            idlingResource.decrement();
        });
    }
    
    @Test
    public void testRetryButton_onLoadStateError() {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            // Simulate error state with retry button
            fragment.showErrorStateWithRetry("Network error", v -> {
                // Retry action
                fragment.showLoadingState();
            });
            
            // Click retry button
            onView(withId(R.id.retryButton))
                .perform(click());
            
            // Then
            // Verify loading state is shown again
            onView(withId(R.id.progressBar))
                .check(ViewAssertions.matches(isDisplayed()));
        });
    }
    
    @Test
    public void testLoadStatePerformance_measuresTime() {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            long startTime = System.currentTimeMillis();
            
            fragment.showLoadingState();
            
            // Simulate data loading
            try {
                Thread.sleep(100); // Simulate network delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            List<ConversationEntity> conversations = createTestConversations();
            PagingData<ConversationEntity> pagingData = PagingData.from(conversations);
            fragment.submitTestData(pagingData);
            fragment.hideLoadingState();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Then
            assertTrue("Load state transition should complete within reasonable time", duration < 5000);
        });
    }
    
    @Test
    public void testLoadState_withLargeDataset() throws InterruptedException {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            idlingResource.increment();
            
            // Create large dataset
            List<ConversationEntity> largeList = createLargeTestConversations(1000);
            PagingData<ConversationEntity> pagingData = PagingData.from(largeList);
            
            long startTime = System.currentTimeMillis();
            fragment.submitTestData(pagingData);
            long endTime = System.currentTimeMillis();
            
            // Then
            assertTrue("Large dataset should load within reasonable time", (endTime - startTime) < 3000);
            
            idlingResource.decrement();
        });
    }
    
    @Test
    public void testLoadState_withConcurrentRequests() throws InterruptedException {
        // Given
        FragmentScenario<InboxFragment> scenario = FragmentScenario.launch(InboxFragment.class);
        
        // When
        scenario.onFragment(fragment -> {
            idlingResource.increment();
            
            // Simulate concurrent load states
            fragment.showLoadingState();
            
            // Submit data while loading
            List<ConversationEntity> conversations = createTestConversations();
            PagingData<ConversationEntity> pagingData = PagingData.from(conversations);
            fragment.submitTestData(pagingData);
            
            // Should handle gracefully without crashing
            fragment.hideLoadingState();
            
            idlingResource.decrement();
        });
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
    
    // Custom IdlingResource for testing
    private static class CountingIdlingResource implements IdlingResource {
        private final String resourceName;
        private volatile int counter = 0;
        private volatile ResourceCallback resourceCallback;
        
        public CountingIdlingResource(String resourceName) {
            this.resourceName = resourceName;
        }
        
        @Override
        public String getName() {
            return resourceName;
        }
        
        @Override
        public boolean isIdleNow() {
            return counter == 0;
        }
        
        @Override
        public void registerIdleTransitionCallback(ResourceCallback callback) {
            this.resourceCallback = callback;
        }
        
        public void increment() {
            counter++;
            if (counter > 0 && resourceCallback != null) {
                resourceCallback.onTransitionToIdle();
            }
        }
        
        public void decrement() {
            counter--;
            if (counter == 0 && resourceCallback != null) {
                resourceCallback.onTransitionToIdle();
            }
        }
    }
}
