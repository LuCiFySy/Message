package com.saurabh.messages
import android.text.TextWatcher
import android.text.Editable
import android.graphics.Typeface
import android.widget.ImageView
import android.widget.FrameLayout
import android.view.ViewGroup.LayoutParams
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.graphics.Color
import android.provider.Telephony
import android.provider.ContactsContract
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.telephony.SmsManager
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
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
        const val ACTION_MESSAGES_CHANGED =
            "com.saurabh.messages.ACTION_MESSAGES_CHANGED"
    }

    private var threadId: String = ""
    private var address: String = ""

    private lateinit var messageList: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var attachmentPreview: LinearLayout
    private lateinit var attachmentPreviewScroll: View

    private val selectedAttachments = mutableListOf<Uri>()

    private val attachmentPicker =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isEmpty()) return@registerForActivityResult

            selectedAttachments.clear()
            selectedAttachments.addAll(uris)

            showAttachmentPreviews()
        }

    private var selectionMode = false
    private val selectedMessageIds = LinkedHashSet<Long>()
    private val selectedMessageBodies = LinkedHashMap<Long, String>()

    private val messagesChangedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (intent.action != ACTION_MESSAGES_CHANGED) return

            val changedThreadId = intent.getLongExtra("thread_id", -1L).toString()
            if (changedThreadId == threadId) {
                loadMessages()
            }
        }
    }

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
        attachmentPreview = findViewById(R.id.attachmentPreview)
        attachmentPreviewScroll = findViewById(R.id.attachmentPreviewScroll)

        val characterCounter = findViewById<TextView>(R.id.characterCounter)

        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                val text = s?.toString() ?: ""

                if (text.isEmpty()) {
                    characterCounter.visibility = View.GONE
                    return
                }

                val isGsm7 = text.all { char ->
                    char.code in 0x20..0x7E ||
                        char == '\n' ||
                        char == '\r' ||
                        char == '\t' ||
                        char in "\u00A3\u20AC\u00A5\u00E8\u00E9\u00F9\u00EC\u00F2\u00C7\u00D8\u00F8\u00C5\u00E6\u0394\u03A6\u0393\u039B\u03A9\u03A0\u03A8\u03A3\u0398\u039E\u00C6\u00DF\u00C9\u00A4"
                }

                val limit = if (isGsm7) 160 else 70
                val length = text.length

                characterCounter.text = "$length / $limit"
                characterCounter.visibility = View.VISIBLE
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        findViewById<View>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.attachmentButton).setOnClickListener {
            attachmentPicker.launch(
                arrayOf("*/*")
            )
        }

        findViewById<View>(R.id.sendButton).setOnClickListener {
            sendMessage()
        }

        markConversationRead()
        loadMessages()
    }

    override fun onStart() {
        super.onStart()

        getSharedPreferences("messages_settings", MODE_PRIVATE)
            .edit()
            .putString("active_thread_id", threadId)
            .apply()

        androidx.core.content.ContextCompat.registerReceiver(
            this,
            messagesChangedReceiver,
            android.content.IntentFilter(ACTION_MESSAGES_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        getSharedPreferences("messages_settings", MODE_PRIVATE)
            .edit()
            .remove("active_thread_id")
            .apply()

        unregisterReceiver(messagesChangedReceiver)
        super.onStop()
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
            Telephony.Sms.TYPE,
            Telephony.TextBasedSmsColumns.STATUS
        )

        try {
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.THREAD_ID}=?",
                arrayOf(threadId),
                "${Telephony.Sms.DATE} ASC, ${Telephony.Sms._ID} ASC"
            )?.use { cursor ->

                val addressIndex =
                    cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex =
                    cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex =
                    cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex =
                    cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val statusIndex =
                    cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.STATUS)

                while (cursor.moveToNext()) {
                    val messageId = cursor.getLong(
                        cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                    )
                    val body = cursor.getString(bodyIndex) ?: ""
                    val date = cursor.getLong(dateIndex)
                    val type = cursor.getInt(typeIndex)
                    val status = cursor.getInt(statusIndex)
                    val messageAddress =
                        cursor.getString(addressIndex) ?: address

                    addMessageBubble(
                        messageId = messageId,
                        body = body,
                        date = date,
                        type = type,
                        status = status,
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
        status: Int,
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
            val statusText = when {
                !outgoing -> ""
                status == Telephony.TextBasedSmsColumns.STATUS_COMPLETE -> "✓✓ "
                status == Telephony.TextBasedSmsColumns.STATUS_FAILED -> "! "
                else -> "✓ "
            }

            text = statusText + formatMessageTime(date)
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

    private fun showAttachmentPreviews() {
        attachmentPreview.removeAllViews()

        if (selectedAttachments.isEmpty()) {
            attachmentPreviewScroll.visibility = View.GONE
            return
        }

        attachmentPreviewScroll.visibility = View.VISIBLE

        selectedAttachments.forEachIndexed { index, uri ->

            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(76),
                    dp(76)
                ).apply {
                    marginEnd = dp(8)
                }
            }

            val image = RoundedImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dp(76),
                    dp(76)
                )

                scaleType = ImageView.ScaleType.CENTER_CROP
                background = null
                clipToOutline = false
            }

            val mimeType = contentResolver.getType(uri) ?: ""

            if (mimeType.startsWith("image/")) {
                try {
                    image.setImageURI(uri)
                } catch (_: Exception) {
                    image.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } else {
                image.setImageResource(android.R.drawable.ic_menu_save)
                image.scaleType = ImageView.ScaleType.CENTER
                image.setPadding(
                    dp(18),
                    dp(18),
                    dp(18),
                    dp(18)
                )
            }

            frame.addView(image)

            val remove = TextView(this).apply {
                text = "×"
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)

                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.BLACK)
                }

                layoutParams = FrameLayout.LayoutParams(
                    dp(24),
                    dp(24),
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = dp(2)
                    rightMargin = dp(2)
                }

                elevation = dp(2).toFloat()

                setOnClickListener {
                    selectedAttachments.removeAt(index)
                    showAttachmentPreviews()
                }
            }

            frame.addView(remove)
            attachmentPreview.addView(frame)
        }
    }

    private fun sendMessage() {
        val body = messageInput.text.toString().trim()

        if (body.isEmpty() && selectedAttachments.isEmpty()) {
            return
        }

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

        if (selectedAttachments.isNotEmpty()) {
            try {
                val settings = com.klinker.android.send_message.Settings()
                settings.setUseSystemSending(true)

                val transaction = com.klinker.android.send_message.Transaction(
                    this,
                    settings
                )

                val message = com.klinker.android.send_message.Message(
                    body,
                    address
                )

                for (uri in selectedAttachments) {
                    val mimeType =
                        contentResolver.getType(uri) ?: "application/octet-stream"

                    val bytes = contentResolver.openInputStream(uri)?.use {
                        it.readBytes()
                    } ?: continue

                    val fileName = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?: "attachment"

                    message.addMedia(
                        bytes,
                        mimeType,
                        fileName
                    )
                }

                transaction.setExplicitBroadcastForSentMms(
                    Intent(this, MmsSentReceiver::class.java)
                )

                transaction.sendNewMessage(message)

                messageInput.text.clear()
                selectedAttachments.clear()
                showAttachmentPreviews()
                loadMessages()
                return

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Failed to send MMS: ${e.message ?: "Unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }

        try {
            val smsManager = SmsManager.getDefault()

            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                put(Telephony.Sms.THREAD_ID, threadId)
                put(Telephony.TextBasedSmsColumns.STATUS, Telephony.TextBasedSmsColumns.STATUS_PENDING)
            }

            val messageUri = contentResolver.insert(
                Telephony.Sms.Sent.CONTENT_URI,
                values
            )

            val messageId = messageUri?.lastPathSegment?.toLongOrNull() ?: -1L
            val requestCode = (System.currentTimeMillis() and 0x7fffffff).toInt()

            val sentIntent = Intent(this, SmsStatusReceiver::class.java).apply {
                action = SmsStatusReceiver.ACTION_SMS_SENT
                putExtra("message_id", messageId)
            }

            val deliveryIntent = Intent(this, SmsStatusReceiver::class.java).apply {
                action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                putExtra("message_id", messageId)
            }

            val sentPendingIntent = android.app.PendingIntent.getBroadcast(
                this,
                requestCode,
                sentIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val deliveryPendingIntent = if (
                getSharedPreferences("messages_settings", MODE_PRIVATE)
                    .getBoolean("delivery_reports", true)
            ) {
                android.app.PendingIntent.getBroadcast(
                    this,
                    requestCode + 1,
                    deliveryIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }

            smsManager.sendTextMessage(
                address,
                null,
                body,
                sentPendingIntent,
                deliveryPendingIntent
            )

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
