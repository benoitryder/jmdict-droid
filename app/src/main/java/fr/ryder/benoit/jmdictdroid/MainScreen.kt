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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

private const val SEARCH_RESULTS_PAGE = 100
private const val SEARCH_RESULTS_LOAD_DISTANCE = SEARCH_RESULTS_PAGE / 2


@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun MainScreen(navController: NavController, jmdictDb: JmdictDb, initialQuery: String) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val resultStyles = ResultStyles(themeResultColors())

    val currentInitialQuery by rememberUpdatedState(initialQuery)
    val searchPatternState = rememberTextFieldState()
    var forceEnglish by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf<SearchQuery?>(null) }
    var posFilter by remember { mutableStateOf<PartOfSpeechFilter?>(null) }
    // Offset for the next call to `loadMoreResults()`, -1 if end has been reached already
    var searchNextOffset by remember { mutableIntStateOf(0) }
    var resultListItems = remember { mutableStateListOf<Jmdict.Entry>() }
    val resultListState = rememberLazyListState()

    var searchComposed by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var bottomBarVisible by remember { mutableStateOf(false) }

    // Load more items into the existing results
    fun loadMoreResults() {
        assert(searchQuery != null)
        if (searchQuery == null || searchNextOffset == -1) {
            return
        }
        val newResults = jmdictDb.search(searchQuery!!, SEARCH_RESULTS_PAGE, searchNextOffset)
        if (newResults.size == SEARCH_RESULTS_PAGE) {
            searchNextOffset += SEARCH_RESULTS_PAGE
        } else {
            searchNextOffset = -1
        }
        resultListItems.addAll(newResults)
    }

    // Focus the search bar, only if composed, to avoid "FocusRequester is not initialized" error
    fun focusSearch() {
        if (searchComposed) {
            searchFocusRequester.requestFocus()
        }
    }

    // Helper to let the user type a search
    fun focusSearchWithKeyboard() {
        focusSearch()
        keyboardController?.show()
        bottomBarVisible = false
    }

    // Run search using a new query, collect the first page of results
    fun searchResults() {
        val pattern = searchPatternState.text.trim().toString()
        Log.d(TAG, "new search pattern: '${pattern}'")
        searchQuery = null
        searchNextOffset = 0
        resultListItems.clear()
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
            searchQuery = SearchQuery(pattern, patternMode)
            if (posFilter != null) {
                searchQuery!!.posFilter = posFilter
            }
            loadMoreResults()
        }
        bottomBarVisible = false
    }

    // Handle initial or new query, set from intent, or empty at startup
    LaunchedEffect(currentInitialQuery) {
        Log.d(TAG, "initial query changed: ${initialQuery}")

        // If the query is empty, request focus to start typing
        // Otherwise, hide the keyboard to free more space for the results
        if (initialQuery == "") {
            focusSearch()
        } else {
            forceEnglish = false
            searchPatternState.setTextAndPlaceCursorAtEnd(initialQuery)
            searchResults()
            // Requesting focus then hiding the keyboard would be better, but it doesn't work as expected
            keyboardController?.hide()
        }
    }

    // Reset scroll when a new pattern is used (more convenient than watching the result list)
    LaunchedEffect(searchQuery) {
        resultListState.scrollToItem(0)
    }

    // Hide bottom bar whenever the IME state changes
    LaunchedEffect(WindowInsets.isImeVisible) {
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
                    searchPatternState = searchPatternState,
                    forceEnglish = forceEnglish,
                    onSearch = { searchResults() },
                    onForceEnglishChange = { forceEnglish = it },
                    focusRequester = searchFocusRequester,
                    navController = navController,
                )
                searchComposed = true
            }
        },
        bottomBar = {
            if (bottomBarVisible) {
                BottomActionBar(
                    selectedFilter = posFilter,
                    onFocusSearch = { clear ->
                        if (clear) {
                            posFilter = null
                            searchPatternState.clearText()
                        }
                        focusSearchWithKeyboard()
                    },
                    onFilter = { filter ->
                        Log.d(TAG, "set pos filter: ${filter}")
                        posFilter = filter
                        searchResults()
                    },
                )
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
                    ResultList(
                        entries = resultListItems,
                        state = resultListState,
                        loadMoreItems = { loadMoreResults() },
                        loadDistance = SEARCH_RESULTS_LOAD_DISTANCE,
                        styles = resultStyles,
                    )
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
    searchPatternState: TextFieldState,
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
                state = searchPatternState,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomActionBar(
    selectedFilter: PartOfSpeechFilter?,
    // Called on action to focus the search, parameter is `true` to also clear the search
    onFocusSearch: (Boolean) -> Unit,
    // Called when changing the part-of-speech filter
    onFilter: (PartOfSpeechFilter?) -> Unit,
) {
    val allFilters = arrayOf(
        Pair("Adjectives", PartOfSpeechFilter.ADJECTIVE),
        Pair("Nouns", PartOfSpeechFilter.NOUN),
        Pair("Verbs", PartOfSpeechFilter.VERB),
    )

    var filtersExpanded by remember { mutableStateOf(false) }
    BottomAppBar(
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(
            modifier = Modifier.fillMaxWidth().weight(1f),
            onClick = { onFocusSearch(true) }
        ) {
            Icon(Icons.Filled.Clear, contentDescription = "New search")
        }
        IconButton(
            modifier = Modifier.fillMaxWidth().weight(1f),
            onClick = { onFocusSearch(false) }
        ) {
            Icon(Icons.Filled.Keyboard, contentDescription = "Edit search")
        }
        IconButton(
            modifier = Modifier.fillMaxWidth().weight(1f),
            onClick = { filtersExpanded = !filtersExpanded },
        ) {
            Icon(Icons.Filled.FilterAlt, contentDescription = "Filters")
            DropdownMenu(
                expanded = filtersExpanded,
                onDismissRequest = { filtersExpanded = false },
            ) {
                allFilters.forEach { (text, filter) ->
                    val selected = filter == selectedFilter
                    DropdownMenuItem(
                        text = { Text(text) },
                        trailingIcon = { if (selected) Icon(Icons.Filled.Check, contentDescription = null) else null },
                        onClick = { onFilter(if (selected) null else filter) },
                    )
                }
            }
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
