package com.senior.scrollstop.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.senior.scrollstop.CrashLog

/**
 * Shows the previous crash with plain Android views (no Compose), so it still works if the
 * crash came from the normal UI.
 */
fun crashReportView(activity: Activity, report: String, onDismiss: () -> Unit): View {
    val pad = (16 * activity.resources.displayMetrics.density).toInt()
    val column = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad * 3, pad, pad)
    }
    column.addView(TextView(activity).apply {
        text = "ScrollStop crashed last time"
        textSize = 22f
        setTextColor(Color.WHITE)
    })
    column.addView(TextView(activity).apply {
        text = "Please share this report with whoever made the app. It contains no personal data, " +
            "only the error and your phone model."
        setTextColor(Color.LTGRAY)
        setPadding(0, pad / 2, 0, pad)
    })
    column.addView(Button(activity).apply {
        text = "Share report"
        setOnClickListener {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "ScrollStop crash report")
                .putExtra(Intent.EXTRA_TEXT, report)
            activity.startActivity(Intent.createChooser(send, "Share crash report"))
        }
    })
    column.addView(Button(activity).apply {
        text = "Dismiss and open ScrollStop"
        setOnClickListener {
            CrashLog.clear(activity)
            onDismiss()
        }
    })
    column.addView(TextView(activity).apply {
        text = report
        typeface = Typeface.MONOSPACE
        textSize = 11f
        setTextColor(Color.LTGRAY)
        setTextIsSelectable(true)
        setPadding(0, pad, 0, 0)
    })
    return ScrollView(activity).apply {
        setBackgroundColor(Color.rgb(0x0F, 0x17, 0x20))
        addView(column)
    }
}
