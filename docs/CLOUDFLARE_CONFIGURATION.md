# Cloudflare Access Configuration Guide for SeerrTV

## Overview

This comprehensive guide provides step-by-step instructions for configuring Cloudflare Access (Zero Trust) to work with the SeerrTV Android TV application. SeerrTV uses **service token authentication** to bypass Cloudflare Access protection by automatically injecting the required headers (`CF-Access-Client-Id` and `CF-Access-Client-Secret`) into API requests.

## What is Cloudflare Access?

Cloudflare Access is a Zero Trust security solution that protects your Seerr, Overseerr, or Jellyseerr server by requiring authentication before allowing access. When enabled, users must authenticate through Cloudflare's identity providers before they can access your server.

## How SeerrTV Handles Cloudflare Protection

SeerrTV automatically detects when your server is protected by Cloudflare Access and provides a configuration step to enter the required credentials. The app uses these credentials to authenticate with Cloudflare and access your protected server.

### Key Features:
- **Automatic Detection**: SeerrTV detects Cloudflare protection when connecting to your server
- **Service Token Authentication**: Uses service tokens to bypass browser-based authentication flows
- **Seamless Integration**: Once configured, Cloudflare authentication happens automatically
- **User-Friendly Setup**: Guided configuration process within the app
- **Multi-language Support**: Configuration interface available in multiple languages

## Prerequisites

Before configuring Cloudflare Access for SeerrTV, you need:

1. **Cloudflare Account**: A Cloudflare account with Zero Trust access
2. **Domain Setup**: Your Seerr, Overseerr, or Jellyseerr server must be behind Cloudflare
3. **Admin Access**: Access to your Cloudflare Zero Trust dashboard

## Step 1: Set Up Cloudflare Access Application

### 1.1 Access Cloudflare Zero Trust Dashboard

1. Log in to your [Cloudflare dashboard](https://dash.cloudflare.com/)
2. Navigate to **Zero Trust** → **Access** → **Applications**
3. Click **"Add an application"**

### 1.2 Configure Your Application

1. **Select Application Type**: Choose **"Self-hosted"**
2. **Application Name**: Enter a descriptive name (e.g., "Seerr Server", "Overseerr Server", or "Jellyseerr Server")
3. **Application Domain**: Enter your server's domain (e.g., `yourdomain.com`, `seerr.yourdomain.com`, `overseerr.yourdomain.com`, or `jellyseerr.yourdomain.com`)
4. **Path**: Set to `/` or the specific path where your Seerr, Overseerr, or Jellyseerr server is hosted

### 1.3 Configure Access Policies

**Important**: For SeerrTV, you need to configure access policies that allow the service token to bypass authentication. You'll typically see policies like:

**Service Token Policies**:
- **ServiceToken**: Policy that includes your service token
- **serviceTokenBypass**: Policy that allows service tokens to bypass authentication flows

**Include Rules** (who can access):
- Service tokens (for SeerrTV app access)
- Specific email addresses (for web browser access)
- Email domains
- Groups

**Exclude Rules** (who is blocked):
- Blocked users
- High-risk countries
- Suspicious devices

**Note**: The identity provider settings (Email OTP, Google OAuth, etc.) are for web browser access only. SeerrTV uses service tokens which bypass these authentication flows entirely.

## Step 2: Create Service Token for SeerrTV

### 2.1 Generate Service Token

1. In your Cloudflare Zero Trust dashboard, go to **Access** → **Service Tokens**
2. Click **"Create Service Token"**
3. **Token Name**: Enter a descriptive name (e.g., "SeerrTV Mobile App")
4. **Client ID**: This will be automatically generated
5. **Client Secret**: This will be automatically generated
6. **Expiration**: Set an appropriate expiration date (recommended: 1 year)

### 2.2 Configure Service Token Permissions

1. **Application**: Select the application you created for your Seerr, Overseerr, or Jellyseerr server
2. **Policies**: Choose policies that allow service token access
   - **ServiceToken**: Include your service token in this policy
   - **serviceTokenBypass**: This policy allows service tokens to bypass authentication
   - **Important**: The service token must be included in the "Include" rules of your access policies
3. **IP Addresses**: Optionally restrict to specific IP addresses

### 2.3 Save Your Credentials

**Important**: Copy and securely store both:
- **Client ID** (CF-Access-Client-Id)
- **Client Secret** (CF-Access-Client-Secret)

These credentials will be needed for SeerrTV configuration.

## Step 3: Configure SeerrTV

SeerrTV offers two configuration methods. Both support Cloudflare Access configuration, but the process differs slightly.

### 3.1 Browser-Based Configuration

**When to use**: Recommended for easier setup with a computer/phone nearby.

1. **Launch SeerrTV** on Android TV
2. **Select "Browser Configuration"** when prompted
3. **Scan QR Code** or **visit the displayed URL** on your computer/phone
4. **Complete Setup in Web Form**:
   - Enter server details (protocol, hostname)
   - **Enter Cloudflare credentials** (Client ID and Client Secret)
   - Choose authentication method and enter credentials
   - Submit the form
5. **SeerrTV Polls Configuration**: The app automatically polls the endpoint to retrieve your configuration
6. **Automatic Testing**: Once configuration is retrieved, SeerrTV tests the connection with all provided credentials

**Advantages**: 
- Easier text input on computer/phone
- Can copy/paste credentials easily
- All configuration done in one web form
- No manual step-by-step wizard on TV

### 3.2 Manual Configuration (TV Remote)

**When to use**: When you prefer to configure directly on the TV or don't have a computer/phone nearby.

1. **Launch SeerrTV** on Android TV
2. **Select "Manual Configuration"** when prompted
3. **Follow the Wizard Steps**:
   - **Step 1**: Enter protocol (HTTP/HTTPS)
   - **Step 2**: Enter hostname
   - **Step 3**: Test connection (Cloudflare detection happens here)
   - **Step 4**: If Cloudflare detected, configure Cloudflare credentials:
     - Use TV remote to enter **Cloudflare Client ID**
     - Use TV remote to enter **Cloudflare Client Secret**
     - Test connection with Cloudflare credentials
   - **Step 5**: Choose authentication method
   - **Step 6**: Enter authentication credentials
   - **Step 7**: Complete setup

**Advantages**:
- Everything done on TV
- No need for external devices
- Step-by-step guidance

### 3.3 How Cloudflare Configuration Differs Between Methods

**Browser-Based Configuration**:
- **Pre-configured**: You provide Cloudflare credentials in the web form along with all other settings
- **No Detection Needed**: SeerrTV uses the provided credentials immediately
- **Polling**: App retrieves complete configuration from the web form endpoint

**Manual Configuration**:
- **Detection-Based**: App tests connection and detects Cloudflare protection
- **Step-by-Step**: Dedicated Cloudflare configuration step appears when protection is detected
- **Real-time Testing**: App tests Cloudflare credentials as you enter them


## Step 4: Verify Configuration

### 4.1 Test Access

1. Complete the SeerrTV configuration
2. The app should successfully connect to your protected server
3. You should see your media library and be able to browse content

### 4.2 Command Line Test

Test your service token with curl:

```bash
curl -H "CF-Access-Client-Id: YOUR_CLIENT_ID" \
     -H "CF-Access-Client-Secret: YOUR_CLIENT_SECRET" \
     https://yourdomain.com/api/v1/status
```

Expected response: `200 OK` with JSON containing server information.

## Security Best Practices

### 4.1 Service Token Management

- **Regular Rotation**: Rotate service tokens periodically (recommended: every 6-12 months)
- **Minimal Permissions**: Only grant necessary permissions to service tokens
- **Secure Storage**: Store credentials securely and never share them publicly
- **Monitoring**: Monitor access logs for unusual activity

### 4.2 Access Policies

- **Principle of Least Privilege**: Only grant access to users who need it
- **Regular Review**: Periodically review and update access policies
- **Device Posture**: Consider requiring device compliance for additional security
- **Location Restrictions**: Restrict access to specific geographic locations if needed

## Advanced Configuration

### 5.1 Multiple Applications

If you have multiple Seerr, Overseerr, or Jellyseerr instances:

1. Create separate Cloudflare Access applications for each instance
2. Generate separate service tokens for each application
3. Configure SeerrTV with the appropriate credentials for each server

### 5.2 Custom Domains

For custom domains or subdomains:

1. Ensure your DNS is properly configured in Cloudflare
2. Set up SSL/TLS certificates
3. Configure the application domain to match your setup

### 5.3 Integration with Other Services

Cloudflare Access can be configured to work alongside:
- **Cloudflare Tunnel**: For secure server access
- **WAF Rules**: For additional security layers
- **Bot Management**: To protect against automated attacks
- **DDoS Protection**: Built-in DDoS mitigation

## Troubleshooting Common Issues

### Issue: "Cloudflare Protection Detected but credentials not configured"

**Solution**: Ensure you've entered both the Client ID and Client Secret in the SeerrTV configuration.

### Issue: "Failed to connect with Cloudflare credentials"

**Possible Causes**:
- Invalid or expired service token
- Service token doesn't have permission for the application
- Incorrect Client ID or Client Secret

**Solution**: 
1. Verify credentials in Cloudflare dashboard
2. Check service token permissions
3. Generate a new service token if necessary

### Issue: "Authentication failed"

**Possible Causes**:
- Service token expired
- Access policies changed
- Server configuration issues

**Solution**:
1. Check service token expiration date
2. Verify access policies in Cloudflare
3. Test server connectivity independently

### Debug Steps

1. **Verify Token Status**: Check expiration in Cloudflare dashboard
2. **Test Manually**: Use curl command to test token
3. **Check Policies**: Verify access policies are correct
4. **Review Logs**: Check Cloudflare Access logs for errors

## Monitoring and Maintenance

### Regular Tasks

- **Monthly**: Review access logs and usage patterns
- **Quarterly**: Audit access policies and permissions
- **Semi-annually**: Rotate service tokens
- **Annually**: Review and update security settings

### Monitoring Tools

- **Cloudflare Analytics**: Traffic and security insights
- **Access Logs**: Detailed authentication logs
- **Security Events**: WAF and bot management events
- **Performance Metrics**: Speed and reliability data

## Support and Resources

### Documentation
- [Cloudflare Zero Trust Documentation](https://developers.cloudflare.com/cloudflare-one/)
- [Cloudflare Access Documentation](https://developers.cloudflare.com/cloudflare-one/applications/)
- [Service Tokens Documentation](https://developers.cloudflare.com/cloudflare-one/identity/service-tokens/)

### Getting Help
- Check the SeerrTV configuration logs for detailed error messages
- Verify your Cloudflare Access configuration in the Zero Trust dashboard
- Test your service token using curl or similar tools
- Join the [Seerr Community Discord](https://discord.gg/nTFk3jHbk5) for support and discussions

## Conclusion

Cloudflare Access provides robust security for your Seerr, Overseerr, or Jellyseerr server while maintaining seamless access through SeerrTV. By following this guide, you can set up a secure, user-friendly system that protects your media server while providing easy access for authorized users.

Remember to regularly review and update your Cloudflare Access configuration to maintain security best practices.
