package com.saurabh.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.text.Editable
import android.text.TextWatcher
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.provider.ContactsContract
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.widget.PopupWindow
import android.view.ViewGroup
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SMS_PERMISSION_REQUEST = 100
        private const val CONTACTS_PERMISSION_REQUEST = 101
        private const val SMS_ROLE_REQUEST = 101
        private const val NOTIFICATION_PERMISSION_REQUEST = 102
        private const val PICK_CONTACT_REQUEST = 103
    }

    private lateinit var conversationList: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var searchInput: EditText

    private var selectionMode = false
    private val selectedThreadIds = LinkedHashSet<String>()

    private val contactNameCache = HashMap<String, String?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mainRoot = findViewById<View>(R.id.mainRoot)

        ViewCompat.setOnApplyWindowInsetsListener(mainRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        ViewCompat.requestApplyInsets(mainRoot)

        conversationList = findViewById(R.id.conversationList)
        emptyText = findViewById(R.id.emptyText)

        searchInput = findViewById(R.id.searchInput)

        findViewById<View>(R.id.moreButton).setOnClickListener {
            startActivity(
                Intent(this, SettingsActivity::class.java)
            )
        }

        findViewById<TextView>(R.id.startChatButton).setOnClickListener {
            val intent = Intent(
                Intent.ACTION_PICK,
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            )
            startActivityForResult(intent, PICK_CONTACT_REQUEST)
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                loadConversations(s?.toString()?.trim() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        findViewById<TextView>(R.id.unreadChip).setOnClickListener {
            showUnreadOnly = !showUnreadOnly

            findViewById<TextView>(R.id.unreadChip).setBackgroundResource(
                if (showUnreadOnly) R.drawable.bg_chip_selected
                else R.drawable.bg_chip
            )

            findViewById<TextView>(R.id.unreadChip).setTextColor(
                ContextCompat.getColor(
                    this,
                    if (showUnreadOnly) R.color.messages_text_primary
                    else R.color.messages_text_secondary
                )
            )

            loadConversations(searchInput.text.toString().trim())
        }

        findViewById<TextView>(R.id.archivedChip).setOnClickListener {
            showArchivedOnly = !showArchivedOnly

            findViewById<TextView>(R.id.archivedChip).setBackgroundResource(
                if (showArchivedOnly) R.drawable.bg_chip_selected
                else R.drawable.bg_chip
            )

            findViewById<TextView>(R.id.archivedChip).setTextColor(
                ContextCompat.getColor(
                    this,
                    if (showArchivedOnly) R.color.messages_text_primary
                    else R.color.messages_text_secondary
                )
            )

            if (showArchivedOnly) {
                showUnreadOnly = false

                findViewById<TextView>(R.id.unreadChip).setBackgroundResource(
                    R.drawable.bg_chip
                )

                findViewById<TextView>(R.id.unreadChip).setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.messages_text_secondary
                    )
                )
            }

            loadConversations(searchInput.text.toString().trim())
        }

        checkSmsAccess()
    }

    private var firstResume = true
    private var showUnreadOnly = false
    private var showArchivedOnly = false

    private val archivedThreads: MutableSet<String>
        get() = getSharedPreferences("messages_settings", MODE_PRIVATE)
            .getStringSet("archived_threads", emptySet())
            ?.toMutableSet()
            ?: mutableSetOf()


    override fun onResume() {
        super.onResume()

        if (firstResume) {
            firstResume = false
            return
        }

        if (::conversationList.isInitialized) {
            checkSmsAccess()
        }
    }

    private fun checkSmsAccess() {
        if (isDefaultSmsApp()) {
            if (hasSmsPermission()) {
                if (hasContactsPermission()) {
                    if (hasNotificationPermission()) {
                        loadConversations()
                    } else {
                        requestNotificationPermission()
                    }
                } else {
                    requestContactsPermission()
                }
            } else {
                requestSmsPermission()
            }
        } else {
            conversationList.removeAllViews()
            emptyText.visibility = View.VISIBLE
            emptyText.text = "Set Messages as the default SMS app to continue"
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            Telephony.Sms.getDefaultSmsPackage(this) == packageName
        }
    }

    private fun requestDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)

            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_SMS)
            ) {
                val intent = roleManager.createRequestRoleIntent(
                    RoleManager.ROLE_SMS
                )
                startActivityForResult(intent, SMS_ROLE_REQUEST)
            }
        } else {
            @Suppress("DEPRECATION")
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(
                    Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                    packageName
                )
            }
            startActivityForResult(intent, SMS_ROLE_REQUEST)
        }
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS
            ),
            SMS_PERMISSION_REQUEST
        )
    }

    private fun requestContactsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_CONTACTS),
            CONTACTS_PERMISSION_REQUEST
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SMS_ROLE_REQUEST) {
            checkSmsAccess()
            return
        }

        if (
            requestCode == PICK_CONTACT_REQUEST &&
            resultCode == RESULT_OK
        ) {
            val contactUri = data?.data ?: return

            contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )

                    if (numberIndex >= 0) {
                        val address = cursor.getString(numberIndex)?.trim()

                        if (!address.isNullOrBlank()) {
                            val threadId =
                                Telephony.Threads.getOrCreateThreadId(this, address)

                            val intent = Intent(
                                this,
                                ConversationActivity::class.java
                            ).apply {
                                putExtra("thread_id", threadId.toString())
                                putExtra("address", address)
                            }

                            startActivity(intent)
                        }
                    }
                }
            }
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

        if (requestCode == SMS_PERMISSION_REQUEST) {
            var smsGranted = false

            for (i in permissions.indices) {
                if (permissions[i] == Manifest.permission.READ_SMS &&
                    grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED
                ) {
                    smsGranted = true
                    break
                }
            }

            if (smsGranted) {
                loadConversations()
            } else {
                conversationList.removeAllViews()
                emptyText.visibility = View.VISIBLE
                emptyText.text = "SMS permission is required to show messages"
            }
        }

        if (requestCode == CONTACTS_PERMISSION_REQUEST) {
            if (hasSmsPermission()) {
                if (hasNotificationPermission()) {
                    loadConversations()
                } else {
                    requestNotificationPermission()
                }
            }
        }

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (hasSmsPermission() && hasContactsPermission()) {
                loadConversations()
            }
        }
    }

    private data class Conversation(
        val threadId: String,
        val address: String,
        val displayName: String,
        val body: String,
        val date: Long,
        val unreadCount: Int
    )

    private fun getContactName(phoneNumber: String): String? {
        if (!hasContactsPermission()) return null

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

    private fun loadConversations(searchQuery: String = "") {
        val queryText = searchQuery.trim()

        Thread {
            val conversations = LinkedHashMap<String, Conversation>()

            val projection = arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ
            )

            try {
                contentResolver.query(
                    Uri.parse("content://sms"),
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC"
                )?.use { cursor ->

                    val threadIdIndex =
                        cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val addressIndex =
                        cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIndex =
                        cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIndex =
                        cursor.getColumnIndex(Telephony.Sms.DATE)
                    val readIndex =
                        cursor.getColumnIndex(Telephony.Sms.READ)

                    while (cursor.moveToNext()) {
                        val threadId =
                            cursor.getString(threadIdIndex) ?: continue
                        val address =
                            cursor.getString(addressIndex) ?: "Unknown"
                        val body =
                            cursor.getString(bodyIndex) ?: ""
                        val date =
                            cursor.getLong(dateIndex)
                        val read =
                            cursor.getInt(readIndex)

                        val displayName = if (contactNameCache.containsKey(address)) {
                            contactNameCache[address] ?: address
                        } else {
                            val name = getContactName(address)
                            contactNameCache[address] = name
                            name ?: address
                        }

                        if (queryText.isNotEmpty()) {
                            val query = queryText.lowercase(Locale.getDefault())

                            if (!address.lowercase(Locale.getDefault()).contains(query) &&
                                !displayName.lowercase(Locale.getDefault()).contains(query) &&
                                !body.lowercase(Locale.getDefault()).contains(query)
                            ) {
                                continue
                            }
                        }

                        val existing = conversations[threadId]

                        if (existing == null) {
                            conversations[threadId] = Conversation(
                                threadId = threadId,
                                address = address,
                                displayName = displayName,
                                body = body,
                                date = date,
                                unreadCount = if (read == 0) 1 else 0
                            )
                        } else if (read == 0) {
                            conversations[threadId] = existing.copy(
                                unreadCount = existing.unreadCount + 1
                            )
                        }
                    }
                }

                val archived = archivedThreads

                val archiveIterator = conversations.entries.iterator()
                while (archiveIterator.hasNext()) {
                    val entry = archiveIterator.next()
                    val isArchived = archived.contains(entry.key)

                    if (showArchivedOnly != isArchived) {
                        archiveIterator.remove()
                    }
                }

                if (showUnreadOnly) {
                    val iterator = conversations.entries.iterator()
                    while (iterator.hasNext()) {
                        if (iterator.next().value.unreadCount == 0) {
                            iterator.remove()
                        }
                    }
                }

                runOnUiThread {
                    conversationList.removeAllViews()

                    if (conversations.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        emptyText.text =
                            if (queryText.isEmpty()) {
                                "No conversations yet"
                            } else {
                                "No matching conversations"
                            }
                    } else {
                        emptyText.visibility = View.GONE

                        conversations.values.forEach { conversation ->
                            addConversationView(conversation)
                        }
                    }
                }

            } catch (e: SecurityException) {
                runOnUiThread {
                    conversationList.removeAllViews()
                    emptyText.visibility = View.VISIBLE
                    emptyText.text =
                        "SMS permission is required to show messages"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    conversationList.removeAllViews()
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "Unable to load messages"
                }
            }
        }.start()
    }

    private fun enterSelectionMode(threadId: String) {
        selectionMode = true
        selectedThreadIds.clear()
        selectedThreadIds.add(threadId)

        findViewById<View>(R.id.mainTopBar).visibility = View.GONE
        findViewById<View>(R.id.greetingText).visibility = View.GONE
        findViewById<View>(R.id.searchContainer).visibility = View.GONE
        findViewById<View>(R.id.filterContainer).visibility = View.GONE
        findViewById<View>(R.id.selectionToolbar).visibility = View.VISIBLE

        updateSelectionToolbar()
        loadConversations(searchInput.text.toString().trim())
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedThreadIds.clear()

        findViewById<View>(R.id.selectionToolbar).visibility = View.GONE
        findViewById<View>(R.id.mainTopBar).visibility = View.VISIBLE
        findViewById<View>(R.id.greetingText).visibility = View.VISIBLE
        findViewById<View>(R.id.searchContainer).visibility = View.VISIBLE
        findViewById<View>(R.id.filterContainer).visibility = View.VISIBLE

        loadConversations(searchInput.text.toString().trim())
    }

    private fun updateSelectionToolbar() {
        val count = selectedThreadIds.size

        findViewById<TextView>(R.id.selectionCount).text =
            "$count selected"

        findViewById<TextView>(R.id.selectionClose).setOnClickListener {
            exitSelectionMode()
        }

        findViewById<TextView>(R.id.selectionArchive).apply {
            text = if (showArchivedOnly) "Unarchive" else "Archive"

            setOnClickListener {
                archiveSelectedConversations()
            }
        }

        findViewById<TextView>(R.id.selectionDelete).setOnClickListener {
            deleteSelectedConversations()
        }
    }


    private fun showConversationMenu(
        anchor: View,
        conversation: Conversation
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        R.color.messages_surface
                    )
                )
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(8).toFloat()
        }

        val popup = PopupWindow(
            container,
            dp(220),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(8).toFloat()
            isOutsideTouchable = true
        }

        fun addAction(
            title: String,
            action: () -> Unit
        ) {
            val item = TextView(this).apply {
                text = title
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        R.color.messages_text_primary
                    )
                )
                setPadding(dp(16), 0, dp(16), 0)
                isClickable = true
                isFocusable = true
                minHeight = dp(52)

                setOnClickListener {
                    popup.dismiss()
                    action()
                }
            }

            container.addView(
                item,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(52)
                )
            )
        }

        addAction("Delete") {
            deleteConversation(conversation.threadId)
        }

        addAction("Select") {
            enterSelectionMode(conversation.threadId)
        }

        addAction(
            if (showArchivedOnly) "Unarchive" else "Archive"
        ) {
            toggleArchive(conversation.threadId)
        }

        addAction("Call") {
            val intent = Intent(
                Intent.ACTION_DIAL,
                Uri.parse("tel:${Uri.encode(conversation.address)}")
            )
            startActivity(intent)
        }

        popup.showAsDropDown(
            anchor,
            dp(12),
            -anchor.height + dp(8)
        )
    }

    private fun deleteConversation(threadId: String) {
        val dialogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(16), dp(12))

            background = GradientDrawable().apply {
                setColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        R.color.messages_surface
                    )
                )
                cornerRadius = dp(24).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = "Delete conversation?"
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.messages_text_primary
                )
            )
        }

        val message = TextView(this).apply {
            text = "All messages in this conversation will be deleted."
            textSize = 15f
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.messages_text_secondary
                )
            )
            setPadding(0, dp(10), 0, dp(16))
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val cancel = TextView(this).apply {
            text = "CANCEL"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.messages_text_secondary
                )
            )
            setPadding(dp(16), 0, dp(16), 0)
            minHeight = dp(48)
            isClickable = true
        }

        val delete = TextView(this).apply {
            text = "DELETE"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.messages_primary
                )
            )
            setPadding(dp(16), 0, dp(16), 0)
            minHeight = dp(48)
            isClickable = true
        }

        buttons.addView(cancel)
        buttons.addView(delete)

        dialogContainer.addView(title)
        dialogContainer.addView(message)
        dialogContainer.addView(
            buttons,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogContainer)
            .create()

        cancel.setOnClickListener {
            dialog.dismiss()
        }

        delete.setOnClickListener {
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
                    loadConversations(searchInput.text.toString().trim())
                }
            }.start()
        }

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        dialog.show()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        dialog.window?.setLayout(
            dp(340),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun archiveSelectedConversations() {
        if (selectedThreadIds.isEmpty()) return

        val prefs = getSharedPreferences("messages_settings", MODE_PRIVATE)
        val archived = prefs.getStringSet(
            "archived_threads",
            emptySet()
        )?.toMutableSet() ?: mutableSetOf()

        if (showArchivedOnly) {
            archived.removeAll(selectedThreadIds)
        } else {
            archived.addAll(selectedThreadIds)
        }

        prefs.edit()
            .putStringSet("archived_threads", archived)
            .apply()

        exitSelectionMode()
    }

    private fun deleteSelectedConversations() {
        if (selectedThreadIds.isEmpty()) return

        val count = selectedThreadIds.size

        val dialogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        R.color.messages_surface
                    )
                )
                cornerRadius = dp(24).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = "Delete $count conversations?"
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.messages_text_primary
                )
            )
        }

        val message = TextView(this).apply {
            text = "All messages in the selected conversations will be deleted."
            textSize = 15f
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.messages_text_secondary
                )
            )
            setPadding(0, dp(10), 0, dp(16))
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancel = TextView(this).apply {
            text = "CANCEL"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.messages_text_secondary
                )
            )
            setPadding(dp(16), 0, dp(16), 0)
            minHeight = dp(48)
        }

        val delete = TextView(this).apply {
            text = "DELETE"
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    R.color.messages_primary
                )
            )
            setPadding(dp(16), 0, dp(16), 0)
            minHeight = dp(48)
        }

        buttons.addView(cancel)
        buttons.addView(delete)

        dialogContainer.addView(title)
        dialogContainer.addView(message)
        dialogContainer.addView(
            buttons,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogContainer)
            .create()

        cancel.setOnClickListener {
            dialog.dismiss()
        }

        delete.setOnClickListener {
            dialog.dismiss()

            val threads = selectedThreadIds.toList()

            Thread {
                threads.forEach { threadId ->
                    try {
                        contentResolver.delete(
                            Telephony.Sms.CONTENT_URI,
                            "${Telephony.Sms.THREAD_ID}=?",
                            arrayOf(threadId)
                        )
                    } catch (_: Exception) {
                    }
                }

                runOnUiThread {
                    exitSelectionMode()
                }
            }.start()
        }

        dialog.show()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        dialog.window?.setLayout(
            dp(340),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun toggleArchive(threadId: String) {
        val prefs = getSharedPreferences("messages_settings", MODE_PRIVATE)
        val archived = prefs.getStringSet(
            "archived_threads",
            emptySet()
        )?.toMutableSet() ?: mutableSetOf()

        val wasArchived = archived.contains(threadId)

        if (wasArchived) {
            archived.remove(threadId)
        } else {
            archived.add(threadId)
        }

        prefs.edit()
            .putStringSet("archived_threads", archived)
            .apply()

        loadConversations(searchInput.text.toString().trim())
    }

    private fun setupConversationSwipe(
        row: View,
        conversation: Conversation
    ) {
        val prefs = getSharedPreferences("messages_settings", MODE_PRIVATE)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val swipeDistance = dp(120)

        var downX = 0f
        var downY = 0f
        var swiping = false
        var cancelledClick = false

        row.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    swiping = false
                    cancelledClick = false

                    view.parent?.requestDisallowInterceptTouchEvent(false)

                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY

                    if (!swiping &&
                        (kotlin.math.abs(dx) > touchSlop ||
                         kotlin.math.abs(dy) > touchSlop)
                    ) {
                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            swiping = true
                            cancelledClick = true
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        } else {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            return@setOnTouchListener false
                        }
                    }

                    if (swiping) {
                        val limitedDx = dx.coerceIn(
                            -view.width.toFloat(),
                            view.width.toFloat()
                        )
                        view.translationX = limitedDx
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (swiping) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)

                        val dx = event.rawX - downX

                        view.animate()
                            .translationX(0f)
                            .setDuration(180)
                            .start()

                        if (kotlin.math.abs(dx) >= swipeDistance) {
                            val action = if (dx < 0) {
                                prefs.getString(
                                    SettingsActivity.PREF_SWIPE_LEFT,
                                    SettingsActivity.SWIPE_ACTION_ARCHIVE
                                ) ?: SettingsActivity.SWIPE_ACTION_ARCHIVE
                            } else {
                                prefs.getString(
                                    SettingsActivity.PREF_SWIPE_RIGHT,
                                    SettingsActivity.SWIPE_ACTION_DELETE
                                ) ?: SettingsActivity.SWIPE_ACTION_DELETE
                            }

                            if (action == SettingsActivity.SWIPE_ACTION_DELETE) {
                                deleteConversation(conversation.threadId)
                            } else {
                                toggleArchive(conversation.threadId)
                            }
                        }

                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    } else {
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        false
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (swiping) {
                        view.animate()
                            .translationX(0f)
                            .setDuration(180)
                            .start()
                    }

                    swiping = false
                    cancelledClick = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    false
                }

                else -> false
            }
        }
    }

    private fun addConversationView(conversation: Conversation) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76)
            setPadding(
                dp(20),
                dp(10),
                dp(16),
                dp(10)
            )
            isClickable = true
            isFocusable = true
            background =
                if (selectionMode && selectedThreadIds.contains(conversation.threadId)) {
                    getDrawable(R.drawable.bg_conversation_selected)
                } else {
                    null
                }
        }

        val avatar = TextView(this).apply {
            val initial = conversation.displayName
                .filter { it.isLetterOrDigit() }
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "?"

            text = initial
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.messages_primary))
            setBackgroundResource(R.drawable.bg_avatar)
        }

        row.addView(
            avatar,
            LinearLayout.LayoutParams(
                dp(48),
                dp(48)
            ).apply {
                marginEnd = dp(14)
            }
        )

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams =
                LinearLayout.LayoutParams(0, -2, 1f)
        }

        val name = TextView(this).apply {
            text = conversation.displayName
            textSize = 17f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.messages_text_primary))
            maxLines = 1
        }

        val preview = TextView(this).apply {
            text = conversation.body.replace("\n", " ")
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.messages_text_secondary))
            maxLines = 1
        }

        textContainer.addView(name)
        textContainer.addView(preview)
        row.addView(textContainer)

        val rightContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        val time = TextView(this).apply {
            text = formatDate(conversation.date)
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.messages_text_secondary))
        }

        rightContainer.addView(time)

        if (conversation.unreadCount > 0) {
            val unread = TextView(this).apply {
                text = conversation.unreadCount.toString()
                textSize = 12f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        R.color.messages_on_primary
                    )
                )
                setBackgroundResource(R.drawable.bg_unread)
            }

            rightContainer.addView(
                unread,
                LinearLayout.LayoutParams(
                    dp(28),
                    dp(28)
                ).apply {
                    topMargin = dp(6)
                }
            )
        }

        row.addView(rightContainer)

        row.setOnLongClickListener {
            showConversationMenu(row, conversation)
            true
        }

        setupConversationSwipe(row, conversation)

        row.setOnClickListener {
            if (selectionMode) {
                if (selectedThreadIds.contains(conversation.threadId)) {
                    selectedThreadIds.remove(conversation.threadId)
                } else {
                    selectedThreadIds.add(conversation.threadId)
                }

                if (selectedThreadIds.isEmpty()) {
                    exitSelectionMode()
                } else {
                    updateSelectionToolbar()
                    loadConversations(searchInput.text.toString().trim())
                }
            } else {
                val intent = Intent(this, ConversationActivity::class.java).apply {
                    putExtra("thread_id", conversation.threadId)
                    putExtra("address", conversation.address)
                }
                startActivity(intent)
            }
        }

        conversationList.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(2)
            }
        )
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L

        return if (now - timestamp < day) {
            SimpleDateFormat(
                "h:mm a",
                Locale.getDefault()
            ).format(date)
        } else {
            SimpleDateFormat(
                "dd/MM/yy",
                Locale.getDefault()
            ).format(date)
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }
}
