package com.example.localledger.di

import android.content.Context
import com.example.localledger.data.ai.AiCredentialStore
import com.example.localledger.data.ai.AiGateway
import com.example.localledger.data.ai.HttpAiProvider
import com.example.localledger.data.ai.RoomLedgerQuerySource
import com.example.localledger.domain.ai.AiCompleter
import com.example.localledger.domain.ai.AiProvider
import com.example.localledger.domain.ai.LedgerQuerySource
import com.example.localledger.security.KeystoreSecretBox
import com.example.localledger.security.SecretBox
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiCredentialBox

@Module
@InstallIn(SingletonComponent::class)
object AiSecretModule {
    @Provides
    @Singleton
    @AiCredentialBox
    fun aiSecretBox(): SecretBox = KeystoreSecretBox(alias = "nn_ai_credential")

    @Provides
    @Singleton
    fun aiCredentialStore(
        @ApplicationContext context: Context,
        @AiCredentialBox secretBox: SecretBox,
    ): AiCredentialStore = AiCredentialStore(File(context.filesDir, AiCredentialStore.FILE_NAME), secretBox)

    @Provides
    @Singleton
    fun httpAiProvider(): AiProvider = HttpAiProvider()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindsModule {
    @Binds
    @Singleton
    abstract fun aiCompleter(impl: AiGateway): AiCompleter

    @Binds
    @Singleton
    abstract fun ledgerQueries(impl: RoomLedgerQuerySource): LedgerQuerySource
}
