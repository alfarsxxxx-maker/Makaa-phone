package com.example.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.database.dao.CatalogInventoryDao
import com.example.database.dao.CoreShopAuthDao
import com.example.database.dao.FinancialLedgerDao
import com.example.database.dao.OperationsDao
import com.example.database.dao.SystemIntegrityDao
import com.example.database.entities.AppSettingEntity
import com.example.database.entities.AuditLogEntity
import com.example.database.entities.BackupMetadataEntity
import com.example.database.entities.BrandEntity
import com.example.database.entities.CashDrawerEntity
import com.example.database.entities.CashSessionEntity
import com.example.database.entities.CategoryEntity
import com.example.database.entities.CustomerEntity
import com.example.database.entities.DeviceEntity
import com.example.database.entities.ExpenseEntity
import com.example.database.entities.InventoryEntity
import com.example.database.entities.InventoryMovementEntity
import com.example.database.entities.MaintenanceOrderEntity
import com.example.database.entities.MaintenancePartEntity
import com.example.database.entities.PendingTransactionEntity
import com.example.database.entities.PermissionEntity
import com.example.database.entities.PriceHistoryEntity
import com.example.database.entities.ProductEntity
import com.example.database.entities.ProductVariantEntity
import com.example.database.entities.PurchaseEntity
import com.example.database.entities.PurchaseItemEntity
import com.example.database.entities.RefundEntity
import com.example.database.entities.RoleEntity
import com.example.database.entities.RolePermissionCrossRef
import com.example.database.entities.SaleEntity
import com.example.database.entities.SaleItemEntity
import com.example.database.entities.SerialNumberEntity
import com.example.database.entities.ShopEntity
import com.example.database.entities.SmsMessageEntity
import com.example.database.entities.SmsParsingResultEntity
import com.example.database.entities.SupplierEntity
import com.example.database.entities.SyncConflictEntity
import com.example.database.entities.SyncQueueEntity
import com.example.database.entities.TransactionEntity
import com.example.database.entities.TransactionLineEntity
import com.example.database.entities.TransferEntity
import com.example.database.entities.UserEntity
import com.example.database.entities.WalletEntity
import com.example.database.entities.WalletProviderEntity
import com.example.database.migrations.DatabaseMigrations

@Database(
    entities = [
        ShopEntity::class,
        RoleEntity::class,
        PermissionEntity::class,
        RolePermissionCrossRef::class,
        UserEntity::class,
        DeviceEntity::class,
        AppSettingEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        CategoryEntity::class,
        BrandEntity::class,
        ProductEntity::class,
        ProductVariantEntity::class,
        PriceHistoryEntity::class,
        SerialNumberEntity::class,
        InventoryEntity::class,
        InventoryMovementEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        MaintenanceOrderEntity::class,
        MaintenancePartEntity::class,
        ExpenseEntity::class,
        RefundEntity::class,
        TransferEntity::class,
        WalletProviderEntity::class,
        WalletEntity::class,
        CashDrawerEntity::class,
        CashSessionEntity::class,
        TransactionEntity::class,
        TransactionLineEntity::class,
        SmsMessageEntity::class,
        SmsParsingResultEntity::class,
        PendingTransactionEntity::class,
        SyncQueueEntity::class,
        SyncConflictEntity::class,
        AuditLogEntity::class,
        BackupMetadataEntity::class
    ],
    version = DatabaseMigrations.CURRENT_SCHEMA_VERSION,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class MekkaPhoneDatabase : RoomDatabase() {

    abstract fun coreShopAuthDao(): CoreShopAuthDao
    abstract fun catalogInventoryDao(): CatalogInventoryDao
    abstract fun operationsDao(): OperationsDao
    abstract fun financialLedgerDao(): FinancialLedgerDao
    abstract fun systemIntegrityDao(): SystemIntegrityDao

    companion object {
        private const val DATABASE_NAME = "mekkaphone_operational.db"

        @Volatile
        private var INSTANCE: MekkaPhoneDatabase? = null

        fun getInstance(context: Context): MekkaPhoneDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): MekkaPhoneDatabase {
            return Room.databaseBuilder(
                context,
                MekkaPhoneDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                        DatabaseMigrations.installDatabaseIntegrityTriggers(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                        DatabaseMigrations.installDatabaseIntegrityTriggers(db)
                    }
                })
                .build()
        }

        /**
         * Creates an in-memory Room database with full foreign-key constraints and SQLite triggers enabled,
         * used for deterministic local JVM / Robolectric testing.
         */
        fun createInMemoryForTesting(context: Context): MekkaPhoneDatabase {
            return Room.inMemoryDatabaseBuilder(context, MekkaPhoneDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                        DatabaseMigrations.installDatabaseIntegrityTriggers(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                        DatabaseMigrations.installDatabaseIntegrityTriggers(db)
                    }
                })
                .build()
        }
    }
}
