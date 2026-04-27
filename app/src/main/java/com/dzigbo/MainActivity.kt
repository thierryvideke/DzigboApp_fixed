package com.dzigbo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dzigbo.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    // URLs de chaque section
    private val SITE       = "https://dzigbodihope.com"
    private val URL_SHOP   = "https://dzigbodihope.com/boutique/"
    private val URL_LIVE   = "https://dzigbodihope.com/live-celibataire/"
    private val URL_BLOG   = "https://dzigbodihope.com/blog/"
    private val WHATSAPP   = "https://wa.me/22898665474"

    private var currentTab = R.id.nav_home
    private var isPageLoaded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        setupWebView()
        setupNav()
        loadHome()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val ws = b.webView.settings
        ws.javaScriptEnabled       = true
        ws.domStorageEnabled       = true
        ws.databaseEnabled         = true
        ws.loadWithOverviewMode    = true
        ws.useWideViewPort         = true
        ws.setSupportZoom(false)
        ws.builtInZoomControls     = false
        ws.cacheMode               = WebSettings.LOAD_DEFAULT
        ws.mixedContentMode        = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        ws.userAgentString         = ws.userAgentString + " DzigboApp/1.0"

        // Interface JS → permet à la page HTML d'appeler l'app native
        b.webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun openUrl(url: String) {
                runOnUiThread { handleExternalUrl(url) }
            }
        }, "DzigboApp")

        b.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                showLoader(true)
            }
            override fun onPageFinished(view: WebView, url: String) {
                showLoader(false)
                isPageLoaded = true
                b.swipe.isRefreshing = false
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                showLoader(false)
                b.swipe.isRefreshing = false
                // Si c'est la page principale et qu'on est hors ligne → afficher la page offline
                if (request.isForMainFrame) {
                    showOfflinePage()
                }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                return handleExternalUrl(url)
            }
        }

        b.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress < 100) {
                    b.progressBar.visibility = View.VISIBLE
                    b.progressBar.progress   = newProgress
                } else {
                    b.progressBar.visibility = View.GONE
                }
            }
        }

        // Pull-to-refresh
        b.swipe.setOnRefreshListener {
            b.swipe.setColorSchemeColors(0xFFC9A84C.toInt())
            when (currentTab) {
                R.id.nav_home -> loadHome()
                else          -> b.webView.reload()
            }
        }
    }

    private fun setupNav() {
        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home  -> { currentTab = R.id.nav_home;  loadHome();           true }
                R.id.nav_shop  -> { currentTab = R.id.nav_shop;  loadOnline(URL_SHOP); true }
                R.id.nav_live  -> { currentTab = R.id.nav_live;  loadOnline(URL_LIVE); true }
                R.id.nav_blog  -> { currentTab = R.id.nav_blog;  loadOnline(URL_BLOG); true }
                R.id.nav_order -> { openWhatsApp();                                     true }
                else           -> false
            }
        }
    }

    // ── ACCUEIL : page embarquée (fonctionne HORS LIGNE) ────────────
    private fun loadHome() {
        b.webView.loadUrl("file:///android_asset/offline_home.html")
        showOfflineBar(false)
    }

    // ── PAGES ONLINE : boutique / live / blog ───────────────────────
    private fun loadOnline(url: String) {
        if (!isOnline()) {
            showNoConnectionToast()
            showOfflinePage()
            return
        }
        showOfflineBar(false)
        b.webView.loadUrl(url)
    }

    private fun showOfflinePage() {
        b.webView.loadUrl("file:///android_asset/offline_home.html")
        showOfflineBar(true)
        b.bottomNav.selectedItemId = R.id.nav_home
        currentTab = R.id.nav_home
    }

    // ── GESTION URLS EXTERNES ────────────────────────────────────────
    private fun handleExternalUrl(url: String): Boolean {
        return when {
            url.startsWith("https://wa.me") || url.startsWith("whatsapp://") -> {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            }
            url.startsWith("tel:") || url.startsWith("mailto:") -> {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            }
            url.startsWith("https://maps") || url.startsWith("geo:") -> {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            }
            // Liens internes du site → ouvrir dans le WebView
            url.contains("dzigbodihope.com") || url.contains("bizva.biz") -> false
            // Tout le reste → navigateur externe
            url.startsWith("http") -> {
                openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                true
            }
            else -> false
        }
    }

    private fun openWhatsApp() {
        openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP)))
    }

    private fun openIntent(intent: Intent) {
        try { startActivity(intent) }
        catch (e: Exception) {
            Toast.makeText(this, "Application non trouvée", Toast.LENGTH_SHORT).show()
        }
    }

    // ── RÉSEAU ───────────────────────────────────────────────────────
    private fun isOnline(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── UI HELPERS ───────────────────────────────────────────────────
    private fun showLoader(show: Boolean) {
        b.loader.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showOfflineBar(show: Boolean) {
        b.offlineBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showNoConnectionToast() {
        Toast.makeText(this, "📵 Pas de connexion internet", Toast.LENGTH_SHORT).show()
    }

    // ── RETOUR ARRIÈRE ───────────────────────────────────────────────
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (b.webView.canGoBack() && currentTab != R.id.nav_home) {
            b.webView.goBack()
        } else if (currentTab != R.id.nav_home) {
            b.bottomNav.selectedItemId = R.id.nav_home
        } else {
            super.onBackPressed()
        }
    }
}
