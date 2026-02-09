package com.bulksms.smsmanager.di;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class WorkManagerModule {

    @Provides
    @Singleton
    public static WorkManager provideWorkManager(@ApplicationContext @NonNull Context context) {
        return WorkManager.getInstance(context);
    }
}
