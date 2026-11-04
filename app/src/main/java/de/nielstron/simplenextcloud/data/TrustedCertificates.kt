package de.nielstron.simplenextcloud.data

import android.content.Context
import okhttp3.OkHttpClient
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** A server certificate the system doesn't trust, shown to the user to accept or reject. */
data class UntrustedCertificate(
    val host: String,
    val sha256: String,
    val subject: String,
    val issuer: String,
    val validUntil: String,
    val encoded: ByteArray,
)

internal fun sha256Fingerprint(encoded: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }

/**
 * Certificates the user accepted explicitly (self-signed or from a private CA), matched by their
 * exact SHA-256 fingerprint. Everything else is verified by the system as usual.
 */
class TrustedCertificates private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("trusted_certificates", Context.MODE_PRIVATE)

    @Volatile
    private var fingerprints: Set<String> = preferences.getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun isTrusted(certificate: X509Certificate) = sha256Fingerprint(certificate.encoded) in fingerprints

    fun trust(certificate: UntrustedCertificate) = update(fingerprints + certificate.sha256)

    fun clear() = update(emptySet())

    private fun update(value: Set<String>) {
        fingerprints = value
        preferences.edit().putStringSet(KEY, value).apply()
    }

    private val systemTrust: X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
            init(null as KeyStore?)
            trustManagers.filterIsInstance<X509TrustManager>().first()
        }

    private val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            systemTrust.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                systemTrust.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                if (chain.isEmpty() || !isTrusted(chain[0])) throw e
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = systemTrust.acceptedIssuers
    }

    // A self-signed certificate often doesn't name the host (e.g. a LAN IP), so an accepted one
    // also stands in for the host name check.
    private val hostnameVerifier = HostnameVerifier { host, session ->
        HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session) ||
            (session.peerCertificates.firstOrNull() as? X509Certificate)?.let(::isTrusted) == true
    }

    val client: OkHttpClient by lazy {
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustManager), null) }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustManager)
            .hostnameVerifier(hostnameVerifier)
            .build()
    }

    companion object {
        private const val KEY = "sha256"

        @Volatile
        private var instance: TrustedCertificates? = null

        fun get(context: Context): TrustedCertificates = instance ?: synchronized(this) {
            instance ?: TrustedCertificates(context.applicationContext).also { instance = it }
        }

        fun client(context: Context) = get(context).client

        /** Whether [failure] (or one of its causes) means the server's certificate isn't trusted. */
        fun isCertificateFailure(failure: Throwable): Boolean =
            generateSequence(failure) { it.cause }.take(8).any {
                it is SSLPeerUnverifiedException || it is CertPathValidatorException ||
                    (it is SSLHandshakeException && it.cause is CertificateException)
            }

        /** Fetches the certificate [url]'s server presents, without trusting it. Blocking. */
        fun fetch(url: String): UntrustedCertificate {
            val uri = URI(url)
            val host = requireNotNull(uri.host) { "No host in $url" }
            val port = if (uri.port > 0) uri.port else 443
            val acceptAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(acceptAll), null) }
            // Only reads the certificate: the connection is closed right after the handshake.
            (ssl.socketFactory.createSocket() as SSLSocket).use { socket ->
                socket.connect(java.net.InetSocketAddress(host, port), TimeUnit.SECONDS.toMillis(10).toInt())
                socket.soTimeout = TimeUnit.SECONDS.toMillis(10).toInt()
                socket.startHandshake()
                val leaf = socket.session.peerCertificates.first() as X509Certificate
                return UntrustedCertificate(
                    host = host,
                    sha256 = sha256Fingerprint(leaf.encoded),
                    subject = leaf.subjectX500Principal.name,
                    issuer = leaf.issuerX500Principal.name,
                    validUntil = DateFormat.getDateInstance(DateFormat.MEDIUM).format(leaf.notAfter),
                    encoded = leaf.encoded,
                )
            }
        }
    }
}
