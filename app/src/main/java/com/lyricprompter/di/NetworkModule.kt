package com.lyricprompter.di

import com.lyricprompter.data.remote.bpm.GetSongBpmApi
import com.lyricprompter.data.remote.lyrics.GeniusApi
import com.lyricprompter.data.remote.lyrics.LrcLibApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val LRCLIB_BASE_URL = "https://lrclib.net/"
    private const val GENIUS_BASE_URL = "https://api.genius.com/"
    private const val GETSONGBPM_BASE_URL = "https://api.getsong.co/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("lrclib")
    fun provideLrcLibRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(LRCLIB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @Named("genius")
    fun provideGeniusRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(GENIUS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideLrcLibApi(@Named("lrclib") retrofit: Retrofit): LrcLibApi {
        return retrofit.create(LrcLibApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeniusApi(@Named("genius") retrofit: Retrofit): GeniusApi {
        return retrofit.create(GeniusApi::class.java)
    }

    @Provides
    @Singleton
    @Named("getsongbpm")
    fun provideGetSongBpmRetrofit(): Retrofit {
        // Create a separate OkHttp client with browser-like headers for GetSongBPM
        // to help bypass Cloudflare protection
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", "https://getsongbpm.com/")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(GETSONGBPM_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideGetSongBpmApi(@Named("getsongbpm") retrofit: Retrofit): GetSongBpmApi {
        return retrofit.create(GetSongBpmApi::class.java)
    }
}
