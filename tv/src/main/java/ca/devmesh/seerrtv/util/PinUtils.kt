package ca.devmesh.seerrtv.util

import java.security.MessageDigest

object PinUtils {
    // Local-only; not meant for high-security adversaries. Just prevents storing plaintext PIN.
    private const val PIN_SALT = "seerrtv_pin_salt_v1"

    fun hashPin(pin: String): String {
        val normalized = pin.trim()
        if (normalized.isEmpty()) return ""
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = (PIN_SALT + ":" + normalized).toByteArray(Charsets.UTF_8)
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, pinHash: String): Boolean {
        if (pinHash.isBlank()) return false
        return hashPin(pin) == pinHash
    }
}

