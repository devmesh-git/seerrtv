package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable

enum class MediaServerType {
    PLEX,
    JELLYFIN,
    EMBY,
    NOT_CONFIGURED
}

enum class MediaType {
    MOVIE,
    TV
}

enum class RequestStatus(val value: Int) {
    UNKNOWN(1),
    PENDING(2),
    PROCESSING(3),
    PARTIALLY_AVAILABLE(4),
    AVAILABLE(5);

    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: UNKNOWN
    }
}

@Serializable
data class PaginatedMediaResponse(
    val pageInfo: PageInfo,
    val results: List<Media>
)

@Serializable
data class PageInfo(
    val pages: Int,
    val pageSize: Int,
    val results: Int,
    val page: Int
)

@Serializable
data class MediaInfo(
    val downloadStatus: List<DownloadStatus> = emptyList(),
    val downloadStatus4k: List<DownloadStatus> = emptyList(),
    val id: Int,
    val mediaType: String,
    val tmdbId: Int,
    val tvdbId: Int? = null,
    val imdbId: String? = null,
    val status: Int,
    val status4k: Int,
    val createdAt: String,
    val updatedAt: String,
    val lastSeasonChange: String,
    val mediaAddedAt: String? = null,
    val serviceId: Int? = null,
    val serviceId4k: Int? = null,
    val externalServiceId: Int? = null,
    val externalServiceId4k: Int? = null,
    val externalServiceSlug: String? = null,
    val externalServiceSlug4k: String? = null,
    val ratingKey: String? = null,
    val ratingKey4k: String? = null,
    val plexUrl: String? = null,
    val iOSPlexUrl: String? = null,
    val mediaUrl: String? = null,
    val serviceUrl: String? = null,
    val seasons: List<Season>? = null,
    val requests: List<Request>? = null,
    val issues: List<Issue>? = null
)

@Serializable
data class Genre(
    val id: Int,
    val name: String
)

@Serializable
data class Credits(
    val cast: List<CastMember>,
    val crew: List<CrewMember>
)

@Serializable
data class CastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profilePath: String? = null,
    val castId: Int? = null,
    val creditId: String? = null,
    val order: Int? = null,
    val gender: Int? = null
)

@Serializable
data class CrewMember(
    val id: Int,
    val name: String,
    val job: String,
    val department: String,
    val profilePath: String? = null
)

@Serializable
data class ExternalIds(
    val imdbId: String?,
    val facebookId: String?,
    val instagramId: String?,
    val twitterId: String?
)

@Serializable
data class WatchProvider(
    val iso_3166_1: String,
    val link: String,
    val buy: List<Provider>?,
    val flatrate: List<Provider>?
)

@Serializable
data class Provider(
    val displayPriority: Int,
    val logoPath: String,
    val id: Int,
    val name: String
)

@Serializable
data class ProductionCompany(
    val id: Int,
    val name: String,
    val originCountry: String,
    val logoPath: String?
)

@Serializable
data class ProductionCountry(
    val iso_3166_1: String,
    val name: String
)

@Serializable
data class SpokenLanguage(
    val englishName: String? = null,
    val iso_639_1: String? = null,
    val name: String? = null
)

@Serializable
data class Season(
    val id: Int,
    val seasonNumber: Int,
    val name: String? = null,
    val status: Int? = null,
    val status4k: Int? = null,
    val airDate: String? = null,
    val episodeCount: Int? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null
)

@Serializable
data class Network(
    val id: Int,
    val name: String,
    val originCountry: String? = null,
    val logoPath: String? = null
)

@Serializable
data class Keyword(
    val id: Int,
    val name: String
)

@Serializable
data class SimilarMediaResponse(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val results: List<SimilarMediaItem>
)

@Serializable
data class SimilarMediaItem(
    val id: Int,
    val mediaType: String,
    val adult: Boolean? = null, // Only for movies
    val genreIds: List<Int>,
    val originalLanguage: String,
    val originalTitle: String? = null, // Only for movies
    val originalName: String? = null, // Only for TV series
    val overview: String,
    val popularity: Double,
    val releaseDate: String? = null, // Only for movies
    val firstAirDate: String? = null, // Only for TV series
    val title: String? = null, // Only for movies
    val name: String? = null, // Only for TV series
    val video: Boolean? = null, // Only for movies
    val voteAverage: Double,
    val voteCount: Int,
    val backdropPath: String?,
    val posterPath: String?,
    val originCountry: List<String>? = null, // Only for TV series
    val mediaInfo: MediaInfo? = null // Optional Seerr data
)

@Serializable
data class SonarrImage(
    val coverType: String,
    val url: String? = null,
    val remoteUrl: String? = null
)

@Serializable
data class SonarrLookupResult(
    val title: String,
    val sortTitle: String? = null,
    val status: String? = null,
    val ended: Boolean? = null,
    val overview: String? = null,
    val airTime: String? = null,
    val images: List<SonarrImage> = emptyList(),
    val originalLanguage: Language? = null,
    val seasons: List<SonarrSeason> = emptyList(),
    val year: Int,
    val qualityProfileId: Int,
    val seasonFolder: Boolean,
    val monitored: Boolean,
    val monitorNewItems: String,
    val useSceneNumbering: Boolean,
    val runtime: Int,
    val tvdbId: Int,
    val tvRageId: Int,
    val tvMazeId: Int,
    val tmdbId: Int,
    val firstAired: String? = null,
    val lastAired: String? = null,
    val seriesType: String,
    val cleanTitle: String,
    val titleSlug: String,
    val folder: String,
    val genres: List<String> = emptyList(),
    val tags: List<Int> = emptyList(),
    val added: String,
    val ratings: SonarrRatings? = null,
    val statistics: SonarrStatistics? = null,
    val languageProfileId: Int
)

@Serializable
data class SonarrSeason(
    val seasonNumber: Int,
    val monitored: Boolean
)

@Serializable
data class Language(
    val id: Int,
    val name: String
)

@Serializable
data class SonarrRatings(
    val votes: Int,
    val value: Double
)

@Serializable
data class SonarrStatistics(
    val seasonCount: Int,
    val episodeFileCount: Int,
    val episodeCount: Int,
    val totalEpisodeCount: Int,
    val sizeOnDisk: Long,
    val percentOfEpisodes: Double
)
