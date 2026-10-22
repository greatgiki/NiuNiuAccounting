package com.example.localledger.widget

import android.content.Context
import com.example.localledger.core.storage.SettingsStore
import com.example.localledger.data.local.database.DatabaseProvider
import com.example.localledger.domain.time.ZoneIdProvider
import com.example.localledger.security.AppLockStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun databaseProvider(): DatabaseProvider
    fun settingsStore(): SettingsStore
    fun appLockStore(): AppLockStore
    fun zoneIdProvider(): ZoneIdProvider
}

internal fun widgetEntryPoint(context: Context): WidgetEntryPoint {
    return EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
}
