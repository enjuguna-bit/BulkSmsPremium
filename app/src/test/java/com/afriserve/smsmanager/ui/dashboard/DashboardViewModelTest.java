package com.afriserve.smsmanager.ui.dashboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.afriserve.smsmanager.data.entity.SmsEntity;
import com.afriserve.smsmanager.data.repository.SmsRepository;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.reactivex.rxjava3.core.Single;

public class DashboardViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Test
    public void loadDashboardData_emitsContentWithActivity() throws Exception {
        SmsRepository repository = Mockito.mock(SmsRepository.class);
        List<SmsEntity> activity = new ArrayList<>();
        activity.add(createSms(1L, "254700000001", "Hello one", System.currentTimeMillis()));
        activity.add(createSms(2L, "254700000002", "Hello two", System.currentTimeMillis()));

        SmsRepository.DashboardSnapshot snapshot = new SmsRepository.DashboardSnapshot(
                12,
                10,
                1,
                1,
                6,
                100.0f,
                activity
        );
        when(repository.getDashboardSnapshot(anyInt())).thenReturn(Single.just(snapshot));

        DashboardViewModel viewModel = new DashboardViewModel(repository);
        viewModel.loadDashboardData();

        DashboardViewModel.DashboardState state = getOrAwaitValue(viewModel.getDashboardState());
        DashboardStats stats = getOrAwaitValue(viewModel.getDashboardStats());

        assertEquals(DashboardViewModel.DashboardState.CONTENT, state);
        assertNotNull(stats);
        assertNotNull(stats.getRecentActivity());
        assertEquals(2, stats.getRecentActivity().size());
        assertEquals(12, stats.getSmsStats().getTotalSent());
    }

    @Test
    public void loadDashboardData_permissionDenied_setsPermissionState() throws Exception {
        SmsRepository repository = Mockito.mock(SmsRepository.class);
        when(repository.getDashboardSnapshot(anyInt()))
                .thenReturn(Single.error(new SecurityException("READ_SMS permission not granted")));

        DashboardViewModel viewModel = new DashboardViewModel(repository);
        viewModel.loadDashboardData();

        DashboardViewModel.DashboardState state = getOrAwaitValue(viewModel.getDashboardState());
        assertEquals(DashboardViewModel.DashboardState.PERMISSION_REQUIRED, state);
    }

    @Test
    public void loadDashboardData_emptySnapshot_setsEmptyState() throws Exception {
        SmsRepository repository = Mockito.mock(SmsRepository.class);
        SmsRepository.DashboardSnapshot snapshot = new SmsRepository.DashboardSnapshot(
                0,
                0,
                0,
                0,
                0,
                0.0f,
                new ArrayList<>()
        );
        when(repository.getDashboardSnapshot(anyInt())).thenReturn(Single.just(snapshot));

        DashboardViewModel viewModel = new DashboardViewModel(repository);
        viewModel.loadDashboardData();

        DashboardViewModel.DashboardState state = getOrAwaitValue(viewModel.getDashboardState());
        DashboardStats stats = getOrAwaitValue(viewModel.getDashboardStats());

        assertEquals(DashboardViewModel.DashboardState.EMPTY, state);
        assertTrue(stats.isEmpty());
    }

    private static SmsEntity createSms(long id, String phone, String message, long createdAt) {
        SmsEntity entity = new SmsEntity();
        entity.id = id;
        entity.phoneNumber = phone;
        entity.message = message;
        entity.createdAt = createdAt;
        entity.status = "SENT";
        entity.boxType = 2;
        entity.isRead = true;
        entity.threadId = id;
        return entity;
    }

    private static <T> T getOrAwaitValue(LiveData<T> liveData) throws Exception {
        final Object[] data = new Object[1];
        final CountDownLatch latch = new CountDownLatch(1);

        Observer<T> observer = new Observer<T>() {
            @Override
            public void onChanged(T value) {
                data[0] = value;
                latch.countDown();
                liveData.removeObserver(this);
            }
        };

        liveData.observeForever(observer);

        if (!latch.await(2, TimeUnit.SECONDS)) {
            throw new TimeoutException("LiveData value was never set");
        }

        @SuppressWarnings("unchecked")
        T result = (T) data[0];
        return result;
    }
}

