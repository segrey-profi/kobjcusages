import java.util.Properties

class Config(properties: Properties) {

    enum class Mode {
        CODE, IMAGES
    }

    val mode: Mode = (properties["search.mode"] as? String)
        ?.let { Mode.valueOf(it.uppercase()) }
        ?: Mode.CODE

    val rootPath: String = (properties["root.dir"] as? String)?.takeIf { it.isNotBlank() }
        ?: error("No root directory provided")

    val targetPaths: List<String> = properties
        .getList("target.dirs")
        .takeIf { it.isNotEmpty() }
        ?.sorted()
        ?: error("No target directories provided")

    val excludePaths: List<String> = properties.getList("exclude.dirs")

    val excludeImports: List<String> = properties.getList("exclude.imports", separator = ",")

    private fun Properties.getList(key: String, separator: String = ":"): List<String> =
        (this[key] as? String)
            ?.split(separator)
            ?.filter { it.isNotBlank() }
            ?.map { it.trim() } ?: emptyList()

}
