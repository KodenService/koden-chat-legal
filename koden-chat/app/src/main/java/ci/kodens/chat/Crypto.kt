package ci.kodens.chat

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Box

/**
 * Chiffrement de bout en bout des messages privés, via libsodium (Lazysodium).
 *
 * Principe de la « boîte scellée » (sealed box) : un message est chiffré avec la
 * CLÉ PUBLIQUE du destinataire. Seul le détenteur de la CLÉ PRIVÉE correspondante
 * peut le déchiffrer. Les téléphones qui relaient ne voient qu'un bloc illisible.
 */
object Crypto {
    private val ls = LazySodiumAndroid(SodiumAndroid())

    /** Génère une paire de clés X25519. Renvoie (clé publique, clé privée) en base64. */
    fun newKeyPair(): Pair<String, String> {
        val kp = ls.cryptoBoxKeypair()
        return b64(kp.publicKey.asBytes) to b64(kp.secretKey.asBytes)
    }

    /** Chiffre un texte pour le détenteur de publicKeyB64. Renvoie le chiffré en base64, ou null si échec. */
    fun seal(plain: String, publicKeyB64: String): String? = try {
        val pub = unb64(publicKeyB64)
        val msg = plain.toByteArray(Charsets.UTF_8)
        val cipher = ByteArray(msg.size + Box.SEALBYTES)
        if (ls.cryptoBoxSeal(cipher, msg, msg.size.toLong(), pub)) b64(cipher) else null
    } catch (e: Exception) {
        null
    }

    /** Déchiffre avec notre paire de clés. Renvoie le texte clair, ou null si ce n'est pas pour nous. */
    fun open(cipherB64: String, publicKeyB64: String, secretKeyB64: String): String? = try {
        val cipher = unb64(cipherB64)
        if (cipher.size < Box.SEALBYTES) {
            null
        } else {
            val msg = ByteArray(cipher.size - Box.SEALBYTES)
            val ok = ls.cryptoBoxSealOpen(
                msg, cipher, cipher.size.toLong(), unb64(publicKeyB64), unb64(secretKeyB64)
            )
            if (ok) String(msg, Charsets.UTF_8) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
