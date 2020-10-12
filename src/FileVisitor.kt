import java.io.File

class FileVisitor {

    private val excludeFiles = arrayOf(".DS_Store", "*.strings")

    fun visitDirectory(dir: File, excludeDirs: List<File> = emptyList(), callback: (File) -> Unit) {
        val files = dir.takeIf { !excludeDirs.contains(it) }?.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                visitDirectory(file, excludeDirs, callback)
                continue
            }
            val isExcluded = excludeFiles.any {
                file.name == it || it.startsWith("*") && file.name.endsWith(it.substring(1))
            }
            if (!isExcluded) callback(file)
        }
    }
}
