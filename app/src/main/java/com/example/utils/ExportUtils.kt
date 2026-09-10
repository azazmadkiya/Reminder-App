package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.PartyEntity
import com.example.data.local.TransactionEntity
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.data.local.ReminderEntity

object ExportUtils {

    private fun getExportsDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun shareFile(context: Context, file: File, mimeType: String, subject: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Export"))
    }

    fun exportLedgerToCsv(context: Context, party: PartyEntity, transactions: List<TransactionEntity>) {
        val exportsDir = getExportsDir(context)
        val fileName = "Ledger_${party.name.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val file = File(exportsDir, fileName)

        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        FileWriter(file).use { writer ->
            writer.append("Date,Type,Amount,Notes\n")
            
            // Add opening balance as first row if any
            if (party.openingBalance > 0) {
                writer.append("-,-,${party.openingBalance},Opening Balance\n")
            }

            for (t in transactions) {
                val dateStr = formatter.format(Date(t.date))
                val typeStr = t.type // "Give" or "Got"
                writer.append("$dateStr,$typeStr,${t.amount},\"${t.description.replace("\"", "\"\"")}\"\n")
            }
        }

        shareFile(context, file, "text/csv", "Ledger CSV Export for ${party.name}")
    }

    fun exportLedgerToPdf(context: Context, party: PartyEntity, transactions: List<TransactionEntity>) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
        }
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
        }

        var yPosition = 50f
        canvas.drawText("Ledger Report: ${party.name}", 50f, yPosition, titlePaint)
        yPosition += 40f

        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        
        paint.isFakeBoldText = true
        canvas.drawText("Date", 50f, yPosition, paint)
        canvas.drawText("Type", 200f, yPosition, paint)
        canvas.drawText("Amount", 350f, yPosition, paint)
        canvas.drawText("Notes", 450f, yPosition, paint)
        paint.isFakeBoldText = false
        yPosition += 30f

        if (party.openingBalance > 0) {
            canvas.drawText("-", 50f, yPosition, paint)
            canvas.drawText("-", 200f, yPosition, paint)
            canvas.drawText("₹${party.openingBalance}", 350f, yPosition, paint)
            canvas.drawText("Opening Balance", 450f, yPosition, paint)
            yPosition += 25f
        }

        for (t in transactions) {
            if (yPosition > 800f) {
                // simple pagination: if too long, we just stop for this basic implementation 
                // (or create new page). Let's just create new page.
                pdfDocument.finishPage(page)
                val newPage = pdfDocument.startPage(pageInfo)
                val newCanvas = newPage.canvas
                yPosition = 50f
                newCanvas.drawText("Date", 50f, yPosition, paint)
                newCanvas.drawText("Type", 200f, yPosition, paint)
                newCanvas.drawText("Amount", 350f, yPosition, paint)
                newCanvas.drawText("Notes", 450f, yPosition, paint)
                yPosition += 30f
                continue
            }
            val dateStr = formatter.format(Date(t.date))
            canvas.drawText(dateStr, 50f, yPosition, paint)
            canvas.drawText(t.type, 200f, yPosition, paint)
            canvas.drawText("₹${t.amount}", 350f, yPosition, paint)
            
            // basic text truncation for description
            val shortNotes = if (t.description.length > 15) t.description.take(15) + "..." else t.description
            canvas.drawText(shortNotes, 450f, yPosition, paint)
            
            yPosition += 25f
        }

        pdfDocument.finishPage(page)

        val exportsDir = getExportsDir(context)
        val fileName = "Ledger_${party.name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(exportsDir, fileName)

        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        shareFile(context, file, "application/pdf", "Ledger PDF Export for ${party.name}")
    }

    fun exportRemindersToCsv(context: Context, reminders: List<ReminderEntity>) {
        val exportsDir = getExportsDir(context)
        val fileName = "Reminders_Tax_Reports_${System.currentTimeMillis()}.csv"
        val file = File(exportsDir, fileName)

        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        FileWriter(file).use { writer ->
            writer.append("Title,Category,Due Date,Status,Amount,Notes\n")
            
            for (r in reminders) {
                val dateStr = formatter.format(Date(r.dueDate))
                val amountStr = r.amount?.toString() ?: "-"
                writer.append("\"${r.title.replace("\"", "\"\"")}\",${r.category},$dateStr,${r.status},$amountStr,\"${r.notes.replace("\"", "\"\"")}\"\n")
            }
        }

        shareFile(context, file, "text/csv", "Reminders and Tax Reports CSV Export")
    }

    fun exportRemindersToPdf(context: Context, reminders: List<ReminderEntity>) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
        }
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
        }

        var yPosition = 50f
        canvas.drawText("Reminders & Tax Reports", 50f, yPosition, titlePaint)
        yPosition += 40f

        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        
        paint.isFakeBoldText = true
        canvas.drawText("Title", 50f, yPosition, paint)
        canvas.drawText("Category", 200f, yPosition, paint)
        canvas.drawText("Due Date", 320f, yPosition, paint)
        canvas.drawText("Status", 420f, yPosition, paint)
        canvas.drawText("Amount", 500f, yPosition, paint)
        paint.isFakeBoldText = false
        yPosition += 30f

        for (r in reminders) {
            if (yPosition > 800f) {
                pdfDocument.finishPage(page)
                val newPage = pdfDocument.startPage(pageInfo)
                val newCanvas = newPage.canvas
                yPosition = 50f
                newCanvas.drawText("Title", 50f, yPosition, paint)
                newCanvas.drawText("Category", 200f, yPosition, paint)
                newCanvas.drawText("Due Date", 320f, yPosition, paint)
                newCanvas.drawText("Status", 420f, yPosition, paint)
                newCanvas.drawText("Amount", 500f, yPosition, paint)
                yPosition += 30f
                continue
            }
            val dateStr = formatter.format(Date(r.dueDate))
            
            val shortTitle = if (r.title.length > 15) r.title.take(15) + "..." else r.title
            canvas.drawText(shortTitle, 50f, yPosition, paint)
            
            val shortCat = if (r.category.length > 12) r.category.take(12) + "..." else r.category
            canvas.drawText(shortCat, 200f, yPosition, paint)
            
            canvas.drawText(dateStr, 320f, yPosition, paint)
            canvas.drawText(r.status, 420f, yPosition, paint)
            
            val amountStr = if (r.amount != null) "₹${r.amount}" else "-"
            canvas.drawText(amountStr, 500f, yPosition, paint)
            
            yPosition += 25f
        }

        pdfDocument.finishPage(page)

        val exportsDir = getExportsDir(context)
        val fileName = "Reminders_Tax_Reports_${System.currentTimeMillis()}.pdf"
        val file = File(exportsDir, fileName)

        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        shareFile(context, file, "application/pdf", "Reminders & Tax Reports PDF Export")
    }
}
