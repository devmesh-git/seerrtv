# SeerrTV

SeerrTV is an Android TV application that provides a comprehensive media browsing and request management interface for Seerr, Jellyseerr and, Overseerr media request systems. It enables users to browse, search, request, and monitor the status of media requests directly from their Android TV device.

## Features

### Media Browsing & Discovery
- **Unified Browse Screens** - Dedicated Movies and Series browse screens with grid-based layout optimized for Android TV
- **Advanced Filtering System** - Comprehensive filtering with 13+ filter categories:
  - Release Date / First Air Date (date range)
  - Genres (multi-select with search)
  - Keywords (searchable multi-select)
  - Original Language (single selection)
  - Content Rating (multi-select with region support)
  - Runtime, User Score, Vote Count (range filters)
  - Studios (Movies only - searchable)
  - Networks (TV only - searchable multi-select)
  - Streaming Services (multi-select with region support)
- **Sort Menu** - Sort by Popularity, Release Date, First Air Date, TMDB Rating, or Title (A→Z / Z→A) with bidirectional sorting
- **Enhanced Search** - Real-time search with D-pad optimized navigation, inline results, and state preservation
- **Category Browsing** - Browse by categories (Recently Added, Recent Requests, Trending, Popular Movies, Movie Genres, etc.)
- **Detailed Media Information** - Cast, crew, ratings, and related content
- **Dynamic Backdrop System** - Cycling backdrop images with gradient overlays
- **Person Information** - Filmography browsing with biographical information
- **Category Exploration** - Browse by genres, studios, and networks with dedicated discovery screens
- **Infinite Scroll Pagination** - Automatic loading of additional results as you browse
- **State Preservation** - Grid position, selection, filters, and sort preferences preserved during navigation

### Request Management
- **Movie & TV Requests** - Request movies and TV shows with configurable options
- **Tag Support** - Organize requests with server-specific tags (multi-select)
- **Sonarr Lookup** - Interactive series matching when TVDB ID is missing (with poster images and D-pad navigation)
- **HD/4K Support** - Separate request options for HD and 4K quality tiers
- **Server Selection** - Choose from multiple Radarr/Sonarr servers when configured
- **Quality Profile Selection** - Select from configured quality profiles
- **Root Folder Selection** - Choose root folder for TV show requests (when enabled)
- **Season Selection** - Multi-select season picker for TV series
- **Real-time Status Monitoring** - Monitor request status and download progress with automatic refresh
- **Permission-Based Actions** - Support for different user permission levels (admin vs. regular user)
- **Request Approval Workflow** - Administrators can approve/decline pending requests
- **Request Deletion** - Delete requests and media files (based on permissions)
- **Media Status Indicators** - Visual status icons including deleted and blacklisted states

### Issue Reporting & Management
- **Permission-Based Access** - Issue functionality controlled by VIEW_ISSUES and CREATE_ISSUES permissions
- **Issue Reporting** - Report media quality and playback issues directly from the app
- **Categorized Issue Types** - Video, Audio, Subtitle, and Other categories with precanned descriptions
- **Season/Episode Selection** - Select specific seasons/episodes for TV series issues
- **Custom Descriptions** - Add detailed problem descriptions beyond precanned options
- **Comment System** - Discussion and updates on issues with threaded comments
- **Issue Status Tracking** - Open and Resolved status with visual indicators
- **Issue Count Indicators** - Display number of reported issues on media details screen
- **Issue Management** - View and manage existing issues with detailed history and status updates

### Authentication & Configuration
- **Multiple Authentication Methods**:
  - API Key authentication
  - Local User authentication
  - Plex authentication with PIN code system
  - Jellyfin/Emby authentication (for Jellyseerr and Seerr)
- **Automatic Server Detection** - Detects Seerr, Overseerr, or Jellyseerr server types automatically
- **Browser-Based Setup** - QR code or URL-based configuration for easier setup
- **Manual Wizard** - Step-by-step configuration wizard with guided setup
- **Cloudflare Protection** - Bypass Cloudflare Access protection with service token authentication
- **Flexible Configuration** - Supports HTTP/HTTPS with SSL certificate validation
- **Connection Testing** - Real-time connection validation before proceeding

### TV-Optimized Interface
- **Material Design 3** - Theming optimized for TV screens with consistent styling
- **D-pad Navigation** - Comprehensive D-pad navigation with focus management throughout the app
- **Visual Feedback** - Clear focus indicators and selection state feedback
- **Slide Animations** - Smooth slide-in/out animations for modals, drawers, and menus
- **Auto-Scrolling** - Content automatically scrolls to keep focused items visible
- **Expandable Content** - Expandable text areas for long descriptions with "Read More/Less"
- **Smart Layout** - Dynamic layout adjustment based on content availability
- **Persistent Top Bar** - Always-visible top bar with search, settings, and clock
- **Settings Menu** - Slide-in settings panel with auto-scrolling and organized submenus

### Localization & Internationalization
- **Independent App Language** - User interface language is independent of Discovery Language
- **Language Selection** - Choose from supported languages (English, German, Spanish, French, Japanese, Dutch, Portuguese, Chinese)
- **On-the-Fly Language Change** - Switch UI language from Settings Menu without restarting
- **Automatic Migration** - Existing users automatically use system default language (if supported) or English
- **Regional Settings** - Default streaming region setting for watch providers and content ratings

### External Integration
- **Media Server Playback**:
  - Direct integration with Plex for media playback
  - Jellyfin media server support
  - Emby media server support with dual-app compatibility (Android TV and regular Android apps)
- **Trailer Viewing** - YouTube integration for trailer playback
- **Metadata Integration** - TMDb (The Movie Database) integration for ratings, cast, crew, and metadata
- **Downstream Services**:
  - Sonarr server integration for TV series management
  - Radarr server integration for movie management
  - Multi-server support for both HD and 4K quality tiers
  - Server-specific quality profiles and root folder selection

## Technologies Used

- **UI Framework**: Jetpack Compose for modern, declarative UI
- **Dependency Injection**: Hilt for clean architecture
- **Image Loading**: Coil for efficient image caching and loading
- **Asynchronous Operations**: Coroutines and Flow for reactive programming
- **Navigation**: Jetpack Navigation with custom transitions
- **Data Storage**: SharedPreferences for configuration
- **HTTP Client**: Ktor for API communication
- **Platform**: Android TV SDK for TV-specific features

## Requirements

- **Java Development Kit (JDK)**: Java 21 (required)
  - **macOS** (recommended): Android Studio includes Java 21 (JBR) which is automatically used via `gradle.properties`
    - Alternative: Using Homebrew: `brew install openjdk@21`
    - Or download from [Adoptium](https://adoptium.net/)
  - **Linux**: 
    - Ubuntu/Debian: `sudo apt install openjdk-21-jdk`
    - Fedora/RHEL: `sudo dnf install java-21-openjdk-devel`
    - Arch Linux: `sudo pacman -S jdk21-openjdk`
    - Or download from [Adoptium](https://adoptium.net/)
  - **Windows**: 
    - Download from [Adoptium](https://adoptium.net/)
    - Or use [Chocolatey](https://chocolatey.org/): `choco install openjdk21`
  - Verify installation: `java -version` (should show version 21)

## Getting Started

1. Clone the repository
2. Open the project in Android Studio
3. Build and run the application on an Android TV device or emulator

## Development Setup

### Building for Distribution

SeerrTV supports two distribution methods with different build variants:

#### Play Store Distribution (.aab)

For Google Play Store releases, build the **play** flavor which excludes update functionality and sensitive permissions:

```bash
# Debug build for testing
# macOS/Linux:
./gradlew bundlePlayDebug
# Windows:
gradlew.bat bundlePlayDebug

# Release build for Play Store submission
# macOS/Linux:
./gradlew bundlePlayRelease
# Windows:
gradlew.bat bundlePlayRelease
```

The Play Store build:
- Contains no auto-update functionality
- Excludes `REQUEST_INSTALL_PACKAGES` permission
- Fully compliant with Play Store policies
- Generates `.aab` files for Play Console upload

#### Direct Distribution (.apk)

For sideloading and direct distribution, build the **direct** flavor which includes update functionality:

```bash
# Debug build for testing
# macOS/Linux:
./gradlew assembleDirectDebug
# Windows:
gradlew.bat assembleDirectDebug

# Release build for direct distribution
# macOS/Linux:
./gradlew assembleDirectRelease
# Windows:
gradlew.bat assembleDirectRelease
```

**Platform Notes**:
- **macOS**: Android Studio's bundled Java 21 (JBR) is automatically used via `gradle.properties`. If you don't have Android Studio, set `JAVA_HOME` environment variable to your JDK 21 installation path, or update `gradle.properties` to set `org.gradle.java.home`.
- **Linux/Windows**: Set `JAVA_HOME` environment variable to your JDK 21 installation path, or override it in `gradle.properties` by setting `org.gradle.java.home`.

The direct build:
- Includes auto-update functionality (fetches from GitHub Releases API)
- Contains `REQUEST_INSTALL_PACKAGES` permission
- Generates `.apk` files for direct installation

#### Build Variants Summary

| Variant | Output | Auto-Updates | Permissions | Use Case |
|---------|--------|--------------|-------------|----------|
| `play` | `.aab` | ❌ Disabled | Minimal | Google Play Store |
| `direct` | `.apk` | ✅ Enabled | Full | Sideloading, websites |

#### Signing Configuration

For release builds, you need to configure APK signing:

1. **Create signing properties file**:
   ```bash
   # macOS/Linux:
   cp tv/signing.properties.template tv/signing.properties
   
   # Windows (Command Prompt):
   copy tv\signing.properties.template tv\signing.properties
   
   # Windows (PowerShell):
   Copy-Item tv\signing.properties.template tv\signing.properties
   ```

2. **Edit `tv/signing.properties`** with your keystore information:
   ```properties
   storeFile=../localSigningKey.jks
   storePassword=your_keystore_password
   keyAlias=your_key_alias
   keyPassword=your_key_password
   ```

3. **Security Notes**:
   - `signing.properties` is automatically gitignored
   - Debug builds use the default debug keystore automatically
   - For CI/CD, you can use environment variables: `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

For detailed signing setup instructions, see [Signing Setup Guide](docs/SIGNING_SETUP.md).

#### Configuration Files

**Browser Configuration**: The browser-based configuration endpoint can be customized by creating a `browser-config.properties` file:
- Copy `tv/browser-config.properties.template` to `tv/browser-config.properties`
- Update the `browser.config.base.url` property if needed
- Default value points to `https://seerrtv.devmesh.ca`
- This file is gitignored for security

**Release Workflow**:
1. **For Play Store**: 
   - macOS/Linux: Build with `./gradlew bundlePlayRelease`
   - Windows: Build with `gradlew.bat bundlePlayRelease`
   - Upload the generated `.aab` to Play Console
2. **For Direct Distribution**: 
   - macOS/Linux: Build with `./gradlew assembleDirectRelease`
   - Windows: Build with `gradlew.bat assembleDirectRelease`
   - Upload the generated `.apk` to a GitHub Release
   - The app automatically checks `https://api.github.com/repos/devmesh-git/seerrtv/releases/latest` for updates
   - Ensure the release tag follows semantic versioning (e.g., `v0.26.3`)

**Note**: On Windows, you can also use `./gradlew` in Git Bash, WSL, or PowerShell. The `gradlew.bat` command works in all Windows shells including Command Prompt.

Both builds share the same codebase and features - only the update mechanism and permissions differ based on the build variant.

## App Configuration

On first run, the app will guide you through the configuration process:

1. Choose configuration method:
   - Browser-based setup (QR code or URL)
   - Manual step-by-step wizard

2. Configure server connection:
   - Enter protocol (HTTP/HTTPS)
   - Set hostname
   - Select authentication method
   - Configure Cloudflare settings if needed (see [Cloudflare Access Configuration Guide](docs/CLOUDFLARE_CONFIGURATION.md))

3. Complete authentication:
   - Follow on-screen instructions for your chosen auth method
   - For Plex auth: Visit plex.tv/link and enter the provided PIN
   - For API key: Enter your server's API key
   - For local user: Enter username and password

For detailed configuration guides, see:
- [Cloudflare Access Configuration Guide](docs/CLOUDFLARE_CONFIGURATION.md) - Setting up Cloudflare Zero Trust protection

## User Guides

### Navigation Guide

The app is optimized for TV remote control navigation:

- **D-pad Navigation**:
  - UP/DOWN: Move between categories
  - LEFT/RIGHT: Navigate within categories
  - ENTER: Select focused item
  - BACK: Return to previous screen

- **Special Actions**:
  - Press UP at top of screen to refresh content
  - Use BACK button to close modals
  - D-pad navigation preserves selection state

### Performance Features

- Lazy loading of media content
- Efficient image caching
- Optimized focus management
- State preservation during navigation
- Debounced search to prevent excessive API calls
- Smart refresh mechanisms
- Memory-efficient data structures

## Contributing

We welcome contributions to SeerrTV! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on:

- How to report bugs
- How to suggest features
- Pull request process
- Code style guidelines
- Android TV-specific requirements

**Important**: SeerrTV is an Android TV application. All contributions must follow Android TV navigation patterns:
- Use D-pad navigation, not touch/click
- Test with TV remote controls
- Follow the custom navigation component patterns

## Downloads

### Play Store

SeerrTV is available on Google Play Store for official releases.

### Sideload Builds

For users who prefer to sideload builds, direct APK releases are available on GitHub:
- **GitHub Releases**: [https://github.com/devmesh-git/seerrtv/releases](https://github.com/devmesh-git/seerrtv/releases)
- The app automatically checks for updates from GitHub Releases when you open it

Release notes and changelogs are also announced in the official Discord community.

## Community

SeerrTV is part of the Seerr community:
- **Seerr Community Website**: [https://seerr.dev/](https://seerr.dev/)
- **Discord**: [Join the Seerr Community Discord](https://discord.gg/nTFk3jHbk5) for support, discussions, and release announcements

This project was originally created by [DevMesh](https://devmesh.ca) and is now community-driven.

## Support

For issues, feature requests, or questions:
- Create an issue on GitHub
- Join the [Seerr Community Discord](https://discord.gg/nTFk3jHbk5) - Official community for support and discussions
- Visit the [Seerr community website](https://seerr.dev/)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
