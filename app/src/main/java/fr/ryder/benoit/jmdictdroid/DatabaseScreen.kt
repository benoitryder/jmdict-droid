@file:OptIn(ExperimentalMaterial3Api::class)

package fr.ryder.benoit.jmdictdroid

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val JMDICT_URL = "http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz"

@Composable
fun DatabaseScreen(navController: NavController, jmdictDb: JmdictDb) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = {
                    Text("Database")
                },
                navigationIcon = {
                    IconButton(onClick = navController::popBackStack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var statusMessage by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf("") }
            var inProgress by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            JmdictTerms()

            Text(
                modifier = Modifier.padding(12.dp),
                text = "Use the button below to create or update the dictionary database from JMdict data. This may take several minutes.",
            )

            Button(
                modifier = Modifier.padding(15.dp),
                enabled = !inProgress,
                onClick = {
                    inProgress = true
                    scope.launch {
                        errorMessage = downloadAndUpdateDatabase(jmdictDb) { status -> statusMessage = status }
                        inProgress = false
                    }
                }
            ) {
                Text("Download and update database")
            }

            if (errorMessage.isNotEmpty()) {
                Text(
                    color = Color.Red,
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    text = errorMessage,
                )
            } else if (statusMessage.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    textAlign = TextAlign.Center,
                    text = statusMessage,
                )
            }

            if (inProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.width(64.dp).padding(12.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
fun JmdictTerms() {
    val annotatedString = buildAnnotatedString {
        append("This application uses the ")

        //TODO Use addLink() helper

        pushStringAnnotation(tag = "URL", annotation = "https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project")
        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline, color = Color.Blue)) {
            append("JMdict")
        }
        pop()

        append(" dictionary files. These files are the property of the ")

        pushStringAnnotation(tag = "URL", annotation = "https://www.edrdg.org/")
        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline, color = Color.Blue)) {
            append("Electronic Dictionary Research and Development Group")
        }
        pop()

        append(", and are used in conformance with the Group's ")

        pushStringAnnotation(tag = "URL", annotation = "https://www.edrdg.org/edrdg/licence.html")
        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline, color = Color.Blue)) {
            append("licence")
        }
        pop()

        append(".")
    }

    val uriHandler = LocalUriHandler.current
    ClickableText(
        modifier = Modifier.padding(12.dp),
        text = annotatedString,
        onClick = { offset ->
            annotatedString.getStringAnnotations(
                tag = "URL", start = offset, end = offset
            ).firstOrNull()?.let { annotation ->
                uriHandler.openUri(annotation.item)
            }
        }
    )
}


// Download and update the database, return an error message, empty on success
private suspend fun downloadAndUpdateDatabase(db: JmdictDb, updateStatus: (String) -> Unit): String {
    Log.i(TAG, "Download JMdict XML file")
    updateStatus("Download and parse JMdict...")
    val jmdict = try {
        getResultWithContext(Dispatchers.IO) {
            Jmdict.parseUrl(JMDICT_URL)
        }
    } catch (e: Exception) {
        Log.e(TAG, "download or parsing failed", e)
        return "Download or parsing failed: ${e}"
    }

    Log.i(TAG, "Import JMdict data to database (${jmdict.entries.size} entries)")
    updateStatus("Build database...")
    try {
        getResultWithContext(Dispatchers.Default) {
            db.importJmdict(jmdict, updateStatus)
        }
    } catch (e: Exception) {
        Log.e(TAG, "update failed", e)
        return "Update failed: ${e}"
    }

    Log.i(TAG, "Database updated")
    updateStatus("Done")

    return ""
}

private suspend fun <T> getResultWithContext(dispatcher: CoroutineDispatcher, block: () -> T): T {
    val result = withContext(dispatcher) {
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    return result.getOrThrow()
}

