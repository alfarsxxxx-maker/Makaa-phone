package com.example.database

import androidx.room.TypeConverter
import com.example.core.SyncState
import com.example.database.entities.FinancialAccountType
import com.example.database.entities.FinancialTransactionType
import com.example.database.entities.InventoryMovementType
import com.example.database.entities.LedgerTransactionStatus
import com.example.database.entities.MaintenanceStatus
import com.example.database.entities.SerialLifecycleStatus

class DatabaseConverters {
    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncState =
        runCatching { SyncState.valueOf(value) }.getOrDefault(SyncState.PENDING)

    @TypeConverter
    fun fromInventoryMovementType(value: InventoryMovementType): String = value.name

    @TypeConverter
    fun toInventoryMovementType(value: String): InventoryMovementType =
        InventoryMovementType.valueOf(value)

    @TypeConverter
    fun fromSerialLifecycleStatus(value: SerialLifecycleStatus): String = value.name

    @TypeConverter
    fun toSerialLifecycleStatus(value: String): SerialLifecycleStatus =
        SerialLifecycleStatus.valueOf(value)

    @TypeConverter
    fun fromMaintenanceStatus(value: MaintenanceStatus): String = value.name

    @TypeConverter
    fun toMaintenanceStatus(value: String): MaintenanceStatus =
        runCatching { MaintenanceStatus.valueOf(value) }.getOrDefault(MaintenanceStatus.RECEIVED)

    @TypeConverter
    fun fromFinancialTransactionType(value: FinancialTransactionType): String = value.name

    @TypeConverter
    fun toFinancialTransactionType(value: String): FinancialTransactionType =
        FinancialTransactionType.valueOf(value)

    @TypeConverter
    fun fromLedgerTransactionStatus(value: LedgerTransactionStatus): String = value.name

    @TypeConverter
    fun toLedgerTransactionStatus(value: String): LedgerTransactionStatus =
        LedgerTransactionStatus.valueOf(value)

    @TypeConverter
    fun fromFinancialAccountType(value: FinancialAccountType): String = value.name

    @TypeConverter
    fun toFinancialAccountType(value: String): FinancialAccountType =
        FinancialAccountType.valueOf(value)
}
