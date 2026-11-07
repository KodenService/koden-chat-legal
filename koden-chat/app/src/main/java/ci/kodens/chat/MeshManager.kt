package ci.kodens.chat

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Un voisin directement connecté. */
data class Peer(val endpointId: String, val userId: String, val pseudo: String)

/**
 * Cœur du maillage :
 * - annonce et recherche des téléphones proches (Bluetooth + Wi-Fi Direct via Nearby Connections),
 * - relais multi-sauts avec anti-doublon et compteur de sauts (ttl),
 * - stocker-et-relayer : les messages de moins de 48 h sont renvoyés à chaque nouveau voisin.
 *
 * Prototype : le stockage est en mémoire. La V1 le remplacera par Room + SQLCipher.
 */
class MeshManager(context: Context, private val identity: Identity) {

    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val strategy = Strategy.P2P_CLUSTER

    /** Identifiants des messages déjà vus, pour ne jamais traiter deux fois le même. */
    private val seen = HashSet<String>()

    /** Tous les messages détenus (y compris ceux relayés pour d'autres). */
    private val store = LinkedHashMap<String, MeshMessage>()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()

    /** Messages affichés à l'utilisateur : salon public, reçus pour moi, envoyés par moi. */
    private val _inbox = MutableStateFlow<List<MeshMessage>>(emptyList())
    val inbox: StateFlow<List<MeshMessage>> = _inbox.asStateFlow()

    private val _status = MutableStateFlow("Arrêté")
    val status: StateFlow<String> = _status.asStateFlow()

    /** Contacts appris au fil des messages reçus (userId -> pseudo). */
    private val _contacts = MutableStateFlow<Map<String, String>>(emptyMap())
    val contacts: StateFlow<Map<String, String>> = _contacts.asStateFlow()

    /** Utilisateurs bloqués (identifiants), conservés sur le téléphone. */
    private val blockPrefs = context.applicationContext.getSharedPreferences("blocked", Context.MODE_PRIVATE)
    private val _blocked = MutableStateFlow(blockPrefs.getStringSet("ids", emptySet())?.toSet() ?: emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    /** Bloque un utilisateur : ses messages ne sont plus affichés (ils restent relayés pour les autres). */
    fun block(userId: String) {
        if (userId == identity.userId) return
        _blocked.update { it + userId }
        blockPrefs.edit().putStringSet("ids", _blocked.value).apply()
        _inbox.update { list -> list.filter { it.from != userId } }
    }

    /** Débloque tous les utilisateurs (leurs nouveaux messages s'afficheront de nouveau). */
    fun unblockAll() {
        _blocked.value = emptySet()
        blockPrefs.edit().remove("ids").apply()
    }

    /** Annuaire des clés publiques apprises (userId -> clé publique base64), pour chiffrer les messages privés. */
    private val keyDir = HashMap<String, String>()

    /** Petits messages d'information à afficher (ex. : impossible de chiffrer). */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** Pseudos des voisins en cours de connexion (endpointId -> nom annoncé). */
    private val pendingNames = HashMap<String, String>()

    // Nom annoncé aux voisins : "userId|pseudo"
    private fun endpointName() = "${identity.userId}|${identity.pseudo}"

    private fun parseName(name: String): Pair<String, String> {
        val i = name.indexOf('|')
        return if (i < 0) name to name else name.substring(0, i) to name.substring(i + 1)
    }

    // ---------------------------------------------------------------- démarrage / arrêt

    fun start() {
        _status.value = "Recherche de téléphones à proximité…"
        client.startAdvertising(
            endpointName(), SERVICE_ID, lifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnFailureListener { Log.w(TAG, "Annonce impossible", it); _status.value = "Erreur : ${it.message}" }

        client.startDiscovery(
            SERVICE_ID, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build()
        ).addOnFailureListener { Log.w(TAG, "Recherche impossible", it); _status.value = "Erreur : ${it.message}" }

        announceKey()
    }

    /**
     * Diffuse notre clé publique dans le maillage (identifiant stable, propagée et conservée 48 h)
     * pour que les autres téléphones puissent nous chiffrer des messages privés.
     */
    private fun announceKey() {
        val msg = MeshMessage(
            id = "key-${identity.userId}",
            from = identity.userId,
            fromName = identity.pseudo,
            to = KEY_ANNOUNCE,
            body = identity.publicKey,
        )
        synchronized(this) {
            seen += msg.id
            store[msg.id] = msg
        }
        keyDir[identity.userId] = identity.publicKey
        forward(msg, exceptEndpoint = null)
    }

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _peers.value = emptyMap()
        _status.value = "Arrêté"
    }

    // ---------------------------------------------------------------- envoi

    /** Envoie un nouveau message, au salon public (to = BROADCAST) ou à un contact (chiffré). */
    fun send(body: String, to: String = BROADCAST) {
        val text = body.trim().take(MAX_BODY_CHARS)
        if (text.isEmpty()) return

        if (to == BROADCAST) {
            // Salon public : non chiffré, lisible par toutes les personnes à proximité.
            val msg = MeshMessage(from = identity.userId, fromName = identity.pseudo, to = to, body = text)
            synchronized(this) {
                seen += msg.id
                store[msg.id] = msg
            }
            showIfMine(msg)
            forward(msg, exceptEndpoint = null)
            return
        }

        // Message privé : chiffrement de bout en bout avec la clé publique du destinataire.
        val pub = keyDir[to]
        if (pub == null) {
            _events.tryEmit("Message non envoyé : clé de ce contact pas encore reçue. Réessayez quand il est à proximité.")
            return
        }
        val cipher = Crypto.seal(text, pub)
        if (cipher == null) {
            _events.tryEmit("Échec du chiffrement du message.")
            return
        }
        val wire = MeshMessage(
            from = identity.userId, fromName = identity.pseudo, to = to, body = cipher, enc = true
        )
        synchronized(this) {
            seen += wire.id
            store[wire.id] = wire
        }
        // On affiche la version en clair localement (seul l'expéditeur la voit en clair ici).
        showIfMine(wire.copy(body = text, enc = false))
        forward(wire, exceptEndpoint = null)
    }

    // ---------------------------------------------------------------- réception et relais

    private fun onMessageReceived(msg: MeshMessage, fromEndpoint: String) {
        if (msg.isExpired()) return

        // Annonce de clé publique : on l'enregistre, on la relaie, mais on ne l'affiche jamais.
        if (msg.to == KEY_ANNOUNCE) {
            keyDir[msg.from] = msg.body
            val fresh = synchronized(this) {
                val added = seen.add(msg.id)
                if (added) store[msg.id] = msg
                added
            }
            if (fresh) {
                if (msg.from != identity.userId) {
                    _contacts.update { it + (msg.from to msg.fromName) }
                }
                if (msg.ttl > 1) forward(msg.copy(ttl = msg.ttl - 1), exceptEndpoint = fromEndpoint)
            }
            return
        }

        synchronized(this) {
            if (!seen.add(msg.id)) return          // déjà vu : on ignore (évite les boucles)
            store[msg.id] = msg
        }
        if (msg.from != identity.userId) {
            _contacts.update { it + (msg.from to msg.fromName) }
        }

        // Message privé chiffré qui m'est destiné : on le déchiffre pour l'affichage.
        val display = if (msg.enc && msg.to == identity.userId) {
            val plain = Crypto.open(msg.body, identity.publicKey, identity.secretKey)
            if (plain == null) null else msg.copy(body = plain, enc = false)
        } else {
            msg
        }
        if (display != null) showIfMine(display)

        // On relaie la version d'origine (toujours chiffrée) tant qu'elle ne nous est pas exclusivement destinée.
        if (msg.to != identity.userId && msg.ttl > 1) {
            forward(msg.copy(ttl = msg.ttl - 1), exceptEndpoint = fromEndpoint)
        }
    }

    private fun showIfMine(msg: MeshMessage) {
        val mine = msg.to == BROADCAST || msg.to == identity.userId || msg.from == identity.userId
        if (mine && msg.from !in _blocked.value) _inbox.update { (it + msg).sortedBy { m -> m.ts } }
    }

    private fun forward(msg: MeshMessage, exceptEndpoint: String?) {
        val targets = _peers.value.keys.filter { it != exceptEndpoint }
        if (targets.isNotEmpty()) {
            client.sendPayload(targets, Payload.fromBytes(msg.toBytes()))
        }
    }

    /** Stocker-et-relayer : un nouveau voisin reçoit les messages récents qu'il n'a peut-être pas. */
    private fun syncWith(endpointId: String) {
        val now = System.currentTimeMillis()
        val toSend = synchronized(this) {
            store.values.removeAll { it.isExpired(now) }
            store.values.filter { it.ttl > 1 || it.from == identity.userId }.toList()
        }
        toSend.forEach { client.sendPayload(endpointId, Payload.fromBytes(it.toBytes())) }
    }

    // ---------------------------------------------------------------- callbacks Nearby

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            val (otherId, _) = parseName(info.endpointName)
            // Un seul des deux téléphones demande la connexion, pour éviter les collisions.
            if (identity.userId < otherId && !_peers.value.containsKey(endpointId)) {
                client.requestConnection(endpointName(), endpointId, lifecycleCallback)
                    .addOnFailureListener { Log.d(TAG, "Connexion refusée ou déjà en cours : ${it.message}") }
            }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            pendingNames[endpointId] = info.endpointName
            // Prototype : acceptation automatique. En V1, l'authentification se fera par clé publique.
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val name = pendingNames.remove(endpointId) ?: return
            if (result.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val (uid, pseudo) = parseName(name)
                _peers.update { it + (endpointId to Peer(endpointId, uid, pseudo)) }
                _contacts.update { it + (uid to pseudo) }
                _status.value = connectedLabel()
                syncWith(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            _peers.update { it - endpointId }
            _status.value = if (_peers.value.isEmpty()) "Recherche de téléphones à proximité…"
            else connectedLabel()
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            MeshMessage.fromBytes(bytes)?.let { onMessageReceived(it, endpointId) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private fun connectedLabel(): String {
        val n = _peers.value.size
        return if (n == 1) "1 téléphone connecté" else "$n téléphones connectés"
    }

    companion object {
        const val SERVICE_ID = "ci.kodens.chat.v1"
        private const val TAG = "KodenChat"
    }
}
