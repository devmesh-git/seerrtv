# Security Policy

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security vulnerability, please report it using one of the following methods:

### Preferred Method: GitHub Issue

Create a new issue on GitHub with the following information:
- Type of vulnerability (e.g., authentication bypass, data exposure, etc.)
- Steps to reproduce the vulnerability
- Potential impact and severity assessment
- Suggested fix (if you have one)

**Note**: While security issues can be sensitive, we prefer transparency. If you have concerns about disclosing publicly, you can mark the issue appropriately or include "Security:" in the title.

### Alternative: Discord Support Channel

If you prefer to discuss the vulnerability first, you can post in the [#support](https://discord.gg/nTFk3jHbk5) channel in the [SeerrTV Community Discord](https://discord.gg/nTFk3jHbk5). Please include the same information as listed above.

## Response and Fix Process

- **Initial response**: Within 48 hours
- **Status update**: Within 7 days
- **Fix timeline**: Security fixes will be addressed in the next version release
  - Critical issues: As soon as possible (typically within 7 days)
  - High priority: Within 30 days
  - Medium/Low priority: Next scheduled release

**Note**: We do not backport security fixes to older versions. All security updates are included in new version releases available through standard distribution channels (Play Store, direct releases, etc.).

## Disclosure Policy

We follow responsible disclosure:
- We will acknowledge receipt of your report
- We will keep you informed of the progress
- Once fixed, we will credit you (if desired) in the CHANGELOG.md and release notes

## Security Best Practices

For users of SeerrTV:

1. **Keep the app updated** - Always use the latest version
2. **Secure your Seerr/Overseerr/Jellyseerr server** - Ensure your media server has proper authentication and HTTPS enabled
3. **Use API keys securely** - Never share your API keys or credentials
4. **Review permissions** - Only grant necessary permissions when configuring the app

## Security Considerations

SeerrTV:
- Stores authentication credentials securely using Android SharedPreferences (encrypted on modern Android versions)
- Does not collect or transmit user data beyond what is necessary for functionality
- Communicates only with your configured Seerr/Overseerr/Jellyseerr server
- Uses HTTPS for all API communications when available

## Known Security Limitations

- The app supports self-signed certificates for local servers (development/testing only)
- Cloudflare Access credentials are stored in SharedPreferences (ensure your device is secured)

## Getting Help

If you have concerns about security or privacy:
- **GitHub**: Create an issue on GitHub (preferred)
- **Discord**: Post in the [#support](https://discord.gg/nTFk3jHbk5) channel in the [SeerrTV Community Discord](https://discord.gg/nTFk3jHbk5) for discussions
