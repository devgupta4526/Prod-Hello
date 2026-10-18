package com.example.romear.core.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/** Fetches an approximate location only after an explicit visibility choice. */
class PresenceLocation(private val fragment: Fragment, private val chosen: (String, Location?) -> Unit) {
    private var pending: String? = null
    private val permission = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val value = pending
        if(granted && value != null) locate(value)
        else Toast.makeText(fragment.context,"Location is needed for nearby discovery",Toast.LENGTH_LONG).show()
    }
    fun choose(availability: String) {
        pending = availability
        if(availability == "OFF") { chosen(availability,null); return }
        if(ContextCompat.checkSelfPermission(fragment.requireContext(),Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate(availability)
        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun locate(availability: String) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val manager = fragment.requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val provider = listOf("fused", LocationManager.NETWORK_PROVIDER).firstOrNull { manager.allProviders.contains(it) && manager.isProviderEnabled(it) }
                    ?: error("Enable approximate location services and try again")
                val location = withTimeout(30000) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val signal = CancellationSignal()
                        continuation.invokeOnCancellation { signal.cancel() }
                        LocationManagerCompat.getCurrentLocation(manager,provider,signal,ContextCompat.getMainExecutor(fragment.requireContext())) { result ->
                            if(continuation.isActive) continuation.resume(result)
                        }
                    }
                } ?: error("Location is unavailable. Please try again")
                if(pending == availability) chosen(availability,location)
            } catch(e: kotlinx.coroutines.TimeoutCancellationException) {
                Toast.makeText(fragment.context,"Location timed out. Please try again",Toast.LENGTH_LONG).show()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { Toast.makeText(fragment.context,e.message ?: "Location unavailable",Toast.LENGTH_LONG).show() }
        }
    }
}
