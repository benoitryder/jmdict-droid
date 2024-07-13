package fr.ryder.benoit.jmdictdroid

import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object Jmdict {
    class Root(
        val entries: List<Entry>,
    )

    class Entry(
        val id: Long,
        val kanjis: List<KanjiElement>,
        val readings: List<ReadingElement>,
        val senses: List<Sense>,
    )

    class KanjiElement(
        val text: String,
        val infos: List<String>,
    )

    class ReadingElement(
        val text: String,
        val infos: List<String>,
    )

    class Sense(
        val partOfSpeech: List<String>,
        val fields: List<String>,
        val miscs: List<String>,
        val infos: List<String>,
        val glosses: List<Gloss>,
    )

    class Gloss(
        val text: String,
        val gtype: String?,
    )

    // Download and parse JMdict
    @JvmStatic fun parseUrl(urlString: String): Root {
        val stream = downloadUrl(urlString) ?: throw RuntimeException("failed to download JMdict: ${urlString}")
        return parseXmlStream(stream)
    }

    // Parse JMdict from XML content 
    @JvmStatic fun parseXmlStream(inputStream: InputStream): Root {
        inputStream.use {
            Log.d(TAG, "create parser")
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            // Don't process DOCDECL: we don't want to replace entities
            // Actually... we want to keep the entity name. We should be able
            // to handel the ENTITY_REF token, but implementation does not
            // match the documentation and we have no access to the raw buffer
            // with the entity name. As a workaround: disable DOCDECL handling
            // and parse its content manually to define entities.
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            parser.setInput(inputStream, "UTF-8")

            // Skip tokens until reaching the root element
            while (true) {
                val token = parser.nextToken()
                if (token == XmlPullParser.START_TAG) {
                    break
                } else if (token == XmlPullParser.DOCDECL) {
                    Log.d(TAG, "DOCDECL found, declare entities")
                    for (m in Regex("<!ENTITY ([^ ]+)").findAll(parser.text)) {
                        val name = m.groupValues[1]
                        parser.defineEntityReplacementText(name, name)
                    }
                }
            }

            Log.d(TAG, "start root parsing")
            val result = readRoot(parser)
            Log.d(TAG, "parsing complete")
            return result
        }
    }

    // Download and parse JMdict from a URL
    @JvmStatic private fun downloadUrl(urlString: String): InputStream? {
        val url = URL(urlString)
        return (url.openConnection() as? HttpURLConnection)?.run {
            requestMethod = "GET"
            doInput = true
            // Disable GZip: content is already compressed
            setRequestProperty("Accept-Encoding", "identity")
            connect()
            var stream = inputStream
            if (contentType == "application/x-gzip") {
                stream = GZIPInputStream(stream)
            }
            stream
        }
    }

    @JvmStatic private fun readRoot(parser: XmlPullParser): Root {
        parser.require(XmlPullParser.START_TAG, null, "JMdict")

        // Add some logs to check progress during debug
        var lastLoggedLine = 0
        val entries = mutableListOf<Entry>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                throw RuntimeException("unexpected EOF")
            }
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }

            if (parser.lineNumber >= lastLoggedLine + 100000) {
                lastLoggedLine = parser.lineNumber
                Log.d(TAG, "parsing line ${lastLoggedLine}")
            }

            // Starts by looking for the entry tag.
            if (parser.name == "entry") {
                entries.add(readEntry(parser))
            } else {
                skipTag(parser)
            }
        }
        return Root(entries)
    }

    @JvmStatic private fun readEntry(parser: XmlPullParser): Entry {
        parser.require(XmlPullParser.START_TAG, null, "entry")

        var id: Long? = null
        val kanjis = mutableListOf<KanjiElement>()
        val readings = mutableListOf<ReadingElement>()
        val senses = mutableListOf<Sense>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                throw RuntimeException("unexpected EOF")
            }
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "ent_seq" -> id = readTextTag(parser).toLong()
                "k_ele" -> kanjis.add(readKanjiElement(parser))
                "r_ele" -> readings.add(readReadingElement(parser))
                "sense" -> senses.add(readSense(parser))
                else -> skipTag(parser)
            }
        }
        return Entry(id!!, kanjis, readings, senses)
    }

    @JvmStatic private fun readKanjiElement(parser: XmlPullParser): KanjiElement {
        parser.require(XmlPullParser.START_TAG, null, "k_ele")

        var text: String? = null
        val infos = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                throw RuntimeException("unexpected EOF")
            }
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "keb" -> text = readTextTag(parser)
                "ke_inf" -> infos.add(readEntityTag(parser))
                else -> skipTag(parser)
            }
        }
        return KanjiElement(text!!, infos)
    }

    @JvmStatic private fun readReadingElement(parser: XmlPullParser): ReadingElement {
        parser.require(XmlPullParser.START_TAG, null, "r_ele")

        var text: String? = null
        val infos = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                throw RuntimeException("unexpected EOF")
            }
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "reb" -> text = readTextTag(parser)
                "re_inf" -> infos.add(readEntityTag(parser))
                else -> skipTag(parser)
            }
        }
        return ReadingElement(text!!, infos)
    }

    @JvmStatic private fun readSense(parser: XmlPullParser): Sense {
        parser.require(XmlPullParser.START_TAG, null, "sense")

        val partOfSpeech = mutableListOf<String>()
        val fields = mutableListOf<String>()
        val misc = mutableListOf<String>()
        val infos = mutableListOf<String>()
        val glosses = mutableListOf<Gloss>()
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) {
                throw RuntimeException("unexpected EOF")
            }
            if (parser.eventType != XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                "pos" -> partOfSpeech.add(readEntityTag(parser))
                "field" -> fields.add(readEntityTag(parser))
                "misc" -> misc.add(readEntityTag(parser))
                "s_inf" -> infos.add(readTextTag(parser))
                "gloss" -> glosses.add(readGloss(parser))
                else -> skipTag(parser)
            }
        }
        return Sense(partOfSpeech, fields, misc, infos, glosses)
    }

    @JvmStatic private fun readGloss(parser: XmlPullParser): Gloss {
        parser.require(XmlPullParser.START_TAG, null, "gloss")

        val gtype = parser.getAttributeValue(null, "g_type")
        val text = readTextTag(parser)
        return Gloss(text, gtype)
    }

    @JvmStatic private fun skipTag(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.nextToken()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    @JvmStatic private fun readTextTag(parser: XmlPullParser): String {
        if (parser.next() != XmlPullParser.TEXT) {
            throw RuntimeException("unexpected token (expected text)")
        }
        val result = parser.text
        if (parser.nextTag() != XmlPullParser.END_TAG) {
            throw RuntimeException("unexpected token (expected tag end)")
        }
        return result
    }

    @JvmStatic private fun readEntityTag(parser: XmlPullParser): String {
        // Entities have been converted, process them as text
        // Keep the methods separated in case implementation change
        return readTextTag(parser)
    }
}

