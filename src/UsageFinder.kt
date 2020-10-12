import java.io.File
import java.util.Properties

object UsageFinder {

    private val fileVisitor = FileVisitor()
    private val sources = mutableMapOf<String, Definition>()
    private val dependencies = mutableMapOf<String, Dependency>()

    private lateinit var rootDir: File

    fun run() {
        val config = readConfig()
        rootDir = File(config.rootPath)

        for (path in config.targetPaths) {
            ProgressWriter.reset()
            println("Processing target $path...")
            fileVisitor.visitDirectory(File(rootDir, path), callback = ::processSource)
        }

        ProgressWriter.reset()
        println("Sources found: ${sources.size}")
        println("Dependenices found: ${dependencies.size}")

        val sourceDefinitions = sources.values
        val externalDependencies = mutableMapOf<String, Dependency>()
        val excludeDirs = (config.excludePaths + config.targetPaths).map { File(rootDir, it) }

        println()
        println("Processing root ${rootDir.absolutePath}...")
        fileVisitor.visitDirectory(rootDir, excludeDirs) { file ->
            processDependency(file, sourceDefinitions, externalDependencies)
        }

        checkLocalDependenices()

        ProgressWriter.reset()

        val unusedDirs = mutableListOf<String>().apply { addAll(config.targetPaths) }
        val sourcesByUsage = sources.values.groupBy { it.usages.isNotEmpty() }

        sourcesByUsage[true]?.sortedBy { it.name }?.let { usableSources ->
            println()
            println("USABLE SOURCES:")
            for (def in usableSources) {
                println()
                println(def.name)
                println(" Contained in:")
                for (path in def.files.sorted()) {
                    println("  $path")
                    unusedDirs.removeAll { path.startsWith(it) }
                }
                println(" Used in:")
                def.usages.sorted().forEach { println("  $it") }
            }
        } ?: println("NO USABLE SOURCES")

        val unusedSources = sourcesByUsage[false]?.filter { src ->
            unusedDirs.none { dir -> src.files.any { it.startsWith(dir) } }
        }?.sortedBy { it.name } ?: emptyList()

        if (unusedSources.isNotEmpty() || unusedDirs.isNotEmpty()) {
            println()
            println("UNUSED SOURCES:")
            for (dir in unusedDirs) {
                println()
                println(dir)
            }
            for (def in unusedSources) {
                println()
                println(def.name)
                println(" Contained in:")
                for (path in def.files.sorted()) {
                    println("  $path")
                }
            }
        }

        val unusedDependencies = dependencies.filter {
            !externalDependencies.containsKey(it.key) && !sources.containsKey(it.key)
        }
        if (unusedDependencies.isNotEmpty()) {
            println()
            println("UNUSED DEPENDENCIES:")
            for ((name, dep) in unusedDependencies.toSortedMap()) {
                println()
                println("$name was used in:")
                dep.usages.forEach { println("  $it") }
            }
        }

        if (externalDependencies.isNotEmpty()) {
            println()
            println("USABLE DEPENDENCIES:")
            for ((name, dep) in externalDependencies.toSortedMap()) {
                println()
                println("$name used in:")
                dep.usages.forEach { println("  $it") }
            }
        }
    }

    private fun readConfig(): Config {
        val properties = Properties()
        val propsFile = File(System.getProperty("user.dir"), "local.properties")
        propsFile.reader().use { properties.load(it) }

        return Config(properties)
    }

    private fun processSource(file: File) {
        ProgressWriter.step()
        val isImplementation = file.name.endsWith(".m")
        val isObjC = isImplementation || file.name.endsWith(".h")
        if (!isObjC) return

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

    private fun processDependency(
        file: File,
        sourceDefinitions: Collection<Definition>,
        externalDependencies: MutableMap<String, Dependency>
    ) {
        ProgressWriter.step()
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

    private fun checkLocalDependenices() {
        var hasLocalDependencies: Boolean
        do {
            ProgressWriter.reset()
            println("Checking for local dependenices...")
            hasLocalDependencies = false
            for ((name, def) in sources) {
                ProgressWriter.step()
                val dependency = dependencies[name] ?: continue
                val localDeps = mutableMapOf<String, MutableSet<String>>()
                collectUsages(name, dependency, localDeps)
                if (localDeps.isEmpty()) continue
                for (depSet in localDeps.values) {
                    val newDeps = depSet.filter { !def.usages.contains(it) }
                    if (newDeps.isEmpty()) continue
                    hasLocalDependencies = true
                    def.usages.addAll(newDeps)
                }
            }
        } while (hasLocalDependencies)
    }

    private fun collectUsages(
        sourceName: String,
        dependency: Dependency,
        localDeps: MutableMap<String, MutableSet<String>>
    ) = dependency.usages.forEach { path ->
        sources.mapNotNull { s ->
            val isSameSource = s.key != sourceName
            val isUsableSource = s.value.usages.isNotEmpty()
            val hasDependency = s.value.files.contains(path)
            s.key.takeIf { !isSameSource && isUsableSource && hasDependency }
        }.forEach {
            localDeps[it] = localDeps[it]?.apply { add(path) } ?: mutableSetOf(path)
        }
    }

}
