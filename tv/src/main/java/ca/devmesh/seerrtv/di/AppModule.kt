package ca.devmesh.seerrtv.di

import android.content.Context
import android.util.Log
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.model.AuthType
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.InstallIn
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSeerrApiService(@ApplicationContext context: Context): SeerrApiService {
        val config = SharedPreferencesUtil.getConfig(context)
        if (config != null) {
            Log.d("AppModule", "Creating API service with config: protocol=${config.protocol}, hostname=${config.hostname}, authType=${config.authType}")
            return SeerrApiService(config, context)
        } else {
            Log.d("AppModule", "No configuration found in SharedPreferences")
            // Create a service with empty config that will be updated when configuration is available
            return SeerrApiService(
                SeerrApiService.SeerrConfig(
                    protocol = "",
                    hostname = "",
                    authType = AuthType.ApiKey.type,
                    apiKey = "",
                    username = "",
                    password = "",
                    cloudflareEnabled = false,
                    cfClientId = "",
                    cfClientSecret = "",
                    isSubmitted = false,
                    createdAt = System.currentTimeMillis().toString()
                ),
                context
            )
        }
    }
} 