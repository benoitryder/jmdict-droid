@file:OptIn(ExperimentalMaterial3Api::class)

package fr.ryder.benoit.jmdictdroid

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.Consumer
import androidx.navigation.NavController
import fr.ryder.benoit.jmdictdroid.ui.theme.ResultColors
import fr.ryder.benoit.jmdictdroid.ui.theme.themeResultColors
import kotlinx.coroutines.launch

private const val SEARCH_RESULTS_LIMIT = 50

@Composable
fun MainScreen(navController: NavController, jmdictDb: JmdictDb) {
    val activity = LocalContext.current.getComponentActivity()
    val resultColors = themeResultColors()

    var query by remember { mutableStateOf("") }
    var reverse by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<AnnotatedString?>(null) }
    var resultScroll = rememberScrollState()

    // Run search using current query, collect results
    fun searchResults() {
        val pattern = query.trim()
        var result = emptyList<Jmdict.Entry>()
        if (pattern.isNotEmpty()) {
            result = jmdictDb.search(pattern, reverse, SEARCH_RESULTS_LIMIT)
            Log.d(TAG, "new results: ${result.size}")
        }
        resultText = if (result.isEmpty()) {
            null
        } else {
            buildResultText(entries = result, colors = resultColors)
        }
    }

    // Run a search with given query if not null
    fun searchWithQuery(newQuery: String?) {
        if (newQuery != null) {
            query = newQuery
            searchResults()
        }
    }

    // Handle initial query from activity intent
    // Update query and run search on new activity intent
    DisposableEffect(Unit) {
        val intent = activity?.intent
        if (intent != null) {
            searchWithQuery(intentToSearchText(intent))
        }

        //TODO
        // - Find a way to switch to the right screen?
        // - Set cursor position, make sure the keyboard is hidden, ...
        val listener = Consumer<Intent> {
            searchWithQuery(intentToSearchText(it))
        }
        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }

    // Reset scroll when result is updated
    LaunchedEffect(resultText) {
        resultScroll.scrollTo(0)
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                AppSearchBar(
                    query = query,
                    reverse = reverse,
                    onSearch = { searchResults() },
                    onQueryChange = { query = it },
                    onReverseChange = { reverse = it },
                    navController = navController,
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (resultText != null) {
                SelectionContainer {
                    Text(
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(resultScroll),
                        text = resultText!!,
                    )
                }
            } else {
                Text(
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.Center),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    fontStyle = FontStyle.Italic,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize.times(1.5),
                    text = "no results"
                )
            }
        }
    }
}

@Composable
fun AppSearchBar(
    query: String,
    reverse: Boolean,
    onSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onReverseChange: (Boolean) -> Unit,
    navController: NavController,
) {
    var expanded = false  //TODO never expand for now
    SearchBar(
        modifier = Modifier
            // The rounded search bar sticks to the sides by default; add some padding
            // Don't add padding at the top: there is already a visible gap
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .fillMaxWidth(),
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onQueryChange(query); onSearch() },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                placeholder = { Text("Translation") },
                leadingIcon = { SearchMenuIcon(navController = navController) },
                trailingIcon = { SearchWayToggle(reverse, onChange = onReverseChange) },
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        // No suggestions
    }
}

@Composable
fun SearchWayToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onChange,
    ) {
        //TODO Use advance icon pack
        if (checked) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Japanese to English")
        } else {
            Icon(Icons.Default.SwapHoriz, contentDescription = "English to Japanese")
        }
    }
}

@Composable
fun SearchMenuIcon(
    navController: NavController,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Database") },
                onClick = { navController.navigate("database") },
                leadingIcon = { Icon(Icons.Filled.Storage, contentDescription = null) }
            )
        }
    }
}

fun buildResultText(entries: List<Jmdict.Entry>, colors: ResultColors): AnnotatedString {
    val defaultStyle = SpanStyle(fontSize = 16.sp)
    val japStyle = SpanStyle(color = colors.jap, fontSize = 20.sp)
    val senseNumStyle = SpanStyle(color = colors.senseNum)
    val sensePosStyle = SpanStyle(color = colors.sensePos)
    val senseAttrStyle = SpanStyle(color = colors.senseAttr)
    val senseInfoStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val glossTypeStyle = SpanStyle(color = colors.glossType)

    return buildAnnotatedString {
        withStyle(defaultStyle) {
            for (entry in entries) {
                // Japanese text: kanji then reading
                withStyle(japStyle) {
                    if (entry.kanjis.isNotEmpty()) {
                        append(entry.kanjis.joinToString(", ") { it.text })
                        append(" / ")
                    }
                    append(entry.readings.joinToString(", ") { it.text })
                    append("\n")
                }

                // Senses, one per line
                for ((iSense, sense) in entry.senses.withIndex()) {
                    // Sense number
                    withStyle(senseNumStyle) {
                        append("${iSense + 1}) ")
                    }

                    // Part of speech
                    if (sense.partOfSpeech.isNotEmpty()) {
                        withStyle(sensePosStyle) {
                            append(sense.partOfSpeech.joinToString(","))
                            append(" ")
                        }
                    }

                    // Attributes
                    if (sense.fields.isNotEmpty() || sense.miscs.isNotEmpty()) {
                        withStyle(senseAttrStyle) {
                            append("[")
                            append((sense.fields.asSequence() + sense.miscs.asSequence()).joinToString(","))
                            append("] ")
                        }
                    }

                    // Informations
                    if (sense.infos.isNotEmpty()) {
                        withStyle(senseInfoStyle) {
                            append("(")
                            append(sense.infos.joinToString("; "))
                            append(") ")
                        }
                    }

                    // Glosses
                    for ((iGloss, gloss) in sense.glosses.withIndex()) {
                        if (iGloss != 0) {
                            append("; ")
                        }
                        if (gloss.gtype != null) {
                            withStyle(glossTypeStyle) {
                                append("(${gloss.gtype}) ")
                            }
                        }
                        append(gloss.text)
                    }
                    append("\n")
                }

                // Change style of previous line break, to have a nice separator
                addStyle(ParagraphStyle(lineHeight = 1.sp), start = length - 1, end = length)
                addStyle(SpanStyle(fontSize = 9.sp), start = length - 1, end = length)
            }
        }
    }
}

