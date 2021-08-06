import java.io.File

class ImageFinder(private val config: Config, private val fileVisitor: FileVisitor) {

    fun run() {
        val images = mutableSetOf<ImageDep>()

        for (path in config.targetPaths) {
            ProgressWriter.reset()
            println("Processing target $path...")
            val targetDir = File(config.rootPath, path)
            fileVisitor.visitDirectory(targetDir) { file ->
                ProgressWriter.step()
                file.parentFile
                    .takeIf { it.name.endsWith(IMAGE_SUFFIX) }
                    ?.let { images.add(ImageDep(it, targetDir)) }
            }
        }

        val rootDir = File(config.rootPath)
        val excludeDirs = with (config) { (excludePaths + targetPaths).map { File(rootDir, it) } }

        ProgressWriter.reset()
        println("Processing root ${rootDir.absolutePath}...")

        fileVisitor.visitDirectory(rootDir, excludeDirs) { file ->
            ProgressWriter.step()
            val filePath = file.toRelativeString(rootDir)
            file.forEachLine { line ->
                images.forEach {
                    if (line.contains("\"${it.name}\"")) {
                        it.usages.add(ImageDep.Usage(line.trim(), filePath))
                    }
                }
            }
        }

        ProgressWriter.reset()

        val imagesByUsage = images.groupBy { it.usages.isNotEmpty() }

        imagesByUsage[true]?.toSortedSet()?.forEach { img ->
            println()
            println("${img.toRelativeString()} used in:")
            img.usages.groupBy { it.filePath }.forEach { (filePath, lines) ->
                println(" $filePath")
                lines.forEach { println(" > ${it.line}") }
            }
        }

        imagesByUsage[false]?.toSortedSet()?.let { unused ->
            println()
            println("NOT USED:")
            unused.forEach { println(it.toRelativeString()) }
        }
    }

    class ImageDep(private val file: File, private val targetDir: File): Comparable<ImageDep> {
        val name: String = file.name.removeSuffix(IMAGE_SUFFIX)
        val usages = mutableSetOf<Usage>()

        fun toRelativeString(): String = file.toRelativeString(targetDir)

        override fun hashCode() = file.hashCode()
        override fun equals(other: Any?) = file == (other as? ImageDep)?.file
        override fun compareTo(other: ImageDep) = file.absolutePath.compareTo(other.file.absolutePath)

        data class Usage(val line: String, val filePath: String)
    }

    companion object {
        const val IMAGE_SUFFIX = ".imageset"
    }
}
