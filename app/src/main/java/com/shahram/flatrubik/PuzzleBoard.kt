package com.shahram.flatrubik

class PuzzleBoard(val level: Level) {

    var grid: Array<IntArray> = buildSolved()

    fun buildSolved(): Array<IntArray> =
        Array(level.rows) { r -> IntArray(level.cols) { c -> getZoneColor(r, c) } }

    fun reset() { grid = buildSolved() }

    fun getZoneColor(r: Int, c: Int): Int {
        for (z in level.zones) if (r in z.r0..z.r1 && c in z.c0..z.c1) return z.color
        return 0xFF444444.toInt()
    }

    fun getZoneIndex(r: Int, c: Int): Int {
        for (i in level.zones.indices) {
            val z = level.zones[i]
            if (r in z.r0..z.r1 && c in z.c0..z.c1) return i
        }
        return -1
    }

    // dir > 0 = right (last→front), dir < 0 = left (first→back)
    fun shiftRow(r: Int, dir: Int) {
        val row = grid[r].copyOf()
        if (dir > 0) {
            val last = row[level.cols - 1]
            System.arraycopy(row, 0, row, 1, level.cols - 1)
            row[0] = last
        } else {
            val first = row[0]
            System.arraycopy(row, 1, row, 0, level.cols - 1)
            row[level.cols - 1] = first
        }
        grid[r] = row
    }

    // dir > 0 = down (last→top), dir < 0 = up (first→bottom)
    fun shiftCol(c: Int, dir: Int) {
        val col = IntArray(level.rows) { r -> grid[r][c] }
        if (dir > 0) {
            val last = col[level.rows - 1]
            System.arraycopy(col, 0, col, 1, level.rows - 1)
            col[0] = last
        } else {
            val first = col[0]
            System.arraycopy(col, 1, col, 0, level.rows - 1)
            col[level.rows - 1] = first
        }
        for (r in 0 until level.rows) grid[r][c] = col[r]
    }

    fun isSolved(): Boolean {
        for (r in 0 until level.rows)
            for (c in 0 until level.cols)
                if (grid[r][c] != getZoneColor(r, c)) return false
        return true
    }

    fun isZoneSolved(i: Int): Boolean {
        val z = level.zones[i]
        for (r in z.r0..z.r1) for (c in z.c0..z.c1) if (grid[r][c] != z.color) return false
        return true
    }

    fun shuffle(n: Int = 30) {
        val rng = java.util.Random()
        repeat(n) {
            if (rng.nextBoolean()) shiftRow(rng.nextInt(level.rows), if (rng.nextBoolean()) 1 else -1)
            else shiftCol(rng.nextInt(level.cols), if (rng.nextBoolean()) 1 else -1)
        }
        if (isSolved()) shuffle(n)
    }

    fun colBoundary() = BooleanArray(level.cols - 1) { c -> getZoneIndex(0, c) != getZoneIndex(0, c + 1) }
    fun rowBoundary() = BooleanArray(level.rows - 1) { r -> getZoneIndex(r, 0) != getZoneIndex(r + 1, 0) }
}
