package com.example.romear

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.romear.core.data.ConnectionResult
import com.example.romear.core.data.FoundationRepository
import com.example.romear.core.di.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationConnectionTest {
    @Test
    fun connects_to_real_backend_from_android() = runBlocking {
        // Explicit opt-in: this test requires the local API/PostgreSQL to be running.
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveBackend") == "true")
        val repository = FoundationRepository(NetworkModule.api(), Dispatchers.IO)
        assertEquals(ConnectionResult.Connected, repository.check())
    }
}
