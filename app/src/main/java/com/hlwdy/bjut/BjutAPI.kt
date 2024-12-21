package com.hlwdy.bjut

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class BjutAPI {
    private fun timestamp(): String {
        return System.currentTimeMillis().toString()
    }

    fun login(usname: String,pwd:String,callback: Callback){
        val imei="bjut"
        HttpUtils().addHeader("x-requested-with","XMLHttpRequest")
            .addHeader("from-eai","1").addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
            .addHeader("authorization-str",BjutHttpRsa.requestEncrypt("{\"ticket\":\"\",\"timestamp\":"+timestamp()+"}")).addParam(
            "content",BjutHttpRsa.requestEncrypt(
                "{\"xgh\":\"$usname\",\"password\":\"$pwd\",\"username\":\"$usname\",\"cid\":\"\",\"city\":\"\",\"type\":\"\",\"del_ids\":[],\"id\":\"\",\"ids\":[],\"imei\":\"$imei\",\"keyword\":\"\",\"login_ticket\":\"\",\"mobile_type\":\"android\",\"app_id\":\"\",\"name\":\"\",\"page\":\"1\",\"page_size\":\"5\",\"pageSize\":\"10\",\"province\":\"\",\"sid\":\"$imei\",\"ticket\":\"\",\"url\":\"\",\"words\":\"\"}"
            )
        ).post("https://itsapp.bjut.edu.cn/bjutapp/wap/app-login/index",callback)
    }

    fun getCardUrl(ses: String,callback: Callback){
        HttpUtils().addHeader("Cookie","eai-sess=$ses;")
            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
            .get("https://itsapp.bjut.edu.cn/uc/api/oauth/index?redirect=https%3A%2F%2Fydapp.bjut.edu.cn%2FopenV8HomePage&appid=200220816093810809&state=V8YKT&qrcode=1",false,callback)
    }

    fun getSchedule(ses: String,year: String, term: String, week: String,callback: Callback){
        HttpUtils().addHeader("Cookie","eai-sess=$ses;")
            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5").addHeader("Referer","https://itsapp.bjut.edu.cn/site/schedule/index")
            .addParam("year",year)
            .addParam("term",term)
            .addParam("week",week)
            .addParam("type","1")
            .post("https://itsapp.bjut.edu.cn/timetable/wap/default/get-data",callback)
    }

    fun getTermWeek(ses: String,callback: Callback){
        HttpUtils().addHeader("Cookie","eai-sess=$ses;")
            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
            .get("https://itsapp.bjut.edu.cn/timetable/wap/default/get-index",true,callback)
    }

    fun getNewsList(ses: String,cid:String,page:String,callback: Callback){
        val imei="bjut"
        HttpUtils().addHeader("Cookie","eai-sess=$ses;")
            .addHeader("x-requested-with","XMLHttpRequest")
            .addHeader("from-eai","1").addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
            .addHeader("authorization-str",BjutHttpRsa.requestEncrypt("{\"ticket\":\"\",\"timestamp\":"+timestamp()+"}")).addParam(
                "content",BjutHttpRsa.requestEncrypt(
                    "{\"xgh\":\"\",\"password\":\"\",\"username\":\"\",\"cid\":\"$cid\",\"city\":\"\",\"type\":\"\",\"del_ids\":[],\"id\":\"\",\"ids\":[],\"imei\":\"$imei\",\"keyword\":\"\",\"login_ticket\":\"\",\"mobile_type\":\"android\",\"app_id\":\"\",\"name\":\"\",\"page\":\"$page\",\"page_size\":\"5\",\"pageSize\":\"10\",\"province\":\"\",\"sid\":\"$imei\",\"ticket\":\"\",\"url\":\"\",\"words\":\"\"}"
                )
            ).post("https://itsapp.bjut.edu.cn/bjutapp/wap/news/news-list",callback)
    }

    fun getWebVpnCookie(ses: String,callback: Callback){
        HttpUtils()
            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
            .addHeader("x-requested-with","cn.edu.bjut.app")
            .get("https://webvpn.bjut.edu.cn/https/77726476706e69737468656265737421f3f652d2253a7d44300d8db9d6562d/clientredirect?client_name=mc-wx&service=https%3A%2F%2Fwebvpn.bjut.edu.cn%2Flogin%3Fcas_login%3Dtrue",false,
                object :
                    Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.onFailure(call,e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        var newUrl= response.headers["Location"].toString()
                        val cookies = response.headers.values("Set-Cookie")
                            .map { it.split(";")[0] }
                            .joinToString("; ")

                        HttpUtils()
                            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
                            .addHeader("x-requested-with","cn.edu.bjut.app")
                            .addHeader("Cookie", cookies)
                            .get(newUrl,false,
                                object :
                                    Callback {
                                    override fun onFailure(call: Call, e: IOException) {
                                        callback.onFailure(call,e)
                                    }
                                    override fun onResponse(call: Call, response: Response) {
                                        newUrl= response.headers["Location"].toString()
                                        val cookies1 = "$cookies; eai-sess=$ses;"
                                        //itsapp
                                        HttpUtils()
                                            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
                                            .addHeader("x-requested-with","cn.edu.bjut.app")
                                            .addHeader("Cookie", cookies1)
                                            .get(newUrl,true,
                                                object :
                                                    Callback {
                                                    override fun onFailure(call: Call, e: IOException) {
                                                        callback.onFailure(call,e)
                                                    }
                                                    override fun onResponse(call: Call, response: Response) {
                                                        //replace
                                                        newUrl= response.request.url.toString().replace("/http/", "/https/")
                                                        //callback.onResponse(call,response)
                                                        HttpUtils()
                                                            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
                                                            .addHeader("x-requested-with","cn.edu.bjut.app")
                                                            .addHeader("Cookie", cookies)
                                                            .get(newUrl,true,
                                                                object :
                                                                    Callback {
                                                                    override fun onFailure(call: Call, e: IOException) {
                                                                        callback.onFailure(call,e)
                                                                    }
                                                                    override fun onResponse(call: Call, response: Response) {
                                                                        newUrl= response.request.url.toString().replace("/http/", "/https/")
                                                                        HttpUtils()
                                                                            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
                                                                            .addHeader("x-requested-with","cn.edu.bjut.app")
                                                                            .addHeader("Cookie", cookies)
                                                                            .get(newUrl,true,callback)
                                                                    } }
                                                            )
                                                    } }
                                            )

                                    } }
                            )

                    } }
            )
    }

    fun WebVpnLoginMy(tk: String,callback: Callback){
        HttpUtils().addHeader("Cookie","wengine_vpn_ticketwebvpn_bjut_edu_cn=$tk;")
            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
            .addHeader("x-requested-with","cn.edu.bjut.app")
            .get("https://webvpn.bjut.edu.cn/https/77726476706e69737468656265737421f3f652d2253a7d44300d8db9d6562d/login?service=https%3A%2F%2Fportal.bjut.edu.cn%2Fcommon%2FactionCasLogin%3Fredirect_url%3Dhttps%3A%2F%2Fportal.bjut.edu.cn%2Fpage%2Fsite%2Findex",false,
                object :
                    Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.onFailure(call,e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val newUrl= response.headers["Location"].toString()
                        if(newUrl=="null"){
                            callback.onFailure(call,IOException("no tk"))
                            return
                        }
                        HttpUtils().addHeader("Cookie","wengine_vpn_ticketwebvpn_bjut_edu_cn=$tk;")
                            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
                            .addHeader("x-requested-with","cn.edu.bjut.app")
                            .get(newUrl,true,callback)
                    } }
            )
    }

    fun getNewsDetail(tk: String,id:String,callback: Callback){
        HttpUtils().addHeader("Cookie","wengine_vpn_ticketwebvpn_bjut_edu_cn=$tk;")
            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
            .get("https://webvpn.bjut.edu.cn/https/77726476706e69737468656265737421e0f85388263c2652741d9de29d51367b7cd8/site/get_news_detail?id=$id",true,callback)
    }

    fun getBookList(text: String,code:String,base:String,callback: Callback){
        HttpUtils().addHeader("Cookie","")
            .addHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
            .addParam("func","find-b")
            .addParam("find_code",code)
            .addParam("local_base",base)
            .addParam("request",text)
            .post("https://libaleph.bjut.edu.cn/F",
                object :
                    Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback.onFailure(call,e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        var res=response.body?.string().toString()
                        var url = "https?://libaleph\\.bjut\\.edu\\.cn[^\\s'\"]+".toRegex().find(res)?.value.toString()
                        HttpUtils()
                            .addHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
                            .get(url,true,
                                object :
                                    Callback {
                                    override fun onFailure(call: Call, e: IOException) {
                                        callback.onFailure(call,e)
                                    }
                                    override fun onResponse(call: Call, response: Response) {
                                        res=response.body?.string().toString()
                                        url = "location\\s*=\\s*'(.*?)'".toRegex().find(res)?.groupValues?.get(1).toString()
                                        url="https://libaleph.bjut.edu.cn"+url.replace("&amp;","&")
                                        HttpUtils()
                                            .addHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
                                            .get(url,true,callback)
                                    } })
                    } }
            )
    }

    fun getBookListPage(par: String,jump:String,callback: Callback){
        HttpUtils().addHeader("Cookie","")
            .addHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
            .get("https://libaleph.bjut.edu.cn/F/$par?func=short-jump&jump=$jump",true,callback)
    }

    fun getCardInfo(id: String,callback: Callback){
        HttpUtils().addHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
            .get("https://ydapp.bjut.edu.cn/home/openHomePageApp?openid=$id",true,callback)
    }

    fun getBookBorrow(ses: String,callback: Callback){
        HttpUtils().addHeader("Cookie","eai-sess=$ses;")
            .addHeader("x-requested-with","XMLHttpRequest")
            .addHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
            .addHeader("Referer","https://itsapp.bjut.edu.cn/site/library/history")
            .get("https://itsapp.bjut.edu.cn/borrow/wap/default/info?title=",true,callback)
    }

    fun getMailList(ses: String,callback: Callback){
        HttpUtils().addHeader("Cookie","eai-sess=$ses;")
            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
            .post("https://itsapp.bjut.edu.cn/bjutapp/wap/index/fixed-app",callback)
    }

    fun getMailUrl(ses: String,ourl:String,callback: Callback){
        HttpUtils().addHeader("Cookie","eai-sess=$ses;")
            .addHeader("User-Agent","ZhilinEai ZhilinBjutApp/2.5")
            .get(ourl,false,callback)
    }

}