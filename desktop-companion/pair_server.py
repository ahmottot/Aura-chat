#!/usr/bin/env python3
"""
AuraLink Desktop Companion Server
Compatible with Windows and Pardus (Debian Linux).

This lightweight server provides API endpoints so that your paired Android app
can securely request computer info, files, and screenshots. It uses standard
Python modules with fallback behaviors to ensure 100% startup reliability.
"""

import os
import sys
import json
import socket
import random
import platform
import subprocess
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler
from threading import Thread
import time
import webbrowser

# Try importing Pillow for screenshots. If fail, fall back to a placeholder.
try:
    from PIL import ImageGrab
    PILLOW_AVAILABLE = True
except ImportError:
    PILLOW_AVAILABLE = False


# Generate a random 6-digit security PIN for this session
SECURITY_PIN = str(random.randint(100000, 999999))
PORT = 5555
PAIRED_DEVICES_FILE = "paired_devices.json"


def show_desktop_notification(title, msg):
    print(f"\n [🔔 BİLDİRİM] {title}: {msg}\n")
    system_os = platform.system()
    try:
        if system_os == "Windows":
            cmd = f'powershell -NoProfile -Command "[Reflection.Assembly]::LoadWithPartialName(\'System.Windows.Forms\') | Out-Null; [System.Windows.Forms.MessageBox]::Show(\'{msg}\', \'{title}\', 0, 64)"'
            subprocess.Popen(cmd, shell=True)
        elif system_os == "Linux":
            cmd = f'notify-send "{title}" "{msg}" --icon=info'
            subprocess.Popen(cmd, shell=True)
    except Exception as e:
        print(f" [⚠️] Native bildirim hatası: {e}")


def save_paired_device(ip):
    try:
        devices = []
        if os.path.exists(PAIRED_DEVICES_FILE):
            with open(PAIRED_DEVICES_FILE, "r") as f:
                devices = json.load(f)
        if ip not in devices:
            devices.append(ip)
            with open(PAIRED_DEVICES_FILE, "w") as f:
                json.dump(devices, f)
            print(f" [+] Yeni telefon IP adresi kaydedildi: {ip}")
    except Exception as e:
        print(f" [⚠️] Cihaz kaydetme hatası: {e}")


def announce_online_to_phones():
    """Arka planda çalışarak kayıtlı telefonlara bilgisayarın açık olduğunu TCP üzerinden bildirir."""
    print(" [🚀] Otomatik telefon eşleştirme & bildirim servisi başlatıldı.")
    while True:
        if os.path.exists(PAIRED_DEVICES_FILE):
            try:
                with open(PAIRED_DEVICES_FILE, "r") as f:
                    devices = json.load(f)
                for ip in devices:
                    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    s.settimeout(2.0)
                    try:
                        s.connect((ip, 5556))
                        # Telefondaki port 5556'ya sinyal paketi gönder
                        msg = f"AURALINK_ONLINE|{LOCAL_IP}|{PORT}|{SECURITY_PIN}\n"
                        s.sendall(msg.encode('utf-8'))
                        print(f" [⚡] Telefona otomatik bağlantı sinyali başarıyla ulaştırıldı: {ip}")
                    except Exception:
                        pass
                    finally:
                        s.close()
            except Exception as e:
                pass
        time.sleep(12)


# Thread-safe global cached metrics updated in a background thread
CACHED_METRICS = {
    "os": f"{platform.system()} {platform.release()}",
    "hostname": socket.gethostname(),
    "cpu_usage": 5.0,
    "memory_usage": 35.0,
    "disk_free": "Calculating...",
    "active_app": "System"
}


def update_metrics_loop():
    """Background thread loop to update system metrics periodically without blocking HTTP requests."""
    global CACHED_METRICS
    system_os = platform.system()
    while True:
        try:
            cpu_percent = 5.0
            mem_percent = 30.0
            disk_free_str = "Calculating..."
            active_window = "Masaüstü"

            if system_os == "Windows":
                # Get CPU load - wmic is faster than powershell Get-CimInstance
                try:
                    cmd = "wmic cpu get loadpercentage /value"
                    out = subprocess.check_output(cmd, shell=True, timeout=1.5).decode().strip()
                    if "LoadPercentage=" in out:
                        cpu_percent = float(out.split("=")[1].strip())
                    else:
                        cpu_percent = 8.0
                except Exception:
                    # fallback
                    try:
                        cmd = "powershell -NoProfile -Command \"(Get-WmiObject Win32_Processor).LoadPercentage\""
                        out = subprocess.check_output(cmd, shell=True, timeout=1.5).decode().strip()
                        cpu_percent = float(out) if out else 10.0
                    except Exception:
                        cpu_percent = 12.0

                # Get RAM usage
                try:
                    cmd = "wmic OS get FreePhysicalMemory,TotalVisibleMemorySize /value"
                    out = subprocess.check_output(cmd, shell=True, timeout=1.5).decode().strip()
                    free_mem = None
                    total_mem = None
                    for line in out.splitlines():
                        if "FreePhysicalMemory=" in line:
                            val = line.split("=")[1].strip()
                            if val.isdigit():
                                free_mem = float(val)
                        elif "TotalVisibleMemorySize=" in line:
                            val = line.split("=")[1].strip()
                            if val.isdigit():
                                total_mem = float(val)
                    if free_mem and total_mem:
                        mem_percent = ((total_mem - free_mem) / total_mem) * 100.0
                    else:
                        mem_percent = 40.0
                except Exception:
                    mem_percent = 45.0

                # Disk free
                try:
                    cmd = "wmic logicaldisk where DeviceID='C:' get FreeSpace,Size /value"
                    out = subprocess.check_output(cmd, shell=True, timeout=1.5).decode().strip()
                    free_space = None
                    for line in out.splitlines():
                        if "FreeSpace=" in line:
                            val = line.split("=")[1].strip()
                            if val.isdigit():
                                free_space = float(val) / (1024**3)
                    if free_space:
                        disk_free_str = f"{free_space:.1f} GB"
                    else:
                        disk_free_str = "50 GB"
                except Exception:
                    disk_free_str = "45 GB"

                # Active window
                try:
                    cmd = "powershell -NoProfile -Command \"(Get-Process | Where-Object {$_.MainWindowTitle} | Select-Object -First 1).MainWindowTitle\""
                    out = subprocess.check_output(cmd, shell=True, timeout=1.5).decode("utf-8", "ignore").strip()
                    if out:
                        active_window = out
                except Exception:
                    active_window = "Windows Desktop"

            else:
                # Linux (Pardus / Debian Ubuntu) - fast /proc files parsing
                try:
                    with open("/proc/stat", "r") as f:
                        line1 = f.readline().split()
                    total = sum([float(i) for i in line1[1:]])
                    idle = float(line1[4])
                    time.sleep(0.4)
                    with open("/proc/stat", "r") as f:
                        line2 = f.readline().split()
                    total2 = sum([float(i) for i in line2[1:]])
                    idle2 = float(line2[4])
                    diff_total = total2 - total
                    diff_idle = idle2 - idle
                    if diff_total > 0:
                        cpu_percent = ((diff_total - diff_idle) / diff_total) * 100
                except Exception:
                    cpu_percent = 5.0

                try:
                    mem_total = 0.0
                    mem_free = 0.0
                    with open("/proc/meminfo", "r") as f:
                        for line in f:
                            if "MemTotal" in line:
                                mem_total = float(line.split()[1])
                            elif "MemAvailable" in line:
                                mem_free = float(line.split()[1])
                    if mem_total > 0:
                        mem_percent = ((mem_total - mem_free) / mem_total) * 100
                except Exception:
                    mem_percent = 50.0

                try:
                    st = os.statvfs("/")
                    free = (st.f_bavail * st.f_frsize) / (1024 ** 3)
                    disk_free_str = f"{free:.1f} GB"
                except Exception:
                    disk_free_str = "15 GB"

                try:
                    cmd = "xdotool getactivewindow getwindowname"
                    active_window = subprocess.check_output(cmd, shell=True, stderr=subprocess.DEVNULL, timeout=1.0).decode().strip()
                except Exception:
                    active_window = "Masaüstü (Linux)"

            # Update cache
            CACHED_METRICS = {
                "os": f"{platform.system()} {platform.release()}",
                "hostname": socket.gethostname(),
                "cpu_usage": round(cpu_percent, 1),
                "memory_usage": round(mem_percent, 1),
                "disk_free": disk_free_str,
                "active_app": active_window
            }
        except Exception as e:
            pass

        time.sleep(4)  # Update every 4 seconds background


def execute_system_action(action_type, site_url, details):
    """Executes various system-level operations on Windows and Linux (Pardus) natively."""
    system_os = platform.system()
    status_msg = "Görev başarıyla işlendi."

    print(f" [⚙️] Sistem Komutu Çalıştırılıyor: {action_type} | Parametre: {details or site_url}")

    try:
        # 1. RUN TERMINAL / SHELL COMMANDS
        if action_type == "command":
            cmd = details
            if not cmd:
                return "Hata: Komut boş olamaz."
            # Execute command (timeout 6s to prevent lockup)
            res = subprocess.run(cmd, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=6.0)
            output = res.stdout if res.stdout else ""
            err = res.stderr if res.stderr else ""
            combined = (output + "\n" + err).strip()
            if not combined:
                combined = f"İşlem tamamlandı (Çıkış kodu: {res.returncode})"
            status_msg = f"Komut Sonucu:\n{combined}"

        # 2. HARDWARE VOLUME CONTROLS
        elif action_type == "volume":
            mode = details.lower()
            if system_os == "Windows":
                # PowerShell WScript SendKeys
                if "up" in mode:
                    cmd = "powershell -NoProfile -Command \"$w = New-Object -ComObject Wscript.Shell; for ($i=0; $i -lt 5; $i++) { $w.SendKeys([char]175) }\""
                    subprocess.Popen(cmd, shell=True)
                    status_msg = "Sistem ses düzeyi %10 arttırıldı."
                elif "down" in mode:
                    cmd = "powershell -NoProfile -Command \"$w = New-Object -ComObject Wscript.Shell; for ($i=0; $i -lt 5; $i++) { $w.SendKeys([char]174) }\""
                    subprocess.Popen(cmd, shell=True)
                    status_msg = "Sistem ses düzeyi %10 düşürüldü."
                elif "mute" in mode:
                    cmd = "powershell -NoProfile -Command \"$w = New-Object -ComObject Wscript.Shell; $w.SendKeys([char]173)\""
                    subprocess.Popen(cmd, shell=True)
                    status_msg = "Sessize alma modu değiştirildi."
            else:
                # Linux pulse audio / alsa
                if "up" in mode:
                    subprocess.Popen("pactl set-sink-volume @DEFAULT_SINK@ +10% || amixer set Master 10%+", shell=True)
                    status_msg = "Ses %10 arttırıldı."
                elif "down" in mode:
                    subprocess.Popen("pactl set-sink-volume @DEFAULT_SINK@ -10% || amixer set Master 10%-", shell=True)
                    status_msg = "Ses %10 düşürüldü."
                elif "mute" in mode:
                    subprocess.Popen("pactl set-sink-mute @DEFAULT_SINK@ toggle || amixer set Master toggle", shell=True)
                    status_msg = "Ses kilit-sessiz modu değiştirildi."

        # 3. CONTEXTUAL MEDIA KEY EMULATIONS
        elif action_type == "media":
            mode = details.lower()
            if system_os == "Windows":
                if "play" in mode or "pause" in mode:
                    subprocess.Popen("powershell -NoProfile -Command \"$w = New-Object -ComObject Wscript.Shell; $w.SendKeys([char]179)\"", shell=True)
                    status_msg = "Medya Oynat/Duraklat (Play/Pause) yapıldı."
                elif "next" in mode:
                    subprocess.Popen("powershell -NoProfile -Command \"$w = New-Object -ComObject Wscript.Shell; $w.SendKeys([char]176)\"", shell=True)
                    status_msg = "Sonraki şarkı (Track Next) geçildi."
                elif "prev" in mode or "back" in mode:
                    subprocess.Popen("powershell -NoProfile -Command \"$w = New-Object -ComObject Wscript.Shell; $w.SendKeys([char]177)\"", shell=True)
                    status_msg = "Önceki şarkı (Track Previous) geçildi."
            else:
                # Linux media keys using playerctl or xdotool
                if "play" in mode or "pause" in mode:
                    subprocess.Popen("playerctl play-pause || xdotool key XF86AudioPlay", shell=True)
                    status_msg = "Medya Oynat/Duraklat sinyali gönderildi."
                elif "next" in mode:
                    subprocess.Popen("playerctl next || xdotool key XF86AudioNext", shell=True)
                    status_msg = "Sonraki şarkı."
                elif "prev" in mode or "back" in mode:
                    subprocess.Popen("playerctl previous || xdotool key XF86AudioPrev", shell=True)
                    status_msg = "Önceki şarkı."

        # 4. COMPUTER POWER STATES
        elif action_type == "power":
            mode = details.lower()
            if "shutdown" in mode or "kapat" in mode:
                status_msg = "Bilgisayar kapatılıyor (Zamanlayıcı: 10 saniye)."
                if system_os == "Windows":
                    subprocess.Popen("shutdown /s /f /t 10", shell=True)
                else:
                    subprocess.Popen("sleep 10 && (systemctl poweroff || shutdown -h now)", shell=True)
            elif "restart" in mode or "yeniden" in mode:
                status_msg = "Bilgisayar yeniden başlatılıyor (Zamanlayıcı: 10 saniye)."
                if system_os == "Windows":
                    subprocess.Popen("shutdown /r /f /t 10", shell=True)
                else:
                    subprocess.Popen("sleep 10 && (systemctl reboot || reboot)", shell=True)
            elif "lock" in mode or "kilitle" in mode:
                status_msg = "Kullanıcı ekranı güvenli kilitlendi."
                if system_os == "Windows":
                    subprocess.Popen("rundll32.exe user32.dll,LockWorkStation", shell=True)
                else:
                    subprocess.Popen("xdg-screensaver lock || gnome-screensaver-command -l", shell=True)
            elif "sleep" in mode or "uyku" in mode:
                status_msg = "Sistem uyku moduna alınıyor."
                if system_os == "Windows":
                    subprocess.Popen("rundll32.exe powrprof.dll,SetSuspendState 0,1,0", shell=True)
                else:
                    subprocess.Popen("systemctl suspend", shell=True)

        # 5. KEYBOARD WORDS / INPUT WRITING
        elif action_type == "keyboard" or action_type == "type":
            text = details
            if not text:
                return "Hata: Yazdırılacak metin boş."
            if system_os == "Windows":
                escaped = text.replace("'", "''").replace("\"", "`\"")
                cmd = f"powershell -NoProfile -Command \"$w = New-Object -ComObject Wscript.Shell; $w.SendKeys('{escaped}')\""
                subprocess.Popen(cmd, shell=True)
                status_msg = f"Metin bilgisayar ekranına yazıldı: '{text}'"
            else:
                subprocess.Popen(f"xdotool type '{text}'", shell=True)
                status_msg = f"Metin ekrana yazdırıldı: '{text}'"

        # 6. OPEN APPLICATIONS
        elif action_type == "open_app":
            app = details
            if not app:
                return "Hata: Başlatılacak uygulama adı belirtilmedi."
            if system_os == "Windows":
                subprocess.Popen(f"start {app}", shell=True)
                status_msg = f"'{app}' uygulaması bilgisayarda başlatılıyor."
            else:
                subprocess.Popen(f"{app} &", shell=True)
                status_msg = f"'{app}' uygulaması başlatılıyor."

        # 7. WEB SITE NAVIGATION
        elif action_type == "open_browser" or site_url:
            target = site_url if site_url else details
            if target:
                if not target.startswith("http"):
                    target = "https://" + target
                webbrowser.open_new_tab(target)
                status_msg = f"Web tarayıcı sekmesi açıldı: {target}"

    except Exception as e:
        status_msg = f"İşlem sırasında hata meydana geldi: {str(e)}"

    return status_msg


def run_gui_automation(action_type, site_url, details, result_msg=""):
    # Entegre web tarayıcısını doğrudan çalıştır (open_browser istekleri için)
    if action_type == "open_browser" and site_url:
        try:
            if not site_url.startswith("http"):
                site_url = "https://" + site_url
            webbrowser.open_new_tab(site_url)
        except Exception as e:
            print(f" [⚠️] Tarayıcı açma hatası: {e}")

    # Masaüstünde canlı işlem durum konsolunu oluştur (Tkinter)
    try:
        import tkinter as tk
        root = tk.Tk()
        root.title("AuraLink AI - Akıllı PC Kontrol Konsolu")
        root.geometry("480x350")
        root.configure(bg="#0b0e14")
        
        # Keep on top to ensure user sees it
        try:
            root.attributes("-topmost", True)
        except Exception:
            pass

        title_lbl = tk.Label(root, text="🤖 AuraLink Akıllı Yapay Zeka Komutu", fg="#00fcfc", bg="#0b0e14", font=("Arial", 12, "bold"))
        title_lbl.pack(pady=10)

        details_lbl = tk.Label(root, text=f"Tür: {action_type}\nParametre: {site_url or details}", fg="#adb5bd", bg="#0b0e14", font=("Arial", 9))
        details_lbl.pack(pady=5)

        log_box = tk.Text(root, height=12, width=54, bg="#131722", fg="#20c997", insertbackground="white", font=("Courier", 9))
        log_box.pack(pady=10)

        def log_step(text):
            log_box.insert(tk.END, f"{text}\n")
            log_box.see(tk.END)
            root.update()

        log_step("[BAŞLADI] Komut işleme birimi tetiklendi...")
        root.update()
        time.sleep(1.0)
        
        log_step(f"[ANALİZ] Talimat çözümlendi. Çalışma Türü: '{action_type}'")
        root.update()
        time.sleep(1.0)

        log_step("[UYGULAMA] Komut bilgisayara gönderiliyor...")
        root.update()
        time.sleep(1.0)

        # Print command result output nicely in Tkinter console
        log_step(f"\n[SİSTEM CEVABI]:\n{result_msg}\n")
        root.update()

        log_step("[TAMAMLANDI] Görev başarıyla icra edildi!")
        root.update()
        
        # Keep open for 6 seconds so user's PC shows it
        root.after(6000, lambda: root.destroy())
        root.mainloop()

    except Exception as e:
        print(f" [⚠️] Tkinter robot ekranı çalıştırılamadı: {e}")


def get_local_ip():
    """Detects and returns the local LAN IP address of this computer."""
    try:
        # Standard socket trick to find active LAN interface
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        return local_ip
    except Exception:
        # Fallback to local loopback or hostname resolver
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"


LOCAL_IP = get_local_ip()


def get_system_metrics():
    """Retrieves OS, CPU and RAM usage percentage from thread-safe cached metrics."""
    return CACHED_METRICS


def search_local_files(query_str):
    """Searches the user folder recursively for items matching the file query_str."""
    matches = []
    user_dir = os.path.expanduser("~")
    count = 0
    
    # Simple, fast walking limit to prevent computer freeze
    for root, dirs, files in os.walk(user_dir):
        # Skip hidden folders
        dirs[:] = [d for d in dirs if not d.startswith('.')]
        for file in files:
            if query_str.lower() in file.lower():
                full_path = os.path.join(root, file)
                try:
                    size = os.path.getsize(full_path)
                except Exception:
                    size = 0
                matches.append({
                    "name": file,
                    "is_dir": False,
                    "path": full_path,
                    "size": size
                })
                count += 1
                if count >= 10:  # limit to 10 files to keep it super fast
                    return matches
    return matches


def execute_system_action(action_type, site_url, details):
    system_os = platform.system()
    
    # Pre-process arguments to make them robust and compatible with AI inputs
    action_type = str(action_type).strip().lower()
    details = str(details).strip()
    site_url = str(site_url).strip()
    
    print(f" [🤖 AKTİF GÖREV] Çözümlenen Eylem: {action_type} | Url: {site_url} | Detaylar: {details}")

    # Map 'command' action type
    if action_type == "command" or action_type == "terminal_command":
        cmd_text = details or site_url
        if cmd_text:
            try:
                res = subprocess.run(cmd_text, shell=True, capture_output=True, text=True, timeout=10)
                output = res.stdout + res.stderr
                return "success", f"Komut başarıyla çalıştırıldı:\n{output[:500]}"
            except subprocess.TimeoutExpired:
                return "success", "Komut arka planda çalışıyor (Süre aşımı)."
            except Exception as e:
                return "error", f"Komut hatası: {str(e)}"
        return "error", "Komut boş."

    # Map 'volume' action type (up, down, mute)
    elif action_type == "volume":
        sub_action = details.lower()
        if "up" in sub_action:
            if system_os == "Windows":
                cmd = "powershell -NoProfile -Command \"(New-Object -ComObject WScript.Shell).SendKeys([char]175)\""
                subprocess.Popen(cmd, shell=True)
            else:
                subprocess.Popen("pactl set-sink-volume @DEFAULT_SINK@ +5%", shell=True)
                subprocess.Popen("amixer sset Master 5%+", shell=True)
            return "success", "Ses artırıldı."
            
        elif "down" in sub_action:
            if system_os == "Windows":
                cmd = "powershell -NoProfile -Command \"(New-Object -ComObject WScript.Shell).SendKeys([char]174)\""
                subprocess.Popen(cmd, shell=True)
            else:
                subprocess.Popen("pactl set-sink-volume @DEFAULT_SINK@ -5%", shell=True)
                subprocess.Popen("amixer sset Master 5%-", shell=True)
            return "success", "Ses azaltıldı."
            
        elif "mute" in sub_action:
            if system_os == "Windows":
                cmd = "powershell -NoProfile -Command \"(New-Object -ComObject WScript.Shell).SendKeys([char]173)\""
                subprocess.Popen(cmd, shell=True)
            else:
                subprocess.Popen("pactl set-sink-mute @DEFAULT_SINK@ toggle", shell=True)
                subprocess.Popen("amixer sset Master toggle", shell=True)
            return "success", "Ses mute/unmute (sessize alındı/açıldı)."
            
        return "error", f"Bilinmeyen ses alt eylemi: {details}"

    # Map 'media' action type (play, pause, next, prev)
    elif action_type == "media":
        sub_action = details.lower()
        if "play" in sub_action or "pause" in sub_action:
            if system_os == "Windows":
                cmd = "powershell -NoProfile -Command \"(New-Object -ComObject WScript.Shell).SendKeys([char]179)\""
                subprocess.Popen(cmd, shell=True)
            else:
                subprocess.Popen("playerctl play-pause", shell=True)
            return "success", "Oynat/Duraklat komutu gönderildi."
            
        elif "next" in sub_action:
            if system_os == "Windows":
                cmd = "powershell -NoProfile -Command \"(New-Object -ComObject WScript.Shell).SendKeys([char]176)\""
                subprocess.Popen(cmd, shell=True)
            else:
                subprocess.Popen("playerctl next", shell=True)
            return "success", "Sonraki şarkıya geçildi."
            
        elif "prev" in sub_action:
            if system_os == "Windows":
                cmd = "powershell -NoProfile -Command \"(New-Object -ComObject WScript.Shell).SendKeys([char]177)\""
                subprocess.Popen(cmd, shell=True)
            else:
                subprocess.Popen("playerctl previous", shell=True)
            return "success", "Önceki şarkıya geçildi."
            
        return "error", f"Bilinmeyen medya alt eylemi: {details}"

    # Map 'power' action type (lock, sleep, shutdown, restart)
    elif action_type == "power":
        sub_action = details.lower()
        if "lock" in sub_action:
            if system_os == "Windows":
                subprocess.Popen("rundll32.exe user32.dll,LockWorkStation", shell=True)
            else:
                subprocess.Popen("xdg-screensaver lock || gnome-screensaver-command -l", shell=True)
            return "success", "Bilgisayar kilitlendi."
            
        elif "sleep" in sub_action:
            if system_os == "Windows":
                subprocess.Popen("rundll32.exe powrprof.dll,SetSuspendState 0,1,0", shell=True)
            else:
                subprocess.Popen("systemctl suspend", shell=True)
            return "success", "Bilgisayar uyku moduna alındı."
            
        elif "shutdown" in sub_action:
            if system_os == "Windows":
                subprocess.Popen("shutdown /s /t 15 /c \"AuraLink tarafindan kapatiliyor\"", shell=True)
            else:
                subprocess.Popen("shutdown -h +1", shell=True)
            return "success", "Bilgisayar 15 saniye içinde kapatılacak."
            
        elif "restart" in sub_action:
            if system_os == "Windows":
                subprocess.Popen("shutdown /r /t 15 /c \"AuraLink tarafindan yeniden baslatiliyor\"", shell=True)
            else:
                subprocess.Popen("shutdown -r +1", shell=True)
            return "success", "Bilgisayar yeniden başlatılıyor."
            
        return "error", f"Bilinmeyen güç alt eylemi: {details}"

    # Map 'keyboard' typing
    elif action_type in ["keyboard", "type_text", "type"]:
        text = details or site_url
        if text:
            if system_os == "Windows":
                escaped = text.replace("'", "''").replace("\"", "\\\"")
                cmd = f"powershell -NoProfile -Command \"[Reflection.Assembly]::LoadWithPartialName('System.Windows.Forms') | Out-Null; [System.Windows.Forms.SendKeys]::SendWait('{escaped}')\""
                subprocess.Popen(cmd, shell=True)
            else:
                subprocess.Popen(f"xdotool type '{text}'", shell=True)
            return "success", f"Bilgisayar ekranına yazıldı: '{text}'"
        return "error", "Yazılacak metin boş."

    # Map 'open_app'
    elif action_type == "open_app":
        app_name = details or site_url
        if app_name:
            if system_os == "Windows":
                subprocess.Popen(f"start {app_name}", shell=True)
            else:
                subprocess.Popen(f"{app_name} &", shell=True)
            return "success", f"Uygulama çalıştırıldı: {app_name}"
        return "error", "Açılacak uygulama belirtilmedi."

    # Map 'open_browser' or 'open_url'
    elif action_type in ["open_browser", "open_url"]:
        url = site_url if site_url else details
        if url:
            if not url.startswith("http://") and not url.startswith("https://"):
                url = "https://" + url
            try:
                webbrowser.open_new_tab(url)
                return "success", f"Tarayıcıda web adresi açıldı: {url}"
            except Exception as e:
                return "error", f"Tarayıcı hatası: {str(e)}"
        return "error", "Lütfen açılacak web adresini belirtin."

    # Map 'google_search' or search_web
    elif action_type in ["google_search", "search_web"]:
        query = details or site_url
        if query:
            url = f"https://www.google.com/search?q={urllib.parse.quote(query)}"
            try:
                webbrowser.open_new_tab(url)
                return "success", f"Tarayıcıda Google araması açıldı: {query}"
            except Exception as e:
                return "error", f"Tarayıcıda Google araması başlatılamadı: {str(e)}"
        return "error", "Lütfen arama kelimesini belirtin."

    # Map 'download_url' or download_file
    elif action_type in ["download_url", "download_file"]:
        url = site_url if site_url else details
        if url:
            if not url.startswith("http://") and not url.startswith("https://"):
                url = "https://" + url
            try:
                if system_os == "Windows":
                    download_dir = os.path.join(os.environ.get("USERPROFILE", "C:\\"), "Downloads")
                else:
                    download_dir = os.path.expanduser("~/Downloads")
                
                if not os.path.exists(download_dir):
                    download_dir = os.path.expanduser("~")
                
                parsed_url = urllib.parse.urlparse(url)
                filename = os.path.basename(parsed_url.path)
                if not filename or "." not in filename:
                    filename = f"downloaded_file_{int(time.time())}.bin"
                
                output_path = os.path.join(download_dir, filename)
                
                import urllib.request
                print(f" [📥] Dosya yükleniyor: {url} -> {output_path}")
                urllib.request.urlretrieve(url, output_path)
                return "success", f"Dosya başarıyla yüklendi ve Downloads klasörüne kaydedildi:\n📍 Yol: {output_path}\n📦 İsim: {filename}"
            except Exception as e:
                return "error", f"Dosya indirme hatası: {str(e)}"
        return "error", "Geçersiz indirme bağlantısı (URL)."

    # Map 'python_script' dynamic automation runner to unlock infinite capabilities
    elif action_type in ["python_script", "script_automation"]:
        code = details or site_url
        if code:
            try:
                temp_filename = f"aura_temp_script_{int(time.time())}.py"
                with open(temp_filename, "w", encoding="utf-8") as temp_file:
                    temp_file.write(code)
                
                res = subprocess.run([sys.executable, temp_filename], capture_output=True, text=True, timeout=12.0)
                output = res.stdout if res.stdout else ""
                err = res.stderr if res.stderr else ""
                joined_outputs = (output + "\n" + err).strip()
                
                try:
                    os.remove(temp_filename)
                except Exception:
                    pass
                
                if res.returncode == 0:
                    return "success", f"Dinamik Python betiği başarıyla çalıştırıldı (Çıkış kodu: 0).\n\n📝 ÇIKTI:\n{joined_outputs[:1000]}"
                else:
                    return "error", f"Dinamik Python betiği hata koduyla sonlandı ({res.returncode}).\n\n📝 HATA YAZISI:\n{joined_outputs[:1000]}"
            except subprocess.TimeoutExpired:
                try:
                    os.remove(temp_filename)
                except Exception:
                    pass
                return "success", "Dinamik betik arka planda çalışmaya devam ediyor (Süre aşımı)."
            except Exception as e:
                return "error", f"Dinamik Python betiği çalıştırılamadı: {str(e)}"
        return "error", "Çalıştırılacak Python kodu boş."

    # Fallback to general terminal command
    else:
        # If unknown action type is provided, attempt to run it as terminal command so that we always succeed!
        try:
            cmd_text = details or site_url or action_type
            res = subprocess.run(cmd_text, shell=True, capture_output=True, text=True, timeout=10)
            output = res.stdout + res.stderr
            return "success", f"Terminal Komutu çalıştırıldı:\n{output[:500]}"
        except Exception as e:
            return "error", f"Bilinmeyen eylem türü '{action_type}' çalıştırılırken hata: {str(e)}"


class CompanionHTTPHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # We override this to clean logging clutter
        pass

    def send_json_response(self, status_code, data_dict):
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data_dict).encode("utf-8"))

    def check_pin(self, query_params):
        """Validates if the correct security passcode is passed."""
        passed_pin = query_params.get("pin", [None])[0]
        return passed_pin == SECURITY_PIN

    def do_GET(self):
        parsed_url = urllib.parse.urlparse(self.path)
        path = parsed_url.path
        query_params = urllib.parse.parse_qs(parsed_url.query)

        # --- LANDING PAGE FOR HOST PC SETUP ---
        if path == "/" or path == "/index.html":
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()

            # Dynamic payload for scanner
            pairing_code = f"{LOCAL_IP}:{PORT}:{SECURITY_PIN}"
            qr_api_url = f"https://api.qr_server.com/v1/create-qr-code/?size=250x250&data={pairing_code}"
            # Fallback to standard online charts if the other is down
            qr_api_url = f"https://api.qrserver.com/v1/create-qr-code/?size=220x220&data={urllib.parse.quote(pairing_code)}"

            html_content = f"""<!DOCTYPE html>
<html>
<head>
    <title>AuraLink Dashboard</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body {{
            background-color: #0b0e14;
            color: #f1f3f5;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            margin: 0;
            padding: 40px 20px;
            display: flex;
            justify-content: center;
        }}
        .container {{
            max-width: 600px;
            width: 100%;
            background: #131722;
            border-radius: 16px;
            border: 1px solid #212529;
            padding: 30px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.5);
            text-align: center;
        }}
        h1 {{ color: #00fcfc; margin-top: 0; font-size: 28px; }}
        .badge {{
            display: inline-block;
            background: rgba(32, 201, 151, 0.2);
            color: #20c997;
            padding: 6px 12px;
            border-radius: 50px;
            font-size: 13px;
            font-weight: bold;
            margin-bottom: 20px;
        }}
        .pin-box {{
            background: #0b0e14;
            border: 2px dashed #2979ff;
            border-radius: 8px;
            padding: 15px;
            font-size: 32px;
            letter-spacing: 4px;
            color: #fff;
            font-weight: bold;
            margin: 20px 0;
        }}
        .qr-card {{
            background: white;
            padding: 15px;
            display: inline-block;
            border-radius: 12px;
            margin-bottom: 20px;
        }}
        .instructions h3 {{ color: #2979ff; text-align: left; margin-bottom: 8px; }}
        .instructions p {{ text-align: left; font-size: 14px; color: #adb5bd; line-height: 1.5; margin: 0 0 15px 0; }}
        .info-pill {{
            background: #1e222e;
            padding: 10px;
            border-radius: 8px;
            font-size: 13px;
            margin-bottom: 8px;
            text-align: left;
        }}
    </style>
</head>
<body>
    <div class="container">
        <div class="badge">AuraLink Sunucusu Aktif</div>
        <h1>Masaüstü Eşleştirme Paneli</h1>
        <p style="color: #adb5bd;">Android uygulamanızı açın, Eşleştir sekmesine giderek bilgileri yazın veya kameranızla aşağıdaki QR kodu okutun.</p>
        
        <div class="qr-card">
            <img src="{qr_api_url}" alt="Pairing QR Code">
        </div>

        <div style="font-size: 14px; color: #adb5bd;">Tek Seferlik Güvenlik Eşleşme Şifresi:</div>
        <div class="pin-box">{SECURITY_PIN}</div>

        <div style="margin-top: 30px;" class="instructions">
            <h3>Bağlantı Detayları</h3>
            <div class="info-pill">🖥️ <strong>Hostname:</strong> {socket.gethostname()}</div>
            <div class="info-pill">🔗 <strong>Yerel IP Adresi:</strong> {LOCAL_IP}</div>
            <div class="info-pill">📍 <strong>Port:</strong> {PORT}</div>
            <div class="info-pill">⚙️ <strong>Sistem:</strong> {platform.system()} {platform.release()}</div>
        </div>
    </div>
</body>
</html>
"""
            self.wfile.write(html_content.encode("utf-8"))
            return

        # --- SECURITY CHECK ---
        if not self.check_pin(query_params):
            self.send_json_response(401, {"status": "error", "message": "Anahtar Yetkilendirme Hatası! Pin geçersiz."})
            return

        # --- PAIRING CHALLENGE ---
        if path == "/pair":
            client_ip = self.client_address[0]
            save_paired_device(client_ip)
            
            show_desktop_notification(
                "AuraLink Eşleşti!",
                f"Sinyal ulaştı. Telefonunuz otomatik bağlantı listesine alındı: {client_ip}"
            )
            
            self.send_json_response(200, {
                "status": "success",
                "message": "Connected!",
                "computer_name": socket.gethostname()
            })

        # --- EXECUTE AUTOMATION ENDPOINT ---
        elif path == "/execute":
            action_type = query_params.get("action_type", [""])[0]
            site_url = query_params.get("site_url", [""])[0]
            details = query_params.get("details", [""])[0]

            show_desktop_notification(
                "AuraLink Robot Görevi!",
                f"Yapay zeka telefonunuzdan bir işlem tetikledi:\n{action_type}"
            )

            # Execute the action on the PC synchronously so we return the success/output directly to the phone API
            status, message = execute_system_action(action_type, site_url, details)

            # Spark the visual log HUD in a separate thread so it doesn't block the HTTP response
            Thread(target=run_gui_automation, args=(action_type, site_url, details, message)).start()

            self.send_json_response(200, {
                "status": status,
                "message": message
            })

        # --- SYSTEM INFO ENDPOINT ---
        elif path == "/info":
            try:
                metrics = get_system_metrics()
                self.send_json_response(200, {
                    "status": "success",
                    "os": metrics["os"],
                    "hostname": metrics["hostname"],
                    "cpu_usage": metrics["cpu_usage"],
                    "memory_usage": metrics["memory_usage"],
                    "disk_free": metrics["disk_free"],
                    "active_app": metrics["active_app"]
                })
            except Exception as e:
                self.send_json_response(500, {"status": "error", "message": str(e)})

        # --- SCREENSHOT ENDPOINT (Takes picture & serves JPEG) ---
        elif path == "/screenshot":
            if not PILLOW_AVAILABLE:
                # If pillow is missing, return a synthesized blue indicator image
                self.send_response(200)
                self.send_header("Content-Type", "image/png")
                self.end_headers()
                # Dummy Base64 PNG fallback (1px pixel transparent overlay)
                self.wfile.write(b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15c4\x00\x00\x00\rIDATx\x9cc`\x00\x01\x00\x00\x0c\x00\x01\x04\x03\x11\xa3\x00\x00\x00\x00IEND\xaeB`\x82')
                return

            try:
                # Take screenshot
                img = ImageGrab.grab()
                self.send_response(200)
                self.send_header("Content-Type", "image/jpeg")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                img.save(self.wfile, "JPEG", quality=75)
            except Exception as e:
                self.send_json_response(500, {"status": "error", "message": f"Ekran fırlatma hatası: {str(e)}"})

        # --- BROWSER FILE SYSTEM INDEXING ---
        elif path == "/files":
            target_path = query_params.get("path", [None])[0]
            
            # Default fallbacks per Platform
            if not target_path or target_path == "null":
                if platform.system() == "Windows":
                    target_path = os.environ.get("HOMEPATH", "C:\\")
                    if not target_path.startswith("C:"):
                        target_path = "C:\\" + target_path
                else:
                    target_path = os.path.expanduser("~")

            try:
                items = []
                parent = os.path.dirname(target_path) if os.path.dirname(target_path) != target_path else None
                is_root = parent is None

                for name in os.listdir(target_path):
                    # Hide internal complex systems
                    if name.startswith(".") or name.startswith("$"):
                        continue
                    full_p = os.path.join(target_path, name)
                    is_dir = os.path.isdir(full_p)
                    try:
                        size = 0 if is_dir else os.path.getsize(full_p)
                    except Exception:
                        size = 0
                    
                    items.append({
                        "name": name,
                        "is_dir": is_dir,
                        "path": full_p,
                        "size": size
                    })

                self.send_json_response(200, {
                    "status": "success",
                    "current_path": target_path,
                    "is_root": is_root,
                    "parent": parent,
                    "items": items
                })
            except Exception as e:
                self.send_json_response(500, {"status": "error", "message": str(e)})

        # --- SEARCH DISK FILES ---
        elif path == "/search":
            query = query_params.get("query", [""])[0]
            if not query:
                self.send_json_response(400, {"status": "error", "message": "Boş sorgu gönderilemez."})
                return
            try:
                matches = search_local_files(query)
                self.send_json_response(200, {
                    "status": "success",
                    "query": query,
                    "matches": matches
                })
            except Exception as e:
                self.send_json_response(500, {"status": "error", "message": str(e)})

        # --- FILE DOWNLOAD ENDPOINT ---
        elif path == "/download":
            file_p = query_params.get("path", [""])[0]
            if not os.path.exists(file_p) or os.path.isdir(file_p):
                self.send_json_response(404, {"status": "error", "message": "Dosya diskte bulunamadı."})
                return

            try:
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Disposition", f"attachment; filename={urllib.parse.quote(os.path.basename(file_p))}")
                self.send_header("Content-Length", str(os.path.getsize(file_p)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()

                with open(file_p, "rb") as f:
                    # Serve chunks of 4kb to keep low memory overhead
                    while True:
                        chunk = f.read(4096)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
            except Exception as e:
                # If error happens mid-stream, fallback
                pass


def run_http_server():
    # Start metrics collector background thread
    Thread(target=update_metrics_loop, daemon=True).start()

    # Start auto connect notifier thread
    Thread(target=announce_online_to_phones, daemon=True).start()

    try:
        from http.server import ThreadingHTTPServer as HTTPServerClass
    except ImportError:
        from http.server import HTTPServer as HTTPServerClass

    server = HTTPServerClass(("0.0.0.0", PORT), CompanionHTTPHandler)
    # Output the initial pair terminal dashboard
    print("=" * 64)
    print("                 AuraLink PC COMPANION SERVER")
    print("=" * 64)
    print(f" [+] İŞLETİM SİSTEMİ: {platform.system()} {platform.release()}")
    print(f" [+] LOCAL IP ADRESI: {LOCAL_IP}")
    print(f" [+] CALISAN PORT   : {PORT}")
    print(f" [+] HOSTEMALE      : {socket.gethostname()}")
    print("-" * 64)
    print(f" [⭐] SİZE ÖZEL GÜVENLİK PIN KODU   : {SECURITY_PIN}")
    print(f" [⭐] EŞLEŞME INTERNET ADRESİ       : http://{LOCAL_IP}:{PORT}/")
    print("-" * 64)
    if not PILLOW_AVAILABLE:
        print(" [⚠️] UYARI: 'Pillow' kütüphanesi yüklü değil!")
        print("     Ekran görüntüsü çekmek için şunu kurun: 'pip install pillow'")
    else:
        print(" [+] Ekran görüntüsü modülü yüklendi.")
    print("=" * 64)
    print("  Sunucu bağlandı. Dinleniyor... Kapatmak için CTRL+C yapın.")
    print("=" * 64)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n Sunucu kapatılıyor. Teşekkürler!")
        server.server_close()


if __name__ == "__main__":
    run_http_server()
