package com.example.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.database.entities.AppSettingEntity
import com.example.database.entities.DeviceEntity
import com.example.database.entities.PermissionEntity
import com.example.database.entities.RoleEntity
import com.example.database.entities.RolePermissionCrossRef
import com.example.database.entities.ShopEntity
import com.example.database.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoreShopAuthDao {
    @Query("SELECT * FROM shops WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun observeShops(): Flow<List<ShopEntity>>

    @Query("SELECT * FROM shops WHERE localId = :shopId LIMIT 1")
    fun observeShopById(shopId: String): Flow<ShopEntity?>

    @Query("SELECT * FROM shops WHERE localId = :shopId LIMIT 1")
    suspend fun getShopById(shopId: String): ShopEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShop(shop: ShopEntity)

    @Update
    suspend fun updateShop(shop: ShopEntity)

    @Query("SELECT * FROM roles WHERE shopId = :shopId ORDER BY createdAt ASC")
    fun observeRoles(shopId: String): Flow<List<RoleEntity>>

    @Query("SELECT * FROM roles WHERE shopId = :shopId AND roleCode = :roleCode LIMIT 1")
    suspend fun getRoleByCode(shopId: String, roleCode: String): RoleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoles(roles: List<RoleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissions(permissions: List<PermissionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRolePermissions(crossRefs: List<RolePermissionCrossRef>)

    @Query(
        """
        SELECT p.* FROM permissions p
        INNER JOIN role_permissions rp ON p.permissionCode = rp.permissionCode
        WHERE rp.roleId = :roleId
        ORDER BY p.moduleName ASC, p.permissionCode ASC
        """
    )
    fun observePermissionsForRole(roleId: String): Flow<List<PermissionEntity>>

    @Query(
        """
        SELECT p.permissionCode FROM permissions p
        INNER JOIN role_permissions rp ON p.permissionCode = rp.permissionCode
        WHERE rp.roleId = :roleId
        """
    )
    suspend fun getPermissionCodesForRole(roleId: String): List<String>

    @Query("SELECT * FROM users WHERE shopId = :shopId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun observeUsers(shopId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE shopId = :shopId AND isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun getAllUsers(shopId: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE localId = :userId LIMIT 1")
    fun observeUserById(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE localId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity)

    @Query("SELECT * FROM devices WHERE shopId = :shopId ORDER BY lastActiveAt DESC")
    fun observeDevices(shopId: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM app_settings WHERE shopId = :shopId ORDER BY settingKey ASC")
    fun observeSettings(shopId: String): Flow<List<AppSettingEntity>>

    @Query("SELECT * FROM app_settings WHERE shopId = :shopId")
    suspend fun getAllSettings(shopId: String): List<AppSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: List<AppSettingEntity>)

    @Query("SELECT * FROM app_settings WHERE shopId = :shopId AND settingKey = :key LIMIT 1")
    suspend fun getSetting(shopId: String, key: String): AppSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetting(setting: AppSettingEntity)
}
