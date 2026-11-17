package com.jarvis.ai.device

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import java.util.Locale

/**
 * Allow-listed Android actions only. Sensitive operations open the relevant system UI;
 * this class never silently changes security or connectivity settings.
 */
class DeviceController(private val context: Context) {
    fun executeIfSupported(rawCommand: String): String? {
        val command = rawCommand.trim().lowercase(Locale.ROOT)
        return try {
            when {
                command in listOf("open settings", "settings", "phone settings") -> {
                    open(Intent(Settings.ACTION_SETTINGS)); "Opening Android Settings."
                }
                command.contains("wi-fi settings") || command.contains("wifi settings") || command == "open wifi" -> {
                    open(Intent(Settings.ACTION_WIFI_SETTINGS)); "Opening Wi-Fi settings. You can change the network there."
                }
                command.contains("bluetooth settings") || command == "open bluetooth" -> {
                    open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); "Opening Bluetooth settings."
                }
                command.contains("display settings") || command.contains("brightness settings") -> {
                    open(Intent(Settings.ACTION_DISPLAY_SETTINGS)); "Opening display settings."
                }
                command.contains("sound settings") || command.contains("volume settings") -> {
                    open(Intent(Settings.ACTION_SOUND_SETTINGS)); "Opening sound settings."
                }
                command.contains("do not disturb") || command.contains("dnd settings") -> {
                    open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); "Opening Do Not Disturb access settings."
                }
                command.startsWith("call ") -> {
                    val number = rawCommand.substringAfter("call ").trim()
                    if (number.isBlank()) "Tell me which number to call." else {
                        open(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + android.net.Uri.encode(number))))
                        "Opening the dialer. Review the number and tap call when ready."
                    }
                }
                command.startsWith("text ") || command.startsWith("send sms ") -> {
                    val body = rawCommand.substringAfter("text ", rawCommand.substringAfter("send sms ", "")).trim()
                    val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:"))
                        .putExtra("sms_body", body)
                    open(intent)
                    "Opening your messaging app. Review and send the message yourself."
                }
                command.contains("battery settings") -> {
                    open(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)); "Opening battery settings."
                }
                command.contains("open camera") || command == "camera" -> {
                    open(Intent("android.media.action.IMAGE_CAPTURE")); "Opening the camera."
                }
                command.contains("open clock") || command == "clock" -> {
                    open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CLOCK)); "Opening Clock."
                }
                command.startsWith("set an alarm") || command.startsWith("set alarm") -> setAlarm(command)
                command.startsWith("open app ") -> launchApp(rawCommand.substringAfter("open app ", "").trim())
                command.startsWith("launch app ") -> launchApp(rawCommand.substringAfter("launch app ", "").trim())
                command.startsWith("open ") -> launchKnownApp(rawCommand.substringAfter("open ").trim()) ?: launchApp(rawCommand.substringAfter("open ").trim())
                command.contains("open notifications") || command == "notifications" -> {
                    open(Intent(Settings.ACTION_NOTIFICATION_SETTINGS)); "Opening notification settings."
                }
                else -> null
            }
        } catch (e: Exception) {
            "I couldn't complete that phone action: ${e.message ?: "Android did not allow it"}."
        }
    }

    private fun setAlarm(command: String): String {
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(command)
            ?: return "Tell me a time, for example: set an alarm for 7:30 AM."
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val meridiem = match.groupValues[3]
        if (minute !in 0..59 || hour !in 0..23) return "That alarm time doesn't look valid."
        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        val label = "JARVIS alarm"
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        open(intent)
        return "Opening the alarm confirmation screen for %02d:%02d.".format(hour, minute)
    }

    private fun launchApp(name: String): String {
        if (name.isBlank()) return "Tell me which app to open."
        val pm = context.packageManager
        val candidates = pm.getInstalledApplications(0)
        val app = candidates.firstOrNull {
            pm.getApplicationLabel(it).toString().equals(name, ignoreCase = true)
        } ?: candidates.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(name, ignoreCase = true)
        } ?: return "I couldn't find an installed app named $name."
        val launch = pm.getLaunchIntentForPackage(app.packageName)
            ?: return "That app doesn't provide a launch screen."
        open(launch)
        return "Opening ${pm.getApplicationLabel(app)}."
    }

    private fun launchKnownApp(name: String): String? {
        val known = listOf("camera", "clock", "settings", "wifi", "bluetooth", "notifications")
        return if (name.lowercase(Locale.ROOT) in known) executeIfSupported(name) else null
    }

    private fun open(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            throw IllegalStateException("No app is available for that action.")
        }
        context.startActivity(intent)
    }
}
