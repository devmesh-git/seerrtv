package ca.devmesh.seerrtv.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.devmesh.seerrtv.model.CombinedCredits
import ca.devmesh.seerrtv.model.CreditItem
import ca.devmesh.seerrtv.model.PersonDetails
import ca.devmesh.seerrtv.navigation.LocalNavController
import ca.devmesh.seerrtv.navigation.rememberNavigationManager
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.Media
import ca.devmesh.seerrtv.ui.components.AuthenticationErrorHandler
import ca.devmesh.seerrtv.ui.components.MediaCard
import ca.devmesh.seerrtv.viewmodel.PersonViewModel
import coil3.compose.AsyncImage
import coil3.ImageLoader
import coil3.request.crossfade
import coil3.request.ImageRequest
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.Period
import kotlinx.coroutines.delay

enum class PersonScreenFocus { Top, ReadMore, KnownFor, Crew }

private fun logPerson(message: String) {
    Log.d("PersonScreen", message)
}

private data class UiPersonScreenState(
    val currentFocus: PersonScreenFocus = PersonScreenFocus.Top,
    val selectedKnownForIndex: Int = 0,
    val selectedCrewIndex: Int = 0,
    val isFullBiographyShown: Boolean = false,
    val hasFocusBeenRequested: Boolean = false,
    val hasEmptyBiography: Boolean = false
)

private data class ComputedPersonScreenState(
    val hasHiddenText: Boolean,
    val hasKnownFor: Boolean,
    val hasCrew: Boolean,
    val backdropImages: List<String>,
    val isBackSelected: Boolean,
    val content: PersonScreenContent,
    val hasEmptyBiography: Boolean
)

private sealed class PersonScreenContent {
    object Loading : PersonScreenContent()
    object Success : PersonScreenContent()
    data class Error(val message: String?) : PersonScreenContent()
}

@Composable
fun PersonScreen(
    personId: String,
    imageLoader: ImageLoader,
    appFocusManager: ca.devmesh.seerrtv.ui.focus.AppFocusManager,
    dpadController: ca.devmesh.seerrtv.ui.focus.DpadController,
    viewModel: PersonViewModel
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val navigationManager = rememberNavigationManager(scope, navController)
    var isNavigating by remember { mutableStateOf(false) }
    
    // Track if this is the initial load to prevent auto-scrolling
    var isInitialLoad by remember { mutableStateOf(true) }

    // Handle navigation state
    DisposableEffect(Unit) {
        onDispose {
            isNavigating = false
        }
    }

    // Use collectAsStateWithLifecycle to observe the state
    val viewModelState by viewModel.state.collectAsStateWithLifecycle()

    // Use stable state for focus management that syncs with viewModel
    val uiState = remember { 
        mutableStateOf(UiPersonScreenState(
            currentFocus = when(viewModelState.currentFocus) {
                "Top" -> PersonScreenFocus.Top
                "ReadMore" -> PersonScreenFocus.ReadMore
                "KnownFor" -> PersonScreenFocus.KnownFor
                "Crew" -> PersonScreenFocus.Crew
                else -> PersonScreenFocus.Top
            },
            selectedKnownForIndex = viewModelState.selectedKnownForIndex,
            selectedCrewIndex = viewModelState.selectedCrewIndex,
            isFullBiographyShown = viewModelState.isFullBiographyShown,
            hasFocusBeenRequested = viewModelState.isDataLoaded,
            hasEmptyBiography = viewModelState.personDetails?.biography.isNullOrBlank()
        ))
    }
    

    // Save UI state changes back to ViewModel
    LaunchedEffect(uiState.value) {
        // Convert enum to string for storage
        val focusString = when(uiState.value.currentFocus) {
            PersonScreenFocus.Top -> "Top"
            PersonScreenFocus.ReadMore -> "ReadMore"
            PersonScreenFocus.KnownFor -> "KnownFor"
            PersonScreenFocus.Crew -> "Crew"
        }
        
        if (focusString != viewModelState.currentFocus) {
            viewModel.updateCurrentFocus(focusString)
        }
        
        if (uiState.value.selectedKnownForIndex != viewModelState.selectedKnownForIndex ||
            uiState.value.selectedCrewIndex != viewModelState.selectedCrewIndex) {
            viewModel.updateCarouselPositions(
                uiState.value.selectedKnownForIndex,
                uiState.value.selectedCrewIndex
            )
        }
        
        if (uiState.value.isFullBiographyShown != viewModelState.isFullBiographyShown) {
            viewModel.updateBiographyState(uiState.value.isFullBiographyShown)
        }
    }

    // Use stable state holders to prevent unnecessary recompositions
    val lazyListState = rememberLazyListState()

    // Effect to set initial focus based on content
    LaunchedEffect(viewModelState.personDetails, viewModelState.combinedCredits) {
        // Only set initial focus if data is loaded and we haven't set focus yet
        if (viewModelState.isDataLoaded && !uiState.value.hasFocusBeenRequested) {
            // Choose an initial focus section - prioritize Overview (Top) to show the screen at the top
            val initialFocus: PersonScreenFocus = PersonScreenFocus.Top

            uiState.value = uiState.value.copy(
                currentFocus = initialFocus,
                hasFocusBeenRequested = true,
                hasEmptyBiography = viewModelState.personDetails?.biography.isNullOrBlank()
            )

            // Map to global focus for centralized DPAD - always Overview since initialFocus is always Top
            appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Overview))
            // Don't auto-scroll on initial load - let user see the overview section
            // Scrolling will happen when user navigates to sections
            logPerson("Initial focus set to: ${uiState.value.currentFocus} for person ID: ${viewModelState.currentPersonId}")
        }
    }

    // Back navigation is handled by the DPAD config onBack handler

    // Load data and handle cleanup with DisposableEffect
    DisposableEffect(personId) {
        // Only load if we don't have the data or if it's for a different person
        if (!viewModelState.isDataLoaded || viewModelState.currentPersonId != personId) {
            viewModel.loadPersonData(personId)
        }
        
        onDispose {
            logPerson("Disposing PersonScreen for ID: $personId")
            // Don't reset focus or selection indices as they're now stored in ViewModel
            isNavigating = false
        }
    }

    // Mark initial load as complete when data is loaded
    LaunchedEffect(viewModelState.isDataLoaded, viewModelState.currentPersonId) {
        if (viewModelState.isDataLoaded && viewModelState.currentPersonId == personId) {
            isInitialLoad = false
        }
    }

    // Handle scroll position based on focus (only after initial load)
    LaunchedEffect(uiState.value.currentFocus, isInitialLoad) {
        if (!isInitialLoad) {
            when (uiState.value.currentFocus) {
                PersonScreenFocus.KnownFor -> {
                    // Scroll to Known For section
                    lazyListState.animateScrollToItem(2) // Adjust index based on your layout
                }
                PersonScreenFocus.Crew -> {
                    // Scroll to Crew section
                    lazyListState.animateScrollToItem(3) // Adjust index based on your layout
                }
                PersonScreenFocus.Top, PersonScreenFocus.ReadMore -> {
                    // Scroll back to top
                    lazyListState.animateScrollToItem(0)
                }
            }
        }
    }

    // Handle back button with cleanup
    val handleBack = remember(scope, navigationManager) {
        {
            if (!isNavigating) {
                logPerson("Back navigation triggered for ID: $personId")
                isNavigating = true
                // Explicitly pop back to the previous Details instance instead of navigating forward
                navigationManager.popBackToDetails()
            } else {
                logPerson("Back navigation skipped - already navigating")
            }
            Unit // Explicitly return Unit
        }
    }

    // Use stable derivedStateOf for computed values to prevent unnecessary recompositions
    val computedState by remember(viewModelState, uiState.value) {
        derivedStateOf {
            ComputedPersonScreenState(
                hasHiddenText = viewModelState.personDetails?.biography?.let { bio ->
                    bio.length > 500 || bio.indexOf("\n\n", 300) != -1
                } == true,
                hasKnownFor = viewModelState.combinedCredits?.cast?.isNotEmpty() == true,
                hasCrew = viewModelState.combinedCredits?.crew?.isNotEmpty() == true,
                backdropImages = extractBackdropImages(viewModelState.combinedCredits),
                isBackSelected = uiState.value.currentFocus == PersonScreenFocus.Top,
                content = when {
                    viewModelState.isLoading -> PersonScreenContent.Loading
                    viewModelState.error != null -> PersonScreenContent.Error(viewModelState.error)
                    viewModelState.isDataLoaded -> PersonScreenContent.Success
                    else -> PersonScreenContent.Loading
                },
                hasEmptyBiography = uiState.value.hasEmptyBiography
            )
        }
    }

    // Handle media selection
    fun handleMediaSelection(mediaId: String, mediaType: String) {
        navigationManager.navigateToDetails(mediaId, mediaType)
    }

    // Handle config navigation
    fun handleConfigNavigation() {
        navigationManager.navigateToConfig()
    }

    val controllerFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1D29))
            .focusRequester(controllerFocusRequester)
            .focusable()
    ) {
        // Register DPAD config for Person screen
        LaunchedEffect(personId) {
            // Helpers to read the latest content booleans at handler invocation time
            fun hasHiddenTextNow(): Boolean {
                val bio = viewModel.state.value.personDetails?.biography ?: ""
                val firstBreakAfter300 = bio.indexOf("\n\n", 300)
                return (firstBreakAfter300 != -1) || bio.length > 500
            }
            fun hasKnownForNow(): Boolean {
                return viewModel.state.value.combinedCredits?.cast?.isNotEmpty() == true
            }
            fun hasCrewNow(): Boolean {
                return viewModel.state.value.combinedCredits?.crew?.isNotEmpty() == true
            }
            fun hasEmptyBiographyNow(): Boolean {
                return viewModel.state.value.personDetails?.biography.isNullOrBlank()
            }
            val config = ca.devmesh.seerrtv.ui.focus.createPersonScreenDpadConfig(
                route = "person",
                focusManager = appFocusManager,
                onUp = {
                    when (uiState.value.currentFocus) {
                        PersonScreenFocus.Crew -> {
                            when {
                                hasKnownForNow() -> {
                                    uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.KnownFor)
                                    appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Cast))
                                }
                                !hasEmptyBiographyNow() && hasHiddenTextNow() -> {
                                    uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.ReadMore)
                                    appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.ReadMore))
                                }
                                else -> {
                                    appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.TopBar(ca.devmesh.seerrtv.ui.focus.TopBarFocus.Settings))
                                }
                            }
                        }
                        PersonScreenFocus.KnownFor -> {
                            if (!hasEmptyBiographyNow() && hasHiddenTextNow()) {
                                uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.ReadMore)
                                appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.ReadMore))
                            } else {
                                appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.TopBar(ca.devmesh.seerrtv.ui.focus.TopBarFocus.Settings))
                            }
                        }
                        PersonScreenFocus.ReadMore, PersonScreenFocus.Top -> {
                            appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.TopBar(ca.devmesh.seerrtv.ui.focus.TopBarFocus.Settings))
                        }
                    }
                },
                onDown = {
                    when (uiState.value.currentFocus) {
                        PersonScreenFocus.Top -> {
                            when {
                                !hasEmptyBiographyNow() && hasHiddenTextNow() -> {
                                    uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.ReadMore)
                                    appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.ReadMore))
                                }
                                hasKnownForNow() -> {
                                    uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.KnownFor)
                                    appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Cast))
                                }
                                hasCrewNow() -> {
                                    uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.Crew)
                                    appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Crew))
                                }
                            }
                        }
                        PersonScreenFocus.ReadMore -> {
                            if (hasKnownForNow()) {
                                uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.KnownFor)
                                appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Cast))
                            } else if (hasCrewNow()) {
                                uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.Crew)
                                appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Crew))
                            }
                        }
                        PersonScreenFocus.KnownFor -> {
                            if (hasCrewNow()) {
                                uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.Crew)
                                appFocusManager.setFocus(ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen(ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Crew))
                            }
                        }
                        PersonScreenFocus.Crew -> { /* stay */ }
                    }
                },
                onLeft = {
                    if (uiState.value.currentFocus == PersonScreenFocus.KnownFor && uiState.value.selectedKnownForIndex > 0) {
                        uiState.value = uiState.value.copy(selectedKnownForIndex = uiState.value.selectedKnownForIndex - 1)
                    } else if (uiState.value.currentFocus == PersonScreenFocus.Crew && uiState.value.selectedCrewIndex > 0) {
                        uiState.value = uiState.value.copy(selectedCrewIndex = uiState.value.selectedCrewIndex - 1)
                    }
                },
                onRight = {
                    if (uiState.value.currentFocus == PersonScreenFocus.KnownFor) {
                        val count = viewModel.state.value.combinedCredits?.cast?.size ?: 0
                        val current = uiState.value.selectedKnownForIndex
                        if (count > 0 && current < count - 1) {
                            val next = current + 1
                            uiState.value = uiState.value.copy(selectedKnownForIndex = next)
                            logPerson("KnownFor -> RIGHT: $current -> $next / $count")
                        } else {
                            logPerson("KnownFor -> RIGHT ignored: current=$current count=$count")
                        }
                    } else if (uiState.value.currentFocus == PersonScreenFocus.Crew) {
                        val count = viewModel.state.value.combinedCredits?.crew?.size ?: 0
                        val current = uiState.value.selectedCrewIndex
                        if (count > 0 && current < count - 1) {
                            val next = current + 1
                            uiState.value = uiState.value.copy(selectedCrewIndex = next)
                            logPerson("Crew -> RIGHT: $current -> $next / $count")
                        } else {
                            logPerson("Crew -> RIGHT ignored: current=$current count=$count")
                        }
                    }
                },
                onEnter = {
                    when (uiState.value.currentFocus) {
                        PersonScreenFocus.ReadMore -> {
                            uiState.value = uiState.value.copy(isFullBiographyShown = !uiState.value.isFullBiographyShown)
                        }
                        PersonScreenFocus.KnownFor -> {
                            viewModel.state.value.combinedCredits?.cast?.getOrNull(uiState.value.selectedKnownForIndex)?.let { credit ->
                                handleMediaSelection(credit.id.toString(), credit.mediaType)
                            }
                        }
                        PersonScreenFocus.Crew -> {
                            viewModel.state.value.combinedCredits?.crew?.getOrNull(uiState.value.selectedCrewIndex)?.let { credit ->
                                handleMediaSelection(credit.id.toString(), credit.mediaType)
                            }
                        }
                        PersonScreenFocus.Top -> {
                            handleBack()
                        }
                    }
                },
                onBack = {
                    handleBack()
                }
            )
            dpadController.registerScreen(config)
            // Ensure DPAD host in this screen has focus
            controllerFocusRequester.requestFocus()
        }

        // Sync local UI focus with global AppFocusManager to drive highlighting/scroll
        LaunchedEffect(appFocusManager.currentFocus) {
            when (val f = appFocusManager.currentFocus) {
                is ca.devmesh.seerrtv.ui.focus.AppFocusState.DetailsScreen -> {
                    when (f.focus) {
                        ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Overview -> {
                            uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.Top)
                            // Only scroll to top if not initial load
                            if (!isInitialLoad) {
                                lazyListState.animateScrollToItem(0)
                            }
                        }
                        ca.devmesh.seerrtv.ui.focus.DetailsFocusState.ReadMore -> {
                            uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.ReadMore)
                            // Scroll to top to show overview section when ReadMore is focused
                            if (!isInitialLoad) {
                                lazyListState.animateScrollToItem(0)
                            }
                        }
                        ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Cast -> {
                            // Prefer KnownFor when available; if not, fall back to Crew
                            if (viewModel.state.value.combinedCredits?.cast?.isNotEmpty() == true) {
                                uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.KnownFor)
                                // Only scroll if not initial load
                                if (!isInitialLoad) {
                                    lazyListState.animateScrollToItem(2)
                                }
                            } else {
                                uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.Crew)
                                if (!isInitialLoad) {
                                    lazyListState.animateScrollToItem(3)
                                }
                            }
                        }
                        ca.devmesh.seerrtv.ui.focus.DetailsFocusState.Crew -> {
                            if (viewModel.state.value.combinedCredits?.crew?.isNotEmpty() == true) {
                                uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.Crew)
                                if (!isInitialLoad) {
                                    lazyListState.animateScrollToItem(3)
                                }
                            } else if (viewModel.state.value.combinedCredits?.cast?.isNotEmpty() == true) {
                                uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.KnownFor)
                                if (!isInitialLoad) {
                                    lazyListState.animateScrollToItem(2)
                                }
                            } else {
                                uiState.value = uiState.value.copy(currentFocus = PersonScreenFocus.Top)
                                if (!isInitialLoad) {
                                    lazyListState.animateScrollToItem(0)
                                }
                            }
                        }
                        else -> { /* ignore other detail states */ }
                    }
                }
                is ca.devmesh.seerrtv.ui.focus.AppFocusState.TopBar -> {
                    // When focus is in TopBar, scroll to top to show overview section
                    if (!isInitialLoad) {
                        lazyListState.animateScrollToItem(0)
                    }
                }
                else -> {}
            }
        }
        CyclingBackdrop(
            backdropImages = computedState.backdropImages,
            imageLoader = imageLoader,
            modifier = Modifier.fillMaxSize(),
            selectedKnownForIndex = uiState.value.selectedKnownForIndex,
            selectedCrewIndex = uiState.value.selectedCrewIndex,
            currentFocus = uiState.value.currentFocus,
            combinedCredits = viewModelState.combinedCredits
        )
        LazyColumn(
            state = lazyListState,
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 16.dp)
        ) {
            item {
                PersonDetailsSection(
                    details = viewModelState.personDetails,
                    imageLoader = imageLoader
                )
            }

            viewModelState.personDetails?.let { details ->
                item {
                    BiographySection(
                        biography = details.biography,
                        isFullBiographyShown = uiState.value.isFullBiographyShown,
                        onToggleFullBiography = { isFullBiographyShown ->
                            uiState.value = uiState.value.copy(isFullBiographyShown = isFullBiographyShown)
                        },
                        isFocused = uiState.value.currentFocus == PersonScreenFocus.ReadMore
                    )
                }
                if (computedState.hasKnownFor) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.personScreen_knownFor),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFFFFFFF),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        KnownForCarousel(
                            credits = viewModelState.combinedCredits?.cast ?: emptyList(),
                            imageLoader = imageLoader,
                            selectedIndex = uiState.value.selectedKnownForIndex,
                            isFocused = uiState.value.currentFocus == PersonScreenFocus.KnownFor && 
                                       appFocusManager.currentFocus !is ca.devmesh.seerrtv.ui.focus.AppFocusState.TopBar
                        )
                    }
                }

                if (computedState.hasCrew) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.personScreen_crew),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFFFFFFF),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        CrewCarousel(
                            credits = viewModelState.combinedCredits?.crew ?: emptyList(),
                            imageLoader = imageLoader,
                            selectedIndex = uiState.value.selectedCrewIndex,
                            isFocused = uiState.value.currentFocus == PersonScreenFocus.Crew && 
                                       appFocusManager.currentFocus !is ca.devmesh.seerrtv.ui.focus.AppFocusState.TopBar
                        )
                    }
                }
            }

            if (viewModelState.isLoading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .wrapContentSize(Alignment.Center)
                    )
                }
            }

            viewModelState.error?.let {
                item {
                    Text(
                        text = stringResource(R.string.common_error)+ ": $it",
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // Add authentication error dialog on top of everything else
        if (viewModelState.showAuthenticationError) {
            AuthenticationErrorHandler(
                isVisible = true,
                onRetry = {
                    viewModel.hideAuthenticationError()
                    viewModel.retryLastAction()
                },
                onReconfigure = {
                    viewModel.hideAuthenticationError()
                    handleConfigNavigation()
                }
            )
        }
    }
}

private fun extractBackdropImages(combinedCredits: CombinedCredits?): List<String> {
    val allCredits = (combinedCredits?.cast ?: emptyList()) + (combinedCredits?.crew ?: emptyList())
    return allCredits
        .asSequence()
        .filter { !it.backdropPath.isNullOrBlank() }
        .map { "https://image.tmdb.org/t/p/original${it.backdropPath}" }
        .distinct()
        .take(10)
        .toList() // Limit to 10 unique backdrops
}

@Composable
fun CyclingBackdrop(
    backdropImages: List<String>,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    selectedKnownForIndex: Int = 0,
    selectedCrewIndex: Int = 0,
    currentFocus: PersonScreenFocus = PersonScreenFocus.Top,
    combinedCredits: CombinedCredits? = null
) {
    var currentBackdropIndex by remember { mutableIntStateOf(0) }

    // Cycle through backdrops when no media is selected
    LaunchedEffect(currentFocus, backdropImages) {
        if (currentFocus !in listOf(PersonScreenFocus.KnownFor, PersonScreenFocus.Crew) && backdropImages.isNotEmpty()) {
            while (currentFocus !in listOf(PersonScreenFocus.KnownFor, PersonScreenFocus.Crew)) {
                delay(10000) // Change image every 10 seconds
                currentBackdropIndex = (currentBackdropIndex + 1) % backdropImages.size
            }
        }
    }

    // Get the currently selected backdrop based on focus and selection
    val selectedBackdrop = remember(currentFocus, selectedKnownForIndex, selectedCrewIndex, combinedCredits, currentBackdropIndex) {
        when (currentFocus) {
            PersonScreenFocus.KnownFor -> combinedCredits?.cast?.getOrNull(selectedKnownForIndex)?.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
            PersonScreenFocus.Crew -> combinedCredits?.crew?.getOrNull(selectedCrewIndex)?.backdropPath?.let { "https://image.tmdb.org/t/p/original$it" }
            else -> backdropImages.getOrNull(currentBackdropIndex)
        }
    }

    Box(modifier = modifier) {
        if (selectedBackdrop != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(selectedBackdrop)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback background when there are no backdrop images
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1D29))
            )
        }
        
        // Add a scrim overlay to ensure text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x80000000), Color(0xCC000000)),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
    }
}

@Composable
fun PersonDetailsSection(
    details: PersonDetails?,
    imageLoader: ImageLoader
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        details?.let { person ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://image.tmdb.org/t/p/w300_and_h450_face${person.profilePath}")
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.personScreen_personProfile),
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                val age = calculateAge(person.birthday)
                Text(
                    text = buildString {
                        append("${stringResource(R.string.personScreen_born)} ${person.birthday ?: stringResource(R.string.common_unknown)}")
                        if (age != null) {
                            append(" ($age ${stringResource(R.string.personScreen_yearsOld)})")
                        }
                        append(" | ${person.placeOfBirth ?: stringResource(R.string.common_unknown)}")
                    },
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (details.alsoKnownAs.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.personScreen_alsoKnownAs) + ": ${person.alsoKnownAs.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

fun calculateAge(birthdate: String?): Int? {
    if (birthdate == null) return null
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val birthDate = LocalDate.parse(birthdate, formatter)
    return Period.between(birthDate, LocalDate.now()).years
}

@Composable
fun BiographySection(
    biography: String,
    isFullBiographyShown: Boolean,
    onToggleFullBiography: (Boolean) -> Unit,
    isFocused: Boolean
) {
    val textColor = if (isFocused) Color.White else Color.LightGray
    val truncatedBiography = if (!isFullBiographyShown) {
        val firstBreakAfter300 = biography.indexOf("\n\n", 300)
        if (firstBreakAfter300 != -1) {
            biography.substring(0, firstBreakAfter300) + "..."
        } else if (biography.length > 500) {
            biography.take(500) + "..."
        } else {
            biography
        }
    } else {
        biography
    }
    val hasHiddenText = biography.length > truncatedBiography.length

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = truncatedBiography,
            color = textColor,
            modifier = Modifier.clickable(enabled = hasHiddenText) { 
                onToggleFullBiography(!isFullBiographyShown) 
            }
        )
        if (hasHiddenText) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.common_readMore),
                color = textColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { onToggleFullBiography(!isFullBiographyShown) }
            )
        }
    }
}

@Composable
fun KnownForCarousel(
    credits: List<CreditItem>,
    imageLoader: ImageLoader,
    selectedIndex: Int,
    isFocused: Boolean
) {
    val listState = rememberLazyListState()
    val itemsPerPage = 8
    val context = LocalContext.current

    // Ensure the selected item stays visible (aligns with MediaDetails behavior)
    LaunchedEffect(selectedIndex, credits.size, isFocused) {
        if (!isFocused || credits.isEmpty()) return@LaunchedEffect
        val first = listState.firstVisibleItemIndex
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size.takeIf { it > 0 } ?: itemsPerPage
        val last = (first + visibleCount - 1).coerceAtLeast(first)

        when {
            selectedIndex >= last -> {
                // Scroll directly to the selected item so it becomes visible
                listState.animateScrollToItem(selectedIndex)
            }
            selectedIndex < first -> {
                listState.animateScrollToItem(selectedIndex)
            }
            else -> {}
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp)
    ) {
        itemsIndexed(credits) { index, credit ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(134.dp)
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(134.dp, 185.dp)
                ) {
                    val mediaContent = Media(
                        id = credit.id,
                        mediaType = credit.mediaType,
                        title = credit.title ?: "",
                        name = credit.name ?: "",
                        posterPath = credit.posterPath ?: "",
                        backdropPath = credit.backdropPath ?: "",
                        overview = credit.overview ?: ""
                    )
                    MediaCard(
                        mediaContent = mediaContent,
                        context = context,
                        imageLoader = imageLoader,
                        isSelected = isFocused && index == selectedIndex,
                        cardWidth = 134.dp,
                        cardHeight = 185.dp,
                    )
                }
                Spacer(modifier = Modifier.height(if (isFocused && index == selectedIndex) 12.dp else 4.dp))
                Text(
                    text = credit.character ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun CrewCarousel(
    credits: List<CreditItem>,
    imageLoader: ImageLoader,
    selectedIndex: Int,
    isFocused: Boolean
) {
    val listState = rememberLazyListState()
    val itemsPerPage = 8
    val context = LocalContext.current

    // Ensure the selected crew item stays visible
    LaunchedEffect(selectedIndex, credits.size, isFocused) {
        if (!isFocused || credits.isEmpty()) return@LaunchedEffect
        val first = listState.firstVisibleItemIndex
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size.takeIf { it > 0 } ?: itemsPerPage
        val last = (first + visibleCount - 1).coerceAtLeast(first)

        when {
            selectedIndex >= last -> {
                listState.animateScrollToItem(selectedIndex)
            }
            selectedIndex < first -> {
                listState.animateScrollToItem(selectedIndex)
            }
            else -> {}
        }
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp)
    ) {
        itemsIndexed(credits) { index, credit ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(134.dp)
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(134.dp, 185.dp)
                ) {
                    val mediaContent = Media(
                        id = credit.id,
                        mediaType = credit.mediaType,
                        title = credit.title ?: "",
                        name = credit.name ?: "",
                        posterPath = credit.posterPath ?: "",
                        backdropPath = credit.backdropPath ?: "",
                        overview = credit.overview ?: ""
                    )
                    
                    MediaCard(
                        mediaContent = mediaContent,
                        context = context,
                        imageLoader = imageLoader,
                        isSelected = isFocused && index == selectedIndex,
                        cardWidth = 134.dp,
                        cardHeight = 185.dp,
                    )
                }
                Spacer(modifier = Modifier.height(if (isFocused && index == selectedIndex) 12.dp else 4.dp))
                Text(
                    text = credit.job ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}