package com.example.harpochat.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.harpochat.calculator.CalculatorActivity
import com.example.harpochat.security.SecureStore
import kotlinx.coroutines.launch

class PinSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        actionBar?.hide()
        super.onCreate(savedInstanceState)
        setContent {
            // Force un schéma sombre simple et fiable
            MaterialTheme(colorScheme = darkColorScheme()) {
                PinSettingsRoot(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSettingsRoot(onBack: () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Réglages",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Sécurité") },
                    selected = true,
                    onClick = { /* déjà ici */ },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                // TODO: autres items de paramètres plus tard
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Security") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour"
                            )
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(remember { SnackbarHostState() }) }
        ) { padding ->
            PinSettingsContent(Modifier.padding(padding))
        }
    }
}

@Composable
private fun PinSettingsContent(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { SecureStore.prefs(context) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var secretPin by remember {
        mutableStateOf(prefs.getString(CalculatorActivity.KEY_SECRET_PIN, "") ?: "")
    }
    var duressPin by remember {
        mutableStateOf(prefs.getString(CalculatorActivity.KEY_DURESS_PIN, "") ?: "")
    }
    var showSecret by remember { mutableStateOf(false) }
    var showDuress by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Host pour les messages
        SnackbarHost(hostState = snackbarHost)

        Text("Codes PIN", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = secretPin,
            onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) secretPin = it },
            label = { Text("PIN de déverrouillage") },
            placeholder = { Text("ex: 527418") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword
            ),
            visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showSecret = !showSecret }) {
                    Icon(
                        if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = duressPin,
            onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) duressPin = it },
            label = { Text("PIN d’effacement (duress)") },
            placeholder = { Text("ex: 1234") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword
            ),
            visualTransformation = if (showDuress) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showDuress = !showDuress }) {
                    Icon(
                        if (showDuress) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    when {
                        secretPin.isBlank() || duressPin.isBlank() ->
                            scope.launch { snackbarHost.showSnackbar("Les deux PIN sont requis") }
                        secretPin == duressPin ->
                            scope.launch { snackbarHost.showSnackbar("Les deux PIN ne doivent pas être identiques") }
                        else -> {
                            prefs.edit {
                                putString(CalculatorActivity.KEY_SECRET_PIN, secretPin)
                                putString(CalculatorActivity.KEY_DURESS_PIN, duressPin)
                            }
                            scope.launch { snackbarHost.showSnackbar("PIN mis à jour") }
                        }
                    }
                }
            ) { Text("Enregistrer") }

            TextButton(onClick = {
                secretPin = ""
                duressPin = ""
            }) { Text("Réinitialiser les champs") }
        }

        Spacer(Modifier.height(24.dp))
    }
}
