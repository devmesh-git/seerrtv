package ca.devmesh.seerrtv.util

import ca.devmesh.seerrtv.model.MediaType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

enum class Permission(val value: Int) {
    NONE(0),
    ADMIN(2),
    MANAGE_SETTINGS(4),             // Jellyseerr Only
    MANAGE_USERS(8),
    MANAGE_REQUESTS(16),
    REQUEST(32),
    VOTE(64),
    AUTO_APPROVE(128),
    AUTO_APPROVE_MOVIE(256),
    AUTO_APPROVE_TV(512),
    REQUEST_4K(1024),
    REQUEST_4K_MOVIE(2048),
    REQUEST_4K_TV(4096),
    REQUEST_ADVANCED(8192),
    REQUEST_VIEW(16384),
    AUTO_APPROVE_4K(32768),
    AUTO_APPROVE_4K_MOVIE(65536),
    AUTO_APPROVE_4K_TV(131072),
    REQUEST_MOVIE(262144),
    REQUEST_TV(524288),
    MANAGE_ISSUES(1048576),
    VIEW_ISSUES(2097152),
    CREATE_ISSUES(4194304),
    AUTO_REQUEST(8388608),
    AUTO_REQUEST_MOVIE(16777216),
    AUTO_REQUEST_TV(33554432),
    RECENT_VIEW(67108864),
    WATCHLIST_VIEW(134217728),      // Jellyseerr Only
    MANAGE_BLACKLIST(268435456),    // Jellyseerr Only
    VIEW_BLACKLIST(1073741824)      // Jellyseerr Only
}

object CommonUtil {
    /**
     * Check if the given permissions include a specific permission
     * @param userPermissions The user's permission value
     * @param requiredPermission The permission to check for
     * @return true if the user has the required permission
     */
    fun hasPermission(userPermissions: Int, requiredPermission: Permission): Boolean {
        // ADMIN permission grants all access
        if ((userPermissions and Permission.ADMIN.value) != 0) return true
        return (userPermissions and requiredPermission.value) != 0
    }

    /**
     * Check if the given permissions include any of the specified permissions
     * @param userPermissions The user's permission value
     * @param requiredPermissions List of permissions to check for
     * @return true if the user has any of the required permissions
     */
    fun hasAnyPermission(userPermissions: Int, vararg requiredPermissions: Permission): Boolean {
        // ADMIN permission grants all access
        if ((userPermissions and Permission.ADMIN.value) != 0) return true
        
        return requiredPermissions.any { permission ->
            (userPermissions and permission.value) != 0
        }
    }

    /**
     * Check if a user can request media based on their permissions and media type
     * @param userPermissions The user's permission value
     * @param mediaType The type of media (movie or tv)
     * @param is4k Whether this is a 4K request
     * @return true if the user can request the media
     */
    fun canRequest(userPermissions: Int?, mediaType: MediaType?, is4k: Boolean = false): Boolean {
        if (userPermissions == null || mediaType == null) return false
        
        // ADMIN permission grants all access
        if ((userPermissions and Permission.ADMIN.value) != 0) return true
        
        // First check if user has general REQUEST permission
        if ((userPermissions and Permission.REQUEST.value) == 0) return false
        
        return when {
            // 4K request checks
            is4k -> when (mediaType) {
                MediaType.MOVIE -> hasAnyPermission(userPermissions, 
                    Permission.REQUEST_4K,
                    Permission.REQUEST_4K_MOVIE
                )
                MediaType.TV -> hasAnyPermission(userPermissions,
                    Permission.REQUEST_4K,
                    Permission.REQUEST_4K_TV
                )
            }
            // Standard request checks
            else -> when (mediaType) {
                MediaType.MOVIE -> hasAnyPermission(userPermissions,
                    Permission.REQUEST,
                    Permission.REQUEST_MOVIE
                )
                MediaType.TV -> hasAnyPermission(userPermissions,
                    Permission.REQUEST,
                    Permission.REQUEST_TV
                )
            }
        }
    }

    /**
     * Check if a user can delete a specific request
     * @param userPermissions The user's permission value
     * @param isRequestor true if the user is the one who made the request
     * @param isRequestPending true if the request status is pending
     * @return true if the user can delete the request
     */
    fun canDeleteRequest(userPermissions: Int?, isRequestor: Boolean, isRequestPending: Boolean): Boolean {
        // If permissions are null, only allow deletion if user is requestor and request is pending
        if (userPermissions == null) {
            return isRequestor && isRequestPending
        }
        
        // Admins and users with MANAGE_REQUESTS can delete any request
        if (hasAnyPermission(userPermissions, Permission.ADMIN, Permission.MANAGE_REQUESTS)) {
            return true
        }
        
        // Regular users can only delete their own pending requests
        val canDelete = isRequestor
        return canDelete
    }

    /**
     * Check if a user can view issues
     * MANAGE_ISSUES permission grants view access
     * @param userPermissions The user's permission value
     * @return true if the user has permission to view issues
     */
    fun canViewIssues(userPermissions: Int?): Boolean {
        if (userPermissions == null) return false
        return hasAnyPermission(userPermissions, 
            Permission.ADMIN,
            Permission.MANAGE_ISSUES, 
            Permission.VIEW_ISSUES
        )
    }

    /**
     * Check if a user can create issues
     * MANAGE_ISSUES permission grants create access
     * @param userPermissions The user's permission value
     * @return true if the user has permission to create issues
     */
    fun canCreateIssues(userPermissions: Int?): Boolean {
        if (userPermissions == null) return false
        return hasAnyPermission(userPermissions, 
            Permission.ADMIN,
            Permission.MANAGE_ISSUES, 
            Permission.CREATE_ISSUES
        )
    }

    fun formatDate(date: String?): String {
        if (date.isNullOrEmpty()) return ""
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
        return try {
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate ?: return "")
        } catch (_: Exception) {
            ""
        }
    }

    fun formatDollars(amount: Long?): String {
        if (amount == null) return ""
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        format.maximumFractionDigits = 0
        return format.format(amount)
    }

    /**
     * Convert a permission integer into a list of permission strings
     * @param permissions The permission integer to convert
     * @return List of permission strings
     */
    fun getPermissionsList(permissions: Int): List<String> {
        // If user has ADMIN permission, just return "Admin"
        if (hasPermission(permissions, Permission.ADMIN)) {
            return listOf("Admin")
        }

        // Otherwise, get all permissions
        return Permission.entries.filter { permission ->
            hasPermission(permissions, permission)
        }.map { permission ->
            // Format the permission name nicely
            permission.name
                .replace("_", " ")
                .lowercase()
                .split(" ")
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
        }
    }
} 