# Signing Setup for SeerrTV

## Overview
This project uses a properties file to configure APK signing, which allows for secure credential management while keeping the build configuration flexible.

## Setup Instructions

### 1. Create signing.properties
Copy the template file and fill in your actual values:
```bash
cp tv/signing.properties.template tv/signing.properties
```

### 2. Edit signing.properties
Update the file with your actual keystore information:
```properties
# Path to your keystore file (relative to project root)
storeFile=../localSigningKey.jks

# Your keystore password
storePassword=your_actual_keystore_password

# Your key alias
keyAlias=your_actual_key_alias

# Your key password
keyPassword=your_actual_key_password
```

### 3. Verify Keystore Path
Make sure your keystore file exists at the specified path. The default path assumes `localSigningKey.jks` is located one directory above the project root.

## Security Notes

- `signing.properties` is automatically ignored by git (added to .gitignore)
- The template file (`signing.properties.template`) can be committed to git
- For CI/CD environments, you can use environment variables instead:
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`

## Building Signed APKs

### Debug Builds
Debug builds use a default debug keystore that will be generated automatically.

### Release Builds
Release builds will use the signing configuration from `signing.properties`:
```bash
# Build direct release variant
./gradlew assembleDirectRelease

# Build play release variant  
./gradlew assemblePlayRelease
```

## Troubleshooting

If you encounter signing issues:
1. Verify the keystore file path is correct
2. Check that passwords and aliases match your keystore
3. Ensure the keystore file is accessible from the project directory
4. For CI/CD, verify environment variables are set correctly 