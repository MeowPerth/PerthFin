package dev.jdtech.jellyfin.cast.dlna

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

/** Minimal UPnP SOAP client used by the AVTransport service. */
class SoapClient(private val httpClient: OkHttpClient = defaultHttpClient()) {

    suspend fun call(
        service: DlnaService,
        action: String,
        arguments: Map<String, String> = emptyMap(),
    ): Map<String, String> =
        withContext(Dispatchers.IO) {
            val body = SoapEnvelope.build(service.serviceType, action, arguments)
            val request =
                Request.Builder()
                    .url(service.controlUrl)
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header("SOAPACTION", "\"${service.serviceType}#$action\"")
                    .header("User-Agent", USER_AGENT)
                    .post(body.toRequestBody(XML_MEDIA_TYPE))
                    .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (text.isBlank()) {
                        throw DlnaException("Empty response from renderer (HTTP ${response.code})")
                    }
                    try {
                        SoapEnvelope.parseResponse(text)
                    } catch (e: DlnaException) {
                        throw e
                    } catch (e: Exception) {
                        throw DlnaException(
                            "Failed to parse renderer response (HTTP ${response.code})",
                            cause = e,
                        )
                    }
                }
            } catch (e: DlnaException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "SOAP $action failed")
                throw DlnaException(
                    "Unable to connect to renderer: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    cause = e,
                )
            }
        }

    companion object {
        private const val USER_AGENT = "PerthFin/1.0 UPnP/1.1 DLNADOC/1.50 Android"
        private val XML_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()

        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
    }
}
