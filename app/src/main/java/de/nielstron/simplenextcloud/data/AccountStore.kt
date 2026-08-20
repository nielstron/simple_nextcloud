package de.nielstron.simplenextcloud.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal const val LOGIN_FLOW_LIFETIME_MILLIS = 20 * 60 * 1_000L

internal fun isLoginFlowExpired(createdAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis - createdAtMillis >= LOGIN_FLOW_LIFETIME_MILLIS

data class PendingLogin(
    val session: LoginFlowSession,
    val expiresAtMillis: Long,
)

class AccountStore(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun load(): Account? {
        val server = preferences.getString("server", null)?.let(::decrypt) ?: return null
        val username = preferences.getString("username", null)?.let(::decrypt) ?: return null
        val password = preferences.getString("password", null)?.let(::decrypt) ?: return null
        return Account(server, username, password)
    }

    fun save(account: Account) {
        preferences.edit()
            .putString("server", encrypt(account.serverUrl))
            .putString("username", encrypt(account.username))
            .putString("password", encrypt(account.appPassword))
            .apply()
    }

    fun clear() = preferences.edit().clear().apply()

    fun savePendingLogin(session: LoginFlowSession): PendingLogin {
        val createdAtMillis = nowMillis()
        preferences.edit()
            .putString("login_url", encrypt(session.loginUrl))
            .putString("poll_endpoint", encrypt(session.pollEndpoint))
            .putString("poll_token", encrypt(session.token))
            .putLong("login_created_at", createdAtMillis)
            .apply()
        return PendingLogin(session, createdAtMillis + LOGIN_FLOW_LIFETIME_MILLIS)
    }

    fun loadPendingLogin(): PendingLogin? {
        val createdAtMillis = preferences.getLong("login_created_at", 0L)
        if (createdAtMillis == 0L || isLoginFlowExpired(createdAtMillis, nowMillis())) {
            clearPendingLogin()
            return null
        }
        val loginUrl = preferences.getString("login_url", null)?.let(::decrypt) ?: return null
        val endpoint = preferences.getString("poll_endpoint", null)?.let(::decrypt) ?: return null
        val token = preferences.getString("poll_token", null)?.let(::decrypt) ?: return null
        return PendingLogin(
            LoginFlowSession(loginUrl, endpoint, token),
            createdAtMillis + LOGIN_FLOW_LIFETIME_MILLIS,
        )
    }

    fun clearPendingLogin() {
        preferences.edit()
            .remove("login_url")
            .remove("poll_endpoint")
            .remove("poll_token")
            .remove("login_created_at")
            .apply()
    }

    private fun key(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray())
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
    }

    private companion object {
        const val KEY_ALIAS = "good_nextcloud_account_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
