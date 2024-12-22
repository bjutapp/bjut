package com.hlwdy.bjut.ui.schedule

import android.content.Context
import android.graphics.Color
import android.icu.util.Calendar
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Space
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.hlwdy.bjut.BaseFragment
import com.hlwdy.bjut.BjutAPI
import com.hlwdy.bjut.R
import com.hlwdy.bjut.account_session_util
import com.hlwdy.bjut.appLogger
import com.hlwdy.bjut.databinding.FragmentScheduleBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ScheduleCacheManager(private val context: Context) {
    private val cacheDir = File(context.cacheDir, "schedule_cache")
    private val cacheFile = File(cacheDir, "schedule.json")
    private val cacheDuration = TimeUnit.HOURS.toMillis(8) // 缓存时间
    private val maxCacheEntries = 50 // 最大缓存条目数

    init {
        cacheDir.mkdirs()
        if (!cacheFile.exists()) {
            try {
                cacheFile.createNewFile()
                cacheFile.writeText("{}")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun getCachedData(key: String): String? {
        if (!cacheFile.exists() || cacheFile.length() == 0L) return null

        try {
            val jsonObject = JSONObject(cacheFile.readText())
            if (!jsonObject.has(key)) return null

            val entry = jsonObject.getJSONObject(key)
            val timestamp = entry.getLong("timestamp")
            val data = entry.getString("data")

            // 检查缓存是否过期
            if (System.currentTimeMillis() - timestamp > cacheDuration) {
                return null
            }

            return data
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun cacheData(key: String, data: String) {
        try {
            val jsonObject = if (cacheFile.exists() && cacheFile.length() > 0) {
                JSONObject(cacheFile.readText())
            } else {
                JSONObject()
            }

            val entry = JSONObject().apply {
                put("data", data)
                put("timestamp", System.currentTimeMillis())
            }

            jsonObject.put(key, entry)

            // 清理旧的缓存条目
            cleanupOldEntries(jsonObject)

            cacheFile.writeText(jsonObject.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanupOldEntries(jsonObject: JSONObject) {
        if (jsonObject.length() <= maxCacheEntries) return

        val entries = jsonObject.keys().asSequence().toList()
        val sortedEntries = entries.sortedBy { key ->
            jsonObject.getJSONObject(key).getLong("timestamp")
        }

        val entriesToRemove = sortedEntries.take(sortedEntries.size - maxCacheEntries)
        entriesToRemove.forEach { key ->
            jsonObject.remove(key)
        }
    }
}

class ScheduleFragment : BaseFragment() {

    private var _binding: FragmentScheduleBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var viewModel: ScheduleViewModel

    private fun generateColorIntForId(id: String): Int {
        val hue = (id.hashCode() and 0xFFFFFF) % 360
        val lightness = 0.3f + (id.hashCode() and 0xFF) / 255f * 0.15f
        return hslToColorInt(hue, lightness)
    }

    private fun hslToColorInt(hue: Int, lightness: Float): Int {
        val c = (1 - abs(2 * lightness - 1)) * 0.7f
        val x = c * (1 - abs((hue / 60f % 2) - 1))
        val m = lightness - c / 2

        val (r, g, b) = when (hue) {
            in 0..59 -> Triple(c, x, 0f)
            in 60..119 -> Triple(x, c, 0f)
            in 120..179 -> Triple(0f, c, x)
            in 180..239 -> Triple(0f, x, c)
            in 240..299 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val red = ((r + m) * 255).toInt()
        val green = ((g + m) * 255).toInt()
        val blue = ((b + m) * 255).toInt()

        return Color.rgb(red, green, blue)
    }

    fun getDayOfWeek(): Int {
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        // Calendar.SUNDAY = 1, Calendar.MONDAY = 2, ... Calendar.SATURDAY = 7
        // 转换成周一为1，周日为7的格式
        return if (day == Calendar.SUNDAY) 7 else day - 1
    }

    private fun createSchedule(courses: List<Course>) {
        val table = binding.scheduleTable

        val curDay=getDayOfWeek()
        // Add header row
        val headerRow = TableRow(context)
        headerRow.addView(createTextView("时间", isHeader = true))
        viewModel.days.forEach { day ->
            if(day==viewModel.days[curDay-1])headerRow.addView(createTextView(day, isHeader = true, colorInt = Color.GRAY))
            else headerRow.addView(createTextView(day, isHeader = true))
        }
        table.addView(headerRow)

        // Create a 2D array to represent the schedule
        val scheduleGrid = Array(viewModel.timeSlots.size) { Array<Course?>(viewModel.days.size) { null } }

        // Fill the scheduleGrid with courses
        courses.forEach { course ->
            val dayIndex = viewModel.days.indexOf(course.day)
            val startSlot = course.timeCode.substring(0, 2).toInt() - 1
            val endSlot = course.timeCode.substring(course.timeCode.length-2, course.timeCode.length).toInt() - 1
            for (i in startSlot..endSlot) {
                scheduleGrid[i][dayIndex] = course
            }
        }

        // Add time slots and courses
        viewModel.timeSlots.forEachIndexed { rowIndex, timeSlot ->
            val row = TableRow(context)
            row.addView(createTextView(timeSlot))

            viewModel.days.forEachIndexed { dayIndex, _ ->
                val course = scheduleGrid[rowIndex][dayIndex]
                if (course != null) {
                    if(rowIndex>=1&&scheduleGrid[rowIndex-1][dayIndex]==scheduleGrid[rowIndex][dayIndex]){
                        if(rowIndex>=2&&scheduleGrid[rowIndex-2][dayIndex]==scheduleGrid[rowIndex-1][dayIndex])row.addView(createTextView("",
                            isCourse = true, colorInt = generateColorIntForId(course.id),
                            moreInfo = "${course.name} (${course.id})\n地点: ${course.location}\n教师: ${course.teacher}\n节次: ${course.timeCode}\n周次: ${course.weeks}"))
                        else row.addView(createTextView(course.location,
                            isCourse = true, colorInt = generateColorIntForId(course.id),
                            moreInfo = "${course.name} (${course.id})\n地点: ${course.location}\n教师: ${course.teacher}\n节次: ${course.timeCode}\n周次: ${course.weeks}"))
                    }else{
                        row.addView(createTextView(course.name,
                            isCourse = true, colorInt = generateColorIntForId(course.id),
                            moreInfo = "${course.name} (${course.id})\n地点: ${course.location}\n教师: ${course.teacher}\n节次: ${course.timeCode}\n周次: ${course.weeks}"))
                    }
                } else{
                    //row.addView(createTextView(""))
                    row.addView(Space(context).apply {
                        layoutParams = TableRow.LayoutParams(1, TableRow.LayoutParams.MATCH_PARENT, 1f)
                    })
                }
            }
            table.addView(row)
        }
    }

    fun Context.getCustomColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun createTextView(text: String, isHeader: Boolean = false, isCourse: Boolean = false,colorInt: Int=0,moreInfo:String=""): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(context.getCustomColor(R.attr.textColor))
            gravity = Gravity.CENTER
            setPadding(8, 18, 8, 18)
            ellipsize = TextUtils.TruncateAt.END
            if (isHeader) {
                if(colorInt!=0)setBackgroundColor(colorInt)
                else setBackgroundColor(context.getCustomColor(R.attr.header_background))
                setTypeface(null, android.graphics.Typeface.BOLD)
            } else if (isCourse) {
                setBackgroundColor(colorInt)
                setTextColor(Color.WHITE)
            } else {
                setBackgroundColor(context.getCustomColor(R.attr.cell_background))
            }
            if(moreInfo!="")setOnClickListener {
                AlertDialog.Builder(context).apply {
                    setTitle("课程信息")
                    setMessage(moreInfo)
                    setPositiveButton("确定") { dialog, _ ->
                        dialog.dismiss()
                    }
                    create()
                    show()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        val root: View = binding.root
        return root
    }

    fun showToast(message: String) {
        activity?.let { fragmentActivity ->
            Handler(Looper.getMainLooper()).post {
                if (isAdded) {
                    Toast.makeText(fragmentActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun processScheduleData(d:String){
        val res = JSONObject(d)
        val courses = mutableListOf<Course>()
        val ar=res.getJSONObject("d").getJSONArray("classes")
        for (i in 0 until ar.length()) {
            val classObject = ar.getJSONObject(i)
            courses.add(Course(classObject.getString("course_name"), viewModel.mp[classObject.getString("weekday")].toString(),
                classObject.getString("lessons"), classObject.getString("location"),
                classObject.getString("teacher"),classObject.getString("course_id"),classObject.getString("week")))
        }
        activity?.let {
            Handler(Looper.getMainLooper()).post {
                if (isAdded) {
                    hideLoading()
                    binding.weekChoice.visibility=View.VISIBLE
                    createSchedule(courses)
                }
            }
        }
    }

    fun runSchedule(year:String,term:String,week:String){
        showLoading()
        binding.scheduleTable.removeAllViews()
        val cacheKey="$year-$term-$week"
        //showToast(cacheKey)
        val cacheManager=ScheduleCacheManager(requireContext())
        val cachedData = cacheManager.getCachedData(cacheKey)
        if(cachedData!=null){
            processScheduleData(cachedData)
        }else{
            BjutAPI().getSchedule(account_session_util(requireContext()).getUserDetails()[account_session_util.KEY_SESS].toString()
                ,year,term,week,object :
                    Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        showToast("network error")
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val res=response.body?.string().toString()
                        //showToast(res)
                        try {
                            processScheduleData(res)
                            appLogger.e("Info", "Refresh ScheduleData:$cacheKey")
                            cacheManager.cacheData(cacheKey,res)
                        }catch (e: JSONException){
                            showToast("error")
                            appLogger.e("Error", "Try ScheduleInfo error",e)
                        }
                    }
                })
        }
    }

    private var selectedWeek = 1

    private fun createWeeks(weekCount: Int, currentWeek: Int) {
        val dropdown = binding.weekDropdown
        val weeks = (1..weekCount).map { "第${it}周" }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            weeks
        )
        dropdown.post {
            dropdown.setAdapter(adapter)
            dropdown.setText(weeks[currentWeek - 1], false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[ScheduleViewModel::class.java]

        showLoading()
        /*
        val year="2024-2025"
        val term="1"
        val week="11"*/

        selectedWeek = savedInstanceState?.getInt("selected_week", 0) ?: 0

        val cacheManager=ScheduleCacheManager(requireContext())
        val cachedTermData = cacheManager.getCachedData("term-info")

        var year=""
        var term=""

        binding.weekDropdown.apply {
            setOnClickListener {
                showDropDown()
            }
            setOnItemClickListener { _, _, position, _ ->
                val week = position + 1
                viewModel.selectedWeek = week
                runSchedule(year, term, viewModel.selectedWeek.toString())
                //showToast(week.toString())
            }
        }
        if (viewModel.selectedWeek == null) {
            if(cachedTermData!=null){
                val tmp=JSONObject(cachedTermData).getJSONObject("d").getJSONObject("params")
                year=tmp.getString("year")
                term=tmp.getString("term")
                runSchedule(year,term,tmp.getString("week"))
                createWeeks(tmp.getString("countweek").toInt(),tmp.getString("week").toInt())
                binding.weekChoice.hint="$year 第 $term 学期:选择周数"
            }else{
                BjutAPI().getTermWeek(account_session_util(requireContext()).getUserDetails()[account_session_util.KEY_SESS].toString()
                    ,object :
                        Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            showToast("network error")
                        }
                        override fun onResponse(call: Call, response: Response) {
                            val res_text=response.body?.string().toString()
                            try{
                                val res=JSONObject(res_text)
                                //if(res.get("e")==0)
                                val tmp=res.getJSONObject("d").getJSONObject("params")
                                year=tmp.getString("year")
                                term=tmp.getString("term")
                                viewModel.selectedWeek = tmp.getString("week").toInt()
                                activity?.let {
                                    Handler(Looper.getMainLooper()).post {
                                        if (isAdded) {
                                            runSchedule(year,term, tmp.getString("week"))
                                            createWeeks(tmp.getString("countweek").toInt(),tmp.getString("week").toInt())
                                            binding.weekChoice.hint="$year 第 $term 学期:选择周数"
                                        }
                                    }
                                }
                                cacheManager.cacheData("term-info",res_text)
                            }catch (e: JSONException){
                                showToast("error")
                                appLogger.e("Error", "Try TermInfo error",e)
                            }
                        }
                    })
            }
        } else {
            //如果已经选择过周数，使用保存的选择，此时一定有term缓存
            val tmp=JSONObject(cachedTermData).getJSONObject("d").getJSONObject("params")
            year=tmp.getString("year")
            term=tmp.getString("term")
            runSchedule(year,term, viewModel.selectedWeek.toString())
            createWeeks(tmp.getString("countweek").toInt(), viewModel.selectedWeek!!)
            binding.weekChoice.hint="$year 第 $term 学期:选择周数"
        }


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}