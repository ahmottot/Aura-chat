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
                        description = "PC üzerinde gelişmiş sistem ve otomasyon işlerini (terminal komut çalıştırma, otomatik web arama, URL'den dosya/program indirme, dinamik Python betiği yürütme, ses ayarları, medya kumandası, klavyeden yazı yazma, uygulama açma, vb.) yürütür.",
                        parameters = FunctionParameters(
                            properties = mapOf(
                                "action_type" to PropertyDescription("STRING", "İşlem türü seçimi. Şu değerlerden birisi veya daha fazlası olmalıdır:\n" +
                                        "- 'command': Terminal / Shell Komut satırı (CMD/PowerShell veya Linux Bash) çalıştırma.\n" +
                                        "- 'google_search': Varsayılan tarayıcıda doğrudan Google araması yapar.\n" +
                                        "- 'download_url': Herhangi bir HTTP/HTTPS linkinden (URL) PC'ye doğrudan dosya indirir.\n" +
                                        "- 'python_script': Dinamik Python kodu çalıştırır (Kullanıcının sınırı olmayan karmaşık her türlü otomasyonu, otomatik satın alma, hesap giriş, tıklama, yükleme vb. işleri için Python kodu yazar ve çalıştırır).\n" +
                                        "- 'volume': Bilgisayarın ses düzeyini açma, kısma ve sessiz modu.\n" +
                                        "- 'media': Medya şarkı oynatma, durdurma, sonraki, önceki kumandası.\n" +
                                        "- 'power': Bilgisayar gücü kontrolü (kapat, yeniden başlat, kilitle, uyku).\n" +
                                        "- 'keyboard': Bilgisayar ekranında klavye harflerini tipleyerek yazma.\n" +
                                        "- 'open_app': Masaüstü uygulaması çalıştırma.\n" +
                                        "- 'open_browser': Web tarayıcısında adres (URL) açma."),
                                "site_url" to PropertyDescription("STRING", "Açılacak veya işlem yapılacak hedef web sitesi url adresi (örn: https://google.com) ya da indirme linki."),
                                "details" to PropertyDescription("STRING", "Komut yürütme ayrıntısı:\n" +
                                        "- 'python_script' ise: PC'de python ile arka planda çalıştırılacak tam ve kendi kendine yeten python kodu (örn: urllib kullanarak webden dosya çekme, selenium/json işlemleri vb.).\n" +
                                        "- 'google_search' ise: Google'da aranacak kelimeler.\n" +
                                        "- 'command' ise: Çalıştırılacak terminal komutu (örn: 'dir', 'ping 8.8.8.8', 'ipconfig', 'ls', 'systeminfo' vb.).\n" +
                                        "- 'volume' ise: 'up' (ses aç), 'down' (ses kıs), 'mute' (ses kapat/aç).\n" +
                                        "- 'media' ise: 'play' (oynat/duraklat), 'next' (sonraki), 'prev' (önceki).\n" +
                                        "- 'power' ise: 'lock' (ekran kilitle), 'sleep' (uyku), 'shutdown' (kapat), 'restart' (yeniden başlat).\n" +
                                        "- 'keyboard' veya 'type' ise: Ekrana yazdırılacak kelimeler veya harfler.\n" +
                                        "- 'open_app' ise: Açılacak uygulama dosyası/adı (örn: 'notepad', 'calc', 'chrome', 'steam').\n" +
                                        "- 'open_browser' ise: Yapılacak işlemin adı veya ek teferruat.")
                            ),
                            required = listOf("action_type")
                        )
                    )
                )
            )
        )

        val systemPrompt = """
            Sen 'Aura Link' akıllı PC asistanısın. Kullanıcı Android telefonundan seninle yazışıyor ve bilgisayarına sorunsuz bağlanmış durumda.
            Bilgisayar işletim sistemi hem Windows hem Pardus (Debian Linux) sistemleri ile %100 uyumludur.
            Kullanıcı sana bilgisayarla ilgili HER TÜRLÜ SINIRSIZ isteği verebilir: google araması, siteden ürün bulup satın alma simülasyonu, bir siteye girip hesap açma, google'da aranan en uygun şeyi bulup PC'ye indirme/yükleme, müzik kontrolü, klavye-mouse simülasyonu vb. Sen bu istekleri yerine getirmek için uygun fonksiyonları ve araçları tam yetkiyle çalıştırırsın.
            
            Yeteneklerin ve Seçeneklerin:
            1. 'get_screenshot': Ekran fotosu istendiğinde, PC ekranını görmek istediğinde veya aktif işleri kontrol ederken çağır.
            2. 'get_system_info': PC performansı, cpu/ram kullanımı, bilgisayarın açık durumu veya aktif açık olan pencere sorulduğunda çağır.
            3. 'search_files': Belirli bir dosya ismi, içerik veya PC üzerinde dosya arayışı yapıldığında çağır.
            4. 'execute_pc_action': PC'deki diğer tüm işler için en yetkin ve esnek kontrol panelindir:
               - Kullanıcı Google araması yapmak istediğinde -> action_type = 'google_search', details = 'aranacak kelimeler'
               - Kullanıcı webden doğrudan dosya, müzik, program ve resim indirmek/yüklemek istediğinde -> action_type = 'download_url', site_url = 'dosya indirme linki'
               - Kullanıcı "hesabıma giriş yap", "ürünü bul satın al", "şuradan şunu otomatik yükle/otomatik bul" gibi kompleks, özel, aşamalı otomasyonlar istediğinde -> action_type = 'python_script', details = 'Betik Kodu (Kompleks işlemleri web istekleri veya python otomasyon kütüphaneleriyle yapacak temiz, çalışan bir python kodu yazarsın)'
               - Kullanıcı terminal/CMD/komut çalıştırmak istediğinde -> action_type = 'command', details = 'komut içeriği'
               - Kullanıcı ses seviyesini değiştirmek istediğinde -> action_type = 'volume', details = 'up' / 'down' / 'mute'
               - Kullanıcı müzik/medya değiştirmek istediğinde -> action_type = 'media', details = 'play' / 'next' / 'prev'
               - Kullanıcı gücü değiştirmek istediğinde (bilgisayarı kapat, kilitle, uykuya al vb.) -> action_type = 'power', details = 'lock' / 'sleep' / 'shutdown' / 'restart'
               - Kullanıcı ekrana klavyeyle yazı yazdırmak istediğinde -> action_type = 'keyboard', details = 'yazılacak metin'
               - Kullanıcı uygulama açmak istediğinde -> action_type = 'open_app', details = 'uygulama adı (notepad, calc, chrome, vb.)'
               - Kullanıcı web sitesi açmak istediğinde -> action_type = 'open_browser', site_url = 'site linki'

            Kullanıcı son derece özgürce isteklerde bulunacaktır. Sınır yoktur! Sen bu istekleri doğrudan 'execute_pc_action' ile yerine getirirsin.
            Eğer kullanıcı karmaşık bir siteye giriş yapmak, ürün listesini otomatik buldurmak, belirli bir hesabı satın alıp indirtmek gibi şeyler isterse, bunu çözmek için 'python_script' eylemi içerisinde çalışan dinamik PYTHON kodları yazar ve gönderirsin. Kodlar standart kütüphaneleri (urllib, subprocess, os, sys vb.) kullanmalı veya gerekirse 'pip install' komutlarını terminalden tetikleyebileceğini bilmelidir.
            Bilgisayardan aldığın sonuçları (örneğin komut çıktısını) son derece açıklayıcı, yardımsever, havalı ve teknolojik bir dilde kullanıcıya özetle. Yanıtlarında Türkçe dil kurallarına uy ve samimi ama profesyonel bir asistan dili kullan.
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

        // Use a robust fallback mechanism for Gemini models to prevent any 401/404/400 errors from un-released local/pre-release models
        val modelCandidates = listOf("gemini-1.5-flash", "gemini-2.5-flash", "gemini-2.0-flash", "gemini-3.5-flash")
        var response: GeminiResponse? = null
        var lastException: Exception? = null

        for (modelName in modelCandidates) {
            try {
                response = withContext(Dispatchers.IO) {
                    GeminiRetrofitClient.service.generateContent(modelName, apiKey, request)
                }
                if (response != null) break
            } catch (e: Exception) {
                lastException = e
            }
        }

        if (response == null) {
            throw lastException ?: Exception("Hiçbir model yanıt vermedi. Lütfen API anahtarınızın geçerli olduğunu doğrulayınız.")
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

        when (call.name) {
            "get_screenshot" -> {
                repository.addMessage(ChatMessage(sender = "SYSTEM", content = "Ekran görüntüsü talep ediliyor..."))
                val url = device.buildUrl("/screenshot")
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
                val url = device.buildUrl("/info")
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
                val url = device.buildUrl("/search")
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

                repository.addMessage(ChatMessage(sender = "SYSTEM", content = "Komut bilgisayara gönderiliyor..."))
                val url = device.buildUrl("/execute")
                try {
                    val response = withContext(Dispatchers.IO) {
                        CompanionClient.service.executeAction(url, actionType, siteUrl, details, device.pinCode)
                    }
                    if (response.status == "success") {
                        val replyText = response.message ?: "İşlem başarıyla tamamlandı!"
                        repository.addMessage(
                            ChatMessage(
                                sender = "AI",
                                content = "Bilgisayarda komut başarıyla gerçekleştirildi!\n" +
                                        "💻 **İşlem**: $actionType \n" +
                                        "🖥️ **Bilgisayar Yanıtı**:\n$replyText"
                            )
                        )
                    } else {
                        repository.addMessage(ChatMessage(sender = "AI", content = "İşlem bilgisayar tarafından reddedildi: ${response.message}"))
                    }
                } catch (e: Exception) {
                    repository.addMessage(
                        ChatMessage(
                            sender = "SYSTEM",
                            content = "İşlem gönderilemedi: ${e.localizedMessage ?: "Bağlantı hatası. Masaüstü sunucusu açık mı?"}"
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
            val url = device.buildUrl("/download")
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
