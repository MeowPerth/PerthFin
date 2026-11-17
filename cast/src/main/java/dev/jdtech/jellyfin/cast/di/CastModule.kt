package dev.jdtech.jellyfin.cast.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.cast.dlna.AndroidMulticastLock
import dev.jdtech.jellyfin.cast.dlna.AvTransport
import dev.jdtech.jellyfin.cast.dlna.DlnaClient
import dev.jdtech.jellyfin.cast.dlna.NetworkLock
import dev.jdtech.jellyfin.cast.dlna.SoapClient
import dev.jdtech.jellyfin.cast.dlna.SsdpDiscovery
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object CastModule {
    @Provides @Singleton fun provideNetworkLock(lock: AndroidMulticastLock): NetworkLock = lock

    @Provides
    @Singleton
    fun provideSsdpDiscovery(networkLock: NetworkLock): SsdpDiscovery = SsdpDiscovery(networkLock)

    @Provides @Singleton fun provideHttpClient(): OkHttpClient = DlnaClient.defaultHttpClient()

    @Provides
    @Singleton
    fun provideSoapClient(httpClient: OkHttpClient): SoapClient = SoapClient(httpClient)

    @Provides
    @Singleton
    fun provideAvTransport(soapClient: SoapClient): AvTransport = AvTransport(soapClient)

    @Provides
    @Singleton
    fun provideDlnaClient(
        ssdpDiscovery: SsdpDiscovery,
        httpClient: OkHttpClient,
        avTransport: AvTransport,
    ): DlnaClient = DlnaClient(ssdpDiscovery, httpClient, avTransport)
}
