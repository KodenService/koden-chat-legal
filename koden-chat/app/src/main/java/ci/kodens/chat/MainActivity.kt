package ci.kodens.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var identity: Identity
    private val permissionsGranted = mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionsGranted.value = result.values.all { it }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()  // obligatoire avec Android 16 (API 36)
        super.onCreate(savedInstanceState)
        identity = Identity(this)
        permissionsGranted.value = requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        setContent {
            MaterialTheme(colorScheme = KodenColors) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    var pseudo by remember { mutableStateOf(identity.pseudo) }
                    when {
                        pseudo.isBlank() -> PseudoScreen { identity.pseudo = it; pseudo = identity.pseudo }
                        !permissionsGranted.value -> PermissionScreen {
                            permissionLauncher.launch(requiredPermissions())
                        }
                        else -> {
                            LaunchedEffect(Unit) { MeshService.start(this@MainActivity) }
                            ChatScreen(MeshHolder.get(this@MainActivity), identity)
                        }
                    }
                }
            }
        }
    }

    private fun requiredPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_ADVERTISE
            list += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.NEARBY_WIFI_DEVICES
            list += Manifest.permission.POST_NOTIFICATIONS
        } else {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return list.toTypedArray()
    }
}

// -------------------------------------------------------------------- marque

private val KodenPurple = Color(0xFF7628B2)
private val KodenTurquoise = Color(0xFF14A0D2)
private val KodenLight = Color(0xFFD6F0F8)
private val KodenLavender = Color(0xFFF0E8F7)

private val KodenColors = lightColorScheme(
    primary = KodenPurple,
    secondary = KodenTurquoise,
    secondaryContainer = KodenLight,
)

/** Logo Koden'S + enveloppe. */
@Composable
private fun BrandLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.koden_chat_logo),
        contentDescription = "Koden Chat",
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = modifier
    )
}

// -------------------------------------------------------------------- écrans

@Composable
private fun PseudoScreen(onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(KodenLavender).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        BrandLogo(Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.padding(12.dp))
        Text("Bienvenue sur Koden Chat", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = KodenPurple)
        Spacer(Modifier.padding(8.dp))
        Text("Discutez avec les personnes autour de vous, sans internet ni crédit.")
        Spacer(Modifier.padding(16.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(30) },
            label = { Text("Votre pseudo") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.padding(8.dp))
        Button(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Commencer")
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Autorisations nécessaires", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(8.dp))
        Text(
            "Koden Chat utilise le Bluetooth et le Wi-Fi pour trouver les téléphones proches. " +
                "Aucune donnée n'est envoyée sur internet."
        )
        Spacer(Modifier.padding(16.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Autoriser") }
    }
}

@Composable
private fun ChatScreen(mesh: MeshManager, identity: Identity) {
    val inbox by mesh.inbox.collectAsStateWithLifecycle()
    val peers by mesh.peers.collectAsStateWithLifecycle()
    val status by mesh.status.collectAsStateWithLifecycle()
    val contacts by mesh.contacts.collectAsStateWithLifecycle()
    val blocked by mesh.blocked.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var draft by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf(BROADCAST) }
    var menuOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MeshMessage?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(inbox.size) {
        if (inbox.isNotEmpty()) listState.animateScrollToItem(inbox.lastIndex)
    }

    LaunchedEffect(Unit) {
        mesh.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        // En-tête
        Column(Modifier.fillMaxWidth().background(KodenLavender).padding(horizontal = 16.dp, vertical = 10.dp)) {
            BrandLogo(Modifier.height(44.dp))
            Text(status, color = KodenPurple, fontSize = 13.sp)
            Text(
                "Appui long sur un message pour le signaler ou bloquer son auteur",
                color = Color.Gray, fontSize = 11.sp
            )
            if (blocked.isNotEmpty()) {
                TextButton(onClick = { mesh.unblockAll() }) {
                    Text("${blocked.size} utilisateur(s) bloqué(s) · Tout débloquer", fontSize = 12.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(KodenPurple))

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(inbox, key = { it.id }) { msg ->
                val mine = msg.from == identity.userId
                MessageBubble(
                    msg, mine = mine, contacts = contacts,
                    onLongPress = { if (!mine) selected = msg }
                )
            }
        }

        // Signaler / bloquer
        selected?.let { msg ->
            AlertDialog(
                onDismissRequest = { selected = null },
                title = { Text("Message de ${msg.fromName}") },
                text = { Text("« ${msg.body.take(120)} »") },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = {
                            reportMessage(context, msg)
                            selected = null
                        }) { Text("Signaler ce message") }
                        TextButton(onClick = {
                            mesh.block(msg.from)
                            if (recipient == msg.from) recipient = BROADCAST
                            Toast.makeText(context, "${msg.fromName} est bloqué", Toast.LENGTH_SHORT).show()
                            selected = null
                        }) { Text("Bloquer ${msg.fromName}", color = Color(0xFFC62828)) }
                        TextButton(onClick = { selected = null }) { Text("Annuler") }
                    }
                }
            )
        }

        // Choix du destinataire
        Box(Modifier.padding(horizontal = 12.dp)) {
            OutlinedButton(onClick = { menuOpen = true }) {
                Text(if (recipient == BROADCAST) "À : tout le monde à proximité" else "🔒 À : ${contacts[recipient] ?: recipient} (privé, chiffré)")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Tout le monde à proximité") }, onClick = {
                    recipient = BROADCAST; menuOpen = false
                })
                contacts.filterKeys { it !in blocked }.forEach { (uid, name) ->
                    DropdownMenuItem(text = { Text("$name (privé)") }, onClick = {
                        recipient = uid; menuOpen = false
                    })
                }
            }
        }

        // Saisie
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it.take(MAX_BODY_CHARS) },
                placeholder = { Text("Votre message") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { mesh.send(draft, recipient); draft = "" }, enabled = draft.isNotBlank()) {
                Text("Envoyer")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageBubble(
    msg: MeshMessage,
    mine: Boolean,
    contacts: Map<String, String>,
    onLongPress: () -> Unit = {},
) {
    val time = remember(msg.ts) { SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(msg.ts)) }
    val privateLabel = when {
        msg.to == BROADCAST -> ""
        mine -> " · privé à ${contacts[msg.to] ?: msg.to}"
        else -> " · privé"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .background(
                    if (mine) KodenLight else Color(0xFFF0F0F0),
                    RoundedCornerShape(12.dp)
                )
                .padding(10.dp)
        ) {
            Text(
                (if (mine) "Moi" else msg.fromName) + privateLabel,
                fontWeight = FontWeight.SemiBold, fontSize = 12.sp
            )
            Text(msg.body, fontSize = 15.sp)
            Text(time, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

/** Ouvre l'application e-mail avec un signalement pré-rempli vers Koden Service. */
private fun reportMessage(context: android.content.Context, msg: MeshMessage) {
    val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(msg.ts))
    val subject = "Signalement Koden Chat"
    val body = "Je signale le message suivant :\n\n" +
        "Auteur : ${msg.fromName} (identifiant ${msg.from})\n" +
        "Date : $time\n" +
        "Message : ${msg.body}\n\n" +
        "Motif du signalement : "
    val uri = Uri.parse(
        "mailto:contact@kodens.ci?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
    )
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune application e-mail. Écrivez à contact@kodens.ci", Toast.LENGTH_LONG).show()
    }
}
