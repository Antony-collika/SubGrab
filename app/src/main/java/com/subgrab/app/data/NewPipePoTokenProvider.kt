package com.subgrab.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.PoTokenResult
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** A compact Android implementation of NewPipe's Web BotGuard flow. */
class NewPipePoTokenProvider(private val context: Context, private val downloader: NewPipeDownloader) : PoTokenProvider {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var webView: WebView? = null
    private var visitorData: String? = null
    private var streamingToken: String? = null
    private var expiresAt = 0L
    private var initialization: CountDownLatch? = null
    private data class TokenWait(val value: AtomicReference<String?>, val latch: CountDownLatch)
    private val tokenResults = mutableMapOf<String, TokenWait>()

    override fun getWebClientPoToken(videoId: String): PoTokenResult? {
        return try {
            ensureInitialized()
            val playerToken = generate(videoId)
            PoTokenResult(visitorData!!, playerToken, streamingToken)
        } catch (firstError: Throwable) {
            android.util.Log.e("SubGrabPoToken", "PO Token failed for videoId=$videoId: ${firstError.message}", firstError)
            try {
                synchronized(lock) {
                    webView?.destroy()
                    webView = null
                    streamingToken = null
                    visitorData = null
                    expiresAt = 0L
                }
                ensureInitialized()
                val playerToken = generate(videoId)
                PoTokenResult(visitorData!!, playerToken, streamingToken)
            } catch (retryError: Throwable) {
                android.util.Log.e("SubGrabPoToken", "PO Token retry failed for videoId=$videoId: ${retryError.message}", retryError)
                null
            }
        }
    }

    override fun getWebEmbedClientPoToken(videoId: String): PoTokenResult? = getWebClientPoToken(videoId)

    override fun getAndroidClientPoToken(videoId: String): PoTokenResult? = null

    override fun getIosClientPoToken(videoId: String): PoTokenResult? = null

    private fun ensureInitialized() {
        synchronized(lock) {
            if (webView != null && System.currentTimeMillis() < expiresAt) return
            visitorData = getVisitorData()
            initialization = CountDownLatch(1)
            main.post {
                val view = WebView(context.applicationContext)
                view.settings.javaScriptEnabled = true
                view.settings.userAgentString = BROWSER_UA
                view.settings.blockNetworkLoads = true
                view.webViewClient = WebViewClient()
                view.webChromeClient = WebChromeClient()
                view.addJavascriptInterface(this, BRIDGE)
                webView = view
                val html = context.assets.open("po_token.html").bufferedReader().use { it.readText() }
                view.loadDataWithBaseURL("https://www.youtube.com", html.replaceFirst("</script>", "\n$BRIDGE.startBotguard()</script>"), "text/html", "utf-8", null)
            }
            check(initialization!!.await(45, TimeUnit.SECONDS)) { "BotGuard WebView initialization timeout" }
            check(streamingToken != null) { "BotGuard did not return streaming token" }
        }
    }

    private fun getVisitorData(): String {
        val info = InnertubeClientRequestInfo.ofWebClient()
        info.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
        return YoutubeParsingHelper.getVisitorDataFromInnertube(
            info,
            NewPipe.getPreferredLocalization(),
            NewPipe.getPreferredContentCountry(),
            YoutubeParsingHelper.getYouTubeHeaders(),
            YoutubeParsingHelper.YOUTUBEI_V1_URL,
            null,
            false
        )
    }

    @JavascriptInterface
    fun startBotguard() {
        postBotguard(CREATE_URL, "[\"$REQUEST_KEY\"]") { raw ->
            val challenge = parseChallenge(raw)
            evaluate("try { data=$challenge; runBotGuard(data).then(function(r){ $BRIDGE.onBotguard(r.botguardResponse); }, function(e){ $BRIDGE.onError(String(e)); }); } catch(e) { $BRIDGE.onError(String(e)); }")
        }
    }

    @JavascriptInterface
    fun onBotguard(response: String) {
        postBotguard(GENERATE_URL, "[\"$REQUEST_KEY\",${JSONObject.quote(response)}]") { raw ->
            val result = JSONArray(raw)
            val integrity = base64Array(result.getString(0))
            val lifetime = result.getLong(1)
            expiresAt = System.currentTimeMillis() + (lifetime - 600).coerceAtLeast(60) * 1000
            evaluate("this.integrityToken=$integrity")
            Thread {
                try {
                    streamingToken = generate(visitorData!!)
                } finally {
                    initialization?.countDown()
                }
            }.start()
        }
    }

    @JavascriptInterface
    fun onError(message: String) {
        initialization?.countDown()
        synchronized(tokenResults) { tokenResults.values.forEach { it.latch.countDown() } }
    }

    private fun generate(identifier: String): String {
        val wait = TokenWait(AtomicReference(null), CountDownLatch(1))
        synchronized(tokenResults) { tokenResults[identifier] = wait }
        main.post {
            val jsIdentifier = JSONObject.quote(identifier)
            evaluate("try { identifier=$jsIdentifier; u8Identifier=${byteArrayJs(identifier.toByteArray())}; poTokenU8=obtainPoToken(webPoSignalOutput,integrityToken,u8Identifier); $BRIDGE.onToken(identifier,poTokenU8.toString()); } catch(e) { $BRIDGE.onError(String(e)); }")
        }
        check(wait.latch.await(30, TimeUnit.SECONDS)) { "BotGuard token generation timeout" }
        synchronized(tokenResults) { tokenResults.remove(identifier) }
        return wait.value.get() ?: error("BotGuard token generation failed")
    }

    @JavascriptInterface
    fun onToken(identifier: String, bytes: String) {
        synchronized(tokenResults) { tokenResults[identifier]?.let { it.value.set(bytesToBase64(bytes)); it.latch.countDown() } }
    }

    private fun postBotguard(url: String, body: String, callback: (String) -> Unit) {
        Thread {
            try {
                val result = downloader.postJson(url, body)
                main.post { callback(result) }
            } catch (_: Throwable) { main.post { onError("BotGuard HTTP failed") } }
        }.start()
    }

    private fun evaluate(script: String) { main.post { webView?.evaluateJavascript(script, null) } }

    private fun parseChallenge(raw: String): String {
        val outer = JSONArray(raw)
        val challenge = if (outer.length() > 1 && outer.opt(1) is String) JSONArray(descramble(outer.getString(1))) else outer.getJSONArray(0)
        return JSONObject().apply {
            put("messageId", challenge.getString(0))
            put("interpreterJavascript", JSONObject().apply {
                put("privateDoNotAccessOrElseSafeScriptWrappedValue", challenge.optJSONArray(1)?.firstString())
                put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", challenge.optJSONArray(2)?.firstString())
            })
            put("interpreterHash", challenge.getString(3))
            put("program", challenge.getString(4))
            put("globalName", challenge.getString(5))
            put("clientExperimentsStateBlob", challenge.getString(7))
        }.toString()
    }

    private fun descramble(value: String): String = Base64.getDecoder().decode(value.replace('-', '+').replace('_', '/').padBase64()).map { (it + 97).toByte() }.toByteArray().toString(Charsets.UTF_8)
    private fun base64Array(value: String): String = "new Uint8Array([" + Base64.getUrlDecoder().decode(value.replace('.', '=').padBase64()).joinToString(",") { it.toUByte().toString() } + "])"
    private fun byteArrayJs(value: ByteArray): String = "new Uint8Array([" + value.joinToString(",") { it.toUByte().toString() } + "])"
    private fun bytesToBase64(value: String): String = value.split(',').filter { it.isNotBlank() }.map { it.toInt().toByte() }.toByteArray().let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun String.padBase64(): String = this + "=".repeat((4 - length % 4) % 4)
    private fun JSONArray.firstString(): String? = (0 until length()).map { opt(it) }.filterIsInstance<String>().firstOrNull()

    companion object {
        private const val BRIDGE = "SubGrabPoToken"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
        private const val GENERATE_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
        private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.3"
    }
}
