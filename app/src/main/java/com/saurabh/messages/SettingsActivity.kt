package com.saurabh.messages

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val SWIPE_ACTION_DELETE = "delete"
        const val SWIPE_ACTION_ARCHIVE = "archive"

        const val PREF_SWIPE_LEFT = "swipe_left_action"
        const val PREF_SWIPE_RIGHT = "swipe_right_action"
    }

    private val prefs by lazy {
        getSharedPreferences("messages_settings", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(
                ContextCompat.getColor(this@SettingsActivity, R.color.messages_background)
            )
            clipToPadding = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), 0, dp(20), 0)
        }

        val title = TextView(this).apply {
            text = "Settings"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.messages_text_primary))
        }

        topBar.addView(title, LinearLayout.LayoutParams(0, dp(64), 1f))
        root.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
        ))

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(32))
        }

        scrollView.addView(content)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        fun section(label: String, card: LinearLayout) {
            val heading = TextView(this).apply {
                text = label.uppercase()
                textSize = 12f
                letterSpacing = 0.08f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.backup_glass_accent))
                setPadding(dp(4), dp(22), dp(4), dp(10))
            }
            content.addView(heading)
            content.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        fun card(): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@SettingsActivity, R.color.backup_glass_surface))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), ContextCompat.getColor(this@SettingsActivity, R.color.backup_glass_border))
            }
        }

        fun divider(parent: LinearLayout) {
            parent.addView(android.view.View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, R.color.messages_divider))
                alpha = 0.45f
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
            })
        }

        val messagesCard = card()
        addSwitchRow(
            messagesCard,
            "Delivery reports",
            "Show delivery reports for sent messages",
            prefs.getBoolean("delivery_reports", false)
        ) { enabled ->
            prefs.edit().putBoolean("delivery_reports", enabled).apply()
        }
        section("Messages", messagesCard)

        val swipeCard = card()
        addActionRow(
            swipeCard,
            "Swipe left",
            prefs.getString(PREF_SWIPE_LEFT, SWIPE_ACTION_DELETE) ?: SWIPE_ACTION_DELETE
        ) { showSwipeActionDialog(true) }
        divider(swipeCard)
        addActionRow(
            swipeCard,
            "Swipe right",
            prefs.getString(PREF_SWIPE_RIGHT, SWIPE_ACTION_ARCHIVE) ?: SWIPE_ACTION_ARCHIVE
        ) { showSwipeActionDialog(false) }
        section("Swipe actions", swipeCard)

        val privacyCard = card()
        addBlockedContactsRow(privacyCard)
        section("Privacy", privacyCard)

        val dataCard = card()
        addBackupRestoreRow(dataCard)
        section("Data", dataCard)

        setContentView(root)
        ViewCompat.requestApplyInsets(root)
    }

    private fun addBackupRestoreRow(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity, BackupRestoreActivity::class.java))
            }
        }
        val title = TextView(this).apply {
            text = "Backup & Restore"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.messages_text_primary))
        }
        row.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val arrow = TextView(this).apply {
            text = "›"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.messages_text_secondary))
        }
        row.addView(arrow, LinearLayout.LayoutParams(dp(32), dp(40)))
        parent.addView(row)
    }

    private fun addSwitchRow(
        parent: LinearLayout,
        titleText: String,
        summaryText: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(15), dp(14), dp(15))
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = titleText
            textSize = 16f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_primary
                )
            )
        }

        val summary = TextView(this).apply {
            text = summaryText
            textSize = 13f
            setPadding(0, dp(3), 0, 0)
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_secondary
                )
            )
        }

        textContainer.addView(title)
        textContainer.addView(summary)

        row.addView(
            textContainer,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val switch = SwitchCompat(this).apply {
            isChecked = checked
            thumbTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@SettingsActivity, R.color.backup_glass_accent)
            )
            trackTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@SettingsActivity, R.color.backup_glass_border)
            )
            setOnCheckedChangeListener { _, enabled -> onChanged(enabled) }
        }

        row.addView(switch)

        parent.addView(row)
    }

    private fun addActionRow(
        parent: LinearLayout,
        titleText: String,
        action: String,
        onClick: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setOnClickListener {
                onClick()
            }
        }

        val title = TextView(this).apply {
            text = titleText
            textSize = 16f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_primary
                )
            )
        }

        val value = TextView(this).apply {
            text = if (action == SWIPE_ACTION_DELETE) {
                "Delete"
            } else {
                "Archive"
            }
            textSize = 15f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_secondary
                )
            )
        }

        row.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        row.addView(value)

        parent.addView(row)
    }

    private fun addBlockedContactsRow(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(15), dp(10), dp(15))
            setOnClickListener {
                showBlockedContacts()
            }
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "Blocked contacts"
            textSize = 16f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_primary
                )
            )
        }

        val summary = TextView(this).apply {
            text = getBlockedSummary()
            textSize = 13f
            setPadding(0, dp(3), 0, 0)
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_secondary
                )
            )
        }

        textContainer.addView(title)
        textContainer.addView(summary)

        row.addView(
            textContainer,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val arrow = TextView(this).apply {
            text = "›"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_secondary
                )
            )
        }

        row.addView(
            arrow,
            LinearLayout.LayoutParams(dp(32), dp(48))
        )

        parent.addView(row)
    }

    private fun getBlockedSummary(): String {
        val count = BlockHelper.getBlockedAddresses(this).size

        return when (count) {
            0 -> "No blocked contacts"
            1 -> "1 blocked contact"
            else -> "$count blocked contacts"
        }
    }

    private fun showBlockedContacts() {
        val addresses = BlockHelper
            .getBlockedAddresses(this)
            .toList()
            .sorted()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Blocked contacts")
            .create()

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(8))
        }

        if (addresses.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No blocked contacts"
                textSize = 15f
                setPadding(0, dp(16), 0, dp(20))
                setTextColor(
                    ContextCompat.getColor(
                        this@SettingsActivity,
                        R.color.messages_text_secondary
                    )
                )
            }

            outer.addView(empty)
        } else {
            // Scrollable blocked-contact list
            val scrollView = ScrollView(this).apply {
                overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            addresses.forEach { address ->
                addBlockedContactRow(
                    container,
                    address,
                    dialog
                )
            }

            scrollView.addView(
                container,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            outer.addView(
                scrollView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(420)
                )
            )
        }

        dialog.setView(outer)

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    setColor(
                        ContextCompat.getColor(
                            this@SettingsActivity,
                            R.color.messages_surface
                        )
                    )
                    cornerRadius = dp(20).toFloat()
                }
            )

            dialog.window?.setLayout(
                dp(340),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        dialog.show()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(
                    ContextCompat.getColor(
                        this@SettingsActivity,
                        R.color.messages_surface
                    )
                )
                cornerRadius = dp(20).toFloat()
            }
        )

        dialog.window?.setLayout(
            dp(340),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun addBlockedContactRow(
        container: LinearLayout,
        address: String,
        dialog: AlertDialog
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val contactName = getContactName(address)

        val title = TextView(this).apply {
            text = contactName ?: address
            textSize = 16f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_primary
                )
            )
        }

        textContainer.addView(title)

        if (contactName != null) {
            val number = TextView(this).apply {
                text = address
                textSize = 13f
                setPadding(0, dp(3), 0, 0)
                setTextColor(
                    ContextCompat.getColor(
                        this@SettingsActivity,
                        R.color.messages_text_secondary
                    )
                )
            }

            textContainer.addView(number)
        }

        row.addView(
            textContainer,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val unblock = TextView(this).apply {
            text = "Unblock"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(4), dp(8))
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_primary
                )
            )

            setOnClickListener {
                BlockHelper.unblock(
                    this@SettingsActivity,
                    address
                )

                Toast.makeText(
                    this@SettingsActivity,
                    "Unblocked",
                    Toast.LENGTH_SHORT
                ).show()

                dialog.dismiss()
                showBlockedContacts()
            }
        }

        row.addView(unblock)

        container.addView(row)
    }

    private fun getContactName(address: String): String? {
        return try {
            contentResolver.query(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(address)
                    .build(),
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
        } catch (_: Exception) {
            null
        }
    }

    private fun showSwipeActionDialog(left: Boolean) {
        val current = prefs.getString(
            if (left) PREF_SWIPE_LEFT else PREF_SWIPE_RIGHT,
            if (left) SWIPE_ACTION_DELETE else SWIPE_ACTION_ARCHIVE
        )

        val dialog = android.app.Dialog(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
            )

            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(
                    ContextCompat.getColor(
                        this@SettingsActivity,
                        R.color.messages_surface_variant
                    )
                )
            }
        }

        val title = TextView(this).apply {
            text = if (left) "Swipe left" else "Swipe right"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_primary
                )
            )
            setPadding(
                dp(16),
                dp(12),
                dp(16),
                dp(8)
            )
        }

        root.addView(title)

        fun addOption(label: String, value: String) {
            val selected = current == value

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(16),
                    dp(6),
                    dp(16),
                    dp(6)
                )
                isClickable = true
                isFocusable = true

                val radio = android.widget.ImageView(this@SettingsActivity).apply {
                    setImageResource(
                        if (selected) {
                            R.drawable.ic_sim_radio_selected
                        } else {
                            R.drawable.ic_sim_radio_unselected
                        }
                    )

                    layoutParams = LinearLayout.LayoutParams(
                        dp(24),
                        dp(24)
                    )
                }

                val text = TextView(this@SettingsActivity).apply {
                    this.text = label
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(
                        ContextCompat.getColor(
                            this@SettingsActivity,
                            R.color.messages_text_primary
                        )
                    )
                    setPadding(
                        dp(14),
                        0,
                        0,
                        0
                    )
                }

                addView(radio)
                addView(
                    text,
                    LinearLayout.LayoutParams(
                        0,
                        dp(52),
                        1f
                    )
                )

                setOnClickListener {
                    prefs.edit()
                        .putString(
                            if (left) PREF_SWIPE_LEFT else PREF_SWIPE_RIGHT,
                            value
                        )
                        .apply()

                    dialog.dismiss()
                    recreate()
                }
            }

            root.addView(row)
        }

        addOption("Delete", SWIPE_ACTION_DELETE)
        addOption("Archive", SWIPE_ACTION_ARCHIVE)

        dialog.setContentView(root)

        dialog.window?.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        dialog.window?.setLayout(
            minOf(
                dp(360),
                resources.displayMetrics.widthPixels - dp(32)
            ),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.show()

        dialog.window?.setLayout(
            minOf(
                dp(360),
                resources.displayMetrics.widthPixels - dp(32)
            ),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
