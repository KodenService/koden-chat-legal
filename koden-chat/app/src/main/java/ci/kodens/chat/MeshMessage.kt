package ci.kodens.chat

import org.json.JSONObject
import java.util.UUID

/** Destinataire spécial : salon public « À proximité ». */
const val BROADCAST = "*"

/** Destinataire spécial : annonce de clé publique (propagée dans le maillage, jamais affichée). */
const val KEY_ANNOUNCE = "#key"

/** Nombre de sauts maximum au départ. */
const val INITIAL_TTL = 7

/** Durée de conservation pour le stocker-et-relayer : 48 h. */
const val RETENTION_MS = 48L * 60 * 60 * 1000

/** Taille maximale du texte d'un message. */
const val MAX_BODY_CHARS = 500

data class MeshMessage(
    val id: String = UUID.randomUUID().toString(),
    val from: String,
    val fromName: String,
    val to: String,
    val body: String,
    val ts: Long = System.currentTimeMillis(),
    val ttl: Int = INITIAL_TTL,
    /** true si body est chiffré (boîte scellée, base64). */
    val enc: Boolean = false,
) {
    fun isExpired(now: Long = System.currentTimeMillis()) = now - ts > RETENTION_MS

    fun toBytes(): ByteArray = JSONObject()
        .put("id", id)
        .put("from", from)
        .put("fromName", fromName)
        .put("to", to)
        .put("body", body)
        .put("ts", ts)
        .put("ttl", ttl)
        .put("enc", enc)
        .toString()
        .toByteArray(Charsets.UTF_8)

    companion object {
        /** Renvoie null si le paquet est invalide (on ignore silencieusement). */
        fun fromBytes(bytes: ByteArray): MeshMessage? = try {
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            MeshMessage(
                id = o.getString("id"),
                from = o.getString("from"),
                fromName = o.getString("fromName").take(30),
                to = o.getString("to"),
                body = o.getString("body"),
                ts = o.getLong("ts"),
                ttl = o.getInt("ttl").coerceIn(0, INITIAL_TTL),
                enc = o.optBoolean("enc", false),
            )
        } catch (e: Exception) {
            null
        }
    }
}
