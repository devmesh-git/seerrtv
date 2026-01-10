package ca.devmesh.seerrtv.model

enum class AuthType(val type: String) {
    ApiKey("apiKey"),
    LocalUser("localUser"),
    Jellyfin("jellyfin"),
    Emby("emby"),
    Plex("plex")
}