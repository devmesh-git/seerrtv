package ca.devmesh.seerrtv.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.devmesh.seerrtv.model.MediaDetails
import ca.devmesh.seerrtv.model.MediaType
import ca.devmesh.seerrtv.model.Network
import ca.devmesh.seerrtv.model.ProductionCompany
import ca.devmesh.seerrtv.model.Provider
import ca.devmesh.seerrtv.R
import ca.devmesh.seerrtv.util.CommonUtil
import ca.devmesh.seerrtv.util.SharedPreferencesUtil
import kotlin.math.round
import java.util.Locale

private const val RELEASE_TYPE_THEATRICAL = 3
private const val RELEASE_TYPE_DIGITAL = 4
private const val RELEASE_TYPE_PHYSICAL = 5



/**
 * Returns (theatricalDate, digitalDate, physicalDate) for the given region from releases.
 * Each date is trimmed to yyyy-MM-dd for CommonUtil.formatDate. Returns nulls when no data.
 */
private fun getMovieReleaseDatesByType(mediaDetails: MediaDetails, region: String): Triple<String?, String?, String?> {
    val results = mediaDetails.releases?.results ?: return Triple(null, null, null)
    val country = results.find { it.iso_3166_1 == region } ?: results.firstOrNull() ?: return Triple(null, null, null)
    val dates = country.releaseDates ?: return Triple(null, null, null)
    fun dateForType(type: Int): String? =
        dates.firstOrNull { it.type == type && !it.releaseDate.isNullOrBlank() }?.releaseDate?.take(10)
    return Triple(
        dateForType(RELEASE_TYPE_THEATRICAL),
        dateForType(RELEASE_TYPE_DIGITAL),
        dateForType(RELEASE_TYPE_PHYSICAL)
    )
}

@Composable
fun MediaInfoTable(
    mediaDetails: MediaDetails,
    modifier: Modifier = Modifier,
    context: Context = LocalContext.current
) {
    Column(modifier = modifier) {
        // Debug logging for ratings
        android.util.Log.d("MediaInfoTable", "Ratings debug for ${mediaDetails.name ?: mediaDetails.title}:")
        android.util.Log.d("MediaInfoTable", "  ratings object: ${mediaDetails.ratings}")
        android.util.Log.d("MediaInfoTable", "  rt object: ${mediaDetails.ratings?.rt}")
        android.util.Log.d("MediaInfoTable", "  rt criticsScore: ${mediaDetails.ratings?.rt?.criticsScore}")
        android.util.Log.d("MediaInfoTable", "  rt audienceScore: ${mediaDetails.ratings?.rt?.audienceScore}")
        android.util.Log.d("MediaInfoTable", "  imdb object: ${mediaDetails.ratings?.imdb}")
        android.util.Log.d("MediaInfoTable", "  imdb criticsScore: ${mediaDetails.ratings?.imdb?.criticsScore}")
        android.util.Log.d("MediaInfoTable", "  voteAverage: ${mediaDetails.voteAverage}")
        
        // Ratings row
        val hasRottenTomatoesCriticsRating = mediaDetails.ratings?.rt?.criticsScore != null && mediaDetails.ratings.rt.criticsScore > 0
        val hasRottenTomatoesAudienceRating = mediaDetails.ratings?.rt?.audienceScore != null && mediaDetails.ratings.rt.audienceScore > 0
        val hasImdbRating = mediaDetails.ratings?.imdb?.criticsScore != null && mediaDetails.ratings.imdb.criticsScore > 0
        val hasTmdbRating = mediaDetails.voteAverage > 0
        
        android.util.Log.d("MediaInfoTable", "  hasRottenTomatoesCriticsRating: $hasRottenTomatoesCriticsRating")
        android.util.Log.d("MediaInfoTable", "  hasRottenTomatoesAudienceRating: $hasRottenTomatoesAudienceRating")
        android.util.Log.d("MediaInfoTable", "  hasImdbRating: $hasImdbRating")
        android.util.Log.d("MediaInfoTable", "  hasTmdbRating: $hasTmdbRating")

        if (hasRottenTomatoesCriticsRating || hasRottenTomatoesAudienceRating || hasImdbRating || hasTmdbRating) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // First row: Rotten Tomatoes ratings
                if (hasRottenTomatoesCriticsRating || hasRottenTomatoesAudienceRating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Rotten Tomatoes Critics Rating
                        if (hasRottenTomatoesCriticsRating) {
                            mediaDetails.ratings.rt.let { rt ->
                                RatingItem(
                                    iconResId = if (rt.criticsRating == "Rotten") {
                                        R.drawable.rotten_tomatoes_green_icon
                                    } else {
                                        R.drawable.rotten_tomatoes_red_icon
                                    },
                                    rating = "${rt.criticsScore}%",
                                    contentDescription = stringResource(R.string.mediaInfoTable_rottenTomatoesRating)
                                )
                            }
                        }

                        // Spacer between RT ratings
                        if (hasRottenTomatoesCriticsRating && hasRottenTomatoesAudienceRating) {
                            Spacer(modifier = Modifier.width(24.dp))
                        }

                        // Rotten Tomatoes Audience Rating
                        if (hasRottenTomatoesAudienceRating) {
                            mediaDetails.ratings.rt.let { rt ->
                                RatingItem(
                                    iconResId = R.drawable.rotten_tomatoes_popcorn_icon,
                                    rating = "${rt.audienceScore}%",
                                    contentDescription = stringResource(R.string.mediaInfoTable_rottenTomatoesRating)
                                )
                            }
                        }
                    }
                }

                // Spacer between rows
                if ((hasRottenTomatoesCriticsRating || hasRottenTomatoesAudienceRating) && (hasImdbRating || hasTmdbRating)) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Second row: IMDB and TMDB ratings
                if (hasImdbRating || hasTmdbRating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // IMDB Rating
                        if (hasImdbRating) {
                            mediaDetails.ratings.imdb.let { imdb ->
                                RatingItem(
                                    iconResId = R.drawable.imdb_icon,
                                    rating = "${imdb.criticsScore}",
                                    contentDescription = stringResource(R.string.mediaInfoTable_rottenTomatoesRating)
                                )
                            }
                        }

                        // Spacer between IMDB and TMDB
                        if (hasImdbRating && hasTmdbRating) {
                            Spacer(modifier = Modifier.width(24.dp))
                        }

                        // TMDB Rating - Fixed calculation using proper rounding
                        if (hasTmdbRating) {
                            RatingItem(
                                iconResId = R.drawable.tmdb_icon,
                                rating = "${round(mediaDetails.voteAverage * 10).toInt()}%",
                                contentDescription = stringResource(R.string.mediaInfoTable_tmdbRating)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = Color.Gray, thickness = 1.dp)
        }

        val appLang = SharedPreferencesUtil.getAppLanguage(context)
        val locale = if (!appLang.isNullOrBlank()) Locale.forLanguageTag(appLang) else (if (!context.resources.configuration.locales.isEmpty) context.resources.configuration.locales[0] else Locale.getDefault())
        when (mediaDetails.mediaType) {
            MediaType.TV -> TvInfoRows(mediaDetails, locale)
            MediaType.MOVIE -> MovieInfoRows(
                mediaDetails = mediaDetails,
                releaseDateRegion = CommonUtil.getRegionForLanguage(SharedPreferencesUtil.getDiscoveryLanguage(context)),
                locale = locale
            )
            null -> {} // Handle null case
        }
    }
}

@Composable
private fun TvInfoRows(mediaDetails: MediaDetails, locale: Locale) {
    mediaDetails.originalTitle?.let { InfoRow(stringResource(R.string.mediaInfoTable_originalTitle), it) }
    InfoRow(stringResource(R.string.mediaInfoTable_status), mediaDetails.status)
    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
    InfoRow(stringResource(R.string.mediaInfoTable_numberOfSeasons), mediaDetails.numberOfSeasons.toString())
    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
    InfoRow(stringResource(R.string.mediaInfoTable_numberOfEpisodes), mediaDetails.numberOfEpisodes.toString())
    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
    InfoRow(stringResource(R.string.mediaInfoTable_firstAirDate), CommonUtil.formatDateShort(mediaDetails.firstAirDate, locale))
    if (mediaDetails.inProduction == false) {
        InfoRow(stringResource(R.string.mediaInfoTable_lastAirDate), CommonUtil.formatDateShort(mediaDetails.lastAirDate, locale))
    }
    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
    InfoRow(stringResource(R.string.mediaInfoTable_productionCountry), mediaDetails.productionCountries.firstOrNull()?.name ?: "")
    mediaDetails.networks?.let { NetworkInfoRow(it) }
}

@Composable
private fun MovieInfoRows(
    mediaDetails: MediaDetails,
    releaseDateRegion: String,
    locale: Locale
) {
    InfoRow(stringResource(R.string.mediaInfoTable_status), mediaDetails.status)
    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
    val (theatricalDate, digitalDate, physicalDate) = getMovieReleaseDatesByType(mediaDetails, releaseDateRegion)
    val hasReleaseDates = theatricalDate != null || digitalDate != null || physicalDate != null
    if (hasReleaseDates) {
        val theatricalDisplay = theatricalDate ?: mediaDetails.releaseDate?.take(10)
        theatricalDisplay?.let { InfoRow(stringResource(R.string.mediaInfoTable_releaseDateTheatrical), CommonUtil.formatDateShort(it, locale)) }
        digitalDate?.let { InfoRow(stringResource(R.string.mediaInfoTable_releaseDateDigital), CommonUtil.formatDateShort(it, locale)) }
        physicalDate?.let { InfoRow(stringResource(R.string.mediaInfoTable_releaseDatePhysical), CommonUtil.formatDateShort(it, locale)) }
    } else {
        InfoRow(stringResource(R.string.mediaInfoTable_releaseDate), CommonUtil.formatDateShort(mediaDetails.releaseDate, locale))
    }
    if (mediaDetails.revenue != null && mediaDetails.revenue > 0) {
        InfoRow(stringResource(R.string.mediaInfoTable_revenue), CommonUtil.formatDollars(mediaDetails.revenue))
    }
    if (mediaDetails.budget != null && mediaDetails.budget > 0) {
        InfoRow(stringResource(R.string.mediaInfoTable_budget), CommonUtil.formatDollars(mediaDetails.budget))
    }
    InfoRow(stringResource(R.string.mediaInfoTable_productionCountry), mediaDetails.productionCountries.firstOrNull()?.name ?: "")
    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
    StudiosInfoRow(mediaDetails.productionCompanies)
    val watchProviders = mediaDetails.watchProviders
    if (watchProviders.isNotEmpty()) {
        val usProviders = watchProviders.find { it.iso_3166_1 == "US" }
        val flatRateProviders = usProviders?.flatrate ?: emptyList()
        if (flatRateProviders.isNotEmpty()) {
            HorizontalDivider(color = Color.Gray, thickness = 1.dp)
            StreamingProvidersInfoRow(flatRateProviders)
        }
    }
}

@Composable
private fun StudiosInfoRow(studios: List<ProductionCompany>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.mediaInfoTable_studios),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            color = Color.White
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column(modifier = Modifier.weight(1f)) {
            when {
                studios.isEmpty() -> Text(
                    text = stringResource(R.string.mediaInfoTable_notApplicable),
                    color = Color.White
                )

                studios.size <= 4 -> {
                    studios.forEach { studio ->
                        Text(
                            text = studio.name,
                            color = Color.White
                        )
                    }
                }

                else -> {
                    studios.take(3).forEach { studio ->
                        Text(
                            text = studio.name,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "(${studios.size - 3} ${stringResource(R.string.mediaInfoTable_more)})",
                        color = Color.White,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingProvidersInfoRow(providers: List<Provider>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.mediaInfoTable_currentlyStreamingOn),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            color = Color.White
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column(modifier = Modifier.weight(1f)) {
            when {
                providers.size <= 4 -> {
                    providers.forEach { provider ->
                        Text(
                            text = provider.name,
                            color = Color.White
                        )
                    }
                }
                else -> {
                    providers.take(3).forEach { provider ->
                        Text(
                            text = provider.name,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "(${providers.size - 3} ${stringResource(R.string.mediaInfoTable_more)})",
                        color = Color.White,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkInfoRow(networks: List<Network>) {
    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.mediaInfoTable_network),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            color = Color.White
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column(modifier = Modifier.weight(1f)) {
            when {
                networks.isEmpty() -> Text(
                    text = stringResource(R.string.mediaInfoTable_notApplicable),
                    color = Color.White
                )

                networks.size <= 4 -> {
                    networks.forEach { network ->
                        Text(
                            text = network.name,
                            color = Color.White
                        )
                    }
                }

                else -> {
                    networks.take(3).forEach { network ->
                        Text(
                            text = network.name,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "(${networks.size - 3} ${stringResource(R.string.mediaInfoTable_more)})",
                        color = Color.White,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            color = Color.White
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = Color.White
        )
    }
}

@Composable
private fun RatingItem(
    iconResId: Int,
    rating: String,
    contentDescription: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = rating,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}
