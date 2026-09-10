package com.example.data.network

import com.example.BuildConfig
import com.example.data.local.PartyEntity
import com.example.data.local.ReminderEntity
import com.example.data.repository.AppRepository
import com.example.notification.AlarmScheduler
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

data class ParsedVoiceTask(
    val title: String,
    val category: String, // "GST", "Income Tax", "Payment Receive", "Payment Paid", "General"
    val tag: String, // "Tax", "Payment", "General"
    val dueDate: Long,
    val amount: Double? = null,
    val partyName: String? = null,
    val priority: String = "Medium"
)

class VoiceParserService {

    suspend fun parseVoiceCommand(text: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "{\"error\": \"Gemini API Key missing\"}"
        }
        
        val prompt = """
            Parse the following voice command into a JSON object representing an action for an accounting app.
            Actions can be: 
            1. ADD_REMINDER (fields: title, category, dueDate, amount, partyName, tag, priority)
            2. ADD_PAYMENT_RECEIVE (fields: partyName, amount, dueDate, mode)
            3. ADD_PAYMENT_PAID (fields: partyName, amount, mode)
            4. ADD_LEDGER (fields: partyName, amount, type, mode)
            
            Return ONLY a valid JSON object matching the requested action.
            Command: "$text"
        """.trimIndent()
        
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json")
        )
        
        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "{}"
        } catch (e: Exception) {
            "{\"error\": \"${e.message}\"}"
        }
    }

    suspend fun parseTask(
        text: String,
        existingParties: List<PartyEntity> = emptyList()
    ): ParsedVoiceTask = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val now = Calendar.getInstance()
                val todayStr = SimpleDateFormat("yyyy-MM-dd (EEEE)", Locale.US).format(now.time)
                val currentYear = now.get(Calendar.YEAR)

                val prompt = """
                    You are an intelligent accounting and tax compliance assistant.
                    Today's date is $todayStr. Current year is $currentYear.
                    
                    Extract reminder/task information from the following spoken voice command.
                    
                    Rules:
                    1. "title": Concise task title (e.g. "File GST Return", "Pay Advance Tax", "TDS Filing Deadline", "Collect Payment").
                    2. "category": Choose one from ["GST", "Income Tax", "Payment Receive", "Payment Paid", "Custom", "General"].
                       - Use "GST" for GST, GSTR-1, GSTR-3B, E-Way Bill deadlines.
                       - Use "Income Tax" for Income Tax, ITR, TDS, Advance Tax, Tax Audit deadlines.
                       - Use "Payment Receive" for collecting receivables/payments from clients.
                       - Use "Payment Paid" for paying bills/vendors.
                    3. "tag": Exactly one of ["Tax", "Payment", "General"].
                       - If related to GST, Income Tax, TDS, Advance Tax, Tax Audit or any tax deadline, MUST use "Tax".
                       - If related to receiving or making payments, MUST use "Payment".
                       - Otherwise "General".
                    4. "dueDate": Date formatted strictly as "YYYY-MM-DD". Calculate exact date using today ($todayStr). If month/day spoken without year, use $currentYear. If "tomorrow", calculate tomorrow. If not mentioned, default to tomorrow.
                    5. "partyName": Name of the company, client, person, or business mentioned (e.g. "Sharma Traders", "ABC Corp", "Ramesh"). If no party mentioned, return null.
                    6. "amount": Number if spoken (e.g., 50000.0), else null.
                    7. "priority": "High" (especially for tax deadlines/penalties or urgent dues), "Medium", or "Low".

                    Return ONLY JSON with keys: title, category, tag, dueDate, partyName, amount, priority.
                    Voice Command: "$text"
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(responseMimeType = "application/json")
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val jsonString = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!jsonString.isNullOrBlank()) {
                    val jsonObj = JSONObject(jsonString)
                    val title = jsonObj.optString("title").ifBlank { text.trim() }
                    val category = jsonObj.optString("category", "General")
                    val tag = jsonObj.optString("tag", if (category in listOf("GST", "Income Tax")) "Tax" else "General")
                    val priority = jsonObj.optString("priority", if (tag == "Tax") "High" else "Medium")
                    val partyName = jsonObj.optString("partyName").takeIf { it.isNotBlank() && it != "null" }
                    val amount = if (jsonObj.has("amount") && !jsonObj.isNull("amount")) jsonObj.optDouble("amount") else null

                    val dueDateStr = jsonObj.optString("dueDate")
                    val dueDateMillis = if (dueDateStr.isNotBlank()) {
                        try {
                            val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dueDateStr)
                            val cal = Calendar.getInstance()
                            if (parsedDate != null) {
                                cal.time = parsedDate
                                cal.set(Calendar.HOUR_OF_DAY, 9)
                                cal.set(Calendar.MINUTE, 0)
                                cal.set(Calendar.SECOND, 0)
                                cal.timeInMillis
                            } else {
                                getDefaultDueDate()
                            }
                        } catch (e: Exception) {
                            getDefaultDueDate()
                        }
                    } else {
                        getDefaultDueDate()
                    }

                    return@withContext ParsedVoiceTask(
                        title = title,
                        category = category,
                        tag = tag,
                        dueDate = dueDateMillis,
                        amount = if (amount != null && amount > 0) amount else null,
                        partyName = partyName,
                        priority = priority
                    )
                }
            } catch (e: Exception) {
                // Fall back to local rule-based parsing below
            }
        }

        // Local Rule-Based NLP Parser
        parseLocally(text, existingParties)
    }

    private fun getDefaultDueDate(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun parseLocally(
        text: String,
        existingParties: List<PartyEntity>
    ): ParsedVoiceTask {
        val lower = text.lowercase(Locale.ROOT)

        // 1. Identify Category, Tag, Priority
        val isGst = lower.contains("gst") || lower.contains("gstr") || lower.contains("eway") || lower.contains("e-way")
        val isIncomeTax = lower.contains("income tax") || lower.contains("itr") || lower.contains("advance tax") ||
                lower.contains("tds") || lower.contains("tcs") || lower.contains("tax audit") ||
                lower.contains("tax return") || lower.contains("audit") || lower.contains("tax")
        val isPaymentReceive = lower.contains("receive") || lower.contains("collect") || lower.contains("receiving") ||
                lower.contains("collection") || lower.contains("from client")
        val isPaymentPaid = lower.contains("pay to") || lower.contains("paid to") || lower.contains("payment to") ||
                lower.contains("pay vendor") || lower.contains("pay bill")

        val (category, tag, priority) = when {
            isGst -> Triple("GST", "Tax", "High")
            isIncomeTax -> Triple("Income Tax", "Tax", "High")
            isPaymentReceive -> Triple("Payment Receive", "Payment", "Medium")
            isPaymentPaid -> Triple("Payment Paid", "Payment", "Medium")
            else -> Triple("General", "General", "Medium")
        }

        // 2. Identify Party Name
        var matchedParty: String? = existingParties.firstOrNull { p ->
            p.name.isNotBlank() && lower.contains(p.name.lowercase(Locale.ROOT))
        }?.name

        if (matchedParty == null) {
            val partyRegex = Regex("""\b(?:for|from|to|with)\s+([A-Za-z0-9&'\.\s]+?)(?=\s+(?:by|on|before|due|amount|worth|rs\.?|inr|₹|priority|at|$))""", RegexOption.IGNORE_CASE)
            val match = partyRegex.find(text)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                val stopWords = setOf("me", "us", "today", "tomorrow", "next week", "next month", "tax", "gst", "advance tax", "audit", "payment")
                if (candidate.length in 2..40 && candidate.lowercase(Locale.ROOT) !in stopWords) {
                    matchedParty = candidate
                }
            }
        }

        // 3. Identify Amount
        var amount: Double? = null
        val amountRegex = Regex("""(?:(?:rs\.?|inr|₹|amount\s+of|amount\s+|sum\s+of)\s*)(\d+(?:,\d+)*(?:\.\d+)?)\s*(k|thousand|lakh|lac)?""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(text)
        if (amountMatch != null) {
            val numStr = amountMatch.groupValues[1].replace(",", "")
            val multiplier = when (amountMatch.groupValues.getOrNull(2)?.lowercase(Locale.ROOT)) {
                "k", "thousand" -> 1000.0
                "lakh", "lac" -> 100000.0
                else -> 1.0
            }
            amount = (numStr.toDoubleOrNull() ?: 0.0) * multiplier
        }

        // 4. Identify Due Date
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var dateFound = false

        if (lower.contains("today")) {
            dateFound = true
        } else if (lower.contains("day after tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 2)
            dateFound = true
        } else if (lower.contains("tomorrow")) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            dateFound = true
        } else if (lower.contains("next week")) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
            dateFound = true
        } else {
            val daysOfWeek = mapOf(
                "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY,
                "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY,
                "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY,
                "saturday" to Calendar.SATURDAY
            )
            for ((dayName, dayConst) in daysOfWeek) {
                if (lower.contains("next $dayName") || lower.contains("on $dayName") || lower.contains("by $dayName")) {
                    val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                    var diff = dayConst - currentDay
                    if (diff <= 0) diff += 7
                    cal.add(Calendar.DAY_OF_YEAR, diff)
                    dateFound = true
                    break
                }
            }
        }

        if (!dateFound) {
            val monthMap = mapOf(
                "jan" to Calendar.JANUARY, "january" to Calendar.JANUARY,
                "feb" to Calendar.FEBRUARY, "february" to Calendar.FEBRUARY,
                "mar" to Calendar.MARCH, "march" to Calendar.MARCH,
                "apr" to Calendar.APRIL, "april" to Calendar.APRIL,
                "may" to Calendar.MAY,
                "jun" to Calendar.JUNE, "june" to Calendar.JUNE,
                "jul" to Calendar.JULY, "july" to Calendar.JULY,
                "aug" to Calendar.AUGUST, "august" to Calendar.AUGUST,
                "sep" to Calendar.SEPTEMBER, "sept" to Calendar.SEPTEMBER, "september" to Calendar.SEPTEMBER,
                "oct" to Calendar.OCTOBER, "october" to Calendar.OCTOBER,
                "nov" to Calendar.NOVEMBER, "november" to Calendar.NOVEMBER,
                "dec" to Calendar.DECEMBER, "december" to Calendar.DECEMBER
            )

            // Pattern: 20th September / 30 Sept
            val datePatternA = Regex("""(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(january|february|march|april|may|june|july|august|september|sept|sep|october|oct|november|nov|december|dec|jan|feb|mar|apr|jun|jul|aug)(?:\s+(\d{4}))?""", RegexOption.IGNORE_CASE)
            val matchA = datePatternA.find(text)
            if (matchA != null) {
                val day = matchA.groupValues[1].toIntOrNull() ?: 1
                val monthName = matchA.groupValues[2].lowercase(Locale.ROOT)
                val year = matchA.groupValues.getOrNull(3)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                val monthConst = monthMap[monthName] ?: Calendar.SEPTEMBER
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, monthConst)
                cal.set(Calendar.DAY_OF_MONTH, day)
                dateFound = true
            } else {
                // Pattern: September 20 / Sept 30th
                val datePatternB = Regex("""(january|february|march|april|may|june|july|august|september|sept|sep|october|oct|november|nov|december|dec|jan|feb|mar|apr|jun|jul|aug)\s+(\d{1,2})(?:st|nd|rd|th)?(?:\s+(\d{4}))?""", RegexOption.IGNORE_CASE)
                val matchB = datePatternB.find(text)
                if (matchB != null) {
                    val monthName = matchB.groupValues[1].lowercase(Locale.ROOT)
                    val day = matchB.groupValues[2].toIntOrNull() ?: 1
                    val year = matchB.groupValues.getOrNull(3)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                    val monthConst = monthMap[monthName] ?: Calendar.SEPTEMBER
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, monthConst)
                    cal.set(Calendar.DAY_OF_MONTH, day)
                    dateFound = true
                }
            }
        }

        if (!dateFound) {
            cal.add(Calendar.DAY_OF_YEAR, 1) // default tomorrow
        }

        // 5. Clean Title
        var cleanTitle = text
            .replace(Regex("""^(?:please\s+)?(?:remind\s+me\s+to|add\s+reminder\s+(?:to|for)?|add\s+task\s+(?:to|for)?|create\s+task\s+(?:to|for)?|set\s+reminder\s+(?:to|for)?|new\s+task\s+(?:to|for)?)\s*""", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleanTitle.length < 3) {
            cleanTitle = when (category) {
                "GST" -> "File GST Return"
                "Income Tax" -> "Income Tax Compliance"
                "Payment Receive" -> "Collect Payment"
                "Payment Paid" -> "Make Payment"
                else -> "Voice Reminder"
            }
        } else {
            cleanTitle = cleanTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        return ParsedVoiceTask(
            title = cleanTitle,
            category = category,
            tag = tag,
            dueDate = cal.timeInMillis,
            amount = if (amount != null && amount > 0) amount else null,
            partyName = matchedParty,
            priority = priority
        )
    }

    suspend fun createTaskFromVoice(
        text: String,
        repository: AppRepository,
        alarmScheduler: AlarmScheduler
    ): Result<ReminderEntity> = withContext(Dispatchers.IO) {
        try {
            val existingParties = repository.getAllPartiesList()
            val parsed = parseTask(text, existingParties)

            var partyId: Long? = null
            if (!parsed.partyName.isNullOrBlank()) {
                val pName = parsed.partyName.trim()
                val existing = existingParties.find { it.name.equals(pName, ignoreCase = true) }
                    ?: existingParties.find { it.name.contains(pName, ignoreCase = true) || pName.contains(it.name, ignoreCase = true) }

                if (existing != null) {
                    partyId = existing.id
                } else {
                    val newParty = PartyEntity(
                        name = pName,
                        businessName = pName,
                        mobile = "",
                        email = "",
                        gstin = "",
                        pan = "",
                        address = "",
                        openingBalance = 0.0,
                        balanceType = if (parsed.tag == "Payment" && parsed.category == "Payment Receive") "Receivable" else "Payable"
                    )
                    partyId = repository.insertParty(newParty)
                }
            }

            val reminder = ReminderEntity(
                title = parsed.title,
                category = parsed.category,
                tag = parsed.tag,
                partyId = partyId,
                amount = parsed.amount,
                dueDate = parsed.dueDate,
                dueTime = parsed.dueDate,
                repeatType = "One-time",
                notificationSettings = "On due date",
                priority = parsed.priority,
                status = "Pending",
                notes = "Voice command: \"$text\""
            )

            val insertedId = repository.insertReminder(reminder)
            val finalReminder = reminder.copy(id = insertedId)
            alarmScheduler.scheduleReminder(finalReminder)
            Result.success(finalReminder)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

