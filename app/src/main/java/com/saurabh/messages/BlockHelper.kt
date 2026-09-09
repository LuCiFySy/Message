package com.saurabh.messages

import android.content.Context
import android.telephony.PhoneNumberUtils

object BlockHelper {

    private const val PREFS = "messages_settings"
    private const val BLOCKED_KEY = "blocked_addresses"

    private fun normalize(address: String): String {
        return PhoneNumberUtils.normalizeNumber(address)
            .ifBlank { address.trim() }
    }

    fun isBlocked(context: Context, address: String): Boolean {
        val normalized = normalize(address)

        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(BLOCKED_KEY, emptySet())
            ?.contains(normalized) == true
    }

    fun block(context: Context, address: String) {
        val normalized = normalize(address)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val blocked = prefs.getStringSet(BLOCKED_KEY, emptySet())
            ?.toMutableSet() ?: mutableSetOf()

        blocked.add(normalized)

        prefs.edit()
            .putStringSet(BLOCKED_KEY, blocked)
            .apply()
    }

    fun unblock(context: Context, address: String) {
        val normalized = normalize(address)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val blocked = prefs.getStringSet(BLOCKED_KEY, emptySet())
            ?.toMutableSet() ?: mutableSetOf()

        blocked.remove(normalized)

        prefs.edit()
            .putStringSet(BLOCKED_KEY, blocked)
            .apply()
    }

    fun getBlockedAddresses(context: Context): Set<String> {
        return context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(BLOCKED_KEY, emptySet())
            ?.toSet()
            ?: emptySet()
    }
}
