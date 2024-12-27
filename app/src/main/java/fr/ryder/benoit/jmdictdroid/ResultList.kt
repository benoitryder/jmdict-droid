package fr.ryder.benoit.jmdictdroid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.ryder.benoit.jmdictdroid.ui.theme.ResultColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

class ResultStyles(colors: ResultColors) {
    val default = SpanStyle(fontSize = 16.sp)
    val jap = SpanStyle(color = colors.jap, fontSize = 20.sp)
    val senseNum = SpanStyle(color = colors.senseNum)
    val sensePos = SpanStyle(color = colors.sensePos)
    val senseAttr = SpanStyle(color = colors.senseAttr)
    val senseInfo = SpanStyle(fontStyle = FontStyle.Italic)
    val glossType = SpanStyle(color = colors.glossType)
}

@Composable
fun ResultList(
    entries: List<Jmdict.Entry>,
    state: LazyListState,
    // Called to load more items
    loadMoreItems: () -> Unit,
    // Number of items to the end below which more items will be loaded
    loadDistance: Int,
    styles: ResultStyles,
) {
    val needLoadMore = remember {
        derivedStateOf {
            // Load more items when approaching the end of the list
            val layoutInfo = state.layoutInfo
            val itemsCount = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex + loadDistance > itemsCount
        }
    }

    // Call `loadMoreItems()` when `needLoadMore` state changes to true
    LaunchedEffect(needLoadMore) {
        snapshotFlow { needLoadMore.value }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                loadMoreItems()
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        state = state,
    ) {
        items(entries, key = { it.id }) {
            Text(
                text = buildResultEntryText(it, styles),
            )
        }
    }
}

fun buildResultEntryText(entry: Jmdict.Entry, styles: ResultStyles): AnnotatedString {
    return buildAnnotatedString {
        withStyle(styles.default) {
            // Japanese text: kanji then reading
            withStyle(styles.jap) {
                if (entry.kanjis.isNotEmpty()) {
                    append(entry.kanjis.joinToString(", ") { it.text })
                    append(" / ")
                }
                append(entry.readings.joinToString(", ") { it.text })
                append("\n")
            }

            // Senses, one per line
            for ((iSense, sense) in entry.senses.withIndex()) {
                if (iSense != 0) {
                    append("\n")
                }

                // Sense number
                withStyle(styles.senseNum) {
                    append("${iSense + 1}) ")
                }

                // Part of speech
                if (sense.partOfSpeech.isNotEmpty()) {
                    withStyle(styles.sensePos) {
                        append(sense.partOfSpeech.joinToString(","))
                        append(" ")
                    }
                }

                // Attributes
                if (sense.fields.isNotEmpty() || sense.miscs.isNotEmpty()) {
                    withStyle(styles.senseAttr) {
                        append("[")
                        append((sense.fields.asSequence() + sense.miscs.asSequence()).joinToString(","))
                        append("] ")
                    }
                }

                // Informations
                if (sense.infos.isNotEmpty()) {
                    withStyle(styles.senseInfo) {
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
                        withStyle(styles.glossType) {
                            append("(${gloss.gtype}) ")
                        }
                    }
                    append(gloss.text)
                }
            }
        }
    }
}

