import java.io.File

enum class MatchType {
    NONE, IMPORT, FORWARD, DEFINITION, SWIFT
}

class LineMatch(val type: MatchType, val text: String)

class Parser(private val excludeImports: List<String>) {
    private val regexMatchers = arrayListOf(
        MatchType.IMPORT to Regex("#import(\\s+)?[\"<]([^\">]+)[\">]"),
        MatchType.FORWARD to Regex("[@](class|protocol)\\s+([^;]+);"),
        MatchType.DEFINITION to Regex("[@](interface|protocol)\\s+(\\w+(\\s+[(][^)]+[)])?)"),
        MatchType.SWIFT to Regex("(class|protocol|struct|enum|typealias)\\s+([A-Z][A-Za-z]+)")
    )

    fun parse(file: File, ignored: Set<MatchType>, callback: (LineMatch) -> Unit) {
        var isComment = false
        file.forEachLine { lineString ->
            val line = lineString.trim()
            var normalizedLine = ""
            when {
                line == COMMENT_LINE || line.startsWith(COMMENT_LINE) -> Unit
                line == COMMENT_PREFIX || line.startsWith(COMMENT_PREFIX) -> isComment = true
                line == COMMENT_SUFFIX -> isComment = false
                !isComment && line.endsWith(COMMENT_PREFIX) -> {
                    normalizedLine = line.removeSuffix(COMMENT_PREFIX).trim()
                    isComment = true
                }
                isComment && line.contains(COMMENT_SUFFIX) -> {
                    isComment = false
                    val index = line.indexOf(COMMENT_SUFFIX) + COMMENT_SUFFIX.length
                    if (index < line.length) normalizedLine = line.substring(index).trim()
                }
                isComment -> Unit
                else -> normalizedLine = line
            }
            normalizedLine.takeIf { it.isNotEmpty() }
                ?.let { parseLine(it, ignored) }
                ?.takeIf { isValidMatch(it, line) }
                ?.let(callback)
        }
    }

    private fun parseLine(line: String, ignored: Set<MatchType>): LineMatch? {
        for (rx in regexMatchers) {
            rx.takeIf { !ignored.contains(it.first) }?.second?.find(line)?.groups?.get(2)?.let {
                return LineMatch(rx.first, it.value)
            }
        }
        return MatchType.NONE.takeIf { !ignored.contains(it) }?.let { LineMatch(it, line) }
    }

    private fun isValidMatch(match: LineMatch, line: String): Boolean = when (match.type) {
        MatchType.SWIFT -> !line.contains(PRIVATE_PREFIX)
            || line.indexOf(PRIVATE_PREFIX) > line.indexOf(match.text)
        MatchType.IMPORT -> !excludeImports.contains(match.text)
        else -> true
    }

    companion object {
        const val COMMENT_LINE = "//"
        const val COMMENT_PREFIX = "/*"
        const val COMMENT_SUFFIX = "*/"
        const val PRIVATE_PREFIX = "private "
    }
}
