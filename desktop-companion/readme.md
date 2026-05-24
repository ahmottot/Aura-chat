# AuraLink Masaüstü Sunucu Kurulum Kılavuzu (Windows & Pardus)

Bu dizin, telefonunuzdaki **AuraLink** Android uygulamasının bilgisayarınızla (Windows veya Pardus) güvenli bağlantı kurup konuşmasını, ekran resimleri fırlatmasını ve dosyaları telefona indirmesini sağlayan hafif bilgisayar sunucu programını barındırır.

---

## 🛠️ Kurulum Seçenekleri

### Seçenek 1: Pardus (Debian Linux) Üzerinde Kurulum

Debian tabanlı yerli işletim sistemimiz **Pardus** üzerinde çalıştırmak için terminali açıp aşağıdaki adımları uygulayın:

1. Gerekli araçları ve Python kütüphanelerini kurun:
   ```bash
   sudo apt update
   sudo apt install python3-pip python3-pil -y
   ```

2. Sunucuyu çalıştırın:
   ```bash
   python3 pair_server.py
   ```

---

### Seçenek 2: Windows Üzerinde Kurulum

1. Bilgisayarınızda Python kurulu olduğundan emin olun (Yoksa [python.org](https://www.python.org/) üzerinden indirip yükleyebilirsiniz. Kurulum esnasında **"Add Python to PATH"** seçeneğini mutlaka işaretleyin).

2. Terminali (PowerShell veya Komut İstemi) açıp masaüstü klasörüne gelin ve bağımlılıkları yükleyin:
   ```cmd
   pip install pillow
   ```

3. Sunucuyu çalıştırın:
   ```cmd
   python pair_server.py
   ```

---

## 🔗 Uygulamayı Eşleştirme

1. Sunucuyu başlattığınızda konsol ekranında size özel bir **GÜVENLİK PIN KODU** (Örn: `124859`) ve bilgisayarın yerel IP adresi oluşturulacaktır.
2. Ayrıca bilgisayarınızda `http://localhost:5555` adresine girdiğinizde karşınıza çıkan **Pairing QR Kodunu** telefondaki AuraLink uygulamasının **Eşleştir (Eşle)** ekranına girip QR kodunu kameranızla tarayarak veya IP ve şifreyi yazarak tek seferlik eşleştirmeyi güvenle tamamlayabilirsiniz.

## 🤖 Yapay Zeka Komut Örnekleri

Eşleştirme kurulduktan sonra, telefondaki **Aura Chat** ekranında Türkçe olarak şu komutları yazıp deneyebilirsiniz:
* *"Steam indirmemin durumunu görmek için ekran görüntüsü at"* (Sizin yerinize anlık ekran resmini çeker ve sohbete gömer).
* *"Bilgisayardaki ders sunumunu içeren dosyayı veya raporu bul"* (PC diskinizde o dosyaları tarar ve telefona indirme butonu eşliğinde bulur).
* *"Bilgisayarımın performans durumunu analiz et"* (CPU, RAM, işletim sistemi ve disk alanlarını size raporlar).
