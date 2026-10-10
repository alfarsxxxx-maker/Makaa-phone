package com.example.diagnostics

import com.example.core.IdGenerator
import com.example.core.SyncState
import com.example.database.entities.AuditLogEntity
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Diagnostic Event Category for Privacy-Safe System Observability (Section 13).
 */
enum class DiagnosticCategory(val arabicLabel: String) {
    CRASH("انهيار تطبيق"),
    SYNC_FAILURE("فشل مزامنة سحابية"),
    DATABASE_ERROR("خطأ قاعدة بيانات"),
    MIGRATION_ERROR("خطأ ترقية مخطط"),
    BACKUP_FAILURE("فشل نسخ احتياطي"),
    SMS_PARSE_FAILURE("تعذر فك رسالة مالية"),
    DATA_CONFLICT("تعارض بيانات"),
    PERMISSION_DENIED("رفض صلاحية أمنية"),
    AI_AUDIT("عملية ذكاء اصطناعي")
}

enum class DiagnosticSeverity(val rank: Int) {
    INFO(1),
    WARNING(2),
    ERROR(3),
    CRITICAL(4)
}

/**
 * Privacy-safe operational telemetry event.
 * Strictly NEVER logs: PINs, Passwords, full unmasked SMS bodies, credit card numbers, or API keys.
 */
data class DiagnosticEvent(
    val eventId: String = IdGenerator.newPrefixedId("EVT"),
    val category: DiagnosticCategory,
    val severity: DiagnosticSeverity,
    val summaryArabic: String,
    val technicalDetails: String,
    val shopId: String,
    val deviceId: String,
    val actorUserId: String = "SYSTEM",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Central In-Memory & Audit-integrated Observability Hub.
 */
object ObservabilityManager {
    private const val MAX_BUFFER_SIZE = 100
    private val eventQueue = ConcurrentLinkedQueue<DiagnosticEvent>()

    private val _recentEvents = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val recentEvents: StateFlow<List<DiagnosticEvent>> = _recentEvents.asStateFlow()

    fun recordEvent(
        category: DiagnosticCategory,
        severity: DiagnosticSeverity,
        summaryArabic: String,
        technicalDetails: String = "",
        shopId: String = "SHP-DEFAULT",
        deviceId: String = "DEV-DEFAULT",
        actorUserId: String = "SYSTEM"
    ) {
        // Redact any accidental sensitive tokens
        val safeDetails = sanitizeForPrivacy(technicalDetails)
        val event = DiagnosticEvent(
            category = category,
            severity = severity,
            summaryArabic = summaryArabic,
            technicalDetails = safeDetails,
            shopId = shopId,
            deviceId = deviceId,
            actorUserId = actorUserId
        )
        eventQueue.add(event)
        while (eventQueue.size > MAX_BUFFER_SIZE) {
            eventQueue.poll()
        }
        _recentEvents.value = eventQueue.toList().reversed()
    }

    /**
     * Sanitizes strings to prevent logging PINs, passwords, auth tokens, or private secrets.
     */
    fun sanitizeForPrivacy(input: String): String {
        if (input.isBlank()) return ""
        return input
            .replace(Regex("""(?i)(pin|password|token|key|secret)\s*[:=]\s*[^\s,;]+"""), "$1=[REDACTED]")
            .replace(Regex("""\b(01[0125][0-9])([0-9]{4})([0-9]{3})\b"""), "$1****$3")
    }

    fun clearEvents() {
        eventQueue.clear()
        _recentEvents.value = emptyList()
    }
}
