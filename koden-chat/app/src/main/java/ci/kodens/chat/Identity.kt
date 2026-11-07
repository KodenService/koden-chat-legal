package ci.kodens.chat

import android.content.Context
import java.util.UUID

/**
 * Identité locale, générée sur le téléphone, sans compte ni serveur.
 * Comprend une paire de clés X25519 pour le chiffrement de bout en bout.
 *
 * Note : dans cette version, la clé privée est stockée dans les préférences de
 * l'application. La V1 renforcera ce stockage (base chiffrée). La clé privée ne
 * quitte jamais le téléphone et n'est jamais transmise.
 */
class Identity(context: Context) {
    private val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)

    val userId: String = prefs.getString("userId", null) ?: UUID.randomUUID()
        .toString().replace("-", "").take(12)
        .also { prefs.edit().putString("userId", it).apply() }

    var pseudo: String
        get() = prefs.getString("pseudo", "") ?: ""
        set(value) = prefs.edit().putString("pseudo", value.trim().take(30)).apply()

    /** Clé publique (partagée avec les autres) et clé privée (gardée secrète), en base64. */
    val publicKey: String
    val secretKey: String

    init {
        val pk = prefs.getString("pk", null)
        val sk = prefs.getString("sk", null)
        if (pk != null && sk != null) {
            publicKey = pk
            secretKey = sk
        } else {
            val (p, s) = Crypto.newKeyPair()
            publicKey = p
            secretKey = s
            prefs.edit().putString("pk", p).putString("sk", s).apply()
        }
    }
}
