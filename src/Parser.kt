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

    fun parse(file: File, ignored: Set<MatchType>, isSwift: Boolean, callback: (LineMatch) -> Unit) {
        val enc = CommentEnclosure(isNested = isSwift)
        file.forEachLine { lineString ->
            val line = lineString.trim()
            if (!enc.isComment && line.startsWith(COMMENT_LINE)) return@forEachLine
            parseLine(line, enc)
                .takeIf { it.isNotEmpty() }
                ?.let { findMatch(it, ignored, isSwift) }
                ?.takeIf { isValidMatch(it, line) }
                ?.let(callback)
        }
    }

    private fun parseLine(line: String, enc: CommentEnclosure): String {
        var str = ""
        var index = 0
        while (index < line.length) {
            val startCommentIndex = line.indexOf(COMMENT_PREFIX, index)
            val endCommentIndex = line.indexOf(COMMENT_SUFFIX, index)
            when {
                enc.isComment -> index = when {
                    startCommentIndex >= index &&
                        (endCommentIndex < 0 || startCommentIndex < endCommentIndex) -> {
                        enc.startComment()
                        startCommentIndex + COMMENT_PREFIX.length
                    }
                    endCommentIndex >= index &&
                        (startCommentIndex < 0 || endCommentIndex < startCommentIndex) -> {
                        enc.endComment()
                        endCommentIndex + COMMENT_SUFFIX.length
                    }
                    else -> line.length
                }
                else -> {
                    val lineCommentIndex = line.indexOf(COMMENT_LINE, index)
                    index = when {
                        lineCommentIndex >= index &&
                            (startCommentIndex < 0 || lineCommentIndex < startCommentIndex) -> {
                            str += line.substring(index, lineCommentIndex)
                            line.length
                        }
                        startCommentIndex >= index -> {
                            if (startCommentIndex > index)
                                str += "${line.substring(index, startCommentIndex)} "
                            enc.startComment()
                            startCommentIndex + COMMENT_PREFIX.length
                        }
                        else -> {
                            str += line.substring(index)
                            line.length
                        }
                    }
                }
            }
        }
        return str.trim()
    }

    private fun findMatch(line: String, ignored: Set<MatchType>, isSwift: Boolean): LineMatch? {
        for (rx in regexMatchers) {
            rx.takeIf { !ignored.contains(it.first) }?.second?.find(line)?.groups?.get(2)?.let {
                return LineMatch(rx.first, it.value)
            }
        }
        return MatchType.NONE
            .takeIf { !ignored.contains(it) && (!isSwift || !line.startsWith(IMPORT_PREFIX)) }
            ?.let { LineMatch(it, line) }
    }

    private fun isValidMatch(match: LineMatch, line: String): Boolean = when (match.type) {
        MatchType.SWIFT -> !line.contains(PRIVATE_PREFIX)
            || line.indexOf(PRIVATE_PREFIX) > line.indexOf(match.text)
        MatchType.IMPORT -> !excludeImports.contains(match.text)
        else -> true
    }

    private class CommentEnclosure(private val isNested: Boolean) {
        private var count = 0

        val isComment: Boolean
            get() = count > 0

        fun startComment() {
            if (!isComment || isNested) count += 1
        }

        fun endComment() {
            when {
                isNested -> count -= 1
                isComment -> count = 0
            }
        }
    }

    companion object {
        const val COMMENT_LINE = "//"
        const val COMMENT_PREFIX = "/*"
        const val COMMENT_SUFFIX = "*/"
        const val PRIVATE_PREFIX = "private "
        const val IMPORT_PREFIX = "import "
    }
}
