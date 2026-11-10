package com.saurabh.messages

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.DocumentsContract
import android.provider.Telephony
import android.util.Base64
import android.util.Xml
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Local-only XML backup compatible with the documented SyncTech SMS Backup & Restore format.
 * Internal provider IDs are deliberately never exported or reused.
 */
class BackupRestoreActivity : AppCompatActivity() {
    private lateinit var smsCheck: CheckBox
    private lateinit var mmsCheck: CheckBox
    private lateinit var callsCheck: CheckBox
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var folderUri: Uri? = null

    private val prefs by lazy { getSharedPreferences("backup_restore", MODE_PRIVATE) }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                folderUri = uri
                prefs.edit().putString("folder_uri", uri.toString()).apply()
                status.text = "Backup folder: ${DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':')}"
            } catch (e: Exception) { toast("Unable to use this folder: ${e.message}") }
        }
    }

    private val messageFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runTask("Restoring messages…") { restoreMessages(uri) }
    }
    private val callFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runTask("Restoring call history…") { restoreCalls(uri) }
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val readOk = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val writeOk = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (readOk) toast("Call-log read access granted. Tap the operation again.")
        else toast("Call-log permission was not granted. Check Android app permissions.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderUri = prefs.getString("folder_uri", null)?.let(Uri::parse)
        buildUi()
    }

    private fun buildUi() {
        val primary = ContextCompat.getColor(this, R.color.backup_glass_accent)
        val surface = ContextCompat.getColor(this, R.color.backup_glass_surface)
        val pageBackground = ContextCompat.getColor(this, R.color.messages_background)
        val textPrimary = ContextCompat.getColor(this, R.color.messages_text_primary)
        val textSecondary = ContextCompat.getColor(this, R.color.messages_text_secondary)
        val divider = ContextCompat.getColor(this, R.color.backup_glass_border)

        fun rounded(color: Int, radius: Int, stroke: Int = 0) =
            android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(radius).toFloat()
                if (stroke != 0) setStroke(dp(1), stroke)
            }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackground)
            clipToPadding = false
        }

        // Respect status/navigation bars, including display cutouts.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        window.statusBarColor = pageBackground
        window.navigationBarColor = pageBackground

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), 0, dp(20), 0)
        }

        val heading = TextView(this).apply {
            text = "Backup & Restore"
            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(textPrimary)
        }

        topBar.addView(
            heading,
            LinearLayout.LayoutParams(0, dp(64), 1f)
        )
        root.addView(
            topBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
            )
        )

        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(28))
        }

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams( 
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        fun sectionTitle(label: String) {
            val title = TextView(this).apply {
                text = label
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(primary)
                setPadding(dp(2), dp(12), dp(2), dp(10))
            }
            content.addView(title)
        }

        val backupCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface, 20, divider)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        fun optionRow(
            label: String,
            checked: Boolean,
            onCreated: (CheckBox) -> Unit
        ) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(12), dp(12))
            }

            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val title = TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(textPrimary)
            }

            texts.addView(title)

            val check = CheckBox(this).apply {
                isChecked = checked
                buttonTintList = android.content.res.ColorStateList.valueOf(primary)
            }

            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(check)
            onCreated(check)

            backupCard.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        sectionTitle("BACKUP CONTENT")

        // Keep the existing checkbox instances used by createBackup().
        optionRow("SMS messages", true) {
            smsCheck = it
        }
        optionRow("MMS messages", true) {
            mmsCheck = it
        }
        optionRow("Call history", true) {
            callsCheck = it
        }

        content.addView(
            backupCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        fun actionButton(
            label: String,
            filled: Boolean = false,
            action: () -> Unit
        ) {
            val button = TextView(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(this@BackupRestoreActivity, if (filled) R.color.backup_glass_button_text else R.color.backup_glass_accent))
                background = rounded(
                    if (filled) ContextCompat.getColor(this@BackupRestoreActivity, R.color.backup_glass_button) else surface,
                    16,
                    if (filled) ContextCompat.getColor(this@BackupRestoreActivity, R.color.backup_glass_button_border) else divider
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
                setPadding(dp(16), 0, dp(16), 0)
            }
            content.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(54)
                ).apply { topMargin = dp(10) }
            )
        }

        sectionTitle("BACKUP LOCATION")
        actionButton("Choose backup folder") {
            folderPicker.launch(folderUri)
        }

        actionButton("Back up selected data", true) {
            if (callsCheck.isChecked &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_CALL_LOG
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
            } else {
                runTask("Creating backup…") { createBackup() }
            }
        }

        sectionTitle("RESTORE")
        actionButton("Restore messages") {
            messageFilePicker.launch(
                arrayOf("text/xml", "application/xml", "text/plain", "application/octet-stream")
            )
        }

        actionButton("Restore call history") {
            val missing = arrayOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG
            ).filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }.toTypedArray()

            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing)
            } else {
                callFilePicker.launch(
                    arrayOf("text/xml", "application/xml", "text/plain", "application/octet-stream")
                )
            }
        }

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        content.addView(
            progress,
            LinearLayout.LayoutParams(
                dp(32),
                dp(32)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(18)
            }
        )

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(textSecondary)
            setPadding(dp(4), dp(14), dp(4), dp(8))
            visibility = View.GONE
        }
        content.addView(status)
        setContentView(root)
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    private fun runTask(label: String, task: () -> String) {
        progress.visibility = View.VISIBLE; status.visibility = View.VISIBLE; status.text = label
        Thread {
            val result = try { task() } catch (e: SecurityException) { "Permission denied: ${e.message}" } catch (e: Exception) { "Failed: ${e.message ?: e.javaClass.simpleName}" }
            runOnUiThread { progress.visibility = View.GONE; status.text = result; Toast.makeText(this, result, Toast.LENGTH_LONG).show() }
        }.start()
    }

    private fun createBackup(): String {
        val folder = folderUri ?: return "Choose a backup folder first."
        if (!smsCheck.isChecked && !mmsCheck.isChecked && !callsCheck.isChecked) return "Select at least one backup type."
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        val backupSet = UUID.randomUUID().toString()
        var result = mutableListOf<String>()
        if (smsCheck.isChecked || mmsCheck.isChecked) {
            val name = "messages-$stamp.xml"
            val uri = createFile(folder, "text/xml", name)
            contentResolver.openOutputStream(uri)?.use { exportMessages(it, smsCheck.isChecked, mmsCheck.isChecked, backupSet, now) } ?: throw IllegalStateException("Cannot open message backup file")
            result.add(name)
        }
        if (callsCheck.isChecked) {
            requirePermission(Manifest.permission.READ_CALL_LOG)
            val name = "calls-$stamp.xml"
            val uri = createFile(folder, "text/xml", name)
            contentResolver.openOutputStream(uri)?.use { exportCalls(it, backupSet, now) } ?: throw IllegalStateException("Cannot open call backup file")
            result.add(name)
        }
        return "Backup created: ${result.joinToString()}"
    }

    private fun createFile(tree: Uri, mime: String, name: String): Uri {
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(contentResolver, dir, mime, name) ?: throw IllegalStateException("Could not create $name")
    }

    private fun exportMessages(out: OutputStream, includeSms: Boolean, includeMms: Boolean, backupSet: String, backupDate: Long) {
        val records = mutableListOf<Pair<String, Long>>()
        if (includeSms) contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), null, null, null)?.use { while (it.moveToNext()) records.add("sms" to it.getLong(0)) }
        if (includeMms) contentResolver.query(Telephony.Mms.CONTENT_URI, arrayOf(Telephony.Mms._ID), null, null, null)?.use { while (it.moveToNext()) records.add("mms" to it.getLong(0)) }
        val serializer = Xml.newSerializer(); serializer.setOutput(out, "UTF-8"); serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "smses"); serializer.attribute(null, "count", records.size.toString()); serializer.attribute(null, "backup_set", backupSet)
        serializer.attribute(null, "backup_date", backupDate.toString())
        records.forEach { (kind, id) -> if (kind == "sms") writeSms(serializer, id) else writeMms(serializer, id) }
        serializer.endTag(null, "smses"); serializer.endDocument(); serializer.flush()
    }

    private fun writeSms(x: XmlSerializer, id: Long) {
        contentResolver.query(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null, null, null)?.use { c -> if (!c.moveToFirst()) return
            x.startTag(null, "sms")
            val fields = listOf("protocol", "address", "date", "date_sent", "type", "subject", "body", "service_center", "read", "status", "locked", "sub_id")
            val cols = listOf(Telephony.Sms.PROTOCOL, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE, Telephony.Sms.SUBJECT, Telephony.Sms.BODY, Telephony.Sms.SERVICE_CENTER, Telephony.Sms.READ, Telephony.Sms.STATUS, Telephony.Sms.LOCKED, Telephony.Sms.SUBSCRIPTION_ID)
            fields.forEachIndexed { i, key -> attr(x, key, c, cols[i]) }; x.endTag(null, "sms")
        }
    }

    private fun writeMms(x: XmlSerializer, id: Long) {
        contentResolver.query(Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, id.toString()), null, null, null, null)?.use { c -> if (!c.moveToFirst()) return
            x.startTag(null, "mms")
            val fields = listOf("date", "date_sent", "msg_box", "m_type", "read", "seen", "locked", "sub_id", "ct_t", "tr_id", "sub", "sub_cs", "m_size", "d_rpt", "rr", "text_only")
            val cols = listOf(Telephony.Mms.DATE, Telephony.Mms.DATE_SENT, Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_TYPE, Telephony.Mms.READ, Telephony.Mms.SEEN, Telephony.Mms.LOCKED, Telephony.Mms.SUBSCRIPTION_ID, Telephony.Mms.CONTENT_TYPE, Telephony.Mms.TRANSACTION_ID, Telephony.Mms.SUBJECT, Telephony.Mms.SUBJECT_CHARSET, Telephony.Mms.MESSAGE_SIZE, Telephony.Mms.DELIVERY_REPORT, Telephony.Mms.READ_REPORT, Telephony.Mms.TEXT_ONLY)
            fields.forEachIndexed { i, key -> val v = value(c, cols[i]); if (v != null) x.attribute(null, key, if (key == "date" || key == "date_sent") (v.toLongOrNull()?.times(1000L) ?: v).toString() else v) }
            val participantAddresses = mutableListOf<String>()
            contentResolver.query(Uri.parse("content://mms/$id/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS), null, null, null)?.use { a ->
                while (a.moveToNext()) { val address = a.getString(0); if (!address.isNullOrBlank() && address != "insert-address-token") participantAddresses.add(address) }
            }
            if (participantAddresses.isNotEmpty()) x.attribute(null, "address", participantAddresses.distinct().joinToString("~"))
            val partList = mutableListOf<Long>()
            contentResolver.query(Uri.parse("content://mms/part"), arrayOf(Telephony.Mms.Part._ID), "${Telephony.Mms.Part.MSG_ID}=?", arrayOf(id.toString()), "${Telephony.Mms.Part.SEQ} ASC")?.use { p -> while (p.moveToNext()) partList.add(p.getLong(0)) }
            x.startTag(null, "parts")
            partList.forEach { partId -> writePart(x, partId) }
            x.endTag(null, "parts")
            val addrUri = Uri.parse("content://mms/$id/addr")
            x.startTag(null, "addrs")
            contentResolver.query(addrUri, null, null, null, null)?.use { a -> while (a.moveToNext()) { x.startTag(null, "addr"); attr(x, "address", a, Telephony.Mms.Addr.ADDRESS); attr(x, "type", a, Telephony.Mms.Addr.TYPE); attr(x, "charset", a, Telephony.Mms.Addr.CHARSET); x.endTag(null, "addr") } }
            x.endTag(null, "addrs"); x.endTag(null, "mms")
        }
    }

    private fun writePart(x: XmlSerializer, partId: Long) {
        contentResolver.query(Uri.withAppendedPath(Uri.parse("content://mms/part"), partId.toString()), null, null, null, null)?.use { p -> if (!p.moveToFirst()) return
            x.startTag(null, "part")
            val keys = listOf("seq", "ct", "name", "cl", "chset", "cid", "cd", "fn", "text")
            val cols = listOf(Telephony.Mms.Part.SEQ, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME, Telephony.Mms.Part.CONTENT_LOCATION, Telephony.Mms.Part.CHARSET, Telephony.Mms.Part.CONTENT_ID, Telephony.Mms.Part.CONTENT_DISPOSITION, Telephony.Mms.Part.FILENAME, Telephony.Mms.Part.TEXT)
            keys.forEachIndexed { i, k -> attr(x, k, p, cols[i]) }
            val ct = value(p, Telephony.Mms.Part.CONTENT_TYPE).orEmpty()
            val dataUri = Uri.withAppendedPath(Uri.parse("content://mms/part"), partId.toString())
            if (ct.startsWith("text/")) { val text = value(p, Telephony.Mms.Part.TEXT); if (text != null) x.attribute(null, "text", text) }
            else contentResolver.openInputStream(dataUri)?.use { x.attribute(null, "data", Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)) }
            x.endTag(null, "part")
        }
    }

    private fun exportCalls(out: OutputStream, backupSet: String, backupDate: Long) {
        // Match SyncTech's calls XML: export every CallLog provider row as-is.
        // Do not collapse identical-looking rows; the provider may legitimately
        // expose repeated entries and the backup must preserve its row count.
        val columns = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DURATION,
            CallLog.Calls.DATE,
            CallLog.Calls.TYPE,
            CallLog.Calls.NUMBER_PRESENTATION,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.POST_DIAL_DIGITS,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.CACHED_NUMBER_LABEL
        )
        val rows = mutableListOf<List<String?>>()
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            columns,
            null,
            null,
            "${CallLog.Calls.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                rows.add(columns.map { column ->
                    val index = c.getColumnIndex(column)
                    if (index >= 0 && !c.isNull(index)) c.getString(index) else null
                })
            }
        }

        val x = Xml.newSerializer()
        x.setOutput(out, "UTF-8")
        x.startDocument("UTF-8", true)
        x.startTag(null, "calls")
        x.attribute(null, "count", rows.size.toString())
        x.attribute(null, "backup_set", backupSet)
        x.attribute(null, "backup_date", backupDate.toString())
        val names = listOf(
            "number", "duration", "date", "type", "presentation",
            "subscription_id", "subscription_component_name",
            "post_dial_digits", "contact_name", "number_type", "number_label"
        )
        val readableDate = SimpleDateFormat("d MMM yyyy HH:mm:ss", Locale.US)
        rows.forEach { row ->
            x.startTag(null, "call")
            names.forEachIndexed { index, name ->
                row[index]?.let { x.attribute(null, name, it) }
            }
            row[2]?.toLongOrNull()?.let {
                x.attribute(null, "readable_date", readableDate.format(Date(it)))
            }
            x.endTag(null, "call")
        }
        x.endTag(null, "calls")
        x.endDocument()
        x.flush()
    }

    private fun restoreMessages(uri: Uri): String {
        requireDefaultSmsApp()
        var imported = 0; var skipped = 0; var failed = 0
        contentResolver.openInputStream(uri)?.use { input ->
            val p = Xml.newPullParser(); p.setInput(input, "UTF-8")
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && (p.name == "sms" || p.name == "mms")) {
                    try { if (p.name == "sms") { val row = readSms(p); if (insertSms(row)) imported++ else skipped++ } else { val row = readMms(p); if (insertMms(row)) imported++ else skipped++ } } catch (_: Exception) { failed++ }
                }
                event = p.next()
            }
        } ?: throw IllegalStateException("Cannot read selected file")
        return "Messages restored: $imported; already present: $skipped; failed: $failed"
    }

    private fun readSms(p: XmlPullParser): ContentValues {
        val v = ContentValues(); fun put(key: String, col: String) { p.getAttributeValue(null,key)?.let { v.put(col,it) } }
        put("address", Telephony.Sms.ADDRESS); put("body", Telephony.Sms.BODY); put("date", Telephony.Sms.DATE); put("date_sent", Telephony.Sms.DATE_SENT); put("type", Telephony.Sms.TYPE); put("read", Telephony.Sms.READ); put("status", Telephony.Sms.STATUS); put("protocol", Telephony.Sms.PROTOCOL); put("locked", Telephony.Sms.LOCKED); put("service_center", Telephony.Sms.SERVICE_CENTER); put("subject", Telephony.Sms.SUBJECT); put("sub_id", Telephony.Sms.SUBSCRIPTION_ID)
        val address = v.getAsString(Telephony.Sms.ADDRESS).orEmpty(); if (address.isNotBlank()) v.put(Telephony.Sms.THREAD_ID, Telephony.Threads.getOrCreateThreadId(this, address))
        skipTag(p, "sms"); return v
    }

    private data class MmsRecord(val values: ContentValues, val addresses: List<ContentValues>, val parts: List<PartRecord>)
    private data class PartRecord(val values: ContentValues, val data: ByteArray?)

    private fun readMms(p: XmlPullParser): MmsRecord {
        val v = ContentValues(); val attrs = (0 until p.attributeCount).associate { p.getAttributeName(it) to p.getAttributeValue(it) }
        fun put(a: String, c: String) { attrs[a]?.let { v.put(c,it) } }
        put("date", Telephony.Mms.DATE); put("date_sent", Telephony.Mms.DATE_SENT); put("msg_box", Telephony.Mms.MESSAGE_BOX); put("m_type", Telephony.Mms.MESSAGE_TYPE); put("read", Telephony.Mms.READ); put("seen", Telephony.Mms.SEEN); put("locked", Telephony.Mms.LOCKED); put("ct_t", Telephony.Mms.CONTENT_TYPE); put("tr_id", Telephony.Mms.TRANSACTION_ID); put("sub", Telephony.Mms.SUBJECT); put("sub_cs", Telephony.Mms.SUBJECT_CHARSET); put("d_rpt", Telephony.Mms.DELIVERY_REPORT); put("rr", Telephony.Mms.READ_REPORT); put("text_only", Telephony.Mms.TEXT_ONLY)
        v.put(Telephony.Mms.DATE, (attrs["date"]?.toLongOrNull() ?: 0L) / 1000L); v.put(Telephony.Mms.DATE_SENT, (attrs["date_sent"]?.toLongOrNull() ?: 0L) / 1000L)
        val addrs = mutableListOf<ContentValues>(); val parts = mutableListOf<PartRecord>()
        val depth = p.depth
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.END_TAG && p.depth == depth) break
            if (p.eventType != XmlPullParser.START_TAG) continue
            when (p.name) {
                "addr" -> { addrs.add(readAddress(p)) }
                "part" -> { parts.add(readPart(p)) }
                "parts", "addrs" -> {
                    val container = p.name
                    val containerDepth = p.depth
                    while (p.next() != XmlPullParser.END_DOCUMENT) {
                        if (p.eventType == XmlPullParser.END_TAG && p.depth == containerDepth) break
                        if (p.eventType == XmlPullParser.START_TAG) {
                            if (container == "parts" && p.name == "part") parts.add(readPart(p))
                            else if (container == "addrs" && p.name == "addr") addrs.add(readAddress(p))
                            else skipTag(p, p.name)
                        }
                    }
                }
                else -> skipTag(p,p.name)
            }
        }
        return MmsRecord(v,addrs,parts)
    }

    private fun readAddress(p: XmlPullParser): ContentValues {
        val a = ContentValues()
        p.getAttributeValue(null, "address")?.let { a.put(Telephony.Mms.Addr.ADDRESS, it) }
        p.getAttributeValue(null, "type")?.toIntOrNull()?.let { a.put(Telephony.Mms.Addr.TYPE, it) }
        p.getAttributeValue(null, "charset")?.toIntOrNull()?.let { a.put(Telephony.Mms.Addr.CHARSET, it) }
        skipTag(p, "addr")
        return a
    }

    private fun readPart(p: XmlPullParser): PartRecord {
        val pv = ContentValues()
        fun putAttr(attribute: String, column: String) { p.getAttributeValue(null, attribute)?.let { pv.put(column, it) } }
        putAttr("seq", Telephony.Mms.Part.SEQ)
        putAttr("ct", Telephony.Mms.Part.CONTENT_TYPE)
        putAttr("name", Telephony.Mms.Part.NAME)
        putAttr("cl", Telephony.Mms.Part.CONTENT_LOCATION)
        putAttr("chset", Telephony.Mms.Part.CHARSET)
        putAttr("cid", Telephony.Mms.Part.CONTENT_ID)
        putAttr("cd", Telephony.Mms.Part.CONTENT_DISPOSITION)
        putAttr("fn", Telephony.Mms.Part.FILENAME)
        p.getAttributeValue(null, "text")?.let { pv.put(Telephony.Mms.Part.TEXT, it) }
        val data = p.getAttributeValue(null, "data")?.let { Base64.decode(it, Base64.DEFAULT) }
        skipTag(p, "part")
        return PartRecord(pv, data)
    }

    private fun insertSms(v: ContentValues): Boolean {
        val address=v.getAsString(Telephony.Sms.ADDRESS).orEmpty(); val date=v.getAsLong(Telephony.Sms.DATE) ?: return false; val type=v.getAsInteger(Telephony.Sms.TYPE) ?: 1
        val exists=contentResolver.query(Telephony.Sms.CONTENT_URI,arrayOf(Telephony.Sms._ID),"${Telephony.Sms.ADDRESS}=? AND ${Telephony.Sms.DATE}=? AND ${Telephony.Sms.TYPE}=? AND ${Telephony.Sms.BODY}=?",arrayOf(address,date.toString(),type.toString(),v.getAsString(Telephony.Sms.BODY).orEmpty()),null)?.use { it.moveToFirst() } ?: false
        if (exists) return false
        if (address.isNotBlank()) v.put(Telephony.Sms.THREAD_ID,Telephony.Threads.getOrCreateThreadId(this,address))
        return contentResolver.insert(Telephony.Sms.CONTENT_URI,v)!=null
    }

    private fun insertMms(r: MmsRecord): Boolean {
        val participants=r.addresses.mapNotNull { it.getAsString(Telephony.Mms.Addr.ADDRESS) }.filter { it.isNotBlank() && it != "insert-address-token" }.distinct()
        if (participants.isEmpty()) return false
        val box=r.values.getAsInteger(Telephony.Mms.MESSAGE_BOX) ?: Telephony.Mms.MESSAGE_BOX_INBOX
        val peer=r.addresses.firstOrNull { it.getAsInteger(Telephony.Mms.Addr.TYPE)==if(box==Telephony.Mms.MESSAGE_BOX_INBOX) 137 else 151 }?.getAsString(Telephony.Mms.Addr.ADDRESS) ?: participants.first()
        val thread=Telephony.Threads.getOrCreateThreadId(this, participants.toSet()); r.values.put(Telephony.Mms.THREAD_ID,thread)
        val date=r.values.getAsLong(Telephony.Mms.DATE) ?: 0L; val dateSent=r.values.getAsLong(Telephony.Mms.DATE_SENT) ?: 0L
        val exists=contentResolver.query(Telephony.Mms.CONTENT_URI,arrayOf(Telephony.Mms._ID),"${Telephony.Mms.DATE}=? AND ${Telephony.Mms.DATE_SENT}=? AND ${Telephony.Mms.THREAD_ID}=? AND ${Telephony.Mms.MESSAGE_BOX}=?",arrayOf(date.toString(),dateSent.toString(),thread.toString(),box.toString()),null)?.use { it.moveToFirst() } ?: false
        if (exists) return false
        val msgUri=contentResolver.insert(Telephony.Mms.CONTENT_URI,r.values) ?: return false; val msgId=msgUri.lastPathSegment?.toLongOrNull() ?: return true
        r.parts.forEach { part -> part.values.put(Telephony.Mms.Part.MSG_ID,msgId); val u=contentResolver.insert(Uri.parse("content://mms/$msgId/part"),part.values); if(u!=null && part.data!=null) contentResolver.openOutputStream(u)?.use { it.write(part.data) } }
        r.addresses.forEach { a -> a.put(Telephony.Mms.Addr.MSG_ID,msgId); contentResolver.insert(Uri.parse("content://mms/$msgId/addr"),a) }
        return true
    }

    private fun restoreCalls(uri: Uri): String {
        requirePermission(Manifest.permission.WRITE_CALL_LOG)
        var imported=0; var skipped=0; var failed=0
        contentResolver.openInputStream(uri)?.use { input -> val p=Xml.newPullParser(); p.setInput(input,"UTF-8"); var e=p.eventType
            while(e!=XmlPullParser.END_DOCUMENT) { if(e==XmlPullParser.START_TAG && p.name=="call") { try { val number=p.getAttributeValue(null,"number") ?: ""; val date=p.getAttributeValue(null,"date")?.toLongOrNull() ?: 0L; val duration=p.getAttributeValue(null,"duration")?.toLongOrNull() ?: 0L; val type=p.getAttributeValue(null,"type")?.toIntOrNull() ?: CallLog.Calls.INCOMING_TYPE
                val presentation = p.getAttributeValue(null,"presentation")?.toIntOrNull() ?: CallLog.Calls.PRESENTATION_ALLOWED
                val exists=contentResolver.query(CallLog.Calls.CONTENT_URI,arrayOf(CallLog.Calls._ID),"${CallLog.Calls.NUMBER}=? AND ${CallLog.Calls.DATE}=? AND ${CallLog.Calls.TYPE}=? AND ${CallLog.Calls.DURATION}=?",arrayOf(number,date.toString(),type.toString(),duration.toString()),null)?.use { it.moveToFirst() } ?: false
                if(exists) skipped++ else {
                    val v=ContentValues().apply {
                        put(CallLog.Calls.NUMBER,number)
                        put(CallLog.Calls.DATE,date)
                        put(CallLog.Calls.DURATION,duration)
                        put(CallLog.Calls.TYPE,type)
                        put(CallLog.Calls.NUMBER_PRESENTATION,presentation)
                        p.getAttributeValue(null,"subscription_id")?.toIntOrNull()?.let { put(CallLog.Calls.PHONE_ACCOUNT_ID, it.toString()) }
                        p.getAttributeValue(null,"subscription_component_name")?.let { put(CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,it) }
                        p.getAttributeValue(null,"post_dial_digits")?.let { put(CallLog.Calls.POST_DIAL_DIGITS,it) }
                        p.getAttributeValue(null,"contact_name")?.let { put(CallLog.Calls.CACHED_NAME,it) }
                    }
                    if(contentResolver.insert(CallLog.Calls.CONTENT_URI,v)!=null) imported++ else failed++
                }
                skipTag(p,"call") } catch(_:Exception) { failed++ } }; e=p.next() }
        } ?: throw IllegalStateException("Cannot read call backup")
        return "Calls restored: $imported; already present: $skipped; failed: $failed"
    }

    private fun attr(x: XmlSerializer, name: String, c: android.database.Cursor, column: String) { val v=value(c,column); if(v!=null) x.attribute(null,name,v) }
    private fun value(c: android.database.Cursor, col: String): String? { val i=c.getColumnIndex(col); if(i<0 || c.isNull(i)) return null; return when(c.getType(i)) { android.database.Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(c.getBlob(i),Base64.NO_WRAP); android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).toString(); android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString(); else -> c.getString(i) } }
    private fun skipTag(p: XmlPullParser, tag: String) { if(p.eventType!=XmlPullParser.START_TAG) return; val d=p.depth; while(p.next()!=XmlPullParser.END_DOCUMENT) if(p.eventType==XmlPullParser.END_TAG && p.depth==d) break }
    private fun requirePermission(permission: String) { if(ContextCompat.checkSelfPermission(this,permission)!=PackageManager.PERMISSION_GRANTED) throw SecurityException("Grant $permission in Android app permissions, then try again") }
    private fun requireDefaultSmsApp() {
        val packageCheck = Telephony.Sms.getDefaultSmsPackage(this) == packageName
        val roleCheck = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            getSystemService(android.app.role.RoleManager::class.java)
                ?.isRoleHeld(android.app.role.RoleManager.ROLE_SMS) == true

        if (!packageCheck && !roleCheck) {
            throw SecurityException("Messages is not recognised as the default SMS app. Check Android's SMS role.")
        }
    }
    private fun toast(s:String) = Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
