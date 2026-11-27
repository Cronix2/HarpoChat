// SPDX-License-Identifier: MIT
package app.calc.scientific.security

import android.content.Context
import androidx.core.content.edit

/**
 * Préférences ordinaires au nom neutre. Elles ne contiennent AUCUN secret en clair :
 * sels, vecteur d'initialisation, clé de base chiffrée, empreinte du PIN de détresse.
 * Sans le PIN et sans la clé matérielle du téléphone, ces valeurs sont inexploitables.
 */
class PrefsVaultStore(context: Context) : VaultStore {
    private val prefs = context.getSharedPreferences("calc_settings", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun putAll(values: Map<String, String>) {
        prefs.edit(commit = true) { values.forEach { (k, v) -> putString(k, v) } }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }
}
