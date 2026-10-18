package com.example.romear.core.data

import com.example.romear.BuildConfig
import com.example.romear.core.network.FoundationApi
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import retrofit2.HttpException

sealed interface ConnectionResult {
    data object Connected : ConnectionResult
    data object Offline : ConnectionResult
    data object Unconfigured : ConnectionResult
    data object Unavailable : ConnectionResult
    data object UpgradeRequired : ConnectionResult
    data object Maintenance : ConnectionResult
}

class FoundationRepository @Inject constructor(
    private val api: FoundationApi,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun check(): ConnectionResult = withContext(dispatcher) {
        if (BuildConfig.API_BASE_URL.contains("unconfigured.invalid")) {
            return@withContext ConnectionResult.Unconfigured
        }
        try {
            val health = api.health().use { JSONObject(it.string()) }
            val config = api.config().use { JSONObject(it.string()) }
            when {
                health.getString("status") != "ok" -> ConnectionResult.Unavailable
                config.getInt("minAppVersion") > BuildConfig.VERSION_CODE -> ConnectionResult.UpgradeRequired
                config.optBoolean("maintenance", false) -> ConnectionResult.Maintenance
                else -> ConnectionResult.Connected
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            ConnectionResult.Offline
        } catch (_: HttpException) {
            ConnectionResult.Unavailable
        } catch (_: JSONException) {
            ConnectionResult.Unavailable
        }
    }
}
