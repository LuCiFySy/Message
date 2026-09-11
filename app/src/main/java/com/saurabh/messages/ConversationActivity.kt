package com.saurabh.messages
import android.text.TextWatcher
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ClickableSpan
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
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
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.PopupWindow
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.provider.Settings
import android.widget.ScrollView
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

    private var selectedSubscriptionId =
        SubscriptionManager.INVALID_SUBSCRIPTION_ID

    private var availableSubscriptions: List<SubscriptionInfo> = emptyList()

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
    private lateinit var popupWindow: PopupWindow
    private val selectedMessageIds = LinkedHashSet<Long>()
    private val selectedMessageBodies = LinkedHashMap<Long, String>()

    private val messagesChangedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (intent.action != ACTION_MESSAGES_CHANGED) return

            val changedThreadId = intent.getLongExtra("thread_id", -1L).toString()
            if (changedThreadId == threadId) {
                markConversationRead()
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

            val composerParams =
                composerContainer.layoutParams as LinearLayout.LayoutParams

            composerParams.bottomMargin = ime.bottom
            composerContainer.layoutParams = composerParams

            if (ime.bottom > 0) {
                messageList.post {
                    val scrollView =
                        findViewById<android.widget.ScrollView>(R.id.messageScroll)
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }

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

        findViewById<View>(R.id.conversationMenuButton).setOnClickListener {
            showConversationMenu()
        }

        findViewById<View>(R.id.attachmentButton).setOnClickListener {
            attachmentPicker.launch(
                arrayOf("*/*")
            )
        }

        val sendButton = findViewById<View>(R.id.sendButton)

        sendButton.setOnClickListener {
            sendMessage()
        }

        sendButton.setOnLongClickListener {
            if (availableSubscriptions.size > 1) {
                showSimPicker(sendButton)
                true
            } else {
                false
            }
        }

        refreshSimSelector()

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

    private fun showConversationMenu() {
        val density = resources.displayMetrics.density

        fun dp(value: Int): Int =
            (value * density + 0.5f).toInt()

        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
            )
            background = GradientDrawable().apply {
                setColor(
                    ContextCompat.getColor(
                        this@ConversationActivity,
                        R.color.messages_surface
                    )
                )
                cornerRadius = dp(20).toFloat()
            }
        }

        fun addItem(
            title: String,
            onClick: () -> Unit
        ) {
            val item = TextView(this).apply {
                text = title
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(
                    ContextCompat.getColor(
                        this@ConversationActivity,
                        R.color.messages_text_primary
                    )
                )
                setPadding(
                    dp(16),
                    0,
                    dp(20),
                    0
                )
                isClickable = true
                isFocusable = true

                layoutParams = LinearLayout.LayoutParams(
                    dp(220),
                    dp(52)
                )

                setOnClickListener {
                    onClick()
                }
            }

            menu.addView(item)
        }

        var popup: PopupWindow? = null

        val hasContact = getContactName(address) != null

        addItem(
            if (hasContact) "View contact" else "Add to contacts"
        ) {
            popup?.dismiss()

            if (hasContact) {
                openContact(address)
            } else {
                addContact(address)
            }
        }

        val muted = NotificationHelper.isMuted(this, address)

        addItem(
            if (muted) "Unmute notifications"
            else "Mute notifications"
        ) {
            if (muted) {
                NotificationHelper.unmute(this, address)
                popup?.dismiss()

                Toast.makeText(
                    this,
                    "Notifications unmuted",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                popup?.dismiss()
                showMuteOptions()
            }
        }

        addItem("Scheduled messages") {
        popup?.dismiss()
        showScheduledMessagesDialog()
    }
        val blocked = BlockHelper.isBlocked(this, address)

        addItem(
            if (blocked) "Unblock" else "Block"
        ) {
            popup?.dismiss()

            if (blocked) {
                BlockHelper.unblock(this, address)

                Toast.makeText(
                    this,
                    "Number unblocked",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                showBlockOptions()
            }
        }

        popup = PopupWindow(
            menu,
            dp(236),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = dp(8).toFloat()
            setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(
                        ContextCompat.getColor(
                            this@ConversationActivity,
                            R.color.messages_surface
                        )
                    )
                    cornerRadius = dp(20).toFloat()
                }
            )
            isOutsideTouchable = true
        }

        val anchor = findViewById<View>(R.id.conversationMenuButton)

        popup.showAsDropDown(
            anchor,
            -dp(188),
            -dp(4)
        )
    }

    private fun showBlockOptions() {
        val density = resources.displayMetrics.density

        fun dp(value: Int): Int =
            (value * density + 0.5f).toInt()

        val contactName = getContactName(address) ?: address

        val dialog = android.app.Dialog(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(24),
                dp(22),
                dp(16),
                dp(12)
            )

            background = GradientDrawable().apply {
                setColor(
                    ContextCompat.getColor(
                        this@ConversationActivity,
                        R.color.messages_surface
                    )
                )
                cornerRadius = dp(28).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = "Block $contactName?"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(
                ContextCompat.getColor(
                    this@ConversationActivity,
                    R.color.messages_text_primary
                )
            )
            setPadding(
                dp(4),
                0,
                dp(4),
                dp(8)
            )
        }

        val message = TextView(this).apply {
            text = "Choose what you want to do with the existing chat."
            textSize = 16f
            setTextColor(
                ContextCompat.getColor(
                    this@ConversationActivity,
                    R.color.messages_text_secondary
                )
            )
            setPadding(
                dp(4),
                0,
                dp(4),
                dp(12)
            )
        }

        container.addView(title)
        container.addView(message)

        fun addOption(
            text: String,
            onClick: () -> Unit
        ) {
            val option = TextView(this).apply {
                this.text = text
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(
                    ContextCompat.getColor(
                        this@ConversationActivity,
                        R.color.messages_text_primary
                    )
                )
                setPadding(
                    dp(16),
                    0,
                    dp(16),
                    0
                )
                isClickable = true
                isFocusable = true

                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(16).toFloat()
                }

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52)
                ).apply {
                    setMargins(
                        0,
                        dp(2),
                        0,
                        dp(2)
                    )
                }

                setOnClickListener {
                    onClick()
                }
            }

            container.addView(option)
        }

        addOption("Block & delete chat") {
            BlockHelper.block(
                this,
                address
            )

            dialog.dismiss()

            Thread {
                try {
                    contentResolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "${Telephony.Sms.THREAD_ID}=?",
                        arrayOf(threadId)
                    )
                } catch (_: Exception) {
                }

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Number blocked and chat deleted",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }
            }.start()
        }

        addOption("Block only") {
            BlockHelper.block(
                this,
                address
            )

            dialog.dismiss()

            Toast.makeText(
                this,
                "Number blocked",
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }

        addOption("Cancel") {
            dialog.dismiss()
        }

        dialog.setContentView(container)
        dialog.setCanceledOnTouchOutside(true)

        dialog.window?.apply {
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(
                    Color.TRANSPARENT
                )
            )

            addFlags(
                android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            attributes = attributes.apply {
                dimAmount = 0.60f
            }
        }

        dialog.show()

        dialog.window?.setLayout(
            dp(340),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun openContact(phoneNumber: String) {
        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )

            val projection = arrayOf(
                ContactsContract.PhoneLookup.CONTACT_ID
            )

            contentResolver.query(
                lookupUri,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getLong(0)

                    val contactUri = Uri.withAppendedPath(
                        ContactsContract.Contacts.CONTENT_URI,
                        contactId.toString()
                    )

                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            contactUri
                        )
                    )
                    return
                }
            }

            addContact(phoneNumber)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Unable to open contact",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun addContact(phoneNumber: String) {
        try {
            val intent = Intent(
                Intent.ACTION_INSERT,
                ContactsContract.Contacts.CONTENT_URI
            ).apply {
                putExtra(
                    ContactsContract.Intents.Insert.PHONE,
                    phoneNumber
                )
            }

            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Unable to open contacts",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showMuteOptions() {
        val density = resources.displayMetrics.density

        fun dp(value: Int): Int =
            (value * density + 0.5f).toInt()

        val surfaceColor = ContextCompat.getColor(
            this,
            R.color.messages_surface
        )

        val primaryColor = ContextCompat.getColor(
            this,
            R.color.messages_text_primary
        )

        val dialog = android.app.Dialog(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(12),
                dp(20),
                dp(12),
                dp(12)
            )
            background = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadius = dp(28).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = "Mute notifications"
            textSize = 22f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(primaryColor)
            setPadding(
                dp(16),
                0,
                dp(16),
                dp(12)
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            )
        }

        container.addView(title)

        fun addOption(
            text: String,
            onClick: () -> Unit
        ) {
            val option = TextView(this).apply {
                this.text = text
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(primaryColor)
                setPadding(
                    dp(16),
                    0,
                    dp(16),
                    0
                )
                isClickable = true
                isFocusable = true

                background = GradientDrawable().apply {
                    setColor(android.graphics.Color.TRANSPARENT)
                    cornerRadius = dp(16).toFloat()
                }

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52)
                ).apply {
                    setMargins(
                        0,
                        dp(2),
                        0,
                        dp(2)
                    )
                }

                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }
            }

            container.addView(option)
        }

        addOption("1 hour") {
            NotificationHelper.setMutedUntil(
                this,
                address,
                System.currentTimeMillis() +
                    60L * 60L * 1000L
            )

            Toast.makeText(
                this,
                "Notifications muted",
                Toast.LENGTH_SHORT
            ).show()
        }

        addOption("8 hours") {
            NotificationHelper.setMutedUntil(
                this,
                address,
                System.currentTimeMillis() +
                    8L * 60L * 60L * 1000L
            )

            Toast.makeText(
                this,
                "Notifications muted",
                Toast.LENGTH_SHORT
            ).show()
        }

        addOption("Always") {
            NotificationHelper.setMutedUntil(
                this,
                address,
                Long.MAX_VALUE
            )

            Toast.makeText(
                this,
                "Notifications muted",
                Toast.LENGTH_SHORT
            ).show()
        }

        addOption("Custom time") {
            showCustomMuteTime()
        }

        dialog.setContentView(container)
        dialog.setCanceledOnTouchOutside(true)

        dialog.window?.apply {
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT
                )
            )

            addFlags(
                android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            attributes = attributes.apply {
                dimAmount = 0.60f
            }
        }

        dialog.show()

        dialog.window?.setLayout(
            minOf(
                dp(360),
                resources.displayMetrics.widthPixels - dp(32)
            ),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showCustomMuteTime() {
        val density = resources.displayMetrics.density

        fun dp(value: Int): Int =
            (value * density + 0.5f).toInt()

        val surfaceColor = ContextCompat.getColor(
            this,
            R.color.messages_surface
        )

        val primaryColor = ContextCompat.getColor(
            this,
            R.color.messages_text_primary
        )

        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.HOUR_OF_DAY, 1)
        }

        fun createDialog(
            titleText: String
        ): Pair<android.app.Dialog, LinearLayout> {
            val dialog = android.app.Dialog(this)

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    dp(16),
                    dp(20),
                    dp(16),
                    dp(12)
                )
                background = GradientDrawable().apply {
                    setColor(surfaceColor)
                    cornerRadius = dp(28).toFloat()
                }
            }

            val title = TextView(this).apply {
                text = titleText
                textSize = 22f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(primaryColor)
                setPadding(
                    dp(8),
                    0,
                    dp(8),
                    dp(12)
                )

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
                )
            }

            container.addView(title)

            dialog.setContentView(container)
            dialog.setCanceledOnTouchOutside(true)

            dialog.window?.apply {
                setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.TRANSPARENT
                    )
                )

                addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
                )

                attributes = attributes.apply {
                    dimAmount = 0.60f
                }
            }

            return Pair(dialog, container)
        }

        fun addButtons(
            container: LinearLayout,
            cancelAction: () -> Unit,
            nextAction: () -> Unit
        ) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(56)
                )
            }

            fun addButton(
                text: String,
                action: () -> Unit
            ) {
                val button = TextView(this).apply {
                    this.text = text
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(primaryColor)
                    isClickable = true
                    isFocusable = true

                    setPadding(
                        dp(16),
                        0,
                        dp(16),
                        0
                    )

                    layoutParams = LinearLayout.LayoutParams(
                        dp(88),
                        dp(48)
                    )

                    setOnClickListener {
                        action()
                    }
                }

                row.addView(button)
            }

            addButton("Cancel", cancelAction)
            addButton("Next", nextAction)

            container.addView(row)
        }

        val dateParts = createDialog("Choose date")
        val dateDialog = dateParts.first
        val dateContainer = dateParts.second

        val datePicker = android.widget.DatePicker(this).apply {
            init(
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH),
                null
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dateContainer.addView(datePicker)

        addButtons(
            dateContainer,
            cancelAction = {
                dateDialog.dismiss()
            },
            nextAction = {
                calendar.set(
                    java.util.Calendar.YEAR,
                    datePicker.year
                )
                calendar.set(
                    java.util.Calendar.MONTH,
                    datePicker.month
                )
                calendar.set(
                    java.util.Calendar.DAY_OF_MONTH,
                    datePicker.dayOfMonth
                )

                dateDialog.dismiss()

                val timeParts = createDialog("Choose time")
                val timeDialog = timeParts.first
                val timeContainer = timeParts.second

                val timePicker = android.widget.TimePicker(this).apply {
                    setIs24HourView(false)

                    hour = calendar.get(
                        java.util.Calendar.HOUR_OF_DAY
                    )

                    minute = calendar.get(
                        java.util.Calendar.MINUTE
                    )

                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                timeContainer.addView(timePicker)

                addButtons(
                    timeContainer,
                    cancelAction = {
                        timeDialog.dismiss()
                    },
                    nextAction = {
                        calendar.set(
                            java.util.Calendar.HOUR_OF_DAY,
                            timePicker.hour
                        )
                        calendar.set(
                            java.util.Calendar.MINUTE,
                            timePicker.minute
                        )
                        calendar.set(
                            java.util.Calendar.SECOND,
                            0
                        )
                        calendar.set(
                            java.util.Calendar.MILLISECOND,
                            0
                        )

                        if (calendar.timeInMillis <=
                            System.currentTimeMillis()
                        ) {
                            Toast.makeText(
                                this,
                                "Choose a future time",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@addButtons
                        }

                        NotificationHelper.setMutedUntil(
                            this,
                            address,
                            calendar.timeInMillis
                        )

                        Toast.makeText(
                            this,
                            "Notifications muted",
                            Toast.LENGTH_SHORT
                        ).show()

                        timeDialog.dismiss()
                    }
                )

                timeDialog.show()

                timeDialog.window?.setLayout(
                    minOf(
                        dp(360),
                        resources.displayMetrics.widthPixels - dp(32)
                    ),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        )

        dateDialog.show()

        dateDialog.window?.setLayout(
            minOf(
                dp(360),
                resources.displayMetrics.widthPixels - dp(32)
            ),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
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

    private fun resendSms(
        messageId: Long,
        body: String,
        recipient: String
    ) {
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
            val smsManager =
                if (
                    selectedSubscriptionId !=
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ) {
                    SmsManager.getSmsManagerForSubscriptionId(
                        selectedSubscriptionId
                    )
                } else {
                    SmsManager.getDefault()
                }

            val values = ContentValues().apply {
                put(
                    Telephony.Sms.TYPE,
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX
                )
                put(
                    Telephony.TextBasedSmsColumns.STATUS,
                    Telephony.TextBasedSmsColumns.STATUS_PENDING
                )
                put(
                    Telephony.Sms.DATE,
                    System.currentTimeMillis()
                )
            }

            val updated = contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms._ID}=?",
                arrayOf(messageId.toString())
            )

            if (updated <= 0) {
                Toast.makeText(
                    this,
                    "Unable to resend message",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            val requestCode =
                (System.currentTimeMillis() and 0x7fffffff).toInt()

            val sentIntent =
                Intent(this, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_SENT
                    putExtra("message_id", messageId)
                }

            val deliveryIntent =
                Intent(this, SmsStatusReceiver::class.java).apply {
                    action = SmsStatusReceiver.ACTION_SMS_DELIVERED
                    putExtra("message_id", messageId)
                }

            val sentPendingIntent =
                android.app.PendingIntent.getBroadcast(
                    this,
                    requestCode,
                    sentIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )

            val deliveryPendingIntent =
                if (
                    getSharedPreferences(
                        "messages_settings",
                        MODE_PRIVATE
                    ).getBoolean("delivery_reports", true)
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
                recipient,
                null,
                body,
                sentPendingIntent,
                deliveryPendingIntent
            )

            loadMessages()

        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Failed to send message",
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
            type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
            type == Telephony.Sms.MESSAGE_TYPE_FAILED

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (outgoing) Gravity.END else Gravity.START
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }

        val bubble = TextView(this).apply {
            text = buildInteractiveMessageText(body, messageId)
            textSize = if (isEmojiOnlyMessage(body)) 30f else 16f
            movementMethod = LinkMovementMethod.getInstance()
            linksClickable = true
            setTextIsSelectable(false)
            setTextColor(
                ContextCompat.getColor(
                    this@ConversationActivity,
                    if (outgoing) {
                        android.R.color.black
                    } else {
                        R.color.messages_text_primary
                    }
                )
            )

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
                    setColor(ContextCompat.getColor(this@ConversationActivity, R.color.messages_outgoing_bubble))
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
                            R.color.messages_outgoing_bubble
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
                    if (outgoing) {
                        android.R.color.black
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
            if (selectionMode) {
                toggleMessageSelection(messageId, body)
            }
        }

        if (selectionMode && selectedMessageIds.contains(messageId)) {
            updateBubbleSelection()
        }

        val time = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(6),
                dp(3),
                dp(6),
                0
            )

            val statusText = when {
                !outgoing -> ""
                status == Telephony.TextBasedSmsColumns.STATUS_COMPLETE -> "✓✓"
                status == Telephony.TextBasedSmsColumns.STATUS_FAILED -> ""
                else -> "✓"
            }

            val statusView = TextView(this@ConversationActivity).apply {
                text = statusText
                textSize = if (
                    status == Telephony.TextBasedSmsColumns.STATUS_FAILED
                ) {
                    16f
                } else {
                    10.5f
                }
                gravity = Gravity.CENTER
                includeFontPadding = false

                if (
                    status == Telephony.TextBasedSmsColumns.STATUS_FAILED
                ) {
                    setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_failed,
                        0,
                        0,
                        0
                    )
                    setTextColor(
                        android.graphics.Color.parseColor("#EF6C6C")
                    )
                } else {
                    setTextColor(
                        ContextCompat.getColor(
                            this@ConversationActivity,
                            R.color.messages_text_hint
                        )
                    )
                }
            }

            addView(
                statusView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(24)
                )
            )

            if (
                outgoing &&
                status == Telephony.TextBasedSmsColumns.STATUS_FAILED
            ) {
                val resendView = android.widget.ImageButton(this@ConversationActivity).apply {
                    setImageResource(R.drawable.ic_resend)
                    background = null
                    scaleType = android.widget.ImageView.ScaleType.CENTER
                    setPadding(0, 0, 0, 0)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Resend"
                    setOnClickListener {
                        resendSms(
                            messageId,
                            body,
                            messageAddress
                        )
                    }
                }

                addView(
                    resendView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(24)
                    )
                )
            }

            val timeView = TextView(this@ConversationActivity).apply {
                text = formatMessageTime(date)
                textSize = 10.5f
                setTextColor(
                    ContextCompat.getColor(
                        this@ConversationActivity,
                        R.color.messages_text_hint
                    )
                )
            }

            addView(
                timeView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
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

    private fun isEmojiOnlyMessage(value: String): Boolean {
        val text = value.trim()
        if (text.isEmpty()) return false

        var hasEmoji = false
        var index = 0

        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)

            when {
                Character.isWhitespace(codePoint) -> Unit

                codePoint in 0x1F000..0x1FAFF ||
                codePoint in 0x2600..0x27BF ||
                codePoint in 0x2300..0x23FF -> {
                    hasEmoji = true
                }

                codePoint == 0xFE0F ||
                codePoint == 0x200D ||
                codePoint in 0x1F3FB..0x1F3FF ||
                codePoint == 0x20E3 -> Unit

                else -> return false
            }

            index += charCount
        }

        return hasEmoji
    }

    private fun buildInteractiveMessageText(
        body: String,
        messageId: Long
    ): Spannable {
        val text = SpannableString(body)

        // Detect web URLs and phone numbers first.
        Linkify.addLinks(
            text,
            Linkify.WEB_URLS or Linkify.PHONE_NUMBERS
        )

        // Replace Android's direct URL/phone actions with our own
        // contextual action menus.
        val urlSpans = text.getSpans(
            0,
            text.length,
            android.text.style.URLSpan::class.java
        )

        for (urlSpan in urlSpans) {
            val start = text.getSpanStart(urlSpan)
            val end = text.getSpanEnd(urlSpan)

            if (start < 0 || end <= start) {
                continue
            }

            val url = urlSpan.url
            val displayValue = body.substring(start, end)

            text.removeSpan(urlSpan)

            text.setSpan(
                object : ClickableSpan() {
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        ds.isUnderlineText = true
                    }

                    override fun onClick(widget: View) {
                        if (selectionMode) {
                            toggleMessageSelection(messageId, body)
                            return
                        }

                        if (url.startsWith("tel:", ignoreCase = true)) {
                            showPhoneActionMenu(
                                widget,
                                url.removePrefix("tel:"),
                                displayValue
                            )
                        } else {
                            showUrlActionMenu(
                                widget,
                                url,
                                displayValue
                            )
                        }
                    }
                },
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Detect OTP/passcode-like numbers only when the surrounding
        // message gives a strong indication that the number is an OTP.
        val otpKeywords = Regex(
            """(?i)\b(?:otp|one[- ]time password|verification code|passcode|security code|authentication code)\b"""
        )
        val numberRegex = Regex("""\b\d{4,8}\b""")

        for (match in numberRegex.findAll(body)) {
            val start = match.range.first
            val end = match.range.last + 1

            val contextStart = maxOf(0, start - 50)
            val contextEnd = minOf(body.length, end + 50)
            val context = body.substring(contextStart, contextEnd)

            if (!otpKeywords.containsMatchIn(context)) {
                continue
            }

            // Don't replace an existing URL/phone span.
            val existingSpans = text.getSpans(
                start,
                end,
                Any::class.java
            )

            if (existingSpans.isNotEmpty()) {
                continue
            }

            val otp = match.value

            text.setSpan(
                object : ClickableSpan() {
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        ds.isUnderlineText = true
                    }

                    override fun onClick(widget: View) {
                        if (selectionMode) {
                            toggleMessageSelection(messageId, body)
                            return
                        }

                        showOtpActionMenu(widget, otp)
                    }
                },
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return text
    }

    private fun toggleMessageSelection(messageId: Long, body: String) {
        if (!selectionMode) {
            selectionMode = true
            selectedMessageIds.clear()
            selectedMessageBodies.clear()
        }

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
            loadMessages()
        }
    }

    private fun showPhoneActionMenu(
        anchor: View,
        phoneNumber: String,
        displayValue: String
    ) {
        showMessageActionPopup(
            anchor,
            listOf(
                "Call" to {
                    val intent = Intent(
                        Intent.ACTION_DIAL,
                        Uri.parse("tel:${Uri.encode(phoneNumber)}")
                    )
                    startActivity(intent)
                },
                "Copy" to {
                    copyMessageActionValue("Phone number", displayValue)
                }
            )
        )
    }

    private fun showUrlActionMenu(
        anchor: View,
        url: String,
        displayValue: String
    ) {
        showMessageActionPopup(
            anchor,
            listOf(
                "Open" to {
                    val openUrl =
                        if (
                            url.startsWith("http://", ignoreCase = true) ||
                            url.startsWith("https://", ignoreCase = true)
                        ) {
                            url
                        } else {
                            "https://$url"
                        }

                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(openUrl)
                        )
                    )
                },
                "Copy" to {
                    copyMessageActionValue("URL", displayValue)
                }
            )
        )
    }

    private fun showOtpActionMenu(
        anchor: View,
        otp: String
    ) {
        showMessageActionPopup(
            anchor,
            listOf(
                "Copy" to {
                    copyMessageActionValue("OTP", otp)
                }
            )
        )
    }

    private fun copyMessageActionValue(
        label: String,
        value: String
    ) {
        val clipboard =
            getSystemService(
                android.content.Context.CLIPBOARD_SERVICE
            ) as android.content.ClipboardManager

        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                label,
                value
            )
        )

        Toast.makeText(
            this@ConversationActivity,
            "$label copied",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showMessageActionPopup(
        anchor: View,
        actions: List<Pair<String, () -> Unit>>
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                dp(6),
                dp(6),
                dp(6),
                dp(6)
            )

            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(
                    ContextCompat.getColor(
                        this@ConversationActivity,
                        R.color.messages_surface
                    )
                )
            }

            elevation = dp(8).toFloat()
        }

        actions.forEach { (label, action) ->
            val button = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(
                    ContextCompat.getColor(
                        this@ConversationActivity,
                        R.color.messages_text_primary
                    )
                )
                gravity = Gravity.CENTER
                setPadding(
                    dp(14),
                    dp(10),
                    dp(14),
                    dp(10)
                )
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    action()
                    popupWindow.dismiss()
                }
            }

            content.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        popupWindow = PopupWindow(
            content,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = dp(8).toFloat()
            setBackgroundDrawable(
                GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(
                        ContextCompat.getColor(
                            this@ConversationActivity,
                            R.color.messages_surface
                        )
                    )
                }
            )
        }

        popupWindow.showAsDropDown(
            anchor,
            0,
            -anchor.height - dp(8)
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
                        NotificationHelper.cancelMessageNotification(
                            this@ConversationActivity,
                            id
                        )
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

                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
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
                image.scaleType = android.widget.ImageView.ScaleType.CENTER
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

    private fun refreshSimSelector() {
        val container = findViewById<View>(R.id.simSelectorContainer)
        val label = findViewById<TextView>(R.id.simSelectorLabel)

        try {
            val subscriptionManager =
                getSystemService(SubscriptionManager::class.java)

            val subscriptions =
                subscriptionManager?.activeSubscriptionInfoList
                    ?.sortedBy { info ->
                        if (info.simSlotIndex >= 0) {
                            info.simSlotIndex
                        } else {
                            Int.MAX_VALUE
                        }
                    }
                    ?: emptyList()

            availableSubscriptions = subscriptions

            if (subscriptions.size <= 1) {
                container.visibility = View.VISIBLE
                label.visibility = View.GONE
                selectedSubscriptionId =
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                return
            }

            container.visibility = View.VISIBLE
            label.visibility = View.VISIBLE

            val defaultSubscriptionId =
                SubscriptionManager.getDefaultSmsSubscriptionId()

            if (
                selectedSubscriptionId ==
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID ||
                subscriptions.none {
                    it.subscriptionId == selectedSubscriptionId
                }
            ) {
                selectedSubscriptionId =
                    if (
                        subscriptions.any {
                            it.subscriptionId == defaultSubscriptionId
                        }
                    ) {
                        defaultSubscriptionId
                    } else {
                        subscriptions.first().subscriptionId
                    }
            }

            label.text = getSimLabel(selectedSubscriptionId)

        } catch (_: Exception) {
            availableSubscriptions = emptyList()
            selectedSubscriptionId =
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            container.visibility = View.VISIBLE
            label.visibility = View.GONE
        }
    }

    private fun getSimLabel(subscriptionId: Int): String {
        val subscription = availableSubscriptions.firstOrNull {
            it.subscriptionId == subscriptionId
        }

        val slot = subscription?.simSlotIndex ?: -1

        return if (slot >= 0) {
            "SIM ${slot + 1}"
        } else {
            "SIM"
        }
    }

    private fun showSimPicker(anchor: View) {
        if (availableSubscriptions.size <= 1) {
            return
        }

        val popupContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(4),
                dp(4),
                dp(4),
                dp(4)
            )

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(
                    ContextCompat.getColor(
                        this@ConversationActivity,
                        R.color.messages_surface_variant
                    )
                )
            }
        }

        val popup = PopupWindow(
            popupContent,
            dp(150),
            WindowManager.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = dp(6).toFloat()
            isOutsideTouchable = true
        }

        for (subscription in availableSubscriptions) {
            val selected =
                subscription.subscriptionId == selectedSubscriptionId

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(10),
                    dp(8),
                    dp(12),
                    dp(8)
                )
                isClickable = true
                isFocusable = true

                val radio = ImageView(this@ConversationActivity).apply {
                    setImageResource(
                        if (selected) {
                            R.drawable.ic_sim_radio_selected
                        } else {
                            R.drawable.ic_sim_radio_unselected
                        }
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        dp(20),
                        dp(20)
                    )
                }

                val text = TextView(this@ConversationActivity).apply {
                    this.text = getSimLabel(subscription.subscriptionId)
                    textSize = 14f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(
                        ContextCompat.getColor(
                            this@ConversationActivity,
                            R.color.messages_text_primary
                        )
                    )
                    setPadding(dp(10), 0, 0, 0)
                }

                addView(radio)
                addView(
                    text,
                    LinearLayout.LayoutParams(
                        0,
                        dp(40),
                        1f
                    )
                )

                setOnClickListener {
                    try {
                        selectedSubscriptionId =
                            subscription.subscriptionId

                        refreshSimSelector()

                        popup.dismiss()
                    } catch (_: Exception) {
                        popup.dismiss()
                    }
                }
            }

            popupContent.addView(item)
        }

        popup.showAsDropDown(
            anchor,
            -dp(98),
            -dp(122)
        )
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
            val smsManager =
                if (
                    selectedSubscriptionId !=
                        SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ) {
                    SmsManager
                        .getSmsManagerForSubscriptionId(
                            selectedSubscriptionId
                        )
                } else {
                    SmsManager.getDefault()
                }

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

    private fun showScheduledMessagesDialog(
        editing: ScheduledMessage? = null
    ) {
        val dialog = android.app.Dialog(this)

        val dialogBackground =
            ContextCompat.getColor(
                this,
                R.color.messages_surface
            )

        val primaryText =
            ContextCompat.getColor(
                this,
                R.color.messages_text_primary
            )

        val secondaryText =
            ContextCompat.getColor(
                this,
                R.color.messages_text_secondary
            )

        val hintText =
            ContextCompat.getColor(
                this,
                R.color.messages_text_hint
            )

        val fieldBackground =
            ContextCompat.getColor(
                this,
                R.color.messages_surface_variant
            )

        val primaryColor =
            ContextCompat.getColor(
                this,
                R.color.messages_primary
            )

        val onPrimaryColor =
            ContextCompat.getColor(
                this,
                R.color.messages_on_primary
            )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(20),
                dp(18),
                dp(20),
                dp(12)
            )
            background = GradientDrawable().apply {
                setColor(dialogBackground)
                cornerRadius = dp(20).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = if (editing == null) {
                "Scheduled messages"
            } else {
                "Edit scheduled message"
            }
            textSize = 20f
            setTextColor(primaryText)
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val bodyInput = EditText(this).apply {
            hint = "Message"
            textSize = 16f
            gravity = Gravity.TOP
            minLines = 3
            setTextColor(primaryText)
            setHintTextColor(hintText)
            setPadding(
                dp(12),
                dp(10),
                dp(12),
                dp(10)
            )
        }

        if (editing != null) {
            bodyInput.setText(editing.body)
            bodyInput.setSelection(bodyInput.text.length)
        }

        val bodyParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(16)
        }
        root.addView(bodyInput, bodyParams)


        val selectedTime = java.util.Calendar.getInstance().apply {
            if (editing != null) {
                timeInMillis = editing.scheduledAt
            } else {
                add(java.util.Calendar.HOUR_OF_DAY, 1)
                set(
                    java.util.Calendar.SECOND,
                    0
                )
                set(
                    java.util.Calendar.MILLISECOND,
                    0
                )
            }
        }

        val dateButton = TextView(this).apply {
            textSize = 16f
            setTextColor(primaryText)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )
            background = GradientDrawable().apply {
                setColor(fieldBackground)
                cornerRadius = dp(12).toFloat()
            }
        }

        val timeButton = TextView(this).apply {
            textSize = 16f
            setTextColor(primaryText)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(12),
                dp(12),
                dp(12),
                dp(12)
            )
            background = GradientDrawable().apply {
                setColor(fieldBackground)
                cornerRadius = dp(12).toFloat()
            }
        }

        fun updateDateText() {
            dateButton.text = String.format(
                Locale.getDefault(),
                "%04d-%02d-%02d",
                selectedTime.get(java.util.Calendar.YEAR),
                selectedTime.get(java.util.Calendar.MONTH) + 1,
                selectedTime.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }

        fun updateTimeText() {
            val hour = selectedTime.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = selectedTime.get(java.util.Calendar.MINUTE)

            timeButton.text = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                hour,
                minute
            )
        }

        updateDateText()
        updateTimeText()

        dateButton.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    selectedTime.set(
                        java.util.Calendar.YEAR,
                        year
                    )
                    selectedTime.set(
                        java.util.Calendar.MONTH,
                        month
                    )
                    selectedTime.set(
                        java.util.Calendar.DAY_OF_MONTH,
                        day
                    )
                    updateDateText()
                },
                selectedTime.get(java.util.Calendar.YEAR),
                selectedTime.get(java.util.Calendar.MONTH),
                selectedTime.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        timeButton.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    selectedTime.set(
                        java.util.Calendar.HOUR_OF_DAY,
                        hour
                    )
                    selectedTime.set(
                        java.util.Calendar.MINUTE,
                        minute
                    )
                    selectedTime.set(
                        java.util.Calendar.SECOND,
                        0
                    )
                    selectedTime.set(
                        java.util.Calendar.MILLISECOND,
                        0
                    )
                    updateTimeText()
                },
                selectedTime.get(java.util.Calendar.HOUR_OF_DAY),
                selectedTime.get(java.util.Calendar.MINUTE),
                true
            ).show()
        }

        val dateTimeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        dateTimeRow.addView(
            dateButton,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ).apply {
                topMargin = dp(12)
                marginEnd = dp(6)
            }
        )

        dateTimeRow.addView(
            timeButton,
            LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            ).apply {
                topMargin = dp(12)
                marginStart = dp(6)
            }
        )

        root.addView(dateTimeRow)

        val actionButton = TextView(this).apply {
            text = if (editing == null) {
                "Schedule"
            } else {
                "Save changes"
            }
            textSize = 16f
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(onPrimaryColor)
            setPadding(
                dp(16),
                dp(12),
                dp(16),
                dp(12)
            )
            background = GradientDrawable().apply {
                setColor(primaryColor)
                cornerRadius = dp(14).toFloat()
            }
        }

        actionButton.setOnClickListener {
            val body = bodyInput.text.toString().trim()

            if (body.isEmpty()) {
                Toast.makeText(
                    this,
                    "Enter a message",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (address.isBlank()) {
                Toast.makeText(
                    this,
                    "No recipient",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val scheduledAt = selectedTime.timeInMillis

            if (scheduledAt <= System.currentTimeMillis()) {
                Toast.makeText(
                    this,
                    "Choose a future date and time",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (!ScheduledMessageReceiver.canScheduleExactAlarms(this)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (_: Exception) {
                    }
                }

                Toast.makeText(
                    this,
                    "Allow exact alarms, then tap Schedule again",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (editing == null) {
                val id =
                    System.currentTimeMillis() * 1024L +
                        java.security.SecureRandom().nextInt(1024)

                val message = ScheduledMessage(
                    id = id,
                    threadId = threadId.toString(),
                    address = address,
                    body = body,
                    scheduledAt = scheduledAt
                )

                ScheduledMessageStore.add(
                    this,
                    message
                )
                ScheduledMessageReceiver.scheduleAlarm(
                    this,
                    message
                )

                Toast.makeText(
                    this,
                    "Message scheduled",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                ScheduledMessageReceiver.cancelAlarm(
                    this,
                    editing.id
                )

                val message = editing.copy(
                    body = body,
                    scheduledAt = scheduledAt,
                    missed = false
                )

                ScheduledMessageStore.update(
                    this,
                    message
                )
                ScheduledMessageReceiver.scheduleAlarm(
                    this,
                    message
                )

                Toast.makeText(
                    this,
                    "Schedule updated",
                    Toast.LENGTH_SHORT
                ).show()
            }

            dialog.dismiss()
        }

        val actionParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        ).apply {
            topMargin = dp(14)
        }
        root.addView(actionButton, actionParams)

        val listTitle = TextView(this).apply {
            text = "Scheduled"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(50, 50, 50))
        }

        root.addView(
            listTitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18)
            }
        )

        val scrollView = ScrollView(this)

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scheduledMessages =
            ScheduledMessageStore.getForThread(
                this,
                threadId.toString(),
                address
            )

        if (scheduledMessages.isEmpty()) {
            list.addView(
                TextView(this).apply {
                    text = "No scheduled messages"
                    textSize = 14f
                    setTextColor(secondaryText)
                    setPadding(
                        0,
                        dp(12),
                        0,
                        dp(12)
                    )
                }
            )
        } else {
            val formatter =
                java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.getDefault()
                )

            scheduledMessages.forEach { message ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        dp(12),
                        dp(10),
                        dp(12),
                        dp(10)
                    )
                    background = GradientDrawable().apply {
                        setColor(fieldBackground)
                        cornerRadius = dp(12).toFloat()
                    }
                }

                val messageText = TextView(this).apply {
                    text = if (message.missed) {
                        "Missed • ${message.body}"
                    } else {
                        message.body
                    }
                    textSize = 15f
                    setTextColor(primaryText)
                }

                item.addView(messageText)

                val dateText = TextView(this).apply {
                    text = formatter.format(
                        java.util.Date(message.scheduledAt)
                    )
                    textSize = 13f
                    setTextColor(secondaryText)
                    setPadding(
                        0,
                        dp(5),
                        0,
                        0
                    )
                }

                item.addView(dateText)

                val buttons = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                }

                val editButton = TextView(this).apply {
                    text = "Edit"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(primaryColor)
                    setPadding(
                        dp(12),
                        dp(8),
                        dp(12),
                        dp(8)
                    )
                    setOnClickListener {
                        dialog.setOnDismissListener {
                            showScheduledMessagesDialog(message)
                        }
                        dialog.dismiss()
                    }
                }

                val cancelButton = TextView(this).apply {
                    text = "Cancel"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.rgb(190, 40, 40))
                    setPadding(
                        dp(12),
                        dp(8),
                        dp(12),
                        dp(8)
                    )
                    setOnClickListener {
                        ScheduledMessageReceiver.cancelAlarm(
                            this@ConversationActivity,
                            message.id
                        )
                        ScheduledMessageStore.remove(
                            this@ConversationActivity,
                            message.id
                        )
                        ScheduledMessageNotification.dismiss(
                            this@ConversationActivity,
                            message.id
                        )
                        dialog.dismiss()
                        showScheduledMessagesDialog()
                    }
                }

                buttons.addView(editButton)
                buttons.addView(cancelButton)
                item.addView(buttons)

                list.addView(
                    item,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(8)
                    }
                )
            }
        }

        scrollView.addView(list)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(4)
            }
        )

        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)

        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                (resources.displayMetrics.heightPixels * 0.82f).toInt()
            )
        }

        dialog.show()

        dialog.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (resources.displayMetrics.heightPixels * 0.82f).toInt()
        )
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
