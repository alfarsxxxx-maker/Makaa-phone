package com.example.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.SyncState

/**
 * Initial RBAC Roles (Requirement 16).
 */
enum class UserRoleType(val arabicTitle: String) {
    OWNER("مالك المتجر (Owner)"),
    MANAGER("مدير الفرع (Manager)"),
    CASHIER("مسؤول الخزينة والمبيعات (Cashier)"),
    TECHNICIAN("مهندس صيانة (Technician)"),
    INVENTORY("مسؤول المخزون والجرد (Inventory)"),
    STAFF("موظف مبيعات (Staff)")
}

/**
 * Tenant / Shop entity (Requirement 15).
 * Every operational and financial record belongs to a shopId.
 */
@Entity(
    tableName = "shops",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["syncState"])
    ]
)
data class ShopEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val code: String,
    val nameArabic: String,
    val nameEnglish: String,
    val defaultCurrency: String = "EGP",
    val phone: String = "",
    val address: String = "",
    val taxRegistrationNumber: String? = null,
    val isActive: Boolean = true,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)

/**
 * Role definition scoped to a shop.
 */
@Entity(
    tableName = "roles",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "roleCode"], unique = true),
        Index(value = ["shopId"])
    ]
)
data class RoleEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val roleCode: String, // OWNER, MANAGER, CASHIER, TECHNICIAN, STAFF
    val nameArabic: String,
    val descriptionArabic: String,
    val isSystemRole: Boolean = true,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Granular permission definition.
 */
@Entity(
    tableName = "permissions",
    indices = [
        Index(value = ["permissionCode"], unique = true)
    ]
)
data class PermissionEntity(
    @PrimaryKey val permissionCode: String,
    val moduleName: String,
    val titleArabic: String,
    val descriptionArabic: String,
    val createdAt: Long
)

/**
 * Many-to-many mapping between Roles and Permissions.
 */
@Entity(
    tableName = "role_permissions",
    primaryKeys = ["roleId", "permissionCode"],
    foreignKeys = [
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["localId"],
            childColumns = ["roleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PermissionEntity::class,
            parentColumns = ["permissionCode"],
            childColumns = ["permissionCode"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["roleId"]),
        Index(value = ["permissionCode"])
    ]
)
data class RolePermissionCrossRef(
    val roleId: String,
    val permissionCode: String,
    val grantedAt: Long,
    val grantedByUserId: String? = null
)

/**
 * Shop User / Staff Member (Requirement 16).
 * Note: Never stores raw passwords in source code or unhashed fields.
 */
@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = RoleEntity::class,
            parentColumns = ["localId"],
            childColumns = ["roleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "username"], unique = true),
        Index(value = ["firebaseUid"], unique = true),
        Index(value = ["shopId"]),
        Index(value = ["roleId"])
    ]
)
data class UserEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val firebaseUid: String? = null,
    val shopId: String,
    val roleId: String,
    val roleCode: String,
    val username: String,
    val fullNameArabic: String,
    val phone: String = "",
    val email: String? = null,
    val pinHashSha256: String? = null,
    val isActive: Boolean = true,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null,
    val isDeleted: Boolean = false
)

/**
 * Registered operational device for multi-device synchronization and audit attribution.
 */
@Entity(
    tableName = "devices",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["shopId", "hardwareFingerprint"], unique = true),
        Index(value = ["shopId"])
    ]
)
data class DeviceEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val deviceName: String,
    val hardwareFingerprint: String,
    val osVersion: String,
    val appVersion: String,
    val isCurrentDevice: Boolean = false,
    val isAuthorized: Boolean = true,
    val lastActiveAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)

/**
 * Key-value application setting scoped to shopId.
 */
@Entity(
    tableName = "app_settings",
    foreignKeys = [
        ForeignKey(
            entity = ShopEntity::class,
            parentColumns = ["localId"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["shopId", "settingKey"], unique = true),
        Index(value = ["shopId"])
    ]
)
data class AppSettingEntity(
    @PrimaryKey val localId: String,
    val cloudId: String? = null,
    val shopId: String,
    val settingKey: String,
    val settingValue: String,
    val valueType: String = "STRING",
    val isSensitive: Boolean = false,
    val updatedByUserId: String? = null,
    val deviceId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1L,
    val syncState: SyncState = SyncState.PENDING,
    val lastSyncedAt: Long? = null
)
