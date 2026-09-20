package com.subgrab.app.data

import com.subgrab.app.domain.RequestLane
import com.subgrab.app.domain.RequestOperation
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class NewPipeDownloader(
    private val pacer: RequestPacer
) : Downloader() {
    private data class RequestContext(val lane: RequestLane, val operation: String)
    private val requestContext = ThreadLocal<RequestContext?>()

    fun <T> withRequestContext(lane: RequestLane, operation: String, block: () -> T): T {
        val previous = requestContext.get()
        requestContext.set(RequestContext(lane, operation))
        return try { block() } finally { requestContext.set(previous) }
    }

    fun postJson(url: String, body: String): String {
        val request = Request.newBuilder().httpMethod("POST").url(url).headers(
            mapOf(
                "User-Agent" to listOf(NewPipePoTokenProvider.BROWSER_UA),
                "Accept" to listOf("application/json"),
                "Content-Type" to listOf("application/json+protobuf"),
                "x-goog-api-key" to listOf("AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"),
                "x-user-agent" to listOf("grpc-web-javascript/0.1")
            )
        ).dataToSend(body.toByteArray()).build()
        val response = execute(request)
        check(response.responseCode() == 200) { "BotGuard HTTP ${response.responseCode()}" }
        return response.responseBody()
    }

    fun fetchText(url: String, referer: String = "https://www.youtube.com/"): String {
        val request = Request.newBuilder().httpMethod("GET").url(url).headers(
            mapOf(
                "Referer" to listOf(referer),
                "Origin" to listOf("https://www.youtube.com"),
                "Cookie" to listOf("SOCS=CAE=")
            )
        ).build()
        return withRequestContext(RequestLane.SUBTITLE_EXTRACTOR, "subtitle.fetch") {
            val response = execute(request)
            check(response.responseCode() in 200..299) {
                "Subtitle HTTP ${response.responseCode()} ${response.responseMessage()}"
            }
            response.responseBody()
        }
    }

    override fun execute(request: Request): Response {
        val context = requestContext.get()
            ?: RequestContext(RequestLane.DISCOVERY_EXTRACTOR, "extractor.request")
        val operation = RequestOperation(
            context.lane,
            context.operation,
            safeUrl(request.url())
        )
        return runBlocking {
            pacer.execute(operation) { executeHttp(request) }
        }
    }

    private fun executeHttp(request: Request): Response {
        val bodyBytes = request.dataToSend()
        DebugLog.d(
            "REQ ${request.httpMethod()} ${safeUrl(request.url())} " +
                "body=${bodyBytes?.size ?: 0} headers=${request.headers().keys.joinToString(",")}"
        )
        return try {
            val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
                requestMethod = request.httpMethod()
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                request.headers().forEach { (key, values) ->
                    values.firstOrNull()?.let { setRequestProperty(key, it) }
                }
                bodyBytes?.let { body ->
                    doOutput = true
                    setRequestProperty("Content-Length", body.size.toString())
                    outputStream.use { it.write(body) }
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val out = ByteArrayOutputStream()
                input.copyTo(out)
                out.toString(Charsets.UTF_8.name())
            }.orEmpty()
            val finalUrl = connection.url?.toString().orEmpty()
            val errorBody = if (code >= 400) {
                " body=${body.take(700).replace(Regex("\\s+"), " ")}"
            } else ""
            DebugLog.d(
                "RESP ${request.httpMethod()} ${safeUrl(request.url())} -> $code " +
                    "${connection.responseMessage.orEmpty()} final=${safeUrl(finalUrl)}$errorBody"
            )
            Response(code, connection.responseMessage.orEmpty(), connection.headerFields, body, connection.url?.toString())
        } catch (error: Throwable) {
            DebugLog.e("FAIL ${request.httpMethod()} ${safeUrl(request.url())}", error)
            throw error
        }
    }

    private fun safeUrl(raw: String): String {
        val queryIndex = raw.indexOf("?")
        return if (queryIndex >= 0) raw.substring(0, queryIndex) + "?<query-redacted>" else raw
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
