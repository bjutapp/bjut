package com.hlwdy.bjut.ui.home

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hlwdy.bjut.BaseFragment
import com.hlwdy.bjut.BjutAPI
import com.hlwdy.bjut.R
import com.hlwdy.bjut.RouterActivity
import com.hlwdy.bjut.account_session_util
import com.hlwdy.bjut.appLogger
import com.hlwdy.bjut.databinding.FragmentHomeBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    fun showToast(message: String) {
        activity?.let { fragmentActivity ->
            Handler(Looper.getMainLooper()).post {
                if (isAdded) {
                    Toast.makeText(fragmentActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var mailRes : String =""
    private var isWaitMail=false
    private var isLoadingMail=false

    fun drawMailInfo(data: String){
        val res = JSONObject(data)
        if(res.getString("e")=="0"){
            val tmp=res.getJSONObject("d").getJSONArray("fixed_app")
            for (i in 0 until tmp.length()){
                if(tmp.getJSONObject(i).getString("key")=="wdyj"){
                    val l=tmp.getJSONObject(i).getJSONArray("email")

                    activity?.let { fragmentActivity ->
                        Handler(Looper.getMainLooper()).post {
                            if (isAdded) {
                                val container = LinearLayout(requireContext()).apply {
                                    orientation = LinearLayout.VERTICAL
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    setPadding(32, 16, 32, 16)
                                }
                                for (j in 0 until l.length()) {
                                    val classObject = l.getJSONObject(j)
                                    val name = classObject.getString("name")
                                    val url = classObject.getString("url")
                                    val num = classObject.getString("value")
                                    val itemLayout = LinearLayout(requireContext()).apply {
                                        orientation = LinearLayout.HORIZONTAL
                                        layoutParams = LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                        ).apply {
                                            setMargins(0, 10, 0, 10)
                                            setPadding(4,10,4,10)
                                        }
                                        gravity = Gravity.CENTER_VERTICAL
                                    }
                                    ImageView(requireContext()).apply {
                                        setImageResource(R.drawable.baseline_email_24)
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
                                    val textContainer = LinearLayout(requireContext()).apply {
                                        orientation = LinearLayout.VERTICAL
                                        layoutParams = LinearLayout.LayoutParams(
                                            0,
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            1f
                                        )
                                    }
                                    TextView(requireContext()).apply {
                                        text = name
                                        textSize = 16f
                                        maxLines = 3
                                        ellipsize = TextUtils.TruncateAt.END
                                        textContainer.addView(this)
                                    }
                                    itemLayout.addView(textContainer)

                                    val badgeContainer = FrameLayout(requireContext()).apply {
                                        layoutParams = LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.WRAP_CONTENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                        ).apply {
                                            marginStart = 16
                                        }
                                    }
                                    val badge = TextView(requireContext()).apply {
                                        val size = 50
                                        layoutParams = FrameLayout.LayoutParams(size, size)
                                        background = GradientDrawable().apply {
                                            shape = GradientDrawable.OVAL
                                            if(num!="0") setColor(Color.RED)
                                            else setColor(Color.GRAY)
                                        }
                                        text = num
                                        setTextColor(Color.WHITE)
                                        textSize = 13f
                                        gravity = Gravity.CENTER
                                        minWidth = size
                                        minHeight = size
                                    }
                                    badgeContainer.addView(badge)
                                    itemLayout.addView(badgeContainer)

                                    itemLayout.apply {
                                        background = RippleDrawable(
                                            ColorStateList.valueOf(TypedValue().apply {
                                                requireContext().theme.resolveAttribute(android.R.attr.colorControlHighlight, this, true)
                                            }.data),
                                            null,
                                            ColorDrawable(Color.WHITE)
                                        )
                                        isClickable = true
                                        isFocusable = true
                                        setOnClickListener {
                                            val intent = Intent(context, MailActivity::class.java).apply {
                                                putExtra("url", url)
                                            }
                                            context.startActivity(intent)
                                        }
                                    }

                                    container.addView(itemLayout)
                                }
                                val dialog=MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("选择邮箱")
                                    .setView(container)
                                    .setNegativeButton("关闭") { dialog, _ ->
                                        dialog.dismiss()
                                    }
                                dialog.show()
                            }
                        }
                    }

                    break
                }
            }
        }else{
            showToast("error")
            appLogger.e("Error", "Try MailList error")
        }
    }

    fun launchMailRefresh(){
        //mailRes=""
        isLoadingMail=true
        BjutAPI().getMailList(account_session_util(requireContext()).getUserDetails()[account_session_util.KEY_SESS].toString(),object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                //showToast("network error")
                appLogger.e("Error","Try MailRefresh Error")
                activity?.let { fragmentActivity ->
                    Handler(Looper.getMainLooper()).post {
                        if (isAdded) {
                            hideLoading()
                        }
                    }
                }
                isLoadingMail=false
            }

            override fun onResponse(call: Call, response: Response) {
                mailRes=response.body?.string().toString()
                if(isWaitMail){
                    activity?.let { fragmentActivity ->
                        Handler(Looper.getMainLooper()).post {
                            if (isAdded) {
                                isWaitMail=false
                                drawMailInfo(mailRes)
                                hideLoading()
                            }
                        }
                    }
                }
                isLoadingMail=false
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.btnWebVpn.setOnClickListener {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.removeSessionCookies(null)
            cookieManager.setCookie(".webvpn.bjut.edu.cn", "wengine_vpn_ticketwebvpn_bjut_edu_cn="+
                    account_session_util(requireContext()).getUserDetails()[account_session_util.KEY_WEBVPNTK].toString()+"; path=/")
            cookieManager.flush()
            val intent = Intent(requireContext(), WebVpnViewActivity::class.java)
            startActivity(intent)
        }

        binding.btnCardCode.setOnClickListener {
            //val bundle = Bundle().apply { putBoolean("jump_code", true) }
            //findNavController().navigate(R.id.nav_card,bundle)
            val intent = Intent(requireContext(), RouterActivity::class.java).apply {
                action = "openCardCode"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        binding.btnMail.setOnClickListener{
            if(mailRes!=""){
                drawMailInfo(mailRes)
            }else{
                if(!isLoadingMail)launchMailRefresh()//之前可能失败了
                showLoading()
                isWaitMail=true
                //wait mail refresh
            }
        }
        isWaitMail=false
        launchMailRefresh()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}