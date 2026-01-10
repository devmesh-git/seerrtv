package ca.devmesh.seerrtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.model.BrowseModels

/**
 * Release date filter controls (From/To date inputs)
 */
@Composable
fun ReleaseDateFilters(
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // From date
        OutlinedTextField(
            value = filters.releaseFrom ?: "",
            onValueChange = { newValue ->
                onFiltersChange(filters.copy(releaseFrom = newValue.ifEmpty { null }))
            },
            label = { Text(stringResource(R.string.filter_from), color = Color.Gray) },
            placeholder = { Text(stringResource(R.string.filter_dateFormat), color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .focusable(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true
        )
        
        // To date
        OutlinedTextField(
            value = filters.releaseTo ?: "",
            onValueChange = { newValue ->
                onFiltersChange(filters.copy(releaseTo = newValue.ifEmpty { null }))
            },
            label = { Text(stringResource(R.string.filter_to), color = Color.Gray) },
            placeholder = { Text(stringResource(R.string.filter_dateFormat), color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .focusable(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true
        )
    }
}

/**
 * Original language single-select filter using chips
 */
@Composable
fun LanguageFilter(
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    val languages = listOf(
        null to stringResource(R.string.filter_anyLanguage),
        "en" to stringResource(R.string.language_english),
        "es" to stringResource(R.string.language_spanish), 
        "fr" to stringResource(R.string.language_french),
        "de" to stringResource(R.string.language_german),
        "it" to stringResource(R.string.language_italian),
        "pt" to stringResource(R.string.language_portuguese),
        "ru" to stringResource(R.string.language_russian),
        "ja" to stringResource(R.string.language_japanese),
        "ko" to stringResource(R.string.language_korean),
        "zh" to stringResource(R.string.language_chinese)
    )
    
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(languages) { (code, name) ->
            val isSelected = code == filters.originalLanguage
            
            FilterChip(
                selected = isSelected,
                onClick = {
                    onFiltersChange(filters.copy(originalLanguage = code))
                },
                label = { 
                    Text(
                        text = name,
                        color = if (isSelected) Color.White else Color.Gray
                    )
                },
                modifier = Modifier.focusable(),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.Blue,
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFF2A2A2A),
                    labelColor = Color.Gray
                )
            )
        }
    }
}

/**
 * Content rating multi-select filter
 */
@Composable
fun ContentRatingFilter(
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    val ratings = listOf(
        stringResource(R.string.rating_nr),
        stringResource(R.string.rating_g),
        stringResource(R.string.rating_pg),
        stringResource(R.string.rating_pg13),
        stringResource(R.string.rating_r),
        stringResource(R.string.rating_nc17)
    )
    
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ratings) { rating ->
            val isSelected = rating in filters.contentRatings
            
            FilterChip(
                selected = isSelected,
                onClick = {
                    val newRatings = if (isSelected) {
                        filters.contentRatings - rating
                    } else {
                        filters.contentRatings + rating
                    }
                    onFiltersChange(filters.copy(contentRatings = newRatings))
                },
                label = { 
                    Text(
                        text = rating,
                        color = if (isSelected) Color.White else Color.Gray
                    )
                },
                modifier = Modifier.focusable(),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color.Blue,
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFF2A2A2A),
                    labelColor = Color.Gray
                )
            )
        }
    }
}

/**
 * Runtime range filter
 */
@Composable
fun RuntimeFilter(
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Min runtime
        OutlinedTextField(
            value = filters.runtimeMin?.toString() ?: "",
            onValueChange = { newValue ->
                val min = newValue.toIntOrNull()
                onFiltersChange(filters.copy(runtimeMin = min))
            },
            label = { Text(stringResource(R.string.filter_min), color = Color.Gray) },
            placeholder = { Text(stringResource(R.string.filter_minRuntime), color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .focusable(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        
        // Max runtime
        OutlinedTextField(
            value = filters.runtimeMax?.toString() ?: "",
            onValueChange = { newValue ->
                val max = newValue.toIntOrNull()
                onFiltersChange(filters.copy(runtimeMax = max))
            },
            label = { Text(stringResource(R.string.filter_max), color = Color.Gray) },
            placeholder = { Text(stringResource(R.string.filter_maxRuntime), color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .focusable(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

/**
 * User score range filter
 */
@Composable
fun UserScoreFilter(
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Min score
        OutlinedTextField(
            value = filters.userScoreMin?.toString() ?: "",
            onValueChange = { newValue ->
                val min = newValue.toFloatOrNull()
                onFiltersChange(filters.copy(userScoreMin = min))
            },
            label = { Text(stringResource(R.string.filter_min), color = Color.Gray) },
            placeholder = { Text(stringResource(R.string.filter_minScore), color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .focusable(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        
        // Max score
        OutlinedTextField(
            value = filters.userScoreMax?.toString() ?: "",
            onValueChange = { newValue ->
                val max = newValue.toFloatOrNull()
                onFiltersChange(filters.copy(userScoreMax = max))
            },
            label = { Text(stringResource(R.string.filter_max), color = Color.Gray) },
            placeholder = { Text(stringResource(R.string.filter_maxScore), color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .focusable(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
    }
}

/**
 * Vote count range filter
 */
@Composable
fun VoteCountFilter(
    filters: BrowseModels.MediaFilters,
    onFiltersChange: (BrowseModels.MediaFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Min vote count
        OutlinedTextField(
            value = filters.voteCountMin?.toString() ?: "",
            onValueChange = { newValue ->
                val min = newValue.toIntOrNull()
                onFiltersChange(filters.copy(voteCountMin = min))
            },
            label = { Text(stringResource(R.string.filter_min), color = Color.Gray) },
            placeholder = { Text(stringResource(R.string.filter_minRuntime), color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .focusable(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        
        // Max vote count
        OutlinedTextField(
            value = filters.voteCountMax?.toString() ?: "",
            onValueChange = { newValue ->
                val max = newValue.toIntOrNull()
                onFiltersChange(filters.copy(voteCountMax = max))
            },
            label = { Text(stringResource(R.string.filter_max), color = Color.Gray) },
            placeholder = { Text(stringResource(R.string.filter_maxVotes), color = Color.Gray) },
            modifier = Modifier
                .weight(1f)
                .focusable(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Blue,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

