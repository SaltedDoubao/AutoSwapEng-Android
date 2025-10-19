package com.autoswapeng.app.logic

/**
 * 基于 Python 版算法的移植：
 * - 词性前缀识别：按最长前缀匹配 TYPE
 * - 去除词性后按分隔符 NO 切分
 * - 逐词计算 Levenshtein 相似度；分数>=98 给予大权重
 */
class WordMatcher {
    private val typeMarkers = listOf("v.&n.", "adj.", "adv.", "vt.", "vi.", "n.", "v.")
    private val separators = listOf(',', ';', '；', '，')

    data class LearnedEntry(
        val word: String,
        val parts: List<String>,  // 含词性+释义
        val posLen: List<Int>,    // 词性长度
    )

    private val memory = LinkedHashMap<String, LearnedEntry>()

    fun learn(word: String, definitionLines: List<String>) {
        val norm = definitionLines.map { normalize(it) }
        val parts = mutableListOf<String>()
        val posLen = mutableListOf<Int>()
        for (line in norm) {
            var i = 0
            while (i < line.length) {
                var p = 1
                for (len in 5 downTo 2) {
                    if (i + len <= line.length) {
                        val sub = line.substring(i, i + len)
                        if (sub in typeMarkers) { p = len; break }
                    }
                }
                if (p != 1) {
                    parts.add(line.substring(i, i + p))
                    posLen.add(p)
                } else {
                    val lastIndex = parts.lastIndex.takeIf { it >= 0 } ?: run {
                        parts.add(""); posLen.add(0); 0
                    }
                    parts[lastIndex] = parts[lastIndex] + line[i]
                }
                i += p
            }
        }
        memory[word] = LearnedEntry(word, parts, posLen)
    }

    fun getLearnedCount(): Int = memory.size
    
    fun match(word: String, options: List<String>): Int {
        val entry = memory[word] ?: return 0
        val scores = DoubleArray(options.size)
        options.forEachIndexed { idx, raw ->
            val opt = normalize(raw)
            val parsed = parseWithPos(opt)
            var score = 0.0
            var count = 1
            for (j in parsed.parts.indices) {
                for (k in entry.parts.indices) {
                    if (entry.posLen[k] != parsed.posLen.getOrElse(j) { 0 }) {
                        count++
                        continue
                    }
                    val left = entry.parts[k].drop(entry.posLen[k])
                    val right = parsed.parts[j].drop(parsed.posLen[j])
                    val lTokens = splitTokens(left)
                    val rTokens = splitTokens(right)
                    for (lt in lTokens) {
                        for (rt in rTokens) {
                            val s = levenshteinSimilarity(lt, rt)
                            score += s
                            if (s >= 98) score += 1000
                            count++
                        }
                    }
                }
            }
            scores[idx] = score / count
        }
        var best = 0
        for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
        return best
    }

    /**
     * 根据中文释义在4个英文单词中选择最匹配的一个（用于“中→英”选择题）。
     * 逻辑：
     * - 将传入 definition 按与 learn 相同规则解析（词性+中文片段分词）
     * - 对每个英文选项，如果该词在记忆库中出现，则与其已学习的 parts 做相似度评分
     * - 取分数最高的选项；若全未学习，则返回0
     */
    fun matchByDefinition(definition: String, englishOptions: List<String>): Int {
        val parsedDef = parseWithPos(normalize(definition))
        if (englishOptions.isEmpty()) return 0
        val scores = DoubleArray(englishOptions.size)
        englishOptions.forEachIndexed { idx, optWordRaw ->
            val optWord = normalize(optWordRaw)
            val entry = memory[optWord]
            if (entry == null) {
                scores[idx] = 0.0
            } else {
                var score = 0.0
                var count = 1
                for (j in parsedDef.parts.indices) {
                    for (k in entry.parts.indices) {
                        // 词性一致加分，否则弱化
                        if (entry.posLen[k] != parsedDef.posLen.getOrElse(j) { 0 }) {
                            count++
                            continue
                        }
                        val left = entry.parts[k].drop(entry.posLen[k])
                        val right = parsedDef.parts[j].drop(parsedDef.posLen[j])
                        val lTokens = splitTokens(left)
                        val rTokens = splitTokens(right)
                        for (lt in lTokens) {
                            for (rt in rTokens) {
                                val s = levenshteinSimilarity(lt, rt)
                                score += s
                                if (s >= 98) score += 1000
                                count++
                            }
                        }
                    }
                }
                scores[idx] = score / count
            }
        }
        var best = 0
        for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
        return best
    }

    private fun parseWithPos(text: String): LearnedEntry {
        val parts = mutableListOf<String>()
        val posLen = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            var p = 1
            for (len in 5 downTo 2) {
                if (i + len <= text.length) {
                    val sub = text.substring(i, i + len)
                    if (sub in typeMarkers) { p = len; break }
                }
            }
            if (p != 1) {
                parts.add(text.substring(i, i + p))
                posLen.add(p)
            } else {
                val lastIndex = parts.lastIndex.takeIf { it >= 0 } ?: run {
                    parts.add(""); posLen.add(0); 0
                }
                parts[lastIndex] = parts[lastIndex] + text[i]
            }
            i += p
        }
        return LearnedEntry("", parts, posLen)
    }

    private fun splitTokens(s: String): List<String> {
        val tokens = mutableListOf<StringBuilder>()
        var cur = StringBuilder()
        for (c in s) {
            if (separators.contains(c)) {
                tokens.add(cur)
                cur = StringBuilder()
            } else cur.append(c)
        }
        tokens.add(cur)
        return tokens.map { it.toString() }.filter { it.isNotEmpty() }
    }

    private fun normalize(s: String): String = buildString {
        for (c in s) append(if (c in 'A'..'Z') (c.code + 32).toChar() else c)
    }

    // 返回 0..100 的相似度
    private fun levenshteinSimilarity(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val la = a.length
        val lb = b.length
        val dp = IntArray(lb + 1) { it }
        for (i in 1..la) {
            var prev = i - 1
            dp[0] = i
            for (j in 1..lb) {
                val tmp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,      // deletion
                    dp[j - 1] + 1,  // insertion
                    prev + cost     // substitution
                )
                prev = tmp
            }
        }
        val dist = dp[lb]
        val maxLen = maxOf(la, lb)
        val sim = ((1.0 - dist.toDouble() / maxLen) * 100).toInt()
        return sim.coerceIn(0, 100)
    }
}


