package com.example.core

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Collision-safe identifier generator and cryptographic integrity utilities for Mekka Phone.
 */
object IdGenerator {
    fun newUuid(): String = UUID.randomUUID().toString()

    fun newPrefixedId(prefix: String): String =
        "${prefix.uppercase()}-${UUID.randomUUID().toString().lowercase()}"

    fun newReferenceNumber(prefix: String, timestampMillis: Long = System.currentTimeMillis()): String {
        val tail = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        return "$prefix-${timestampMillis % 1_000_000_000L}-$tail"
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Supported synchronization states for Local-First operations (Requirement 12 & 19).
 * Uses truthful Arabic states:
 * - محفوظ محليًا
 * - في انتظار المزامنة
 * - تمت المزامنة
 * - فشلت المزامنة
 * - تعارض يحتاج مراجعة
 */
enum class SyncState(val arabicLabel: String) {
    PENDING("في انتظار المزامنة"),
    SYNCING("في انتظار المزامنة"),
    RETRYING("في انتظار المزامنة"),
    SYNCED("تمت المزامنة"),
    FAILED("فشلت المزامنة"),
    CONFLICT("تعارض يحتاج مراجعة");

    fun truthfulLabel(isCloudBackendConfigured: Boolean = false): String = when (this) {
        PENDING -> if (isCloudBackendConfigured) "في انتظار المزامنة" else "محفوظ محليًا"
        SYNCING, RETRYING -> "في انتظار المزامنة"
        SYNCED -> "تمت المزامنة"
        FAILED -> "فشلت المزامنة"
        CONFLICT -> "تعارض يحتاج مراجعة"
    }
}

/**
 * Centralized error model translating technical exceptions into clear Arabic user messages
 * while preserving technical details for Diagnostics (Part 3 Rule 16).
 */
data class UserFacingError(
    val userMessageArabic: String,
    val technicalCode: String,
    val technicalDetails: String,
    val technicalExceptionClass: String = technicalCode,
    val timestampMillis: Long = System.currentTimeMillis()
)

typealias FriendlyAppError = UserFacingError

object CentralizedErrorMapper {

    fun fromAppError(error: AppResult.Error): UserFacingError = toUserFacingError(
        rawMessage = error.message,
        errorCode = error.code,
        cause = error.cause
    )

    fun fromAppError(
        rawMessage: String?,
        errorCode: String = "OPERATION_ERROR",
        cause: Throwable? = null
    ): UserFacingError = toUserFacingError(
        rawMessage = rawMessage,
        errorCode = errorCode,
        cause = cause
    )

    fun toUserFacingError(
        rawMessage: String?,
        errorCode: String = "OPERATION_ERROR",
        cause: Throwable? = null
    ): UserFacingError {
        val combinedTech = buildString {
            append("Code=$errorCode")
            if (!rawMessage.isNullOrBlank()) append(" | Message=$rawMessage")
            if (cause != null) append(" | Cause=${cause::class.java.simpleName}: ${cause.message}")
        }
        val combinedCheck = "$errorCode ${rawMessage.orEmpty()} ${cause?.javaClass?.simpleName.orEmpty()} ${cause?.message.orEmpty()}"

        val friendlyArabic = when {
            combinedCheck.contains("LEDGER_IMMUTABLE", ignoreCase = true) ->
                "لا يمكن حذف أو تعديل قيد مالي معتمد. لتصحيح العملية استخدم إجراء «قيد عكسي»."
            combinedCheck.contains("INVENTORY_IMMUTABLE", ignoreCase = true) ||
                combinedCheck.contains("AUDIT_IMMUTABLE", ignoreCase = true) ->
                "لا يمكن حذف السجلات التاريخية للمخزون أو التدقيق حفاظاً على سلامة البيانات."
            combinedCheck.contains("SQLiteConstraintException", ignoreCase = true) ||
                combinedCheck.contains("FOREIGN KEY constraint failed", ignoreCase = true) ->
                "تعذر حفظ العملية لأن البيانات مرتبطة بسجل آخر."
            combinedCheck.contains("UNIQUE constraint failed", ignoreCase = true) ->
                "تعذر الحفظ لأن هذا البيان (مثل رقم الهاتف أو الكود أو IMEI) مسجل مسبقاً."
            errorCode == "PERMISSION_DENIED" ->
                rawMessage?.takeIf { it.contains("صلاحية") }
                    ?: "عفواً، لا تملك الصلاحية اللازمة لتنفيذ هذه العملية."
            errorCode == "CROSS_SHOP_ACCESS_DENIED" || errorCode == "WRONG_SHOP_MEMBERSHIP" ->
                "رفض أمني: لا يمكن الوصول إلى بيانات متجر آخر غير المتجر الحالي."
            errorCode == "UNAUTHENTICATED_SESSION" || errorCode == "SESSION_EXPIRED" ->
                "يرجى تسجيل الدخول برمز PIN السري للمتابعة."
            errorCode == "AUTH_ACCOUNT_LOCKED_RATE_LIMIT" ->
                rawMessage ?: "تم قفل الحساب مؤقتاً بسبب تكرار إدخال رمز PIN غير صحيح."
            errorCode == "CLOUD_BACKEND_NOT_CONFIGURED" ->
                "البيانات محفوظة محليًا بأمان (في انتظار المزامنة عند تهيئة الخادم السحابي)."
            !rawMessage.isNullOrBlank() &&
                !rawMessage.contains("Exception") &&
                !rawMessage.contains("SQLite") -> rawMessage
            else -> "تعذر إتمام العملية حالياً. يرجى مراجعة البيانات والمحاولة مرة أخرى."
        }

        return UserFacingError(
            userMessageArabic = friendlyArabic,
            technicalCode = errorCode,
            technicalDetails = combinedTech
        )
    }

    fun mapException(throwable: Throwable, fallbackArabic: String = "تعذر إتمام العملية"): UserFacingError {
        return toUserFacingError(
            rawMessage = throwable.message ?: fallbackArabic,
            errorCode = throwable::class.java.simpleName,
            cause = throwable
        )
    }

    fun mapExceptionToArabic(throwable: Throwable, fallbackArabic: String = "تعذر إتمام العملية"): String {
        return toUserFacingError(
            rawMessage = throwable.message ?: fallbackArabic,
            errorCode = throwable::class.java.simpleName,
            cause = throwable
        ).userMessageArabic
    }
}

/**
 * Standard synchronization metadata embedded across operational entities.
 */
data class SyncMetadata(
    val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Generic domain result wrapper for atomic operations and validation.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val message: String, val code: String = "OPERATION_ERROR", val cause: Throwable? = null) : AppResult<Nothing>()
}
