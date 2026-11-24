package com.example.facefit.data.privacy

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.facefit.data.auth.AuthManager
import com.example.facefit.data.auth.GoogleSignInClient
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.example.facefit.data.session.UserPreferencesRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class PrivacyRepository(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val prefs = context.applicationContext.getSharedPreferences("privacy_preferences", Context.MODE_PRIVATE)
    val scans = UserPreferencesRepository(context)
    val analyticsEnabled: Boolean get() = prefs.getBoolean("analyticsEnabled", false)
    val usesPassword: Boolean get() = auth.currentUser?.providerData?.any { it.providerId == "password" } == true
    val signedIn: Boolean get() = auth.currentUser != null

    fun applyAnalyticsPreference() {
        val analytics = FirebaseAnalytics.getInstance(context)
        analytics.setConsent(mapOf(
            FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to if (analyticsEnabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
        ))
        analytics.setAnalyticsCollectionEnabled(analyticsEnabled)
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("analyticsEnabled", enabled).apply()
        val analytics = FirebaseAnalytics.getInstance(context)
        applyAnalyticsPreference()
        if (!enabled) analytics.resetAnalyticsData()
    }

    suspend fun exportData(): Intent {
        val user = auth.currentUser
        val uid = user?.uid
        val cloud = if (user != null) call("exportMyAccountData") else JSONObject()
        check(auth.currentUser?.uid == uid) { "Your account changed. Please export again." }
        val data = JSONObject().put("schemaVersion", 1).put("exportedAt", java.time.Instant.now().toString())
            .put("account", JSONObject().put("uid", uid).put("email", user?.email).put("displayName", user?.displayName))
            .put("preferences", JSONObject().put("saveScanHistory", scans.isHistoryEnabled())
                .put("usageAnalytics", analyticsEnabled))
            .put("localScan", scans.exportLocalScan() ?: JSONObject.NULL).put("cloud", cloud)
        val file = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "privacy-exports").apply { mkdirs() }
            directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }
                ?.forEach { it.delete() }
            File.createTempFile("facefit-data-", ".json", directory).apply { writeText(data.toString(2)) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".privacy-files", file)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("FaceFit data export", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Export your FaceFit data")
    }

    /**
     * Offload: archive the account instead of deleting it. Profile, favorites and scans stay in the
     * cloud, marked archived; this device's copies are removed and the user is signed out.
     * Signing in again restores everything (AuthManager.restoreIfOffloaded).
     */
    suspend fun offloadAccount() {
        val user = checkNotNull(auth.currentUser) { "Sign in before offloading your account." }
        FirebaseFirestore.getInstance().collection("users").document(user.uid)
            .set(mapOf("accountStatus" to AuthManager.ACCOUNT_ARCHIVED,
                "archivedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
        check(auth.currentUser?.uid == user.uid) { "Your account changed. Please try again." }
        scans.clearLocalCopy() // Restored from the cloud at the next sign-in.
        setAnalyticsEnabled(false)
        withContext(Dispatchers.IO) { File(context.cacheDir, "privacy-exports").listFiles()?.forEach { it.delete() } }
        auth.signOut()
        GoogleSignInClient.clearSignInState(context)
    }

    suspend fun deleteAccount(password: String) {
        val user = checkNotNull(auth.currentUser) { "Sign in before deleting your account." }
        val credential = if (usesPassword) {
            EmailAuthProvider.getCredential(checkNotNull(user.email), password)
        } else {
            GoogleAuthProvider.getCredential(GoogleSignInClient(context).getIdToken(), null)
        }
        user.reauthenticate(credential).await()
        check(auth.currentUser?.uid == user.uid) { "Your account changed. Please try again." }
        check(call("deleteMyAccountData").optBoolean("deleted")) { "Account deletion was not confirmed. Please try again." }
        // Only report success after the server confirms Firestore descendants AND Auth were deleted.
        scans.clearScan()
        setAnalyticsEnabled(false)
        withContext(Dispatchers.IO) { File(context.cacheDir, "privacy-exports").listFiles()?.forEach { it.delete() } }
        auth.signOut()
        GoogleSignInClient.clearSignInState(context)
    }

    /** Firebase callable wire protocol; no credentials or personal data are logged. */
    private suspend fun call(name: String): JSONObject {
        val user = checkNotNull(auth.currentUser) { "Please sign in first." }
        val token = checkNotNull(user.getIdToken(true).await().token)
        val project = checkNotNull(FirebaseApp.getInstance().options.projectId)
        return withContext(Dispatchers.IO) {
            val connection = URL("https://us-central1-$project.cloudfunctions.net/$name").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 120_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.outputStream.use { it.write("{\"data\":{}}".toByteArray()) }
                val status = connection.responseCode
                if (status == 404) error("Account data service is not deployed yet. Please try again after it is enabled.")
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val response = runCatching { JSONObject(body) }.getOrElse {
                    error("The account data service is unavailable. Please try again.")
                }
                response.optJSONObject("error")?.let {
                    error(it.optString("message", "The request could not be completed. Please try again."))
                }
                check(status in 200..299) { "The account data request failed. Please try again." }
                response.optJSONObject("result") ?: response.optJSONObject("data")
                    ?: error("The account data service returned an invalid response.")
            } finally { connection.disconnect() }
        }
    }
}
