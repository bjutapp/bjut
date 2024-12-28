package com.hlwdy.bjut.ui.card

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.hlwdy.bjut.BjutAPI
import com.hlwdy.bjut.account_session_util
import com.hlwdy.bjut.databinding.FragmentCardBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import androidx.fragment.app.viewModels
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File
import java.net.URL
import android.webkit.WebView
import androidx.core.content.ContextCompat.startActivity
import com.hlwdy.bjut.BaseFragment
import com.hlwdy.bjut.appLogger
import okhttp3.Cookie
import org.json.JSONObject
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

private var isJumpCode:Boolean=false
private var isJumped:Boolean=false

private var isNeedNewID:Boolean=false

class EnhancedCachingWebViewClient(private val context: Context,private val frag:BaseFragment) : WebViewClient() {

    private val cacheDir = File(context.cacheDir, "web_cache")
    private val cssPattern = Regex("\\.css$")
    private val vendorPattern = Regex("chunk-vendors.*\\.js$")
    private val picPattern = Regex("\\.png$")

    // 缓存有效期（毫秒）
    private val CSS_CACHE_DURATION = TimeUnit.DAYS.toMillis(5) // 1天
    private val VENDOR_CACHE_DURATION = TimeUnit.DAYS.toMillis(7) // 7天
    private val PIC_CACHE_DURATION = TimeUnit.DAYS.toMillis(15)

    init {
        cacheDir.mkdirs()
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()

        when {
            cssPattern.containsMatchIn(url) -> return handleCacheableResource(url, "text/css", CSS_CACHE_DURATION)
            vendorPattern.containsMatchIn(url) -> return handleCacheableResource(url, "application/javascript", VENDOR_CACHE_DURATION)
            picPattern.containsMatchIn(url) -> return handleCacheableResource(url, "image/png", PIC_CACHE_DURATION)
        }

        // 对于其他文件，使用默认处理
        return super.shouldInterceptRequest(view, request)
    }

    private fun handleCacheableResource(url: String, mimeType: String, cacheDuration: Long): WebResourceResponse? {
        val cachedFile = File(cacheDir, url.hashCode().toString())

        // 如果缓存文件存在且未过期，直接从缓存加载
        if (cachedFile.exists() && (System.currentTimeMillis() - cachedFile.lastModified() < cacheDuration)) {
            return WebResourceResponse(mimeType, "UTF-8", FileInputStream(cachedFile))
        }

        // 如果缓存不存在或已过期，下载并缓存
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()
            val inputStream = connection.inputStream

            // 保存到缓存
            FileOutputStream(cachedFile).use { output ->
                inputStream.copyTo(output)
            }

            // 返回下载的内容
            return WebResourceResponse(mimeType, "UTF-8", FileInputStream(cachedFile))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }


    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return try {
            if (url!!.startsWith("http:") || url.startsWith("https:")) {
                false
            } else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(context,intent,null)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        if(isJumpCode){
            view.clearHistory()
        }
        frag.hideLoading()
        if(isNeedNewID){
            val cardid=url.takeLast(32)
            appLogger.e("Info", "NewCard:$cardid")
            account_session_util(context).editCardID(cardid)
            isNeedNewID=false
        }
        // 注入CSS
        val css = "@media (prefers-color-scheme: dark) { body,uni-page-body,.bdbg1,.code-bg,.news-w-bg,.bdbg,.ticket-nav,.tap2,.alert-page>uni-view,.pages_water-water-virtualcard-mywaterorder-mywaterorder uni-view,.grey1,.grey2{ background: #121212 !important; }uni-navigator,.menu-list,.news-w,.payment-method,.condition, .condition-checkbox,.amtbutton,.v-tabs__container,.v-tabs__container-item,.tr,.nowcode,.tap-box,.uni-picker-select,.uni-picker-header,.newsCol,.text-w,.txt,.itemArea_li,#popup_content,.bg12{background:#313131 !important;color:white !important;}uni-text{color:white !important}.code-w{box-shadow:none !important;}.news-w,.newsli{border-bottom:none !important;}.bdbg1,uni-view{color:white !important}.bdt{border-top: 1px solid #555555 !important;}.per-details,.bor-bottom{border-bottom: 1px solid #555555 !important;}}"
        view.evaluateJavascript(
            """  
            (function() {  
                var style = document.createElement('style');  
                style.type = 'text/css';  
                style.innerHTML = '$css';  
                document.head.appendChild(style);  
            })();  
            """,
            null
        )
        // 显示WebView
        view.visibility = View.VISIBLE
        if(isJumpCode&&!isJumped){
            view.loadUrl("https://ydapp.bjut.edu.cn/#/pages_other/qrcode/qrcode/qrcode?openid="+url.takeLast(32))
            isJumped=true
        }
    }

}

class CardFragment : BaseFragment() {

    private var _binding: FragmentCardBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val viewModel: CardViewModel by viewModels()

    fun showToast(message: String) {
        activity?.let { fragmentActivity ->
            Handler(Looper.getMainLooper()).post {
                if (isAdded) {
                    Toast.makeText(fragmentActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun loadUrl(url: String) {
        activity?.let {
            Handler(Looper.getMainLooper()).post {
                if (isAdded) {
                    viewModel.webView?.settings?.javaScriptEnabled=true
                    viewModel.webView?.webViewClient=EnhancedCachingWebViewClient(requireContext(),this)
                    viewModel.webView?.loadUrl(url)
                    viewModel.isWebViewInitialized=true
                    //hideLoading()
                }
            }
        }
    }

    private fun isNightTime(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // 检查是否在晚上23点到凌晨6点之间
        return currentHour >= 23 || currentHour < 6
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentCardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        if (viewModel.webView == null) {
            viewModel.webView = binding.cardView
        } else {
            // 如果WebView已存在，从旧的父视图中移除
            (viewModel.webView?.parent as? ViewGroup)?.removeView(viewModel.webView)
            // 将已存在的WebView添加到新的布局中
            (binding.root as? ViewGroup)?.addView(viewModel.webView)
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (isNightTime()) {
            binding.bannerLayout.visibility = View.VISIBLE
        }

        isJumpCode = arguments?.getBoolean("jump_code", false) ?: false
        isJumped=false

        showLoading()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.webView?.canGoBack() == true) {
                    viewModel.webView?.goBack()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if(!viewModel.isWebViewInitialized){
            var cardid=account_session_util(requireContext()).getUserDetails()[account_session_util.KEY_CARDID].toString()
            BjutAPI().getCardInfo(cardid,object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    showToast("network error")
                }
                override fun onResponse(call: Call, response: Response) {
                    val res=JSONObject(response.body?.string().toString())
                    if(res.getString("success")=="true"&&res.getString("data")!="null"){
                        val tk=Cookie.parseAll(response.request.url,response.headers).find { it.name == "JSESSIONID" }?.value.toString()
                        appLogger.e("Info", "CardLoad ok:$tk")
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.removeSessionCookies(null)
                        cookieManager.setCookie("ydapp.bjut.edu.cn", "JSESSIONID=$tk; path=/")
                        cookieManager.flush()
                        if(isJumpCode){
                            isJumped=true
                            loadUrl("https://ydapp.bjut.edu.cn/#/pages_other/qrcode/qrcode/qrcode?openid=$cardid")
                        }else{
                            loadUrl("https://ydapp.bjut.edu.cn/#/pages/homepage/index/index?openid=$cardid")
                        }
                    }else{
                        BjutAPI().getCardUrl(account_session_util(requireContext()).getUserDetails()[account_session_util.KEY_SESS].toString(),object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                showToast("network error")
                            }
                            override fun onResponse(call: Call, response: Response) {
                                if(response.code==302){
                                    val cardUrl= response.headers["Location"].toString()
                                    //showToast(cardUrl)
                                    isNeedNewID=true
                                    loadUrl(cardUrl)
                                }
                            }
                        })
                    }
                }
            })
        }else{
            viewModel.webViewState?.let { viewModel.webView?.restoreState(it) }
            hideLoading()
        }

    }
    override fun onPause() {
        super.onPause()
        // 保存WebView状态
        viewModel.webView?.let { webView ->
            val state = Bundle()
            webView.saveState(state)
            viewModel.webViewState = state
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (viewModel.webView?.parent as? ViewGroup)?.removeView(viewModel.webView)
        _binding = null
    }


}