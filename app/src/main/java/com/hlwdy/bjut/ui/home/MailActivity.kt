package com.hlwdy.bjut.ui.home

import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.hlwdy.bjut.BaseActivity
import com.hlwdy.bjut.BjutAPI
import com.hlwdy.bjut.R
import com.hlwdy.bjut.account_session_util
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

class MailActivity : BaseActivity() {
    private lateinit var webview: WebView

    fun showToast(message: String) {
        android.os.Handler(this.mainLooper).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    fun loadUrl(url:String){
        android.os.Handler(this.mainLooper).post {
            webview.settings.javaScriptEnabled=true
            webview.settings.domStorageEnabled=true
            webview.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null) {
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
                    if (url != null) {
                        request?.url?.let { uri ->
                            view?.loadUrl(uri.toString())
                            return true
                        }
                    }
                    return false
                }
            }
            webview.loadUrl(url)
            hideLoading()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mail)

        webview=findViewById<WebView>(R.id.mail_webview)
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val ourl = intent.getStringExtra("url").toString()
        showLoading()

        BjutAPI().getMailUrl(account_session_util(this).getUserDetails()[account_session_util.KEY_SESS].toString(),ourl,object :
            Callback {
            override fun onFailure(call: Call, e: IOException) {
                showToast("network error")
            }
            override fun onResponse(call: Call, response: Response) {
                try{
                    val url=response.headers["Location"].toString()
                    loadUrl(url)
                }catch (e:Exception){
                    showToast("error")
                }
            }
        })
    }

    override fun onBackPressed() {
        if (webview.canGoBack()) {
            webview.goBack()
        } else {
            super.onBackPressed()
        }
    }
}