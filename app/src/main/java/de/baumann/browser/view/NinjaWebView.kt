package de.baumann.browser.view

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.util.AttributeSet
import android.util.Base64
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.preference.PreferenceManager
import de.baumann.browser.Ninja.BuildConfig
import de.baumann.browser.Ninja.R
import de.baumann.browser.browser.*
import de.baumann.browser.preference.ConfigManager
import de.baumann.browser.unit.BrowserUnit
import de.baumann.browser.unit.ViewUnit
import java.io.IOException
import java.io.InputStream
import java.util.*

class NinjaWebView : WebView, AlbumController {
    private var dimen144dp = 0
    private var dimen108dp = 0
    private var onScrollChangeListener: OnScrollChangeListener? = null
    private val album: Album
    private val webViewClient: NinjaWebViewClient
    private val webChromeClient: NinjaWebChromeClient
    private val downloadListener: NinjaDownloadListener = NinjaDownloadListener(context)
    private var clickHandler: NinjaClickHandler? = null
    private val gestureDetector: GestureDetector = GestureDetector(context, NinjaGestureListener(this))

    val adBlock: AdBlock
    val cookieHosts: Cookie
    private val javaHosts: Javascript

    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var config: ConfigManager? = null
    var isForeground = false
        private set

    private val isVerticalRead: Boolean
        private get() = sp.getBoolean("sp_vertical_read", false)

    var browserController: BrowserController? = null

    constructor(context: Context?, browserController: BrowserController) : super(context!!) {
        this.browserController = browserController
        album = Album(context, this, browserController)
        initAlbum()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        album = Album(context, this, browserController)
        initAlbum()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr) {
        album = Album(context, this, browserController)
        initAlbum()
    }

    override fun onScrollChanged(l: Int, t: Int, old_l: Int, old_t: Int) {
        super.onScrollChanged(l, t, old_l, old_t)
        if (onScrollChangeListener != null) {
            onScrollChangeListener!!.onScrollChange(t, old_t)
        }
    }

    fun setOnScrollChangeListener(onScrollChangeListener: OnScrollChangeListener) {
        this.onScrollChangeListener = onScrollChangeListener
    }

    fun applyBoldFontStyle() {
        injectCss(boldFontCss.toByteArray())
    }

    fun updateCssStyle() {
        val cssStyle = (if (config!!.boldFontStyle) boldFontCss else "") +
                if (config!!.fontStyleSerif) notoSansSerifFontCss else ""
        injectCss(cssStyle.toByteArray())
    }

    interface OnScrollChangeListener {
        fun onScrollChange(scrollY: Int, oldScrollY: Int)
    }

    init {
        dimen144dp = resources.getDimensionPixelSize(R.dimen.layout_width_144dp)
        dimen108dp = resources.getDimensionPixelSize(R.dimen.layout_height_108dp)
        isForeground = false
        adBlock = AdBlock(context)
        javaHosts = Javascript(context)
        cookieHosts = Cookie(context)
        webViewClient = NinjaWebViewClient(this)
        webChromeClient = NinjaWebChromeClient(this)
        clickHandler = NinjaClickHandler(this)
        initWebView()
        initWebSettings()
        initPreferences()
    }

    private fun initWebView() {
        if (BuildConfig.DEBUG) {
            setWebContentsDebuggingEnabled(true)
        }
        setWebViewClient(webViewClient)
        setWebChromeClient(webChromeClient)
        setDownloadListener(downloadListener)
        setOnTouchListener { _, motionEvent: MotionEvent? ->
            gestureDetector.onTouchEvent(motionEvent)
            false
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun initWebSettings() {
        with (settings) {
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            setSupportMultipleWindows(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            if (Build.VERSION.SDK_INT >= 26) {
                safeBrowsingEnabled = true
            }
        }
    }

    fun initPreferences() {
        config = ConfigManager(context)
        val userAgent = sp.getString("userAgent", "") ?: ""
        if (userAgent.isNotEmpty()) {
            settings.setUserAgentString(userAgent)
        }
        val isDesktopMode = sp.getBoolean("sp_desktop", false)
        if (isDesktopMode) {
            settings.setUserAgentString(BrowserUnit.UA_DESKTOP)
        } else {
            val defaultUserAgent = settings.userAgentString
            settings.setUserAgentString(defaultUserAgent.replace("wv", ""))
        }
        with (settings) {
            useWideViewPort = isDesktopMode
            loadWithOverviewMode = isDesktopMode
            setAppCacheEnabled(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            textZoom = sp.getString("sp_fontSize", "100")!!.toInt()
            allowFileAccessFromFileURLs = sp.getBoolean("sp_remote", true)
            allowUniversalAccessFromFileURLs = sp.getBoolean("sp_remote", true)
            domStorageEnabled = sp.getBoolean("sp_remote", true)
            databaseEnabled = true
            blockNetworkImage = !sp.getBoolean(context!!.getString(R.string.sp_images), true)
            javaScriptEnabled = sp.getBoolean(context!!.getString(R.string.sp_javascript), true)
            javaScriptCanOpenWindowsAutomatically = sp.getBoolean(context!!.getString(R.string.sp_javascript), true)
            setSupportMultipleWindows(sp.getBoolean(context!!.getString(R.string.sp_javascript), true))
            setGeolocationEnabled(sp.getBoolean(context!!.getString(R.string.sp_location), false))
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        }
        webViewClient.enableAdBlock(sp.getBoolean(context!!.getString(R.string.sp_ad_block), true))

        CookieManager.getInstance().apply {
            setAcceptCookie(sp.getBoolean(context!!.getString(R.string.sp_cookies), true))
            setAcceptThirdPartyCookies(this@NinjaWebView, sp.getBoolean(context.getString(R.string.sp_cookies), true))
        }
    }

    private fun initAlbum() {
        with(album) {
            setAlbumCover(null)
            albumTitle = context!!.getString(R.string.app_name)
        }
    }

    val requestHeaders: HashMap<String, String>
        get() {
            val requestHeaders = HashMap<String, String>()
            requestHeaders["DNT"] = "1"
            if (sp.getBoolean(context!!.getString(R.string.sp_savedata), false)) {
                requestHeaders["Save-Data"] = "on"
            }
            return requestHeaders
        }

    /* continue playing if preference is set */
    override fun onWindowVisibilityChanged(visibility: Int) {
        if (sp.getBoolean("sp_media_continue", false)) {
            if (visibility != GONE && visibility != INVISIBLE) super.onWindowVisibilityChanged(VISIBLE)
        } else {
            super.onWindowVisibilityChanged(visibility)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun loadUrl(url: String) {
        if (url.trim { it <= ' ' }.isEmpty()) {
            NinjaToast.show(context, R.string.toast_load_error)
            return
        }
        if (!sp.getBoolean(context!!.getString(R.string.sp_javascript), true)) {
            if (javaHosts.isWhite(url)) {
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.javaScriptEnabled = true
            } else {
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.javaScriptEnabled = false
            }
        }

        if (url.startsWith("javascript")) {
            // Daniel
            super.loadUrl(url)
            return
        }
        super.loadUrl(BrowserUnit.queryWrapper(context, url.trim { it <= ' ' }), requestHeaders)
    }

    override fun getAlbumView(): View = album.albumView

    override fun setAlbumCover(bitmap: Bitmap?) = album.setAlbumCover(bitmap)

    override fun getAlbumTitle(): String = album.albumTitle

    override fun setAlbumTitle(title: String) {
        album.albumTitle = title
    }

    @Synchronized
    override fun activate() {
        requestFocus()
        isForeground = true
        album.activate()
    }

    @Synchronized
    override fun deactivate() {
        clearFocus()
        isForeground = false
        album.deactivate()
    }

    @Synchronized
    fun update(progress: Int) {
        if (isForeground) {
            browserController?.updateProgress(progress)
        }
        if (isLoadFinish) {
            Handler().postDelayed({ setAlbumCover(ViewUnit.capture(this@NinjaWebView, dimen144dp.toFloat(), dimen108dp.toFloat(), Bitmap.Config.RGB_565)) }, 250)
            if (prepareRecord()) {
                browserController?.updateAutoComplete()
            }
        }
    }

    @Synchronized
    fun update(title: String?) {
        album.albumTitle = title
    }

    @Synchronized
    override fun destroy() {
        stopLoading()
        onPause()
        clearHistory()
        visibility = GONE
        removeAllViews()
        super.destroy()
    }

    val isLoadFinish: Boolean
        get() = progress >= BrowserUnit.PROGRESS_MAX

    fun onLongPress() {
        val click = clickHandler!!.obtainMessage()
        click.target = clickHandler
        requestFocusNodeHref(click)
    }

    private fun prepareRecord(): Boolean {
        val title = title
        val url = url
        return !(title == null || title.isEmpty()
                || url == null || url.isEmpty()
                || url.startsWith(BrowserUnit.URL_SCHEME_ABOUT)
                || url.startsWith(BrowserUnit.URL_SCHEME_MAIL_TO)
                || url.startsWith(BrowserUnit.URL_SCHEME_INTENT))
    }

    fun jumpToTop() {
        scrollTo(0, 0)
    }

    fun jumpToBottom() {
        if (isVerticalRead) {
            scrollTo(computeHorizontalScrollRange(), 0)
        } else {
            scrollTo(0, computeVerticalScrollRange())
        }
    }

    fun pageDownWithNoAnimation() {
        if (isVerticalRead) {
            scrollBy(shiftOffset(), 0)
        } else {
            scrollBy(0, shiftOffset())
        }
    }

    fun pageUpWithNoAnimation() {
        if (isVerticalRead) {
            scrollBy(-shiftOffset(), 0)
        } else {
            scrollBy(0, -shiftOffset())
        }
    }

    private fun shiftOffset(): Int {
        return if (isVerticalRead) {
            width - ViewUnit.dpToPixel(context, 40).toInt()
        } else {
            height - ViewUnit.dpToPixel(context, config!!.pageReservedOffset).toInt()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (browserController == null) return false

        return if (!browserController!!.handleKeyEvent(event)) {
            super.dispatchKeyEvent(event)
        } else {
            true
        }
    }

    fun applyVerticalRead() {
        injectCss(verticalLayoutCss.toByteArray())
    }

    fun applyHorizontalRead() {
        injectCss(horizontalLayoutCss.toByteArray())
    }

    fun applyReaderMode() {
        val jsInput: InputStream
        val cssInput: InputStream
        try {
            jsInput = context.assets.open("readability.js")
            val buffer = ByteArray(jsInput.available())
            jsInput.read(buffer)
            jsInput.close()
            cssInput = context.assets.open("readability.css")
            val cssBuffer = ByteArray(cssInput.available())
            cssInput.read(cssBuffer)
            cssInput.close()

            // String-ify the script byte-array using BASE64 encoding !!!
            val encodedJs = Base64.encodeToString(buffer, Base64.NO_WRAP)
            val encodedCss = Base64.encodeToString(cssBuffer, Base64.NO_WRAP)
            loadUrl("javascript:(function() {" +
                    "var parent = document.getElementsByTagName('head').item(0);" +
                    "var script = document.createElement('script');" +
                    "script.type = 'text/javascript';" +
                    "script.innerHTML = window.atob('" + encodedJs + "');" +
                    "parent.appendChild(script)" +
                    "})()")
            injectCss(cssBuffer)
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
    }

    fun applyPageNoMargins() = injectCss(pageNoMarginCss.toByteArray())

    /*
    fun applyReaderMode1() {
        addJavascriptInterface(
                MyJavaScriptInterface(
                        this.url ?: ""
                ) { article ->
                    post {
                        loadData(article.document.toString(), "text/html",  "utf-8")
                    }
                },
                "HtmlViewer"
        )
        loadUrl("javascript:window.HtmlViewer.showHTML('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');")
//        evaluateJavascript("(function() { return '<html>' + document.getElementsByTagName('html')[0].innerHTML + '</html>'; })()") {
//            loadData(ArticleExtractor((this.url ?: "").toHttpUrl(), it).extractContent().article.document.toString(), "text/html",  "utf-8")
//        }
    }

     */

    private fun injectCss(bytes: ByteArray) {
        try {
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            loadUrl("javascript:(function() {" +
                    "var parent = document.getElementsByTagName('head').item(0);" +
                    "var style = document.createElement('style');" +
                    "style.type = 'text/css';" +
                    "style.innerHTML = window.atob('" + encoded + "');" +
                    "parent.appendChild(style)" +
                    "})()")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /*
    class MyJavaScriptInterface(
            private val url: String,
            private val action: (article: Article) -> Unit
    ) {

        @JavascriptInterface
        fun showHTML(html: String) {
            val article = ArticleExtractor(url.toHttpUrl(), html).extractContent().article
            action.invoke(article)
        }
    }

     */

    companion object {
        private const val verticalLayoutCss = "body {\n" +
                "-webkit-writing-mode: vertical-rl;\n" +
                "writing-mode: vertical-rl;\n" +
                "max-height: 95%;\n" +
                "}\n" +  // facebook css styles
                "._2w79 { display: inline; max-height: 500px;}\n" +
                "._4o5j ._4o51 { display: none; }\n" +
                "._24e4 { max-height: none;}\n"
        private const val notoSansSerifFontCss = "@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+TC:wght@400&display=swap');" +
                "@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+JP:wght@400&display=swap');" +
                "@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+KR:wght@400&display=swap');" +
                "body {\n" +
                "font-family: 'Noto Serif TC', 'Noto Serif JP', 'Noto Serif KR', serif !important;\n" +
                "}\n"
        private const val horizontalLayoutCss = "body {\n" +
                "-webkit-writing-mode: horizontal-tb;\n" +
                "writing-mode: horizontal-tb;\n" +
                "}\n"
        private const val pageNoMarginCss = "@page{\n" +
                "margin-left: 5px;\n" +
                "margin-right: 5px;\n" +
                "margin-top: 5px;\n" +
                "margin-bottom: 5px;\n" +
                "padding:0px;\n" +
                "}\n"
        private const val boldFontCss = "* {\n" +
                "\tcolor: #000000!important;\n" +
                "\tfont-weight:700 !important;\n" +  /*"\tborder-color: #555555 !important;\n" +*/
                "\tscrollbar-arrow-color: #CCCCCC !important;\n" +
                "\tscrollbar-base-color: #2266AA !important;\n" +
                "\tscrollbar-shadow-color: #2266AA !important;\n" +
                "\tscrollbar-face-color: #333333 !important;\n" +
                "\tscrollbar-highlight-color: #2266AA !important;\n" +
                "\tscrollbar-dark-shadow-color: #2266AA !important;\n" +
                "\tscrollbar-3d-light-color: #2266AA !important;\n" +
                "\tscrollbar-track-color: #333333 !important;\n" +
                "}\n" +
                "a,a * {\n" +
                "\tcolor: #000000 !important;\n" +
                "\tfont-weight:700 !important;\n" +
                "}\n" +
                "a: visited,a: visited *,a: active,a: active * {\n" +
                "\tcolor: #000000 !important;\n" +
                "\tfont-weight:700 !important;\n" +
                "}\n" +
                "a: hover,a: hover * {\n" +
                "\tcolor: #000000 !important;\n" +
                "\tbackground: #666666 !important;\n" +
                "\tfont-weight:700 !important;\n" +
                "}\n" +
                "input,select,option,button,textarea {\n" +
                "\tcolor: #000000 !important;\n" +
                "\tfont-weight:700 !important;\n" +
                "\tbackground: #FFFFFF !important;\n" +
                "\tborder: #FFFFFF !important;\n" +
                "\tborder-color: #FFFFFF #FFFFFF #FFFFFF #FFFFFF !important;\n" +
                "}\n" +
                "input: focus,select: focus,option: focus,button: focus,textarea: focus,input: hover,select: hover,option: hover,button: hover,textarea: hover {\n" +
                "\tcolor: #000000 !important;\n" +
                "\tbackground: #FFFFFF !important;\n" +
                "\tfont-weight:700 !important;\n" +
                "\tborder-color: #FFFFFF #FFFFFF #FFFFFF #FFFFFF !important;\n" +
                "}\n" +
                "input[type=button],input[type=submit],input[type=reset],input[type=image] {\n" +
                "\tborder-color: #FFFFFF #FFFFFF #FFFFFF #FFFFFF !important;\n" +
                "}\n" +
                "input[type=button]: focus,input[type=submit]: focus,input[type=reset]: focus,input[type=image]: focus, input[type=button]: hover,input[type=submit]: hover,input[type=reset]: hover,input[type=image]: hover {\n" +
                "\tcolor: #000000 !important;\n" +
                "\tfont-weight:700 !important;\n" +
                "\tbackground: #FFFFFF !important;\n" +
                "\tborder-color: #FFFFFF #FFFFFF #FFFFFF #FFFFFF !important;\n" +
                "}\n"
    }
}

