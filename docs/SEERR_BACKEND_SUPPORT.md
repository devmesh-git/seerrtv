# Backend support: Seerr, Jellyseerr, Overseerr

## Which backend SeerrTV targets

**[Seerr](https://seerr.dev) ([seerr-team/seerr](https://github.com/seerr-team/seerr)) is the primary
backend.** Seerr is the merger of Overseerr and Jellyseerr into a single application supporting
Plex, Jellyfin and Emby.

**Jellyseerr and Overseerr remain supported as legacy alternates.** Features are designed against
Seerr first and degrade gracefully on the older backends — never the other way round.

Practical consequences when working on the app:

- Check server behaviour against `seerr-team/seerr` (default branch `develop`), not the archived
  Overseerr or `fallenbagel/jellyseerr` repos. Their API shapes have diverged.
- **"Blacklist" is the legacy Jellyseerr spelling of Seerr's "blocklist".** Seerr renamed the
  concept and migrated its settings keys (`hideBlacklisted` → `hideBlocklisted`), keeping
  `/api/v1/blacklist` as a deprecated alias of `/api/v1/blocklist`. Permission bit values did not
  change. When reading a setting whose key was renamed, accept both spellings.
- `SeerrApiService.ServerType` already distinguishes `SEERR`, `JELLYSEERR` and `OVERSEERR`; gate
  anything backend-specific on it rather than assuming.

## Filtering that the server does *not* do for you

Seerr returns blocklisted and already-available titles to **every** API caller and hides them in
its web client instead. Any client — including SeerrTV — has to reproduce that filtering itself,
or it will show users content they cannot see in a browser.

SeerrTV's port lives in
[`MediaVisibilityFilter.kt`](../tv/src/main/java/ca/devmesh/seerrtv/data/MediaVisibilityFilter.kt)
and is applied in the API layer as responses are unwrapped. It mirrors two independent layers:

| Layer | Source in Seerr | Rule |
| --- | --- | --- |
| Permission (always on) | `Common/ListView`, `MediaSlider` | A user without `MANAGE_BLOCKLIST` **or** `VIEW_BLOCKLIST` never sees media whose status is `BLOCKLISTED` (6). `ADMIN` satisfies any permission check. |
| Settings | `useDiscover` | Public setting `hideAvailable` drops status 4/5 for everyone; `hideBlocklisted` drops status 6 for users who hold `MANAGE_BLOCKLIST`. |

Per-surface policy, matching the web client exactly:

| Surface | Permission layer | Settings layer |
| --- | --- | --- |
| Discover rows, browse grids, genre/keyword/studio/network, custom sliders, similar, watchlist | yes | yes |
| Search | yes | no |
| Recently Added, requests, person credits | no | no |

Both settings come from `GET /api/v1/settings/public` and are cached per connection, cleared
alongside the other service caches on a profile or connection change.

### Media status values

`server/constants/media.ts`: `UNKNOWN = 1`, `PENDING = 2`, `PROCESSING = 3`,
`PARTIALLY_AVAILABLE = 4`, `AVAILABLE = 5`, `BLOCKLISTED = 6`, `DELETED = 7`.

Requesting a blocklisted title is rejected server-side, so the details screen suppresses the
Request button for status 6 rather than offering an action that can only fail.

### API key vs per-user login

An `X-Api-Key` connection authenticates as the Seerr **admin**, and admins legitimately see
blocklisted titles in the web UI too. Per-user visibility requires a per-user login (Local,
Jellyfin/Emby, or Plex). This is the usual explanation when a user reports that SeerrTV shows
content their Seerr account should not see.
