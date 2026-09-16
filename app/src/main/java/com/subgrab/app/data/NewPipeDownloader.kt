package com.subgrab.app.data

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class NewPipeDownloader : Downloader() {
    fun fetchText(url: String, referer: String = "https://www.youtube.com/"): String {
        val request = Request.newBuilder()
            .httpMethod("GET")
            .url(url)
            .headers(mapOf(
                "Referer" to listOf(referer),
                "Origin" to listOf("https://www.youtube.com"),
                "Cookie" to listOf("SOCS=CAE=")
            ))
            .build()
        val response = execute(request)
        check(response.responseCode() in 200..299) { "Subtitle HTTP ${response.responseCode()} ${response.responseMessage()}" }
        return response.responseBody()
    }

    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            request.headers().forEach { (key, values) -> values.firstOrNull()?.let { setRequestProperty(key, it) } }
            request.dataToSend()?.let { body ->
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
        return Response(code, connection.responseMessage.orEmpty(), connection.headerFields, body, connection.url?.toString())
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
