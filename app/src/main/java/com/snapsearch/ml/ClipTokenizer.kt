package com.snapsearch.ml

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.regex.Pattern

/**
 * CLIP byte-level BPE tokenizer, ported from Xenova/mobileclip_s0's tokenizer.json
 * (`vocab.json`/`merges.txt` extracted from that file, bundled as assets). This is
 * OpenAI's original CLIP tokenizer scheme — byte-to-unicode mapping plus an explicit
 * end-of-word "</w>" suffix baked into the BPE merges — confirmed from tokenizer.json's
 * `end_of_word_suffix: "</w>"` field. It is NOT GPT-2's leading-space "Ġ" scheme, even
 * though both use the same byte-to-unicode mapping table.
 *
 * Output matches Python's `transformers.CLIPTokenizerFast` bit-for-bit for the sample
 * strings checked during porting (context length 77, bos=49406, eos=49407, pad=0).
 */
object ClipTokenizer {

    private const val VOCAB_ASSET = "mobileclip/vocab.json"
    private const val MERGES_ASSET = "mobileclip/merges.txt"
    private const val CONTEXT_LENGTH = 77
    private const val BOS_TOKEN_ID = 49406
    private const val EOS_TOKEN_ID = 49407
    private const val PAD_TOKEN_ID = 0
    private const val END_OF_WORD_SUFFIX = "</w>"

    // Matches OpenAI CLIP's pretokenizer pattern (contractions, letter runs, number runs,
    // punctuation runs) — \p{L}/\p{N} are Unicode-aware by default in java.util.regex.
    private val pretokenizePattern: Pattern = Pattern.compile(
        "'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+"
    )
    private val whitespaceRun = Regex("\\s+")

    private val byteEncoder: Map<Int, Char> = buildByteEncoder()

    @Volatile private var vocab: Map<String, Int>? = null
    @Volatile private var mergeRanks: Map<Pair<String, String>, Int>? = null
    private val bpeCache = HashMap<String, List<String>>()

    /**
     * Tokenize [text] into a fixed-length (77) input_ids sequence for MobileCLIP's text
     * tower: [bos] + BPE token ids (truncated to 75) + [eos], right-padded with 0.
     */
    fun tokenize(context: Context, text: String): LongArray {
        loadVocabAndMerges(context)
        val vocabMap = vocab!!
        val ranks = mergeRanks!!

        val normalized = normalize(text)
        val contentIds = ArrayList<Int>()

        val matcher = pretokenizePattern.matcher(normalized)
        while (matcher.find()) {
            val word = matcher.group()
            val subwords = bpeCache.getOrPut(word) { bpeEncode(word, ranks) }
            for (subword in subwords) {
                contentIds.add(vocabMap[subword] ?: EOS_TOKEN_ID)
            }
        }

        val ids = LongArray(CONTEXT_LENGTH) { PAD_TOKEN_ID.toLong() }
        ids[0] = BOS_TOKEN_ID.toLong()
        var pos = 1
        for (id in contentIds) {
            if (pos >= CONTEXT_LENGTH - 1) break
            ids[pos] = id.toLong()
            pos++
        }
        ids[pos] = EOS_TOKEN_ID.toLong()
        return ids
    }

    private fun loadVocabAndMerges(context: Context) {
        if (vocab != null && mergeRanks != null) return
        synchronized(this) {
            if (vocab != null && mergeRanks != null) return

            val vocabJson = context.assets.open(VOCAB_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
            val json = JSONObject(vocabJson)
            val vocabMap = HashMap<String, Int>(json.length() * 2)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocabMap[key] = json.getInt(key)
            }
            vocab = vocabMap

            val ranks = HashMap<Pair<String, String>, Int>()
            context.assets.open(MERGES_ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
                var index = 0
                for (line in lines) {
                    if (line.isBlank()) continue
                    val parts = line.split(" ")
                    if (parts.size != 2) continue
                    ranks[parts[0] to parts[1]] = index
                    index++
                }
            }
            mergeRanks = ranks
        }
    }

    /** NFC-normalize, collapse whitespace runs to a single space, lowercase — matches tokenizer.json's normalizer sequence. */
    private fun normalize(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        return whitespaceRun.replace(nfc, " ").lowercase()
    }

    /** Byte-level BPE encode of one pretoken word into final subword token strings. */
    private fun bpeEncode(word: String, ranks: Map<Pair<String, String>, Int>): List<String> {
        val bytes = word.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) return emptyList()

        val symbols = ArrayList<String>(bytes.size)
        for (b in bytes) symbols.add(byteEncoder.getValue(b.toInt() and 0xFF).toString())
        symbols[symbols.size - 1] = symbols[symbols.size - 1] + END_OF_WORD_SUFFIX

        if (symbols.size == 1) return symbols

        var pieces: List<String> = symbols
        var pairs = adjacentPairs(pieces)
        while (pairs.isNotEmpty()) {
            val bigram = pairs.minByOrNull { ranks[it] ?: Int.MAX_VALUE } ?: break
            if (ranks[bigram] == null) break
            pieces = mergeAll(pieces, bigram)
            if (pieces.size == 1) break
            pairs = adjacentPairs(pieces)
        }
        return pieces
    }

    private fun adjacentPairs(word: List<String>): Set<Pair<String, String>> {
        if (word.size < 2) return emptySet()
        val pairs = LinkedHashSet<Pair<String, String>>()
        for (i in 0 until word.size - 1) pairs.add(word[i] to word[i + 1])
        return pairs
    }

    /** Merge every non-overlapping occurrence of [bigram] in [word], left to right (matches HF/GPT-2 BPE semantics). */
    private fun mergeAll(word: List<String>, bigram: Pair<String, String>): List<String> {
        val (first, second) = bigram
        val result = ArrayList<String>(word.size)
        var i = 0
        while (i < word.size) {
            if (word[i] == first && i < word.size - 1 && word[i + 1] == second) {
                result.add(first + second)
                i += 2
            } else {
                result.add(word[i])
                i += 1
            }
        }
        return result
    }

    /** GPT-2/CLIP's byte-to-unicode table: printable Latin-1 bytes map to themselves, the rest to codepoints 256+. */
    private fun buildByteEncoder(): Map<Int, Char> {
        val bs = mutableListOf<Int>()
        bs.addAll('!'.code..'~'.code)
        bs.addAll('¡'.code..'¬'.code)
        bs.addAll('®'.code..'ÿ'.code)
        val cs = bs.toMutableList()
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }
        val map = HashMap<Int, Char>(256)
        for (i in bs.indices) map[bs[i]] = cs[i].toChar()
        return map
    }
}
