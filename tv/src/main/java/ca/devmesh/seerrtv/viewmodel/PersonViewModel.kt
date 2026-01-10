package ca.devmesh.seerrtv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.devmesh.seerrtv.data.ApiResult
import ca.devmesh.seerrtv.data.SeerrApiService
import ca.devmesh.seerrtv.model.CombinedCredits
import ca.devmesh.seerrtv.model.CreditItem
import ca.devmesh.seerrtv.model.PersonDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class PersonScreenState(
    val personDetails: PersonDetails? = null,
    val combinedCredits: CombinedCredits? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAuthenticationError: Boolean = false,
    val currentPersonId: String? = null,
    val isDataLoaded: Boolean = false, // New flag to track if data has been loaded
    val selectedKnownForIndex: Int = 0, // Store selected cast carousel position
    val selectedCrewIndex: Int = 0, // Store selected crew carousel position 
    val isFullBiographyShown: Boolean = false, // Track biography expanded state
    val currentFocus: String = "Top" // Store current focus area
)

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val SeerrApiService: SeerrApiService
) : ViewModel() {

    private val _state = MutableStateFlow(PersonScreenState())
    val state: StateFlow<PersonScreenState> = _state.asStateFlow()

    private var loadJob: Job? = null

    // Add methods to update UI state
    fun updateCarouselPositions(knownForIndex: Int, crewIndex: Int) {
        _state.update { it.copy(
            selectedKnownForIndex = knownForIndex,
            selectedCrewIndex = crewIndex
        ) }
    }

    fun updateCurrentFocus(focus: String) {
        _state.update { it.copy(currentFocus = focus) }
    }

    fun updateBiographyState(isExpanded: Boolean) {
        _state.update { it.copy(isFullBiographyShown = isExpanded) }
    }

    /**
     * Determines the appropriate initial focus area based on available person data
     */
    private fun determineInitialFocus(personDetails: PersonDetails?, combinedCredits: CombinedCredits?): String {
        val hasBiography = !personDetails?.biography.isNullOrBlank()
        val hasCast = combinedCredits?.cast?.isNotEmpty() == true
        val hasCrew = combinedCredits?.crew?.isNotEmpty() == true

        return when {
            // If there's no biography but there are cast items, focus KnownFor
            !hasBiography && hasCast -> "KnownFor"
            // If there's no biography and no cast but there are crew items, focus Crew
            !hasBiography && hasCrew -> "Crew"
            // Default to top for all other cases
            else -> "Top"
        }
    }

    fun loadPersonData(personId: String) {
        // Don't reload if we already have the data for this person and it's loaded
        if (_state.value.currentPersonId == personId && 
            _state.value.isDataLoaded &&
            _state.value.error == null) {
            Log.d("PersonViewModel", "Skipping reload for person ID: $personId as data is already loaded")
            return
        }

        // Get the current UI state values to preserve them
        val currentKnownForIndex = _state.value.selectedKnownForIndex
        val currentCrewIndex = _state.value.selectedCrewIndex
        val currentBiographyState = _state.value.isFullBiographyShown
        val currentFocus = _state.value.currentFocus

        // Cancel any existing job before starting a new one
        loadJob?.cancel()
        
        loadJob = viewModelScope.launch {
            try {
                // Set initial loading state atomically, but preserve UI state if loading the same person
                _state.update { it.copy(
                    isLoading = true,
                    error = null,
                    showAuthenticationError = false,
                    currentPersonId = personId,
                    isDataLoaded = false,
                    // Clear existing data to prevent stale data display
                    personDetails = null,
                    combinedCredits = null,
                    // Preserve UI state if this is the same person, reset otherwise
                    selectedKnownForIndex = if (it.currentPersonId == personId) currentKnownForIndex else 0,
                    selectedCrewIndex = if (it.currentPersonId == personId) currentCrewIndex else 0,
                    isFullBiographyShown = if (it.currentPersonId == personId) currentBiographyState else false,
                    currentFocus = if (it.currentPersonId == personId) currentFocus else determineInitialFocus(it.personDetails, it.combinedCredits)
                ) }

                // Use supervisorScope to prevent child coroutine failures from cancelling siblings
                supervisorScope {
                    // Load data in parallel
                    val personDeferred = async { SeerrApiService.getPersonDetails(personId) }
                    val creditsDeferred = async { SeerrApiService.getPersonCombinedCredits(personId) }

                    // Wait for both results before updating state
                    val personResult = personDeferred.await()
                    val creditsResult = creditsDeferred.await()

                    // Update state atomically with both results
                    _state.update { currentState ->
                        var newState = currentState

                        when (personResult) {
                            is ApiResult.Success -> {
                                newState = newState.copy(
                                    personDetails = personResult.data,
                                    isDataLoaded = true
                                )
                                Log.d("PersonViewModel", "Successfully loaded person details for ID: $personId")
                            }
                            is ApiResult.Error -> {
                                Log.e("PersonViewModel", "Error loading person details: ${personResult.exception.message}")
                                newState = newState.copy(
                                    error = personResult.exception.message,
                                    showAuthenticationError = personResult.statusCode == 403 || personResult.statusCode == 401,
                                    isDataLoaded = false
                                )
                            }
                            is ApiResult.Loading -> {
                                // Ignore loading state as we handle it separately
                            }
                        }

                        when (creditsResult) {
                            is ApiResult.Success -> {
                                // Sort cast and crew separately, but intermix movies and TV shows within each list
                                val sortedCredits = creditsResult.data.copy(
                                    cast = creditsResult.data.cast.sortedByDescending { it.getReleaseDateOrFirstAirDate() },
                                    crew = creditsResult.data.crew.sortedByDescending { it.getReleaseDateOrFirstAirDate() }
                                )
                                
                                newState = newState.copy(
                                    combinedCredits = sortedCredits,
                                    isDataLoaded = newState.personDetails != null // Only mark as loaded if we have both
                                )
                                Log.d("PersonViewModel", "Successfully loaded combined credits for ID: $personId")
                            }
                            is ApiResult.Error -> {
                                Log.e("PersonViewModel", "Error loading combined credits: ${creditsResult.exception.message}")
                                if (newState.error == null) {
                                    newState = newState.copy(
                                        error = creditsResult.exception.message,
                                        showAuthenticationError = creditsResult.statusCode == 403 || creditsResult.statusCode == 401,
                                        isDataLoaded = false
                                    )
                                }
                            }
                            is ApiResult.Loading -> {
                                // Ignore loading state as we handle it separately
                            }
                        }

                        // Set final focus area based on loaded data
                        if (newState.isDataLoaded) {
                            val initialFocus = determineInitialFocus(newState.personDetails, newState.combinedCredits)
                            Log.d("PersonViewModel", "Setting initial focus to: $initialFocus for person ID: $personId")
                            newState = newState.copy(currentFocus = initialFocus)
                        }

                        newState.copy(isLoading = false)
                    }
                }
            } catch (_: CancellationException) {
                // Log cancellation but don't treat it as an error
                Log.d("PersonViewModel", "Load job cancelled for ID: $personId")
            } catch (e: Exception) {
                Log.e("PersonViewModel", "Error loading person data", e)
                _state.update { it.copy(
                    error = e.message,
                    showAuthenticationError = e.message?.contains("403") == true || e.message?.contains("401") == true,
                    isLoading = false,
                    isDataLoaded = false
                ) }
            }
        }
    }

    fun hideAuthenticationError() {
        _state.update { it.copy(showAuthenticationError = false) }
    }

    fun retryLastAction() {
        _state.value.currentPersonId?.let { loadPersonData(it) }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        loadJob = null
    }

    private fun CreditItem.getReleaseDateOrFirstAirDate(): LocalDate? {
        val dateString = releaseDate ?: firstAirDate
        if (dateString.isNullOrBlank()) return null
        return try {
            LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE)
        } catch (e: DateTimeParseException) {
            Log.w("PersonViewModel", "Failed to parse date: $dateString", e)
            null
        }
    }
}