package com.example.transaction

import com.example.database.MekkaPhoneDatabase
import com.example.database.entities.AppSettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Smart Categorization Engine & User Correction Learning (V4.1 Part 2 - Sections 23 & 24).
 * Provides category suggestions based on Arabic natural language keywords.
 * Remembers user corrections so that future suggestions adapt to shop owner preferences.
 */
class SmartCategorizationEngine(
    private val database: MekkaPhoneDatabase
) {
    companion object {
        const val SETTING_KEY_LEARNED_CATEGORIES = "SMART_CATEGORIZATION_LEARNED_MAP"

        // Default Egyptian Phone Shop Operational Categories
        val DEFAULT_RULES = listOf(
            listOf("كهرباء", "فاتورة كهرباء", "نور", "مياه", "غاز", "انترنت", "إنترنت", "واي فاي") to "مرافق وفواتير",
            listOf("بنزين", "وقود", "سولار", "مواصلات", "اوبر", "أوبر", "تاكسي", "شحن بضاعة", "دليفري", "توصيل") to "انتقالات وشحن",
            listOf("صيانة", "تصليح", "شاشة مكسورة", "باغة", "لحام", "قطع غيار صيانة") to "مصروفات صيانة وقطع غيار",
            listOf("بوفيه", "شاي", "سكر", "قهوة", "مياه معدنية", "فطار", "غداء", "أكل") to "ضيافة وبوفيه",
            listOf("ايجار", "إيجار", "إيجار المحل", "ايجار المحل") to "إيجار المقر",
            listOf("رواتب", "مرتب", "أجور", "حوافز", "سلفة", "سلف") to "أجور ومرتبات",
            listOf("دعاية", "اعلان", "إعلان", "تسويق", "فيس بوك", "مطبوعات", "كروت") to "دعاية وتسويق",
            listOf("نظافة", "كلور", "منظفات", "ادوات نظافة", "أدوات نظافة") to "نظافة وتشغيل",
            listOf("اكياس", "أكياس", "تغليف", "كراتين", "لزق", "استرتش") to "تعبئة وتغليف"
        )
    }

    private val learnedCache = mutableMapOf<String, String>()
    private var isLoaded = false

    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext
        try {
            val setting = database.coreShopAuthDao().getSetting("shop_mekka_main", SETTING_KEY_LEARNED_CATEGORIES)
            if (setting != null && setting.settingValue.isNotBlank()) {
                val json = JSONObject(setting.settingValue)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    learnedCache[k.lowercase().trim()] = json.getString(k)
                }
            }
        } catch (_: Exception) {}
        isLoaded = true
    }

    /**
     * Suggests a category for an expense or transaction description.
     * Evaluates learned user preferences first, then standard rules.
     */
    suspend fun suggestCategory(description: String): String = withContext(Dispatchers.IO) {
        ensureLoaded()
        val lower = description.lowercase().trim()

        // 1. Check exact or substring learned corrections
        for ((learnedKeyword, targetCategory) in learnedCache) {
            if (lower.contains(learnedKeyword)) {
                return@withContext targetCategory
            }
        }

        // 2. Check default keyword rules
        for ((keywords, category) in DEFAULT_RULES) {
            if (keywords.any { lower.contains(it) }) {
                return@withContext category
            }
        }

        "مصروفات عامة"
    }

    /**
     * Records a user correction (Section 24: User Correction Learning).
     * e.g. User corrects "بنزين" from "انتقالات" to "تشغيل عام".
     */
    suspend fun recordUserCorrection(
        rawKeywordOrDescription: String,
        chosenCategory: String,
        actorUserId: String = "SYSTEM"
    ): Unit = withContext(Dispatchers.IO) {
        ensureLoaded()
        val cleanKeyword = extractCoreKeyword(rawKeywordOrDescription)
        learnedCache[cleanKeyword] = chosenCategory

        // Persist to database AppSettingEntity
        val json = JSONObject()
        for ((k, v) in learnedCache) {
            json.put(k, v)
        }
        val now = System.currentTimeMillis()
        val entity = AppSettingEntity(
            localId = "SETTING_$SETTING_KEY_LEARNED_CATEGORIES",
            shopId = "shop_mekka_main",
            settingKey = SETTING_KEY_LEARNED_CATEGORIES,
            settingValue = json.toString(),
            deviceId = "DEVICE_PRIMARY",
            updatedByUserId = actorUserId,
            createdAt = now,
            updatedAt = now
        )
        database.coreShopAuthDao().upsertSetting(entity)
    }

    /**
     * Returns all learned rules for user inspection and editing.
     */
    suspend fun getLearnedRules(): Map<String, String> = withContext(Dispatchers.IO) {
        ensureLoaded()
        learnedCache.toMap()
    }

    /**
     * Removes a learned correction rule.
     */
    suspend fun removeLearnedRule(keyword: String, actorUserId: String = "SYSTEM"): Unit = withContext(Dispatchers.IO) {
        ensureLoaded()
        val clean = keyword.lowercase().trim()
        learnedCache.remove(clean)

        val json = JSONObject()
        for ((k, v) in learnedCache) {
            json.put(k, v)
        }
        val now = System.currentTimeMillis()
        val entity = AppSettingEntity(
            localId = "SETTING_$SETTING_KEY_LEARNED_CATEGORIES",
            shopId = "shop_mekka_main",
            settingKey = SETTING_KEY_LEARNED_CATEGORIES,
            settingValue = json.toString(),
            deviceId = "DEVICE_PRIMARY",
            updatedByUserId = actorUserId,
            createdAt = now,
            updatedAt = now
        )
        database.coreShopAuthDao().upsertSetting(entity)
    }

    private fun extractCoreKeyword(text: String): String {
        val lower = text.lowercase().trim()
        // Strip common leading words like "مصروف", "دفع", "سداد", "فاتورة"
        val words = lower.split(Regex("""\s+""")).filter {
            it !in listOf("مصروف", "مصاريف", "دفع", "دفعت", "سداد", "حق", "قيمة", "جنيه", "ج.م") &&
                it.length >= 2 &&
                !it.all { ch -> ch.isDigit() }
        }
        return if (words.isNotEmpty()) words.first() else lower
    }
}
