# GitHub Labels

This document describes the recommended GitHub labels for the SeerrTV project. Labels help organize issues and pull requests, making it easier for contributors to find tasks that match their interests and skill level.

## Label Categories

### Priority Labels

- `priority: high` - Issues that need immediate attention
- `priority: medium` - Important issues that should be addressed soon
- `priority: low` - Nice to have, can be addressed when time permits

### Type Labels

- `bug` - Something isn't working as expected (automatically added by bug report template)
- `enhancement` - New feature or request (automatically added by feature request template)
- `documentation` - Improvements or additions to documentation
- `question` - Further information is requested
- `help wanted` - Extra attention is needed, community contributions welcome
- `good first issue` - Good for newcomers, perfect starting point for new contributors

### Platform Labels

- `android-tv` - Specific to Android TV functionality or navigation
- `ui/ux` - User interface or user experience related
- `backend` - Server-side or API related
- `performance` - Performance optimization or issues

### Status Labels

- `wontfix` - This will not be worked on
- `duplicate` - This issue or pull request already exists
- `invalid` - This doesn't seem right
- `blocked` - Blocked by another issue or external factor
- `needs-triage` - Requires maintainer review and classification

## Recommended Labels to Create

To set up these labels in your GitHub repository:

1. Go to your repository on GitHub
2. Navigate to **Issues** → **Labels**
3. Click **New label** and create each label with the appropriate color

### Essential Labels (Start Here)

Create these labels first as they're most commonly used:

| Label | Description | Color | Usage |
|-------|-------------|-------|-------|
| `good first issue` | Good for newcomers | `#7057ff` (purple) | Tag issues that are suitable for first-time contributors |
| `help wanted` | Extra attention needed | `#008672` (teal) | Tag issues where community help would be appreciated |
| `bug` | Something isn't working | `#d73a4a` (red) | Automatically added via bug report template |
| `enhancement` | New feature or request | `#a2eeef` (light blue) | Automatically added via feature request template |
| `documentation` | Documentation improvements | `#0075ca` (blue) | For docs, README, or guide improvements |
| `question` | Further information requested | `#d876e3` (magenta) | For questions that need clarification |
| `android-tv` | Android TV specific | `#1d76db` (dark blue) | For TV-specific features or navigation issues |
| `wontfix` | Will not be fixed | `#ffffff` (white, black text) | For issues that won't be addressed |
| `duplicate` | Duplicate issue | `#cfd3d7` (gray) | For duplicate issues |
| `invalid` | Invalid issue | `#e4e669` (yellow) | For invalid or incorrectly reported issues |

### Color Coding Guide

- **Red tones** (`#d73a4a`, `#b60205`) - Critical issues, bugs
- **Blue tones** (`#0075ca`, `#1d76db`) - Features, platform-specific
- **Green tones** (`#008672`, `#0e8a16`) - Good for community, help wanted
- **Purple/Magenta** (`#7057ff`, `#d876e3`) - Newcomer-friendly, questions
- **Yellow** (`#e4e669`, `#fbca04`) - Warnings, needs attention
- **Gray/White** (`#cfd3d7`, `#ffffff`) - Status, inactive

## Label Usage Guidelines

### For Maintainers

- Apply `good first issue` to issues that are:
  - Well-documented with clear requirements
  - Limited in scope
  - Don't require deep domain knowledge
  - Have clear acceptance criteria

- Apply `help wanted` when:
  - The issue is valuable but maintainers don't have time
  - Community input would be beneficial
  - The issue requires specific expertise

- Use `android-tv` for issues specifically related to:
  - D-pad navigation problems
  - TV-specific UI/UX concerns
  - Remote control interactions
  - Focus management issues

### For Contributors

- Look for `good first issue` labels when starting out
- Check `help wanted` for opportunities to contribute
- Use `question` label when you need clarification before starting work

## Automating Labels

GitHub issue templates automatically apply labels:
- `bug` - Applied when using the bug report template
- `enhancement` - Applied when using the feature request template

You can enhance templates to suggest additional labels or create more specific templates for different issue types.

## Label Maintenance

Periodically review and update labels:
- Remove unused labels
- Consolidate similar labels
- Ensure labels are being used consistently
- Update this documentation as labels evolve
