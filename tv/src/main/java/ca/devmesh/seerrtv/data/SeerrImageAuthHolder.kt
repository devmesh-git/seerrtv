package ca.devmesh.seerrtv.data

import android.annotation.SuppressLint

/**
 * Holds the singleton [SeerrApiService] for Coil image requests that need Seerr session/API auth.
 *
 * Not a leak: the only assignment (AppModule.provideSeerrApiService) builds the service with the
 * Hilt @ApplicationContext, so no Activity context is ever retained.
 */
@SuppressLint("StaticFieldLeak")
object SeerrImageAuthHolder {
    @Volatile
    var apiService: SeerrApiService? = null
}
