import java.io.File

enum class MatchType {
    NONE, IMPORT, FORWARD, DEFINITION
}

class LineMatch(val type: MatchType, val text: String)

object Parser {
    private val regexMatchers = arrayListOf(
        MatchType.IMPORT to Regex("#import(\\s+)?[\"<]([^\">]+)[\">]"),
        MatchType.FORWARD to Regex("[@](class|protocol)\\s+([^;]+);"),
        MatchType.DEFINITION to Regex("[@](interface|protocol)\\s+(\\w+)")
    )

    private val excludeImports = arrayOf(
        "UIKit/UIKit.h",
        "Foundation/Foundation.h",
        "profi-Swift.h"
    )

    fun parse(file: File, ignored: MatchType): List<LineMatch> {
        val matches = mutableListOf<LineMatch>()
        file.forEachLine { line ->
            parseLine(line.trim(), ignored)
                ?.takeIf { it.type != MatchType.IMPORT || !excludeImports.contains(it.text) }
                ?.let { matches.add(it) }
        }
        return matches
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
