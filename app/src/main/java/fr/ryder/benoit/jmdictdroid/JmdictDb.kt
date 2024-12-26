@file:OptIn(ExperimentalSerializationApi::class)

package fr.ryder.benoit.jmdictdroid

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private val SQL_TABLE_NAMES = arrayOf("kanji", "reading", "sense", "gloss")

private val SQL_CREATE_STATEMENTS = arrayOf("""
    CREATE TABLE kanji (
      entry_id INT NOT NULL,
      text TEXT NOT NULL,
      data TEXT NOT NULL
    );
""", """
    CREATE TABLE reading (
      entry_id INT NOT NULL,
      text TEXT NOT NULL,
      romaji TEXT NOT NULL,
      data TEXT NOT NULL
    );
""", """
    CREATE TABLE sense (
      entry_id INT NOT NULL,
      sense_num INT NOT NULL,
      data TEXT NOT NULL,
      PRIMARY KEY (entry_id, sense_num)
    );
""", """
    CREATE TABLE gloss (
      entry_id INT NOT NULL,
      sense_num INT NOT NULL,
      text TEXT NOT NULL,
      gtype TEXT
    );
""")

private const val DB_NAME = "jmdict"


@Serializable
private class KanjiData(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val infos: List<String> = emptyList(),
)

@Serializable
private class ReadingData(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val infos: List<String> = emptyList(),
)

@Serializable
private class SenseData(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val pos: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fields: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val miscs: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val infos: List<String> = emptyList(),
)


private fun openJmdictDatabase(context: Context): SQLiteDatabase {
    return context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
}

private typealias ImportProgress = (String) -> Unit

enum class PatternMode {
  AUTO,
  JAPANESE_TO_ENGLISH,
  ENGLISH_TO_JAPANESE,
}

enum class PartOfSpeechFilter {
    ADJECTIVE,
    NOUN,
    VERB,
}

private class SearchArgs(
    sqlPattern: String,
    val posFilter: PartOfSpeechFilter?,
    var reverse: Boolean,
    val limit: Int,
) {
    val sqlPattern: String

    init {
        var pattern = sqlPattern
            .replace('*', '%')
            .replace('＊', '%')
            .replace('％', '%')
            .replace('?', '_')
            .replace('？', '_')
            .replace('＿', '_')
        if (pattern.all { it != '%' && it != '_' }) {
            pattern += '%'
        }
        this.sqlPattern = pattern
    }
}


/**
 * Manage the JMdict database
 *
 * All queries go through this method. This avoid messy concurrent accesses when database is recreated. 
 */
class JmdictDb(context: Context) {
    private val db = openJmdictDatabase(context)

    // Return true if the database is initialized with content
    fun isInitialized(): Boolean {
        return try {
            DatabaseUtils.queryNumEntries(db, "gloss") > 0
        } catch (_: SQLiteException) {
            false  // table does not exist
        }
    }

    // Import JMdict, recreate the database
    //
    // `progress` is an optional callback to 
    fun importJmdict(jmdict: Jmdict.Root, progress: ImportProgress) {
        // Don't reopen the database, work in place. It's easier to handle.
        db.beginTransaction()
        try {
            fillDatabase(jmdict, progress)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        Log.d(TAG, "vacuum database")
        progress("Finalize database...")
        db.execSQL("VACUUM")
        progress("Import complete")
    }

    // Fill database from JMdict data
    //
    // This method is expected to run in a transaction.
    private fun fillDatabase(root: Jmdict.Root, progress: ImportProgress) {
        // Recreate tables
        Log.d(TAG, "recreate tables")
        for (name in SQL_TABLE_NAMES) {
            db.execSQL("DROP TABLE IF EXISTS ${name}")
        }
        for (statement in SQL_CREATE_STATEMENTS) {
            db.execSQL(statement)
        }

        val stmtKanji = db.compileStatement("INSERT INTO kanji VALUES (?, ?, ?)")!!
        val stmtReading = db.compileStatement("INSERT INTO reading VALUES (?, ?, ?, ?)")!!
        val stmtSense = db.compileStatement("INSERT INTO sense VALUES (?, ?, ?)")!!
        val stmtGloss = db.compileStatement("INSERT INTO gloss VALUES (?, ?, ?, ?)")!!

        var currentEntry = 0
        val entryCount = root.entries.size

        for (entry in root.entries) {
            currentEntry += 1
            if (currentEntry % 100 == 0) {
                val percent = 100 * currentEntry.toFloat() / entryCount
                progress("Import entries: ${percent.roundToInt()}%")
            }
            if (currentEntry % 1000 == 0) {
                Log.d(TAG, "import entry ${currentEntry} / ${entryCount}")
            }

            stmtKanji.bindLong(1, entry.id)
            stmtReading.bindLong(1, entry.id)
            stmtSense.bindLong(1, entry.id)
            stmtGloss.bindLong(1, entry.id)

            for (kanji in entry.kanjis) {
                stmtKanji.bindString(2, kanji.text)
                stmtKanji.bindString(3, Json.encodeToString(KanjiData(kanji.infos)))
                stmtKanji.execute()
            }

            for (reading in entry.readings) {
                stmtReading.bindString(2, reading.text)
                stmtReading.bindString(3, kanaToRomaji(reading.text))
                stmtReading.bindString(4, Json.encodeToString(ReadingData(reading.infos)))
                stmtReading.execute()
            }

            for ((i, sense) in entry.senses.withIndex()) {
                stmtSense.bindLong(2, i.toLong())
                val data = SenseData(sense.partOfSpeech, sense.fields, sense.miscs, sense.infos)
                stmtSense.bindString(3, Json.encodeToString(data))
                stmtSense.execute()

                for (gloss in sense.glosses) {
                    stmtGloss.bindLong(2, i.toLong())
                    stmtGloss.bindString(3, gloss.text)
                    if (gloss.gtype == null) {
                      stmtGloss.bindNull(4)
                    } else {
                      stmtGloss.bindString(4, gloss.gtype)
                    }
                    stmtGloss.execute()
                }
            }
        }

        // Create indexes last, to avoid superfluous updates
        Log.d(TAG, "create database indexes")
        progress("Create indexes...")
        db.execSQL("CREATE INDEX k_entry ON kanji (entry_id);")
        db.execSQL("CREATE INDEX r_entry ON reading (entry_id);")
        db.execSQL("CREATE INDEX g_sense ON gloss (entry_id, sense_num);")
    }

    // Search in database, yield entries
    fun search(pattern: String, mode: PatternMode, limit: Int): List<Jmdict.Entry> {
        Log.d(TAG, "new search with pattern '${pattern}', mode ${mode}, limit ${limit}")

        // Separate additional markers if any; ignore unknowns
        var posFilter: PartOfSpeechFilter? = null
        var textPattern = pattern
        while (textPattern.contains(" +")) {
            when (textPattern.substringAfterLast(" +")) {
                "adj" -> posFilter = PartOfSpeechFilter.ADJECTIVE
                "n" -> posFilter = PartOfSpeechFilter.NOUN
                "v" -> posFilter = PartOfSpeechFilter.VERB
                else -> null
            }
            textPattern = textPattern.substringBeforeLast(" +")
        }

        // Guess "actual" mode from pattern
        // Keep value set to `AUTO` if not sure yet
        var retryReverse = false
        val reverse = when (mode) {
            PatternMode.JAPANESE_TO_ENGLISH -> false
            PatternMode.ENGLISH_TO_JAPANESE -> true
            PatternMode.AUTO -> {
                if (textPattern.startsWith("/")) {
                    textPattern = textPattern.substring(1)
                    true
                } else if (!isLatinText(textPattern)) {
                    false
                } else {
                    retryReverse = true
                    false
                }
            }
        }
        assert(!(reverse && retryReverse))

        if (reverse && textPattern.startsWith("/")) {
            textPattern = textPattern.substring(1)
        }

        val searchArgs = SearchArgs(
            sqlPattern = textPattern,
            posFilter = posFilter,
            reverse = reverse,
            limit = limit,
        )
        Log.d(TAG, "translated SQL pattern: '${searchArgs.sqlPattern}")

        var entryIds = searchFromArgs(searchArgs)
        if (retryReverse && entryIds.isEmpty()) {
            searchArgs.reverse = true
            Log.d(TAG, "re-try auto pattern in reverse")
            entryIds = searchFromArgs(searchArgs)
        }

        return collectEntries(entryIds)
    }

    // Search for an already translated SQL pattern, return a list of matching entry IDs
    private fun searchFromArgs(args: SearchArgs): List<Long> {
        // Build the SQL query from individual internal pieces
        val buildQuery: (String, String, String) -> String = { table, whereExpr, order ->
            var tables = "${table} f"
            var whereExpr = whereExpr
            if (args.posFilter != null) {
                val posFilter = when (args.posFilter) {
                    PartOfSpeechFilter.ADJECTIVE -> "pos.value LIKE 'adj%'"
                    PartOfSpeechFilter.NOUN -> "(pos.value = 'n' OR pos.value LIKE 'n-%')"
                    // Exclude "suru" verbs, and vt/vi (they always come along with another one)
                    PartOfSpeechFilter.VERB -> "(pos.value LIKE 'v%' AND pos.value NOT IN ('vi', 'vt') AND pos.value NOT LIKE 'vs%')"
                }
                tables += ", sense s, json_each(s.data, '$.pos') AS pos"
                whereExpr += " AND s.entry_id = f.entry_id AND ${posFilter}"
            }
            "SELECT DISTINCT f.entry_id FROM ${tables} WHERE ${whereExpr} ORDER BY ${order} LIMIT ${args.limit}"
        }

        var query = if (args.reverse) {
            // English to Japanese
            buildQuery("gloss", "text LIKE ?1", "length(f.text)")
        } else if (isLatinText(args.sqlPattern)) {
            // Romaji to English
            buildQuery("reading", "romaji LIKE ?1", "length(f.romaji)")
        } else if (args.sqlPattern.codePoints().allMatch { it == '%'.code || it == '_'.code || isKanaChar(it) }) {
            // Kanji/kana to English
            val union = 
                ("SELECT DISTINCT entry_id, length(text) AS o FROM kanji WHERE text LIKE ?1"
                + " UNION "
                + "SELECT DISTINCT entry_id, length(text) AS o FROM reading WHERE text LIKE ?1")
            // Avoid a subquery to order directly from the UNION, when possible to improve execution time.
            if (args.posFilter == null) {
                "${union} ORDER BY o LIMIT ${args.limit}"
            } else {
                buildQuery("(${union})", "1", "f.o")
            }
        } else {
            // Kanji to English
            buildQuery("kanji", "text LIKE ?1", "length(f.text)")
        }
        Log.d(TAG, "sql query: ${query}")
        Log.d(TAG, "sql pattern: ${args.sqlPattern}")

        val entryIds = arrayListOf<Long>()
        db.rawQuery(query, arrayOf(args.sqlPattern)).use { cursor ->
            while (cursor.moveToNext()) {
                entryIds.add(cursor.getLong(0))
            }
        }

        return entryIds
    }

    private fun collectEntries(entryIds: List<Long>): List<Jmdict.Entry> {
        // The result order is provided by `entryIds`, which may contain duplicates
        // Iterate on the `entries` map to build `entryIdsArray` to avoid the duplicates
        val entries: LinkedHashMap<Long, Jmdict.Entry> = LinkedHashMap(entryIds.size)
        for (id in entryIds) {
            entries[id] = Jmdict.Entry(id, arrayListOf(), arrayListOf(), arrayListOf())
        }
        val sqlEntryIds = entries.keys.joinToString(",")

        // Fill the structures with `MutableList`.
        // Classes expose a (readonly) `List` but a cast is safe in our case.

        // Kanjis
        db.rawQuery("SELECT entry_id, text, data FROM kanji WHERE entry_id IN (${sqlEntryIds})", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val text = cursor.getString(1)
                val data = Json.decodeFromString<KanjiData>(cursor.getString(2))
                val item = Jmdict.KanjiElement(text, data.infos)
                (entries[id]!!.kanjis as MutableList).add(item)
            }
        }

        // Readings
        db.rawQuery("SELECT entry_id, text, data FROM reading WHERE entry_id IN (${sqlEntryIds})", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val text = cursor.getString(1)
                val data = Json.decodeFromString<ReadingData>(cursor.getString(2))
                val item = Jmdict.ReadingElement(text, data.infos)
                (entries[id]!!.readings as MutableList).add(item)
            }
        }

        // Senses
        db.rawQuery("SELECT entry_id, sense_num, data FROM sense WHERE entry_id IN (${sqlEntryIds}) ORDER BY entry_id, sense_num", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val num = cursor.getInt(1)
                val data = Json.decodeFromString<SenseData>(cursor.getString(2))
                val entry = entries[id]!!
                assert(num == entry.senses.size)  // ensured by import
                val item = Jmdict.Sense(data.pos, data.fields, data.miscs, data.infos, arrayListOf())
                (entry.senses as MutableList).add(item)
            }
        }

        // Glosses
        db.rawQuery("SELECT entry_id, sense_num, text, gtype FROM gloss WHERE entry_id IN (${sqlEntryIds}) ORDER BY entry_id, sense_num", emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val num = cursor.getInt(1)
                val text = cursor.getString(2)
                val gtype = if (cursor.isNull(3)) null else cursor.getString(3)
                val item = Jmdict.Gloss(text, gtype)
                (entries[id]!!.senses[num].glosses as MutableList).add(item)
            }
        }

        return entries.values.toList()
    }
}

