package fr.ryder.benoit.jmdictdroid


// Return `true` if a string contains only Latin characters
fun isLatinText(text: String): Boolean {
    return text.codePoints().allMatch { isLatinChar(it) }
}

// Return `true` if a character looks Latin
//
// But it should not cause conflict with character actually used with English,
// including emojis. Check does not check specifically for Japanese.
fun isLatinChar(code: Int): Boolean {
    return !(
           code in  0x3000.. 0x9FFF  // kanas, punctuation, CJK
        || code in  0xF900.. 0xFFEF  // half-width kanas, more CJK
        || code in 0x20000..0x3FFFF  // more CJK
    )
}

// Return `true` if a character is a kana character
fun isKanaChar(code: Int): Boolean {
    return (
           code in  0x3000.. 0x30FF  // kanas, punctuation and symbols
        || code in  0xFF00.. 0xFFEF  // half-width kanas
    )
}


private object RomajiConverter {
    // Static tables for replacement
    // Item order is important
    val CONVERSION_TABLE: LinkedHashMap<String, String> = linkedMapOf(
        // Hiragana
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja",  "じゅ" to "ju",  "じょ" to "jo",
        "ぢゃ" to "ja",  "ぢゅ" to "ju",  "ぢょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "あ" to "a",  "い" to "i",   "う" to "u",   "え" to "e",  "お" to "o",
        "か" to "ka", "き" to "ki",  "く" to "ku",  "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su",  "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni",  "ぬ" to "nu",  "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi",  "ふ" to "fu",  "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi",  "む" to "mu",  "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu",  "よ" to "yo",
        "ら" to "ra", "り" to "ri",  "る" to "ru",  "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "ゐ" to "wi",  "ゑ" to "we",  "を" to "wo",
        "ん" to "n",
        "が" to "ga", "ぎ" to "gi",  "ぐ" to "gu",  "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji",  "ず" to "zu",  "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji",  "づ" to "zu",  "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi",  "ぶ" to "bu",  "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi",  "ぷ" to "pu",  "ぺ" to "pe", "ぽ" to "po",
        "ぁ" to "a",  "ぃ" to "i",   "ぅ" to "u",   "ぇ" to "e",  "ぉ" to "o",
        // Katakana (same as hiragana)
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja",  "ジュ" to "ju",  "ジョ" to "jo",
        "ヂャ" to "ja",  "ヂュ" to "ju",  "ヂョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
        "ア" to "a",  "イ" to "i",   "ウ" to "u",   "エ" to "e",  "オ" to "o",
        "カ" to "ka", "キ" to "ki",  "ク" to "ku",  "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su",  "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni",  "ヌ" to "nu",  "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi",  "フ" to "fu",  "ヘ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi",  "ム" to "mu",  "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu",  "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri",  "ル" to "ru",  "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヰ" to "wi",  "ヱ" to "we",  "ヲ" to "wo",
        "ン" to "n",
        "ガ" to "ga", "ギ" to "gi",  "グ" to "gu",  "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji",  "ズ" to "zu",  "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji",  "ヅ" to "zu",  "デ" to "de", "ド" to "do",
        "バ" to "ba", "ビ" to "bi",  "ブ" to "bu",  "ベ" to "be", "ボ" to "bo",
        "パ" to "pa", "ピ" to "pi",  "プ" to "pu",  "ペ" to "pe", "ポ" to "po",
        "ァ" to "a",  "ィ" to "i",   "ゥ" to "u",   "ェ" to "e",  "ォ" to "o",
        // Katakana (extra)
        "イェ" to "ye",
        "ヴァ" to "va",  "ヴィ" to "vi",  "ヴェ" to "ve",  "ヴォ" to "vo",
        "ヴャ" to "vya", "ヴュ" to "vyu", "ヴョ" to "vyo",
        "ブュ" to "byu",
        "シェ" to "she", "ジェ" to "je",
        "チェ" to "che",
        "スィ" to "si",  "ズィ" to "zi",
        "ティ" to "ti",  "トゥ" to "tu",  "テュ" to "tyu", "ドュ" to "dyu",
        "ディ" to "di",  "ドゥ" to "du",  "デュ" to "dyu",
        "ツァ" to "tsa", "ツィ" to "tsi", "ツェ" to "tse", "ツォ" to "tso",
        "ファ" to "fa",  "フィ" to "fi",  "ホゥ" to "hu",  "フェ" to "fe",  "フォ" to "fo",   "フュ" to "fyu",
        "ウィ" to "wi",  "ウェ" to "we",  "ウォ" to "wo",
        "クヮ" to "kwa", "クァ" to "kwa", "クィ" to "kwi", "クェ" to "kwe", "クォ" to "kwo",
        "グヮ" to "gwa", "グァ" to "gwa", "グィ" to "gwi", "グェ" to "gwe", "グォ" to "gwo",
        "ヴ" to "vu",
        // Symbols
        "〜" to "~", "。" to ".", "、" to ",", "　" to " ",
    )

    val RE = Regex(CONVERSION_TABLE.keys.joinToString("|"))
    val RE_CONSONANT = Regex("[っッ]([bcdfghjkmnprstvwz])")
    val RE_VOWEL = Regex("([aeiou])ー")
    val RE_DASH = Regex("[っッ]")
    val RE_TSU = Regex("[っッ]")
    val RE_FW_ASCII = Regex("[\\uff00-\\uff5e]")
}

// Convert kana to romaji
fun kanaToRomaji(text: String): String {
    // Note: it's better to run replacements that reduce the string length and size first 
    return text
        .replace(RomajiConverter.RE) { m -> RomajiConverter.CONVERSION_TABLE[m.value]!! }
        .replace(RomajiConverter.RE_CONSONANT, "$1$1")
        .replace(RomajiConverter.RE_VOWEL, "$1$1")
        .replace(RomajiConverter.RE_DASH, "-")
        .replace(RomajiConverter.RE_TSU, "-tsu")
        // Replace full-width ASCII characters
        .replace(RomajiConverter.RE_FW_ASCII) { m -> (m.value[0] - 0xfee0).toString() }
}

