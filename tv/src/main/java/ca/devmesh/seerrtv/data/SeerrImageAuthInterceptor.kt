package ca.devmesh.seerrtv.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds Seerr authentication headers/cookies to image requests targeting the configured Seerr host.
 */
class SeerrImageAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val authed = SeerrImageAuthHolder.apiService?.authenticateImageRequestIfNeeded(request)
        return chain.proceed(authed ?: request)
    }
}
