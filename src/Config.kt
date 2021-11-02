import java.io.File
import java.util.Properties

class Config(properties: Properties) {

    private companion object {
        private const val PARENT_SUFFIX = "/*"
    }

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
        ?.checkingParents(rootPath)
        ?.sorted()
        ?: error("No target directories provided")

    val excludePaths: List<String> = properties.getList("exclude.dirs")

    val excludeImports: List<String> = properties.getList("exclude.imports", separator = ",")

    val excludeSwiftDefinitions: List<String> = properties.getList("exclude.swift", separator = ",")

    private fun Properties.getList(key: String, separator: String = ":"): List<String> =
        (this[key] as? String)
            ?.split(separator)
            ?.filter { it.isNotBlank() }
            ?.map { it.trim() } ?: emptyList()

    private fun List<String>.checkingParents(rootPath: String): List<String> {
        if (none { it.endsWith(PARENT_SUFFIX) }) return this
        val paths = mutableListOf<String>()
        val root = File(rootPath)
        for (path in this) {
            if (path.endsWith(PARENT_SUFFIX)) {
                File(root, path.removeSuffix(PARENT_SUFFIX))
                    .takeIf { it.isDirectory }
                    ?.listFiles { file, _ -> file?.isDirectory ?: false }
                    ?.forEach { paths.add(it.toRelativeString(root)) }
            } else {
                paths.add(path)
            }
        }
        return paths
    }
}
