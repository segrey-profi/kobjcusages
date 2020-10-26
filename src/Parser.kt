import java.io.File

enum class MatchType {
    NONE, IMPORT, FORWARD, DEFINITION
}

class LineMatch(val type: MatchType, val text: String)

class Parser(private val excludeImports: List<String>) {
    private val regexMatchers = arrayListOf(
        MatchType.IMPORT to Regex("#import(\\s+)?[\"<]([^\">]+)[\">]"),
        MatchType.FORWARD to Regex("[@](class|protocol)\\s+([^;]+);"),
        MatchType.DEFINITION to Regex("[@](interface|protocol)\\s+(\\w+(\\s+[(][^)]+[)])?)")
    )

    fun parse(file: File, ignored: MatchType, callback: (LineMatch) -> Unit) {
        file.forEachLine { line ->
            parseLine(line.trim(), ignored)
                ?.takeIf { it.type != MatchType.IMPORT || !excludeImports.contains(it.text) }
                ?.let(callback)
        }
    }

    private fun parseLine(line: String, ignored: MatchType): LineMatch? {
        for (rx in regexMatchers) {
            rx.takeIf { it.first != ignored }?.second?.find(line)?.groups?.get(2)?.let {
                return LineMatch(rx.first, it.value)
            }
        }
        return MatchType.NONE.takeIf { ignored != it }?.let { LineMatch(it, line) }
    }
}
