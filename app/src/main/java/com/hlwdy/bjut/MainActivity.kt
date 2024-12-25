package com.hlwdy.bjut

import BiometricHelper
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hlwdy.bjut.databinding.ActivityMainBinding
import com.hlwdy.bjut.ui.AboutActivity
import com.hlwdy.bjut.ui.LogViewActivity
import com.hlwdy.bjut.ui.LoginActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    fun showToast(message: String) {
        android.os.Handler(this.mainLooper).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    fun updateVPNTK(tk:String){
        android.os.Handler(this.mainLooper).post {
            account_session_util(this).editWebVpnTK(tk,(System.currentTimeMillis() / 1000L).toString())
        }
    }

    fun refreshWebVpn(isForce:Boolean=false){
        fun getCookieValue(cookieString: String, key: String): String? {
            return cookieString.split(";")
                .map { it.trim() }
                .find { it.startsWith("$key=") }
                ?.substringAfter("=")
        }
        if(!isForce){
            var time_str=account_session_util(this).getUserDetails()[account_session_util.KEY_WEBVPNTKTIME]
            if(time_str!=null){
                if(time_str.toLong()+1200>(System.currentTimeMillis() / 1000L)){
                    //showToast("use tk cache")
                    appLogger.e("Info", "Use WebVPNTK cache:$time_str")
                    return
                }
            }
        }
        //refresh webvpn tk
        BjutAPI().getWebVpnCookie(account_session_util(this).getUserDetails()[account_session_util.KEY_SESS].toString(),object :
            Callback {
            override fun onFailure(call: Call, e: IOException) {
                showToast("network error")
            }
            override fun onResponse(call: Call, response: Response) {
                var tk=getCookieValue(response.request.headers.get("Cookie").toString(),"wengine_vpn_ticketwebvpn_bjut_edu_cn")
                updateVPNTK(tk.toString())
                appLogger.e("Info", "New WebVPN tk $tk")
                if(isForce)showToast("WEBVPN刷新完毕")
                //showToast("new webvpn tk $tk")
                //prelogin to my
                BjutAPI().WebVpnLoginMy(tk.toString()
                    ,object :
                        Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            showToast("network error")
                        }
                        override fun onResponse(call: Call, response: Response) {}
                    })
            }
        })
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appLogger.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //webvpn
        refreshWebVpn()

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,R.id.nav_news, R.id.nav_card, R.id.nav_library,R.id.nav_schedule,R.id.nav_otp,R.id.nav_network,R.id.nav_ehall,R.id.nav_ai
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        val navheader=binding.navView.getHeaderView(0)

        var autoCheck=settings_util(this).getSettingBool(settings_util.KEY_AUTOUPDATE)
        if(autoCheck==null){
            autoCheck=true
        }
        if(autoCheck){
            checkUpdate(true)
        }

        var user_info=account_session_util(this).getUserDetails()
        navheader.findViewById<TextView>(R.id.name_text).setText(user_info[account_session_util.KEY_NAME])
        navheader.findViewById<TextView>(R.id.userid).setText(user_info[account_session_util.KEY_USERNAME])

        appLogger.e("Info", "User配置读取完成-"+user_info[account_session_util.KEY_USERNAME])
    }

    fun clearAppWebView() {
        try {
            // 获取应用的 app_webview 目录
            val webviewDir = File(this.applicationInfo.dataDir, "app_webview")
            if (webviewDir.exists()) {
                webviewDir.deleteRecursively()
            }
            showToast("Cleared successfully")

        } catch (e: Exception) {
            showToast("Error clearing webview data: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    fun logout(){
        Toast.makeText(this, "logout", Toast.LENGTH_SHORT).show()
        account_session_util(this).logoutUser()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun ShowUpdate(res: JSONObject,isAuto:Boolean) {
        val Curversion = packageManager.getPackageInfo(packageName, 0).versionName
        if (Curversion != res.getString("tag_name")) {
            if(isAuto){
                var ignoreVersion=settings_util(this).getSettingStr(settings_util.KEY_UPDATEIGNORE)
                if(ignoreVersion==res.getString("tag_name")){
                    appLogger.e("Info","Ignore update:$ignoreVersion")
                    return
                }
            }
            val tmp = res.getJSONArray("assets")
            android.os.Handler(this.mainLooper).post {
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(32, 16, 32, 16)
                }

                // 版本信息
                TextView(this).apply {
                    text = """  
    发布日期：${res.getString("published_at")}
    更新内容：

${res.getString("body")}

点击下载 (推荐arm64-v8a)：  
""".trimIndent()
                    container.addView(this)
                }

                // 为每个下载项创建按钮
                for (i in 0 until tmp.length()) {
                    val classObject = tmp.getJSONObject(i)
                    val name = classObject.getString("name")
                    val url = classObject.getString("browser_download_url")
                    val itemLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 10, 0, 10)
                        }
                        gravity = Gravity.CENTER_VERTICAL
                    }
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
                    val textContainer = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    }
                    TextView(this).apply {
                        text = name
                        textSize = 16f
                        maxLines = 3
                        ellipsize = TextUtils.TruncateAt.END
                        textContainer.addView(this)
                    }
                    itemLayout.addView(textContainer)
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
                /*
                for (i in 0 until tmp.length()) {
                    val classObject = tmp.getJSONObject(i)
                    val name = classObject.getString("name")
                    val url = classObject.getString("browser_download_url")
/*
                    TextView(this).apply {
                        text = name
                        setPadding(0, 16, 0, 4)
                        container.addView(this)
                    }
*/
                    MaterialButton(this).apply {
                        text = "$name"
                        setOnClickListener {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }
                        isAllCaps = false
                        container.addView(this)
                    }
                }

                 */

                val scrollView = ScrollView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                scrollView.addView(container)

                val dialog=MaterialAlertDialogBuilder(this)
                    .setTitle("可更新:$Curversion -> " + res.getString("tag_name")+"(new)")
                    .setView(scrollView)
                    .setNegativeButton("稍后再说") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                if(isAuto){
                    dialog.setNeutralButton("忽略此次更新") { dialog, _ ->
                        settings_util(this).editSettingStr(settings_util.KEY_UPDATEIGNORE,res.getString("tag_name"))
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
        }else{
            if(!isAuto)showToast("已是最新版本")
        }
    }

    fun checkUpdate(isAuto:Boolean){
        ApkUpdate().getLatest(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showToast("更新检查失败")
            }
            override fun onResponse(call: Call, response: Response) {
                val res_text=response.body?.string().toString()
                try{
                    val res=JSONObject(res_text)
                    ShowUpdate(res,isAuto)
                }catch (e: JSONException){
                    showToast("error")
                    appLogger.e("Error", "Try CheckUpdate error",e)
                }
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clearCache->{
                clearAppWebView()
                true
            }
            //logout
            R.id.action_logout -> {
                if(account_session_util(this).getUserDetails()[account_session_util.KEY_OTPDATA]==null){
                    logout()
                }else{
                    MaterialAlertDialogBuilder(this)
                        .setTitle("退出前请备份好OTPdata，否则无法找回，是否确认退出?")
                        .setPositiveButton("继续退出") { _, _ ->
                            logout()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                true
            }
            R.id.action_logLook->{
                startActivity(Intent(this, LogViewActivity::class.java))
                true
            }
            R.id.action_about->{
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            R.id.action_checkUpdate->{
                checkUpdate(false)
                true
            }
            R.id.action_refreshWebvpn->{
                refreshWebVpn(true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
        //BiometricHelper(this).authenticate({},{finish()})//跳转生物验证
    }
}