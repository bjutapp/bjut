package com.hlwdy.bjut.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.hlwdy.bjut.R

class WebVpnViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var chipGroup: ChipGroup
    private lateinit var progressBar: LinearProgressIndicator

    data class Website(
        val name: String,
        val url: String
    )

    private val websites = listOf(
        //Website("教务管理系统", "https://webvpn.bjut.edu.cn/https/77726476706e69737468656265737421fae046903f242652741d9de29d51367becd8/"),
        Website("教务管理系统", "https://webvpn.bjut.edu.cn/https/77726476706e69737468656265737421fae046903f242652741d9de29d51367becd8/sso/ddlogin"),
        //https://jwglxt.bjut.edu.cn/
        Website("网络计费系统", "https://webvpn.bjut.edu.cn/https/77726476706e69737468656265737421faf152992b362652741d9de29d51367b6c5c/"),
        //https://jfself.bjut.edu.cn/
        Website("北工大12345","https://webvpn.bjut.edu.cn/https/77726476706e69737468656265737421f2f045cd75633c05300a83b98c1b263166c764a1/"),
        Website("图书馆资源","https://webvpn.bjut.edu.cn/https/77726476706e69737468656265737421fcfe43862e297d5170468ba68d416d30b4b1ee2861/"),
        Website("EduOJ", "https://webvpn.bjut.edu.cn/http/77726476706e69737468656265737421a1a013d2756126012946d8fccc/"),
        //http://172.21.17.104/
    )
    private fun setTitle(text:String){
        android.os.Handler(this.mainLooper).post {
            findViewById<TextView>(R.id.webTitle).text = text
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.webvpn_view)

        webView = findViewById(R.id.webvpn_webview)
        chipGroup = findViewById(R.id.chipGroup)
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        progressBar = findViewById(R.id.progressBar)
        setupWebView()
        createChips()
    }

    private fun setupWebView() {
            webView.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled=true//localstorage
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url != null&&url.contains("http")) {
                            view?.loadUrl(url)
                            return true
                        }
                        return false
                    }

                    // 对于Android API 24及以上版本
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        if (url != null&&url.toString().contains("http")) {
                            request?.url?.let { uri ->
                                view?.loadUrl(uri.toString())
                                return true
                            }
                        }
                        return false
                    }

                    // 可选：添加页面加载状态回调
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        // 显示加载进度条或提示
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // 隐藏加载进度条或提示
                    }
                }

                webChromeClient=object:WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (newProgress < 100) {
                            progressBar.visibility = View.VISIBLE
                            progressBar.progress = newProgress
                        } else {
                            progressBar.visibility = View.GONE
                        }
                    }
                }

                settings.apply {
                    // 支持缩放
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false

                    // 自适应屏幕
                    useWideViewPort = true
                    loadWithOverviewMode = true

                    // 允许混合内容（HTTP和HTTPS）
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
            }
    }

    private fun createChips() {
        chipGroup.isSelectionRequired=true
        websites.forEachIndexed { index, (name, url) ->
            val chip = Chip(this).apply {
                text = name
                isCheckable = true

                setOnClickListener {
                    if (isChecked) {
                        webView.loadUrl(url)
                    }
                }

                if (index == 0) {
                    isChecked = true
                    webView.loadUrl(url)
                }
            }
            chipGroup.addView(chip)
        }
    }

    // 处理返回按钮
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}