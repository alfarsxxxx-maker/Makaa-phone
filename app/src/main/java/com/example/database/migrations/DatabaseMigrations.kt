package com.example.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Database Migration Strategy & SQLite Invariant Enforcement Triggers (Requirements 6 & 7).
 * Never uses destructive migration (`fallbackToDestructiveMigration` is strictly forbidden).
 */
object DatabaseMigrations {

    const val CURRENT_SCHEMA_VERSION = 4

    /**
     * Explicit Migration 1 -> 2:
     * Ensures all operational indexes and immutable ledger / inventory triggers are installed
     * while preserving 100% of existing shop, ledger, and inventory data.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_financial_transactions_shopId_transactionType_transactionTimestamp` " +
                    "ON `financial_transactions` (`shopId`, `transactionType`, `transactionTimestamp`)"
            )
            installDatabaseIntegrityTriggers(db)
        }
    }

    /**
     * Explicit Migration 2 -> 3 for V4 Advanced Operations & Business Core:
     * Adds V4 columns and price_history audit table non-destructively and idempotently.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "customers", "defaultCreditDays", "INTEGER NOT NULL DEFAULT 30")

            addColumnIfMissing(db, "products", "wholesalePriceMinor", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "specialCustomerPriceMinor", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "maxDiscountMinor", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "promotionalPriceMinor", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "isPromotionActive", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "products", "reorderPointQty", "INTEGER NOT NULL DEFAULT 5")

            addColumnIfMissing(db, "sales", "dueDateTimestamp", "INTEGER")
            addColumnIfMissing(db, "sales", "shiftId", "TEXT")
            addColumnIfMissing(db, "sales", "pricingTierUsed", "TEXT NOT NULL DEFAULT 'RETAIL'")

            addColumnIfMissing(db, "sale_items", "returnedQuantity", "INTEGER NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "maintenance_orders", "isCustomerApproved", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "maintenance_orders", "customerApprovedAt", "INTEGER")
            addColumnIfMissing(db, "maintenance_orders", "isCustomerNotified", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "maintenance_orders", "customerNotifiedAt", "INTEGER")
            addColumnIfMissing(db, "maintenance_orders", "isTestingPassed", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "maintenance_orders", "expectedDeliveryAt", "INTEGER")
            addColumnIfMissing(db, "maintenance_orders", "closedAt", "INTEGER")

            addColumnIfMissing(db, "refunds", "returnType", "TEXT NOT NULL DEFAULT 'FULL_RETURN'")
            addColumnIfMissing(db, "refunds", "settlementMode", "TEXT NOT NULL DEFAULT 'CASH_REFUND'")
            addColumnIfMissing(db, "refunds", "productCondition", "TEXT NOT NULL DEFAULT 'RESTOCKABLE'")
            addColumnIfMissing(db, "refunds", "replacementSaleId", "TEXT")
            addColumnIfMissing(db, "refunds", "priceDifferenceMinor", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "refunds", "returnedItemsSnapshotJson", "TEXT NOT NULL DEFAULT '[]'")

            addColumnIfMissing(db, "cash_sessions", "openingCashInputMinor", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "cash_sessions", "openingWalletBalancesJson", "TEXT NOT NULL DEFAULT '{}'")
            addColumnIfMissing(db, "cash_sessions", "closingWalletBalancesJson", "TEXT NOT NULL DEFAULT '{}'")
            addColumnIfMissing(db, "cash_sessions", "varianceReasonArabic", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "cash_sessions", "approvedByUserId", "TEXT")
            addColumnIfMissing(db, "cash_sessions", "approvalStatus", "TEXT NOT NULL DEFAULT 'NOT_REQUIRED'")
            addColumnIfMissing(db, "cash_sessions", "explicitAdjustmentTxId", "TEXT")

            addColumnIfMissing(db, "financial_transactions", "shiftId", "TEXT")

            addColumnIfMissing(db, "audit_logs", "reasonArabic", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "audit_logs", "referenceNumber", "TEXT NOT NULL DEFAULT ''")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `price_history` (
                    `localId` TEXT NOT NULL,
                    `cloudId` TEXT,
                    `shopId` TEXT NOT NULL,
                    `productId` TEXT NOT NULL,
                    `priceType` TEXT NOT NULL,
                    `oldPriceMinor` INTEGER NOT NULL,
                    `newPriceMinor` INTEGER NOT NULL,
                    `changedByUserId` TEXT NOT NULL,
                    `changedByRoleCode` TEXT NOT NULL,
                    `reasonArabic` TEXT NOT NULL,
                    `deviceId` TEXT NOT NULL,
                    `changedAt` INTEGER NOT NULL,
                    `syncState` TEXT NOT NULL,
                    PRIMARY KEY(`localId`),
                    FOREIGN KEY(`shopId`) REFERENCES `shops`(`localId`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`productId`) REFERENCES `products`(`localId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`changedByUserId`) REFERENCES `users`(`localId`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_history_shopId_productId_changedAt` ON `price_history` (`shopId`, `productId`, `changedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_history_productId` ON `price_history` (`productId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_history_changedByUserId` ON `price_history` (`changedByUserId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_history_syncState` ON `price_history` (`syncState`)")

            installDatabaseIntegrityTriggers(db)
        }
    }

    /**
     * Explicit Migration 3 -> 4 for P0 Critical Business Core:
     * Adds `operationalStatus` column to `wallets` table non-destructively and idempotently.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "wallets", "operationalStatus", "TEXT NOT NULL DEFAULT 'ACTIVE'")
            installDatabaseIntegrityTriggers(db)
        }
    }

    private fun addColumnIfMissing(
        db: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnDefinition: String
    ) {
        var exists = false
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0 && cursor.getString(nameIdx).equals(columnName, ignoreCase = true)) {
                    exists = true
                    break
                }
            }
        }
        if (!exists) {
            db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDefinition")
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4
    )

    /**
     * Installs database-level SQLite triggers to protect core business invariants (Requirement 6, 8, 9, 12):
     * 1. No deletion of financial ledger transactions or lines.
     * 2. No mutation of financial transaction amounts, types, or accounts after posting.
     * 3. No zero or negative transaction amounts.
     * 4. No impossible inventory movement quantities (quantity <= 0, quantityAfter < 0, unitCostMinor < 0).
     * 5. No deletion of historical inventory movements or audit logs.
     */
    fun installDatabaseIntegrityTriggers(db: SupportSQLiteDatabase) {
        // 1. Prevent deleting financial transactions
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_prevent_ledger_delete
            BEFORE DELETE ON financial_transactions
            BEGIN
                SELECT RAISE(ABORT, 'LEDGER_IMMUTABLE: Cannot delete financial ledger entries');
            END;
            """.trimIndent()
        )

        // 2. Prevent deleting financial transaction lines
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_prevent_ledger_line_delete
            BEFORE DELETE ON transaction_lines
            BEGIN
                SELECT RAISE(ABORT, 'LEDGER_IMMUTABLE: Cannot delete financial transaction lines');
            END;
            """.trimIndent()
        )

        // 3. Prevent mutating posted financial transaction amounts, accounts, or types
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_prevent_ledger_amount_mutation
            BEFORE UPDATE ON financial_transactions
            WHEN OLD.amountMinor != NEW.amountMinor
              OR OLD.sourceAccountId != NEW.sourceAccountId
              OR OLD.destinationAccountId != NEW.destinationAccountId
              OR OLD.transactionType != NEW.transactionType
              OR OLD.shopId != NEW.shopId
            BEGIN
                SELECT RAISE(ABORT, 'LEDGER_IMMUTABLE: Cannot mutate financial amounts, accounts, or transaction types');
            END;
            """.trimIndent()
        )

        // 4. Require positive amounts on financial transactions
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_validate_ledger_positive_amount
            BEFORE INSERT ON financial_transactions
            WHEN NEW.amountMinor <= 0 OR NEW.feeMinor < 0 OR NEW.commissionMinor < 0
            BEGIN
                SELECT RAISE(ABORT, 'INVALID_LEDGER_AMOUNT: Transaction amount must be positive and fees non-negative');
            END;
            """.trimIndent()
        )

        // 5. Prevent impossible quantities or negative resulting stock on inventory movements
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_validate_inventory_movement_quantities
            BEFORE INSERT ON inventory_movements
            WHEN NEW.quantity <= 0
              OR NEW.quantityAfter < 0
              OR NEW.unitCostMinor < 0
              OR NEW.weightedAvgCostAfterMinor < 0
            BEGIN
                SELECT RAISE(ABORT, 'INVALID_INVENTORY_MOVEMENT: Impossible quantity, negative stock, or negative cost');
            END;
            """.trimIndent()
        )

        // 6. Prevent deleting historical inventory movements
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_prevent_inventory_movement_delete
            BEFORE DELETE ON inventory_movements
            BEGIN
                SELECT RAISE(ABORT, 'INVENTORY_IMMUTABLE: Cannot delete historical inventory movements');
            END;
            """.trimIndent()
        )

        // 7. Prevent deleting audit logs
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_prevent_audit_log_delete
            BEFORE DELETE ON audit_logs
            BEGIN
                SELECT RAISE(ABORT, 'AUDIT_IMMUTABLE: Cannot delete audit trail records');
            END;
            """.trimIndent()
        )
    }
}
