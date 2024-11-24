@file:OptIn(ExperimentalMaterial3Api::class)

package fr.ryder.benoit.jmdictdroid

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.navigation.NavController
import fr.ryder.benoit.jmdictdroid.ui.theme.ResultColors
import fr.ryder.benoit.jmdictdroid.ui.theme.themeResultColors

private const val SEARCH_RESULTS_LIMIT = 50

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(navController: NavController, jmdictDb: JmdictDb, initialQuery: String) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val resultColors = themeResultColors()

    val currentInitialQuery by rememberUpdatedState(initialQuery)
    val queryState = rememberTextFieldState()
    var forceEnglish by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<AnnotatedString?>(null) }
    val resultScroll = rememberScrollState()
    val searchFocusRequester = remember { FocusRequester() }

    // Run search using current query, collect results
    fun searchResults() {
        val pattern = queryState.text.trim().toString()
        var result = emptyList<Jmdict.Entry>()
        if (pattern.isNotEmpty()) {
            val patternMode = if (forceEnglish) {
                if (isLatinText(pattern)) {
                    PatternMode.ENGLISH_TO_JAPANESE
                } else {
                    Log.d(TAG, "ignore 'force English' toggle because text is not latin")
                    PatternMode.JAPANESE_TO_ENGLISH
                }
            } else {
                PatternMode.AUTO
            }
            result = jmdictDb.search(pattern, patternMode, SEARCH_RESULTS_LIMIT)
            Log.d(TAG, "new results: ${result.size}")
        }
        resultText = if (result.isEmpty()) {
            null
        } else {
            buildResultText(entries = result, colors = resultColors)
        }
    }

    // Handle initial or new query, set from intent, or empty at startup
    LaunchedEffect(currentInitialQuery) {
        Log.d(TAG, "initial query changed: ${initialQuery}")

        // If the query is empty, request focus to start typing
        // Otherwise, hide the keyboard to free more space for the results
        if (initialQuery == "") {
            searchFocusRequester.requestFocus()
        } else {
            forceEnglish = false
            queryState.setTextAndPlaceCursorAtEnd(initialQuery)
            searchResults()
            // Requesting focus then hiding the keyboard would be better, but it doesn't work as expected
            keyboardController?.hide()
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
                    queryState = queryState,
                    forceEnglish = forceEnglish,
                    onSearch = { searchResults() },
                    onForceEnglishChange = { forceEnglish = it },
                    focusRequester = searchFocusRequester,
                    navController = navController,
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            var inputModifier = Modifier
                .pointerInput(Unit) {
                    detectDoubleTapWithoutConsume {
                        searchFocusRequester.requestFocus()
                        // Display the keyboard even if the search already has the focus
                        keyboardController?.show()
                    }
                }
            if (resultText != null) {
                SelectionContainer {
                    Text(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxHeight()
                            .verticalScroll(resultScroll)
                            .then(inputModifier),
                        text = resultText!!,
                    )
                }
            } else {
                Text(
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .wrapContentSize(Alignment.Center)
                        .then(inputModifier),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    fontStyle = FontStyle.Italic,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize.times(1.5),
                    text = "no results",
                )
            }
        }
    }
}

@Composable
fun AppSearchBar(
    queryState: TextFieldState,
    forceEnglish: Boolean,
    onSearch: () -> Unit,
    onForceEnglishChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    navController: NavController,
) {
    var expanded = false  // Never expand for now

    SearchBar(
        modifier = Modifier
            .focusRequester(focusRequester)
            // The rounded search bar sticks to the sides by default; add some padding
            // Don't add padding at the top: there is already a visible gap
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .fillMaxWidth(),
        inputField = {
            SearchBarDefaults.InputField(
                state = queryState,
                onSearch = { onSearch() },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                placeholder = { Text("Translate...") },
                leadingIcon = { SearchMenuIcon(navController = navController) },
                trailingIcon = { SearchModeToggle(forceEnglish, onChange = onForceEnglishChange) },
            )
        },
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        // No suggestions
    }
}

@Composable
fun SearchModeToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = onChange,
    ) {
        if (checked) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Auto-detect input language")
        } else {
            Icon(Icons.Default.SwapHoriz, contentDescription = "Force English to Japanese")
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
            DropdownMenuItem(
                text = { Text("Help") },
                onClick = { navController.navigate("help") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) }
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


// PointerInputScope.detectTapGestures(


// Detect a double tap without consuming the initial tap
//
// This is similar to `PointerInputScope.detectTapGestures()` but does not
// consume the initial single tap. It makes it suitable for use within a
// `SelectionContainer`, which does not use double taps itself.
internal suspend fun PointerInputScope.detectDoubleTapWithoutConsume(onDoubleTap: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown()
        val firstUp = awaitTapUpOrCancel()
        if (firstUp != null) {
            val secondDown = awaitDoubleTapDownOrCancel(firstUp)
            if (secondDown != null) {
                val secondUp = awaitTapUpOrCancel()
                if (secondUp != null) {
                    secondUp.consume()
                    onDoubleTap()
                }
            }
        }
    }
}


// Wait for a "tap up". Cancel if pointer moved too much or event has been consumed.
internal suspend fun AwaitPointerEventScope.awaitTapUpOrCancel(): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.fastAny { it.isConsumed || it.isOutOfBounds(size, extendedTouchPadding) }) {
            return null  // canceled
        }
        if (event.changes.fastAll { it.changedToUp() }) {
            return event.changes[0]
        }
        // Cancel if event has been consumed later on the stack (e.g. due to position)
        val laterEvent = awaitPointerEvent(PointerEventPass.Final)
        if (laterEvent.changes.fastAny { it.isConsumed }) {
            return null
        }
    }
}


// Wait for a "tap down" with a "double tap" timeout
internal suspend fun AwaitPointerEventScope.awaitDoubleTapDownOrCancel(firstUp: PointerInputChange): PointerInputChange? =
    withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        // Wait for a minimum delay between first "up" and second "down"
        val minUptime = firstUp.uptimeMillis + viewConfiguration.doubleTapMinTimeMillis
        var change: PointerInputChange
        do {
            change = awaitFirstDown()
        } while (change.uptimeMillis < minUptime)
        change
    }

