package ca.devmesh.seerrtv.data

/**
 * Holds the singleton [SeerrApiService] for Coil image requests that need Seerr session/API auth.
 */
object SeerrImageAuthHolder {
    @Volatile
    var apiService: SeerrApiService? = null
}
