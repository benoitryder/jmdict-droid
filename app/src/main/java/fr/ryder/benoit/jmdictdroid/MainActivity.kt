@file:OptIn(ExperimentalMaterial3Api::class)

package fr.ryder.benoit.jmdictdroid

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.util.Consumer
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.ryder.benoit.jmdictdroid.ui.theme.AppMaterialTheme

internal const val TAG = "JmdictDroid"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    scrolledContainerColor = Color.Red,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val jmdictDb = JmdictDb(this)
        val startDestination = if (jmdictDb.isInitialized()) "main" else "database"

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color(0x40000000).toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color(0x40000000).toArgb()),
        )

        setContent {
            AppMaterialTheme {
                val navController = rememberNavController()
                var initialQuery = remember { mutableStateOf(intentToSearchText(intent) ?: "") }

                DisposableEffect(Unit) {
                    val listener = Consumer<Intent> {
                        val newQuery = intentToSearchText(it)
                        if (newQuery != null) {
                            initialQuery.value = newQuery
                            navController.navigate(
                                route = "main",
                                navOptions = NavOptions.Builder()
                                    .setLaunchSingleTop(true)
                                    .setPopUpTo("main", false)
                                    .build(),
                            )
                        }
                    }
                    addOnNewIntentListener(listener)
                    onDispose {
                        removeOnNewIntentListener(listener)
                    }
                }

                NavHost(navController = navController, startDestination = startDestination) {
                    composable("main") {
                        MainScreen(navController, jmdictDb, initialQuery.value)
                    }
                    composable("database") {
                        DatabaseScreen(navController, jmdictDb)
                    }
                    composable("help") {
                        HelpScreen(navController)
                    }
                }
            }
        }
    }
}

// Helper method to get activity in Compose
internal fun Context.getComponentActivity(): ComponentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) {
            return context
        } else if (context is Activity) {
            return null  // should not happen
        }
        context = context.baseContext
    }
    return null
}

internal fun intentToSearchText(intent: Intent?): String? {
    return if (intent == null) {
        null
    } else if (intent.action == Intent.ACTION_SEND) {
        intent.getStringExtra(Intent.EXTRA_TEXT)
    } else {
        null
    }
}
