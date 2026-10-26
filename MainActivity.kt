package com.jarvish.ai
import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        tts = TextToSpeech(this) { st -> if (st == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN") }
        setContent { App(tts) }
    }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

val FEATS = listOf(
    listOf("🎙️", "Voice Assistant", "Voice", "settings kholo"), listOf("📷", "Camera", "Phone", "camera kholo"),
    listOf("📶", "Bluetooth", "Phone", "bluetooth on karo"), listOf("🌐", "Wi-Fi Settings", "Phone", "wifi off karo"),
    listOf("📞", "Phone Calls", "Phone", "mummy ko call karo"), listOf("💬", "WhatsApp Chat", "Apps", "whatsapp par rahul ko message bhejo"),
    listOf("▶️", "YouTube Player", "Apps", "youtube par gujarati song search karo"), listOf("🔍", "Google Web Search", "Apps", "google par weather search karo"),
    listOf("🛍️", "Play Store", "Apps", "play store kholo aur whatsapp search karo"), listOf("🔊", "Volume", "Phone", "volume badhao"),
    listOf("🔋", "Device Status", "Phone", "phone ki battery kitni hai"), listOf("⏰", "Reminders & Timers", "Phone", "reminder lagao"),
    listOf("↕️", "Smart Scrolling", "Phone", "screen scroll karo"), listOf("⚙️", "System Settings", "Phone", "settings kholo"))

@Composable fun App(tts: TextToSpeech?) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("j", 0) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var name by remember { mutableStateOf(prefs.getString("name", "सुनील")!!) }
    var tab by remember { mutableStateOf(0) }
    val hist = remember { mutableStateListOf<Pair<Boolean, String>>().apply {
        prefs.getString("h", "")!!.split("\u0001").filter { it.length > 2 }.forEach { add((it[0] == 'U') to it.substring(2)) } } }
    var status by remember { mutableStateOf("Tap to Start") }
    var pending by remember { mutableStateOf<Action?>(null) }
    var queued by remember { mutableStateOf<Action?>(null) }
    fun save() { prefs.edit().putString("h", hist.joinToString("\u0001") { (if (it.first) "U|" else "A|") + it.second.replace("\u0001", " ") }).apply() }
    fun say(t: String) { hist.add(false to t); save(); status = t; tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j") }
    fun exec(a: Action) { say(Exec.run(ctx, a)) }
    val permL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val a = queued; queued = null
        if (a != null) { if (ok) pending = a else say("Contacts ki permission nahi mili.") } }
    fun handle(t: String) {
        hist.add(true to t); save()
        val a = Parser.parse(t)
        if (a == null) { say("Maaf kijiye, yeh command samajh nahi aaya."); return }
        if (a.ask != null) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) pending = a
            else { queued = a; permL.launch(Manifest.permission.READ_CONTACTS) }
        } else exec(a)
    }
    val voiceL = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (t != null) handle(t) else status = "Tap to Start" }
    fun listen() {
        tts?.stop(); status = "Sun rahi hoon…"
        try { voiceL.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")) } catch (e: Exception) { status = "Is phone par speech recognition nahi mila. Neeche type karein." }
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFFB9A4FF)) else lightColorScheme(primary = Color(0xFF7C5CFF), background = Color(0xFFF4F0FF), surface = Color.White)) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
            NavigationBar { listOf("🏠" to "Home", "🕘" to "History", "⚙️" to "Settings", "✨" to "Features").forEachIndexed { i, (e, l) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(e) }, label = { Text(l) }) } }
        }) { p ->
            Box(Modifier.padding(p).fillMaxSize()) {
                when (tab) {
                    0 -> Home(name, dark, { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() }, status, { listen() }, { tts?.stop(); status = "Tap to Start" }, { handle(it) })
                    1 -> History(hist) { hist.clear(); save() }
                    2 -> SettingsScreen(name, { name = it; prefs.edit().putString("name", it).apply() }, dark) { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() }
                    else -> Features { tab = 0; handle(it) }
                }
            }
        }
        pending?.let { a ->
            AlertDialog(onDismissRequest = { pending = null }, title = { Text("Confirm karein") },
                text = { Text(if (a.kind == "call") "${a.ask} ko call karne ke liye dialer kholun?" else "WhatsApp par ${a.ask} ki chat kholun? Message aap khud bhejenge.") },
                confirmButton = { TextButton(onClick = { pending = null; exec(a) }) { Text("Haan") } },
                dismissButton = { TextButton(onClick = { pending = null }) { Text("Nahi") } })
        }
    }
}

@Composable fun Card2(m: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(m.fillMaxWidth().padding(vertical = 6.dp), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0x227C5CFF))) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable fun Home(name: String, dark: Boolean, onDark: () -> Unit, status: String, onMic: () -> Unit, onStop: () -> Unit, onType: (String) -> Unit) {
    val ctx = LocalContext.current
    var txt by remember { mutableStateOf("") }
    val bat = remember { (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    val date = remember { java.text.SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date()) }
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hi = if (h < 12) "शुभ प्रभात" else if (h < 17) "शुभ दोपहर" else "शुभ संध्या"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("JARVISH", Modifier.weight(1f), fontSize = 28.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            TextButton(onClick = onDark) { Text(if (dark) "☀️" else "🌙", fontSize = 22.sp) }
        }
        Text("$hi, $name", Modifier.fillMaxWidth(), fontSize = 20.sp)
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(150.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { onMic() }, contentAlignment = Alignment.Center) { Text("🎙️", fontSize = 48.sp) }
        Spacer(Modifier.height(12.dp))
        Text(status, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row { Button(onClick = onMic) { Text("Tap to Start") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = onStop) { Text("⏹ Stop") } }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(txt, { txt = it }, Modifier.fillMaxWidth(), label = { Text("Ya command type karein") }, singleLine = true,
            trailingIcon = { TextButton(onClick = { if (txt.isNotBlank()) { onType(txt); txt = "" } }) { Text("Bhejo") } })
        Card2 { Text("🔋 Battery: $bat%"); Text("📅 $date"); Text("📍 Location: abhi connect nahi (permission nahi li gayi)") }
    }
}

@Composable fun History(h: List<Pair<Boolean, String>>, onClear: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("बातचीत", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth()) { listOf("कुल" to h.size, "आपके" to h.count { it.first }, "JARVISH" to h.count { !it.first }).forEach { (l, n) ->
                Card2(Modifier.weight(1f).padding(horizontal = 4.dp)) { Text("$n", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(l, fontSize = 12.sp) } } }
            if (h.isNotEmpty()) OutlinedButton(onClick = { ask = true }) { Text("Clear History") }
            if (h.isEmpty()) Text("अभी कोई बातचीत नहीं। Home पर Tap to Start दबाइए।")
        }
        items(h) { (u, t) ->
            Box(Modifier.fillMaxWidth().padding(4.dp), contentAlignment = if (u) Alignment.CenterEnd else Alignment.CenterStart) {
                Surface(shape = RoundedCornerShape(16.dp), color = if (u) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface) {
                    Text(t, Modifier.padding(12.dp), color = if (u) Color.White else MaterialTheme.colorScheme.onSurface) } }
        }
    }
    if (ask) AlertDialog(onDismissRequest = { ask = false }, title = { Text("History saaf karein?") },
        confirmButton = { TextButton(onClick = { ask = false; onClear() }) { Text("Haan") } }, dismissButton = { TextButton(onClick = { ask = false }) { Text("Nahi") } })
}

@Composable fun Sec(icon: String, title: String, body: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card2 {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp); Spacer(Modifier.width(12.dp)); Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text(if (open) "▲" else "▼") }
        if (open) { Spacer(Modifier.height(10.dp)); body() }
    }
}

@Composable fun SettingsScreen(name: String, onName: (String) -> Unit, dark: Boolean, onDark: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Sec("👤", "Profile & Language") { OutlinedTextField(name, onName, label = { Text("नाम") }); Text("Voice भाषा: हिन्दी (hi-IN)") }
        Sec("🆘", "SOS & Contacts") { Text("SOS अभी उपलब्ध नहीं है।") }
        Sec("🛠️", "Tools & Behavior") { Row(verticalAlignment = Alignment.CenterVertically) { Text("Dark mode", Modifier.weight(1f)); Switch(dark, { onDark() }) } }
        Sec("👁️", "Screen Awareness") {
            Text("Scroll के लिए Accessibility सेवा आपको खुद चालू करनी होगी। यह सिर्फ़ scroll होने वाला हिस्सा ढूँढती है; कुछ सेव या भेजती नहीं।")
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility खोलें") } }
        Sec("🔑", "API & Intelligence") { Text("Cloud AI (Gemini) अभी जुड़ा नहीं है। API key कभी app में नहीं रखी जाएगी; इसके लिए backend चाहिए।") }
    }
}

@Composable fun Features(onRun: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All") }
    val list = FEATS.filter { (cat == "All" || it[2] == cat) && it[1].contains(q, true) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Features", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Search features") }, singleLine = true)
            Row(Modifier.padding(vertical = 8.dp)) { listOf("All", "Voice", "Phone", "Apps").forEach { c ->
                Surface(Modifier.padding(end = 8.dp).clickable { cat = c }, shape = RoundedCornerShape(50), color = if (cat == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface) {
                    Text(c, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (cat == c) Color.White else MaterialTheme.colorScheme.onSurface) } } }
            if (list.isEmpty()) Text("कोई feature नहीं मिला।")
        }
        items(list) { f ->
            Card2(Modifier.clickable { onRun(f[3]) }) { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f[0], fontSize = 24.sp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(f[1], fontWeight = FontWeight.Bold); Text(f[2], fontSize = 12.sp) }; Text("›", fontSize = 22.sp) } }
        }
    }
}
