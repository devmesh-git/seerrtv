package ca.devmesh.seerrtv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import ca.devmesh.seerrtv.viewmodel.DiscoveryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

val LocalNavController = compositionLocalOf<NavController> { error("No NavController provided") }

class NavigationManager(
    private val scope: CoroutineScope,
    private val navController: NavController
) {
    private val _isNavigating = MutableStateFlow(false)
    private val _lastNavigationTime = MutableStateFlow(0L)
    private val navigationDebounceTime = 300L

    fun canNavigate(): Boolean {
        val currentTime = System.currentTimeMillis()
        return !_isNavigating.value && currentTime - _lastNavigationTime.value > navigationDebounceTime
    }

    fun navigateBack() {
        if (canNavigate()) {
            scope.launch {
                if (ca.devmesh.seerrtv.BuildConfig.DEBUG) {
                    android.util.Log.d(
                        "NavigationManager",
                        "navigateBack() called\n" +
                            android.util.Log.getStackTraceString(Throwable())
                    )
                }
                _isNavigating.value = true
                _lastNavigationTime.value = System.currentTimeMillis()
                navController.popBackStack()
                delay(navigationDebounceTime)
                _isNavigating.value = false
            }
        }
    }

    fun navigateToDetails(mediaId: String, mediaType: String, popUpTo: String? = null, showRequestModal: Boolean = false) {
        if (canNavigate()) {
            scope.launch {
                _isNavigating.value = true
                _lastNavigationTime.value = System.currentTimeMillis()
                val route = if (showRequestModal) {
                    "details/$mediaId/$mediaType?showRequestModal=true"
                } else {
                    "details/$mediaId/$mediaType"
                }
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo?.let { routeStr ->
                        popUpTo(routeStr) {
                            inclusive = true
                            saveState = true
                        }
                    }
                }
                delay(navigationDebounceTime)
                _isNavigating.value = false
            }
        }
    }

    fun navigateToPerson(personId: String, popUpTo: String? = null) {
        if (canNavigate()) {
            scope.launch {
                _isNavigating.value = true
                _lastNavigationTime.value = System.currentTimeMillis()
                navController.navigate("person/$personId") {
                    popUpTo?.let { route ->
                        popUpTo(route) { inclusive = true }
                    }
                }
                delay(navigationDebounceTime)
                _isNavigating.value = false
            }
        }
    }

    fun popBackToDetails() {
        if (canNavigate()) {
            scope.launch {
                _isNavigating.value = true
                _lastNavigationTime.value = System.currentTimeMillis()
                navController.popBackStack()
                delay(navigationDebounceTime)
                _isNavigating.value = false
            }
        }
    }

    fun navigateToConfig() {
        if (canNavigate()) {
            scope.launch {
                _isNavigating.value = true
                _lastNavigationTime.value = System.currentTimeMillis()
                navController.navigate("config")
                delay(navigationDebounceTime)
                _isNavigating.value = false
            }
        }
    }

    fun navigateToKeywordDiscovery(type: String, keywordId: String, keywordText: String) {
        if (canNavigate()) {
            scope.launch {
                _isNavigating.value = true
                _lastNavigationTime.value = System.currentTimeMillis()
                val timestamp = System.currentTimeMillis()
                navController.navigate("mediaDiscovery/$type/$keywordId/$keywordText/$timestamp")
                delay(navigationDebounceTime)
                _isNavigating.value = false
            }
        }
    }

    /**
     * Navigate to the Media Discovery screen
     * 
     * @param discoveryType The discovery type as an enum (preferred) or null if using discoveryTypeStr
     * @param categoryId The ID of the selected category
     * @param categoryName The name of the category for display
     * @param categoryImage The image URL for the category (optional)
     * @param mediaType The media type (movie/tv), determined from discoveryType if null
     * @param discoveryTypeStr String representation of discovery type (only used if discoveryType is null)
     */
    fun navigateToMediaDiscovery(
        discoveryType: DiscoveryType? = null,
        categoryId: String,
        categoryName: String,
        categoryImage: String = "",
        mediaType: String? = null,
        discoveryTypeStr: String? = null
    ) {
        if (canNavigate()) {
            scope.launch {
                _isNavigating.value = true
                _lastNavigationTime.value = System.currentTimeMillis()
                
                // Determine values based on provided parameters
                val actualMediaType = mediaType ?: when {
                    discoveryType != null -> when {
                        discoveryType.name.contains("MOVIE") -> "movie"
                        discoveryType.name.contains("TV") || 
                        discoveryType.name.contains("SERIES") || 
                        discoveryType.name.contains("NETWORK") -> "tv"
                        else -> "movie" // Default fallback
                    }
                    else -> "movie" // Default fallback if neither is provided
                }
                
                val actualDiscoveryType = discoveryType?.name ?: discoveryTypeStr ?: "SEARCH"
                
                val timestamp = System.currentTimeMillis()
                val encodedName = java.net.URLEncoder.encode(categoryName, "UTF-8")
                
                // Only include image parameter if it's not empty
                val route = if (categoryImage.isNotEmpty()) {
                    val encodedImage = java.net.URLEncoder.encode(categoryImage, "UTF-8")
                    "mediaDiscovery/$actualMediaType/$categoryId/$actualDiscoveryType/$encodedName/$timestamp?image=$encodedImage"
                } else {
                    "mediaDiscovery/$actualMediaType/$categoryId/$actualDiscoveryType/$encodedName/$timestamp"
                }
                
                navController.navigate(route)
                
                delay(navigationDebounceTime)
                _isNavigating.value = false
            }
        }
    }

    fun navigateToMoviesBrowse() {
        if (canNavigate()) {
            scope.launch {
                _isNavigating.value = true
                _lastNavigationTime.value = System.currentTimeMillis()
                navController.navigate("browse/movies")
                delay(navigationDebounceTime)
                _isNavigating.value = false
            }
        }
    }

    fun navigateToSeriesBrowse() {
        if (canNavigate()) {
            scope.launch {
                _isNavigating.value = true
                _lastNavigationTime.value = System.currentTimeMillis()
                navController.navigate("browse/series")
                delay(navigationDebounceTime)
                _isNavigating.value = false
            }
        }
    }
}

@Composable
fun rememberNavigationManager(
    scope: CoroutineScope,
    navController: NavController
): NavigationManager {
    return remember(scope, navController) {
        NavigationManager(scope, navController)
    }
} 