package com.example.sync

import com.example.core.AppResult
import com.example.diagnostics.DiagnosticCategory
import com.example.diagnostics.DiagnosticSeverity
import com.example.diagnostics.ObservabilityManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Real Production HTTP / Cloud Sync Gateway (Section 3 & 4).
 * Communicates with a real external cloud endpoint (e.g. Firebase Cloud Functions, Node.js, Go backend)
 * via authenticated, idempotent HTTPS requests.
 */
class HttpCloudSyncGateway(
    private var customEndpointUrl: String = "",
    private var authToken: String = ""
) : RemoteSyncGateway {

    override val gatewayName: String = "MekkaRealHttpCloudGateway"

    override val isExternalCloudConfigured: Boolean
        get() = customEndpointUrl.isNotBlank() && (customEndpointUrl.startsWith("http://") || customEndpointUrl.startsWith("https://"))

    fun updateConfiguration(endpointUrl: String, token: String = "") {
        this.customEndpointUrl = endpointUrl.trim()
        this.authToken = token.trim()
    }

    override suspend fun pushOperation(
        request: RemoteSyncRequest,
        authenticatedShopId: String
    ): AppResult<RemoteSyncAck> = withContext(Dispatchers.IO) {
        if (!isExternalCloudConfigured) {
            return@withContext AppResult.Error(
                "لم يتم تكوين رابط خادم المزامنة السحابي (Cloud Sync Endpoint) في الإعدادات — العمليات محفوظة محليًا بأمان",
                "CLOUD_BACKEND_NOT_CONFIGURED"
            )
        }

        if (request.shopId != authenticatedShopId) {
            ObservabilityManager.recordEvent(
                category = DiagnosticCategory.PERMISSION_DENIED,
                severity = DiagnosticSeverity.CRITICAL,
                summaryArabic = "رفض محاولة مزامنة خارج المتجر المصرح به",
                technicalDetails = "Requested shopId: ${request.shopId}, authShop: $authenticatedShopId",
                shopId = authenticatedShopId,
                deviceId = request.deviceId
            )
            return@withContext AppResult.Error(
                "رفض أمني: محاولة مزامنة بيانات متجر (${request.shopId}) لا تطابق المتجر المصرح (${authenticatedShopId})",
                "SHOP_ISOLATION_VIOLATION"
            )
        }

        var connection: HttpURLConnection? = null
        try {
            val targetUrl = if (customEndpointUrl.endsWith("/")) {
                "${customEndpointUrl}push"
            } else {
                "${customEndpointUrl}/push"
            }

            val url = URL(targetUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Shop-Id", request.shopId)
                setRequestProperty("X-Device-Id", request.deviceId)
                setRequestProperty("X-Idempotency-Key", request.deduplicationKey)
                if (authToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $authToken")
                }
            }

            val payload = JSONObject().apply {
                put("operationId", request.operationId)
                put("deduplicationKey", request.deduplicationKey)
                put("shopId", request.shopId)
                put("deviceId", request.deviceId)
                put("entityType", request.entityType)
                put("entityLocalId", request.entityLocalId)
                put("operationType", request.operationType)
                put("entityVersion", request.entityVersion)
                put("isFinancialLedgerRecord", request.isFinancialLedgerRecord)
                put("createdAt", request.createdAt)
                put("payloadJson", request.payloadJson)
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseBody = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { it.readText() }
                val json = runCatching { JSONObject(responseBody) }.getOrNull()
                if (json == null) {
                    ObservabilityManager.recordEvent(
                        category = DiagnosticCategory.SYNC_FAILURE,
                        severity = DiagnosticSeverity.ERROR,
                        summaryArabic = "استجابة الخادم غير صالحة أو ليست بصيغة JSON",
                        technicalDetails = responseBody.take(200),
                        shopId = request.shopId,
                        deviceId = request.deviceId
                    )
                    return@withContext AppResult.Error(
                        "استجابة الخادم غير صالحة أو ليست بصيغة JSON مقبولة",
                        "INVALID_SERVER_RESPONSE"
                    )
                }

                val accepted = json.optBoolean("accepted", false)
                val alreadyApplied = json.optBoolean("alreadyAppliedIdempotent", false)
                val remoteVersion = json.optLong("serverRemoteVersion", request.entityVersion)
                val ackTimestamp = json.optLong("serverAckTimestamp", System.currentTimeMillis())
                val cloudId = json.optString("remoteCloudId", "CLD-${request.entityLocalId}")
                val ackOpId = json.optString("operationId", request.operationId)
                val ackDedup = json.optString("deduplicationKey", request.deduplicationKey)

                val ack = RemoteSyncAck(
                    operationId = ackOpId,
                    deduplicationKey = ackDedup,
                    accepted = accepted,
                    alreadyAppliedIdempotent = alreadyApplied,
                    serverRemoteVersion = remoteVersion,
                    serverAckTimestamp = ackTimestamp,
                    remoteCloudId = cloudId
                )
                AppResult.Success(ack)
            } else {
                val errorBody = runCatching {
                    BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream, "UTF-8")).use { it.readText() }
                }.getOrNull().orEmpty()

                ObservabilityManager.recordEvent(
                    category = DiagnosticCategory.SYNC_FAILURE,
                    severity = DiagnosticSeverity.ERROR,
                    summaryArabic = "فشل استجابة خادم المزامنة (HTTP $responseCode)",
                    technicalDetails = "Response: $errorBody",
                    shopId = request.shopId,
                    deviceId = request.deviceId
                )

                AppResult.Error(
                    "فشل الاتصال بخادم المزامنة (رمز الخطأ: $responseCode): ${errorBody.take(120)}",
                    "CLOUD_HTTP_ERROR_$responseCode"
                )
            }
        } catch (e: java.net.SocketTimeoutException) {
            ObservabilityManager.recordEvent(
                category = DiagnosticCategory.SYNC_FAILURE,
                severity = DiagnosticSeverity.WARNING,
                summaryArabic = "انتهت مهلة الاتصال بخادم المزامنة (Timeout)",
                technicalDetails = e.message.orEmpty(),
                shopId = request.shopId,
                deviceId = request.deviceId
            )
            AppResult.Error(
                "انتهت مهلة الاتصال بخادم المزامنة (Request Timeout): ${e.message ?: "Timeout"}",
                "CLOUD_TIMEOUT_ERROR",
                e
            )
        } catch (e: Exception) {
            ObservabilityManager.recordEvent(
                category = DiagnosticCategory.SYNC_FAILURE,
                severity = DiagnosticSeverity.WARNING,
                summaryArabic = "تعذر الاتصال بخادم المزامنة — سيتم إعادة المحاولة آليًا",
                technicalDetails = e.message.orEmpty(),
                shopId = request.shopId,
                deviceId = request.deviceId
            )
            AppResult.Error(
                "تعذر الاتصال بخادم المزامنة السحابي: ${e.message ?: "خطأ في الشبكة"}",
                "CLOUD_NETWORK_ERROR",
                e
            )
        } finally {
            connection?.disconnect()
        }
    }
}
