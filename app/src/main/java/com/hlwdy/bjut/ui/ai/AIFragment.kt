package com.hlwdy.bjut.ui.ai

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hlwdy.bjut.BaseFragment
import com.hlwdy.bjut.BjutAPI
import com.hlwdy.bjut.R
import com.hlwdy.bjut.appLogger
import com.hlwdy.bjut.databinding.FragmentAiBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AIFragment : BaseFragment() {
    private lateinit var binding: FragmentAiBinding
    private lateinit var adapter: ChatAdapter
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        getConversationID()

        setupRecyclerView()
        setupMessageInput()
        observeMessages()
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

    fun getConversationID(){
        if(viewModel.conversationID!="")return
        showLoading()
        BjutAPI().getAIConversationId("","",object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showToast("network error")
            }
            override fun onResponse(call: Call, response: Response) {
                var res=JSONObject(response.body?.string().toString())
                BjutAPI().getAIConversationId(res.getString("visitorId"),res.getString("visitorVc"),object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        showToast("network error")
                    }
                    override fun onResponse(call: Call, response: Response) {
                        res=JSONObject(response.body?.string().toString())
                        if(res.getString("code")=="1"){
                            viewModel.conversationID=res.getJSONObject("cvsInfo").getString("conversationId")
                            //showToast(viewModel.conversationID)
                            appLogger.e("Info", "New ConversationID:"+viewModel.conversationID)
                            activity?.let {
                                Handler(Looper.getMainLooper()).post {
                                    if (isAdded) {
                                        viewModel.onResume()
                                        hideLoading()
                                    }
                                }
                            }
                        }else{
                            showToast("error")
                            appLogger.e("Error", "Try AIRefresh "+res.getString("visitorId")+" error")
                        }
                    }
                })
            }
        })
    }

    private fun setupRecyclerView() {
        binding.chatRecyclerView.itemAnimator = null
        adapter = ChatAdapter()
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = this@AIFragment.adapter
        }
    }

    private fun setupMessageInput() {
        binding.sendButton.setOnClickListener {
            val message = binding.messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                binding.messageInput.text.clear()
            }
        }

        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                binding.sendButton.performClick()
                true
            } else {
                false
            }
        }

        binding.clearButton.setOnClickListener {
            viewModel.onDestroy()
            viewModel.conversationID=""
            viewModel._messages.value = mutableListOf()
            getConversationID()
        }
    }

    private fun observeMessages() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages.toList()) {
                //当新消息添加时，滚动到底部
                if (messages.isNotEmpty()) {
                    binding.chatRecyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun sendMessage(content: String) {
        viewModel.sendMessage(content)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}

data class ChatMessage(
    val content: String,
    val isSentByMe: Boolean,
    val isTyping: Boolean = false,    // 表示是否是"正在输入"状态
    val isStreaming: Boolean = false  // 表示是否是正在流式传输的消息
)

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageCard: MaterialCardView = itemView.findViewById(R.id.messageCard)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        fun bind(message: ChatMessage) {
            messageText.text = message.content

            val context = itemView.context
            // 获取主题中定义的颜色
            val typedValue = TypedValue()
            val theme = context.theme

            if (message.isSentByMe) {
                // 发送的消息
                theme.resolveAttribute(R.attr.sentMessageBackgroundColor, typedValue, true)
                messageCard.setCardBackgroundColor(typedValue.data)

                theme.resolveAttribute(R.attr.sentMessageTextColor, typedValue, true)
                messageText.setTextColor(typedValue.data)

                // 设置约束
                val constraintSet = ConstraintSet()
                val containerLayout = itemView as ConstraintLayout
                constraintSet.clone(containerLayout)
                constraintSet.clear(messageCard.id, ConstraintSet.START)
                constraintSet.connect(messageCard.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                constraintSet.applyTo(containerLayout)
            } else {
                // 接收的消息
                theme.resolveAttribute(R.attr.receivedMessageBackgroundColor, typedValue, true)
                messageCard.setCardBackgroundColor(typedValue.data)

                theme.resolveAttribute(R.attr.receivedMessageTextColor, typedValue, true)
                messageText.setTextColor(typedValue.data)

                // 设置约束
                val constraintSet = ConstraintSet()
                val containerLayout = itemView as ConstraintLayout
                constraintSet.clone(containerLayout)
                constraintSet.clear(messageCard.id, ConstraintSet.END)
                constraintSet.connect(messageCard.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.applyTo(containerLayout)
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}

class WebSocketClient(
    private val uri: String,
    private val keepaliveInterval: Long = 30 // 心跳间隔（秒）
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket需要禁用超时
        .build()

    var isConnected = false
    private var keepaliveJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 用于接收消息的 Channel
    private val messageChannel = kotlinx.coroutines.channels.Channel<String>()

    suspend fun connect(): Boolean = suspendCoroutine { continuation ->
        try {
            val request = Request.Builder()
                .url(uri)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    isConnected = true
                    println("已连接到服务器: $uri")
                    // 启动心跳任务
                    startKeepalive()
                    continuation.resume(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    coroutineScope.launch {
                        messageChannel.send(text)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    isConnected = false
                    println("WebSocket失败: ${t.message}")
                    continuation.resumeWithException(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    isConnected = false
                    println("WebSocket已关闭")
                }
            })
        } catch (e: Exception) {
            println("连接失败: ${e.message}")
            continuation.resume(false)
        }
    }

    private fun startKeepalive() {
        keepaliveJob = coroutineScope.launch {
            while (isConnected) {
                try {
                    val keepaliveMessage = JSONObject().apply {
                        put("type", "KEEPALIVE")
                    }
                    sendMessage(keepaliveMessage)
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date())
                    println("[$timestamp] 发送心跳包")
                    delay(keepaliveInterval * 1000)
                } catch (e: Exception) {
                    println("发送心跳包失败: ${e.message}")
                    break
                }
            }
        }
    }

    suspend fun sendMessage(data: JSONObject) {
        try {
            val message = data.toString()
            webSocket?.send(message)
            if (data.optString("type") != "KEEPALIVE") {
                println("已发送数据: $message")
            }
        } catch (e: Exception) {
            println("发送失败: ${e.message}")
            isConnected = false
            throw e
        }
    }

    suspend fun receiveMessage(): JSONObject? {
        return try {
            val message = messageChannel.receive()
            println(message)
            val data = JSONObject(message)
            if (data.optString("type") != "KEEPALIVE") {
                println("收到数据")
            }
            data
        } catch (e: Exception) {
            println("接收失败: ${e.message}")
            isConnected = false
            null
        }
    }

    suspend fun close() {
        isConnected = false
        keepaliveJob?.cancelAndJoin()
        webSocket?.close(1000, "Normal closure")
        messageChannel.close()
        println("连接已关闭")
    }
}

class ChatViewModel : ViewModel() {
    val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> = _messages

    private var webSocketClient: WebSocketClient? = null

    // 用于存储当前正在构建的消息
    private var currentStreamingMessage: StringBuilder? = null
    private var currentMessageId: String? = null

    private var listenerJob: Job? = null  // 添加这行来跟踪监听器的协程
    private var isActive = true  // 添加状态标志

    private var connectionJob: Job? = null

    var conversationID=""

    init {
        _messages.value = mutableListOf()
        /*
        connectionJob = viewModelScope.launch {
            connectWebSocket()
        }*/
    }

    private suspend fun connectWebSocket() {
        //先断开现有连接
        webSocketClient?.close()
        webSocketClient = null
        listenerJob?.cancel()

        isActive = true
        webSocketClient = WebSocketClient(
            "wss://robot.chaoxing.com/v1/ws/chat/30211/visitor?unitId=30211&robotId=5ae979d078284514b60512512b21e0b9&channel=WEB&conversationId=$conversationID&lang=zh-CN",
            keepaliveInterval = 30
        )

        try {
            webSocketClient?.connect()?.let { connected ->
                if (connected) {
                    startMessageListener()
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "WebSocket连接失败", e)
        }
    }

    fun onResume() {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            connectionJob?.cancelAndJoin()
            if ((webSocketClient == null || webSocketClient?.isConnected != true) && conversationID!="") {
                connectWebSocket()
            }
        }
    }

    private fun startMessageListener() {
        listenerJob = viewModelScope.launch {
            webSocketClient?.let { client ->
                while (isActive) {  // 使用状态标志控制循环
                    try {
                        val response = client.receiveMessage()
                        if (!isActive) break  // 检查是否应该继续
                        response?.let { handleServerResponse(it) }
                    } catch (e: Exception) {
                        if (isActive) {  // 只在活动状态下记录错误
                            Log.e("ChatViewModel", "消息接收失败", e)
                        }
                        break
                    }
                }
            }
        }
    }

    fun sendMessage(content: String) {
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        currentMessages.add(ChatMessage(content = content, isSentByMe = true))
        _messages.value = currentMessages

        viewModelScope.launch {
            try {
                val message = JSONObject().apply {
                    put("msgTimeId", System.currentTimeMillis())
                    put("time", System.currentTimeMillis())
                    put("direction", "IN")
                    put("messageType", "TEXT")
                    put("communicateType", "DATA")
                    put("lang", "zh-CN")
                    put("msg", JSONObject().apply {
                        put("channel", "WEB")
                        put("question", content)
                        put("questionType", "TEXT")
                    })
                    put("robot", JSONObject().apply {
                        put("type", "")
                        put("scene", -1)
                        put("subject", "")
                        put("spage", 1)
                    })
                    put("dxNumber", "")
                    put("d", "")
                    put("ackStatus", "SENDING")
                }

                webSocketClient?.sendMessage(message)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "消息发送失败", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            webSocketClient?.close()
        }
    }

    private fun handleServerResponse(response: JSONObject) {
        if (response.optString("type") == "KEEPALIVE") return

        try {
            val messageId = response.optString("messageId")
            val answer = response.optString("answer")
            val answerJson = JSONObject(answer)

            //println(answer)

            when {
                answerJson.optString("message") == "正在生成中" -> {
                    showTypingIndicator()
                }
                answerJson.optString("flag") == "stop" -> {
                    finalizeCurrentMessage()
                    hideTypingIndicator()
                }
                answerJson.has("responseText") -> {
                    val responseText = answerJson.optString("responseText")
                    if (messageId != currentMessageId) {
                        currentMessageId = messageId
                        currentStreamingMessage = StringBuilder()
                    }
                    currentStreamingMessage?.append(responseText)
                    updateCurrentMessage()
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "响应处理失败", e)
        }
    }

    private fun showTypingIndicator() {
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        if (currentMessages.lastOrNull()?.isTyping != true) {
            currentMessages.add(ChatMessage(content = "", isSentByMe = false, isTyping = true,isStreaming = true))
            _messages.postValue(currentMessages)
        }
    }

    private fun hideTypingIndicator() {
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        if (currentMessages.lastOrNull()?.isTyping == true) {
            currentMessages.removeAt(currentMessages.size - 1)
            _messages.postValue(currentMessages)
        }
    }

    private fun updateCurrentMessage() {
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        val streamingContent = currentStreamingMessage?.toString() ?: return

        val lastMessage = currentMessages.lastOrNull()
        if (lastMessage?.isStreaming == true) {
            currentMessages[currentMessages.size - 1] = ChatMessage(
                content = streamingContent,
                isSentByMe = false,
                isStreaming = true
            )
        } else {
            currentMessages.add(ChatMessage(
                content = streamingContent,
                isSentByMe = false,
                isStreaming = true
            ))
        }

        _messages.postValue(currentMessages)
    }

    private fun finalizeCurrentMessage() {
        val currentMessages = _messages.value?.toMutableList() ?: mutableListOf()
        val streamingContent = currentStreamingMessage?.toString() ?: return

        if (currentMessages.lastOrNull()?.isStreaming == true) {
            currentMessages[currentMessages.size - 1] = ChatMessage(
                content = streamingContent,
                isSentByMe = false,
                isStreaming = false
            )
        }

        _messages.postValue(currentMessages)
        currentStreamingMessage = null
        currentMessageId = null
    }

    fun onDestroy() {
        viewModelScope.launch {
            try {
                isActive = false  // 首先设置状态标志
                listenerJob?.cancelAndJoin()  // 取消并等待监听器协程完成
                webSocketClient?.close()  // 关闭WebSocket连接
                webSocketClient = null
            } catch (e: Exception) {
                Log.e("ChatViewModel", "清理资源失败", e)
            }
        }
    }
}