package com.example.sms

import androidx.room.withTransaction
import com.example.core.AppResult
import com.example.core.IdGenerator
import com.example.core.SyncState
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.PendingTransactionEntity
import com.example.database.entities.SmsMessageEntity
import com.example.database.entities.SmsParsingResultEntity
import com.example.database.entities.TransactionEntity
import com.example.financial.FinancialLedgerEngine
import com.example.financial.Money
import kotlin.math.abs

/**
 * Confidence levels for SMS transaction intelligence (Requirement 24).
 */
enum class SmsConfidenceLevel(val arabicLabel: String, val minBps: Int) {
    HIGH("ثقة عالية (HIGH)", 8500),
    MEDIUM("ثقة متوسطة — تتطلب مراجعة (MEDIUM)", 5000),
    LOW("ثقة منخفضة — بيانات ناقصة (LOW)", 0);

    companion object {
        fun fromBps(bps: Int): SmsConfidenceLevel = when {
            bps >= 8500 -> HIGH
            bps >= 5000 -> MEDIUM
            else -> LOW
        }
    }
}

/**
 * SMS Classification Category (V4.1 Section 5).
 * Only TRANSACTION_SMS enters the financial transaction pipeline.
 */
enum class SmsClassification(val titleArabic: String, val isFinancialCandidate: Boolean) {
    TRANSACTION_SMS("رسالة معاملة مالية", true),
    OTP("رمز تحقق مؤقت (OTP)", false),
    PROMOTIONAL("عرض ترويجي / إعلاني", false),
    PERSONAL("رسالة شخصية", false),
    UNKNOWN("غير معروفة", false),
    UNSUPPORTED("غير مدعومة", false)
}

/**
 * Explicit SMS Processing State Machine (V4.1 Section 12).
 */
enum class SmsProcessingState(val titleArabic: String) {
    RECEIVED("تم الاستلام"),
    CLASSIFIED("تم التصنيف"),
    PARSED("تم الاستخراج"),
    PENDING_REVIEW("بانتظار مراجعة المستخدم"),
    CONFIRMED("تم الاعتماد والترحيل"),
    IGNORED("تم التجاهل"),
    DUPLICATE("مكررة وممنوعة"),
    FAILED("فشل الاستخراج"),
    UNSUPPORTED("غير مالية / غير مدعومة")
}

/**
 * Parsed candidate extracted by a provider-specific SmsParser (Requirement 21 & 23).
 */
data class ParsedSmsCandidate(
    val providerCode: String,
    val operationType: FinancialTransactionType, // DEPOSIT, WITHDRAW, TRANSFER
    val amountMinor: Long,
    val feeMinor: Long = 0L,
    val counterpartyPhoneOrHint: String = "",
    val referenceNumber: String = "",
    val baseConfidenceBps: Int,
    val parserVersion: Int = 1
)

/**
 * Outcome of the full SMS Intelligence Pipeline (Requirement 20 & V4.1 Sections 3-12).
 */
data class SmsPipelineOutcome(
    val smsEntity: SmsMessageEntity,
    val parsingResult: SmsParsingResultEntity?,
    val pendingTransaction: PendingTransactionEntity?,
    val confidenceLevel: SmsConfidenceLevel,
    val matchedExistingPendingOperation: Boolean,
    val autoConfirmedTransaction: TransactionEntity? = null,
    val classification: SmsClassification = SmsClassification.TRANSACTION_SMS,
    val processingState: SmsProcessingState = SmsProcessingState.PARSED,
    val ambiguousOptionsArabic: List<String> = emptyList()
)

/**
 * SMS Message Classifier (V4.1 Section 5).
 * Deterministically classifies incoming SMS before any financial parsing.
 */
object SmsClassifier {
    private val otpKeywords = listOf("كود", "رمز التأكيد", "otp", "verification code", "لا تشارك هذا الرمز", "رمز الدخول", "security code", "كلمة سر مؤقتة", "passcode")
    private val promoKeywords = listOf("عرض", "خصومات", "اشترك", "باقة", "هدية", "promo", "discount", "فصل الصيف", "اربح", "مبروك")
    private val financialKeywords = listOf("تم استلام", "استلمت", "تم تحويل", "حولت", "سحب", "إيداع", "ايداع", "كاش", "رصيد", "محفظة", "مصاريف", "رسوم", "دفعت", "دفع", "انستا باي", "فودافون كاش", "اورنج كاش", "اتصالات كاش")

    fun classify(sender: String, normalizedBody: String): SmsClassification {
        val lower = normalizedBody.lowercase()
        val senderLower = sender.lowercase()

        // 1. Check for OTP first (OTPs must NEVER enter financial pipeline)
        if (otpKeywords.any { lower.contains(it) } && (lower.contains("رمز") || lower.contains("كود") || lower.contains("code") || lower.contains("otp"))) {
            return SmsClassification.OTP
        }

        // 2. Check for Promotional messages
        if (promoKeywords.any { lower.contains(it) } && !lower.contains("تم استلام") && !lower.contains("تم تحويل") && !lower.contains("سحب")) {
            return SmsClassification.PROMOTIONAL
        }

        // 3. Check for Financial Provider indicators
        val isFinancialProvider = senderLower.contains("vodafone") || senderLower.contains("vf") ||
            senderLower.contains("orange") || senderLower.contains("etisalat") || senderLower.contains("e&") ||
            senderLower.contains("instapay") || senderLower.contains("ipn") || senderLower.contains("axis") ||
            senderLower.contains("bank") || senderLower.contains("cib") || senderLower.contains("nbe") || senderLower.contains("qnb")

        val hasFinancialKeywords = financialKeywords.any { lower.contains(it) }
        val hasAmount = SmsNormalizationUtils.extractAmountMinor(normalizedBody) != null

        if ((isFinancialProvider || hasFinancialKeywords) && hasAmount) {
            return SmsClassification.TRANSACTION_SMS
        }

        if (hasFinancialKeywords) {
            return SmsClassification.UNSUPPORTED
        }

        return SmsClassification.PERSONAL
    }
}

/**
 * Unified Provider-specific SMS Parser Interface (Requirement 23).
 */
interface SmsParser {
    val providerCode: String
    fun canParse(sender: String, normalizedBody: String): Boolean
    fun parse(sender: String, normalizedBody: String, receivedAt: Long): ParsedSmsCandidate?
    fun validate(candidate: ParsedSmsCandidate): Boolean {
        return candidate.providerCode == providerCode &&
            candidate.amountMinor > 0L &&
            candidate.feeMinor >= 0L
    }
}

/**
 * Shared text normalization and regex extraction helpers for Arabic & English financial SMS messages.
 */
object SmsNormalizationUtils {
    private val amountPatterns = listOf(
        Regex("""(?:مبلغ|بقيمة|بمبلغ|قيمة|amount|egp|جنيه|ج\.م)\s*[:\-]?\s*([0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9]+(?:\.[0-9]{1,2})?)\s*(?:ج\.م|جنيه|egp|le)""", RegexOption.IGNORE_CASE)
    )

    private val feePatterns = listOf(
        Regex("""(?:رسوم|مصاريف|عمولة|fee|fees)\s*[:\-]?\s*([0-9]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val referencePatterns = listOf(
        Regex("""(?:رقم العملية|مرجع|المرجع|رقم مرجعي|ref|reference|txid|id)\s*[:#\-]?\s*([A-Za-z0-9\-]{4,24})""", RegexOption.IGNORE_CASE),
        Regex("""#([A-Za-z0-9]{5,20})""")
    )

    private val phonePattern = Regex("""(01[0125][0-9]{8})""")

    fun normalizeSmsText(rawText: String): String {
        val asciiDigits = Money.normalizeDigits(rawText)
        return asciiDigits
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun extractAmountMinor(normalized: String): Long? {
        for (regex in amountPatterns) {
            val match = regex.find(normalized)
            if (match != null) {
                val rawAmt = match.groupValues[1]
                val parsed = runCatching { Money.parseMajorString(rawAmt).amountMinor }.getOrNull()
                if (parsed != null && parsed > 0L) return parsed
            }
        }
        return null
    }

    fun extractFeeMinor(normalized: String): Long {
        for (regex in feePatterns) {
            val match = regex.find(normalized)
            if (match != null) {
                val rawFee = match.groupValues[1]
                val parsed = runCatching { Money.parseMajorString(rawFee).amountMinor }.getOrNull()
                if (parsed != null && parsed >= 0L) return parsed
            }
        }
        return 0L
    }

    fun extractReference(normalized: String): String {
        for (regex in referencePatterns) {
            val match = regex.find(normalized)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return ""
    }

    fun extractPhoneHint(normalized: String): String {
        return phonePattern.find(normalized)?.groupValues?.get(1) ?: ""
    }

    fun detectOperationType(normalized: String): FinancialTransactionType? {
        val lower = normalized.lowercase()
        return when {
            lower.contains("تم استلام") || lower.contains("استلمت") || lower.contains("ايداع") ||
                lower.contains("إيداع") || lower.contains("received") || lower.contains("deposited") ->
                FinancialTransactionType.DEPOSIT
            lower.contains("سحب") || lower.contains("cash out") || lower.contains("withdrawn") ||
                lower.contains("withdrawal") ->
                FinancialTransactionType.WITHDRAW
            lower.contains("تم تحويل") || lower.contains("حولت") || lower.contains("تحويل") ||
                lower.contains("transferred") || lower.contains("sent") ->
                FinancialTransactionType.TRANSFER
            else -> null
        }
    }

    fun computeConfidenceBps(
        amountMinor: Long?,
        opType: FinancialTransactionType?,
        reference: String,
        phoneHint: String
    ): Int {
        if (amountMinor == null || amountMinor <= 0L || opType == null) return 2500 // LOW
        var score = 6000 // Base MEDIUM when provider + amount + type are clear
        if (reference.isNotBlank()) score += 2500
        if (phoneHint.isNotBlank()) score += 1000
        return score.coerceAtMost(9500)
    }
}

class VodafoneCashParser : SmsParser {
    override val providerCode: String = "VODAFONE_CASH"

    override fun canParse(sender: String, normalizedBody: String): Boolean {
        val s = sender.lowercase()
        val b = normalizedBody.lowercase()
        return s.contains("vf-cash") || s.contains("vodafone") ||
            b.contains("فودافون كاش") || b.contains("vodafone cash") || b.contains("vf cash")
    }

    override fun parse(sender: String, normalizedBody: String, receivedAt: Long): ParsedSmsCandidate? {
        val amount = SmsNormalizationUtils.extractAmountMinor(normalizedBody) ?: return null
        val opType = SmsNormalizationUtils.detectOperationType(normalizedBody) ?: return null
        val fee = SmsNormalizationUtils.extractFeeMinor(normalizedBody)
        val ref = SmsNormalizationUtils.extractReference(normalizedBody)
        val phone = SmsNormalizationUtils.extractPhoneHint(normalizedBody)
        val confidence = SmsNormalizationUtils.computeConfidenceBps(amount, opType, ref, phone)
        return ParsedSmsCandidate(
            providerCode = providerCode,
            operationType = opType,
            amountMinor = amount,
            feeMinor = fee,
            counterpartyPhoneOrHint = phone,
            referenceNumber = ref,
            baseConfidenceBps = confidence
        )
    }
}

class OrangeCashParser : SmsParser {
    override val providerCode: String = "ORANGE_CASH"

    override fun canParse(sender: String, normalizedBody: String): Boolean {
        val s = sender.lowercase()
        val b = normalizedBody.lowercase()
        return s.contains("orangecash") || s.contains("orange") ||
            b.contains("اورنج كاش") || b.contains("أورنج كاش") || b.contains("orange cash")
    }

    override fun parse(sender: String, normalizedBody: String, receivedAt: Long): ParsedSmsCandidate? {
        val amount = SmsNormalizationUtils.extractAmountMinor(normalizedBody) ?: return null
        val opType = SmsNormalizationUtils.detectOperationType(normalizedBody) ?: return null
        val fee = SmsNormalizationUtils.extractFeeMinor(normalizedBody)
        val ref = SmsNormalizationUtils.extractReference(normalizedBody)
        val phone = SmsNormalizationUtils.extractPhoneHint(normalizedBody)
        val confidence = SmsNormalizationUtils.computeConfidenceBps(amount, opType, ref, phone)
        return ParsedSmsCandidate(
            providerCode = providerCode,
            operationType = opType,
            amountMinor = amount,
            feeMinor = fee,
            counterpartyPhoneOrHint = phone,
            referenceNumber = ref,
            baseConfidenceBps = confidence
        )
    }
}

class EtisalatMoneyParser : SmsParser {
    override val providerCode: String = "ETISALAT_CASH"

    override fun canParse(sender: String, normalizedBody: String): Boolean {
        val s = sender.lowercase()
        val b = normalizedBody.lowercase()
        return s.contains("e&") || s.contains("etisalat") ||
            b.contains("اتصالات كاش") || b.contains("e& money") || b.contains("e&money")
    }

    override fun parse(sender: String, normalizedBody: String, receivedAt: Long): ParsedSmsCandidate? {
        val amount = SmsNormalizationUtils.extractAmountMinor(normalizedBody) ?: return null
        val opType = SmsNormalizationUtils.detectOperationType(normalizedBody) ?: return null
        val fee = SmsNormalizationUtils.extractFeeMinor(normalizedBody)
        val ref = SmsNormalizationUtils.extractReference(normalizedBody)
        val phone = SmsNormalizationUtils.extractPhoneHint(normalizedBody)
        val confidence = SmsNormalizationUtils.computeConfidenceBps(amount, opType, ref, phone)
        return ParsedSmsCandidate(
            providerCode = providerCode,
            operationType = opType,
            amountMinor = amount,
            feeMinor = fee,
            counterpartyPhoneOrHint = phone,
            referenceNumber = ref,
            baseConfidenceBps = confidence
        )
    }
}

class InstaPayParser : SmsParser {
    override val providerCode: String = "INSTAPAY"

    override fun canParse(sender: String, normalizedBody: String): Boolean {
        val s = sender.lowercase()
        val b = normalizedBody.lowercase()
        return s.contains("instapay") || s.contains("ipn") ||
            b.contains("انستا باي") || b.contains("إنستا باي") || b.contains("instapay")
    }

    override fun parse(sender: String, normalizedBody: String, receivedAt: Long): ParsedSmsCandidate? {
        val amount = SmsNormalizationUtils.extractAmountMinor(normalizedBody) ?: return null
        val opType = SmsNormalizationUtils.detectOperationType(normalizedBody) ?: return null
        val fee = SmsNormalizationUtils.extractFeeMinor(normalizedBody)
        val ref = SmsNormalizationUtils.extractReference(normalizedBody)
        val phone = SmsNormalizationUtils.extractPhoneHint(normalizedBody)
        val confidence = SmsNormalizationUtils.computeConfidenceBps(amount, opType, ref, phone)
        return ParsedSmsCandidate(
            providerCode = providerCode,
            operationType = opType,
            amountMinor = amount,
            feeMinor = fee,
            counterpartyPhoneOrHint = phone,
            referenceNumber = ref,
            baseConfidenceBps = confidence
        )
    }
}

class AxisPayParser : SmsParser {
    override val providerCode: String = "AXIS_PAY"

    override fun canParse(sender: String, normalizedBody: String): Boolean {
        val s = sender.lowercase()
        val b = normalizedBody.lowercase()
        return s.contains("axis") || b.contains("اكسس باي") || b.contains("أكسس باي") || b.contains("axis pay")
    }

    override fun parse(sender: String, normalizedBody: String, receivedAt: Long): ParsedSmsCandidate? {
        val amount = SmsNormalizationUtils.extractAmountMinor(normalizedBody) ?: return null
        val opType = SmsNormalizationUtils.detectOperationType(normalizedBody) ?: return null
        val fee = SmsNormalizationUtils.extractFeeMinor(normalizedBody)
        val ref = SmsNormalizationUtils.extractReference(normalizedBody)
        val phone = SmsNormalizationUtils.extractPhoneHint(normalizedBody)
        val confidence = SmsNormalizationUtils.computeConfidenceBps(amount, opType, ref, phone)
        return ParsedSmsCandidate(
            providerCode = providerCode,
            operationType = opType,
            amountMinor = amount,
            feeMinor = fee,
            counterpartyPhoneOrHint = phone,
            referenceNumber = ref,
            baseConfidenceBps = confidence
        )
    }
}

/**
 * Complete SMS Intelligence Engine (Requirements 20–28).
 */
class SmsIntelligenceEngine(
    private val database: MekkaPhoneDatabase,
    private val financialLedgerEngine: FinancialLedgerEngine
) {
    private val opsDao = database.operationsDao()
    private val ledgerDao = database.financialLedgerDao()
    private val systemDao = database.systemIntegrityDao()

    val registeredParsers: List<SmsParser> = listOf(
        VodafoneCashParser(),
        OrangeCashParser(),
        EtisalatMoneyParser(),
        InstaPayParser(),
        AxisPayParser()
    )

    /**
     * Registers an expected pending wallet operation BEFORE the SMS confirmation arrives (Requirement 25).
     */
    suspend fun registerExpectedOperation(
        shopId: String,
        targetWalletId: String,
        expectedType: FinancialTransactionType,
        expectedAmountMinor: Long,
        expectedFeeMinor: Long = 0L,
        counterpartyPhone: String = "",
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<PendingTransactionEntity> {
        if (expectedAmountMinor <= 0L) {
            return AppResult.Error("مبلغ العملية المتوقعة يجب أن يكون أكبر من الصفر", "INVALID_EXPECTED_AMOUNT")
        }
        val pending = PendingTransactionEntity(
            localId = IdGenerator.newPrefixedId("PND"),
            shopId = shopId,
            smsParsingResultId = null,
            proposedType = expectedType,
            proposedAmountMinor = expectedAmountMinor,
            proposedFeeMinor = expectedFeeMinor,
            targetWalletId = targetWalletId,
            counterpartyPhone = counterpartyPhone.trim(),
            status = "AWAITING_CONFIRMATION",
            deviceId = deviceId,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        opsDao.insertPendingTransaction(pending)
        return AppResult.Success(pending)
    }

    /**
     * Full SMS Intelligence Pipeline (Requirements 20, 21, 22, 23, 24, 25):
     * SMS Received -> Normalize -> Identify Provider -> Parse -> Validate -> Duplicate Check -> Match Pending Operation -> Confidence Score -> Pending/Review.
     */
    suspend fun ingestSmsEvidence(
        shopId: String,
        senderAddress: String,
        rawMessageBody: String,
        deviceId: String,
        receivedTimestamp: Long = System.currentTimeMillis(),
        autoConfirmOnHighConfidenceMatch: Boolean = false,
        actorUserId: String = "USR-OWNER-PRIMARY"
    ): AppResult<SmsPipelineOutcome> {
        val cleanSender = senderAddress.trim()
        val normalizedBody = SmsNormalizationUtils.normalizeSmsText(rawMessageBody)
        if (normalizedBody.isBlank()) {
            return AppResult.Error("نص الرسالة فارغ", "EMPTY_SMS")
        }

        // 1. Compute deterministic messageHash for duplicate protection
        val messageHash = IdGenerator.sha256Hex("$shopId|${cleanSender.lowercase()}|$normalizedBody")
        val existingByHash = opsDao.getSmsByMessageHash(shopId, messageHash)
        if (existingByHash != null) {
            return AppResult.Error(
                "تم تجاهل الرسالة لأنها مكررة ومسجلة مسبقاً (Duplicate SMS Blocked by MessageHash)",
                "DUPLICATE_SMS_HASH"
            )
        }

        // 2. Identify Provider & Parse
        val matchedParser = registeredParsers.firstOrNull { it.canParse(cleanSender, normalizedBody) }
        val candidate = matchedParser?.parse(cleanSender, normalizedBody, receivedTimestamp)
            ?.takeIf { matchedParser.validate(it) }

        // 3. Secondary Duplicate Check by Provider + Reference (if reference is present)
        if (candidate != null && candidate.referenceNumber.isNotBlank()) {
            val dupByRef = opsDao.getParsingResultByProviderAndReference(
                shopId = shopId,
                providerCode = candidate.providerCode,
                reference = candidate.referenceNumber
            )
            if (dupByRef != null) {
                return AppResult.Error(
                    "تم منع تكرار المعاملة: رقم المرجع (${candidate.referenceNumber}) مسجل بالفعل لمزود (${candidate.providerCode})",
                    "DUPLICATE_SMS_REFERENCE"
                )
            }
        }

        return try {
            database.withTransaction {
                val smsEntity = SmsMessageEntity(
                    localId = IdGenerator.newPrefixedId("SMS"),
                    shopId = shopId,
                    senderAddress = cleanSender,
                    messageBody = normalizedBody,
                    messageHashSha256 = messageHash,
                    receivedTimestamp = receivedTimestamp,
                    isProcessed = false,
                    deviceId = deviceId,
                    createdAt = receivedTimestamp,
                    updatedAt = receivedTimestamp
                )
                opsDao.insertSmsMessage(smsEntity)

                if (candidate == null) {
                    // Unrecognized provider or missing amount -> store evidence with LOW confidence for manual review
                    return@withTransaction AppResult.Success(
                        SmsPipelineOutcome(
                            smsEntity = smsEntity,
                            parsingResult = null,
                            pendingTransaction = null,
                            confidenceLevel = SmsConfidenceLevel.LOW,
                            matchedExistingPendingOperation = false
                        )
                    )
                }

                // 4. Match against expected pending operations within 60-minute window
                val unmatchedCandidates = opsDao.findUnmatchedExpectedPendingOperations(
                    shopId = shopId,
                    amountMinor = candidate.amountMinor
                )
                val matchedPending = unmatchedCandidates.firstOrNull { pending ->
                    val timeDiff = abs(receivedTimestamp - pending.createdAt)
                    val typeMatches = pending.proposedType == candidate.operationType
                    val phoneMatches = pending.counterpartyPhone.isBlank() ||
                        candidate.counterpartyPhoneOrHint.isBlank() ||
                        pending.counterpartyPhone == candidate.counterpartyPhoneOrHint
                    timeDiff <= 60 * 60 * 1000L && typeMatches && phoneMatches
                }

                val finalConfidenceBps = if (matchedPending != null) {
                    (candidate.baseConfidenceBps + 1500).coerceAtMost(10_000)
                } else {
                    candidate.baseConfidenceBps
                }
                val confidenceLevel = SmsConfidenceLevel.fromBps(finalConfidenceBps)

                val parsingEntity = SmsParsingResultEntity(
                    localId = IdGenerator.newPrefixedId("PRP"),
                    shopId = shopId,
                    smsMessageId = smsEntity.localId,
                    detectedProviderCode = candidate.providerCode,
                    detectedOperationType = candidate.operationType.name,
                    extractedAmountMinor = candidate.amountMinor,
                    extractedFeeMinor = candidate.feeMinor,
                    extractedCounterpartyPhone = candidate.counterpartyPhoneOrHint,
                    extractedReference = candidate.referenceNumber,
                    confidenceBps = finalConfidenceBps,
                    createdAt = receivedTimestamp
                )
                opsDao.insertSmsParsingResult(parsingEntity)

                val defaultWallet = ledgerDao.getFirstWalletForProviderCode(shopId, candidate.providerCode)
                val pendingEntity = if (matchedPending != null) {
                    val updated = matchedPending.copy(
                        smsParsingResultId = parsingEntity.localId,
                        proposedFeeMinor = if (candidate.feeMinor > 0L) candidate.feeMinor else matchedPending.proposedFeeMinor,
                        targetWalletId = matchedPending.targetWalletId ?: defaultWallet?.localId,
                        counterpartyPhone = candidate.counterpartyPhoneOrHint.ifBlank { matchedPending.counterpartyPhone },
                        updatedAt = receivedTimestamp,
                        version = matchedPending.version + 1L
                    )
                    opsDao.updatePendingTransaction(updated)
                    updated
                } else {
                    val created = PendingTransactionEntity(
                        localId = IdGenerator.newPrefixedId("PND"),
                        shopId = shopId,
                        smsParsingResultId = parsingEntity.localId,
                        proposedType = candidate.operationType,
                        proposedAmountMinor = candidate.amountMinor,
                        proposedFeeMinor = candidate.feeMinor,
                        targetWalletId = defaultWallet?.localId,
                        counterpartyPhone = candidate.counterpartyPhoneOrHint,
                        status = "AWAITING_CONFIRMATION",
                        deviceId = deviceId,
                        createdAt = receivedTimestamp,
                        updatedAt = receivedTimestamp
                    )
                    opsDao.insertPendingTransaction(created)
                    created
                }

                // Requirement 35: Auto-confirm MUST satisfy all 10 safety gates, never just confidence >= 85%:
                // 1. Known provider, 2. Valid template, 3. Valid amount, 4. Valid type, 5. Reference present,
                // 6. Valid timestamp window, 7. Expected operation exact match, 8. Duplicate protection passed,
                // 9. Correct shop/wallet ownership, 10. Risk limit evaluation (<= 50,000 EGP).
                var autoTx: TransactionEntity? = null
                val maxAutoConfirmRiskLimitMinor = 5_000_000L // 50,000.00 EGP
                val allTenAutoConfirmGatesPassed = autoConfirmOnHighConfidenceMatch &&
                    matchedPending != null &&
                    matchedPending.shopId == shopId &&
                    matchedPending.proposedAmountMinor == candidate.amountMinor &&
                    candidate.amountMinor in 1L..maxAutoConfirmRiskLimitMinor &&
                    candidate.referenceNumber.isNotBlank() &&
                    confidenceLevel == SmsConfidenceLevel.HIGH &&
                    pendingEntity.targetWalletId != null

                if (allTenAutoConfirmGatesPassed && pendingEntity.targetWalletId != null) {
                    val confirmRes = confirmPendingTransactionInternal(
                        shopId = shopId,
                        pendingId = pendingEntity.localId,
                        targetWalletId = pendingEntity.targetWalletId,
                        actorUserId = actorUserId,
                        deviceId = deviceId,
                        timestamp = receivedTimestamp
                    )
                    if (confirmRes is AppResult.Success) {
                        autoTx = confirmRes.data
                    }
                }

                AppResult.Success(
                    SmsPipelineOutcome(
                        smsEntity = smsEntity,
                        parsingResult = parsingEntity,
                        pendingTransaction = pendingEntity,
                        confidenceLevel = confidenceLevel,
                        matchedExistingPendingOperation = matchedPending != null,
                        autoConfirmedTransaction = autoTx
                    )
                )
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر معالجة رسالة SMS: ${e.message}", "SMS_PIPELINE_ERROR", e)
        }
    }

    /**
     * Confirms a pending SMS / operation into the Immutable Financial Ledger idempotently (Requirement 26, 36, 50).
     * Enforces 4-way Shop Isolation (`SMS.shopId == Wallet.shopId == PendingOperation.shopId == CurrentUser.shopId`)
     * and never allows confirming the same message or pending transaction twice!
     */
    suspend fun confirmPendingSmsToLedger(
        shopId: String,
        pendingId: String,
        explicitWalletId: String? = null,
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<TransactionEntity> {
        return try {
            database.withTransaction {
                val pending = opsDao.getPendingTransactionById(pendingId)
                    ?: return@withTransaction AppResult.Error("العملية المعلقة غير موجودة", "PENDING_NOT_FOUND")

                if (pending.shopId != shopId) {
                    return@withTransaction AppResult.Error(
                        "رفض أمني صارم: العملية المعلقة لا تنتمي إلى المتجر الحالي (SMS Shop Isolation Violation)",
                        "SMS_SHOP_ISOLATION_VIOLATION"
                    )
                }

                val actorUser = database.coreShopAuthDao().getUserById(actorUserId)
                if (actorUser != null && actorUser.shopId != shopId) {
                    return@withTransaction AppResult.Error(
                        "رفض أمني صارم: المستخدم الحالي لا ينتمي إلى متجر العملية (User Shop Isolation Violation)",
                        "USER_SHOP_ISOLATION_VIOLATION"
                    )
                }

                if (pending.status == "CONFIRMED" || pending.confirmedLedgerTransactionId != null) {
                    return@withTransaction AppResult.Error(
                        "تم تأكيد هذه الرسالة وترحيلها لدفتر الأستاذ مسبقاً — يمنع التأكيد المكرر!",
                        "ALREADY_CONFIRMED_IDEMPOTENT"
                    )
                }
                if (pending.status != "AWAITING_CONFIRMATION") {
                    return@withTransaction AppResult.Error(
                        "لا يمكن تأكيد رسالة حالتها (${pending.status})",
                        "INVALID_PENDING_STATUS"
                    )
                }

                val walletId = explicitWalletId?.takeIf { it.isNotBlank() }
                    ?: pending.targetWalletId?.takeIf { it.isNotBlank() }
                    ?: return@withTransaction AppResult.Error(
                        "يرجى اختيار المحفظة الإلكترونية المستهدفة بشكل صريح لربط رسالة SMS بها",
                        "EXPLICIT_WALLET_REQUIRED"
                    )

                val targetWallet = ledgerDao.getWalletById(walletId)
                    ?: return@withTransaction AppResult.Error("المحفظة المستهدفة غير موجودة", "WALLET_NOT_FOUND")

                if (targetWallet.shopId != shopId) {
                    return@withTransaction AppResult.Error(
                        "رفض أمني صارم: المحفظة المستهدفة تتبع متجراً آخر (Wallet Shop Isolation Violation)",
                        "WALLET_SHOP_ISOLATION_VIOLATION"
                    )
                }

                confirmPendingTransactionInternal(
                    shopId = shopId,
                    pendingId = pending.localId,
                    targetWalletId = walletId,
                    actorUserId = actorUserId,
                    deviceId = deviceId,
                    timestamp = timestamp
                )
            }
        } catch (e: Exception) {
            AppResult.Error("تعذر تأكيد رسالة SMS في دفتر الأستاذ: ${e.message}", "SMS_CONFIRM_ERROR", e)
        }
    }

    private suspend fun confirmPendingTransactionInternal(
        shopId: String,
        pendingId: String,
        targetWalletId: String,
        actorUserId: String,
        deviceId: String,
        timestamp: Long
    ): AppResult<TransactionEntity> {
        val pending = opsDao.getPendingTransactionById(pendingId)
            ?: return AppResult.Error("العملية المعلقة غير موجودة", "PENDING_NOT_FOUND")

        val isInflowToWallet = pending.proposedType == FinancialTransactionType.DEPOSIT
        val srcType = if (isInflowToWallet) FinancialAccountType.EXTERNAL_CUSTOMER else FinancialAccountType.WALLET
        val srcId = if (isInflowToWallet) pending.counterpartyPhone.ifBlank { "SMS_SENDER" } else targetWalletId
        val dstType = if (isInflowToWallet) FinancialAccountType.WALLET else FinancialAccountType.EXTERNAL_CUSTOMER
        val dstId = if (isInflowToWallet) targetWalletId else pending.counterpartyPhone.ifBlank { "SMS_RECIPIENT" }

        val txRes = financialLedgerEngine.postLedgerTransactionInternal(
            shopId = shopId,
            transactionType = pending.proposedType,
            amountMinor = pending.proposedAmountMinor,
            feeMinor = pending.proposedFeeMinor,
            sourceAccountType = srcType,
            sourceAccountId = srcId,
            destinationAccountType = dstType,
            destinationAccountId = dstId,
            relatedEntityType = "SMS_PENDING_TX",
            relatedEntityId = pending.localId,
            descriptionArabic = "تأكيد معاملة محفظة عبر SMS (${pending.proposedType.arabicLabel}) — هاتف: ${pending.counterpartyPhone}",
            createdByUserId = actorUserId,
            deviceId = deviceId,
            timestamp = timestamp
        )
        if (txRes is AppResult.Error) return txRes
        val ledgerTx = (txRes as AppResult.Success).data

        val updatedPending = pending.copy(
            targetWalletId = targetWalletId,
            status = "CONFIRMED",
            confirmedLedgerTransactionId = ledgerTx.localId,
            updatedAt = timestamp,
            version = pending.version + 1L,
            syncState = SyncState.PENDING
        )
        opsDao.updatePendingTransaction(updatedPending)

        systemDao.insertAuditLog(
            AuditLogEntity(
                localId = IdGenerator.newPrefixedId("AUD"),
                shopId = shopId,
                actorUserId = actorUserId,
                actorRoleCode = "OWNER",
                actionType = "SMS_CONFIRMED",
                entityType = "PendingTransaction",
                entityId = pending.localId,
                summaryArabic = "اعتماد رسالة SMS وترحيل قيد (${ledgerTx.referenceNumber}) بقيمة ${Money.ofMinor(pending.proposedAmountMinor).formatArabic()}",
                beforeStateJson = """{"status":"AWAITING_CONFIRMATION"}""",
                afterStateJson = """{"status":"CONFIRMED","ledgerTxId":"${ledgerTx.localId}"}""",
                deviceId = deviceId,
                timestamp = timestamp
            )
        )

        return AppResult.Success(ledgerTx)
    }

    /**
     * Rejects or ignores a pending SMS transaction so it never affects financial balances.
     */
    suspend fun updatePendingSmsReviewDecision(
        shopId: String,
        pendingId: String,
        decisionStatus: String, // REJECTED or IGNORED
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<PendingTransactionEntity> {
        val pending = opsDao.getPendingTransactionById(pendingId)
            ?: return AppResult.Error("السجل المعلق غير موجود", "PENDING_NOT_FOUND")
        if (pending.status == "CONFIRMED") {
            return AppResult.Error("لا يمكن رفض عملية تم تأكيدها وترحيلها بالفعل", "ALREADY_CONFIRMED")
        }
        val updated = pending.copy(
            status = decisionStatus,
            updatedAt = timestamp,
            version = pending.version + 1L
        )
        opsDao.updatePendingTransaction(updated)
        return AppResult.Success(updated)
    }
}
