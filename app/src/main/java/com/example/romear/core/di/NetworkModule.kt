package com.example.romear.core.di

import com.example.romear.BuildConfig
import com.example.romear.core.network.FoundationApi
import com.example.romear.core.network.SessionAuthenticator
import com.example.romear.core.security.TokenStore
import com.example.romear.feature.auth.data.AuthApi
import com.example.romear.feature.chat.data.ChatApi
import com.example.romear.feature.discovery.data.DiscoveryApi
import com.example.romear.feature.events.data.EventsApi
import com.example.romear.feature.hello.data.HelloApi
import com.example.romear.feature.pools.data.PoolsApi
import com.example.romear.feature.profile.data.ProfileApi
import com.example.romear.feature.safety.data.SafetyApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("authenticated")
    fun authenticatedRetrofit(store: TokenStore): Retrofit {
        val plain = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build()
        val authenticated = plain.newBuilder()
            .authenticator(SessionAuthenticator(store, plain))
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                store.accessToken()?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }.build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(authenticated)
            .build()
    }

    @Provides
    @Singleton
    fun authApi(@Named("authenticated") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun discoveryApi(@Named("authenticated") retrofit: Retrofit): DiscoveryApi =
        retrofit.create(DiscoveryApi::class.java)

    @Provides
    @Singleton
    fun helloApi(@Named("authenticated") retrofit: Retrofit): HelloApi =
        retrofit.create(HelloApi::class.java)

    @Provides
    @Singleton
    fun chatApi(@Named("authenticated") retrofit: Retrofit): ChatApi =
        retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun poolsApi(@Named("authenticated") retrofit: Retrofit): PoolsApi =
        retrofit.create(PoolsApi::class.java)

    @Provides
    @Singleton
    fun eventsApi(@Named("authenticated") retrofit: Retrofit): EventsApi =
        retrofit.create(EventsApi::class.java)

    @Provides
    @Singleton
    fun safetyApi(@Named("authenticated") retrofit: Retrofit): SafetyApi =
        retrofit.create(SafetyApi::class.java)

    @Provides
    @Singleton
    fun profileApi(@Named("authenticated") retrofit: Retrofit): ProfileApi =
        retrofit.create(ProfileApi::class.java)

    @Provides
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun api(): FoundationApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(75, TimeUnit.SECONDS)
                .build(),
        )
        .build()
        .create(FoundationApi::class.java)
}
