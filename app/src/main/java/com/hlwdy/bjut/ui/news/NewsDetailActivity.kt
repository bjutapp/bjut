package com.hlwdy.bjut.ui.news

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hlwdy.bjut.BaseActivity
import com.hlwdy.bjut.BjutAPI
import com.hlwdy.bjut.R
import com.hlwdy.bjut.account_session_util
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NewsDetailActivity : BaseActivity() {
    private var isHiding = false

    fun showToast(message: String) {
        android.os.Handler(this.mainLooper).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    fun hideLoad(){
        android.os.Handler(this.mainLooper).post {
            hideLoading()
        }
    }
    private fun showContent(content:String,tk:String=""){
        android.os.Handler(this.mainLooper).post {
            var webview=findViewById<WebView>(R.id.news_webview)
            webview.settings.javaScriptEnabled=true
            //部分资源使用webvpn
            if(tk!=""){
                webview.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (request?.url?.host?.endsWith("webvpn.bjut.edu.cn") == true) {
                            try {
                                val connection = URL(request.url.toString()).openConnection() as HttpURLConnection
                                // 添加cookie
                                connection.setRequestProperty("Cookie", "wengine_vpn_ticketwebvpn_bjut_edu_cn=$tk")
                                // 复制原始请求
                                connection.requestMethod = request.method
                                return WebResourceResponse(
                                    connection.contentType ?: "text/html",
                                    connection.contentEncoding ?: "utf-8",
                                    connection.inputStream
                                )
                            } catch (e: Exception) {
                                showToast("network error")
                                e.printStackTrace()
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, curl: String?): Boolean {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(curl))
                        view?.context?.startActivity(intent)
                        return true
                    }

                    // 对于Android API 24及以上版本
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        request?.url?.let { uri ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()))
                            view?.context?.startActivity(intent)
                        }
                        return true
                    }
                }
            }

            val newsTitle=findViewById<TextView>(R.id.news_title)
            webview.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                // 计算滚动距离
                val scrollDelta = scrollY - oldScrollY
                // 添加一个阈值，避免微小滚动触发动画
                if (Math.abs(scrollDelta) < 10) return@setOnScrollChangeListener

                if (scrollDelta > 0) { // 向下滚动
                    if (!isHiding && newsTitle.translationY == 0f) {
                        isHiding = true
                        newsTitle.animate()
                            .translationY(-newsTitle.height.toFloat())
                            .setDuration(200)
                            .withEndAction { isHiding = false }
                            .start()
                    }
                } else { // 向上滚动
                    if (!isHiding && newsTitle.translationY < 0) {
                        isHiding = true
                        newsTitle.animate()
                            .translationY(0f)
                            .setDuration(200)
                            .withEndAction { isHiding = false }
                            .start()
                    }
                }
            }
            val titleDp = (newsTitle.height / resources.displayMetrics.density).toInt()

            val Newcontent=content.replace("bgdwzq120.bjut.edu.cn/","webvpn.bjut.edu.cn/https/77726476706e69737468656265737421f2f0458b3d2139022e468ba68d416d30c350f5ad89/")
                .replace("rsc.bjut.edu.cn/","webvpn.bjut.edu.cn/https/77726476706e69737468656265737421e2e442d2253a7d44300d8db9d6562d/")
            webview.loadDataWithBaseURL(null,
                "$Newcontent <script type=\"text/javascript\">var tables = document.getElementsByTagName(\"img\");for(var i = 0; i<tables.length; i++){tables[i].style.width = \"100%\";tables[i].style.height = \"auto\";}</script>"
                +"<style>@media (prefers-color-scheme: dark) { body,span{ background-color: #121212 !important; color: #cfcfcf  !important; } p{ background-color: #121212 !important; } }table { border-color: #333 !important; } th, td { border-color: #333 !important; }</style>"
                +"<style>body{margin-top:${titleDp}px;}</style>",
                "text/html", "UTF-8", null)
        }
    }

    private fun showAttachment(l: JSONArray){
        android.os.Handler(this.mainLooper).post {
            findViewById<FloatingActionButton>(R.id.attachmentButton).visibility= View.VISIBLE
            findViewById<FloatingActionButton>(R.id.attachmentButton).setOnClickListener {
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(24, 8, 24, 8)
                }

                for (i in 0 until l.length()) {
                    val classObject = l.getJSONObject(i)
                    val name = classObject.getString("name")
                    val url = classObject.getString("path")

                    // 创建一个水平布局来包含图标和文件名
                    val itemLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 10, 0, 10)  // 设置每个项目之间的间距
                        }
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    // 添加文件图标
                    ImageView(this).apply {
                        setImageResource(R.drawable.baseline_attach_file_24)
                        imageTintList = context.getColorStateList(
                            context.theme.obtainStyledAttributes(
                                intArrayOf(android.R.attr.colorControlNormal)
                            ).getResourceId(0, 0)
                        )
                        layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                            marginEnd = 16
                        }
                        itemLayout.addView(this)
                    }

                    // 文件名和信息的垂直布局
                    val textContainer = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f // 权重为1，占据剩余空间
                        )
                    }

                    // 文件名 TextView
                    TextView(this).apply {
                        text = name
                        textSize = 16f
                        maxLines = 3
                        ellipsize = TextUtils.TruncateAt.END
                        textContainer.addView(this)
                    }

                    itemLayout.addView(textContainer)

                    // 添加点击效果
                    itemLayout.apply {
                        background = RippleDrawable(
                            ColorStateList.valueOf(getColor(TypedValue().apply {
                                theme.resolveAttribute(android.R.attr.colorControlHighlight, this, true)
                            }.resourceId)),
                            null,
                            ColorDrawable(Color.WHITE)
                        )
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }
                    }

                    container.addView(itemLayout)
                }

                val scrollView = ScrollView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                scrollView.addView(container)

                MaterialAlertDialogBuilder(this)
                    .setTitle("新闻附件")
                    .setView(scrollView)
                    .setNegativeButton("关闭") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.news_detail)

        val title = intent.getStringExtra(EXTRA_TITLE)
        val id = intent.getStringExtra(EXTRA_ID)
        val tk=account_session_util(this).getUserDetails()[account_session_util.KEY_WEBVPNTK].toString()

        showLoading()
        BjutAPI().getNewsDetail(tk,id.toString(),object :
            Callback {
            override fun onFailure(call: Call, e: IOException) {
                hideLoad()
                showToast("network error")
            }
            override fun onResponse(call: Call, response: Response) {
                hideLoad()
                try{
                    val res=JSONObject(response.body?.string().toString())
                    if(res.getString("e")=="0"){
                        showContent(res.getJSONObject("d").getString("content"),tk)
                        if(res.getJSONObject("d").getString("newsfile")!=""){
                            showAttachment(res.getJSONObject("d").getJSONArray("newsfile"))
                        }
                    }else{
                        showContent(res.toString())
                        showToast("error")
                    }
                }catch (e:Exception){
                    showToast("error")
                }
            }
        })

        findViewById<TextView>(R.id.news_title).text = title
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ID = "extra_id"

        fun start(context: Context, newsItem: NewsItem) {
            val intent = Intent(context, NewsDetailActivity::class.java).apply {
                putExtra(EXTRA_TITLE, newsItem.title)
                putExtra(EXTRA_ID, newsItem.id)
            }
            context.startActivity(intent)
        }
    }
}