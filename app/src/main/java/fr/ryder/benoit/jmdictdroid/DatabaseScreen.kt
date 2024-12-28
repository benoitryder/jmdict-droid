@file:OptIn(ExperimentalMaterial3Api::class)

package fr.ryder.benoit.jmdictdroid

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

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
                    IconButton(onClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate("main")
                        }
                    }) {
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
            val context = LocalContext.current
            var statusMessage by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf("") }
            var inProgress by remember { mutableStateOf(false) }
            var dictUri by remember { mutableStateOf(JMDICT_URL) }
            val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                if (it != null) {
                    dictUri = it.toString()
                }
            }
            val scope = rememberCoroutineScope()
            var dbStatistics by remember { mutableStateOf(JmdictDb.Statistics()) }

            LaunchedEffect(Unit) {
                dbStatistics = jmdictDb.getStatistics()
            }

            Text(
                modifier = Modifier.padding(12.dp),
                text = "Use the button below to create or update the dictionary database from JMdict data. File can be downloaded from a URL or retrieved locally. The operation may take several minutes.",
            )

            TextField(
                modifier = Modifier.fillMaxWidth().padding(15.dp),
                value = dictUri,
                onValueChange = { dictUri = it },
                label = { Text("Dictionary to load") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        pickFileLauncher.launch(arrayOf("application/gzip", "application/x-gzip"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Load file"
                        )
                    }
                },
            )

            Button(
                modifier = Modifier.padding(15.dp),
                enabled = !inProgress,
                onClick = {
                    statusMessage = ""
                    errorMessage = ""
                    inProgress = true
                    scope.launch {
                        val uri = Uri.parse(dictUri)
                        errorMessage = downloadAndUpdateDatabase(context, jmdictDb, uri) { status -> statusMessage = status }
                        inProgress = false
                        dbStatistics = jmdictDb.getStatistics()
                    }
                }
            ) {
                Text("Load and update database")
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
                // Add an empty text to avoid shifting other items when download starts
                Text("\n")
            }

            HorizontalDivider(modifier = Modifier.padding(8.dp))

            BasicText(
                modifier = Modifier.padding(12.dp),
                text = if (inProgress) {
                    "database is being updated"
                } else if (dbStatistics.totalEntries > 0) {
                    "database contains ${dbStatistics.totalEntries} entries, ${dbStatistics.totalSenses} senses"
                } else {
                    "database is empty"
                }
            )

            Button(
                modifier = Modifier.padding(8.dp),
                enabled = !inProgress && dbStatistics.totalEntries > 0,
                onClick = {
                    if (!navController.popBackStack()) {
                        navController.navigate("main")
                    }
                }
            ) {
                Text("Go to main screen")
            }

            Spacer(modifier = Modifier.weight(1f))

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
private suspend fun downloadAndUpdateDatabase(context: Context, db: JmdictDb, dictUri: Uri, updateStatus: (String) -> Unit): String {
    Log.i(TAG, "Load JMdict XML file from ${dictUri}")
    updateStatus("Load and parse JMdict...")
    val jmdict = try {
        getResultWithContext(Dispatchers.IO) {
            val stream = openDictInputStreamFromUri(context, dictUri)
            Jmdict.parseXmlStream(stream)
        }
    } catch (e: Exception) {
        Log.e(TAG, "loading or parsing failed", e)
        return "Loading or parsing failed: ${e}"
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

// Get a JMdict stream from a URI
private fun openDictInputStreamFromUri(context: Context, uri: Uri): InputStream {
    if (uri.scheme == "http" || uri.scheme == "https") {
        val url = URL(uri.toString())
        return openDictInputStreamFromUrl(url) ?: throw RuntimeException("failed to download JMdict: ${uri}")
    } else {
        val stream = context.contentResolver.openInputStream(uri)
        return GZIPInputStream(stream)
    }
}

// Get a JMdict stream from a URL
private fun openDictInputStreamFromUrl(url: URL): InputStream? {
    return (url.openConnection() as? HttpURLConnection)?.run {
        requestMethod = "GET"
        doInput = true
        // Disable GZip: content is already compressed
        setRequestProperty("Accept-Encoding", "identity")
        connect()
        var stream = inputStream
        if (contentType == "application/gzip" || contentType == "application/x-gzip") {
            stream = GZIPInputStream(stream)
        }
        stream
    }
}

