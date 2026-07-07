package com.example

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.service.ServerForegroundService
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppLogger

class WebViewActivity : ComponentActivity() {

    private var webView: WebView? = null
    var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        const val EXTRA_SANDBOX_MODE = "EXTRA_SANDBOX_MODE"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isSandbox = intent.getBooleanExtra(EXTRA_SANDBOX_MODE, false)
        val urlToLoad = if (isSandbox) {
            "file:///android_asset/editor.html"
        } else {
            "http://127.0.0.1:8080"
        }

        setContent {
            MyApplicationTheme {
                val isLoading = remember { mutableStateOf(true) }
                val progress = remember { mutableFloatStateOf(0f) }
                val webViewTitle = remember { mutableStateOf(if (isSandbox) "Offline Editor" else "VS Code") }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = webViewTitle.value,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (isSandbox) "Local Sandbox" else "127.0.0.1:8080",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF1E1E1E),
                                titleContentColor = Color.White,
                                actionIconContentColor = Color.White,
                                navigationIconContentColor = Color.White
                            ),
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (webView?.canGoBack() == true) {
                                        webView?.goBack()
                                    } else {
                                        finish()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = { webView?.reload() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reload"
                                    )
                                }
                                if (!isSandbox) {
                                    IconButton(onClick = {
                                        AppLogger.log("WebView", "User initiated Stop Server from IDE")
                                        val stopIntent = Intent(this@WebViewActivity, ServerForegroundService::class.java).apply {
                                            action = ServerForegroundService.ACTION_STOP
                                        }
                                        startService(stopIntent)
                                        finish()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Stop Server",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFF1E1E1E))
                    ) {
                        WebViewScreen(
                            url = urlToLoad,
                            onProgressChanged = { p -> progress.floatValue = p },
                            onLoadingStateChanged = { loading -> isLoading.value = loading },
                            onTitleReceived = { title -> webViewTitle.value = title },
                            onWebViewCreated = { wv -> webView = wv }
                        )

                        if (isLoading.value) {
                            LinearProgressIndicator(
                                progress = { progress.floatValue },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color(0xFF2D2D2D)
                            )
                        }
                    }
                }
            }
        }

        // Add back-press callback to handle WebView history
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2002) {
            val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    url: String,
    onProgressChanged: (Float) -> Unit,
    onLoadingStateChanged: (Boolean) -> Unit,
    onTitleReceived: (String) -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    loadsImagesAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    // Support scaling and layout fitting
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onLoadingStateChanged(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onLoadingStateChanged(false)
                        onProgressChanged(1.0f)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val requestUrl = request?.url.toString()
                        if (requestUrl.startsWith("http://127.0.0.1") || requestUrl.startsWith("http://localhost") || requestUrl.startsWith("file:///")) {
                            return false
                        }
                        // Open external links in external browser
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            AppLogger.log("WebView", "Could not resolve external link: ${e.message}")
                        }
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChanged(newProgress / 100f)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        title?.let { onTitleReceived(it) }
                    }

                    // For code-server file uploads/import mappings
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        val activity = context as? WebViewActivity ?: return false
                        activity.fileUploadCallback = filePathCallback
                        
                        val intent = fileChooserParams?.createIntent() ?: return false
                        try {
                            activity.startActivityForResult(intent, 2002)
                        } catch (e: Exception) {
                            return false
                        }
                        return true
                    }
                }

                onWebViewCreated(this)
                loadUrl(url)
            }
        },
        update = { webView ->
            // Optionally update state here if the URL changes dynamically
        }
    )
}
