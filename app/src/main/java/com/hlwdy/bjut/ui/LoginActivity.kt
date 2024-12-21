package com.hlwdy.bjut.ui

import BiometricHelper
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hlwdy.bjut.BaseActivity
import com.hlwdy.bjut.BjutAPI
import com.hlwdy.bjut.BjutHttpRsa
import com.hlwdy.bjut.MainActivity
import com.hlwdy.bjut.R
import com.hlwdy.bjut.account_session_util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.IOException
import org.json.JSONObject

class LoginActivity : BaseActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var rememberPasswordCheckBox: CheckBox

    private lateinit var overlayView: View

    private lateinit var sharedPreferences: SharedPreferences

    fun showToast(message: String) {
        android.os.Handler(this.mainLooper).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    fun finishLogin(usname: String,name: String,tk: String,ses: String) {
        android.os.Handler(this.mainLooper).post {
            account_session_util(this).createLoginSession(usname, name, tk, ses)
        }
    }
    fun hideLoad(){
        android.os.Handler(this.mainLooper).post {
            hideLoading()
        }
    }

    fun jumpToMain(){
        // 登录成功，跳转到主界面
        val intent = Intent(this, MainActivity::class.java)
        //intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        startActivity(intent)
        finish() // 结束登录Activity，防止用户返回
    }

    class OverlayView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {

        init {
            setBackgroundColor(ContextCompat.getColor(context, R.color.bjut_blue))

            // 创建主图片的 Bitmap
            val mainOptions = BitmapFactory.Options().apply {
                inSampleSize = 7  // 缩小
            }
            val mainBitmap = BitmapFactory.decodeResource(resources, R.drawable.bjut, mainOptions)

            // 创建底部横幅的 Bitmap
            val bannerOptions = BitmapFactory.Options().apply {
                inSampleSize = 1
            }
            val bannerBitmap = BitmapFactory.decodeResource(resources, R.drawable.bjut_all, bannerOptions)

            // 创建一个新的 Bitmap 来组合两张图片
            val combinedBitmap = Bitmap.createBitmap(
                mainBitmap.width,
                mainBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(combinedBitmap)

            // 绘制主图片
            canvas.drawBitmap(mainBitmap, 0f, 0f, null)

            // 计算横幅的位置（底部）
            val bannerMatrix = Matrix()
            val scale = mainBitmap.width.toFloat() / bannerBitmap.width.toFloat()
            bannerMatrix.setScale(scale, scale)
            bannerMatrix.postTranslate(
                0f,
                mainBitmap.height - (bannerBitmap.height * scale)
            )

            // 绘制横幅
            canvas.drawBitmap(bannerBitmap, bannerMatrix, null)

            // 设置组合后的图片
            setImageBitmap(combinedBitmap)
            scaleType = ScaleType.CENTER

            // 回收不需要的 Bitmap
            mainBitmap.recycle()
            bannerBitmap.recycle()
        }

        override fun onTouchEvent(event: MotionEvent?): Boolean {
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        //splashScreen.setKeepOnScreenCondition { true }
        //setContentView(R.layout.login_page)

        overlayView = OverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.VISIBLE
        }
        val rootLayout = findViewById<FrameLayout>(android.R.id.content)
        rootLayout.addView(overlayView)

        val context=this
        scope.launch {
            if(account_session_util(context).isLoggedIn()){
                BiometricHelper(context).authenticate({//跳转生物验证
                    jumpToMain()
                },{finish()})
            }
            else {
                rootLayout.removeView(overlayView)
                setContentView(R.layout.login_page)
                usernameEditText = findViewById(R.id.usernameEditText)
                passwordEditText = findViewById(R.id.passwordEditText)
                loginButton = findViewById(R.id.loginButton)
                rememberPasswordCheckBox = findViewById(R.id.rememberPasswordCheckBox)

                sharedPreferences = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
                if (sharedPreferences.getBoolean("remember_password", false)) {
                    usernameEditText.setText(sharedPreferences.getString("username", ""))
                    passwordEditText.setText(sharedPreferences.getString("password", ""))
                    rememberPasswordCheckBox.isChecked = true
                }

                loginButton.setOnClickListener {
                    showLoading()
                    val usname = usernameEditText.text.toString()
                    val password = passwordEditText.text.toString()

                    if (rememberPasswordCheckBox.isChecked) {
                        with(sharedPreferences.edit()) {
                            putString("username", usname)
                            putString("password", password)
                            putBoolean("remember_password", true)
                            apply()
                        }
                    } else {
                        with(sharedPreferences.edit()) {
                            remove("username")
                            remove("password")
                            putBoolean("remember_password", false)
                            apply()
                        }
                    }

                    BjutAPI().login(usname,password,object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            hideLoad()
                            showToast("network error")
                        }
                        override fun onResponse(call: Call, response: Response) {
                            hideLoad()
                            //showToast(BjutHttpRsa.requestDecrypt(response.body?.string().toString()))
                            val res = JSONObject(
                                BjutHttpRsa.requestDecrypt(
                                    response.body?.string().toString()
                                )
                            )
                            val ses= Cookie.parseAll(response.request.url,response.headers).find { it.name == "eai-sess" }?.value.toString()
                            if (res.get("e")==0) {
                                finishLogin(usname,res.getJSONObject("d").get("name").toString(),res.getJSONObject("d").get("login_ticket").toString(),ses)
                                jumpToMain()
                            } else {
                                showToast(res.get("m").toString())
                            }
                        }
                    })
                }
            }
        }
        
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
