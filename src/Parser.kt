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
        file.forEachLine { line ->
            parseLine(line.trim(), ignored)
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
        MatchType.SWIFT -> !line.contains(PRIVATE_PREFIX) || line.indexOf(PRIVATE_PREFIX) > line.indexOf(match.text)
        MatchType.IMPORT -> !excludeImports.contains(match.text)
        else -> true
    }

    companion object {
        const val PRIVATE_PREFIX = "private "
    }
}
