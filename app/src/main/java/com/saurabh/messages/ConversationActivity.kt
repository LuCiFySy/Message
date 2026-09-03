package com.saurabh.messages
import android.graphics.Typeface
import android.view.ViewGroup
import android.graphics.Color
import android.provider.Telephony
import android.provider.ContactsContract
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.telephony.SmsManager
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationActivity : AppCompatActivity() {

    companion object {
        private const val SEND_SMS_REQUEST = 200
    }

    private var threadId: String = ""
    private var address: String = ""

    private lateinit var messageList: LinearLayout
    private lateinit var messageInput: EditText

    private var selectionMode = false
    private val selectedMessageIds = LinkedHashSet<Long>()
    private val selectedMessageBodies = LinkedHashMap<Long, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_conversation)

        val conversationRoot = findViewById<View>(R.id.conversationRoot)
        val composerContainer = findViewById<View>(R.id.composerContainer)

        ViewCompat.setOnApplyWindowInsetsListener(conversationRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                0
            )

            composerContainer.translationY = -ime.bottom.toFloat()

            insets
        }

        ViewCompat.requestApplyInsets(conversationRoot)

threadId = intent.getStringExtra("thread_id") ?: ""
        address = intent.getStringExtra("address") ?: "Unknown"

val contactName = getContactName(address) ?: address

findViewById<TextView>(R.id.conversationTitle).text = contactName
        findViewById<TextView>(R.id.conversationAddress).text = address

        val avatar = findViewById<TextView>(R.id.conversationAvatar)
        avatar.text = contactName
            .filter { it.isLetterOrDigit() }
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"

        messageList = findViewById(R.id.messageList)
        messageInput = findViewById(R.id.messageInput)

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.sendButton).setOnClickListener {
            sendMessage()
        }

        markConversationRead()
        loadMessages()
    }

    override fun onResume() {
        super.onResume()

        if (::messageList.isInitialized) {
            markConversationRead()
            loadMessages()
        }
    }

    private fun loadMessages() {
        messageList.removeAllViews()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.THREAD_ID}=?",
                arrayOf(threadId),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->

                val addressIndex =
                    cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex =
                    cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex =
                    cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex =
                    cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    val messageId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    )
                    val body = cursor.getString(bodyIndex) ?: ""
                    val date = cursor.getLong(dateIndex)
                    val type = cursor.getInt(typeIndex)
                    val messageAddress =
                        cursor.getString(addressIndex) ?: address

                    addMessageBubble(
                        messageId = messageId,
                        body = body,
                        date = date,
                        type = type,
                        messageAddress = messageAddress
                    )
                }
            }

            messageList.post {
                val scrollView = findViewById<android.widget.ScrollView>(
                    R.id.messageScroll
                )
                scrollView.fullScroll(View.FOCUS_DOWN)
            }

        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "SMS permission is required",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun addMessageBubble(
        messageId: Long,
        body: String,
        date: Long,
        type: Int,
        messageAddress: String
    ) {
        val outgoing =
            type == Telephony.Sms.MESSAGE_TYPE_SENT ||
            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (outgoing) Gravity.END else Gravity.START
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }

        val bubble = TextView(this).apply {
            text = body
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@ConversationActivity, if (outgoing) R.color.messages_on_primary else R.color.messages_text_primary))
            setPadding(
                dp(17),
                dp(11),
                dp(17),
                dp(11)
            )
            maxWidth = dp(320)
            includeFontPadding = true

            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()

                if (outgoing) {
                    setColor(ContextCompat.getColor(this@ConversationActivity, R.color.messages_primary))
                } else {
                    setColor(ContextCompat.getColor(this@ConversationActivity, R.color.messages_surface_variant))
                }
            }
        }

        fun updateBubbleSelection() {
            val selected = selectedMessageIds.contains(messageId)

            bubble.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()

                if (selected) {
                    setColor(
                        ContextCompat.getColor(
                            this@ConversationActivity,
                            R.color.messages_selection_background
                        )
                    )
                } else if (outgoing) {
                    setColor(
                        ContextCompat.getColor(
                            this@ConversationActivity,
                            R.color.messages_primary
                        )
                    )
                } else {
                    setColor(
                        ContextCompat.getColor(
                            this@ConversationActivity,
                            R.color.messages_surface_variant
                        )
                    )
                }
            }

            bubble.setTextColor(
                ContextCompat.getColor(
                    this@ConversationActivity,
                    if (selected) {
                        R.color.messages_text_primary
                    } else if (outgoing) {
                        R.color.messages_on_primary
                    } else {
                        R.color.messages_text_primary
                    }
                )
            )
        }

        bubble.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true
                selectedMessageIds.clear()
                selectedMessageBodies.clear()
            }

            selectedMessageIds.add(messageId)
            selectedMessageBodies[messageId] = body
            updateSelectionToolbar()
            updateBubbleSelection()
            true
        }

        bubble.setOnClickListener {
            if (!selectionMode) return@setOnClickListener

            if (selectedMessageIds.contains(messageId)) {
                selectedMessageIds.remove(messageId)
                selectedMessageBodies.remove(messageId)
            } else {
                selectedMessageIds.add(messageId)
                selectedMessageBodies[messageId] = body
            }

            if (selectedMessageIds.isEmpty()) {
                exitSelectionMode()
            } else {
                updateSelectionToolbar()
                updateBubbleSelection()
            }
        }

        if (selectionMode && selectedMessageIds.contains(messageId)) {
            updateBubbleSelection()
        }

        val time = TextView(this).apply {
            text = formatMessageTime(date)
            textSize = 10.5f
            setTextColor(ContextCompat.getColor(this@ConversationActivity, R.color.messages_text_hint))
            setPadding(
                dp(6),
                dp(3),
                dp(6),
                0
            )
        }

        container.addView(
            bubble,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (outgoing) {
                    marginStart = dp(45)
                } else {
                    marginEnd = dp(45)
                }
            }
        )

        container.addView(
            time,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        messageList.addView(
            container,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun enterMessageSelection(messageId: Long, body: String) {
        selectionMode = true
        selectedMessageIds.clear()
        selectedMessageBodies.clear()
        selectedMessageIds.add(messageId)
        selectedMessageBodies[messageId] = body
        updateSelectionToolbar()
        loadMessages()
    }

    private fun updateSelectionToolbar() {
        findViewById<View>(R.id.messageSelectionToolbar).visibility = View.VISIBLE

        findViewById<View>(R.id.backButton).visibility = View.GONE
        findViewById<View>(R.id.conversationAvatar).visibility = View.GONE
        findViewById<View>(R.id.conversationTitle).visibility = View.GONE
        findViewById<View>(R.id.conversationAddress).visibility = View.GONE

        findViewById<TextView>(R.id.messageSelectionCount).text =
            "${selectedMessageIds.size} selected"

        findViewById<TextView>(R.id.messageSelectionClose).setOnClickListener {
            exitSelectionMode()
        }

        findViewById<TextView>(R.id.messageSelectionCopy).setOnClickListener {
            copySelectedMessages()
        }

        findViewById<TextView>(R.id.messageSelectionDelete).setOnClickListener {
            deleteSelectedMessages()
        }
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedMessageIds.clear()
        selectedMessageBodies.clear()

        findViewById<View>(R.id.messageSelectionToolbar).visibility = View.GONE

        findViewById<View>(R.id.backButton).visibility = View.VISIBLE
        findViewById<View>(R.id.conversationAvatar).visibility = View.VISIBLE
        findViewById<View>(R.id.conversationTitle).visibility = View.VISIBLE
        findViewById<View>(R.id.conversationAddress).visibility = View.VISIBLE

        loadMessages()
    }

    private fun copySelectedMessages() {
        if (selectedMessageBodies.isEmpty()) return

        val text = selectedMessageBodies.values.joinToString("\n\n")

        val clipboard =
            getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("Messages", text)
        )

        Toast.makeText(
            this,
            "Message copied",
            Toast.LENGTH_SHORT
        ).show()

        exitSelectionMode()
    }

    private fun deleteSelectedMessages() {
        if (selectedMessageIds.isEmpty()) return

        val dialogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(16), dp(12))

            background = GradientDrawable().apply {
                setColor(
                    ContextCompat.getColor(
                        this@ConversationActivity,
                        R.color.messages_surface
                    )
                )
                cornerRadius = dp(24).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = "Delete messages?"
            textSize = 20f
            setTextColor(
                ContextCompat.getColor(
                    this@ConversationActivity,
                    R.color.messages_text_primary
                )
            )
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val message = TextView(this).apply {
            text = "${selectedMessageIds.size} message(s) will be deleted."
            textSize = 15f
            setTextColor(
                ContextCompat.getColor(
                    this@ConversationActivity,
                    R.color.messages_text_secondary
                )
            )
            setPadding(0, dp(10), 0, dp(14))
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        lateinit var dialog: android.app.Dialog

        val cancel = TextView(this).apply {
            text = "CANCEL"
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(
                ContextCompat.getColor(
                    this@ConversationActivity,
                    R.color.messages_text_secondary
                )
            )
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            minHeight = dp(48)
            setOnClickListener {
                dialog.dismiss()
            }
        }

        val delete = TextView(this).apply {
            text = "DELETE"
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(
                ContextCompat.getColor(
                    this@ConversationActivity,
                    R.color.messages_primary
                )
            )
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            minHeight = dp(48)
            setOnClickListener {
                val ids = selectedMessageIds.toList()

                Thread {
                    for (id in ids) {
                        contentResolver.delete(
                            Telephony.Sms.CONTENT_URI,
                            "${Telephony.Sms._ID}=?",
                            arrayOf(id.toString())
                        )
                    }

                    runOnUiThread {
                        dialog.dismiss()
                        exitSelectionMode()
                    }
                }.start()
            }
        }

        buttons.addView(cancel)
        buttons.addView(delete)

        dialogContainer.addView(title)
        dialogContainer.addView(message)
        dialogContainer.addView(buttons)

        dialog = android.app.Dialog(this)
        dialog.setContentView(dialogContainer)
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        )

        dialog.setOnShowListener {
            dialog.window?.setLayout(
                dp(340),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()

        dialog.window?.setLayout(
            dp(340),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun sendMessage() {
        val body = messageInput.text.toString().trim()

        if (body.isEmpty()) return

        if (address.isBlank() || address == "Unknown") {
            Toast.makeText(
                this,
                "Invalid phone number",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                SEND_SMS_REQUEST
            )
            return
        }

        try {
            val smsManager = SmsManager.getDefault()

            smsManager.sendTextMessage(
                address,
                null,
                body,
                null,
                null
            )

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.THREAD_ID, threadId)
            }

            try {
                contentResolver.insert(
                    Telephony.Sms.Sent.CONTENT_URI,
                    values
                )
            } catch (_: Exception) {
                // Sending already succeeded.
            }

            messageInput.text.clear()
            loadMessages()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Failed to send message",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun markConversationRead() {
        if (threadId.isBlank()) return

        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }

            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.READ}=0",
                arrayOf(threadId)
            )
        } catch (_: Exception) {
            // Ignore provider errors.
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == SEND_SMS_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            sendMessage()
        }
    }

    private fun formatMessageTime(timestamp: Long): String {
        return SimpleDateFormat(
            "h:mm a",
            Locale.getDefault()
        ).format(Date(timestamp))
    }


private fun getContactName(phoneNumber: String): String? {
    return try {
        val lookupUri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(phoneNumber)
            .build()

        contentResolver.query(
            lookupUri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        ContactsContract.PhoneLookup.DISPLAY_NAME
                    )
                )
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}   
 private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }
}
