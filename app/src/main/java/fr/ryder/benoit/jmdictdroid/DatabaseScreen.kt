@file:OptIn(ExperimentalMaterial3Api::class)

package fr.ryder.benoit.jmdictdroid

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
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
            var dictUrl by remember { mutableStateOf(JMDICT_URL) }
            val scope = rememberCoroutineScope()

            Text(
                modifier = Modifier.padding(12.dp),
                text = "Use the button below to create or update the dictionary database from JMdict data. This may take several minutes.",
            )

            TextField(
                modifier = Modifier.fillMaxWidth().padding(15.dp),
                value = dictUrl,
                onValueChange = { dictUrl = it },
                label = { Text("Dictionary URL") },
                singleLine = true,
            )

            Button(
                modifier = Modifier.padding(15.dp),
                enabled = !inProgress,
                onClick = {
                    inProgress = true
                    scope.launch {
                        errorMessage = downloadAndUpdateDatabase(jmdictDb, dictUrl) { status -> statusMessage = status }
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
            } else {
                // Add an empty text to avoid shifting JmdictTerms when download starts
                Text("\n")
            }

            JmdictTerms()
        }
    }
}

@Composable
fun JmdictTerms() {
    val annotatedString = buildAnnotatedString {
        val linkStyles = TextLinkStyles(
            style = SpanStyle(color = Color.Blue),
            hoveredStyle = SpanStyle(textDecoration = TextDecoration.Underline)
        )

        append("This application uses the ")

        withLink(LinkAnnotation.Url("https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project", linkStyles)) {
            append("JMdict")
        }

        append(" dictionary files. These files are the property of the ")

        withLink(LinkAnnotation.Url("https://www.edrdg.org/", linkStyles)) {
            append("Electronic Dictionary Research and Development Group")
        }

        append(", and are used in conformance with the Group's ")

        withLink(LinkAnnotation.Url("https://www.edrdg.org/edrdg/licence.html", linkStyles)) {
            append("licence")
        }

        append(".")
    }

    BasicText(
        modifier = Modifier.padding(12.dp),
        text = annotatedString,
    )
}


// Download and update the database, return an error message, empty on success
private suspend fun downloadAndUpdateDatabase(db: JmdictDb, dictUrl: String, updateStatus: (String) -> Unit): String {
    Log.i(TAG, "Download JMdict XML file")
    updateStatus("Download and parse JMdict...")
    val jmdict = try {
        getResultWithContext(Dispatchers.IO) {
            Jmdict.parseUrl(dictUrl)
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

