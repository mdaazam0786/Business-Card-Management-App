package com.example.swiftcard.di

import android.content.Context
import com.example.swiftcard.data.repository.BusinessCardRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Singleton


@Module
@InstallIn(ViewModelComponent::class)
object AppModule {

    @Provides
    @ViewModelScoped
    fun provideBusinessCardRepository(): BusinessCardRepository {
        return BusinessCardRepository()
    }


}