package com.example.financial

import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.*

/**
 * P0-K — Capital Management Engine.
 * Segregates Equity/Capital completely from Liquid Cash and Wallets:
 * Capital ≠ Cash ≠ Wallets.
 * - Opening Capital: Initial equity contribution.
 * - Owner Injection: Additional capital injected into shop assets (Cash or Wallet).
 * - Owner Withdrawal: Personal drawing / capital return to owner.
 * - Current Capital: Opening Capital + Injections - Withdrawals + Accumulated Net Operating Profit.
 */
data class CapitalStatement(
    val openingCapitalMinor: Long,
    val ownerInjectionsMinor: Long,
    val ownerWithdrawalsMinor: Long,
    val netCapitalContributionsMinor: Long, // Opening + Injections - Withdrawals
    val totalRevenueMinor: Long,
    val totalCogsMinor: Long,
    val totalExpensesMinor: Long,
    val netProfitMinor: Long,
    val currentTotalCapitalMinor: Long, // Net Capital Contributions + Net Profit
    // Segregated Asset/Liability Breakdowns to prove Capital != Cash:
    val cashDrawerBalanceMinor: Long,
    val walletsTotalBalanceMinor: Long,
    val inventoryValueMinor: Long,
    val customerReceivablesMinor: Long,
    val supplierPayablesMinor: Long,
    val netLiquidAssetsMinor: Long, // Cash + Wallets
    val timestamp: Long = System.currentTimeMillis()
)

class CapitalManagementEngine(
    private val database: MekkaPhoneDatabase,
    private val financialLedgerEngine: FinancialLedgerEngine
) {

    /**
     * Records additional capital injected by the owner into the shop's liquid assets (Cash or Wallet).
     * Debit: Cash Drawer or Wallet (Asset increases)
     * Credit: Owner's Equity & Capital (Equity increases)
     */
    suspend fun recordCapitalInjection(
        shopId: String,
        amountMinor: Long,
        destinationAccountType: FinancialAccountType, // CASH_DRAWER or WALLET
        destinationAccountId: String,
        notes: String,
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<TransactionEntity> {
        if (amountMinor <= 0L) {
            return AppResult.Error("مبلغ ضخ رأس المال يجب أن يكون أكبر من الصفر", "INVALID_AMOUNT")
        }
        val desc = "ضخ رأس مال إضافي من المالك: ${Money.ofMinor(amountMinor).formatArabic()}${if (notes.isNotBlank()) " — $notes" else ""}"
        return financialLedgerEngine.postLedgerTransactionInternal(
            shopId = shopId,
            transactionType = FinancialTransactionType.CASH_IN,
            amountMinor = amountMinor,
            sourceAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
            sourceAccountId = AccountingEngine.ACC_OWNER_EQUITY,
            destinationAccountType = destinationAccountType,
            destinationAccountId = destinationAccountId,
            relatedEntityType = "CAPITAL_INJECTION",
            relatedEntityId = destinationAccountId,
            descriptionArabic = desc,
            createdByUserId = actorUserId,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    /**
     * Records a personal capital withdrawal / drawings by the owner.
     * Debit: Owner's Equity & Capital (Equity decreases)
     * Credit: Cash Drawer or Wallet (Asset decreases)
     */
    suspend fun recordCapitalWithdrawal(
        shopId: String,
        amountMinor: Long,
        sourceAccountType: FinancialAccountType, // CASH_DRAWER or WALLET
        sourceAccountId: String,
        notes: String,
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<TransactionEntity> {
        if (amountMinor <= 0L) {
            return AppResult.Error("مبلغ مسحوبات رأس المال يجب أن يكون أكبر من الصفر", "INVALID_AMOUNT")
        }
        val desc = "مسحوبات شخصية للمالك من رأس المال: ${Money.ofMinor(amountMinor).formatArabic()}${if (notes.isNotBlank()) " — $notes" else ""}"
        return financialLedgerEngine.postLedgerTransactionInternal(
            shopId = shopId,
            transactionType = FinancialTransactionType.CASH_OUT,
            amountMinor = amountMinor,
            sourceAccountType = sourceAccountType,
            sourceAccountId = sourceAccountId,
            destinationAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
            destinationAccountId = AccountingEngine.ACC_OWNER_EQUITY,
            relatedEntityType = "CAPITAL_WITHDRAWAL",
            relatedEntityId = sourceAccountId,
            descriptionArabic = desc,
            createdByUserId = actorUserId,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    /**
     * Authoritatively records the initial opening capital contribution into the shop's liquid assets.
     * Debit: Cash Drawer or Wallet (Asset increases)
     * Credit: Owner's Equity & Capital (Equity increases)
     */
    suspend fun recordOpeningCapital(
        shopId: String,
        amountMinor: Long,
        destinationAccountType: FinancialAccountType = FinancialAccountType.CASH_DRAWER,
        destinationAccountId: String = com.example.repository.MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID,
        notes: String = "رأس مال افتتاحي",
        actorUserId: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis()
    ): AppResult<TransactionEntity> {
        if (amountMinor <= 0L) {
            return AppResult.Error("مبلغ رأس المال الافتتاحي يجب أن يكون أكبر من الصفر", "INVALID_AMOUNT")
        }
        val desc = "رأس مال افتتاحي: ${Money.ofMinor(amountMinor).formatArabic()}${if (notes.isNotBlank()) " — $notes" else ""}"
        return financialLedgerEngine.postLedgerTransactionInternal(
            shopId = shopId,
            transactionType = FinancialTransactionType.CASH_IN,
            amountMinor = amountMinor,
            sourceAccountType = FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT,
            sourceAccountId = AccountingEngine.ACC_OWNER_EQUITY,
            destinationAccountType = destinationAccountType,
            destinationAccountId = destinationAccountId,
            relatedEntityType = "OPENING_CAPITAL",
            relatedEntityId = destinationAccountId,
            descriptionArabic = desc,
            createdByUserId = actorUserId,
            deviceId = deviceId,
            timestamp = timestamp
        )
    }

    /**
     * Generates a fully reconciled, authoritative Capital Statement directly from the Double-Entry Ledger.
     */
    suspend fun getCapitalStatement(shopId: String): CapitalStatement {
        val ledgerDao = database.financialLedgerDao()
        val allTx = ledgerDao.getAllTransactionsChronological(shopId).filter { it.status == LedgerTransactionStatus.POSTED }

        var openingCapital = 0L
        var injections = 0L
        var withdrawals = 0L
        var revenue = 0L
        var cogs = 0L
        var expenses = 0L

        for (tx in allTx) {
            when {
                tx.relatedEntityType == "OPENING_CAPITAL" ||
                    (tx.sourceAccountType == FinancialAccountType.EQUITY_OPENING_OR_ADJUSTMENT &&
                        (tx.relatedEntityType == "OPENING_BALANCE" ||
                            (tx.sourceAccountId == AccountingEngine.ACC_OWNER_EQUITY &&
                                tx.destinationAccountType in listOf(FinancialAccountType.CASH_DRAWER, FinancialAccountType.WALLET) &&
                                tx.relatedEntityType !in listOf("CAPITAL_INJECTION", "CAPITAL_WITHDRAWAL", "CASH_DRAWER", "WALLET")))) -> {
                    openingCapital += tx.amountMinor
                }
                tx.relatedEntityType == "CAPITAL_INJECTION" ||
                    (tx.sourceAccountId == AccountingEngine.ACC_OWNER_EQUITY &&
                        tx.destinationAccountType in listOf(FinancialAccountType.CASH_DRAWER, FinancialAccountType.WALLET) &&
                        tx.relatedEntityType !in listOf("OPENING_CAPITAL", "OPENING_BALANCE", "CAPITAL_WITHDRAWAL", "CASH_DRAWER", "WALLET")) -> {
                    injections += tx.amountMinor
                }
                tx.relatedEntityType == "CAPITAL_WITHDRAWAL" ||
                    (tx.destinationAccountId == AccountingEngine.ACC_OWNER_EQUITY &&
                        tx.sourceAccountType in listOf(FinancialAccountType.CASH_DRAWER, FinancialAccountType.WALLET) &&
                        tx.relatedEntityType !in listOf("OPENING_CAPITAL", "OPENING_BALANCE", "CAPITAL_INJECTION", "CASH_DRAWER", "WALLET")) -> {
                    withdrawals += tx.amountMinor
                }
                tx.transactionType == FinancialTransactionType.SALE -> {
                    revenue += tx.amountMinor
                }
                tx.transactionType == FinancialTransactionType.EXPENSE -> {
                    expenses += (tx.amountMinor + tx.feeMinor)
                }
            }
        }

        // COGS and Product Profit from Sale Items
        val allSaleItems = database.operationsDao().getAllSaleItems(shopId)
        cogs = allSaleItems.sumOf { it.unitCostAtSaleMinor * it.quantity }

        val netProfit = (revenue - cogs - expenses).coerceAtLeast(-10_000_000_00L)
        val netCapitalContributions = openingCapital + injections - withdrawals
        val currentTotalCapital = netCapitalContributions + netProfit

        val cashDrawers = ledgerDao.getAllCashDrawers(shopId)
        val cashDrawerBalance = if (cashDrawers.isNotEmpty()) {
            cashDrawers.sumOf {
                ledgerDao.getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, it.localId)
            }
        } else {
            ledgerDao.getDerivedAccountBalanceMinor(shopId, FinancialAccountType.CASH_DRAWER, com.example.repository.MekkaPhoneRepository.DEFAULT_MAIN_DRAWER_ID)
        }
        val wallets = ledgerDao.getAllWallets(shopId)
        val walletsTotalBalance = wallets.sumOf {
            ledgerDao.getDerivedAccountBalanceMinor(shopId, FinancialAccountType.WALLET, it.localId)
        }

        val allInventory = database.catalogInventoryDao().getProductInventoryProjections(shopId)
        val inventoryValue = allInventory.sumOf { it.stockValueMinor }

        val customers = database.catalogInventoryDao().getAllCustomers(shopId)
        val customerReceivables = customers.sumOf {
            ledgerDao.getDerivedAccountBalanceMinor(shopId, FinancialAccountType.EXTERNAL_CUSTOMER, it.localId).coerceAtLeast(0L)
        }

        val suppliers = database.catalogInventoryDao().getAllSuppliers(shopId)
        val supplierPayables = suppliers.sumOf {
            val bal = ledgerDao.getDerivedAccountBalanceMinor(shopId, FinancialAccountType.EXTERNAL_SUPPLIER, it.localId)
            (-bal).coerceAtLeast(0L)
        }

        return CapitalStatement(
            openingCapitalMinor = openingCapital,
            ownerInjectionsMinor = injections,
            ownerWithdrawalsMinor = withdrawals,
            netCapitalContributionsMinor = netCapitalContributions,
            totalRevenueMinor = revenue,
            totalCogsMinor = cogs,
            totalExpensesMinor = expenses,
            netProfitMinor = netProfit,
            currentTotalCapitalMinor = currentTotalCapital,
            cashDrawerBalanceMinor = cashDrawerBalance,
            walletsTotalBalanceMinor = walletsTotalBalance,
            inventoryValueMinor = inventoryValue,
            customerReceivablesMinor = customerReceivables,
            supplierPayablesMinor = supplierPayables,
            netLiquidAssetsMinor = cashDrawerBalance + walletsTotalBalance
        )
    }
}
