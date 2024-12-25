package com.hlwdy.bjut.ui.home

import android.content.Intent
import android.net.Uri
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
                override fun shouldOverrideUrlLoading(view: WebView?, curl: String?): Boolean {
                    if (curl != null&&curl.contains("mail.bjut.edu.cn")) {
                        view?.loadUrl(curl)
                        return true
                    }
                    if(curl!=null&&curl.startsWith("mailto:")){
                        try{
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(curl))
                            view?.context?.startActivity(intent)
                        }catch (e: Exception) {
                            showToast("无系统邮箱应用")
                        }
                    }else{
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(curl))
                        view?.context?.startActivity(intent)
                    }
                    return true
                }

                // 对于Android API 24及以上版本
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    request?.url?.let { uri ->
                        if (uri != null&&uri.toString().contains("mail.bjut.edu.cn")) {
                            view?.loadUrl(uri.toString())
                        }else{
                            if(uri!=null&&uri.toString().startsWith("mailto:")){
                                try{
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uri.toString()))
                                    view?.context?.startActivity(intent)
                                }catch (e: Exception) {
                                    showToast("无系统邮箱应用")
                                }
                            }else{
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()))
                                view?.context?.startActivity(intent)
                            }
                        }
                    }
                    return true
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