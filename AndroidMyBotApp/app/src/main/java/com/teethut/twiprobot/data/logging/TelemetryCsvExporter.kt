package com.teethut.twiprobot.data.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.teethut.twiprobot.domain.model.TelemetryLogEntry
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TelemetryCsvExporter {

    fun exportToShareIntent(context: Context, entries: List<TelemetryLogEntry>): Intent? {
        if (entries.isEmpty()) return null

        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "twip_telemetry_$timestamp.csv"
            val file = File(context.cacheDir, filename)

            FileWriter(file).use { writer ->
                writer.append(TelemetryLogEntry.CSV_HEADER).append("\n")
                for (entry in entries) {
                    writer.append(entry.toCsvRow()).append("\n")
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "TWIP Robot Telemetry ($timestamp)")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            return Intent.createChooser(shareIntent, "Share Telemetry CSV")
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

