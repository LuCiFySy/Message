package com.saurabh.messages

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
            setPadding(0, dp(20), 0, dp(20))
            setBackgroundColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_background
                )
            )
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val back = android.widget.ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            contentDescription = "Back"
            background = null
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        }

        val title = TextView(this).apply {
            text = "Settings"
            textSize = 28f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_primary
                )
            )
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        topBar.addView(
            back,
            LinearLayout.LayoutParams(dp(56), dp(56))
        )

        topBar.addView(
            title,
            LinearLayout.LayoutParams(
                0,
                dp(56),
                1f
            )
        )

        root.addView(topBar)

        val messagesSection = TextView(this).apply {
            text = "Messages"
            textSize = 14f
            setPadding(dp(20), dp(28), dp(20), dp(10))
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_secondary
                )
            )
        }

        root.addView(messagesSection)

        addSwitchRow(
            root = root,
            title = "Delivery reports",
            summary = "Show when SMS messages are delivered",
            checked = prefs.getBoolean("delivery_reports", true)
        ) { enabled ->
            prefs.edit()
                .putBoolean("delivery_reports", enabled)
                .apply()
        }

        val swipeSection = TextView(this).apply {
            text = "Swipe actions"
            textSize = 14f
            setPadding(dp(20), dp(28), dp(20), dp(10))
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_secondary
                )
            )
        }

        root.addView(swipeSection)

        addActionRow(
            root = root,
            title = "Swipe left",
            summary = "Choose what happens when you swipe a conversation left",
            preferenceKey = PREF_SWIPE_LEFT,
            defaultAction = SWIPE_ACTION_ARCHIVE
        )

        addActionRow(
            root = root,
            title = "Swipe right",
            summary = "Choose what happens when you swipe a conversation right",
            preferenceKey = PREF_SWIPE_RIGHT,
            defaultAction = SWIPE_ACTION_DELETE
        )

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                0,
                dp(20) + systemBars.top,
                0,
                dp(20) + systemBars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun addSwitchRow(
        root: LinearLayout,
        title: String,
        summary: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(20), 0, dp(20), 0)
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_primary
                )
            )
        }

        val summaryView = TextView(this).apply {
            text = summary
            textSize = 13f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_secondary
                )
            )
            setPadding(0, dp(4), 0, 0)
        }

        textContainer.addView(titleView)
        textContainer.addView(summaryView)

        val switch = ModernToggleView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(24))
            setChecked(checked)
            setOnCheckedChangeListener { enabled ->
                onChanged(enabled)
            }
        }

        row.addView(textContainer)
        row.addView(switch)

        root.addView(row)
    }

    private fun addActionRow(
        root: LinearLayout,
        title: String,
        summary: String,
        preferenceKey: String,
        defaultAction: String
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76)
            setPadding(dp(20), dp(8), dp(20), dp(8))
            isClickable = true
            isFocusable = true
        }

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_primary
                )
            )
        }

        val summaryView = TextView(this).apply {
            text = summary
            textSize = 13f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_secondary
                )
            )
            setPadding(0, dp(4), 0, 0)
        }

        textContainer.addView(titleView)
        textContainer.addView(summaryView)

        val valueView = TextView(this).apply {
            textSize = 15f
            setTextColor(
                ContextCompat.getColor(
                    this@SettingsActivity,
                    R.color.messages_text_primary
                )
            )
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }

        fun updateValue() {
            val action = prefs.getString(
                preferenceKey,
                defaultAction
            ) ?: defaultAction

            valueView.text =
                if (action == SWIPE_ACTION_DELETE) {
                    "Delete  ›"
                } else {
                    "Archive  ›"
                }
        }

        row.setOnClickListener {
            val current = prefs.getString(
                preferenceKey,
                defaultAction
            ) ?: defaultAction

            val selectedIndex =
                if (current == SWIPE_ACTION_DELETE) 0 else 1

            val dialog = android.app.Dialog(this)

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(20), dp(24), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(
                        ContextCompat.getColor(
                            this@SettingsActivity,
                            R.color.messages_surface
                        )
                    )
                    cornerRadius = dp(28).toFloat()
                }
            }

            val dialogTitle = TextView(this).apply {
                text = title
                textSize = 20f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(
                    ContextCompat.getColor(
                        this@SettingsActivity,
                        R.color.messages_text_primary
                    )
                )
                setPadding(0, 0, 0, dp(12))
            }

            container.addView(dialogTitle)

            fun addChoice(label: String, value: String, selected: Boolean) {
                val choice = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(52)
                    setPadding(dp(4), 0, dp(4), 0)
                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        prefs.edit()
                            .putString(preferenceKey, value)
                            .apply()

                        updateValue()
                        dialog.dismiss()
                    }
                }

                val indicator = TextView(this).apply {
                    text = if (selected) "●" else "○"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(
                        ContextCompat.getColor(
                            this@SettingsActivity,
                            if (selected) {
                                R.color.messages_primary
                            } else {
                                R.color.messages_text_secondary
                            }
                        )
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        dp(40),
                        dp(48)
                    )
                }

                val labelView = TextView(this).apply {
                    text = label
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(
                        ContextCompat.getColor(
                            this@SettingsActivity,
                            R.color.messages_text_primary
                        )
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        dp(48),
                        1f
                    )
                }

                choice.addView(indicator)
                choice.addView(labelView)
                container.addView(choice)
            }

            addChoice(
                "Delete",
                SWIPE_ACTION_DELETE,
                selectedIndex == 0
            )

            addChoice(
                "Archive",
                SWIPE_ACTION_ARCHIVE,
                selectedIndex == 1
            )

            dialog.setContentView(container)

            dialog.window?.setBackgroundDrawableResource(
                android.R.color.transparent
            )

            dialog.window?.setLayout(
                dp(320),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )

            dialog.show()

            dialog.window?.setLayout(
                dp(320),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        row.addView(textContainer)
        row.addView(valueView)

        root.addView(row)

        updateValue()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
