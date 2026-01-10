package ca.devmesh.seerrtv.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import androidx.annotation.StringRes
import ca.devmesh.seerrtv.R

@Serializable
data class Issue(
    val id: Int,
    val issueType: Int,
    val status: Int,
    val problemSeason: Int,
    val problemEpisode: JsonElement, // String for TV series, Int (0) for movies
    val createdAt: String,
    val updatedAt: String,
    val media: MediaInfo? = null,
    val createdBy: IssueUser? = null,
    val modifiedBy: IssueUser? = null,
    val comments: List<IssueComment> = emptyList()
)

@Serializable
data class IssueComment(
    val id: Int,
    val message: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class IssueUser(
    val id: Int,
    val email: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val plexUsername: String? = null,
    val jellyfinUsername: String? = null,
    val avatar: String? = null,
    val permissions: Int? = null,
    val userType: Int? = null,
    val plexId: Int? = null,
    val jellyfinUserId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val requestCount: Int? = null
)

@Serializable
data class CreateIssueRequest(
    val issueType: Int,
    val message: String,
    val mediaId: Int,
    val problemSeason: Int,
    val problemEpisode: JsonElement
)

data class PrecannedIssue(
    val id: String,
    @StringRes val descriptionResId: Int,
    val category: IssueType
)

enum class IssueType(val value: Int) {
    VIDEO(1),
    AUDIO(2),
    SUBTITLE(3),
    OTHER(4)
}

enum class IssueStatus(val value: Int) {
    OPEN(1),
    RESOLVED(3)
}

val videoIssues = listOf(
    PrecannedIssue("video_quality", R.string.issue_video_quality, IssueType.VIDEO),
    PrecannedIssue("video_stuttering", R.string.issue_video_stuttering, IssueType.VIDEO),
    PrecannedIssue("video_aspect", R.string.issue_video_aspect, IssueType.VIDEO),
    PrecannedIssue("video_playback", R.string.issue_video_playback, IssueType.VIDEO),
    PrecannedIssue("video_sync", R.string.issue_video_sync, IssueType.VIDEO)
)

val audioIssues = listOf(
    PrecannedIssue("audio_missing", R.string.issue_audio_missing, IssueType.AUDIO),
    PrecannedIssue("audio_quality", R.string.issue_audio_quality, IssueType.AUDIO),
    PrecannedIssue("audio_sync", R.string.issue_audio_sync, IssueType.AUDIO),
    PrecannedIssue("audio_language", R.string.issue_audio_language, IssueType.AUDIO),
    PrecannedIssue("audio_stuttering", R.string.issue_audio_stuttering, IssueType.AUDIO)
)

val subtitleIssues = listOf(
    PrecannedIssue("subtitle_missing", R.string.issue_subtitle_missing, IssueType.SUBTITLE),
    PrecannedIssue("subtitle_sync", R.string.issue_subtitle_sync, IssueType.SUBTITLE),
    PrecannedIssue("subtitle_language", R.string.issue_subtitle_language, IssueType.SUBTITLE),
    PrecannedIssue("subtitle_display", R.string.issue_subtitle_display, IssueType.SUBTITLE),
    PrecannedIssue("subtitle_quality", R.string.issue_subtitle_quality, IssueType.SUBTITLE)
)

val otherIssues = listOf(
    PrecannedIssue("wrong_content", R.string.issue_other_wrong_content, IssueType.OTHER),
    PrecannedIssue("missing_content", R.string.issue_other_missing_content, IssueType.OTHER),
    PrecannedIssue("incorrect_metadata", R.string.issue_other_incorrect_metadata, IssueType.OTHER),
    PrecannedIssue("app_crash", R.string.issue_other_app_crash, IssueType.OTHER),
    PrecannedIssue("other", R.string.issue_other_other, IssueType.OTHER)
)

