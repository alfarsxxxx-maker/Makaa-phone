package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.core.AppResult
import com.example.database.MekkaPhoneDatabase
import com.example.database.dao.ProductInventoryProjection
import com.example.financial.Money
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Product Vision Recognition Engine (Section 8).
 * Uses image analysis solely to find candidate visual keywords/barcodes/models,
 * then queries the LOCAL CATALOG.
 * STRICTLY NEVER decides price, stock, IMEI, serial, cost, or balance by itself.
 * All pricing and inventory numbers come exclusively from the Room Database.
 */
class ProductVisionEngine(
    private val database: MekkaPhoneDatabase
) {
    private val catalogDao = database.catalogInventoryDao()

    /**
     * Identifies products from an image bitmap by combining visual keyword extraction
     * with an exact search against the verified local product catalog.
     */
    suspend fun identifyProductFromImage(
        bitmap: Bitmap?,
        fallbackBarcodeOrTextHint: String? = null,
        shopId: String
    ): ProductVisionResult = withContext(Dispatchers.IO) {
        val localProducts = catalogDao.observeProductInventoryProjections(shopId).first()
        if (localProducts.isEmpty()) {
            return@withContext ProductVisionResult(
                matchedProducts = emptyList(),
                isReliableMatch = false,
                statusMessageArabic = "كتالوج المنتجات فارغ حالياً في قاعدة البيانات"
            )
        }

        // 1. If explicit barcode or text hint is passed (e.g. from optical barcode scanner), match directly
        if (!fallbackBarcodeOrTextHint.isNullOrBlank()) {
            val hint = fallbackBarcodeOrTextHint.trim().lowercase()
            val exactMatches = localProducts.filter { p ->
                (p.barcode != null && p.barcode.equals(hint, ignoreCase = true)) ||
                    p.sku.equals(hint, ignoreCase = true) ||
                    p.nameArabic.lowercase().contains(hint)
            }
            if (exactMatches.isNotEmpty()) {
                val candidates = exactMatches.map { p ->
                    ProductVisionCandidate(
                        productId = p.productId,
                        productNameArabic = p.nameArabic,
                        sku = p.sku,
                        barcode = p.barcode,
                        currentStockQty = p.derivedStockQty,
                        sellingPriceMinor = p.effectiveRetailPriceMinor,
                        confidenceScorePercent = 95,
                        matchReasonArabic = "تطابق تام مع الباركود أو الكود الممسوح (${p.sku})"
                    )
                }
                return@withContext ProductVisionResult(
                    matchedProducts = candidates,
                    isReliableMatch = true,
                    statusMessageArabic = "تم العثور على ${candidates.size} منتج مطابق للرمز الممسوح"
                )
            }
        }

        // 2. If Bitmap is available and Gemini API key is configured, use Gemini Multimodal Vision to extract visual keywords
        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        val isKeyConfigured = geminiApiKey.isNotBlank() && geminiApiKey != "MY_GEMINI_API_KEY"

        if (bitmap != null && isKeyConfigured) {
            val visualKeywords = extractVisualKeywordsFromImage(geminiApiKey, bitmap)
            if (visualKeywords.isNotEmpty()) {
                val scoredMatches = mutableListOf<Pair<ProductInventoryProjection, Int>>()

                for (p in localProducts) {
                    val pText = "${p.nameArabic} ${p.sku} ${p.brandId ?: ""} ${p.barcode ?: ""}".lowercase()
                    var matchScore = 0
                    for (kw in visualKeywords) {
                        if (pText.contains(kw.lowercase())) {
                            matchScore += 25
                        }
                    }
                    if (matchScore >= 40) {
                        scoredMatches.add(p to matchScore.coerceAtMost(90))
                    }
                }

                if (scoredMatches.isNotEmpty()) {
                    val topMatches = scoredMatches.sortedByDescending { it.second }.take(3).map { (p, score) ->
                        ProductVisionCandidate(
                            productId = p.productId,
                            productNameArabic = p.nameArabic,
                            sku = p.sku,
                            barcode = p.barcode,
                            currentStockQty = p.derivedStockQty,
                            sellingPriceMinor = p.effectiveRetailPriceMinor,
                            confidenceScorePercent = score,
                            matchReasonArabic = "تطابق بصري مع مظهر الصنف في المخزن (${score}%)"
                        )
                    }
                    return@withContext ProductVisionResult(
                        matchedProducts = topMatches,
                        isReliableMatch = true,
                        statusMessageArabic = "تم التعرف البصري على الصنف ومطابقته مع المخزن"
                    )
                }
            }
        }

        // 3. Fallback when confidence is insufficient
        ProductVisionResult(
            matchedProducts = emptyList(),
            isReliableMatch = false,
            statusMessageArabic = "لم يتم العثور على منتج مطابق بدرجة ثقة كافية"
        )
    }

    private fun extractVisualKeywordsFromImage(apiKey: String, bitmap: Bitmap): List<String> {
        var connection: HttpURLConnection? = null
        return try {
            val model = "gemini-2.5-flash"
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val url = URL(endpoint)

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val b64 = bitmapToBase64(bitmap)
            val prompt = "Identify the phone, smartphone accessory, or electronics product in this image. Return 3-5 concise Arabic and English product model keywords or brand names, separated by commas. Example: 'iPhone 15, جراب ابل, شاحن سامسونج'."

            val requestJson = JSONObject().apply {
                val parts = JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", b64)
                        })
                    })
                }
                val contentObj = JSONObject().apply { put("parts", parts) }
                put("contents", JSONArray().put(contentObj))
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            if (connection.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { it.readText() }
                val root = JSONObject(responseText)
                val text = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                if (!text.isNullOrBlank()) {
                    text.split(",", "،", "\n", "-").map { it.trim() }.filter { it.length >= 2 }
                } else emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
