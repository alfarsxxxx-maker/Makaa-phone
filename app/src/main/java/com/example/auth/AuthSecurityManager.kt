package com.example.auth

import com.example.core.AppResult
import com.example.core.IdGenerator
import com.example.database.dao.CoreShopAuthDao
import com.example.database.entities.AppSettingEntity
import com.example.database.entities.PermissionEntity
import com.example.database.entities.RoleEntity
import com.example.database.entities.RolePermissionCrossRef
import com.example.database.entities.UserEntity
import com.example.database.entities.UserRoleType
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Granular permissions across all Mekka Phone modules (Requirement 16 & 17).
 */
enum class PermissionCode(
    val moduleName: String,
    val titleArabic: String,
    val descriptionArabic: String
) {
    SALE_CREATE("SALES", "إنشاء فاتورة بيع", "إصدار فواتير المبيعات وصرف الأصناف من المخزن"),
    REFUND_APPROVE("SALES", "اعتماد مرتجع مبيعات", "الموافقة على مرتجعات المبيعات ورد المبالغ للعملاء"),
    PURCHASE_CREATE("PURCHASES", "تسجيل فاتورة مشتريات", "توريد بضاعة جديدة للمخزن وتحديث المتوسط المرجح للتكلفة"),
    INVENTORY_ADJUST("INVENTORY", "تسوية المخزون والجرد", "إجراء تسويات جردية أو إثبات توالف"),
    PRICE_CHANGE("INVENTORY", "تعديل أسعار البيع", "تحديث أسعار البيع الافتراضية والحد الأدنى"),
    WALLET_OPERATE("WALLETS", "تنفيذ عمليات المحافظ", "تحويل واستقبال وسحب وإيداع المحافظ الإلكترونية"),
    CASH_OPERATE("CASH", "إدارة الخزينة والورديات", "توريد وصرف نقدية وفتح وإغلاق جلسات الخزينة"),
    CASH_ADJUST("CASH", "تسوية فروق الخزينة", "إصدار قيود تسوية محاسبية للخزينة"),
    EXPENSE_CREATE("EXPENSES", "تسجيل مصروفات تشغيلية", "صرف وتسجيل المصروفات التشغيلية للمتجر"),
    MAINTENANCE_MANAGE("MAINTENANCE", "إدارة أوامر الصيانة", "استلام الأجهزة وفحصها واستهلاك قطع الغيار وتسليمها"),
    LEDGER_REVERSE("FINANCIAL", "إصدار قيد عكسي", "إصدار قيد عكسي تصحيحي في دفتر الأستاذ العام"),
    CONFLICT_RESOLVE("SYNC", "مراجعة وحل التعارضات", "مراجعة التعارضات المالية والمزامنة واعتماد الحلول"),
    BACKUP_RESTORE("BACKUP", "النسخ الاحتياطي والاستعادة", "إنشاء والتحقق من واستعادة النسخ الاحتياطية المشفرة"),
    USER_PERMISSION_MANAGE("SECURITY", "إدارة المستخدمين والصلاحيات", "إدارة الموظفين والأدوار والصلاحيات"),
    AUDIT_VIEW("SECURITY", "الاطلاع على سجل المراجعة", "عرض سجل التدقيق المالي والأمني الكامل"),
    REPORTS_SENSITIVE_VIEW("REPORTS", "الاطلاع على التقارير المالية الحساسة", "عرض تقارير الأرباح والتكاليف وميزان المراجعة"),
    SHIFT_VARIANCE_APPROVE("CASH", "اعتماد فروق الورديات", "اعتماد فروق الجرد النقدي للورديات وتسويتها"),
    PRICE_OVERRIDE_APPROVE("SALES", "اعتماد تجاوز الحد الأدنى للسعر", "السماح بالبيع أقل من السعر الأدنى المسموح أو تجاوز سقف الخصم"),
    CREDIT_LIMIT_OVERRIDE("SALES", "اعتماد تجاوز الحد الائتماني للعميل", "السماح بالبيع الآجل فوق الحد الائتماني المحدد للعميل"),
    RETURN_UNLINKED_OVERRIDE("SALES", "اعتماد مرتجع استثنائي بدون فاتورة", "السماح بتسجيل مرتجع موثق غير مرتبط بفاتورة بيع أصلية"),
    EXCHANGE_EXECUTE("SALES", "تنفيذ استبدال مبيعات", "استبدال صنف مباع بصنف جديد مع تسوية فرق السعر"),
    CAPITAL_OPERATE("FINANCIAL", "إدارة رأس المال وحسابات الشركاء", "تسجيل إيداعات وسحوبات رأس مال المالك والشركاء"),
    WALLET_ADJUST("WALLETS", "تسوية ومطابقة المحافظ", "إجراء تسويات مالية استثنائية لأرصدة المحافظ")
}

/**
 * Unique Device Installation Identity generated with a secure random UUID (Requirement 9).
 * Never uses hardware IMEI or static constants for installation identity.
 */
data class InstallationIdentity(
    val installationId: String,
    val shopId: String,
    val registeredOwnerUserId: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val status: String = "ACTIVE"
)

/**
 * Verified Application Session bound to an authenticated User, Shop Membership, Role, Permissions, and Installation ID (Requirements 5, 6, 7).
 */
data class AuthenticatedSession(
    val sessionToken: String,
    val userId: String,
    val userFullName: String,
    val shopId: String,
    val roleId: String,
    val roleCode: String,
    val grantedPermissions: Set<PermissionCode>,
    val installationId: String,
    val authProvider: String = "PBKDF2_HMAC_SHA256",
    val issuedAtMillis: Long,
    val expiresAtMillis: Long
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis > expiresAtMillis

    fun hasPermission(permission: PermissionCode): Boolean =
        grantedPermissions.contains(permission)
}

/**
 * Rate-limiting state for failed PIN/password attempts (Requirement 39).
 */
data class FailedAttemptTracker(
    val failedCount: Int = 0,
    val lockedUntilMillis: Long = 0L,
    val lastAttemptMillis: Long = 0L
)

/**
 * Role-Based Access Control (RBAC) Matrix & Security Foundation (Requirements 16 & 17).
 * Never stores hardcoded passwords or plain-text secrets in source code.
 */
object RolePermissionMatrix {

    fun hasPermission(roleCode: String, permission: PermissionCode): Boolean {
        val normalizedRole = UserRoleType.entries.firstOrNull { it.name.equals(roleCode, ignoreCase = true) }
            ?: return false
        return defaultPermissionsForRole(normalizedRole).contains(permission)
    }

    fun defaultPermissionsForRole(role: UserRoleType): Set<PermissionCode> = when (role) {
        UserRoleType.OWNER -> PermissionCode.entries.toSet()
        UserRoleType.MANAGER -> PermissionCode.entries.filterNot {
            it == PermissionCode.BACKUP_RESTORE || it == PermissionCode.USER_PERMISSION_MANAGE
        }.toSet()
        UserRoleType.CASHIER -> setOf(
            PermissionCode.SALE_CREATE,
            PermissionCode.EXCHANGE_EXECUTE,
            PermissionCode.WALLET_OPERATE,
            PermissionCode.CASH_OPERATE,
            PermissionCode.EXPENSE_CREATE
        )
        UserRoleType.TECHNICIAN -> setOf(
            PermissionCode.MAINTENANCE_MANAGE
        )
        UserRoleType.INVENTORY -> setOf(
            PermissionCode.PURCHASE_CREATE,
            PermissionCode.INVENTORY_ADJUST,
            PermissionCode.PRICE_CHANGE
        )
        UserRoleType.STAFF -> setOf(
            PermissionCode.SALE_CREATE,
            PermissionCode.MAINTENANCE_MANAGE
        )
    }

    /**
     * Enforces route-level RBAC so lower-privileged users cannot bypass UI restrictions via direct navigation or deep links.
     */
    fun canRoleAccessRoute(roleCode: String, route: String): Boolean {
        val normalizedRole = UserRoleType.entries.firstOrNull { it.name.equals(roleCode, ignoreCase = true) }
            ?: return false
        if (normalizedRole == UserRoleType.OWNER) return true
        val perms = defaultPermissionsForRole(normalizedRole)
        return when (route) {
            "dashboard", "more" -> true
            "sales", "customers" -> perms.contains(PermissionCode.SALE_CREATE)
            "products" -> perms.contains(PermissionCode.SALE_CREATE) ||
                perms.contains(PermissionCode.INVENTORY_ADJUST) ||
                perms.contains(PermissionCode.PRICE_CHANGE) ||
                perms.contains(PermissionCode.PURCHASE_CREATE) ||
                perms.contains(PermissionCode.MAINTENANCE_MANAGE)
            "purchases", "suppliers" -> perms.contains(PermissionCode.PURCHASE_CREATE) ||
                perms.contains(PermissionCode.INVENTORY_ADJUST)
            "wallets", "sms_intelligence" -> perms.contains(PermissionCode.WALLET_OPERATE)
            "cash_drawer" -> perms.contains(PermissionCode.CASH_OPERATE)
            "expenses" -> perms.contains(PermissionCode.EXPENSE_CREATE)
            "maintenance" -> perms.contains(PermissionCode.MAINTENANCE_MANAGE) ||
                perms.contains(PermissionCode.SALE_CREATE)
            "ledger", "audit", "reports" -> perms.contains(PermissionCode.AUDIT_VIEW)
            "sync" -> perms.contains(PermissionCode.CONFLICT_RESOLVE) || perms.contains(PermissionCode.AUDIT_VIEW)
            "backup" -> perms.contains(PermissionCode.BACKUP_RESTORE)
            "settings", "users_permissions", "diagnostics" -> perms.contains(PermissionCode.USER_PERMISSION_MANAGE)
            else -> false
        }
    }

    fun buildAllPermissionEntities(timestamp: Long): List<PermissionEntity> =
        PermissionCode.entries.map { perm ->
            PermissionEntity(
                permissionCode = perm.name,
                moduleName = perm.moduleName,
                titleArabic = perm.titleArabic,
                descriptionArabic = perm.descriptionArabic,
                createdAt = timestamp
            )
        }
}

class AuthSecurityManager(
    private val coreShopAuthDao: CoreShopAuthDao
) {
    companion object {
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val PBKDF2_ITERATIONS = 65_536
        const val PBKDF2_KEY_BITS = 256
        const val SALT_BYTES_LEN = 16
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 15 * 60 * 1000L // 15 minutes lockout
        const val DEFAULT_SESSION_TTL_MS = 12 * 60 * 60 * 1000L // 12 hours session TTL
        const val KEY_INSTALLATION_ID = "installation_identity_uuid_v2"
    }

    private val secureRandom = SecureRandom()
    private val failedAttemptsMap = ConcurrentHashMap<String, FailedAttemptTracker>()

    private val _activeSession = MutableStateFlow<AuthenticatedSession?>(null)
    val activeSession: StateFlow<AuthenticatedSession?> = _activeSession.asStateFlow()

    private val _isSessionLocked = MutableStateFlow(false)
    val isSessionLocked: StateFlow<Boolean> = _isSessionLocked.asStateFlow()

    /**
     * Generates or loads a persistent random UUID installation identity for this device (Requirement 9).
     */
    suspend fun ensureInstallationIdentity(
        shopId: String,
        ownerUserId: String,
        timestamp: Long = System.currentTimeMillis()
    ): InstallationIdentity {
        val existingSetting = coreShopAuthDao.getSetting(shopId, KEY_INSTALLATION_ID)
        if (existingSetting != null && existingSetting.settingValue.startsWith("INST-")) {
            return InstallationIdentity(
                installationId = existingSetting.settingValue,
                shopId = shopId,
                registeredOwnerUserId = ownerUserId,
                createdAt = existingSetting.createdAt,
                lastSeenAt = timestamp,
                status = "ACTIVE"
            )
        }
        val newId = "INST-${UUID.randomUUID().toString().lowercase()}"
        coreShopAuthDao.upsertSetting(
            AppSettingEntity(
                localId = IdGenerator.newPrefixedId("SET"),
                shopId = shopId,
                settingKey = KEY_INSTALLATION_ID,
                settingValue = newId,
                valueType = "STRING",
                updatedByUserId = ownerUserId,
                deviceId = newId,
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
        return InstallationIdentity(
            installationId = newId,
            shopId = shopId,
            registeredOwnerUserId = ownerUserId,
            createdAt = timestamp,
            lastSeenAt = timestamp,
            status = "ACTIVE"
        )
    }

    /**
     * Hashes an operational PIN using PBKDF2-HMAC-SHA256 with a cryptographic random 128-bit salt
     * and 65,536 iterations (Requirement 39).
     * Format: `pbkdf2$65536$<saltHex>$<derivedKeyHex>`
     */
    fun hashOperationalPin(
        shopId: String,
        userId: String,
        rawPin: String,
        explicitSalt: ByteArray? = null
    ): String {
        val salt = explicitSalt ?: ByteArray(SALT_BYTES_LEN).also { secureRandom.nextBytes(it) }
        val domainBoundChars = "$shopId:$userId:${rawPin.trim()}".toCharArray()
        val spec = PBEKeySpec(domainBoundChars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val derived = factory.generateSecret(spec).encoded
        spec.clearPassword()
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = derived.joinToString("") { "%02x".format(it) }
        return "pbkdf2$$PBKDF2_ITERATIONS$$saltHex$$hashHex"
    }

    /**
     * Verifies an operational PIN against a stored PBKDF2-HMAC-SHA256 hash using constant-time comparison,
     * with seamless fallback verification for legacy v1 hashes during upgrade.
     */
    fun verifyOperationalPin(
        shopId: String,
        userId: String,
        rawPin: String,
        storedHash: String
    ): Boolean {
        if (storedHash.startsWith("pbkdf2$")) {
            val parts = storedHash.split("$")
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = hexToBytes(parts[2]) ?: return false
            val expectedBytes = hexToBytes(parts[3]) ?: return false
            val domainBoundChars = "$shopId:$userId:${rawPin.trim()}".toCharArray()
            val spec = PBEKeySpec(domainBoundChars, salt, iterations, expectedBytes.size * 8)
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val computedBytes = factory.generateSecret(spec).encoded
            spec.clearPassword()
            return MessageDigest.isEqual(computedBytes, expectedBytes)
        }
        // Legacy fallback verification
        val legacyHash = IdGenerator.sha256Hex("mekkaphone-pin-v1:$shopId:$userId:${rawPin.trim()}")
        return MessageDigest.isEqual(
            legacyHash.toByteArray(StandardCharsets.UTF_8),
            storedHash.toByteArray(StandardCharsets.UTF_8)
        )
    }

    /**
     * Authenticates a user with PBKDF2 PIN verification, rate-limiting lockout protection,
     * shop membership validation, and issues a verified `AuthenticatedSession` (Requirements 5, 6, 7, 39).
     */
    suspend fun authenticateWithPin(
        userId: String,
        rawPin: String,
        expectedShopId: String? = null,
        installationId: String = "INST-${UUID.randomUUID()}",
        sessionTtlMillis: Long = DEFAULT_SESSION_TTL_MS,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<AuthenticatedSession> {
        val user = coreShopAuthDao.getUserById(userId)
            ?: return AppResult.Error("المستخدم غير موجود", "AUTH_USER_NOT_FOUND")

        if (!user.isActive || user.isDeleted) {
            return AppResult.Error("حساب المستخدم موقوف أو غير نشط", "AUTH_USER_INACTIVE")
        }

        if (expectedShopId != null && user.shopId != expectedShopId) {
            return AppResult.Error(
                "رفض الوصول: المستخدم لا ينتمي إلى هذا المتجر (Shop Isolation Violation)",
                "WRONG_SHOP_MEMBERSHIP"
            )
        }

        val attemptKey = "${user.shopId}:${user.localId}"
        val tracker = failedAttemptsMap[attemptKey]
        if (tracker != null && tracker.lockedUntilMillis > timestamp) {
            val remainingSec = ((tracker.lockedUntilMillis - timestamp) / 1000L).coerceAtLeast(1L)
            return AppResult.Error(
                "تم قفل الحساب مؤقتاً بسبب تجاوز الحد الأقصى للمحاولات الخاطئة. يرجى الانتظار $remainingSec ثانية",
                "AUTH_ACCOUNT_LOCKED_RATE_LIMIT"
            )
        }

        val isValidPin = verifyOperationalPin(
            shopId = user.shopId,
            userId = user.localId,
            rawPin = rawPin,
            storedHash = user.pinHashSha256.orEmpty()
        )
        if (!isValidPin) {
            val nextFailed = (tracker?.failedCount ?: 0) + 1
            val lockUntil = if (nextFailed >= MAX_FAILED_ATTEMPTS) timestamp + LOCKOUT_DURATION_MS else 0L
            failedAttemptsMap[attemptKey] = FailedAttemptTracker(
                failedCount = nextFailed,
                lockedUntilMillis = lockUntil,
                lastAttemptMillis = timestamp
            )
            val msg = if (lockUntil > 0L) {
                "رمز PIN غير صحيح — تم قفل الحساب مؤقتاً لحماية البيانات بعد $MAX_FAILED_ATTEMPTS محاولات فاشلة"
            } else {
                "رمز PIN غير صحيح (محاولة $nextFailed من $MAX_FAILED_ATTEMPTS)"
            }
            return AppResult.Error(msg, "AUTH_INVALID_PIN")
        }

        // Reset failed attempts on successful authentication
        failedAttemptsMap.remove(attemptKey)

        // Automatically upgrade legacy hash to PBKDF2-HMAC-SHA256 if needed
        val currentHash = user.pinHashSha256.orEmpty()
        if (!currentHash.startsWith("pbkdf2$")) {
            val upgradedHash = hashOperationalPin(user.shopId, user.localId, rawPin)
            coreShopAuthDao.updateUser(user.copy(pinHashSha256 = upgradedHash, updatedAt = timestamp))
        }

        val grantedCodes = coreShopAuthDao.getPermissionCodesForRole(user.roleId)
        val grantedPerms = grantedCodes.mapNotNull { code ->
            PermissionCode.entries.firstOrNull { it.name == code }
        }.toSet()

        val session = AuthenticatedSession(
            sessionToken = "SES-${UUID.randomUUID()}",
            userId = user.localId,
            userFullName = user.fullNameArabic,
            shopId = user.shopId,
            roleId = user.roleId,
            roleCode = user.roleCode,
            grantedPermissions = grantedPerms,
            installationId = installationId,
            issuedAtMillis = timestamp,
            expiresAtMillis = timestamp + sessionTtlMillis
        )
        _activeSession.value = session
        _isSessionLocked.value = false
        return AppResult.Success(session)
    }

    /**
     * Establishes or restores a verified session for an active user after verifying their database membership & role.
     */
    suspend fun establishSessionForUser(
        userId: String,
        expectedShopId: String? = null,
        installationId: String = "INST-${UUID.randomUUID()}",
        sessionTtlMillis: Long = DEFAULT_SESSION_TTL_MS,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<AuthenticatedSession> {
        val user = coreShopAuthDao.getUserById(userId)
            ?: return AppResult.Error("المستخدم غير موجود", "AUTH_USER_NOT_FOUND")
        if (!user.isActive || user.isDeleted) {
            return AppResult.Error("حساب المستخدم غير نشط", "AUTH_USER_INACTIVE")
        }
        if (expectedShopId != null && user.shopId != expectedShopId) {
            return AppResult.Error("رفض الوصول: عدم تطابق المتجر", "WRONG_SHOP_MEMBERSHIP")
        }
        val grantedCodes = coreShopAuthDao.getPermissionCodesForRole(user.roleId)
        val grantedPerms = grantedCodes.mapNotNull { code ->
            PermissionCode.entries.firstOrNull { it.name == code }
        }.toSet()
        val session = AuthenticatedSession(
            sessionToken = "SES-${UUID.randomUUID()}",
            userId = user.localId,
            userFullName = user.fullNameArabic,
            shopId = user.shopId,
            roleId = user.roleId,
            roleCode = user.roleCode,
            grantedPermissions = grantedPerms,
            installationId = installationId,
            issuedAtMillis = timestamp,
            expiresAtMillis = timestamp + sessionTtlMillis
        )
        _activeSession.value = session
        _isSessionLocked.value = false
        return AppResult.Success(session)
    }

    /**
     * Signs out and locks the current authenticated session (Requirement 6).
     */
    fun signOut() {
        _activeSession.value = null
        _isSessionLocked.value = true
    }

    /**
     * Validates an `AuthenticatedSession` for expiration, shop isolation, and required permission (Requirements 5, 6, 7).
     */
    suspend fun authorizeSessionAction(
        session: AuthenticatedSession?,
        requiredPermission: PermissionCode,
        targetShopId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): AppResult<UserEntity> {
        if (session == null) {
            return AppResult.Error("يجب تسجيل الدخول أولاً لتنفيذ هذه العملية", "UNAUTHENTICATED_SESSION")
        }
        if (session.isExpired(nowMillis)) {
            _activeSession.value = null
            return AppResult.Error("انتهت صلاحية الجلسة — يرجى إعادة تسجيل الدخول", "SESSION_EXPIRED")
        }
        if (session.shopId != targetShopId) {
            return AppResult.Error(
                "رفض أمني صارم: لا يمكن لمستخدم المتجر (${session.shopId}) الوصول لبيانات المتجر ($targetShopId)",
                "CROSS_SHOP_ACCESS_DENIED"
            )
        }
        return authorizeAction(
            userId = session.userId,
            requiredPermission = requiredPermission,
            expectedShopId = targetShopId
        )
    }

    /**
     * Verifies whether the user holds the required permission and belongs to `expectedShopId` (if provided).
     */
    suspend fun authorizeAction(
        userId: String,
        requiredPermission: PermissionCode,
        expectedShopId: String? = null
    ): AppResult<UserEntity> {
        if (_isSessionLocked.value) {
            return AppResult.Error("الجلسة مقفلة — يجب تسجيل الدخول برمز PIN أولاً", "UNAUTHENTICATED_SESSION")
        }
        val currentSession = _activeSession.value
        if (currentSession != null) {
            if (currentSession.isExpired()) {
                _activeSession.value = null
                _isSessionLocked.value = true
                return AppResult.Error("انتهت صلاحية الجلسة — يرجى إعادة تسجيل الدخول", "SESSION_EXPIRED")
            }
            if (currentSession.roleCode != "OWNER" && currentSession.userId != userId) {
                return AppResult.Error(
                    "رفض أمني لتصعيد الصلاحيات: الجلسة النشطة (${currentSession.userId}) لا تملك صلاحية انتحال هوية ($userId)",
                    "PRIVILEGE_ESCALATION_BLOCKED"
                )
            }
        }

        val user = coreShopAuthDao.getUserById(userId)
            ?: return AppResult.Error("المستخدم غير موجود أو الجلسة غير صالحة", "AUTH_USER_NOT_FOUND")

        if (!user.isActive || user.isDeleted) {
            return AppResult.Error("حساب المستخدم موقوف حالياً", "AUTH_USER_INACTIVE")
        }

        if (expectedShopId != null && user.shopId != expectedShopId) {
            return AppResult.Error(
                "رفض أمني صارم: المستخدم لا ينتمي إلى المتجر المطلوب ($expectedShopId)",
                "CROSS_SHOP_ACCESS_DENIED"
            )
        }

        val grantedCodes = coreShopAuthDao.getPermissionCodesForRole(user.roleId)
        if (!grantedCodes.contains(requiredPermission.name)) {
            return AppResult.Error(
                "صلاحية غير كافية: الدور (${user.roleCode}) لا يملك صلاحية (${requiredPermission.titleArabic})",
                "PERMISSION_DENIED"
            )
        }
        return AppResult.Success(user)
    }

    /**
     * Provisions the 5 standard roles and their default permission matrix for a shop.
     */
    suspend fun ensureStandardRolesAndPermissions(
        shopId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): Map<UserRoleType, RoleEntity> {
        val allPerms = RolePermissionMatrix.buildAllPermissionEntities(timestamp)
        coreShopAuthDao.insertPermissions(allPerms)

        val rolesList = mutableListOf<RoleEntity>()
        val crossRefs = mutableListOf<RolePermissionCrossRef>()
        val roleMap = mutableMapOf<UserRoleType, RoleEntity>()

        for (roleType in UserRoleType.entries) {
            val existing = coreShopAuthDao.getRoleByCode(shopId, roleType.name)
            val roleEntity = existing ?: RoleEntity(
                localId = "ROLE-${shopId.takeLast(6)}-${roleType.name}",
                shopId = shopId,
                roleCode = roleType.name,
                nameArabic = roleType.arabicTitle,
                descriptionArabic = "الدور القياسي: ${roleType.arabicTitle}",
                isSystemRole = true,
                deviceId = deviceId,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            rolesList.add(roleEntity)
            roleMap[roleType] = roleEntity

            for (perm in RolePermissionMatrix.defaultPermissionsForRole(roleType)) {
                crossRefs.add(
                    RolePermissionCrossRef(
                        roleId = roleEntity.localId,
                        permissionCode = perm.name,
                        grantedAt = timestamp
                    )
                )
            }
        }

        coreShopAuthDao.insertRoles(rolesList)
        coreShopAuthDao.insertRolePermissions(crossRefs)
        return roleMap
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            null
        }
    }
}
