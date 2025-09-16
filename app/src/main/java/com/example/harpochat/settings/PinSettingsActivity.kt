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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.harpochat.calculator.CalculatorActivity
import com.example.harpochat.security.PinHasher
import com.example.harpochat.security.SecureStore
import kotlinx.coroutines.launch

class PinSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        actionBar?.hide()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PinSettingsRoot(onBack = { finish() })
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
            }
        }
    ) {
        val snackbarHost = remember { SnackbarHostState() }

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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHost) }
        ) { padding ->
            PinSettingsContent(
                modifier = Modifier.padding(padding),
                snackbarHost = snackbarHost
            )
        }
    }
}

@Composable
private fun PinSettingsContent(
    modifier: Modifier = Modifier,
    snackbarHost: SnackbarHostState
) {
    val context = LocalContext.current
    val prefs = remember { SecureStore.prefs(context) }
    val scope = rememberCoroutineScope()

    // Par sécurité on NE pré-remplit PAS depuis les prefs
    var secretPin by remember { mutableStateOf("") }
    var duressPin by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var showDuress by remember { mutableStateOf(false) }

    // État d’info : est-ce que l’utilisateur a déjà changé au moins un PIN ?
    val pinsChanged by remember {
        mutableStateOf(prefs.getBoolean(CalculatorActivity.KEY_PINS_CHANGED, false))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Codes PIN", style = MaterialTheme.typography.titleLarge)

        AssistChip(
            onClick = {},
            label = {
                Text(
                    if (pinsChanged) "PIN modifiés ✔︎"
                    else "PIN par défaut en place ⚠︎"
                )
            }
        )

        OutlinedTextField(
            value = secretPin,
            onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) secretPin = it },
            label = { Text("PIN de déverrouillage") },
            placeholder = { Text("4 à 12 chiffres") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
            placeholder = { Text("4 à 12 chiffres") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
                    // Validation
                    val bothProvided = secretPin.isNotBlank() && duressPin.isNotBlank()
                    val lengthsOk = secretPin.length in 4..12 && duressPin.length in 4..12
                    val numeric = secretPin.all(Char::isDigit) && duressPin.all(Char::isDigit)
                    val different = secretPin != duressPin

                    when {
                        !bothProvided ->
                            scope.launch { snackbarHost.showSnackbar("Les deux PIN sont requis") }
                        !numeric ->
                            scope.launch { snackbarHost.showSnackbar("PIN numériques uniquement") }
                        !lengthsOk ->
                            scope.launch { snackbarHost.showSnackbar("PIN : 4 à 12 chiffres") }
                        !different ->
                            scope.launch { snackbarHost.showSnackbar("Les deux PIN doivent être différents") }
                        else -> {
                            // Hash + sauvegarde
                            val secretHash = PinHasher.hash(secretPin)
                            val duressHash = PinHasher.hash(duressPin)
                            prefs.edit {
                                putString(CalculatorActivity.KEY_SECRET_PIN_HASH, secretHash)
                                putString(CalculatorActivity.KEY_DURESS_PIN_HASH, duressHash)
                                // On supprime les anciennes clés en clair si elles existaient
                                remove(CalculatorActivity.KEY_SECRET_PIN)
                                remove(CalculatorActivity.KEY_DURESS_PIN)
                                // L’utilisateur a changé au moins un PIN : utile pour masquer l’alerte
                                putBoolean(CalculatorActivity.KEY_PINS_CHANGED, true)
                            }
                            // Efface le champ en mémoire (hygiene)
                            secretPin = ""
                            duressPin = ""

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
