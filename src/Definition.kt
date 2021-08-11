class Definition(val name: String, file: String) : Comparable<Definition> {

    val files = mutableSetOf(file)
    val usages = mutableSetOf<String>()

    private val regex: Regex
    private val eolRegex: Regex

    init {
        val prefix = "[ \\[(<.:,=]$name"
        regex = Regex("${prefix}[ \\]()<>*!?.:,;=]")
        eolRegex = Regex("${prefix}$")
    }

    fun hasMatches(str: String) = regex.containsMatchIn(str) || eolRegex.containsMatchIn(str)

    override fun compareTo(other: Definition) = name.compareTo(other.name)
}
