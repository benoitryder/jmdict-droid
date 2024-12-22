@file:OptIn(ExperimentalMaterial3Api::class)

package fr.ryder.benoit.jmdictdroid

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.ryder.benoit.jmdictdroid.ui.theme.themeResultColors

private const val SEARCH_RESULTS_LIMIT = 100

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun MainScreen(navController: NavController, jmdictDb: JmdictDb, initialQuery: String) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val resultStyles = ResultStyles(themeResultColors())

    val currentInitialQuery by rememberUpdatedState(initialQuery)
    val queryState = rememberTextFieldState()
    var forceEnglish by remember { mutableStateOf(false) }
    // The result list is always fully replaced, mutableStateListOf() is not needed
    var resultListItems by remember { mutableStateOf(emptyList<Jmdict.Entry>()) }
    val resultListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    var bottomBarVisible by remember { mutableStateOf(false) }

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
        resultListItems = result
        bottomBarVisible = false
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
    LaunchedEffect(resultListItems) {
        resultListState.scrollToItem(0)
    }

    // Hide bottom bar whenever the IME state changes
    LaunchedEffect(WindowInsets.isImeVisible) {
        bottomBarVisible = false
    }

    // Helper to let the user type a search
    fun focusSearchWithKeyboard() {
        searchFocusRequester.requestFocus()
        keyboardController?.show()
        bottomBarVisible = false
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(bottomBarVisible) {
                        if (bottomBarVisible) {
                            // Same handler in result box
                            awaitEachGesture {
                                awaitFirstDown(pass = PointerEventPass.Initial)
                                bottomBarVisible = false
                            }
                        }
                    },
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
        bottomBar = {
            if (bottomBarVisible) {
                BottomAppBar(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = {
                            queryState.clearText()
                            focusSearchWithKeyboard()
                        },
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = "New search")
                    }
                    IconButton(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onClick = { focusSearchWithKeyboard() },
                    ) {
                        Icon(Icons.Filled.Keyboard, contentDescription = "Edit search")
                    }
                    // Placeholder, does nothing for now
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "empty",
                        modifier = Modifier.alpha(0f).fillMaxWidth().weight(1f),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(bottomBarVisible) {
                    // If the bottom bar is visible, hide it on any touch (including scrolling).
                    // Otherwise, display the bottom bar on a simple touch.
                    // Caveats
                    // - Hiding the bottom has to be handled on both the result text and the top
                    //   bar, because there is no easy way to handle all touches except those on the
                    //   bottom bar.
                    // - Touching when a selection is active should not show/hide the bottom bar,
                    //   but there is now way to get access to the selection state.
                    if (bottomBarVisible) {
                        // Same handler in topBar
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            bottomBarVisible = false
                        }
                    } else {
                        detectTapWithoutConsume {
                            bottomBarVisible = true
                        }
                    }
                }
    ) {
            if (resultListItems.isNotEmpty()) {
                SelectionContainer {
                    ResultList(resultListItems, resultListState, resultStyles)
                }
            } else {
                Text(
                    modifier = Modifier
                        .padding(vertical = 24.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .wrapContentSize(Alignment.Center),
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


// Detect a tap without consuming it
//
// This is similar to `PointerInputScope.detectTapGestures()` but does not
// consume the tap. It makes it suitable for use within a `SelectionContainer`.
internal suspend fun PointerInputScope.detectTapWithoutConsume(onTap: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown()
        val firstUp = waitForUpOrCancellation()
        if (firstUp != null) {
            onTap()
        }
    }
}

