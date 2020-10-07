import java.io.File
import java.util.*

class Dependency(val name: String, usage: String) : Comparable<Dependency> {
    val usages = mutableSetOf(usage)
    override fun compareTo(other: Dependency) = name.compareTo(other.name)
}

class Definition(val name: String, file: String) : Comparable<Definition> {
    val files = mutableSetOf(file)
    val usages = mutableSetOf<String>()
    val regex = Regex("[ \\[(]$name[ \\]()*]")
    override fun compareTo(other: Definition) = name.compareTo(other.name)
}

val excludeFiles = arrayOf(".DS_Store", "*.strings")
fun processDir(dir: File, excludeDirs: List<File> = emptyList(), callback: (File) -> Unit) {
    val files = dir.takeIf { !excludeDirs.contains(it) }?.listFiles() ?: return
    for (file in files) {
        if (file.isDirectory) {
            processDir(file, excludeDirs, callback)
            continue
        }
        val isExcluded = excludeFiles.any {
            file.name == it || it.startsWith("*") && file.name.endsWith(it.substring(1))
        }
        if (!isExcluded) callback(file)
    }
}

object Progress {
    private const val pt = "."
    private var points = 0
    fun reset() {
        if (points > 0) {
            println()
            points = 0
        }
    }

    fun step() = System.out.run {
        if (points > 80) {
            println(pt)
            points = 0
        } else {
            print(pt)
            points += 1
        }
        flush()
    }
}

fun readProps(): Triple<String, List<String>, List<String>> {
    val properties = Properties()
    val propsFile = File(System.getProperty("user.dir"), "local.properties")
    propsFile.reader().use { properties.load(it) }

    val rootPath = properties["root.dir"] as? String ?: ""
    if (rootPath.isEmpty()) error("No root directory provided")

    fun readList(key: String): List<String> = (properties[key] as? String)
        ?.split(":")
        ?.filter { it.isNotBlank() }
        ?.map { it.trim() } ?: emptyList()

    val targetPaths = readList("target.dirs")
    if (targetPaths.isEmpty()) error("No target directories provided")

    val excludePaths = readList("exclude.dirs")

    return Triple(rootPath, targetPaths, excludePaths)
}

val (rootPath, targetDirs, excludeDirs) = readProps()
val rootDir = File(rootPath)

val sources = mutableMapOf<String, Definition>()
val dependencies = mutableMapOf<String, Dependency>()

for (path in targetDirs) {
    Progress.reset()
    println("Processing target $path...")
    processDir(File(rootDir, path)) { file ->
        Progress.step()
        val isImplementation = file.name.endsWith(".m")
        val isObjC = isImplementation || file.name.endsWith(".h")
        if (!isObjC) return@processDir

        val filePath = file.toRelativeString(rootDir)
        sources[file.name] = sources[file.name]?.apply { files.add(filePath) }
            ?: Definition(file.name, filePath)

        for (match in Parser.parse(file, ignored = MatchType.NONE)) {
            when (match.type) {
                MatchType.IMPORT, MatchType.FORWARD -> dependencies[match.text] =
                    dependencies[match.text]?.apply { usages.add(filePath) }
                        ?: Dependency(match.text, filePath)
                MatchType.DEFINITION -> sources[file.name] =
                    sources[match.text]?.apply { files.add(filePath) }
                        ?: Definition(match.text, filePath)
                MatchType.NONE -> Unit
            }
        }
    }
}

Progress.reset()
println("Sources found: ${sources.size}")
println("Dependenices found: ${dependencies.size}")

val externalDependencies = mutableMapOf<String, Dependency>()
val sourceDefinitions = sources.values

println("Processing root ${rootDir.absolutePath}...")
processDir(rootDir, (excludeDirs + targetDirs).map { File(rootDir, it) }) { file ->
    Progress.step()
    val filePath = file.toRelativeString(rootDir)
    for (match in Parser.parse(file, ignored = MatchType.DEFINITION)) {
        when (match.type) {
            MatchType.IMPORT, MatchType.FORWARD ->
                if (sources.containsKey(match.text)) {
                    sources[match.text]?.usages?.add(filePath)
                } else if (dependencies.containsKey(match.text)) {
                    externalDependencies[match.text] =
                        externalDependencies[match.text]?.apply { usages.add(filePath) }
                            ?: Dependency(match.text, filePath)
                }
            MatchType.NONE -> for (def in sourceDefinitions) {
                if (def.regex.containsMatchIn(match.text)) def.usages.add(filePath)
            }
            MatchType.DEFINITION -> Unit
        }
    }
}

var hasLocalDependencies = false
do {
    Progress.reset()
    println("Checking for local dependenices...")
    hasLocalDependencies = false
    for ((name, def) in sources) {
        Progress.step()
        val dependency = dependencies[name] ?: continue
        val localDeps = mutableMapOf<String, MutableSet<String>>()
        for (path in dependency.usages) {
            for ((localName, localDef) in sources) {
                if (localName != name && localDef.usages.isNotEmpty() && localDef.files.contains(path)) {
                    localDeps[localName] = localDeps[localName]?.apply { add(path) }
                        ?: mutableSetOf(path)
                }
            }
        }
        if (localDeps.isEmpty()) continue
        for (depSet in localDeps.values) {
            val newDeps = depSet.filter { !def.usages.contains(it) }
            if (newDeps.isEmpty()) continue
            hasLocalDependencies = true
            def.usages.addAll(newDeps)
        }
    }
} while (hasLocalDependencies)

Progress.reset()
println("")
println("SOURCES:")
for (def in sources.values.filter { it.usages.isNotEmpty() }.sortedBy { it.name }) {
    println(" ")
    println(def.name)
    println(" Contained in:")
    def.files.forEach { println("  $it") }
    println(" Used in:")
    def.usages.forEach { println("  $it") }
}

// TODO unused dependencies = dependencies - externalDependencies

println("")
println("DEPENDENCIES:")
for ((name, dep) in externalDependencies.toSortedMap()) {
    println(" ")
    println("$name used in:")
    dep.usages.forEach { println("  $it") }
}
