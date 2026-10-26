package com.jarvish.ai
import android.accessibilityservice.AccessibilityService
import android.content.*
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class Action(val kind: String, val arg: String = "", val ask: String? = null)

object Parser {
    fun parse(raw: String): Action? {
        val t = raw.lowercase().trim()
        fun has(vararg k: String) = k.any { t.contains(it) }
        val stop = setOf("youtube","google","par","pe","search","karo","kholo","khojo","dhundo","me","mein","aur","jarvish","hello","play","store","app","ko","kar","do","kijiye","is")
        val q = t.split(" ").filter { it !in stop && it.isNotBlank() }.joinToString(" ")
        val who = Regex("(\\S+) ko ").find("$t ")?.groupValues?.get(1)
        return when {
            has("play store", "install") -> Action("store", q)
            has("whatsapp") && has("message", "msg", "bhejo") -> who?.let { Action("wa", ask = it) }
            has("call") -> who?.let { Action("call", ask = it) }
            has("youtube") -> Action("yt", q)
            has("google", "web search") -> Action("web", q)
            has("bluetooth") -> Action("bt")
            has("wifi", "wi-fi") -> Action("wifi")
            has("volume") -> Action("vol", if (has("badha", "zyada", "up")) "up" else "down")
            has("camera", "selfie") -> Action("cam")
            has("scroll") -> Action("scroll")
            has("battery") -> Action("bat")
            has("reminder", "timer") -> Action("rem")
            has("setting") -> Action("set")
            else -> null
        }
    }
}

object Exec {
    private fun number(c: Context, n: String): String? {
        val p = ContactsContract.CommonDataKinds.Phone.NUMBER
        c.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(p),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$n%"), null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }
    private fun go(c: Context, i: Intent, ok: String): String = try {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ok
    } catch (e: Exception) { "Yeh nahi ho paya: ${e.message}" }
    private fun view(u: String) = Intent(Intent.ACTION_VIEW, Uri.parse(u))

    fun run(c: Context, a: Action): String = when (a.kind) {
        "yt" -> go(c, view(if (a.arg.isBlank()) "https://www.youtube.com" else "https://www.youtube.com/results?search_query=" + Uri.encode(a.arg)), "YouTube khol rahi hoon.")
        "web" -> go(c, view("https://www.google.com/search?q=" + Uri.encode(a.arg)), "Google search khol rahi hoon.")
        "bt" -> go(c, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth settings khol di. Switch aap khud dabaiye.")
        "wifi" -> go(c, Intent(Settings.Panel.ACTION_WIFI), "Wi-Fi panel khol diya. Switch aap khud dabaiye.")
        "vol" -> { (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (a.arg == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); "Volume badal diya." }
        "store" -> go(c, view("market://search?q=" + Uri.encode(a.arg)), "Play Store khol rahi hoon. Install aap khud confirm karein.")
        "cam" -> go(c, Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Camera khol rahi hoon.")
        "bat" -> "Battery ${(c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}% hai."
        "rem" -> go(c, Intent(AlarmClock.ACTION_SHOW_ALARMS), "Alarm screen khol di. Reminder aap wahan set karein.")
        "set" -> go(c, Intent(Settings.ACTION_SETTINGS), "Settings khol rahi hoon.")
        "scroll" -> {
            val s = JarvishService.on
            if (s == null) go(c, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Scroll ke liye Accessibility me JARVISH Scroll chalu karein.")
            else if (s.scroll()) "Scroll kiya." else "Yahan scroll hone wala hissa nahi mila."
        }
        "call" -> number(c, a.ask ?: "")?.let { go(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it")), "Dialer khol diya. Call aap khud dabaiye.") } ?: "Contact nahi mila."
        "wa" -> number(c, a.ask ?: "")?.let {
            var d = it.filter { ch -> ch.isDigit() }; if (d.length == 10) d = "91$d"
            go(c, view("https://wa.me/$d"), "WhatsApp chat khol di. Message aap khud likh kar bhejein.")
        } ?: "Contact nahi mila."
        else -> "Yeh action supported nahi hai."
    }
}

class JarvishService : AccessibilityService() {
    companion object { var on: JarvishService? = null }
    override fun onServiceConnected() { on = this }
    override fun onDestroy() { on = null; super.onDestroy() }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    fun scroll(): Boolean = rootInActiveWindow?.let { find(it) } ?: false
    private fun find(n: AccessibilityNodeInfo): Boolean {
        if (n.isScrollable) return n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; if (find(c)) return true }
        return false
    }
}
