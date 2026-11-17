package com.nocturne.player.di

import android.content.Context
import androidx.room.Room
import com.nocturne.player.data.db.DownloadDao
import com.nocturne.player.data.db.NocturneDatabase
import com.nocturne.player.data.db.PlaylistDao
import com.nocturne.player.data.db.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): NocturneDatabase =
        Room.databaseBuilder(context, NocturneDatabase::class.java, "nocturne.db")
            .addMigrations(*NocturneDatabase.ALL_MIGRATIONS) // never destructive: the library must survive updates
            .build()

    @Provides fun trackDao(db: NocturneDatabase): TrackDao = db.trackDao()
    @Provides fun playlistDao(db: NocturneDatabase): PlaylistDao = db.playlistDao()
    @Provides fun downloadDao(db: NocturneDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
