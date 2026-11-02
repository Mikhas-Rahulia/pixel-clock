# Polityka prywatności / Privacy Policy

Ostatnia aktualizacja / Last updated: 2026-11-02

Aplikacja **Clock** (`com.example.pixelclock`) — dalej „Aplikacja”.

---

## 1. Polski

### 1.1. Administrator i kontakt
Aplikacja jest udostępniana bezpłatnie przez autora (Mikhas-Rahulia) jako projekt open source. Pytania dotyczące prywatności zgłaszaj przez [GitHub Issues](https://github.com/Mikhas-Rahulia/pixel-clock/issues).

### 1.2. Jakie dane są przetwarzane
Aplikacja **nie zbiera, nie wysyła i nie udostępnia żadnych danych osobowych**. Autor nie otrzymuje żadnych danych z telefonu.

Dane, które aplikacja zapisuje, są przechowywane **wyłącznie lokalnie na urządzeniu**, w pamięci aplikacji:
- lista alarmów (godzina, nazwa, dni tygodnia, data, stan włączenia),
- lista timerów oraz historia uruchomień timerów z ostatniej godziny (do liczników „N min · M×”),
- ustawienia wyglądu.

### 1.3. Brak połączenia z internetem
Aplikacja **nie deklaruje uprawnienia dostępu do internetu** (`INTERNET`). Nie korzysta z żadnych serwerów, analityki, raportowania awarii, reklam ani bibliotek zewnętrznych, które zbierają dane.

### 1.4. Uprawnienia i ich cel
| Uprawnienie | Po co jest potrzebne |
|---|---|
| `POST_NOTIFICATIONS` | Pokazywanie powiadomień alarmów i timerów |
| `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM` | Dokładne uruchamianie alarmów i timerów o zaplanowanej porze |
| `USE_FULL_SCREEN_INTENT` | Pełnoekranowy ekran dzwonienia alarmu lub timera |
| `RECEIVE_BOOT_COMPLETED` | Ponowne zaplanowanie alarmów po restarcie telefonu |
| `VIBRATE`, `WAKE_LOCK` | Wibracja i utrzymanie dzwonienia, gdy ekran jest wyłączony |
| `FOREGROUND_SERVICE` (+ typy `specialUse`, `mediaPlayback`) | Działanie w tle podczas odliczania timera i dzwonienia alarmu |

### 1.5. Podstawa prawna i prawa osób (RODO)
Ponieważ autor nie przetwarza danych osobowych, nie posiada dostępu do danych użytkowników i nie ma informacji umożliwiających identyfikację osób. Wszystkie dane pozostają na urządzeniu użytkownika, dlatego prawa z art. 15–22 RODO realizuje użytkownik samodzielnie: może je przeglądać, zmieniać lub usuwać w samej aplikacji, a także usunąć wszystkie dane, odinstalowując aplikację lub czyszcząc jej dane w ustawieniach systemu Android.

### 1.6. Dzieci
Aplikacja nie zbiera danych od nikogo, w tym od dzieci.

### 1.7. Zmiany polityki
Zmiany tej polityki będą publikowane w tym pliku z nową datą aktualizacji.

---

## 2. English

The app **Clock** (`com.example.pixelclock`) is free and open source.

- **No personal data is collected, sent or shared.** The author receives nothing from your phone.
- Alarms, timers and their run history are stored **only on your device**, in the app's private storage.
- The app **does not request internet access** (`INTERNET` is not declared), and contains no analytics, crash reporting, ads or data-collecting SDKs.
- Permissions are used only for alarms and timers: notifications, exact alarms, full-screen alerts, restoring alarms after reboot, vibration, keeping the device awake while an alarm rings, and background timers.
- To exercise your rights (access, correction, erasure), change or delete data inside the app, or uninstall the app / clear its data in Android settings.
- Questions: [GitHub Issues](https://github.com/Mikhas-Rahulia/pixel-clock/issues).
