# Contributing to SeerrTV

Thank you for your interest in contributing to SeerrTV! This document provides guidelines and instructions for contributing to the project.

## How to Contribute

### Reporting Bugs

If you find a bug, please create an issue with:
- A clear title and description
- Steps to reproduce the issue
- Expected vs. actual behavior
- Screenshots if applicable
- Device information (Android TV model, Android version)
- App version (found in Settings > About)

### Suggesting Features

Feature requests are welcome! Please create an issue with:
- A clear description of the feature
- Use case and motivation
- Potential implementation ideas (optional)

### Pull Requests

1. **Fork the repository** and create a feature branch
2. **Make your changes** following the code style guidelines
3. **Test your changes** on an Android TV device or emulator
4. **Update documentation** if needed
5. **Submit a pull request** with a clear description of changes

## Code Style Guidelines

### Kotlin Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4 spaces for indentation
- Maximum line length: 120 characters
- Prefer explicit types for public APIs

### Android TV Specific Requirements

**IMPORTANT**: This is an Android TV application. Follow these critical rules:

- **Do NOT use `onClick` modifiers** - Android TV uses D-pad navigation, not touch
- **Do NOT use `focusable()` or `clickable()` modifiers** for navigation
- **Use the custom navigation component** - All navigation must go through the D-pad navigation system
- **Test with a TV remote** - Always test navigation with D-pad controls, not mouse/touch
- **Focus management** - Ensure all interactive elements are focusable and properly handle focus state

### Compose Guidelines

- Use `@Composable` functions for UI components
- Follow Material Design 3 principles
- Optimize for TV screen sizes and viewing distances
- Ensure proper focus indicators and visual feedback

### Architecture

- Use MVVM pattern with ViewModels
- Dependency injection via Hilt
- State management with StateFlow/Flow
- Navigation via Jetpack Navigation Compose

## Testing Requirements

- Test on physical Android TV device when possible
- Test on Android TV emulator (minimum API level 25)
- Verify D-pad navigation works correctly
- Test with different screen sizes and resolutions
- Verify accessibility features work

## Development Setup

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17 or higher
- Android SDK with API level 25-36
- Android TV emulator or physical device

### Building

```bash
# Clone the repository
git clone https://github.com/devmesh-git/seerrtv.git
cd seerrtv

# Build debug APK
./gradlew assembleDirectDebug

# Build release APK
./gradlew assembleDirectRelease
```

### Configuration

1. Copy `tv/browser-config.properties.template` to `tv/browser-config.properties` and customize if needed
2. For release builds, configure signing (see `SIGNING_SETUP.md`)

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/) format:

```
feat: Add new feature description
fix: Fix bug description
docs: Update documentation
style: Code style changes (formatting, etc.)
refactor: Code refactoring
test: Add or update tests
chore: Maintenance tasks
```

Examples:
- `feat: Add filter by genre functionality`
- `fix: Resolve D-pad navigation issue in settings menu`
- `docs: Update README with setup instructions`

## Code Review Process

1. All PRs require at least one review before merging
2. Address review comments promptly
3. Ensure CI checks pass
4. Keep PRs focused - one feature or fix per PR
5. Update CHANGELOG.md for user-facing changes

## Questions?

If you have questions about contributing, please:
- Check existing issues and discussions
- Create a new issue with the `question` label
- Join the [Seerr Community Discord](https://discord.gg/nTFk3jHbk5) for discussions and support

Thank you for contributing to SeerrTV!
