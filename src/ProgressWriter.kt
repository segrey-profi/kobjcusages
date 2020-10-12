object ProgressWriter {

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
