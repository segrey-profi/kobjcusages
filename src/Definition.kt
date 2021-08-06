class Definition(val name: String, file: String) : Comparable<Definition> {

    val files = mutableSetOf(file)
    val usages = mutableSetOf<String>()
    val regex = Regex("[ \\[(<.:,=]$name[ \\]()<>*!?.:,;=]")

    override fun compareTo(other: Definition) = name.compareTo(other.name)
}
