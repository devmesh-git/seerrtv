# Changelog

## 0.26.8

### Bug Fixes

#### Jellyfin Authentication
- **Fixed "Jellyfin hostname already configured" auth failure** – When Jellyfin returns this error, the app now retries with a simple username/password login (`/auth/jellyfin`) instead of the full request body, allowing authentication to succeed when the hostname is already set.
- **Added JellyfinLoginRequest model** – New serializable data class for the simple Jellyfin login payload used in the retry flow.

### Documentation / Project

#### README
- **Updated badges** – Added Cursor AI development badge, Discord badge, and applied `style=for-the-badge` to license, version, GitHub release, and downloads badges for consistent styling.

### Files Modified
- `README.md` – Cursor and Discord badges; badge styling.
- `tv/build.gradle.kts` – Bumped version to 0.26.8 (versionCode 111).
- `tv/src/main/java/ca/devmesh/seerrtv/data/SeerrApiService.kt` – Added `JellyfinLoginRequest`, retry logic for "Jellyfin hostname already configured" with simple login.

---

## 0.26.7

### Bug Fixes

#### Splash / Update Flow
- **Fixed race between update check and config validation** – On direct flavor, config validation (and `onContinue`) now waits for the startup update check to finish, so the app no longer navigates away from splash before deciding whether to show the update dialog.
- **Update dialog dismiss now clears state** – Closing the update-available dialog (via back or dismiss) now sets `showUpdateDialog = false` and clears `updateInfoForDialog`, so the dialog closes correctly and state stays in sync.

#### Config / Language
- **Language step only when no language set** – The config wizard shows the language selection step only when the user has not yet chosen a language (`SharedPreferencesUtil.getAppLanguage(context) == null`); otherwise it goes straight to config selection.
- **Language preference no longer auto-persisted** – System language is used for the session only when no preference exists; the app no longer writes a resolved system language to preferences until the user explicitly selects a language on the language screen.

### Technical Improvements

#### Config Screen – Language Selection UX
- **Simplified language step navigation** – Removed the separate focusable “Next” button; Enter on a language now both selects it and advances. D-pad navigation is list-only (no index -1 for Next).
- **Language step layout** – `ConfigStepLayout` for the language step no longer receives `onNext`, `nextButtonText`, or `nextButtonFocused`; selection + advance is handled by the controller.

#### Update System
- **Version comparison testability** – `compareVersions` in `UpdateManager` is now a top-level `internal` function so it can be covered by unit tests.
- **Unit tests for version comparison** – Added `UpdateManagerVersionTest` to verify semantic version comparison and the direct-flavor “update available” logic.

### Files Modified
- `tv/build.gradle.kts` – Bumped version to 0.26.7 (versionCode 110).
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` – Added `updateCheckComplete` state and gated config validation on it; passed through to `SplashScreen`.
- `tv/src/main/java/ca/devmesh/seerrtv/ui/ConfigScreen.kt` – Language step visibility from stored preference; simplified language controller (no Next focus); language step uses null next button.
- `tv/src/main/java/ca/devmesh/seerrtv/ui/SettingsMenu.kt` – Update dialog `onDismissRequest` and `onClose` clear `showUpdateDialog` and `updateInfoForDialog`.
- `tv/src/main/java/ca/devmesh/seerrtv/ui/SplashScreen.kt` – Added `updateCheckComplete` parameter; `onContinue` only when update check complete and (auth complete or not configured).
- `tv/src/main/java/ca/devmesh/seerrtv/util/SharedPreferencesUtil.kt` – `resolveAppLanguage` no longer persists system language; only persists when user selects on language screen.
- `tv/src/main/java/ca/devmesh/seerrtv/util/UpdateManager.kt` – Moved `compareVersions` to top-level `internal fun` for tests.

### Files Added
- `tv/src/test/java/ca/devmesh/seerrtv/util/UpdateManagerVersionTest.kt` – Unit tests for `compareVersions` and update-availability logic.

---

## 0.26.6

### Bug Fixes

#### Splash / Update Flow
- **Fixed splash behavior after update dialog closes** - closing the update dialog now keeps the splash screen visible until the user explicitly continues (direct flavor) or automatic navigation occurs (Play flavor), preventing premature transition away from splash.

#### Issue Models Annotation
- **Resolved future Kotlin annotation target warning** - updated `PrecannedIssue` to use `@param:StringRes` so the `descriptionResId` annotation target remains stable with upcoming Kotlin changes.

### Technical Improvements

#### Build & Tooling
- **Refined Kotlin plugin wiring** - switched top-level Kotlin serialization plugin to `id("org.jetbrains.kotlin.plugin.serialization") … apply false` and applied it only in the TV module for clearer, AGP 9–compatible configuration.
- **Hilt plugin compatibility update** - bumped Hilt to `2.59` (compiler and runtime) to align with AGP 9.0.0 and Kotlin 2.2.10.
- **Deprecated Android Gradle flags cleaned up** - removed obsolete migration toggles from `gradle.properties` and adopted current defaults to eliminate AGP deprecation warnings.
- **KSP + built‑in Kotlin bridge** - enabled `android.disallowKotlinSourceSets=false` to allow KSP’s current `kotlin.sourceSets` usage to work with AGP 9’s built‑in Kotlin integration.
- **Version catalog consolidation** - moved the JUnit 4 test dependency to the Gradle version catalog and updated the TV module to use `libs.junit` for unit tests.

#### Build Output Visibility
- **Automatic build output printing** - added a `printBuildOutputs` Gradle task in the TV module and wired it to run after key assemble tasks so APK (and future AAB) paths are printed at the end of TV builds.

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` – adjusted splash/update dialog flow so closing the dialog no longer forces splash off, and `onContinue` controls navigation for direct flavor only.
- `tv/src/main/java/ca/devmesh/seerrtv/model/IssueModels.kt` – updated `PrecannedIssue` to use `@param:StringRes` for `descriptionResId`.
- `tv/build.gradle.kts` – bumped version to 0.26.6 (versionCode 109), applied Kotlin serialization plugin explicitly, and added `printBuildOutputs` task plus assemble hooks.
- `build.gradle.kts` – switched Kotlin serialization plugin to `id("org.jetbrains.kotlin.plugin.serialization") … apply false`.
- `gradle/libs.versions.toml` – updated Hilt Android and compiler to 2.59 and added a version-catalog entry for JUnit.
- `gradle.properties` – removed deprecated AGP flags, set `android.dependency.useConstraints=false`, disabled configuration cache comment, and added `android.disallowKotlinSourceSets=false`.

---

## 0.26.5

### Bug Fixes

#### Settings Menu - Default Streaming Region Navigation
- **Fixed DPAD navigation in Default Streaming Region submenu** - users can now navigate up/down the list and select items using DPAD controls
- **Removed mouse click handlers** - RadioButton onClick handlers removed to prevent mouse-only interactions on Android TV
- **Unified navigation pattern** - Default Streaming Region submenu now follows the same navigation pattern as Discovery Language submenu
- **Enhanced controller integration** - controller now properly handles up/down navigation and Enter key selection for Default Streaming Region

### Technical Improvements

#### Settings Menu Controller Enhancements
- **Added regions state management** - controller now tracks regions count and list for proper navigation bounds
- **Improved key event handling** - Default Streaming Region navigation now handled through controller's handleKeyEvent method
- **Consistent navigation flow** - all submenus now use the same navigation pattern for better maintainability
- **Removed conflicting event handlers** - eliminated onPreviewKeyEvent that was interfering with controller's key handling

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/ui/SettingsMenu.kt` - Fixed DPAD navigation for Default Streaming Region submenu, removed onClick handlers, enhanced controller integration

---

## 0.26.4

### Major Features

#### GitHub Releases Integration
- **Migrated update system to GitHub Releases API** - direct flavor now fetches updates directly from GitHub Releases instead of custom update.json files
- **Automatic update detection** - app checks `https://api.github.com/repos/devmesh-git/seerrtv/releases/latest` for the latest release
- **Semantic version comparison** - uses semantic versioning (e.g., "0.26.4" vs "0.26.3") to determine if updates are available
- **Automatic APK detection** - automatically finds and uses the first `.apk` file in GitHub release assets

### Technical Improvements

#### Update System Refactoring
- **Removed legacy update.json system** - completely removed custom JSON update file generation and parsing
- **Simplified UpdateManager** - streamlined code to only handle GitHub Releases API format
- **Cleaner build process** - removed `generateUpdateJson` Gradle task and related build configuration
- **Enhanced error handling** - improved error messages and logging for GitHub API interactions

#### Build Configuration
- **Removed WriteUpdateJsonTask** - eliminated custom Gradle task for generating update.json
- **Simplified build outputs** - direct release builds now only generate APK files
- **Updated build messages** - added guidance to upload APK to GitHub Releases

### Documentation Updates
- **Updated README.md** - removed references to legacy update.json system
- **Updated release workflow** - documented GitHub Releases upload process
- **Updated sideload builds section** - now points to GitHub Releases instead of legacy website

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/util/UpdateManager.kt` - Migrated to GitHub Releases API, removed legacy JSON parsing
- `tv/src/main/java/ca/devmesh/seerrtv/ui/SplashScreen.kt` - Updated default update URL to GitHub API
- `tv/src/main/java/ca/devmesh/seerrtv/ui/SettingsMenu.kt` - Updated default update URL to GitHub API
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` - Updated hardcoded update URL to GitHub API
- `tv/build.gradle.kts` - Removed WriteUpdateJsonTask and generateUpdateJson task registration, simplified build output
- `README.md` - Updated to reflect GitHub Releases workflow and removed legacy update.json references

---

## 0.26.3

### Major Features

#### First Open Source Release
- **Initial open source release** - SeerrTV is now fully open source and available to the community
- **Comprehensive documentation** - added complete documentation suite for contributors and users
- **Community infrastructure** - established GitHub workflows and templates for open source collaboration

### Documentation & Project Infrastructure

#### New Documentation Files
- **CHANGELOG.md** - comprehensive version history and release notes
- **LICENSE** - open source license (Apache 2.0)
- **CONTRIBUTING.md** - contributor guidelines and development workflow
- **CODE_OF_CONDUCT.md** - community code of conduct
- **SECURITY.md** - security policy and reporting guidelines
- **README.md** - comprehensive project documentation with features, setup, and usage

#### GitHub Infrastructure
- **Issue templates** - bug report and feature request templates for better issue tracking
- **Pull request template** - standardized PR template for contributions
- **FUNDING.yml** - funding configuration for project support

### Codebase Cleanup

#### Project Organization
- **Enhanced .gitignore** - improved IDE settings and build artifact exclusions
- **Code style configuration** - standardized formatting rules for consistent codebase
- **IDE configuration** - added proper IDE settings for Android Studio and VSCode
- **Build configuration cleanup** - removed unused configurations and streamlined build process

#### UI/UX Improvements
- **SplashScreen layout improvements** - improved version display and centered message scrolling
- **Branding removal** - removed commercial branding elements for open source release

### Files Added
- `.github/ISSUE_TEMPLATE/bug_report.md` - Bug report template
- `.github/ISSUE_TEMPLATE/feature_request.md` - Feature request template
- `.github/pull_request_template.md` - Pull request template
- `.github/FUNDING.yml` - Funding configuration
- `CHANGELOG.md` - Version history
- `LICENSE` - Apache 2.0 License
- `CONTRIBUTING.md` - Contribution guidelines
- `CODE_OF_CONDUCT.md` - Code of conduct
- `SECURITY.md` - Security policy
- `.cursor/rules/seerrtv.mdc` - Cursor IDE rules
- `.idea/` configuration files - IDE settings
- `.vscode/` configuration files - VSCode settings
- `docs/CLOUDFLARE_CONFIGURATION.md` - Cloudflare setup documentation
- `docs/SIGNING_SETUP.md` - Signing configuration documentation

### Files Modified
- `.gitignore` - Enhanced with additional IDE settings
- `tv/src/main/java/ca/devmesh/seerrtv/ui/SplashScreen.kt` - Improved layout and message scrolling
- `README.md` - Complete rewrite for open source release
- Build configuration files - Cleanup and standardization

---

## 0.26.2

### Major Features

#### Application Localization
- **Independent App Language** - User interface language is now independent of Discovery Language
- **Language Selection Flow** - Initial setup wizard now begins with language selection
- **Automatic Migration** - Existing users automatically use system default language (if supported) or English
- **Settings Integration** - New "App Language" option in Settings Menu for changing UI language on the fly

### UI/UX Improvements

#### Settings Menu Enhancements
- **Refined Menu Order** - Reorganized menu for better usability: Config, App Language, Discovery, Region, Folder, Clock, Update, About
- **Smart Auto-Scrolling** - Menu now proactively scrolls to keep selected items visible and away from screen edges
- **Title Visibility** - Auto-scroll ensures "Settings" title is always visible when navigating to the top
- **Smoother Navigation** - Improved index handling and scroll animations

### Bug Fixes

#### Localization & UI
- **Fixed Settings Menu Ambiguity** - Resolved overload resolution ambiguity in `SettingsMenu.kt`
- **Missing Translations** - Added `settingsMenu_appLanguage` string resource to all supported languages (de, es, fr, ja, nl, pt, zh)
- **Compilation Fixes** - Resolved syntax errors in ConfigScreen wizard logic

### Dependency Updates

#### Core Dependencies
- **Kotlin**: 2.1.0 → 2.1.21
- **KSP**: 2.1.0-1.0.28 → 2.1.21-2.0.1 (upgraded to KSP2)
- **Ktor**: 3.3.0 → 3.3.3
- **Android Gradle Plugin**: 8.13.1 → 8.13.2

#### AndroidX Libraries
- **Compose BOM**: 2025.09.00 → 2025.12.01
- **Foundation**: 1.9.1 → 1.10.0
- **UI**: 1.9.1 → 1.10.0
- **Lifecycle Runtime KTX**: 2.9.4 → 2.10.0
- **Lifecycle ViewModel KTX**: 2.9.4 → 2.10.0
- **Lifecycle Runtime Compose**: 2.9.4 → 2.10.0
- **Activity Compose**: 1.11.0 → 1.12.2
- **Navigation Compose**: 2.9.4 → 2.9.6
- **Webkit**: 1.14.0 → 1.15.0

#### Other Dependencies
- **Hilt Android**: 2.57.1 → 2.57.2
- **Hilt Compiler**: 2.57.1 → 2.57.2
- **ZXing Core**: 3.5.3 → 3.5.4
- **Gradle Versions Plugin**: 0.52.0 → 0.53.0

### Build Configuration

#### Gradle Settings
- **Increased Metaspace Memory** - MaxMetaspaceSize increased from 512m to 1024m to resolve out-of-memory errors during compilation
- **Updated Java Home Path** - Changed to use Android Studio's bundled JBR
- **Enhanced Plugin Resolution** - Added Maven Central as first repository and KSP resolution strategy in `settings.gradle.kts`
- **Version Catalog Integration** - Updated build files to use version catalog for Kotlin version instead of hardcoded values

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` - Locale context wrapping
- `tv/src/main/java/ca/devmesh/seerrtv/ui/ConfigScreen.kt` - Added Language Selection step
- `tv/src/main/java/ca/devmesh/seerrtv/ui/SettingsMenu.kt` - Added App Language, reordered items, fixed scrolling
- `tv/src/main/java/ca/devmesh/seerrtv/util/LocaleContextWrapper.kt` - New utility for context wrapping
- `tv/src/main/java/ca/devmesh/seerrtv/util/SharedPreferencesUtil.kt` - App language persistence
- `tv/src/main/res/values-*/strings.xml` - Added missing translation
- `tv/build.gradle.kts` - Version bump to 0.26.2 (versionCode 105)
- `build.gradle.kts` - Updated to use version catalog for Kotlin version
- `gradle/libs.versions.toml` - Updated all dependency versions
- `gradle.properties` - Increased MetaspaceSize to 1024m, updated Java home path
- `settings.gradle.kts` - Added Maven Central first, KSP resolution strategy

---

## 0.26.1

### Bug Fixes

#### Authentication Race Condition
- **Fixed intermittent authentication failures on app startup** - resolved issue where first authentication attempt would fail with 500 error but subsequent attempts would succeed
- **Added client initialization delay** - 100ms delay after HTTP client refresh to ensure client is fully ready before making requests
- **Improved error handling in refreshClient()** - gracefully handles cases where client might already be closed
- **Enhanced logging** - added token validation logging to help diagnose authentication issues

#### Authentication Retry Logic
- **Automatic retry for transient 500 errors** - authentication now automatically retries up to 2 times (3 total attempts) when receiving 500 server errors
- **Smart retry timing** - 500ms delay between retry attempts to allow server/client to stabilize
- **Better error reporting** - distinguishes between retryable 500 errors and other authentication failures

#### Filter Drawer Performance
- **Optimized scroll behavior** - filter list scrolling now only occurs when selected item is outside visible viewport
- **Reduced unnecessary animations** - prevents scrolling when item is already visible, improving performance
- **Improved scroll timing** - added small delay to ensure UI has updated before calculating visibility

### Technical Improvements

#### Startup Sequence Optimization
- **Separated update check from configuration validation** - update check now runs in separate LaunchedEffect to prevent interference
- **Better lifecycle management** - clearer separation of concerns between update checking and configuration validation

#### Code Quality
- **Enhanced error handling** - improved exception handling in HTTP client refresh
- **Better logging** - added diagnostic logging for authentication token validation
- **Performance optimizations** - reduced unnecessary scroll operations in filter lists

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` - Added delay after config update, separated update check LaunchedEffect
- `tv/src/main/java/ca/devmesh/seerrtv/data/SeerrApiService.kt` - Added retry logic, token validation logging, improved refreshClient() error handling
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/FiltersDrawer.kt` - Optimized scroll behavior for filter lists
- `tv/build.gradle.kts` - Version bump to 0.26.1 (versionCode 104)

---

## 0.26.0

### Major Features

#### Unified Media Browse Screens
- **New MediaBrowseScreen component** - unified browsing interface for both Movies and Series with consistent UI/UX
- **Grid-based media browsing** with 6-column layout optimized for Android TV
- **Infinite scroll pagination** with automatic loading of additional results
- **Position and selection persistence** - grid position and selected item are preserved when navigating to media details and returning
- **Smart state management** - separate state tracking for Movies and Series browse screens
- **Seamless navigation** - integrated with existing navigation system and D-pad controls

#### Comprehensive Filtering System
- **Advanced FiltersDrawer component** - slide-in drawer panel with full D-pad navigation support
- **Multi-screen filter interface** with 13+ filter categories:
  - Release Date / First Air Date (date range selection)
  - Genres (multi-select with search)
  - Keywords (searchable multi-select)
  - Original Language (single selection)
  - Content Rating (multi-select with region support)
  - Runtime (min/max range)
  - User Score (TMDB rating range)
  - Vote Count (min/max range)
  - Studios (Movies only - searchable single selection)
  - Networks (TV only - searchable multi-select)
  - Streaming Services (multi-select with region support)
  - Region (for content ratings and watch providers)
- **Active filter count indicator** - displays number of active filters on filter button
- **Clear all filters** functionality with single action
- **Filter state persistence** - filters are preserved when navigating away and returning
- **Smart filter loading** - filter options loaded on-demand when drawer opens
- **Region-aware filtering** - supports different regions for content ratings and streaming services

#### Sort Menu System
- **New SortMenu component** - slide-out menu with D-pad navigation matching RequestModal style
- **Media-type specific sorting**:
  - Movies: Popularity, Release Date, TMDB Rating, Title (A→Z / Z→A)
  - Series: Popularity, First Air Date, TMDB Rating, Title (A→Z / Z→A)
- **Bidirectional sorting** - toggle between ascending/descending for each sort option
- **Visual sort indicators** - checkmarks show currently selected sort option and direction
- **Sort state persistence** - sort preferences saved per media type

#### Enhanced Search Experience
- **CustomSearchBar component** - reusable search bar with consistent styling across the app
- **D-pad optimized navigation** - proper focus handling for Android TV
- **Keyboard integration** - Enter key opens on-screen keyboard for text input
- **Smart focus management** - automatic navigation to results grid when results are available
- **Inline search** - search results update in real-time as user types
- **Search state management** - search query preserved during navigation

#### Discovery Grid Component
- **Extracted DiscoveryGrid component** - reusable grid component for displaying media in grid layout
- **Optimized infinite scroll** - triggers loading when within 3 rows of bottom
- **Loading indicators** - shows progress when loading more results
- **Empty state handling** - proper messaging when no results are found
- **Grid position tracking** - maintains scroll position and selection state

### Technical Improvements

#### Data Models
- **New BrowseModels.kt** - comprehensive data models for browse functionality:
  - `MediaFilters` - complete filter state with all filter types
  - `SortOption` - enum-based sort options with API parameter mapping
  - Filter validation and active count calculation
- **New FilterModels.kt** - filter-related data structures:
  - `FilterLanguage` - language selection model
  - Additional filter option models

#### ViewModel Enhancements
- **MediaDiscoveryViewModel refactoring** - converted from ViewModel to AndroidViewModel for context access
- **Browse-specific state flows**:
  - `currentFilters` - tracks active filter state
  - `currentSort` - tracks current sort option
  - `activeFilterCount` - count of active filters
  - Filter option state flows (regions, languages, content ratings, keywords, studios, networks, watch providers)
- **New browse methods**:
  - `loadPopularMovies()` / `loadPopularSeries()` - initial browse screen loading
  - `browseMovies()` / `browseSeries()` - browse with filters and sorting
  - `applyFilters()` - apply new filters and reset pagination
  - `applySort()` - apply new sort option
  - `loadFilterOptions()` - load available filter options
  - `loadGenres()`, `loadStudios()`, `loadNetworks()`, `loadWatchProviders()` - filter option loaders
- **Enhanced pagination** - supports browse mode pagination with filter/sort state
- **Discovery mode tracking** - new `MOVIE_BROWSE` and `TV_BROWSE` modes

#### API Service Enhancements
- **New browseMedia() method** - comprehensive browse API integration:
  - Maps MediaFilters to Jellyseerr/TMDB discover endpoint parameters
  - Supports all filter types (dates, genres, keywords, language, ratings, runtime, scores, votes, studios, networks, watch providers)
  - Handles pagination with state management
  - Region-aware parameter construction
- **Enhanced discoverMovies() and getPopularSeries()** - support for browse mode
- **New filter option endpoints**:
  - Region, language, content rating loading
  - Keyword search
  - Studio/company search
  - Network search
  - Watch provider loading with region support
- **Seerr server support** - added detection and support for Seerr API (in addition to Overseerr/Jellyseerr)
- **URL normalization improvements** - better hostname handling and URL construction
- **Enhanced error messages** - updated to include Seerr in server detection messages

#### State Management
- **GridPositionManager enhancements**:
  - `saveSelection()` / `getSavedSelection()` - selection state persistence
  - `savePosition()` / `getSavedPosition()` - scroll position persistence
  - `saveBrowseFilters()` / `getSavedBrowseFilters()` - filter state persistence
  - `saveBrowseSort()` / `getSavedBrowseSort()` - sort state persistence
  - `clearScreenState()` - clear all state for a screen
  - `clearBrowseState()` - clear only filter/sort state
- **Screen-specific state keys** - separate state management for Movies and Series browse screens
- **Returning from details tracking** - flags to detect when returning from media details screen

#### Navigation Enhancements
- **NavigationManager updates**:
  - `navigateToMoviesBrowse()` - navigate to Movies browse screen
  - `navigateToSeriesBrowse()` - navigate to Series browse screen
  - Enhanced debug logging for navigation tracking
- **New browse routes** - "browse/movies" and "browse/series" routes added to navigation graph
- **D-pad configuration** - new ScreenDpadConfig for browse screens with TopBar, Search, Grid sections

#### Focus Management
- **AppFocusManager integration** - browse screens integrated with global focus management
- **DpadController support** - full D-pad navigation support for browse screens
- **Modal focus handling** - proper focus management when SortMenu or FiltersDrawer are open
- **Focus restoration** - focus properly restored when returning from media details

### UI/UX Improvements

#### Component Architecture
- **Modular component design** - extracted reusable components:
  - `MediaBrowseScreen` - main browse screen
  - `DiscoveryGrid` - grid display component
  - `CustomSearchBar` - search input component
  - `SortMenu` - sort selection menu
  - `FiltersDrawer` - filter selection drawer
  - `FilterControls` - individual filter control components
- **Consistent styling** - all components follow Material Design 3 guidelines
- **TV-optimized interactions** - all components designed for D-pad navigation

#### Filter Controls
- **Date range pickers** - intuitive date selection for release dates
- **Multi-select lists** - checkbox-based selection for genres, keywords, networks, streaming services
- **Single-select lists** - radio button style for language, studio selection
- **Range sliders** - for runtime, user score, vote count
- **Searchable lists** - keyword, studio, network search with real-time results
- **Clear selection options** - easy reset for individual filters

#### Visual Enhancements
- **Active filter indicators** - visual feedback for applied filters
- **Loading states** - proper loading indicators during API calls
- **Empty states** - user-friendly messages when no results found
- **Focus highlights** - clear visual feedback for D-pad navigation
- **Modal animations** - smooth slide-in/out animations for menus and drawers

### Internationalization

#### New String Resources
- **Browse screen strings** - Movies, Series, No results
- **Sort menu strings** - Sort By, Popularity, Release Date, First Air Date, TMDB Rating, Title A→Z
- **Filter strings** - comprehensive filter-related strings:
  - Filter section names (Release Date, Genres, Keywords, etc.)
  - Filter control labels (From, To, Min, Max, etc.)
  - Content rating labels (NR, G, PG, PG-13, R, NC-17)
  - Language names (English, Spanish, French, etc.)
  - Filter actions (Clear All, Clear Selection, Apply Range, etc.)
- **Updated existing strings** - references to "Overseerr/Jellyseerr" updated to include "Seerr"
- **Plural support** - proper plural forms for Sonarr/Radarr server counts

#### Localization Updates
- All new strings localized across all supported languages:
  - English (en)
  - German (de)
  - Spanish (es)
  - French (fr)
  - Japanese (ja)
  - Dutch (nl)
  - Portuguese (pt)
  - Chinese (zh)

### Bug Fixes

#### Media Discovery Screen
- **Refactored MediaDiscoveryScreen** - simplified to use shared MediaBrowseScreen component
- **Improved state management** - better handling of search vs browse modes
- **Enhanced error handling** - better error messages and recovery

#### Settings Menu
- **Default streaming region setting** - new setting for default watch provider region
- **Enhanced settings persistence** - better state management for settings

#### Configuration Screen
- **Seerr server detection** - added support for detecting Seerr servers
- **Updated help text** - references updated to include Seerr

### Build Configuration

#### Version Updates
- **Version Code**: 102 → 103
- **Version Name**: 0.25.16 → 0.26.0
- **NDK Version**: 27.0.12077973
- **Build Tools Version**: 36.1.0

### Files Added
- `tv/src/main/java/ca/devmesh/seerrtv/model/BrowseModels.kt` - Browse data models
- `tv/src/main/java/ca/devmesh/seerrtv/model/FilterModels.kt` - Filter data models
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MediaBrowseScreen.kt` - Unified browse screen
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/DiscoveryGrid.kt` - Reusable grid component
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/CustomSearchBar.kt` - Reusable search bar
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/SortMenu.kt` - Sort selection menu
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/FiltersDrawer.kt` - Filter selection drawer
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/FilterControls.kt` - Filter control components

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` - Browse route handling, Seerr support
- `tv/src/main/java/ca/devmesh/seerrtv/data/SeerrApiService.kt` - Browse API methods, Seerr detection, filter endpoints
- `tv/src/main/java/ca/devmesh/seerrtv/viewmodel/MediaDiscoveryViewModel.kt` - Browse functionality, filter management
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MediaDiscoveryScreen.kt` - Refactored to use MediaBrowseScreen
- `tv/src/main/java/ca/devmesh/seerrtv/ui/ConfigScreen.kt` - Seerr detection updates
- `tv/src/main/java/ca/devmesh/seerrtv/ui/SettingsMenu.kt` - Default streaming region setting
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/MainTopBar.kt` - Browse screen integration
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/TopBarController.kt` - Browse navigation support
- `tv/src/main/java/ca/devmesh/seerrtv/navigation/NavigationManager.kt` - Browse route methods
- `tv/src/main/java/ca/devmesh/seerrtv/ui/focus/AppFocusManager.kt` - Browse focus integration
- `tv/src/main/java/ca/devmesh/seerrtv/ui/focus/DpadController.kt` - Browse D-pad support
- `tv/src/main/java/ca/devmesh/seerrtv/ui/focus/ScreenDpadConfigs.kt` - Browse screen D-pad config
- `tv/src/main/java/ca/devmesh/seerrtv/ui/position/GridPositionManager.kt` - Browse state persistence
- `tv/src/main/java/ca/devmesh/seerrtv/util/SharedPreferencesUtil.kt` - Default streaming region support
- `tv/src/main/res/values/strings.xml` - New browse, sort, filter strings
- `tv/src/main/res/values-*/strings.xml` - Localized new strings (de, es, fr, ja, nl, pt, zh)
- `tv/build.gradle.kts` - Version bump, build tools update

---

## 0.25.16

### UI/UX Enhancements

#### Splash Messaging – Sonarr/Radarr
- Show HD/4K split only when at least one 4K server is configured per server type
- When no 4K servers exist, display a simplified total-only message (no HD/4K breakdown)

#### Media Card Status Icons
- **Added deleted status icon** - custom trash can icon displays when media has status 7 (DELETED) from Jellyseerr
- **Added blacklisted status icon** - custom prohibition/blocked icon displays when media has status 6 (BLACKLISTED) from Jellyseerr
- **Enhanced status priority system** - deleted status (7) takes highest priority, followed by blacklisted (6), then existing statuses
- **Consistent icon design** - both new icons follow the established pattern with colored backgrounds and semantic visual representations

#### Status Icon System
- **CustomTrashIcon** - red circular icon with trash can design for deleted media
- **CustomBlockedIcon** - black circular icon with prohibition symbol for blacklisted media
- **Improved status hierarchy** - proper priority ordering: Deleted > Blacklisted > Available > Partially Available > Pending > Not Requested
- **Dual-tier support** - icons work for both regular and 4K status fields

### Technical Improvements
- **Enhanced getMediaInfoIcon function** - now properly handles all Jellyseerr status values (1-7)
- **Canvas-based icon rendering** - custom drawn icons for consistent visual quality
- **Color scheme alignment** - icons use colors matching the RequestStatus system

### Internationalization
- Added localized strings for simplified total-only Sonarr/Radarr messages across all supported locales (en, de, es, fr, ja, nl, pt, zh)

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` – conditional messaging for Sonarr/Radarr counts on splash
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/MediaCard.kt` – added CustomTrashIcon and CustomBlockedIcon composables, updated getMediaInfoIcon function
- `tv/src/main/res/values/strings.xml` – added total-only strings
- `tv/src/main/res/values-de/strings.xml` – localized total-only strings
- `tv/src/main/res/values-es/strings.xml` – localized total-only strings
- `tv/src/main/res/values-fr/strings.xml` – localized total-only strings
- `tv/src/main/res/values-ja/strings.xml` – localized total-only strings
- `tv/src/main/res/values-nl/strings.xml` – localized total-only strings
- `tv/src/main/res/values-pt/strings.xml` – localized total-only strings
- `tv/src/main/res/values-zh/strings.xml` – localized total-only strings

---

## 0.25.15

### Major Features

#### Issue Permission System Integration
- **Permission-based issue functionality** - full integration of Jellyseerr's VIEW_ISSUES and CREATE_ISSUES permissions
- **Smart modal routing** - automatically routes users to appropriate modals based on their permissions
- **Conditional UI rendering** - ISSUE button and New Issue button visibility controlled by user permissions
- **MANAGE_ISSUES support** - users with MANAGE_ISSUES permission get both view and create capabilities
- **Graceful permission handling** - UI adapts seamlessly when users have partial permissions

### UI/UX Enhancements

#### Permission-Aware Issue Interface
- **Hidden ISSUE button** when user lacks any issue permissions - completely removed from UI and DPAD navigation
- **Conditional New Issue button** in IssueDetailsModal - only shown to users with CREATE_ISSUES permission
- **Smart modal selection** based on permissions:
  - VIEW_ISSUES only: clicking ISSUE goes to view existing issues (create button hidden)
  - CREATE_ISSUES only: clicking ISSUE goes directly to create new issue (skips view modal)
  - Both permissions: normal flow (view if issues exist, else create)
- **Centered button layout** when New Issue button is hidden for better visual balance

#### Enhanced DPAD Navigation
- **Smart navigation skipping** - DPAD navigation automatically bypasses ISSUE button when user lacks permissions
- **Seamless flow preservation** - Up from top action button goes directly to TopBar when ISSUE is hidden
- **Consistent behavior** - Down from OVERVIEW goes to first action button when ISSUE is hidden
- **No dead-end navigation** - all navigation paths properly skip unavailable buttons

### Bug Fixes

#### Media Details Tag Scrolling
- **Fixed tag visibility issue** when navigating through many tags - screen now scrolls automatically to keep selected tag visible
- **Improved viewport calculation** - uses fixed 900px viewport height based on actual Android TV screen dimensions instead of percentage-based calculation
- **Enhanced tag position tracking** - properly accounts for content offset (poster, action buttons, overview) above tags section
- **Smart scroll triggering** - scrolls only when tag is outside visible viewport, with proper bounds checking
- **Better scroll positioning** - positions tags with appropriate padding (120px) for optimal visibility
- **Added selectedTagIndex to scroll dependencies** - ensures scroll effect triggers when navigating between tags, not just when entering tags section

#### Splash Screen Error Handling
- **Fixed authentication error handling** - all connection and validation errors now properly trigger authentication error modal
- **Consistent error flow** - base connection failures, authentication failures, and Cloudflare errors all use unified error handling
- **Improved user experience** - users can retry authentication or reconfigure without restarting the app
- **Better error recovery** - proper error state management throughout the validation process

### Technical Improvements

#### Permission Helper Functions
- **Added canViewIssues()** - checks for VIEW_ISSUES, MANAGE_ISSUES, or ADMIN permissions
- **Added canCreateIssues()** - checks for CREATE_ISSUES, MANAGE_ISSUES, or ADMIN permissions
- **Null-safe permission handling** - proper handling of missing or null permission values
- **Centralized permission logic** in CommonUtil for consistent checks across the app

#### Modal Controller Enhancements
- **IssueDetailsModalController** updated to handle conditional New Issue button navigation
- **Smart button navigation** - Up/Down/Left/Right navigation adapts when New Issue button is hidden
- **No navigation to hidden elements** - controller prevents focus on unavailable buttons
- **Maintained backward compatibility** with default parameter values

#### MediaDetails Integration
- **Permission checks on screen load** - retrieves and validates user permissions immediately
- **hasAnyIssuePermission flag** - combined permission check for simplified DPAD logic
- **Enhanced Enter key handler** - routes to appropriate modal based on specific permissions
- **Consistent state management** across all permission-related UI elements

### Security Improvements
- **Enforced permission boundaries** - users cannot access functionality beyond their permissions
- **Server-side permission validation** - relies on Jellyseerr's permission system
- **Graceful permission denial** - UI elements hidden rather than showing error messages
- **No permission bypass paths** - all routes to issue functionality check permissions

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/util/CommonUtil.kt` - Added permission helper functions (canViewIssues, canCreateIssues)
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MediaDetails.kt` - Integrated permission checks, updated DPAD navigation, and fixed tag scrolling visibility
- `tv/src/main/java/ca/devmesh/seerrtv/ui/IssueDetailsModal.kt` - Added conditional New Issue button rendering based on permissions
- `tv/src/main/java/ca/devmesh/seerrtv/ui/IssueModalController.kt` - Enhanced navigation to skip hidden buttons when permissions are missing
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` - Fixed authentication error handling for all validation failure scenarios
- `tv/build.gradle.kts` - Version bump to 0.25.15 (versionCode 101)

---

## 0.25.14

### Bug Fixes

#### Emby Playback - Movies and Series
- **CRITICAL FIX: Emby playback now works correctly for both movies and series** - resolved issue where playback would drop users at the Emby home screen instead of the specific media item
- **Fixed itemId extraction** - now prioritizes parsing `mediaUrl` to get the correct item ID instead of using incorrect `externalServiceId`
- **Enhanced deep link support** - implemented proper `emby://item/{serverId}/{itemId}` URI scheme for Emby TV app
- **Dual-method launch strategy** - tries deep link first, falls back to StartupActivity with intent extras if needed
- **Server context preservation** - properly extracts and passes serverId from mediaUrl to maintain authentication context

#### Request Modal - Tag Selection
- **Fixed tag selection visibility** - tags menu now appears when server has 1 or more tags (previously required 2+ tags)
- **Changed visibility logic** from `tagsCount > 1` to `tagsCount > 0` to properly support single-tag servers
- **Improved user experience** - users can now select tags even when only one tag is configured

### Technical Improvements
- **Rewrote getEmbyPlayUrl()** to prefer URL fragment parsing over externalServiceId (which was pointing to wrong items for both movies and series)
- **Added comprehensive deep link implementation** with proper serverId/itemId structure
- **Enhanced fallback chain** with two Emby TV methods before trying regular Android app
- **Improved logging** throughout the Emby launch process for easier debugging
- **Fixed tag menu visibility logic** in RequestModalController for proper single-tag support

### User Impact
- **Movies now launch correctly** to their detail/playback screen in Emby TV
- **Series now launch correctly** to their detail/playback screen in Emby TV (previously was broken)
- **Consistent behavior** across all media types with proper navigation to content
- **Better reliability** with multiple launch methods and proper error handling
- **Tag selection now available** for servers with any number of tags (including just one)

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MediaDetails.kt` - Fixed getEmbyPlayUrl() and enhanced tryEmbyPlayback() with deep link support
- `tv/src/main/java/ca/devmesh/seerrtv/ui/RequestModal.kt` - Fixed tag visibility condition to support single-tag servers

---

## 0.25.13

### Major Features

#### Enhanced Emby Playback System
- **CRITICAL IMPROVEMENT: Dual Emby app support** - now supports both Emby Android TV and regular Emby Android apps with intelligent fallback
- **Emby Android TV app support** - uses explicit `StartupActivity` launch with proper server context for direct media navigation
- **Emby Android app deep link support** - implements official `emby://items/{SERVER_ID}/{ITEM_ID}` URI scheme for seamless media playback
- **Smart server ID extraction** - automatically extracts and passes server ID from media URLs to maintain authentication context
- **Comprehensive fallback chain** - tries Android TV app first, then falls back to regular Android app with deep links

### Bug Fixes

#### ConfigScreen Focus Management
- **FIXED: D-pad navigation not working** when navigating from MainScreen → SettingsMenu → EDIT API
- **Enhanced focus management** - replaced arbitrary delays with proper Compose focus lifecycle handling
- **Improved focus request timing** - uses `withFrameMillis` to ensure composition and layout completion before requesting focus
- **Better error handling** - includes retry logic for focus requests with proper exception handling
- **State-based interaction control** - only enables interactions after focus is successfully received

#### MainActivity Key Event Handling
- **Fixed DpadController conflicts** - screens using native Compose focus (config, splash) now bypass DpadController
- **Improved key event routing** - prevents DpadController from intercepting events on screens that handle their own focus
- **Enhanced debugging** - added logging to track when DpadController is bypassed for specific routes

#### SettingsMenu Navigation
- **Streamlined config navigation** - removed unnecessary delays when navigating to ConfigScreen
- **Immediate navigation** - ConfigScreen now handles its own focus management without external delays
- **Cleaner state management** - proper submenu closure before navigation

### Technical Improvements

#### Emby Integration Architecture
- **Dual-launch strategy** - Android TV app uses explicit activity launch, regular app uses deep link scheme
- **Server context preservation** - extracts and passes server ID to maintain user session context
- **Enhanced error handling** - comprehensive logging and fallback mechanisms for both app types
- **User guidance integration** - provides helpful messages about Emby auto-login configuration

#### Focus Management Enhancements
- **Native Compose focus support** - proper integration with Compose's focus system for config and splash screens
- **Lifecycle-aware focus requests** - ensures focus is requested at the right time in the composition lifecycle
- **Robust error recovery** - retry mechanisms for failed focus requests with graceful degradation

#### Code Quality Improvements
- **Removed arbitrary delays** - replaced timing-based approaches with state-based focus management
- **Enhanced logging** - comprehensive debug logging for troubleshooting focus and playback issues
- **Better separation of concerns** - clear distinction between DpadController and native Compose focus handling

### User Impact
- **Seamless Emby playback** - works with both Android TV and regular Android Emby apps
- **Reliable config screen navigation** - D-pad navigation works consistently from all entry points
- **Better user experience** - eliminates focus issues and provides clear feedback for configuration steps
- **Universal Emby support** - users can play media regardless of which Emby app they have installed

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MediaDetails.kt` - Enhanced Emby playback with dual app support and deep linking
- `tv/src/main/java/ca/devmesh/seerrtv/ui/ConfigScreen.kt` - Improved focus management with lifecycle-aware focus requests
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` - Added DpadController bypass for native Compose focus screens
- `tv/src/main/java/ca/devmesh/seerrtv/ui/SettingsMenu.kt` - Streamlined config navigation without delays

---

## 0.25.12

### Bug Fixes

#### Emby Playback - Regular Android App Support
- **CRITICAL FIX: Regular Emby Android app now launches correctly** - fixed playback failure when only the regular Emby app is installed
- **Removed incorrect CATEGORY_LEANBACK_LAUNCHER** - this Android TV-specific category was preventing the regular Emby app from launching
- **Enhanced dual-app compatibility** - Android TV version (`tv.emby.embyatv`) uses LEANBACK category, regular app (`com.mb.android`) does not
- **Improved fallback behavior** - when Android TV app fails, regular app now launches successfully without conflicts

### Technical Improvements
- **Fixed intent configuration** - removed `addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)` from regular Emby Android app intent
- **Maintained TV app support** - Android TV version still uses proper LEANBACK_LAUNCHER category
- **Better error handling** - proper fallback chain ensures users can play media regardless of which Emby app they have installed

### User Impact
- Users with only the regular Emby for Android app installed can now successfully play media
- Users with both apps installed experience no changes (TV app is still preferred)
- Eliminates "no media player found" errors for regular Emby app users

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MediaDetails.kt` - Fixed `tryEmbyPlayback()` function intent configuration

---

## 0.25.11

### Bug Fixes

#### Emby Playback
- **CRITICAL FIX: Emby playback now working** - resolved "no media player app found" error for Emby users
- **Updated intent format** - now uses the same proven approach as Jellyfin (plain itemId with setClassName)
- **Multi-app support** - tries both Emby for Android TV (`tv.emby.embyatv`) and regular Emby app (`com.mb.android`)
- **Enhanced compatibility** - Android TV app is tried first, with regular app as fallback for broader device support

### Technical Improvements
- **Removed broken deep link format** - eliminated `embyatv://tv.emby.embyatv/direct/$itemId` URI scheme
- **Adopted Jellyfin-style intent** - uses plain `itemId.toUri()` as data with proper activity targeting
- **Added `putExtra("source", 30)`** - matches working Jellyfin implementation
- **Enhanced error handling** - better logging and fallback attempts for both Emby packages

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MediaDetails.kt` - Rewrote `tryEmbyPlayback()` function

---

## 0.25.10

### Bug Fixes

#### MainScreen Navigation
- **Fixed crash on empty category lists** - prevented IllegalArgumentException when coercing index to invalid range
- **Added safety check** - ensures maxIndex is at least 0 before calling coerceIn to handle empty data gracefully

### Technical Improvements
- **Enhanced error handling** in MainScreen category navigation with proper bounds checking

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MainScreen.kt` - Fixed empty list crash in updateSelectedIndex

---

## 0.25.9

### Major Features

#### Fixed Emby Media Server Detection
- **CRITICAL FIX**: Discovered Jellyseerr returns `mediaServerType: 3` for Emby (not type 2)
- **Added proper Emby detection** - app now correctly handles type 3 from Jellyseerr API
- **Immediate Emby support** - Emby users now see correct "Play on Emby" button from first launch
- **Removed unnecessary complexity** - eliminated JELLYFIN_OR_EMBY enum and auth type checking
- **Simplified detection logic** - clean switch statement: 1=Plex, 2=Jellyfin, 3=Emby

### Bug Fixes

#### Media Server Detection
- **Fixed Emby not being detected** - app was treating `mediaServerType: 3` as unknown
- **Removed JELLYFIN_OR_EMBY fallback type** - not needed with correct API mapping
- **Cleaned up detection code** - removed authentication type checking logic
- **Fixed Play button text** - Emby users no longer see "No media player found" message

### Technical Improvements
- **Simplified SeerrApiService.detectMediaServerType()** to handle types 1, 2, and 3 directly
- **Removed unnecessary enum value** from MediaServerType
- **Cleaned up switch statements** in MainActivity, MediaDetails, and ActionButtons
- **Reduced code complexity** with straightforward API value mapping

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/data/SeerrApiService.kt` - Added case for mediaServerType: 3 (Emby)
- `tv/src/main/java/ca/devmesh/seerrtv/model/CommonModels.kt` - Removed JELLYFIN_OR_EMBY enum
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` - Removed JELLYFIN_OR_EMBY case
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MediaDetails.kt` - Removed JELLYFIN_OR_EMBY playback logic
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/ActionButtons.kt` - Removed JELLYFIN_OR_EMBY button text case
- `_ANALYSIS/EmbyJellyfinDetection.md` - Updated with final solution

---

## 0.25.8

### Major Features

#### Enhanced Emby/Jellyfin Media Server Detection
- **Smart media server detection** with automatic learning capability for API Key authentication users
- **Ambiguous type handling** with new JELLYFIN_OR_EMBY media server type for uncertain configurations
- **Persistent detection memory** that remembers the correct media server type after first successful playback
- **Splash screen feedback** showing detected media server type during app startup
- **Fallback playback support** that tries alternative players if the first attempt fails

### Bug Fixes

#### Play Button Issues
- **Fixed "No media player app found" error** appearing as button text for Emby users with API Key authentication
- **Fixed missing media server detection** - detection now runs on every app startup instead of relying on cached values
- **Fixed button text display** for ambiguous media server types (JELLYFIN_OR_EMBY)
- **Enhanced error handling** with more accurate error messages when playback fails

#### Media Server Detection
- **Improved type detection logic** to properly handle Emby vs Jellyfin when authentication type is API Key or Local User
- **Added detection persistence** so the app remembers which player worked after the first successful playback attempt
- **Enhanced Jellyseerr integration** to properly detect media server type from API responses
- **Better authentication type handling** for distinguishing between confirmed and ambiguous media server types

### Technical Improvements

#### Detection System Architecture
- **Added MediaServerType.JELLYFIN_OR_EMBY** enum value for ambiguous type scenarios
- **Implemented SharedPreferences storage** for detected media server type with save/get/clear methods
- **Enhanced SeerrApiService.detectMediaServerType()** with comprehensive detection logic and fallback handling
- **Added detection call to MainActivity.validateConfiguration()** ensuring detection runs on every startup

#### Playback Logic Enhancement
- **Smart fallback system** that tries alternative players when the primary player fails
- **Automatic type learning** that saves the working media server type after successful playback
- **Comprehensive logging** throughout the detection and playback process for easier debugging
- **Improved error messages** with generic messaging for Jellyfin/Emby cases

#### UI/UX Improvements
- **Splash screen media server messages** showing detection results during startup:
  - "Plex media server detected" (green) for confirmed Plex
  - "Jellyfin media server detected" (green) for confirmed Jellyfin
  - "Emby media server detected" (green) for confirmed Emby
  - "Jellyfin or Emby detected (will auto-detect on first playback)" (yellow) for ambiguous type
  - "No media server configured" (warning) when not configured
- **Proper Play button text** for all media server type scenarios
- **Enhanced user feedback** throughout the detection and playback process

### Files Modified
- `tv/src/main/java/ca/devmesh/seerrtv/model/CommonModels.kt` - Added JELLYFIN_OR_EMBY enum
- `tv/src/main/java/ca/devmesh/seerrtv/util/SharedPreferencesUtil.kt` - Added detection persistence methods
- `tv/src/main/java/ca/devmesh/seerrtv/data/SeerrApiService.kt` - Enhanced detection logic
- `tv/src/main/java/ca/devmesh/seerrtv/MainActivity.kt` - Added detection call and splash screen feedback
- `tv/src/main/java/ca/devmesh/seerrtv/ui/MediaDetails.kt` - Improved playback logic with fallbacks
- `tv/src/main/java/ca/devmesh/seerrtv/ui/components/ActionButtons.kt` - Fixed button text for ambiguous type
- `_ANALYSIS/EmbyJellyfinDetection.md` - Comprehensive requirements and implementation documentation

---

## 0.25.7

### Major Features

#### Emby Media Server Support
- **Added Emby as a new media server type** allowing users to play content directly from Emby servers
- **Enhanced media server detection** to differentiate between Emby and Jellyfin based on user authentication type
- **Emby playback integration** with proper Android TV app launching using `embyatv://` URI scheme
- **Comprehensive error handling** with localized error messages for Emby app not found scenarios
- **Fallback support** for Emby in NOT_CONFIGURED mode alongside Plex and Jellyfin

### Technical Improvements

#### Media Server Detection Enhancement
- **Smart server type detection** using authentication type to distinguish Emby from Jellyfin (both return `mediaServerType: 2`)
- **Hybrid detection approach** combining server API response with user authentication method
- **Enhanced SeerrApiService** with improved media server type mapping logic
- **Updated MediaServerType enum** to include EMBY as a distinct server type

#### UI/UX Enhancements
- **Updated MediaDetails screen** with Emby playback support and proper intent handling
- **Enhanced ActionButtons component** to display "Play on Emby" text when Emby is selected
- **Improved SettingsMenu** to properly display Emby as a configured media server type
- **Consistent error messaging** across all media server types with proper localization

### Bug Fixes
- **Fixed media server type detection** to properly handle Emby authentication alongside Jellyfin
- **Enhanced playback logic** to support Emby's custom URI scheme (`embyatv://tv.emby.embyatv/direct/{itemId}`)
- **Improved fallback behavior** when no specific media server is configured
- **Added safety checks in EnhancedMediaCarousel** to prevent IndexOutOfBoundsException
- **Adjusted scrolling behavior in MediaDetails** to maintain visibility of action buttons

---

## 0.25.6

### Major Features

#### Sonarr Lookup System for TV Series
- **Series matching** for TV requests when TVDB ID is missing
- **Interactive series selection** with poster images and detailed information
- **D-pad navigation** through lookup results with focus management
- **Smart fallback handling** when automatic matching fails
- **Seamless integration** with existing request modal workflow

#### Tag Support for Request Categorization
- **Tag selection interface** in request modal for better organization
- **Multi-tag support** with checkbox-based selection
- **Server-specific tags** from Sonarr and Radarr configurations
- **Default tag behavior** matching Jellyseerr conventions (no tags selected by default)
- **Enhanced request payload** including selected tag IDs

#### Standardized ActionButton Component
- **Unified button styling** across the entire application
- **Consistent dimensions and behavior** for TV navigation
- **Focus management integration** with D-pad navigation system
- **Reusable component architecture** for better maintainability

### Technical Improvements

#### Ratings System Optimization
- **404 response caching** to prevent repeated failed requests for media without ratings
- **Intelligent cache management** with no-ratings tracking system
- **Polling optimization** to use cached ratings data during media details updates
- **Memory efficiency** with automatic cleanup of tracking data
- **Enhanced error handling** for ratings API failures

#### API Service Enhancements
- **Sonarr lookup endpoint** integration for series matching
- **Extended data models** for Sonarr lookup results with comprehensive metadata
- **Improved error handling** with status code preservation in API results
- **Enhanced request body support** for tag inclusion in media requests

#### UI/UX Improvements
- **Enhanced scrolling behavior** in all request modal submenus
- **Improved focus management** with better scroll-to-focus functionality
- **Visual consistency** across tag, quality profile, and root folder selections
- **Better error states** with user-friendly messages and recovery options
- **Optimized list rendering** with LazyColumn performance improvements

### Bug Fixes

#### Request Modal Enhancements
- **Fixed tag integration** in request payload construction
- **Improved server switching** with proper tag data updates
- **Enhanced season selection** with better visual hierarchy
- **Fixed focus restoration** after modal interactions

#### Performance Optimizations
- **Reduced API calls** during media details polling by using cached ratings
- **Improved memory usage** with better cache management
- **Enhanced scrolling performance** in long lists
- **Optimized component rendering** with reduced recompositions

### Internationalization
- **Added new string resources** for Sonarr lookup functionality
- **Enhanced tag-related translations** across all supported languages
- **Improved error message localization** for better user experience

---

## 0.25.5

### Bug Fixes

#### Media Details Performance and Architecture
- **Fixed excessive API calls and polling conflicts** by implementing proper request deduplication and cooldown mechanisms
- **Enhanced caching system** with improved cache management and reduced redundant network requests
- **Optimized media details polling** with better lifecycle management and reduced frequency (15s/30s intervals)
- **Fixed race conditions** in media details fetching with mutex-based synchronization
- **Improved ratings API integration** with caching to prevent duplicate requests

#### UI/UX Improvements
- **Reduced instruction count** by removing excessive debug logging throughout MediaDetails screen
- **Enhanced component architecture** by extracting action buttons, carousels, and content layout into separate components
- **Improved focus management** with dedicated FocusArea constants and better state organization
- **Streamlined MediaDownloadStatus component** to use shared state instead of duplicate orchestration
- **Better error handling** for API failures with graceful degradation

#### State Management Enhancements
- **Centralized MediaDetailsStateManager** for better state organization and reduced recomposition
- **Improved polling coordination** between different components to prevent conflicts
- **Enhanced cache cleanup** with automatic expiration and memory management
- **Better lifecycle handling** for polling to prevent unnecessary start/stop cycles

### Technical Improvements

#### Architecture Refactoring
- **Modularized MediaDetails screen** into focused components (ActionButtons, Carousels, ContentLayout)
- **Extracted FocusArea constants** into dedicated state management file
- **Improved component separation** for better maintainability and testability
- **Enhanced state sharing** between components to reduce duplication

#### Performance Optimizations
- **Reduced API call frequency** with intelligent caching and cooldown mechanisms
- **Improved memory usage** with better cache management and cleanup
- **Enhanced polling efficiency** with proper job tracking and lifecycle management
- **Optimized component rendering** by reducing unnecessary recompositions

---

## 0.25.4

### Bug Fixes

#### Issue Modal Navigation and Focus Management
- **Fixed focus restoration after IME keyboard closes** in AddCommentModal and IssueReportModal
- **Enhanced D-pad navigation** in issue modals with proper focus state preservation
- **Improved text input handling** with keyboard trigger system for consistent focus management
- **Fixed modal scrolling** to top when navigating to "None" option in AddCommentModal
- **Enhanced submit button validation** with proper precanned issue checking and user feedback

#### Media Details Auto-Refresh
- **Fixed polling state synchronization** by ensuring single ViewModel instance across components
- **Enhanced auto-refresh behavior** to pause during issue modal interactions and resume after closing
- **Improved data freshness** with forced refresh when opening Issue Details modal
- **Fixed focus restoration** after IME Done/Back actions in text fields

#### Navigation Improvements
- **Enhanced up-navigation** from common issues section back to issue type selection
- **Fixed Cancel to Submit button navigation** in IssueReportModal
- **Improved scroll positioning** for better focus visibility in modal lists

### Technical Improvements

#### Architecture Enhancements
- **Unified ViewModel usage** to prevent multiple SeerrViewModel instances and ensure state consistency
- **Enhanced focus management** with explicit controller state updates after IME interactions
- **Improved modal lifecycle handling** with proper focus restoration and state management
- **Better error handling** for form validation with user-friendly feedback messages

#### UI/UX Enhancements
- **Consistent focus behavior** across all issue-related modals
- **Improved keyboard interaction** with proper IME handling and focus restoration
- **Enhanced navigation flow** with better button state management and validation
- **Better visual feedback** for form validation errors and user actions

---

## 0.25.2

### Bug Fixes

#### Issue Modal Navigation
- **Fixed device-specific BACK button handling** in issue reporting and management modals
- **Resolved Chromecast compatibility issue** - BACK button now works consistently on physical devices
- **Enhanced key event handling** - issue modals now explicitly intercept BACK key events in onKeyEvent (matching RequestModal pattern)
- **Improved device compatibility** - ensures BACK button works consistently across emulator and physical devices
- **Nested modal behavior corrected** - BACK now closes only the top-most modal (e.g., Add Comment) without closing the underlying Issue Details or returning to Media Details
- **Prevented cascading close** from nested modals by tracking child Back events and ignoring parent Back within a short window

### Technical Improvements

#### Modal Event Handling
- **Enhanced key event handling** - issue modals now explicitly intercept BACK key events in onKeyEvent (matching RequestModal pattern)
- **Improved device compatibility** - ensures BACK button works consistently across emulator and physical devices
- **Maintained dual handling** - both BackHandler and explicit key event handling for maximum compatibility
- **Global back suppression** in `MediaDetails` while modals are visible and for 600ms after a modal Back/close to avoid unintended navigation
- **Explicit Back KeyDown/KeyUp consumption** in `AddCommentModal`, `IssueDetailsModal`, and `IssueReportModal` to prevent bubbling
- **Parent notification on child Back** (`AddCommentModal` → `IssueDetailsModal`) to avoid double-close
- **Minor compile fix**: added missing key input imports in `IssueReportModal`

#### Requests
- **4K request flow fixed**: correctly routes tier-specific server selection and prevents fallback to regular tier when 4K is available
- **Auto-submit guard improved**: avoids duplicate submissions and ensures direct submit respects selected tier and server

#### UI/UX
- **Tag icon rendering fixed**: corrected icon alignment/visibility in tag chips and ensured proper focus highlight
- **Minor visual polish** across request/action buttons for consistent focus borders

---

## 0.25.1

### Bug Fixes

#### Issue Modal Navigation
- **Fixed BACK button functionality** in issue reporting and management modals
- **Resolved modal focus conflicts** between MediaDetails DpadController and issue modal BackHandlers
- **Improved modal independence** - issue modals now handle their own BACK button events properly
- **Enhanced user experience** for issue reporting workflow with working cancel/back navigation

### Technical Improvements

#### Modal Event Handling
- **Separated BACK button handling** between main screen and modal overlays
- **Removed interference** from parent screen's DpadController for modal-specific events
- **Maintained proper debouncing** (500ms) for modal BACK button events
- **Preserved existing functionality** for main MediaDetails screen BACK navigation

---

## 0.25.0

### Major Features

#### Issue Reporting and Management System
- **Complete issue reporting system** for media quality and playback problems
- **Issue creation workflow** with categorized issue types (Video, Audio, Subtitle, Other)
- **Precanned issue descriptions** for common problems with customizable options
- **Issue details modal** for viewing and managing existing issues
- **Comment system** for issue discussions and updates
- **Season/episode selection** for TV series issue reporting
- **Issue status tracking** (Open, Resolved) with visual indicators

#### Enhanced Media Details Integration
- **Issue button integration** in media details screen with smart visibility
- **Issue count indicators** showing number of reported issues per media
- **Seamless navigation** between media details and issue management

### UI/UX

#### Issue Reporting Interface
- **Comprehensive issue report modal** with intuitive D-pad navigation
- **Categorized issue selection** with radio button interface for TV users
- **Custom description support** with text input for detailed issue reporting
- **Season/episode picker** for TV series with increment/decrement controls
- **Smart form validation** ensuring meaningful issue descriptions

#### Issue Management Interface
- **Issue list view** with status badges and metadata display
- **Comment threading** showing issue history and discussions
- **Issue status indicators** with color-coded badges (Open/Resolved)
- **User attribution** showing who reported and commented on issues
- **Auto-updating timestamps** for issue and comment timestamps

#### Enhanced Action Buttons
- **Issue reporting buttons** integrated into media action button system
- **Smart button visibility** based on media availability and existing issues
- **Dual-tier support** for HD and 4K issue reporting
- **Visual status indicators** for issue presence and count

### API/Models

#### Issue Data Models
- **Comprehensive issue models** with full serialization support
- **Issue type enumeration** (Video, Audio, Subtitle, Other)
- **Issue status tracking** with proper state management
- **Comment system models** for issue discussions
- **User attribution models** for issue ownership and modification tracking

#### API Integration
- **Issue creation endpoints** with proper request/response handling
- **Comment management** with add comment functionality
- **Issue repository pattern** for clean data access
- **Error handling** with proper API result types

### Technical Improvements

#### Architecture Enhancements
- **IssueViewModel** for centralized issue state management
- **Modal management system** with proper focus handling
- **Focus management utilities** for D-pad navigation
- **Refresh management** for data synchronization after issue operations

#### Component Architecture
- **Modular UI components** for issue reporting and management
- **Reusable action buttons** with enhanced functionality
- **Carousel components** for related media and person displays
- **Media information components** with status integration

#### Navigation and Focus
- **Enhanced D-pad navigation** throughout issue management flow
- **Focus state management** with proper restoration
- **Modal overlay system** with z-index management
- **Back button handling** with debouncing for TV interface

### Bug Fixes

#### UI/UX Improvements
- **Fixed focus management** in modal overlays
- **Improved navigation flow** between issue creation and management
- **Enhanced error handling** for API failures
- **Better state management** for complex modal interactions

---

## 0.24.3

### Major Features
- Introduced a centralized navigation/focus system for Android TV:
  - `NavigationCoordinator` as the single source of truth for route changes, back-stack entry processing, and back debouncing.
  - App-wide `AppFocusManager` and a configurable `DpadController` with screen-specific DPAD configs (`ScreenDpadConfigs`).
  - Persistent, always-on `MainTopBar` with its own `TopBarController` handling D-pad and actions.

### UI/UX
- Persistent top bar shown across `main`, `search`, `mediaDiscovery`, `details`, and `person` routes with Search/Settings indicators and clock.
- Settings Menu:
  - Added “Check for Update” entry for direct flavor, with dialog when an update is available; otherwise shows a toast.
  - Debounced Back handling and improved z-order so it overlays content/top bar reliably.
  - Clear version display (`BuildConfig.VERSION_NAME`) and streamlined submenus.
- Media Discovery:
  - More robust grid state restore when returning from details; improved backdrop and loading visuals.
  - Smoother pagination: earlier “near bottom” detection and consistent loading row.
  - Search bar interactions refined for DPAD and IME handoff.
- Person screen:
  - Focus-aware sections (Top/Read More/Known For/Crew) integrated with global focus; backdrop cycles when idle and follows selection when focused.
- Media carousels:
  - `EnhancedMediaCarousel` scrolling made more resilient with debouncing and emergency positioning to keep the selected item visible.
- Media cards:
  - Improved badges/status icons; pending/available state derived from regular and 4K status.

### API/Models
- Sonarr models extended: servers now include tags, optional language profiles, and richer server info (`SonarrServerInfo`).

### Startup / Updates
- Direct flavor: splash checks for updates before config/auth steps; unified loading step feedback maintained.

### Technical
- Centralized DPAD registration per screen; screens save/restore focus and DPAD state via coordinator.
- `ScrollPositionManager` reworked: simplified locking, direction tracking, and periodic cleanup job.
- Project-wide Kotlin/Compose plugin alignment maintained (Kotlin 2.1.0 toolchain).

---
## 0.24.2

### Startup / Configuration
- Sonarr/Radarr loading at startup now shows explicit errors when configuration calls fail (HTTP 4xx/5xx), instead of reporting "Not configured".
- "Not configured" is only shown for successful calls returning an empty list (HTTP 200 with no servers).
- Startup sequence remains blocking: each step waits/timeout-completes before proceeding, reducing misleading splash messages.

### Requests / API Alignment
- Movie requests: removed redundant `tmdbId` field; `mediaId` continues to carry the TMDB id.
- TV requests: include `tvdbId` when available (alongside `mediaId`=TMDB) to improve Sonarr mapping and avoid backend mis-resolution.

### UI/UX
- Request Action Modal: moved season chips below the media status to prevent cramped layout; disabled chip text wrapping so season tags like `S5` never split across lines.

### Minor
- Avoid token refresh while splash/update check is active to prevent interference during startup.

---
## 0.24.1

### Major Features

#### Media Details Screen Overhaul
- **Complete UI/UX redesign** of the Media Details screen with improved layout and navigation
- **Enhanced action button system** with better focus management and D-pad navigation
- **Improved request flow** with separate HD/4K request options and management capabilities
- **Better visual hierarchy** with reorganized content sections and improved spacing
- **Streamlined focus areas** with dedicated constants for different UI elements

#### Automatic Media Details Polling
- **Real-time updates** for media details when downloads are in progress
- **Smart polling intervals** (10s when downloading, 20s when idle)
- **Automatic failure handling** with retry logic and graceful degradation
- **Performance optimizations** to prevent excessive API calls

#### Enhanced Ratings System
- **Improved ratings API integration** with proper endpoint handling for movies vs TV shows
- **Extended ratings models** supporting both combined and flat response formats
- **Better Rotten Tomatoes integration** with comprehensive rating data structure

### Technical Improvements

#### Architecture Changes
- **Removed MediaDetailsDownloadViewModel** - functionality consolidated into SeerrViewModel
- **Enhanced SeerrViewModel** with polling capabilities and improved state management
- **Updated dependency injection** with proper Hilt navigation compose integration
- **Improved API service** with better error handling and endpoint management

#### Dependencies Updated
- **Kotlin**: 2.2.0 → 2.2.20
- **Compose BOM**: 2025.08.01 → 2025.09.00
- **Hilt Navigation Compose**: 1.2.0 → 1.3.0
- **Navigation Compose**: 2.9.3 → 2.9.4
- **Ktor**: 3.2.3 → 3.3.0
- **Activity Compose**: 1.10.1 → 1.11.0
- **Foundation**: 1.9.0 → 1.9.1
- **UI**: 1.9.0 → 1.9.1

#### Code Quality
- **Improved focus management** with better D-pad navigation support
- **Enhanced error handling** throughout the application
- **Better state management** with proper lifecycle handling
- **Code cleanup** and removal of unused functionality

### UI/UX Improvements

#### Request Management
- **Simplified request action modal** with removed "Request More" functionality
- **Better request status display** with improved visual feedback
- **Enhanced download status indicators** with clearer progress representation

#### Navigation Enhancements
- **Improved focus areas** with dedicated constants for different UI sections
- **Better D-pad navigation** throughout the application
- **Enhanced accessibility** for Android TV users

#### Media Information Display
- **Enhanced media info table** with additional metadata support
- **Improved person screen** with better layout and information display
- **Better media discovery** with enhanced filtering and display options

### Configuration and Setup

#### Development Environment
- **Updated code style configuration** for consistent formatting
- **Enhanced project structure** with better organization
- **Improved build configuration** with updated version codes

#### Documentation
- **Comprehensive requirements analysis** with detailed feature specifications
- **Added TODO tracking** with prioritized development roadmap
- **Enhanced project documentation** with better organization

### Bug Fixes

#### API Integration
- **Fixed ratings endpoint handling** for different media types
- **Improved error handling** for network requests
- **Better timeout management** for API calls

#### UI Issues
- **Fixed focus management** issues in various screens
- **Resolved navigation problems** in media details
- **Improved state consistency** across different UI components

### Removed Features

#### Deprecated Functionality
- **Removed MediaDetailsDownloadViewModel** - functionality moved to SeerrViewModel
- **Removed "Request More" button** from request action modal
- **Cleaned up unused code** and deprecated methods

### Version Information

- **Version Code**: 82 → 83
- **Version Name**: 0.24.0 → 0.24.1
- **Target SDK**: 36
- **Minimum SDK**: 25