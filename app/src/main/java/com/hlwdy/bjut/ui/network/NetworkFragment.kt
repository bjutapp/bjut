package com.hlwdy.bjut.ui.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.hlwdy.bjut.BaseFragment
import com.hlwdy.bjut.account_session_util

import java.net.HttpURLConnection
import java.net.URL


import com.hlwdy.bjut.databinding.FragmentNetworkBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select
import java.net.InetAddress
import org.json.JSONObject
import com.hlwdy.bjut.network_account_util
import kotlinx.coroutines.delay
import java.io.DataOutputStream
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

fun isInternalIp(ip: String?): Boolean {
    val privateIpPatterns = listOf(
        "^10\\..*".toRegex(),
        "^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*".toRegex(),
        "^192\\.168\\..*".toRegex()
    )
    return ip!=null&&privateIpPatterns.any { it.matches(ip) }
}
fun checkWebsiteAccessibility(urlString: String): Boolean {
    return try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout=600
        connection.readTimeout=600
        connection.instanceFollowRedirects=false
        connection.connect()

        val responseCode = connection.responseCode
        if(responseCode == HttpURLConnection.HTTP_OK){
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            //Log.d("normal","response: "+response)
            connection.disconnect()
            response.isNotEmpty()
        }else{
            connection.disconnect()
            false
        }
    } catch (e: Exception) {
        false
    }
}

fun getDataFromUrl(urlString: String): String? {
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            inputStream.bufferedReader().use { reader ->
                return reader.readText()
            }
        } else {
            // 处理错误响应
            println("Error: ${connection.responseCode}")
            return null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
fun postUrl(urlString: String, params: Set<String>): String? {
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val postData = params.joinToString("&")

        // 发送请求参数
        val outputStream = DataOutputStream(connection.outputStream)
        outputStream.writeBytes(postData)
        outputStream.flush()
        outputStream.close()
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            inputStream.bufferedReader().use { reader ->
                return reader.readText()
            }
        } else {
            // 处理错误响应
            println("Error: ${connection.responseCode}")
            return null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
suspend fun checkBjutLocation():String{
    return coroutineScope {
        //测试是否为宿舍光猫
        val bPingDeferred = async(Dispatchers.IO){checkWebsiteAccessibility("http://10.21.221.98")}
        //测试是否为bjut_wifi
        val wPingDeferred = async(Dispatchers.IO){checkWebsiteAccessibility("http://wlgn.bjut.edu.cn")}
        //测试是否为其他情况（例如有线网）
        val lPingDeferred = async(Dispatchers.IO){checkWebsiteAccessibility("http://172.30.201.2")}

        val bwPingRes=select {
            bPingDeferred.onAwait{
                if(it){
                    "dorm"
                }else if(wPingDeferred.await()){
                    "wifi"
                }else{""}
            }
            wPingDeferred.onAwait{
                if(it){
                    "wifi"
                }else if(bPingDeferred.await()){
                    "dorm"
                }else{""}
            }
        }
        if(bwPingRes!=""){
            bwPingRes
        }else {
            val lres = lPingDeferred.await()
            if (lres) {
                "bjut"
            }else {
                "unknownType"
            }
        }
    }
}
fun checkLoginStatusBjut():Pair<Boolean,Boolean> {
    val urlString="https://lgn.bjut.edu.cn"
    val htmlData=getDataFromUrl(urlString)?:return Pair(false,false)
    val ipv4states=htmlData.contains("<!--Dr.COMWebLoginID_1.htm-->")
    val ipv6states=!htmlData.contains(";v6ip='::'")
    return Pair(ipv4states,(ipv4states&&ipv6states))
}
fun checkLoginStatusDorm():Pair<Boolean,Boolean> {
    val urlString="http://10.21.221.98:801/eportal/portal/online_list"
    val resString = getDataFromUrl(urlString) ?: return Pair(false,false)
    try {
        val jsonString=resString.substringAfter("(").substringBeforeLast(")")
        if (jsonString != "") {
            val jsonObject = JSONObject(jsonString)
            val res=(jsonObject.getInt("result") == 1)
            return Pair(res,res)
        } else {
            return Pair(false,false)
        }
    }catch (e:Exception){
        return Pair(false,false)
    }
}
fun checkLoginStatusWifiIpv6():Boolean{
    val urlString="https://lgn6.bjut.edu.cn"
    val htmlData=getDataFromUrl(urlString)?:return false
    return htmlData.contains("<!--Dr.COMWebLoginID_1.htm-->")
}
fun checkLoginStatusWifi():Pair<Boolean,Boolean> {
    val urlString="https://wlgn.bjut.edu.cn/drcom/chkstatus?callback=dr1002"
    val resString = getDataFromUrl(urlString) ?: return Pair(false,false)
    return try {
        val jsonString=resString.substringAfter("(").substringBeforeLast(")")
        if (jsonString != "") {
            val jsonObject = JSONObject(jsonString)
            val res=(jsonObject.getInt("result") == 1)
            if(res){
                Pair(true,checkLoginStatusWifiIpv6())
            }else{
                Pair(false,false)
            }
        } else {
            Pair(false,false)
        }
    }catch (e:Exception){
        Pair(false,false)
    }
}
fun checkLoginStatus(networkStates:String):Pair<Boolean,Boolean> {
    return when(networkStates){
        "wifi"->checkLoginStatusWifi()
        "dorm"->checkLoginStatusDorm()
        "bjut"->checkLoginStatusBjut()
        else->Pair(false,false)
    }
}
fun bjutNetworkLoginDorm(networkAccount: network_account_util):Pair<Boolean,Boolean>{
    val user=networkAccount.getUserName()
    val pwd=networkAccount.getUserPwd()
    val urlString="http://10.21.221.98:801/eportal/?c=Portal&a=login&login_method=1&user_account=$user%40campus&user_password=$pwd"

    val resString=getDataFromUrl(urlString)?:"jsonpReturn({\"result\":0})"
    val jsonString=resString.substringAfter("(").substringBeforeLast(")")
    if (jsonString != "") {
        val jsonObject = JSONObject(jsonString)
        val res=(jsonObject.getInt("result") == 1)
        return Pair(res,res)
    } else {
        return Pair(false,false)
    }
}
fun bjutNetworkLoginWifi(networkAccount: network_account_util):Pair<Boolean,Boolean>{
    val user=networkAccount.getUserName()
    val pwd=networkAccount.getUserPwd()
    val urlString="http://wlgn.bjut.edu.cn/drcom/login?callback=dr1002&DDDDD=${user}&upass=${pwd}&0MKKey=123456&R1=0&R2=&R3=0&R6=0&para=00&v6ip=&terminal_type=1&lang=zh%2Dcn&jsVersion=4.1&v=1234&lang=zh"
    val resString=getDataFromUrl(urlString)?:"jsonpReturn({\"result\":0})"
    //Log.d("normal",resString)
    val jsonString=resString.substringAfter("(").substringBeforeLast(")")
    if (jsonString != "") {
        val jsonObject = JSONObject(jsonString)
        return Pair((jsonObject.getInt("result") == 1),false)
    } else {
        return Pair(false,false)
    }
}
fun bjutNetworkLoginBjut(networkAccount: network_account_util):Pair<Boolean,Boolean>{
    val user=networkAccount.getUserName()
    val pwd=networkAccount.getUserPwd()
    val ipv6address=try{
        val ipv6Res=postUrl("http://lgn6.bjut.edu.cn/V6?https://lgn.bjut.edu.cn",setOf("DDDDD=${user}","upass=${pwd}","v46s=0","v6ip=","f4serip=172.30.201.10","0MKKey="))
        //Log.d("normal",ipv6Res?:"empty lgn6 response")
        ipv6Res?.substringAfter("name='v6ip' value='")?.substringBeforeLast("'>")
    }catch (e:Exception){
        null
    }
    //Log.d("normal",ipv6address?:"no ipv6 address")
    val htmlData=ipv6address?.let{
        postUrl("https://lgn.bjut.edu.cn/",setOf("DDDDD=${user}","upass=${pwd}","0MKKey=Login","v6ip=${it}"))
    }?:run{
        postUrl("https://lgn.bjut.edu.cn/",setOf("DDDDD=${user}","upass=${pwd}","v46s=1","0MKKey="))
    }?:return Pair(false,false)
    //Log.d("normal",htmlData?:"empty lgn response")
    return Pair(htmlData.contains("<!--Dr.COMWebLoginID_3.htm-->"),ipv6address!=null)
}

fun bjutNetworkLoginBjutIpv6(networkAccount: network_account_util):Boolean{
    val user=networkAccount.getUserName()
    val pwd=networkAccount.getUserPwd()

    val htmlData=postUrl("https://lgn6.bjut.edu.cn/",setOf("DDDDD=${user}","upass=${pwd}","v46s=2","0MKKey="))?:return false
    //Log.d("normal",htmlData)
    return htmlData.contains("<!--Dr.COMWebLoginID_3.htm-->")
}
fun checkNetworkFlow():Long{
    val urlString="https://lgn.bjut.edu.cn"
    val htmlData=getDataFromUrl(urlString)?:return -1
    val pattern = Regex("flow='(.*?)'")
    val matchResult = pattern.find(htmlData)
    val flowValue = matchResult?.groupValues?.get(1)?.trim()
    return flowValue?.let{it.toLongOrNull()?:-1} ?:-1
}
fun formatFlowSize(sizeInBytes: Long): String {
    val unit = 1024
    if (sizeInBytes < unit) return "$sizeInBytes B"
    val exp = (ln(sizeInBytes.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = "KMGTPE"[exp - 1] + "i"
    return String.format(Locale("zh", "CN"),"%.2f %sB", sizeInBytes / unit.toDouble().pow(exp.toDouble()), pre)
}
fun checkInBjut():Int{
   return try {
        val addresses = InetAddress.getAllByName("www.bjut.edu.cn")
        if(addresses.any { isInternalIp(it.hostAddress) }) {
            1
        }else {
            0
        }
    }catch (e: Exception) {
        -1
    }
}
class NetworkFragment : BaseFragment() {

    private var _binding: FragmentNetworkBinding? = null
    private var networkStates="unknown"
    private var networkLoginipv4States=false
    private var networkLoginipv6States=false
    private var networkFlow=-1L
    private var lastRequireLoginTime=0L
    private var wifiInterface:Network?=null
    private var networkCallback: ConnectivityManager.NetworkCallback? =null
    private var connectivityManager:ConnectivityManager?=null
    val handler = Handler(Looper.getMainLooper())
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private fun showToast(message: String) {
        activity?.let { fragmentActivity ->
            Handler(Looper.getMainLooper()).post {
                if (isAdded) {
                    Toast.makeText(fragmentActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun updateShow(){
        when(networkStates){
            "unknown"->binding.networkStateMonitor.setText("网络状态未知")
            "wifi"->binding.networkStateMonitor.setText("bjut_wifi")
            "dorm"->binding.networkStateMonitor.setText("宿舍光猫")
            "bjut"->binding.networkStateMonitor.setText("有线网")
            "unknownType"->binding.networkStateMonitor.setText("未知校内网络")
            "outBJUT"->binding.networkStateMonitor.setText("校外")
            "noNetwork"->binding.networkStateMonitor.setText("无网络")
            else->binding.networkStateMonitor.setText("error: $networkStates")
        }
        if(networkStates=="wifi"&&networkLoginipv4States){
            binding.btnwlgnipv6Login.visibility= View.VISIBLE
        }else{
            binding.btnwlgnipv6Login.visibility= View.INVISIBLE
        }
        if(networkLoginipv4States&&networkLoginipv6States){
            binding.networkLoginStateMonitor.setText("登录(v4&v6)")
        }else if(networkLoginipv4States){
            binding.networkLoginStateMonitor.setText("登录(ipv4)")
        }else if(networkLoginipv6States){
            binding.networkLoginStateMonitor.setText("登录(ipv6)")
        }else{
            binding.networkLoginStateMonitor.setText("未登录")
        }
        if(networkFlow!=-1L){
            val text=formatFlowSize( networkFlow*1024)
            binding.networkFlowMonitor.setText(text)
        }else{
            binding.networkFlowMonitor.setText("")
        }
    }
    private fun bindWifi(){
        try {
            if(connectivityManager ==null){
                connectivityManager=requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            }
            if(networkCallback==null) {
                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        wifiInterface = network
                    }
                    override fun onLost(network: Network) {
                        wifiInterface = null
                        connectivityManager!!.bindProcessToNetwork(null)
                    }
                }
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager!!.requestNetwork(request, networkCallback!!)
        }catch (e:Exception){
            showToast("未能绑定wifi网络")
        }
    }
    private fun unBindWifi(){
        networkCallback?.let{connectivityManager!!.unregisterNetworkCallback(it)}
        wifiInterface=null
    }
    private fun bindNetworkToWifi(){
        wifiInterface?.let {connectivityManager!!.bindProcessToNetwork(it)}
    }
    private fun unBindNetworkToWifi(){
        connectivityManager!!.bindProcessToNetwork(null)
    }

    private fun checkNetworkStates(networkAccount: network_account_util,showToastFlag: Boolean) {
        showLoading()
        lifecycleScope.launch {
            for (i in 0..2) {
                if(wifiInterface==null){
                    withContext(Dispatchers.IO) {
                        delay(10L)
                    }
                }else{
                    break
                }
            }
            if(wifiInterface==null){
                showToast("未使用wifi网络")
            }
            bindNetworkToWifi()
            val inBjut = withContext(Dispatchers.IO) { checkInBjut() }
            if (inBjut == 1) {
                networkStates=withContext(Dispatchers.IO) { checkBjutLocation() }
                val pair=withContext(Dispatchers.IO) { checkLoginStatus(networkStates) }
                networkLoginipv4States=pair.first
                networkLoginipv6States=pair.second
                if(networkLoginipv4States) {
                    networkFlow=withContext(Dispatchers.IO) { checkNetworkFlow() }
                }else{
                    networkFlow=-1
                    if (lastRequireLoginTime != 0L) {
                        if (System.currentTimeMillis() - lastRequireLoginTime < 5000) {
                            loginNetwork(networkAccount)
                        }
                        lastRequireLoginTime = 0L
                    }

                }
            } else if (inBjut == 0) {
                networkStates = "outBJUT"
                networkLoginipv4States = false
                networkLoginipv6States = false
                networkFlow = -1
            } else if (inBjut == -1) {
                networkStates = "noNetwork"
                networkLoginipv4States = false
                networkLoginipv6States = false
                networkFlow = -1
            }
            hideLoading()
            unBindNetworkToWifi()
            if(showToastFlag) {
                showToast("刷新完成")
            }
            updateShow()
        }
    }
    private fun setPassword(networkAccount: network_account_util){
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(if(networkAccount.haveData())"更新保存的密码" else "保存密码 (首次)")
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val usernameLayout = TextInputLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hint = "账号"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE // 设置外边框样式
        }
        val username = TextInputEditText(requireContext()).apply { setText(account_session_util(requireContext()).getUserDetails()[account_session_util.KEY_USERNAME]) }
        usernameLayout.addView(username)

        val passwordLayout = TextInputLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            hint = "密码"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            // 添加密码可见性开关
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val password = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            //setText(networkAccount.getUserPwd())
        }
        passwordLayout.addView(password)

        layout.addView(usernameLayout)
        layout.addView(passwordLayout)
        builder.setView(layout)
        builder.setPositiveButton("保存") { dialog, _ ->
            val userAccount = username.text.toString()
            val userPassword = password.text.toString()
            networkAccount.editUserData(userAccount, userPassword)
            showToast("保存成功")
            dialog.cancel()
        }

        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.cancel()
        }

        val dialog = builder.create()
        dialog.show()
    }
    private fun loginNetwork(networkAccount: network_account_util){
        if(networkLoginipv4States){
            showToast("已登录")
            return
        }
        if(!networkAccount.haveData()){
            setPassword(networkAccount)
        }
        bindNetworkToWifi()
        when (networkStates) {
            "dorm" -> {
                lifecycleScope.launch {
                    val pair=withContext(Dispatchers.IO) {
                        bjutNetworkLoginDorm(networkAccount)
                    }
                    networkLoginipv4States=pair.first
                    networkLoginipv6States=pair.second
                    if(networkLoginipv4States){
                        updateShow()
                        withContext(Dispatchers.IO) {
                            delay(10L)
                        }
                        networkFlow=withContext(Dispatchers.IO) {checkNetworkFlow()}
                        updateShow()
                    }else{
                        showToast("登录失败")
                    }
                    unBindNetworkToWifi()
                }
            }
            "wifi" -> {
                lifecycleScope.launch {
                    val pair=withContext(Dispatchers.IO) {
                        bjutNetworkLoginWifi(networkAccount)
                    }
                    networkLoginipv4States=pair.first
                    networkLoginipv6States=pair.second
                    if(networkLoginipv4States){
                        updateShow()
                        withContext(Dispatchers.IO) {
                            delay(10L)
                        }
                        networkFlow=withContext(Dispatchers.IO) {checkNetworkFlow()}
                        updateShow()
                    }else{
                        showToast("登录失败")
                    }
                    unBindNetworkToWifi()
                }
            }
            "bjut" -> {
                lifecycleScope.launch {
                    val pair=withContext(Dispatchers.IO) {
                        bjutNetworkLoginBjut(networkAccount)
                    }
                    networkLoginipv4States=pair.first
                    networkLoginipv6States=pair.second
                    if(networkLoginipv4States){
                        updateShow()
                        withContext(Dispatchers.IO) {
                            delay(10L)
                        }
                        networkFlow=withContext(Dispatchers.IO) {checkNetworkFlow()}
                        updateShow()
                    }else{
                        showToast("登录失败")
                    }
                    unBindNetworkToWifi()
                }
            }
            else -> {
                lastRequireLoginTime=System.currentTimeMillis()
                unBindNetworkToWifi()
            }
        }
    }
    private fun loginNetworkWifiIpv6(networkAccount: network_account_util){
        if(networkLoginipv6States){
            showToast("已登录")
            return
        }
        if(!networkAccount.haveData()){
            setPassword(networkAccount)
        }
        if(networkStates!="wifi") {
            return
        }
        bindNetworkToWifi()
        lifecycleScope.launch {
            networkLoginipv6States=withContext(Dispatchers.IO) {
                bjutNetworkLoginBjutIpv6(networkAccount)
            }
            if(networkLoginipv6States) {
                updateShow()
            }else{
                showToast("登录失败")
            }
            unBindNetworkToWifi()
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkBinding.inflate(inflater, container, false)
        bindWifi()
        val networkAccount=network_account_util(requireContext())
        val loginNow = arguments?.getBoolean("loginNow")?:false
        if(loginNow){
            lastRequireLoginTime=System.currentTimeMillis()
            //checkNetworkStates()会在确认网络状态后执行登录
        }
        checkNetworkStates(networkAccount,false)
        val root: View = binding.root
        binding.btnRefresh.setOnClickListener {
            checkNetworkStates(networkAccount,true)
        }
        binding.btnLogin.setOnClickListener {
            loginNetwork(networkAccount)
        }
        binding.btnSetPwd.setOnClickListener {
            setPassword(networkAccount)
        }
        binding.btnwlgnipv6Login.visibility= View.INVISIBLE
        binding.btnwlgnipv6Login.setOnClickListener{
            loginNetworkWifiIpv6(networkAccount)
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    override fun onDestroy(){
        super.onDestroy()
        unBindWifi()
    }
}