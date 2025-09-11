package com.example.harpochat.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.harpochat.calculator.CalculatorActivity // pour accéder aux clés existantes
import com.example.harpochat.security.SecureStore

class PinSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    PinSettingsScreen(
                        onSave = { secret, duress ->
                            val prefs = SecureStore.prefs(this)
                            prefs.edit {
                                putString(CalculatorActivity.KEY_SECRET_PIN, secret)
                                putString(CalculatorActivity.KEY_DURESS_PIN, duress)
                            }
                            Toast.makeText(this, "PIN mis à jour", Toast.LENGTH_SHORT).show()
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PinSettingsScreen(
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var secret by remember { mutableStateOf("") }
    var duress by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var showDuress by remember { mutableStateOf(false) }

    // règles simples : 4..12 chiffres, et différents
    val isNumeric: (String) -> Boolean = { it.isNotBlank() && it.all(Char::isDigit) }
    val validLen: (String) -> Boolean = { it.length in 4..12 }
    val notEqual = secret != duress
    val valid = isNumeric(secret) && isNumeric(duress) && validLen(secret) && validLen(duress) && notEqual

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sécurité", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Définissez un code de déverrouillage et un code d’effacement (différents).",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = secret,
            onValueChange = { if (it.length <= 12) secret = it.filter(Char::isDigit) },
            label = { Text("Nouveau PIN (déverrouillage)") },
            singleLine = true,
            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showSecret = !showSecret }) {
                    Icon(if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                }
            },
            supportingText = { if (!validLen(secret)) Text("4 à 12 chiffres") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = duress,
            onValueChange = { if (it.length <= 12) duress = it.filter(Char::isDigit) },
            label = { Text("PIN d’effacement") },
            singleLine = true,
            visualTransformation = if (showDuress) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showDuress = !showDuress }) {
                    Icon(if (showDuress) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                }
            },
            supportingText = {
                when {
                    !validLen(duress) -> Text("4 à 12 chiffres")
                    !notEqual -> Text("Doit être différent du PIN de déverrouillage")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Annuler")
            }
            Button(
                onClick = { onSave(secret, duress) },
                enabled = valid,
                modifier = Modifier.weight(1f)
            ) { Text("Enregistrer") }
        }
    }
}
