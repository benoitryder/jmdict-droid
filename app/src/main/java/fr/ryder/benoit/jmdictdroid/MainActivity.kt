@file:OptIn(ExperimentalMaterial3Api::class)

package fr.ryder.benoit.jmdictdroid

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color(0x40000000).toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color(0x40000000).toArgb()),
        )

        setContent {
            AppMaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(navController, jmdictDb)
                    }
                    composable("database") {
                        DatabaseScreen(navController, jmdictDb)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG,"new intent: ${intent.action}")
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

internal fun intentToSearchText(intent: Intent): String? {
    if (intent.action == Intent.ACTION_SEND) {
        return intent.getStringExtra(Intent.EXTRA_TEXT)
    } else {
        return null
    }
}
