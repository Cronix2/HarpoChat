// SPDX-License-Identifier: MIT
package app.harpochat.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Fausse puce sécurisée : HMAC avec une clé en mémoire, destructible. */
private class FakeBinder : KeyBinder {
    var key: ByteArray? = null
    override fun exists() = key != null
    override fun create() { key = ByteArray(32).also(SecureRandom()::nextBytes) }
    override fun bind(input: ByteArray): ByteArray {
        val k = key ?: throw IllegalStateException("clé détruite")
        return Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(k, "HmacSHA256")); doFinal(input) }
    }
    override fun destroy() { key = null }
    override fun protectionLevel() = ProtectionLevel.SOFTWARE
}

private class MapStore : VaultStore {
    val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun putAll(values: Map<String, String>) { map.putAll(values) }
    override fun clear() = map.clear()
}

class VaultTest {
    private lateinit var store: MapStore
    private lateinit var binder: FakeBinder
    private lateinit var vault: Vault

    @Before fun setUp() {
        store = MapStore()
        binder = FakeBinder()
        vault = Vault(store, binder, iterations = 1_000) // itérations réduites pour la vitesse des tests
    }

    @Test fun notConfiguredInitially() = assertFalse(vault.isConfigured())

    @Test fun secretPinReturnsTheSameDbKey() {
        val key = vault.setup("482913", "771045")
        val r = vault.unlock("482913")
        assertTrue(r is Vault.UnlockResult.Secret)
        assertArrayEquals(key, (r as Vault.UnlockResult.Secret).dbKey)
    }

    @Test fun duressPinIsRecognised() {
        vault.setup("482913", "771045")
        assertEquals(Vault.UnlockResult.Duress, vault.unlock("771045"))
    }

    @Test fun wrongPinMatchesNothing() {
        vault.setup("482913", "771045")
        assertEquals(Vault.UnlockResult.NoMatch, vault.unlock("000001"))
    }

    @Test fun secretPinIsNeverStored() {
        vault.setup("482913", "771045")
        assertFalse(store.map.values.any { it.contains("482913") || it.contains("771045") })
    }

    @Test fun storedDataIsUselessWithoutTheHardwareKey() {
        vault.setup("482913", "771045")
        // Un attaquant copie les préférences sur un autre « téléphone » (autre clé matérielle).
        val otherPhone = FakeBinder().apply { create() }
        val stolen = Vault(MapStore().apply { map.putAll(store.map) }, otherPhone, iterations = 1_000)
        assertEquals(Vault.UnlockResult.NoMatch, stolen.unlock("482913"))
    }

    @Test fun wipeMakesEverythingUnreadable() {
        vault.setup("482913", "771045")
        vault.wipe()
        assertFalse(vault.isConfigured())
        assertEquals(Vault.UnlockResult.NoMatch, vault.unlock("482913"))
        assertTrue(store.map.isEmpty())
    }

    @Test fun changePinsKeepsTheSameDbKey() {
        val key = vault.setup("482913", "771045")
        assertTrue(vault.changePins("482913", "650187", "209384"))
        assertEquals(Vault.UnlockResult.NoMatch, vault.unlock("482913"))
        assertEquals(Vault.UnlockResult.Duress, vault.unlock("209384"))
        val r = vault.unlock("650187") as Vault.UnlockResult.Secret
        assertArrayEquals(key, r.dbKey)
    }

    @Test fun changePinsRequiresTheCurrentSecret() {
        vault.setup("482913", "771045")
        assertFalse(vault.changePins("999999", "650187", "209384"))
        assertTrue(vault.unlock("482913") is Vault.UnlockResult.Secret)
    }
}
