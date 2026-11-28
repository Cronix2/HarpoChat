// SPDX-License-Identifier: MIT
package app.calc.scientific.bg

import android.content.Context
import androidx.core.content.edit

/**
 * Choix « réception en arrière-plan ». Il doit se lire app verrouillée : il vit dans les
 * préférences ordinaires, et ne dit rien d'autre qu'un booléen sous un nom neutre.
 */
object BackgroundPrefs {
    private const val FILE = "calc_settings"
    private const val KEY = "bg_refresh"

    fun isEnabled(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
}
