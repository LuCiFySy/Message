package com.saurabh.messages

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ScheduledMessage(
    val id: Long,
    val threadId: String,
    val address: String,
    val body: String,
    val scheduledAt: Long,
    val missed: Boolean = false
)

object ScheduledMessageStore {

    private const val PREFS = "scheduled_messages"
    private const val KEY_MESSAGES = "messages"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun getAll(context: Context): List<ScheduledMessage> {
        val raw = prefs(context).getString(KEY_MESSAGES, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            val result = ArrayList<ScheduledMessage>(array.length())

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                result.add(
                    ScheduledMessage(
                        id = obj.getLong("id"),
                        threadId = obj.optString("threadId"),
                        address = obj.optString("address"),
                        body = obj.optString("body"),
                        scheduledAt = obj.getLong("scheduledAt"),
                        missed = obj.optBoolean("missed", false)
                    )
                )
            }

            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(context: Context, message: ScheduledMessage) {
        val list = getAll(context).toMutableList()
        list.add(message)
        save(context, list)
    }

    @Synchronized
    fun update(context: Context, message: ScheduledMessage) {
        val list = getAll(context).toMutableList()
        val index = list.indexOfFirst { it.id == message.id }

        if (index >= 0) {
            list[index] = message
            save(context, list)
        }
    }

    @Synchronized
    fun remove(context: Context, id: Long) {
        val list = getAll(context)
            .filterNot { it.id == id }

        save(context, list)
    }

    @Synchronized
    fun get(context: Context, id: Long): ScheduledMessage? {
        return getAll(context).firstOrNull { it.id == id }
    }

    @Synchronized
    fun getForThread(
        context: Context,
        threadId: String,
        address: String
    ): List<ScheduledMessage> {
        return getAll(context)
            .filter {
                it.threadId == threadId ||
                    (threadId.isBlank() && it.address == address)
            }
            .sortedBy { it.scheduledAt }
    }

    @Synchronized
    fun markMissed(context: Context, id: Long) {
        val message = get(context, id) ?: return

        update(
            context,
            message.copy(missed = true)
        )
    }

    private fun save(
        context: Context,
        messages: List<ScheduledMessage>
    ) {
        val array = JSONArray()

        messages.forEach { message ->
            array.put(
                JSONObject().apply {
                    put("id", message.id)
                    put("threadId", message.threadId)
                    put("address", message.address)
                    put("body", message.body)
                    put("scheduledAt", message.scheduledAt)
                    put("missed", message.missed)
                }
            )
        }

        prefs(context)
            .edit()
            .putString(KEY_MESSAGES, array.toString())
            .apply()
    }
}
