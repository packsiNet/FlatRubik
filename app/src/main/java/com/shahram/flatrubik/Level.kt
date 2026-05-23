package com.shahram.flatrubik

import android.graphics.Color

data class Zone(
    val r0: Int, val r1: Int,
    val c0: Int, val c1: Int,
    val color: Int,
    val name: String
)

data class Level(
    val id: String,
    val name: String,
    val sub: String,
    val emoji: String,
    val accentColor: Int,
    val cols: Int,
    val rows: Int,
    val zones: List<Zone>
)

val LEVELS = listOf(
    Level(
        id = "easy", name = "DRIFT", sub = "4×4 · four zones", emoji = "🟢",
        accentColor = Color.parseColor("#34d399"),
        cols = 4, rows = 4,
        zones = listOf(
            Zone(0, 1, 0, 1, Color.parseColor("#ef4444"), "ember"),
            Zone(0, 1, 2, 3, Color.parseColor("#facc15"), "solar"),
            Zone(2, 3, 0, 1, Color.parseColor("#22d3ee"), "tidal"),
            Zone(2, 3, 2, 3, Color.parseColor("#a78bfa"), "nebula"),
        )
    ),
    Level(
        id = "medium", name = "PRISM", sub = "6×6 · four zones", emoji = "🟡",
        accentColor = Color.parseColor("#fbbf24"),
        cols = 6, rows = 6,
        zones = listOf(
            Zone(0, 2, 0, 2, Color.parseColor("#ef4444"), "ember"),
            Zone(0, 2, 3, 5, Color.parseColor("#facc15"), "solar"),
            Zone(3, 5, 0, 2, Color.parseColor("#22d3ee"), "tidal"),
            Zone(3, 5, 3, 5, Color.parseColor("#a78bfa"), "nebula"),
        )
    ),
    Level(
        id = "hard", name = "VORTEX", sub = "9×9 · nine zones", emoji = "🔴",
        accentColor = Color.parseColor("#f87171"),
        cols = 9, rows = 9,
        zones = listOf(
            Zone(0, 2, 0, 2, Color.parseColor("#ef4444"), "ember"),
            Zone(0, 2, 3, 5, Color.parseColor("#facc15"), "solar"),
            Zone(0, 2, 6, 8, Color.parseColor("#34d399"), "fern"),
            Zone(3, 5, 0, 2, Color.parseColor("#22d3ee"), "tidal"),
            Zone(3, 5, 3, 5, Color.parseColor("#a78bfa"), "nebula"),
            Zone(3, 5, 6, 8, Color.parseColor("#f472b6"), "rose"),
            Zone(6, 8, 0, 2, Color.parseColor("#fb923c"), "amber"),
            Zone(6, 8, 3, 5, Color.parseColor("#60a5fa"), "azure"),
            Zone(6, 8, 6, 8, Color.parseColor("#c084fc"), "violet"),
        )
    )
)
