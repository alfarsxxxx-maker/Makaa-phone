package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.CentralizedErrorMapper
import com.example.financial.Money
import com.example.ui.theme.FinancialInfo
import com.example.ui.theme.FinancialNegative
import com.example.ui.theme.FinancialPositive
import com.example.ui.theme.FinancialWarning
import com.example.ui.theme.LedgerMonoFontFamily
import com.example.ui.theme.MekkaCrimsonError
import com.example.ui.theme.MekkaEmeraldGreen
import com.example.ui.theme.SemanticCancelledColor
import com.example.ui.theme.SemanticCancelledContainerDark
import com.example.ui.theme.SemanticCancelledContainerLight
import com.example.ui.theme.SemanticErrorColor
import com.example.ui.theme.SemanticErrorContainerDark
import com.example.ui.theme.SemanticErrorContainerLight
import com.example.ui.theme.SemanticInfoColor
import com.example.ui.theme.SemanticInfoContainerDark
import com.example.ui.theme.SemanticInfoContainerLight
import com.example.ui.theme.SemanticLoadingColor
import com.example.ui.theme.SemanticLoadingContainerDark
import com.example.ui.theme.SemanticLoadingContainerLight
import com.example.ui.theme.SemanticOfflineColor
import com.example.ui.theme.SemanticOfflineContainerDark
import com.example.ui.theme.SemanticOfflineContainerLight
import com.example.ui.theme.SemanticPendingColor
import com.example.ui.theme.SemanticPendingContainerDark
import com.example.ui.theme.SemanticPendingContainerLight
import com.example.ui.theme.SemanticProcessingColor
import com.example.ui.theme.SemanticProcessingContainerDark
import com.example.ui.theme.SemanticProcessingContainerLight
import com.example.ui.theme.SemanticSuccessColor
import com.example.ui.theme.SemanticSuccessContainerDark
import com.example.ui.theme.SemanticSuccessContainerLight
import com.example.ui.theme.SemanticSyncFailedColor
import com.example.ui.theme.SemanticSyncFailedContainerDark
import com.example.ui.theme.SemanticSyncFailedContainerLight
import com.example.ui.theme.SemanticSyncedColor
import com.example.ui.theme.SemanticSyncedContainerDark
import com.example.ui.theme.SemanticSyncedContainerLight
import com.example.ui.theme.SemanticSyncingColor
import com.example.ui.theme.SemanticSyncingContainerDark
import com.example.ui.theme.SemanticSyncingContainerLight
import com.example.ui.theme.SemanticWarningColor
import com.example.ui.theme.SemanticWarningContainerDark
import com.example.ui.theme.SemanticWarningContainerLight
import kotlinx.coroutines.delay

// ============================================================================
// MEKKA PHONE V3.2 — INTELLIGENT UX FEEDBACK & CONFIRMATION FOUNDATION
// ============================================================================

/**
 * Standard Semantic Status Model (Section 3 & Section 4).
 * Every state pairs ICON + TEXT + COLOR so the UI never relies on color alone.
 */
enum class UxSemanticStatus(
    val arabicLabel: String,
    val defaultPersistenceNoteArabic: String,
    val icon: ImageVector,
    val primaryColor: Color,
    val lightContainerColor: Color,
    val darkContainerColor: Color
) {
    IDLE(
        arabicLabel = "جاهز",
        defaultPersistenceNoteArabic = "النظام جاهز لتنفيذ العمليات",
        icon = Icons.Default.Info,
        primaryColor = SemanticInfoColor,
        lightContainerColor = SemanticInfoContainerLight,
        darkContainerColor = SemanticInfoContainerDark
    ),
    LOADING(
        arabicLabel = "جاري التحميل...",
        defaultPersistenceNoteArabic = "جاري قراءة البيانات الفعلية من قاعدة البيانات",
        icon = Icons.Default.HourglassTop,
        primaryColor = SemanticLoadingColor,
        lightContainerColor = SemanticLoadingContainerLight,
        darkContainerColor = SemanticLoadingContainerDark
    ),
    PROCESSING(
        arabicLabel = "جارٍ التنفيذ...",
        defaultPersistenceNoteArabic = "جاري تنفيذ المعاملة الذرية وتوثيق القيد المحاسبي",
        icon = Icons.Default.HourglassTop,
        primaryColor = SemanticProcessingColor,
        lightContainerColor = SemanticProcessingContainerLight,
        darkContainerColor = SemanticProcessingContainerDark
    ),
    SUCCESS(
        arabicLabel = "تم بنجاح",
        defaultPersistenceNoteArabic = "تم حفظ العملية على الجهاز بأمان",
        icon = Icons.Default.CheckCircle,
        primaryColor = SemanticSuccessColor,
        lightContainerColor = SemanticSuccessContainerLight,
        darkContainerColor = SemanticSuccessContainerDark
    ),
    WARNING(
        arabicLabel = "تحتاج مراجعة",
        defaultPersistenceNoteArabic = "يرجى مراجعة التنبيه قبل المتابعة",
        icon = Icons.Default.WarningAmber,
        primaryColor = SemanticWarningColor,
        lightContainerColor = SemanticWarningContainerLight,
        darkContainerColor = SemanticWarningContainerDark
    ),
    ERROR(
        arabicLabel = "تعذر التنفيذ",
        defaultPersistenceNoteArabic = "لم يتم تغيير البيانات. حاول مرة أخرى.",
        icon = Icons.Default.ErrorOutline,
        primaryColor = SemanticErrorColor,
        lightContainerColor = SemanticErrorContainerLight,
        darkContainerColor = SemanticErrorContainerDark
    ),
    INFO(
        arabicLabel = "معلومة",
        defaultPersistenceNoteArabic = "البيانات محفوظة ومحدثة",
        icon = Icons.Default.Info,
        primaryColor = SemanticInfoColor,
        lightContainerColor = SemanticInfoContainerLight,
        darkContainerColor = SemanticInfoContainerDark
    ),
    PENDING(
        arabicLabel = "في انتظار المزامنة",
        defaultPersistenceNoteArabic = "تم حفظ العملية على الجهاز وسيتم مزامنتها عند توفر الاتصال",
        icon = Icons.Default.Schedule,
        primaryColor = SemanticPendingColor,
        lightContainerColor = SemanticPendingContainerLight,
        darkContainerColor = SemanticPendingContainerDark
    ),
    OFFLINE(
        arabicLabel = "في انتظار الاتصال",
        defaultPersistenceNoteArabic = "العمل محلي آمن — سيتم مزامنة العمليات عند توفر الاتصال",
        icon = Icons.Default.CloudOff,
        primaryColor = SemanticOfflineColor,
        lightContainerColor = SemanticOfflineContainerLight,
        darkContainerColor = SemanticOfflineContainerDark
    ),
    SYNCING(
        arabicLabel = "قيد المزامنة",
        defaultPersistenceNoteArabic = "جاري إرسال ومصادقة العمليات مع الخادم السحابي",
        icon = Icons.Default.CloudSync,
        primaryColor = SemanticSyncingColor,
        lightContainerColor = SemanticSyncingContainerLight,
        darkContainerColor = SemanticSyncingContainerDark
    ),
    SYNCED(
        arabicLabel = "تمت المزامنة",
        defaultPersistenceNoteArabic = "تمت مصادقة وحفظ جميع العمليات على الخادم السحابي",
        icon = Icons.Default.CloudDone,
        primaryColor = SemanticSyncedColor,
        lightContainerColor = SemanticSyncedContainerLight,
        darkContainerColor = SemanticSyncedContainerDark
    ),
    SYNC_FAILED(
        arabicLabel = "فشلت المزامنة",
        defaultPersistenceNoteArabic = "تعذر الاتصال بالخادم. تم حفظ العملية محليًا وستتم محاولة المزامنة لاحقًا.",
        icon = Icons.Default.SyncProblem,
        primaryColor = SemanticSyncFailedColor,
        lightContainerColor = SemanticSyncFailedContainerLight,
        darkContainerColor = SemanticSyncFailedContainerDark
    ),
    CANCELLED(
        arabicLabel = "تم الإلغاء",
        defaultPersistenceNoteArabic = "تم إلغاء العملية بناءً على طلب المستخدم ولم تتغير البيانات",
        icon = Icons.Default.Block,
        primaryColor = SemanticCancelledColor,
        lightContainerColor = SemanticCancelledContainerLight,
        darkContainerColor = SemanticCancelledContainerDark
    );

    @Composable
    fun containerColor(): Color =
        if (isSystemInDarkTheme()) darkContainerColor else lightContainerColor
}

/**
 * Infers the appropriate semantic icon from a badge text or color so every badge
 * automatically complies with Section 4: ICON + TEXT + COLOR (never color alone).
 */
fun inferSemanticIconForBadge(text: String, color: Color): ImageVector {
    val normalized = text.trim()
    return when {
        normalized.contains("تمت المزامنة") -> Icons.Default.CloudDone
        normalized.contains("قيد المزامنة") || normalized.contains("جاري المزامنة") -> Icons.Default.CloudSync
        normalized.contains("فشلت المزامنة") || normalized.contains("تعارض") -> Icons.Default.SyncProblem
        normalized.contains("غير متصل") || normalized.contains("انتظار الاتصال") || normalized.contains("محفوظ محليًا") -> Icons.Default.CloudOff
        normalized.contains("انتظار") || normalized.contains("معلق") -> Icons.Default.Schedule
        normalized.contains("إلغاء") || normalized.contains("ملغى") -> Icons.Default.Block
        normalized.contains("تحذير") || normalized.contains("مراجعة") || normalized.contains("منخفض") || normalized.contains("مديونية") || normalized.contains("آجل") -> Icons.Default.WarningAmber
        normalized.contains("خطأ") || normalized.contains("فشل") || normalized.contains("حرج") || normalized.contains("نفد") || normalized.contains("مرتجع") || normalized.contains("عجز") -> Icons.Default.ErrorOutline
        normalized.contains("تم") || normalized.contains("نجاح") || normalized.contains("مسدد") || normalized.contains("متاح") || normalized.contains("معتمد") || normalized.contains("نشط") || normalized.contains("سليم") -> Icons.Default.CheckCircle
        color == FinancialNegative || color == SemanticErrorColor -> Icons.Default.ErrorOutline
        color == FinancialWarning || color == SemanticWarningColor -> Icons.Default.WarningAmber
        color == FinancialPositive || color == SemanticSuccessColor || color == MekkaEmeraldGreen -> Icons.Default.CheckCircle
        else -> Icons.Default.Info
    }
}

/**
 * Central UX Operation State Model (Sections 2, 3, 7, 10, 13).
 * Answers the 7 core user questions for every operation:
 * 1. ماذا يحدث الآن؟
 * 2. ماذا حدث؟
 * 3. ماذا تغير؟
 * 4. هل العملية نجحت؟
 * 5. هل البيانات محفوظة؟
 * 6. هل توجد مشكلة؟
 * 7. ماذا يمكنني أن أفعل بعد ذلك؟
 */
sealed class UxOperationState {
    abstract val semanticStatus: UxSemanticStatus

    data object Idle : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.IDLE
    }

    data class Loading(
        val whatIsHappeningArabic: String = "جاري تحميل البيانات..."
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.LOADING
    }

    data class Processing(
        val whatIsHappeningArabic: String,
        val operationKey: String = ""
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.PROCESSING
    }

    /**
     * Real Progress Indicator State (Section 13).
     * Never uses fake percentages: only calculates a percentage or fraction when [totalSteps] > 0.
     */
    data class Progress(
        val whatIsHappeningArabic: String,
        val currentStep: Int,
        val totalSteps: Int,
        val displayAsFraction: Boolean = false
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.PROCESSING

        val hasMeasurableProgress: Boolean
            get() = totalSteps > 0 && currentStep >= 0

        val progressFraction: Float
            get() = if (hasMeasurableProgress) {
                (currentStep.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
            } else 0f

        val progressPercentage: Int
            get() = if (hasMeasurableProgress) {
                ((currentStep * 100) / totalSteps).coerceIn(0, 100)
            } else 0

        val formattedProgressArabic: String
            get() = when {
                !hasMeasurableProgress -> ""
                displayAsFraction -> "$currentStep / $totalSteps"
                else -> "$progressPercentage%"
            }
    }

    data class Success(
        val whatHappenedArabic: String,
        val whatChangedArabic: String = "",
        val dataPersistenceNoteArabic: String = "تم حفظ العملية على الجهاز وسيتم مزامنتها عند توفر الاتصال",
        val nextActionLabelArabic: String? = null,
        val onNextAction: (() -> Unit)? = null
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.SUCCESS
    }

    data class Warning(
        val messageArabic: String,
        val detailsArabic: String = "",
        val actionLabelArabic: String? = null,
        val onAction: (() -> Unit)? = null
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.WARNING
    }

    data class Error(
        val problemArabic: String,
        val causeArabic: String = "",
        val dataSafetyNoteArabic: String = "لم يتم تغيير البيانات.",
        val actionLabelArabic: String = "حاول مرة أخرى",
        val onAction: (() -> Unit)? = null,
        val technicalDiagnostics: String? = null
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.ERROR

        val fullUserMessageArabic: String
            get() = buildString {
                append(problemArabic.trim())
                if (causeArabic.isNotBlank() && !problemArabic.contains(causeArabic)) {
                    if (!endsWith(".") && !endsWith("،")) append(" ")
                    append(causeArabic.trim())
                }
                if (dataSafetyNoteArabic.isNotBlank() && !problemArabic.contains(dataSafetyNoteArabic)) {
                    append("\n")
                    append(dataSafetyNoteArabic.trim())
                }
            }
    }

    data class Pending(
        val messageArabic: String = "تم حفظ العملية على الجهاز\nوسيتم مزامنتها عند توفر الاتصال",
        val pendingCount: Int = 1
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.PENDING
    }

    data class Offline(
        val messageArabic: String = "في انتظار الاتصال — العمليات محفوظة محلياً بأمان"
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.OFFLINE
    }

    data class Syncing(
        val messageArabic: String = "قيد المزامنة مع الخادم...",
        val currentCount: Int = 0,
        val totalCount: Int = 0
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.SYNCING
    }

    data class Synced(
        val messageArabic: String = "تمت المزامنة ومصادقة الخادم بنجاح",
        val syncedCount: Int = 0
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.SYNCED
    }

    data class SyncFailed(
        val messageArabic: String = "تعذر الاتصال بالخادم.\nتم حفظ العملية محليًا وستتم محاولة المزامنة لاحقًا.",
        val onRetry: (() -> Unit)? = null
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.SYNC_FAILED
    }

    data class Cancelled(
        val messageArabic: String = "تم إلغاء العملية. لم يتم تغيير البيانات."
    ) : UxOperationState() {
        override val semanticStatus: UxSemanticStatus = UxSemanticStatus.CANCELLED
    }
}

// ============================================================================
// SECTIONS 8 & 9: ERROR SANITIZATION & ACTIONABLE ERROR ENGINE
// المشكلة + السبب + الإجراء + طمأنة عدم تغيير البيانات
// ============================================================================

data class ActionableErrorSpec(
    val problemArabic: String,
    val causeArabic: String,
    val dataSafetyArabic: String,
    val actionLabelArabic: String,
    val technicalDetailForDiagnostics: String
) {
    val combinedUserFacingArabic: String
        get() = buildString {
            append(problemArabic)
            if (causeArabic.isNotBlank() && !problemArabic.contains(causeArabic)) {
                append(" ")
                append(causeArabic)
            }
            if (dataSafetyArabic.isNotBlank() && !problemArabic.contains(dataSafetyArabic)) {
                append("\n")
                append(dataSafetyArabic)
            }
        }

    val persistenceNoteArabic: String
        get() = if (causeArabic.contains("تم حفظ العملية محليًا")) causeArabic else dataSafetyArabic

    val technicalDiagnosticDetails: String
        get() = technicalDetailForDiagnostics
}

object UxErrorSanitizer {
    private val technicalLeakTokens = listOf(
        "SQLite",
        "ConstraintException",
        "NullPointerException",
        "IllegalArgumentException",
        "IllegalStateException",
        "RuntimeException",
        "IOException",
        "SocketTimeoutException",
        "UnknownHostException",
        "HTTP 500",
        "HTTP 404",
        "HTTP 401",
        "HTTP 403",
        "Exception:",
        "java.lang.",
        "java.io.",
        "java.net.",
        "kotlin.",
        "android.database.",
        "androidx.room.",
        "at com.example."
    )

    fun containsTechnicalLeak(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return technicalLeakTokens.any { token -> text.contains(token, ignoreCase = true) }
    }

    /**
     * Converts any raw error string or exception into a structured ActionableErrorSpec
     * following Section 8 & Section 9 (Problem + Cause + Data Safety + Action).
     */
    fun toActionableError(
        rawMessage: String?,
        operationContextArabic: String = "حفظ العملية",
        cause: Throwable? = null
    ): ActionableErrorSpec {
        val rawCombined = "${rawMessage.orEmpty()} ${cause?.javaClass?.simpleName.orEmpty()} ${cause?.message.orEmpty()}".trim()

        return when {
            // 1. Stock / Quantity insufficient (Section 9 Example 1)
            rawCombined.contains("الرصيد غير كاف", ignoreCase = true) ||
                rawCombined.contains("الرصيد المخزني غير كاف", ignoreCase = true) ||
                rawCombined.contains("غير كافٍ", ignoreCase = true) ||
                rawCombined.contains("الكمية المطلوبة غير متوفرة", ignoreCase = true) ||
                rawCombined.contains("Insufficient stock", ignoreCase = true) ||
                rawCombined.contains("INSUFFICIENT_STOCK", ignoreCase = true) -> {
                ActionableErrorSpec(
                    problemArabic = "تعذر حفظ البيع لأن الكمية المطلوبة غير متوفرة.",
                    causeArabic = rawMessage?.takeIf { !containsTechnicalLeak(it) } ?: "الرصيد المتاح بالمخزون أقل من الكمية المطلوبة.",
                    dataSafetyArabic = "لم يتم تغيير البيانات.",
                    actionLabelArabic = "مراجعة الكمية",
                    technicalDetailForDiagnostics = rawCombined
                )
            }

            // 2. Network / Cloud Sync unavailable (Section 9 Example 2)
            rawCombined.contains("تعذر الاتصال بالخادم", ignoreCase = true) ||
                rawCombined.contains("Network", ignoreCase = true) ||
                rawCombined.contains("Socket", ignoreCase = true) ||
                rawCombined.contains("UnknownHost", ignoreCase = true) ||
                rawCombined.contains("HTTP ", ignoreCase = true) ||
                rawCombined.contains("SYNC_FAILED", ignoreCase = true) -> {
                ActionableErrorSpec(
                    problemArabic = "تعذر الاتصال بالخادم.",
                    causeArabic = "تم حفظ العملية محليًا وستتم محاولة المزامنة لاحقًا.",
                    dataSafetyArabic = "جميع بياناتك محفوظة بأمان على الجهاز.",
                    actionLabelArabic = "إعادة المحاولة",
                    technicalDetailForDiagnostics = rawCombined
                )
            }

            // 3. IMEI / Serial validation error
            rawCombined.contains("IMEI", ignoreCase = true) ||
                rawCombined.contains("سيريال", ignoreCase = true) ||
                rawCombined.contains("SERIAL", ignoreCase = true) -> {
                val cleanCause = rawMessage?.takeIf { !containsTechnicalLeak(it) }
                    ?: "رقم الـ IMEI غير مكتمل أو مسجل مسبقاً في المخزون."
                ActionableErrorSpec(
                    problemArabic = "تعذر $operationContextArabic بسبب بيانات السيريال (IMEI).",
                    causeArabic = cleanCause,
                    dataSafetyArabic = "لم يتم تغيير البيانات.",
                    actionLabelArabic = "مراجعة السيريال",
                    technicalDetailForDiagnostics = rawCombined
                )
            }

            // 4. Cash Drawer insufficient funds
            rawCombined.contains("رصيد الخزينة", ignoreCase = true) ||
                rawCombined.contains("السيولة النقدية", ignoreCase = true) ||
                rawCombined.contains("INSUFFICIENT_CASH", ignoreCase = true) -> {
                ActionableErrorSpec(
                    problemArabic = "تعذر $operationContextArabic لعدم كفاية الرصيد النقدي بالخزينة.",
                    causeArabic = rawMessage?.takeIf { !containsTechnicalLeak(it) } ?: "المبلغ المطلوب صرفه أكبر من النقدية المتاحة بالدرج.",
                    dataSafetyArabic = "لم يتم تغيير البيانات.",
                    actionLabelArabic = "مراجعة المبلغ",
                    technicalDetailForDiagnostics = rawCombined
                )
            }

            // 5. Wallet balance or daily/monthly limit
            rawCombined.contains("سقف المحفظة", ignoreCase = true) ||
                rawCombined.contains("رصيد المحفظة", ignoreCase = true) ||
                rawCombined.contains("WALLET_LIMIT", ignoreCase = true) -> {
                ActionableErrorSpec(
                    problemArabic = "تعذر تنفيذ عملية المحفظة.",
                    causeArabic = rawMessage?.takeIf { !containsTechnicalLeak(it) } ?: "المبلغ يتجاوز الرصيد المتاح أو السقف اليومي/الشهري للمحفظة.",
                    dataSafetyArabic = "لم يتم تغيير البيانات.",
                    actionLabelArabic = "تعديل المبلغ أو المحفظة",
                    technicalDetailForDiagnostics = rawCombined
                )
            }

            // 6. SQLite / Unique / Foreign Key Constraints (Section 8)
            rawCombined.contains("UNIQUE constraint", ignoreCase = true) -> {
                ActionableErrorSpec(
                    problemArabic = "تعذر $operationContextArabic لأن البيان مسجل مسبقاً.",
                    causeArabic = "تأكد من عدم تكرار الكود (SKU) أو رقم الهاتف أو السيريال.",
                    dataSafetyArabic = "لم يتم تغيير البيانات.",
                    actionLabelArabic = "مراجعة البيانات",
                    technicalDetailForDiagnostics = rawCombined
                )
            }

            rawCombined.contains("SQLite", ignoreCase = true) ||
                rawCombined.contains("ConstraintException", ignoreCase = true) ||
                rawCombined.contains("FOREIGN KEY", ignoreCase = true) -> {
                ActionableErrorSpec(
                    problemArabic = "تعذر حفظ العملية.",
                    causeArabic = "البيانات المدخلة تتعارض مع قيد مرتبط في النظام.",
                    dataSafetyArabic = "لم يتم تغيير البيانات. حاول مرة أخرى.",
                    actionLabelArabic = "إعادة المحاولة",
                    technicalDetailForDiagnostics = rawCombined
                )
            }

            // 7. Clean domain validation message without technical leaks
            !rawMessage.isNullOrBlank() && !containsTechnicalLeak(rawMessage) -> {
                ActionableErrorSpec(
                    problemArabic = rawMessage.trim(),
                    causeArabic = "",
                    dataSafetyArabic = "لم يتم تغيير البيانات.",
                    actionLabelArabic = "مراجعة البيانات",
                    technicalDetailForDiagnostics = rawCombined
                )
            }

            // 8. Safe Fallback (Section 8 exact wording)
            else -> {
                ActionableErrorSpec(
                    problemArabic = "تعذر حفظ العملية.",
                    causeArabic = "",
                    dataSafetyArabic = "لم يتم تغيير البيانات. حاول مرة أخرى.",
                    actionLabelArabic = "إعادة المحاولة",
                    technicalDetailForDiagnostics = rawCombined
                )
            }
        }
    }

    fun sanitizeUserMessage(rawMessage: String?): String {
        if (rawMessage.isNullOrBlank()) {
            return "تعذر حفظ العملية.\nلم يتم تغيير البيانات.\nحاول مرة أخرى."
        }
        if (containsTechnicalLeak(rawMessage)) {
            return toActionableError(rawMessage).combinedUserFacingArabic
        }
        return rawMessage
    }

    fun sanitizeForUser(rawMessage: String?): String = sanitizeUserMessage(rawMessage)

    fun toActionableError(
        rawMessage: String?,
        operationNameArabic: String,
        localSaveSucceeded: Boolean = false
    ): ActionableErrorSpec {
        val base = toActionableError(rawMessage = rawMessage, operationContextArabic = operationNameArabic, cause = null)
        return if (localSaveSucceeded && !base.causeArabic.contains("تم حفظ العملية محليًا")) {
            base.copy(causeArabic = "تم حفظ العملية محليًا وستتم محاولة المزامنة لاحقًا.")
        } else {
            base
        }
    }
}

// ============================================================================
// SECTION 4 & 5: REUSABLE FEEDBACK COMPONENTS (ICON + TEXT + COLOR)
// ============================================================================

/**
 * Reusable Semantic Status Badge (Section 4: ICON + TEXT + COLOR).
 */
@Composable
fun SemanticStatusBadge(
    status: UxSemanticStatus,
    textOverride: String? = null,
    modifier: Modifier = Modifier
) {
    val label = textOverride ?: status.arabicLabel
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = status.containerColor(),
        border = BorderStroke(1.dp, status.primaryColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = status.icon,
                contentDescription = label,
                tint = status.primaryColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = status.primaryColor
            )
        }
    }
}

/**
 * Reusable Inline Status Banner for persistent states (Section 5: Inline Status).
 * Examples: «قيد المزامنة», «في انتظار الاتصال», «تحتاج مراجعة».
 * Always uses ICON + TEXT + COLOR + optional action button.
 */
@Composable
fun MekkaInlineStatusBanner(
    status: UxSemanticStatus,
    titleArabic: String = status.arabicLabel,
    subtitleArabic: String = status.defaultPersistenceNoteArabic,
    actionLabelArabic: String? = null,
    onActionClick: (() -> Unit)? = null,
    testTag: String = "inline_status_banner_${status.name.lowercase()}",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = status.containerColor(),
        border = BorderStroke(1.dp, status.primaryColor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (status == UxSemanticStatus.LOADING || status == UxSemanticStatus.PROCESSING || status == UxSemanticStatus.SYNCING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.2.dp,
                        color = status.primaryColor
                    )
                } else {
                    Icon(
                        imageVector = status.icon,
                        contentDescription = titleArabic,
                        tint = status.primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = titleArabic,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = status.primaryColor
                    )
                    if (subtitleArabic.isNotBlank()) {
                        Text(
                            text = subtitleArabic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            if (!actionLabelArabic.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onActionClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, status.primaryColor),
                    modifier = Modifier.testTag("${testTag}_action_btn")
                ) {
                    Text(
                        text = actionLabelArabic,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = status.primaryColor
                    )
                }
            }
        }
    }
}

/**
 * Central UX Operation State Banner / Snackbar replacement (Sections 2, 5, 6, 7, 8, 9, 10, 13).
 * Renders Loading, Processing, Real Progress (e.g. 65% or 320 / 850), Success, Warning, Actionable Error,
 * Pending, Offline, Syncing, Synced, SyncFailed, and Cancelled states.
 */
@Composable
fun MekkaCentralUxFeedbackHost(
    operationState: UxOperationState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissSuccessMillis: Long = 5500L
) {
    if (operationState is UxOperationState.Idle) return

    val status = operationState.semanticStatus
    LaunchedEffect(operationState) {
        if ((operationState is UxOperationState.Success ||
                operationState is UxOperationState.Synced ||
                operationState is UxOperationState.Cancelled) &&
            autoDismissSuccessMillis > 0L
        ) {
            delay(autoDismissSuccessMillis)
            onDismiss()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("central_ux_feedback_host_${status.name.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        color = status.containerColor(),
        shadowElevation = 2.dp,
        border = BorderStroke(1.2.dp, status.primaryColor.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(status.primaryColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    when (operationState) {
                        is UxOperationState.Loading,
                        is UxOperationState.Processing,
                        is UxOperationState.Progress,
                        is UxOperationState.Syncing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.2.dp,
                                color = status.primaryColor
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = status.icon,
                                contentDescription = status.arabicLabel,
                                tint = status.primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SemanticStatusBadge(status = status)
                        if (operationState is UxOperationState.Progress && operationState.hasMeasurableProgress) {
                            Text(
                                text = operationState.formattedProgressArabic,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = LedgerMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = status.primaryColor,
                                modifier = Modifier.testTag("ux_progress_value_label")
                            )
                        }
                    }

                    val primaryMessage = when (operationState) {
                        is UxOperationState.Loading -> operationState.whatIsHappeningArabic
                        is UxOperationState.Processing -> operationState.whatIsHappeningArabic
                        is UxOperationState.Progress -> operationState.whatIsHappeningArabic
                        is UxOperationState.Success -> operationState.whatHappenedArabic
                        is UxOperationState.Warning -> operationState.messageArabic
                        is UxOperationState.Error -> operationState.fullUserMessageArabic
                        is UxOperationState.Pending -> operationState.messageArabic
                        is UxOperationState.Offline -> operationState.messageArabic
                        is UxOperationState.Syncing -> operationState.messageArabic
                        is UxOperationState.Synced -> operationState.messageArabic
                        is UxOperationState.SyncFailed -> operationState.messageArabic
                        is UxOperationState.Cancelled -> operationState.messageArabic
                        UxOperationState.Idle -> ""
                    }

                    Text(
                        text = primaryMessage,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val secondaryPersistenceOrChange = when (operationState) {
                        is UxOperationState.Success -> buildString {
                            if (operationState.whatChangedArabic.isNotBlank()) {
                                append(operationState.whatChangedArabic)
                                append(" • ")
                            }
                            append(operationState.dataPersistenceNoteArabic)
                        }
                        is UxOperationState.Warning -> operationState.detailsArabic
                        else -> ""
                    }

                    if (secondaryPersistenceOrChange.isNotBlank()) {
                        Text(
                            text = secondaryPersistenceOrChange,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action Button for Retry / Review (Section 9)
                val (actionLabel, actionCallback) = when (operationState) {
                    is UxOperationState.Error -> operationState.actionLabelArabic to operationState.onAction
                    is UxOperationState.SyncFailed -> "إعادة المحاولة" to operationState.onRetry
                    is UxOperationState.Warning -> operationState.actionLabelArabic to operationState.onAction
                    is UxOperationState.Success -> operationState.nextActionLabelArabic to operationState.onNextAction
                    else -> null to null
                }

                if (!actionLabel.isNullOrBlank() && actionCallback != null) {
                    OutlinedButton(
                        onClick = actionCallback,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, status.primaryColor),
                        modifier = Modifier.testTag("central_ux_action_button")
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = status.primaryColor
                        )
                    }
                }

                if (operationState !is UxOperationState.Processing && operationState !is UxOperationState.Progress) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("central_ux_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق الإشعار",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Real Progress Bar (Section 13: Measurable percentage/fraction or indeterminate)
            if (operationState is UxOperationState.Progress) {
                if (operationState.hasMeasurableProgress) {
                    LinearProgressIndicator(
                        progress = { operationState.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("ux_measurable_progress_bar"),
                        color = status.primaryColor,
                        trackColor = status.primaryColor.copy(alpha = 0.18f)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .testTag("ux_indeterminate_progress_bar"),
                        color = status.primaryColor,
                        trackColor = status.primaryColor.copy(alpha = 0.18f)
                    )
                }
            }
        }
    }
}

// ============================================================================
// SECTIONS 11 & 12: BUTTON PROCESSING STATE & DOUBLE-TAP PROTECTION
// Before: «تأكيد البيع» -> During: «جارٍ تسجيل البيع...» (Disabled + Debounced)
// ============================================================================

@Composable
fun MekkaProcessingButton(
    idleTextArabic: String,
    processingTextArabic: String,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    containerColor: Color = if (isDestructive) MekkaCrimsonError else MekkaEmeraldGreen,
    icon: ImageVector? = null,
    debounceWindowMillis: Long = 650L
) {
    var lastClickEpochMillis by remember { mutableLongStateOf(0L) }

    Button(
        onClick = {
            if (isProcessing || !enabled) return@Button
            val now = System.currentTimeMillis()
            if (now - lastClickEpochMillis < debounceWindowMillis) return@Button
            lastClickEpochMillis = now
            onClick()
        },
        enabled = enabled && !isProcessing,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = processingTextArabic,
                fontWeight = FontWeight.Bold
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = idleTextArabic,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ============================================================================
// SECTIONS 14, 15, 17: 3-LEVEL CONFIRMATION STRATEGY & PROFESSIONAL DIALOG
// ============================================================================

@Composable
fun MekkaProcessingButton(
    idleLabelArabic: String,
    processingLabelArabic: String,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    containerColor: Color = if (isDestructive) MekkaCrimsonError else MekkaEmeraldGreen,
    icon: ImageVector? = null,
    testTag: String? = null,
    debounceWindowMillis: Long = 650L
) {
    MekkaProcessingButton(
        idleTextArabic = idleLabelArabic,
        processingTextArabic = processingLabelArabic,
        isProcessing = isProcessing,
        onClick = onClick,
        modifier = if (testTag != null) modifier.testTag(testTag) else modifier,
        enabled = enabled,
        isDestructive = isDestructive,
        containerColor = containerColor,
        icon = icon,
        debounceWindowMillis = debounceWindowMillis
    )
}

/**
 * Three-Level Confirmation Strategy (Section 14):
 * - LEVEL_1_SAFE: Execute immediately (Open screen, Search, Filter, View details).
 * - LEVEL_2_MODERATE: Light confirmation (Remove item from cart, Cancel draft, Discard unsaved form).
 * - LEVEL_3_HIGH_RISK: Explicit confirmation explaining What Will Happen, What Data Will Change,
 *   Whether It Can Be Undone, and What The User Must Do.
 */
enum class ConfirmationRiskLevel(val arabicLabel: String) {
    LEVEL_1_SAFE("آمن — تنفيذ فوري"),
    LEVEL_2_MODERATE("متوسط — تأكيد خفيف"),
    LEVEL_3_HIGH_RISK("خطورة عالية — تأكيد صريح ومفصل")
}

@Composable
fun MekkaProfessionalConfirmationDialog(
    titleArabic: String,
    whatWillHappenArabic: String,
    whatDataWillChangeArabic: String,
    canBeUndoneArabic: String,
    whatUserMustDoArabic: String = "",
    confirmButtonLabelArabic: String,
    cancelButtonLabelArabic: String = "إلغاء",
    isDestructive: Boolean = false,
    isProcessing: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    testTag: String = "professional_confirmation_dialog"
) {
    val lines = whatDataWillChangeArabic.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else "البيان" to line
        }
    val combinedWarning = buildString {
        append(canBeUndoneArabic.trim())
        if (whatUserMustDoArabic.isNotBlank()) {
            append(" • ")
            append(whatUserMustDoArabic.trim())
        }
    }
    MekkaProfessionalConfirmationDialog(
        titleArabic = titleArabic,
        whatWillHappenArabic = whatWillHappenArabic,
        whatDataWillChangeLines = lines,
        undoabilityWarningArabic = combinedWarning,
        confirmButtonLabelArabic = confirmButtonLabelArabic,
        cancelButtonLabelArabic = cancelButtonLabelArabic,
        isDestructive = isDestructive,
        isProcessing = isProcessing,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        testTag = testTag
    )
}

/**
 * Professional Confirmation Dialog (Sections 15 & 17).
 * Never uses vague buttons such as «نعم» or «موافق» for destructive or financial actions.
 */
@Composable
fun MekkaProfessionalConfirmationDialog(
    titleArabic: String,
    whatWillHappenArabic: String,
    whatDataWillChangeLines: List<Pair<String, String>> = emptyList(),
    undoabilityWarningArabic: String,
    confirmButtonLabelArabic: String,
    cancelButtonLabelArabic: String = "إلغاء",
    isDestructive: Boolean = false,
    requireDeliberateVerificationCheckbox: Boolean = false,
    deliberateVerificationLabelArabic: String = "أقر بمسؤوليتي عن تنفيذ هذا الإجراء النهائي",
    isProcessing: Boolean = false,
    processingButtonLabelArabic: String = "جارٍ التنفيذ...",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    testTag: String = "professional_confirmation_dialog"
) {
    var deliberateChecked by rememberSaveable { mutableStateOf(false) }
    val accentColor = if (isDestructive) MekkaCrimsonError else MekkaEmeraldGreen
    val headerIcon = if (isDestructive) Icons.Default.DeleteForever else Icons.Default.VerifiedUser

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        modifier = Modifier.testTag(testTag),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = headerIcon,
                    contentDescription = titleArabic,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = titleArabic,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDestructive) MekkaCrimsonError else MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1. WHAT WILL HAPPEN
                Text(
                    text = whatWillHappenArabic,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 2. WHAT DATA WILL CHANGE
                if (whatDataWillChangeLines.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            whatDataWillChangeLines.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = value,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = LedgerMonoFontFamily,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. WHETHER IT CAN BE UNDONE
                Surface(
                    color = (if (isDestructive) MekkaCrimsonError else FinancialWarning).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (isDestructive) MekkaCrimsonError else FinancialWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = undoabilityWarningArabic,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDestructive) MekkaCrimsonError else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 4. DELIBERATE EXTRA CONFIRMATION FOR EXTREMELY DESTRUCTIVE ACTIONS (Section 17)
                if (requireDeliberateVerificationCheckbox) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!isProcessing) deliberateChecked = !deliberateChecked },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deliberateChecked,
                            onCheckedChange = { if (!isProcessing) deliberateChecked = it },
                            modifier = Modifier.testTag("${testTag}_deliberate_checkbox")
                        )
                        Text(
                            text = deliberateVerificationLabelArabic,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            MekkaProcessingButton(
                idleTextArabic = confirmButtonLabelArabic,
                processingTextArabic = processingButtonLabelArabic,
                isProcessing = isProcessing,
                enabled = !requireDeliberateVerificationCheckbox || deliberateChecked,
                isDestructive = isDestructive,
                containerColor = accentColor,
                onClick = onConfirm,
                modifier = Modifier.testTag("${testTag}_confirm_btn")
            )
        },
        dismissButton = {
            if (!isProcessing) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("${testTag}_cancel_btn")
                ) {
                    Text(cancelButtonLabelArabic, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

// ============================================================================
// SECTION 16: FINANCIAL REVIEW BEFORE COMMIT DIALOG
// Supports: SALE, PURCHASE, WALLET, EXPENSE, CUSTOMER/SUPPLIER PAYMENT
// ============================================================================

@Composable
fun FinancialReviewBeforeCommitDialog(
    operationTitleArabic: String,
    summaryRows: List<Pair<String, String>>,
    highlightedTotalLabelArabic: String,
    highlightedTotalValueArabic: String,
    confirmButtonLabelArabic: String,
    processingButtonLabelArabic: String,
    isProcessing: Boolean,
    errorMessageArabic: String? = null,
    onConfirmCommit: () -> Unit,
    onEditBack: () -> Unit,
    testTag: String = "financial_review_commit_dialog"
) {
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onEditBack() },
        modifier = Modifier.testTag(testTag),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = operationTitleArabic,
                    tint = MekkaEmeraldGreen,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = operationTitleArabic,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        summaryRows.forEach { (label, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = highlightedTotalLabelArabic,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = highlightedTotalValueArabic,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = LedgerMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = MekkaEmeraldGreen
                            )
                        }
                    }
                }

                if (!errorMessageArabic.isNullOrBlank()) {
                    MekkaInlineStatusBanner(
                        status = UxSemanticStatus.ERROR,
                        titleArabic = "تعذر الحفظ",
                        subtitleArabic = UxErrorSanitizer.sanitizeUserMessage(errorMessageArabic)
                    )
                }
            }
        },
        confirmButton = {
            MekkaProcessingButton(
                idleTextArabic = confirmButtonLabelArabic,
                processingTextArabic = processingButtonLabelArabic,
                isProcessing = isProcessing,
                onClick = onConfirmCommit,
                modifier = Modifier.testTag("${testTag}_confirm_btn")
            )
        },
        dismissButton = {
            if (!isProcessing) {
                OutlinedButton(
                    onClick = onEditBack,
                    modifier = Modifier.testTag("${testTag}_edit_btn")
                ) {
                    Text("تعديل", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

// ============================================================================
// SECTION 18: UNSAVED CHANGES DETECTION & GUARD DIALOG
// «لديك تغييرات لم يتم حفظها.»
// Options: «متابعة التعديل» | «حفظ» | «تجاهل التغييرات»
// ============================================================================

@Composable
fun UnsavedChangesGuardDialog(
    visible: Boolean,
    messageArabic: String = "لديك تغييرات لم يتم حفظها. هل ترغب في حفظها أو متابعة التعديل قبل المغادرة؟",
    onContinueEditing: () -> Unit,
    onDiscardChanges: () -> Unit,
    onSaveAndLeave: (() -> Unit)? = null,
    testTag: String = "unsaved_changes_guard_dialog"
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onContinueEditing,
        modifier = Modifier.testTag(testTag),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "تغييرات غير محفوظة",
                    tint = FinancialWarning,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "لديك تغييرات لم يتم حفظها.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Text(
                text = messageArabic,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onSaveAndLeave != null) {
                    Button(
                        onClick = onSaveAndLeave,
                        colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                        modifier = Modifier.testTag("${testTag}_save_btn")
                    ) {
                        Text("حفظ", fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = onContinueEditing,
                    colors = ButtonDefaults.buttonColors(containerColor = FinancialInfo),
                    modifier = Modifier.testTag("${testTag}_continue_btn")
                ) {
                    Text("متابعة التعديل", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscardChanges,
                modifier = Modifier.testTag("${testTag}_discard_btn")
            ) {
                Text(
                    text = "تجاهل التغييرات",
                    color = MekkaCrimsonError,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun UnsavedChangesDialog(
    changesSummaryArabic: String = "لديك تغييرات لم يتم حفظها. هل ترغب في حفظها أو متابعة التعديل قبل المغادرة؟",
    canSaveDirectly: Boolean = true,
    onContinueEditing: () -> Unit,
    onSaveChanges: () -> Unit,
    onDiscardChanges: () -> Unit,
    testTag: String = "unsaved_changes_guard_dialog"
) {
    UnsavedChangesGuardDialog(
        visible = true,
        messageArabic = changesSummaryArabic,
        onContinueEditing = onContinueEditing,
        onDiscardChanges = onDiscardChanges,
        onSaveAndLeave = if (canSaveDirectly) onSaveChanges else null,
        testTag = testTag
    )
}

/**
 * Intercepts system Back navigation when [hasUnsavedChanges] is true and prompts the user
 * with [UnsavedChangesGuardDialog] so user input is never silently lost (Section 18).
 */
@Composable
fun UnsavedChangesBackHandler(
    hasUnsavedChanges: Boolean,
    onDiscardAndLeave: () -> Unit,
    onSaveAndLeave: (() -> Unit)? = null
) {
    var showPrompt by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = hasUnsavedChanges) {
        showPrompt = true
    }

    UnsavedChangesGuardDialog(
        visible = showPrompt,
        onContinueEditing = { showPrompt = false },
        onDiscardChanges = {
            showPrompt = false
            onDiscardAndLeave()
        },
        onSaveAndLeave = if (onSaveAndLeave != null) {
            {
                showPrompt = false
                onSaveAndLeave()
            }
        } else null
    )
}

// ============================================================================
// SECTION 5: REUSABLE BOTTOM SHEET & FULL-SCREEN ERROR COMPONENTS
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MekkaContextualBottomSheet(
    visible: Boolean,
    titleArabic: String,
    subtitleArabic: String = "",
    onDismiss: () -> Unit,
    testTag: String = "contextual_bottom_sheet",
    content: @Composable ColumnScope.() -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleArabic,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (subtitleArabic.isNotBlank()) {
                        Text(
                            text = subtitleArabic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
            HorizontalDivider()
            content()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Full-Screen Error Component (Section 5: Only for serious screen-level failures).
 */
@Composable
fun MekkaFullScreenErrorState(
    problemTitleArabic: String,
    causeDescriptionArabic: String,
    dataSafetyNoteArabic: String = "لم يتم تغيير البيانات المحفوظة على الجهاز.",
    retryButtonLabelArabic: String = "إعادة المحاولة",
    onRetry: () -> Unit,
    secondaryActionLabelArabic: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    testTag: String = "full_screen_error_state"
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MekkaCrimsonError.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MekkaCrimsonError.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = problemTitleArabic,
                        tint = MekkaCrimsonError,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = problemTitleArabic,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MekkaCrimsonError,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = causeDescriptionArabic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Surface(
                    color = FinancialPositive.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = FinancialPositive,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = dataSafetyNoteArabic,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = FinancialPositive
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = MekkaEmeraldGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${testTag}_retry_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(retryButtonLabelArabic, fontWeight = FontWeight.Bold)
                }

                if (!secondaryActionLabelArabic.isNullOrBlank() && onSecondaryAction != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(secondaryActionLabelArabic)
                    }
                }
            }
        }
    }
}

// ============================================================================
// SECTION 19: LOCALIZED TIMELY FORM VALIDATION HELPER
// Never returns generic "Invalid input" — always returns specific Arabic guidance.
// ============================================================================

data class FormFieldValidationResult(
    val isValid: Boolean,
    val errorMessageArabic: String? = null
)

object LocalizedFormValidator {
    fun validateEgyptianPhone(rawPhone: String): String? =
        validateEgyptianPhoneInternal(rawPhone = rawPhone, isRequired = true)

    private fun validateEgyptianPhoneInternal(rawPhone: String, isRequired: Boolean): String? {
        val normalized = Money.normalizeDigits(rawPhone).trim()
        if (normalized.isEmpty()) {
            return if (isRequired) "أدخل رقم هاتف صحيح." else null
        }
        val digitsOnly = normalized.filter { it.isDigit() }
        if (digitsOnly.length < 10 || digitsOnly.length > 14) {
            return "أدخل رقم هاتف صحيح."
        }
        return null
    }

    fun validateEgyptianPhone(rawPhone: String, required: Boolean): FormFieldValidationResult {
        val err = validateEgyptianPhoneInternal(rawPhone = rawPhone, isRequired = required)
        return FormFieldValidationResult(isValid = err == null, errorMessageArabic = err)
    }

    fun validateRequiredName(
        rawName: String,
        fieldLabelArabic: String = "الاسم",
        minLength: Int = 2
    ): FormFieldValidationResult {
        val trimmed = rawName.trim()
        return when {
            trimmed.isEmpty() -> FormFieldValidationResult(false, "يرجى إدخال $fieldLabelArabic.")
            trimmed.length < minLength -> FormFieldValidationResult(false, "أدخل $fieldLabelArabic بشكل صحيح ($minLength أحرف على الأقل).")
            else -> FormFieldValidationResult(true, null)
        }
    }

    fun validatePositiveAmount(rawAmount: String, fieldNameArabic: String = "المبلغ"): String? {
        val normalized = Money.normalizeDigits(rawAmount).trim()
        if (normalized.isEmpty()) {
            return "يرجى إدخال $fieldNameArabic."
        }
        val minor = runCatching { Money.parseMajorString(normalized).amountMinor }.getOrNull()
        if (minor == null || minor <= 0L) {
            return "أدخل قيمة صحيحة لـ $fieldNameArabic أكبر من الصفر."
        }
        return null
    }

    fun validatePositiveQuantity(rawQty: String, availableStock: Int? = null): String? {
        val normalized = Money.normalizeDigits(rawQty).trim()
        if (normalized.isEmpty()) {
            return "يرجى إدخال الكمية المطلوبة."
        }
        val qty = normalized.toIntOrNull()
        if (qty == null || qty <= 0) {
            return "الكمية يجب أن تكون رقماً صحيحاً أكبر من الصفر."
        }
        if (availableStock != null && qty > availableStock) {
            return "تعذر حفظ البيع لأن الكمية المطلوبة غير متوفرة (المتاح: $availableStock)."
        }
        return null
    }
}

// ============================================================================
// COMPATIBILITY & DOMAIN HELPERS FOR V3.2 TESTS & SERVICES
// ============================================================================

typealias UxFeedbackStatus = UxSemanticStatus

data class SemanticStyleDescriptor(
    val status: UxSemanticStatus,
    val labelArabic: String,
    val icon: ImageVector,
    val primaryColor: Color
)

object MekkaSemanticStyleCatalog {
    fun styleFor(status: UxSemanticStatus): SemanticStyleDescriptor = SemanticStyleDescriptor(
        status = status,
        labelArabic = status.arabicLabel,
        icon = status.icon,
        primaryColor = status.primaryColor
    )
}

data class DomainSuccessMessageSpec(
    val titleArabic: String,
    val persistenceAndSyncNoteArabic: String
)

object UxSuccessMessageBuilder {
    private fun syncNote(isRemoteSynced: Boolean): String =
        if (isRemoteSynced) {
            "تم حفظ العملية على الجهاز وتمت مزامنتها مع الخادم السحابي"
        } else {
            "تم حفظ العملية على الجهاز\nوسيتم مزامنتها عند توفر الاتصال"
        }

    fun saleCompleted(
        invoiceNumber: String,
        totalFormattedArabic: String,
        isRemoteSynced: Boolean = false
    ): DomainSuccessMessageSpec = DomainSuccessMessageSpec(
        titleArabic = "تم تسجيل البيع بنجاح (#$invoiceNumber • $totalFormattedArabic)",
        persistenceAndSyncNoteArabic = syncNote(isRemoteSynced)
    )

    fun purchaseCompleted(
        productsCount: Int,
        invoiceNumber: String,
        isRemoteSynced: Boolean = false
    ): DomainSuccessMessageSpec = DomainSuccessMessageSpec(
        titleArabic = "تم إضافة $productsCount منتجات للمخزون (#$invoiceNumber)",
        persistenceAndSyncNoteArabic = syncNote(isRemoteSynced)
    )

    fun customerPaymentRecorded(
        amountFormattedArabic: String,
        customerNameArabic: String = "",
        isRemoteSynced: Boolean = false
    ): DomainSuccessMessageSpec = DomainSuccessMessageSpec(
        titleArabic = "تم تسجيل دفعة العميل بقيمة $amountFormattedArabic",
        persistenceAndSyncNoteArabic = syncNote(isRemoteSynced)
    )

    fun backupCreated(
        fileName: String,
        recordsCount: Int
    ): DomainSuccessMessageSpec = DomainSuccessMessageSpec(
        titleArabic = "تم إنشاء النسخة الاحتياطية بنجاح",
        persistenceAndSyncNoteArabic = "تم التحقق من بصمة SHA-256 وحفظ $recordsCount سجل بأمان ($fileName)"
    )
}

object OperationIdempotencyGuard {
    private val inFlightKeys = mutableSetOf<String>()
    private val completedFingerprints = mutableMapOf<String, Pair<String, Long>>()

    @Synchronized
    fun tryAcquireOperation(
        operationKey: String,
        payloadFingerprint: String = "",
        cooldownWindowMs: Long = 1_500L
    ): Boolean {
        if (inFlightKeys.contains(operationKey)) {
            return false
        }
        val now = System.currentTimeMillis()
        val last = completedFingerprints[operationKey]
        if (last != null && payloadFingerprint.isNotEmpty() && last.first == payloadFingerprint) {
            if (now - last.second < cooldownWindowMs) {
                return false
            }
        }
        inFlightKeys.add(operationKey)
        if (payloadFingerprint.isNotEmpty()) {
            completedFingerprints[operationKey] = payloadFingerprint to now
        }
        return true
    }

    @Synchronized
    fun releaseOperation(operationKey: String, recordCompletionTime: Boolean = true) {
        inFlightKeys.remove(operationKey)
        if (recordCompletionTime) {
            val existing = completedFingerprints[operationKey]
            if (existing != null) {
                completedFingerprints[operationKey] = existing.first to System.currentTimeMillis()
            }
        } else {
            completedFingerprints.remove(operationKey)
        }
    }

    @Synchronized
    fun isOperationInFlight(operationKey: String): Boolean = inFlightKeys.contains(operationKey)

    @Synchronized
    fun clearAllForTesting() {
        inFlightKeys.clear()
        completedFingerprints.clear()
    }
}
