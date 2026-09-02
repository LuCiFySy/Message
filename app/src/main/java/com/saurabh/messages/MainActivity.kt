package com.saurabh.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.view.Gravity
import android.view.View
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
        private const val SMS_ROLE_REQUEST = 101
    }

    private lateinit var conversationList: LinearLayout
    private lateinit var emptyText: TextView

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

        checkSmsAccess()
    }

    override fun onResume() {
        super.onResume()

        if (::conversationList.isInitialized) {
            checkSmsAccess()
        }
    }

    private fun checkSmsAccess() {
        if (isDefaultSmsApp()) {
            if (hasSmsPermission()) {
                loadConversations()
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

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_SMS),
            SMS_PERMISSION_REQUEST
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
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                loadConversations()
            } else {
                conversationList.removeAllViews()
                emptyText.visibility = View.VISIBLE
                emptyText.text = "SMS permission is required to show messages"
            }
        }
    }

    private data class Conversation(
        val threadId: String,
        val address: String,
        val body: String,
        val date: Long,
        val unreadCount: Int
    )

    private fun loadConversations() {
        conversationList.removeAllViews()

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

                    val existing = conversations[threadId]

                    if (existing == null) {
                        conversations[threadId] = Conversation(
                            threadId = threadId,
                            address = address,
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
        } catch (e: SecurityException) {
            emptyText.visibility = View.VISIBLE
            emptyText.text =
                "SMS permission is required to show messages"
            return
        } catch (e: Exception) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "Unable to load messages"
            return
        }

        if (conversations.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "No conversations yet"
        } else {
            emptyText.visibility = View.GONE

            conversations.values.forEach { conversation ->
                addConversationView(conversation)
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
                getDrawable(android.R.drawable.list_selector_background)
        }

        val avatar = TextView(this).apply {
            val initial = conversation.address
                .filter { it.isLetterOrDigit() }
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "?"

            text = initial
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF777777.toInt())
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
            text = conversation.address
            textSize = 17f
            setTextColor(0xFF202124.toInt())
            maxLines = 1
        }

        val preview = TextView(this).apply {
            text = conversation.body.replace("\n", " ")
            textSize = 14f
            setTextColor(0xFF777777.toInt())
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
            setTextColor(0xFF777777.toInt())
        }

        rightContainer.addView(time)

        if (conversation.unreadCount > 0) {
            val unread = TextView(this).apply {
                text = conversation.unreadCount.toString()
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF6750A4.toInt())
                setPadding(
                    dp(7),
                    dp(3),
                    dp(7),
                    dp(3)
                )
            }

            rightContainer.addView(
                unread,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            )
        }

        row.addView(rightContainer)

        row.setOnClickListener {
            val intent = Intent(this, ConversationActivity::class.java).apply {
                putExtra("thread_id", conversation.threadId)
                putExtra("address", conversation.address)
            }
            startActivity(intent)
        }

        conversationList.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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
