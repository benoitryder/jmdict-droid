package fr.ryder.benoit.jmdictdroid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = {
                    Text("Help")
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
			HelpContent()
        }
    }
}

@Composable
fun HelpContent() {
    val titleParagraphStyle = ParagraphStyle(lineHeight = 20.sp)
    val titleSpanStyle = SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace)
    val emphStyle = SpanStyle(fontStyle = FontStyle.Italic)

    fun AnnotatedString.Builder.appendTitle(text: String) {
        withStyle(titleParagraphStyle) {
            withStyle(titleSpanStyle) {
                append(text)
            }
        }
    }

    fun AnnotatedString.Builder.appendCode(text: String) {
        append(" ")
        withStyle(codeStyle) {
            append(text)
        }
        append(" ")
    }

    fun AnnotatedString.Builder.appendEmph(text: String) {
        withStyle(emphStyle) {
            append(text)
        }
    }

    Text(
        modifier = Modifier.padding(12.dp),
        text = buildAnnotatedString {
            appendTitle("Search pattern")
            append("Both Japanese and rōmaji can be used, but they cannot be mixed. Using rōmaji will search entries based on their pronunciation.\n")
            append("The following wildcards can be used, both with Japanese and rōmaji:\n")
            append("\t\t"); appendCode("*"); append(" and "); appendCode("%"); append(" match any number of characters\n")
            append("\t\t"); appendCode("_"); append(" and "); appendCode("?"); append(" match a single character\n")
            append("If no wildcard is used, return all entries starting with the provided pattern.\n")

            appendTitle("Search direction")
            append("Search will attempt to guess if the input is a Japanese word to be translated to English, or the reverse.\n")
            append("If latin text does not return any result when searched as rōmaji, it will search for an English word.\n")
            append("Use the "); appendInlineContent("iconReverse", "[reverse]"); append(" button or start the pattern with "); appendCode("/"); append(" to force the search of an English word when latin text is input.\n")

            appendTitle("Rōmaji conversion")
            append("Conversion is based on Hepburn romanization.\n")
            append("Kanas are converted directly, without special cases (は used as a particle, ん is always "); appendEmph("n"); append("...)\n")
            append("For long vowels:\n")
            append("\t\tー repeats the previous vowel (ロー is "); appendEmph("roo"); append(")\n")
            append("\t\tう/い are converted to "); appendEmph("u"); append("/"); appendEmph("i"); append("\n")
            append("Apostrophes are not used.\n")
        },
		inlineContent = mapOf(
			Pair("iconReverse", InlineTextContent(
				Placeholder(width = 12.sp, height = 12.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
			) {
				Icon(Icons.Filled.SwapHoriz, "swap")
			}),
		),
    )
}
