package ca.devmesh.seerrtv.model

import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class SearchResponse(
    val page: Int = 1,
    val results: List<SearchResult> = emptyList(),
    val totalPages: Int = 1,
    val totalResults: Int = 0
)

@Serializable(with = SearchResultSerializer::class)
sealed interface SearchResult {
    val id: Int
    val mediaType: String?
    val overview: String?
    val popularity: Double
    val backdropPath: String?
    val posterPath: String?
    val mediaInfo: MediaInfo?
}

@Serializable
data class TV(
    override val id: Int,
    override val mediaType: String? = null,
    override val overview: String? = null,
    override val popularity: Double = 0.0,
    override val backdropPath: String? = null,
    override val posterPath: String? = null,
    override val mediaInfo: MediaInfo? = null,
    val name: String = "",
    val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
    val firstAirDate: String? = null,
    val genreIds: List<Int> = emptyList(),
    val originCountry: List<String> = emptyList(),
    val originalLanguage: String? = null,
    val originalName: String? = null
) : SearchResult

@Serializable
data class Movie(
    override val id: Int,
    override val mediaType: String? = null,
    override val overview: String? = null,
    override val popularity: Double = 0.0,
    override val backdropPath: String? = null,
    override val posterPath: String? = null,
    override val mediaInfo: MediaInfo? = null,
    val title: String = "",
    val voteAverage: Double = 0.0,
    val voteCount: Int = 0,
    val adult: Boolean = false,
    val genreIds: List<Int> = emptyList(),
    val originalLanguage: String? = null,
    val originalTitle: String? = null,
    val releaseDate: String? = null,
    val video: Boolean = false
) : SearchResult

@Serializable
data class Collection(
    override val id: Int,
    override val mediaType: String? = null,
    override val overview: String? = null,
    override val popularity: Double = 0.0,
    override val backdropPath: String? = null,
    override val posterPath: String? = null,
    override val mediaInfo: MediaInfo? = null,
    val name: String = "",
    val adult: Boolean = false
) : SearchResult

@Serializable
data class Person(
    override val id: Int,
    override val mediaType: String? = null,
    override val overview: String? = null,
    override val popularity: Double = 0.0,
    override val backdropPath: String? = null,
    override val posterPath: String? = null,
    override val mediaInfo: MediaInfo? = null,
    val name: String = "",
    val profilePath: String? = null,
    val knownFor: List<KnownForMedia> = emptyList()
) : SearchResult

@Serializable
data class KnownForMedia(
    val id: Int,
    val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null
)

object SearchResultSerializer : JsonContentPolymorphicSerializer<SearchResult>(SearchResult::class) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out SearchResult> {
        return when (val type = element.jsonObject["mediaType"]?.jsonPrimitive?.content) {
            "movie" -> Movie.serializer()
            "tv" -> TV.serializer()
            "person" -> Person.serializer()
            "collection" -> Collection.serializer()
            null -> {
                when {
                    "title" in element.jsonObject -> Movie.serializer()
                    "name" in element.jsonObject && "firstAirDate" in element.jsonObject -> TV.serializer()
                    "name" in element.jsonObject -> Person.serializer()
                    "name" in element.jsonObject && "adult" in element.jsonObject -> Collection.serializer()
                    else -> throw SerializationException("Cannot determine type for: $element")
                }
            }
            else -> throw SerializationException("Unknown type: $type")
        }
    }
}