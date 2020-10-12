class Dependency(private val name: String, usage: String) : Comparable<Dependency> {

    val usages = mutableSetOf(usage)

    override fun compareTo(other: Dependency) = name.compareTo(other.name)
}
