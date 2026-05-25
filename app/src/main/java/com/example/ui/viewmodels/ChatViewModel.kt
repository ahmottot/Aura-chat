package com.example.ui.viewmodels

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.*
import com.example.data.database.AppDatabase
import com.example.data.database.ChatMessage
import com.example.data.database.PairedDevice
import com.example.data.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DeviceRepository
    private val context: Context = application.applicationContext

    val chatMessages: StateFlow<List<ChatMessage>>

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _errorFlow.asStateFlow()

    private val _storedApiKey = MutableStateFlow("")
    val storedApiKey: StateFlow<String> = _storedApiKey.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = DeviceRepository(database.deviceDao())

        chatMessages = repository.chatHistory.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Load saved API Key from preferences
        _storedApiKey.value = context.getSharedPreferences("aura_link_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: ""
    }

    fun saveGeminiApiKey(key: String) {
        context.getSharedPreferences("aura_link_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("gemini_api_key", key.trim())
            .apply()
        _storedApiKey.value = key.trim()
    }

    fun getEffectiveApiKey(): String {
        val savedKey = _storedApiKey.value
        if (savedKey.isNotBlank()) return savedKey
        return BuildConfig.GEMINI_API_KEY
    }

    fun sendMessage(text: String, currentDevice: PairedDevice?) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Save user message to database
            repository.addMessage(ChatMessage(sender = "USER", content = text))

            _isGenerating.value = true
            _errorFlow.value = null

            try {
                processGeminiAI(text, currentDevice)
            } catch (e: Exception) {
                _errorFlow.value = "Gemini Hatası: ${e.localizedMessage ?: "Bilinmeyen bir hata oluştu."}"
                repository.addMessage(
                    ChatMessage(
                        sender = "SYSTEM",
                        content = "Hata oluştu: ${e.localizedMessage ?: "Bağlantı kesilmiş olabilir."}"
                    )
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private suspend fun processGeminiAI(userPrompt: String, device: PairedDevice?) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            repository.addMessage(
                ChatMessage(
                    sender = "SYSTEM",
                    content = "Google AI Studio'daki Secrets panelinden veya telefondaki Ayarlar/Giriş ekranından 'GEMINI_API_KEY' nizi tanımlayınız."
                )
            )
            return
        }

        // Build tools representation for Gemini
        val toolsList = listOf(
            Tool(
                functionDeclarations = listOf(
                    FunctionDeclaration(
                        name = "get_screenshot",
                        description = "PC'nin anlık ekran görüntüsünü (screenshot) çeker ve telefonda gösterir. Steam indirmeleri gibi ekrandaki şeyleri izlemek için idealdir."
                    ),
                    FunctionDeclaration(
                        name = "get_system_info",
                        description = "PC'nin işletim sistemi, işlemci (CPU) ve RAM kullanımı ile açık olan uygulamaları listeler."
                    ),
                    FunctionDeclaration(
                        name = "search_files",
                        description = "PC disklerindeki dosyaları kelime araması ile bilgisayar üzerinde tarar.",
                        parameters = FunctionParameters(
                            properties = mapOf(
                                "query" to PropertyDescription("STRING", "PC'de aranacak dosya adı veya kelime")
                            ),
                            required = listOf("query")
                        )
                    ),
                    FunctionDeclaration(
                        name = "execute_pc_action",
                        description = "PC üzerinde otomatik bir işlem (site açma, üye girişi, satın alma simülasyonu, vb.) başlatır.",
                        parameters = FunctionParameters(
                            properties = mapOf(
                                "action_type" to PropertyDescription("STRING", "İşlem türü. Örn: 'open_browser', 'purchase_simulation', 'login_automation'"),
                                "site_url" to PropertyDescription("STRING", "İşlemin yapılacağı hedef web sitesi url'i."),
                                "details" to PropertyDescription("STRING", "Kullanıcının talimatının ayrıntıları (sipariş verilecek ürün, girilecek mail/şifre vb.).")
                            ),
                            required = listOf("action_type", "site_url", "details")
                        )
                    )
                )
            )
        )

        val systemPrompt = """
            Sen 'Aura Link' akıllı PC asistanısın. Kullanıcı Android telefonundan seninle yazışıyor ve bilgisayarına bağlanmış durumda.
            Bilgisayar işletim sistemi hem Windows hem Pardus (Debian Linux) uyumludur.
            Kullanıcı sana bilgisayarla ilgili komutlar verdiğinde uygun fonksiyonları/araçları çağırarak bu işlemleri gerçekleştirebilirsin.
            Seçenekler:
            1. 'get_screenshot': Ekran fotosu istendiğinde, steam indirmesi sorulduğunda veya pc ekranını görmek istediğinde çağır.
            2. 'get_system_info': PC performansı, cpu/ram kullanımı, bilgisayarın açık olup olmaması veya aktif programlar sorulduğunda çağır.
            3. 'search_files': Belirli bir dosya ismi, içerik veya PC üzerinde dosya arayışı yapıldığında çağır.
            4. 'execute_pc_action': Kullanıcı "bana yemek sipariş et", "şuradan yumurta al", "şu siteye git", "şununla giriş yap" dediğinde çağır. Gerekli site url'ini, hesap bilgilerini veya ürün detaylarını parametre olarak ver.
            
            Kullanıcı Türkçe konuşacaktır. Son derece açıklayıcı, yardımsever ve teknolojik bir dilde yanıt ver.
            Ekran görüntüsü çekilirse veya bir otomasyon başlatılırsa, kullanıcıyı sevinçle karşıla ve durumu anlat. Örneğin otomasyonu başlattığında: "Otomasyon işlemini bilgisayarınızda başlattım! Süreci Durum sayfasındaki 'Ekranı Canlı İzle' seçeneğinden izleyebilirsiniz" de.
        """.trimIndent()

        // 1. Compile chat history to feed into Gemini API
        val currentHistory = chatMessages.value
        val contents = mutableListOf<Content>()
        
        // Take last 10 turns to avoid running out of token limits
        val recentHistory = currentHistory.takeLast(10)
        recentHistory.forEach { msg ->
            val role = if (msg.sender == "USER") "user" else "model"
            contents.add(
                Content(
                    role = role,
                    parts = listOf(Part(text = msg.content))
                )
            )
        }

        // Create Request payload with tools
        val request = GeminiRequest(
            contents = contents,
            tools = toolsList,
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(temperature = 0.4f)
        )

        // Generate response using Retrofit Direct Client
        val response = withContext(Dispatchers.IO) {
            GeminiRetrofitClient.service.generateContent("gemini-3.5-flash", apiKey, request)
        }

        val candidate = response.candidates?.firstOrNull()
        val responseContent = candidate?.content
        val parts = responseContent?.parts

        if (parts != null) {
            val hasFunctionCall = parts.any { it.functionCall != null }

            if (hasFunctionCall) {
                // Handle the function call requested by AI!
                val funcPart = parts.first { it.functionCall != null }
                val functionCall = funcPart.functionCall!!
                
                executeFunctionCall(functionCall, device)
            } else {
                // Just regular text response
                val textResponse = parts.firstOrNull { it.text != null }?.text ?: "Boş yanıt"
                repository.addMessage(ChatMessage(sender = "AI", content = textResponse))
            }
        } else {
            repository.addMessage(ChatMessage(sender = "AI", content = "Cevap üretilemedi."))
        }
    }

    private suspend fun executeFunctionCall(call: FunctionCall, device: PairedDevice?) {
        if (device == null) {
            repository.addMessage(
                ChatMessage(
                    sender = "SYSTEM",
                    content = "Hata: Şu anda eşleşmiş ve aktif bir bilgisayar bulunamadı. Lütfen önce eşleştirin."
                )
            )
            return
        }

        val baseUrl = "http://${device.ipAddress}:${device.port}"

        when (call.name) {
            "get_screenshot" -> {
                repository.addMessage(ChatMessage(sender = "SYSTEM", content = "Ekran görüntüsü talep ediliyor..."))
                val url = "$baseUrl/screenshot"
                try {
                    // Fetch screenshot as binary ResponseBody
                    val responseBody = withContext(Dispatchers.IO) {
                        CompanionClient.service.downloadFile(url, "", device.pinCode)
                    }
                    val bitmap = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeStream(responseBody.byteStream())
                    }
                    if (bitmap != null) {
                        // Save bitmap to phone local files
                        val fileName = "screenshot_${System.currentTimeMillis()}.jpg"
                        val file = File(context.filesDir, fileName)
                        val fos = withContext(Dispatchers.IO) { FileOutputStream(file) }
                        withContext(Dispatchers.IO) {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                            fos.flush()
                            fos.close()
                        }
                        
                        // Add screenshot to Chat history
                        repository.addMessage(
                            ChatMessage(
                                sender = "AI",
                                content = "Bilgisayarın anlık ekran görüntüsünü çektim ve kaydettim:",
                                imageUrl = file.absolutePath
                            )
                        )
                    } else {
                        repository.addMessage(ChatMessage(sender = "AI", content = "Ekran görüntüsü alındı fakat dosya çözümlenemedi."))
                    }
                } catch (e: Exception) {
                    repository.addMessage(
                        ChatMessage(
                            sender = "SYSTEM",
                            content = "Ekran görüntüsü alınamadı: ${e.localizedMessage ?: "Bağlantı hatası. Sunucu açık mı?"}"
                        )
                    )
                }
            }

            "get_system_info" -> {
                repository.addMessage(ChatMessage(sender = "SYSTEM", content = "Performans bilgileri okunuyor..."))
                val url = "$baseUrl/info"
                try {
                    val info = withContext(Dispatchers.IO) {
                        CompanionClient.service.getSystemInfo(url, device.pinCode)
                    }
                    val detailsText = """
                        💻 **Bilgisayar Bilgileri:**
                        • **İşletim Sistemi**: ${info.os}
                        • **Hostname**: ${info.hostname}
                        • **CPU Kullanımı**: %${String.format("%.1f", info.cpu_usage)}
                        • **RAM Kullanımı**: %${String.format("%.1f", info.memory_usage)}
                        • **Boş Disk**: ${info.disk_free}
                        • **Aktif Uygulama**: ${info.active_app ?: "Masaüstü"}
                    """.trimIndent()
                    repository.addMessage(ChatMessage(sender = "AI", content = detailsText))
                } catch (e: Exception) {
                    repository.addMessage(
                        ChatMessage(
                            sender = "SYSTEM",
                            content = "Sistem bilgileri alınamadı: ${e.localizedMessage ?: "Bağlantı hatası."}"
                        )
                    )
                }
            }

            "search_files" -> {
                val query = call.args?.get("query") ?: ""
                repository.addMessage(ChatMessage(sender = "SYSTEM", content = "'$query' dosyası bilgisayarda aranıyor..."))
                val url = "$baseUrl/search"
                try {
                    val response = withContext(Dispatchers.IO) {
                        CompanionClient.service.searchFiles(url, query, device.pinCode)
                    }
                    if (response.status == "success" && response.matches.isNotEmpty()) {
                        val buildText = StringBuilder("🔍 **Bulunan Eşleşen Dosyalar (${response.matches.size} adet):**\n")
                        response.matches.take(5).forEach { file ->
                            buildText.append("• `${file.name}`\n  📁 Konum: `${file.path}` (${formatFileSize(file.size)})\n")
                        }
                        if (response.matches.size > 5) {
                            buildText.append("...ve ${response.matches.size - 5} dosya daha.")
                        }
                        
                        // Save to chat
                        repository.addMessage(
                            ChatMessage(
                                sender = "AI",
                                content = buildText.toString(),
                                fileName = response.matches.first().name,
                                filePath = response.matches.first().path
                            )
                        )
                    } else {
                        repository.addMessage(ChatMessage(sender = "AI", content = "Aradığınız kriterlere uygun herhangi bir dosya bulunamadı."))
                    }
                } catch (e: Exception) {
                    repository.addMessage(
                        ChatMessage(
                            sender = "SYSTEM",
                            content = "Dosya araması yapılamadı: ${e.localizedMessage ?: "Bağlantı hatası."}"
                        )
                    )
                }
            }

            "execute_pc_action" -> {
                val actionType = call.args?.get("action_type") ?: "open_browser"
                val siteUrl = call.args?.get("site_url") ?: ""
                val details = call.args?.get("details") ?: ""

                repository.addMessage(ChatMessage(sender = "SYSTEM", content = "Otomasyon görevi bilgisayara gönderiliyor..."))
                val url = "$baseUrl/execute"
                try {
                    val response = withContext(Dispatchers.IO) {
                        CompanionClient.service.executeAction(url, actionType, siteUrl, details, device.pinCode)
                    }
                    if (response.status == "success") {
                        repository.addMessage(
                            ChatMessage(
                                sender = "AI",
                                content = "Otomasyon bilgisayarınızda başlatıldı!\n💻 **İşlem**: $actionType\n🌐 **Site**: $siteUrl\n📝 **Detaylar**: $details\n\nSüreci anlık takip etmek için Durum sayfasından 'Ekranı Canlı İzle' tuşuna basabilirsiniz!"
                            )
                        )
                    } else {
                        repository.addMessage(ChatMessage(sender = "AI", content = "Otomasyon bilgisayar tarafından reddedildi: ${response.message}"))
                    }
                } catch (e: Exception) {
                    repository.addMessage(
                        ChatMessage(
                            sender = "SYSTEM",
                            content = "Otomasyon gönderilemedi: ${e.localizedMessage ?: "Bağlantı hatası. Masaüstü sunucusu açık mı?"}"
                        )
                    )
                }
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.toDouble())).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.toDouble(), digitGroups.toDouble()), units[digitGroups])
    }

    fun downloadFileToPhone(device: PairedDevice, pcFilePath: String, fileName: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            repository.addMessage(ChatMessage(sender = "SYSTEM", content = "'$fileName' indiriliyor..."))
            val url = "http://${device.ipAddress}:${device.port}/download"
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    CompanionClient.service.downloadFile(url, pcFilePath, device.pinCode)
                }
                
                val downloadedPath = withContext(Dispatchers.IO) {
                    saveFileToDownloads(responseBody, fileName)
                }
                
                if (downloadedPath != null) {
                    repository.addMessage(
                        ChatMessage(
                            sender = "SYSTEM",
                            content = "Başarılı! '$fileName' telefonun Downloads (İndirilenler) klasörüne kaydedildi.\nDosya Yolu: $downloadedPath"
                        )
                    )
                    onComplete(downloadedPath)
                } else {
                    repository.addMessage(ChatMessage(sender = "SYSTEM", content = "Hata: Dosya telefona kaydedilemedi."))
                }
            } catch (e: Exception) {
                repository.addMessage(
                    ChatMessage(
                        sender = "SYSTEM",
                        content = "Dosya indirme hatası: ${e.localizedMessage ?: "Bağlantı koptu."}"
                    )
                )
            }
        }
    }

    private fun saveFileToDownloads(body: ResponseBody, fileName: String): String? {
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            
            // To prevent overwrites
            var destinationFile = File(downloadDir, fileName)
            var count = 1
            while (destinationFile.exists()) {
                val extension = fileName.substringAfterLast(".", "")
                val base = fileName.substringBeforeLast(".")
                val name = if (extension.isNotEmpty()) "$base ($count).$extension" else "$base ($count)"
                destinationFile = File(downloadDir, name)
                count++
            }

            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(destinationFile)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
