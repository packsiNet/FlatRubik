package com.shahram.flatrubik

import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.shahram.flatrubik.databinding.ActivityGameBinding

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private lateinit var level: Level
    private lateinit var activeBoard: PuzzleBoard

    private enum class Phase { COUNTDOWN, PLAY, WON }
    private enum class FeedbackMode { SOUND, VIBRATE, SILENT }

    private var phase = Phase.COUNTDOWN
    private var countdown = 10
    private var moves = 0
    private var seconds = 0
    private var shuffleEpoch = 0

    private var feedbackMode = FeedbackMode.SOUND
    private var toneGen: ToneGenerator? = null

    private val handler = Handler(Looper.getMainLooper())
    private val countdownTick = object : Runnable {
        override fun run() {
            if (countdown <= 0) {
                startPlay()
                return
            }
            countdown--
            updateCountdown()
            handler.postDelayed(this, 1000)
        }
    }
    private val timerTick = object : Runnable {
        override fun run() {
            if (phase != Phase.PLAY) return
            seconds++
            updateTimer()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(
                left   = bars.left   + (16 * density).toInt(),
                top    = bars.top,
                right  = bars.right  + (16 * density).toInt(),
                bottom = bars.bottom
            )
            insets
        }

        val levelId = intent.getStringExtra("levelId") ?: "easy"
        level = LEVELS.first { it.id == levelId }

        loadFeedbackMode()
        setupHeader()
        setupBoard()
        setupLegend()
        startCountdown()
    }

    private fun setupHeader() {
        binding.tvLevelName.text = level.name
        binding.tvLevelName.setTextColor(level.accentColor)

        val prefs = getSharedPreferences("flatrubik_records_v1", MODE_PRIVATE)
        val bestMoves = prefs.getInt("${level.id}_moves", 0)
        binding.tvBest.text = if (bestMoves > 0) "BEST $bestMoves" else "BEST —"

        binding.btnBack.setOnClickListener { finish() }
        binding.btnReset.setOnClickListener { confirmReset() }
        binding.btnSettings.setOnClickListener { showFeedbackSettings() }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this, R.style.WinDialog)
            .setTitle("Restart?")
            .setMessage("Current progress will be lost.")
            .setPositiveButton("RESTART") { _, _ -> resetGame() }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun setupBoard() {
        activeBoard = PuzzleBoard(level)   // starts solved; scrambled during countdown
        binding.boardView.board = activeBoard
        binding.boardView.isInteractive = false
        binding.boardView.isPreviewMode = false

        binding.boardView.onMove = {
            moves++
            updateStats()
            doFeedback()
        }
        binding.boardView.onSolved = {
            if (phase == Phase.PLAY) {
                phase = Phase.WON
                handler.removeCallbacks(timerTick)
                showWinDialog()
            }
        }
    }

    private fun setupLegend() {
        binding.legendContainer.removeAllViews()
        for (zone in level.zones) {
            val item = LayoutInflater.from(this).inflate(R.layout.item_legend, binding.legendContainer, false)
            item.findViewById<View>(R.id.legendDot).background =
                createCircle(zone.color)
            item.findViewById<TextView>(R.id.legendName).text = zone.name.uppercase()
            item.tag = zone.name
            binding.legendContainer.addView(item)
        }
    }

    private fun startCountdown() {
        binding.countdownView.visibility = View.VISIBLE
        binding.countdownView.accentColor = level.accentColor
        binding.countdownView.countdown = countdown
        val epoch = ++shuffleEpoch
        val totalMoves = when (level.id) {
            "medium" -> 55
            "hard" -> 65
            else -> 40
        }
        startShuffleAnimation(epoch, totalMoves)
        handler.postDelayed(countdownTick, 1000)
    }

    private fun startShuffleAnimation(epoch: Int, remaining: Int) {
        if (epoch != shuffleEpoch || remaining <= 0 || phase != Phase.COUNTDOWN) return
        val rng = java.util.Random()
        val isRow = rng.nextBoolean()
        val index = if (isRow) rng.nextInt(level.rows) else rng.nextInt(level.cols)
        val dir = if (rng.nextBoolean()) 1 else -1
        val steps: Int; val animDuration: Long; val gapMs: Long
        when (level.id) {
            "medium" -> { steps = rng.nextInt(5) + 1; animDuration = 150L; gapMs = 30L }
            "hard"   -> { steps = rng.nextInt(8) + 1; animDuration = 130L; gapMs = 20L }
            else     -> { steps = 1; animDuration = 150L; gapMs = 70L }
        }
        binding.boardView.pushShuffleMove(isRow, index, dir, steps, animDuration) {
            handler.postDelayed({ startShuffleAnimation(epoch, remaining - 1) }, gapMs)
        }
    }

    private fun updateCountdown() {
        binding.countdownView.countdown = countdown
    }

    private fun startPlay() {
        shuffleEpoch++                          // stop any remaining shuffle callbacks
        binding.boardView.cancelShuffle()       // stop in-progress shuffle animation
        binding.countdownView.visibility = View.GONE
        binding.boardView.isInteractive = true
        phase = Phase.PLAY
        updateStats()
        handler.postDelayed(timerTick, 1000)
    }

    private fun updateStats() {
        binding.tvMoves.text = moves.toString().padStart(3, '0')
        updateZoneCount()
        updateProgress()
        updateLegendSolvedState()
    }

    private fun updateTimer() {
        val m = seconds / 60; val s = seconds % 60
        binding.tvTime.text = "$m:${s.toString().padStart(2, '0')}"
    }

    private fun updateZoneCount() {
        val solved = (0 until level.zones.size).count { activeBoard.isZoneSolved(it) }
        binding.tvZones.text = "$solved/${level.zones.size}"
    }

    private fun updateProgress() {
        val solved = (0 until level.zones.size).count { activeBoard.isZoneSolved(it) }
        val pct = solved.toFloat() / level.zones.size
        binding.progressFill.layoutParams = (binding.progressFill.layoutParams as LinearLayout.LayoutParams).also {
            it.weight = pct
        }
        binding.progressEmpty.layoutParams = (binding.progressEmpty.layoutParams as LinearLayout.LayoutParams).also {
            it.weight = (1f - pct)
        }
    }

    private fun updateLegendSolvedState() {
        for (i in 0 until binding.legendContainer.childCount) {
            val item = binding.legendContainer.getChildAt(i)
            val zone = level.zones.getOrNull(i) ?: continue
            val solved = activeBoard.isZoneSolved(i)
            val checkmark = item.findViewById<TextView>(R.id.legendCheck)
            checkmark.visibility = if (solved) View.VISIBLE else View.GONE
            item.alpha = if (solved) 1f else 0.55f
            if (solved) {
                item.findViewById<View>(R.id.legendDot).background = createCircleGlow(zone.color)
            }
        }
    }

    private fun showWinDialog() {
        val prefs = getSharedPreferences("flatrubik_records_v1", MODE_PRIVATE)
        val prevBest = prefs.getInt("${level.id}_moves", 0)
        val isRecord = prevBest == 0 || moves < prevBest || (moves == prevBest && seconds < prefs.getInt("${level.id}_secs", Int.MAX_VALUE))

        if (isRecord) {
            val timeStr = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
            prefs.edit()
                .putInt("${level.id}_moves", moves)
                .putInt("${level.id}_secs", seconds)
                .putString("${level.id}_time", timeStr)
                .apply()
        }

        val timeStr = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        val msg = "${if (isRecord) "🏆 NEW RECORD!\n" else ""}Moves: $moves\nTime: $timeStr"

        AlertDialog.Builder(this, R.style.WinDialog)
            .setTitle(if (isRecord) "🏆 SOLVED!" else "✨ SOLVED!")
            .setMessage(msg)
            .setPositiveButton("NEW GAME") { _, _ -> resetGame() }
            .setNegativeButton("← BACK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun resetGame() {
        moves = 0; seconds = 0; countdown = 10; phase = Phase.COUNTDOWN
        handler.removeCallbacks(timerTick)
        handler.removeCallbacks(countdownTick)
        shuffleEpoch++                          // invalidate pending shuffle callbacks
        binding.boardView.cancelShuffle()
        activeBoard = PuzzleBoard(level)        // fresh solved board
        binding.boardView.board = activeBoard
        binding.boardView.isInteractive = false
        binding.tvMoves.text = "000"
        binding.tvTime.text = "0:00"
        updateZoneCount()
        setupLegend()
        startCountdown()
    }

    private fun createCircle(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun createCircleGlow(color: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
            setStroke((1.5f * resources.displayMetrics.density).toInt(), Color.argb(100, Color.red(color), Color.green(color), Color.blue(color)))
        }
    }

    private fun loadFeedbackMode() {
        val prefs = getSharedPreferences("flatrubik_settings", MODE_PRIVATE)
        feedbackMode = when (prefs.getString("feedback_mode", "sound")) {
            "vibrate" -> FeedbackMode.VIBRATE
            "silent"  -> FeedbackMode.SILENT
            else      -> FeedbackMode.SOUND
        }
    }

    private fun saveFeedbackMode(mode: FeedbackMode) {
        feedbackMode = mode
        val key = when (mode) {
            FeedbackMode.SOUND   -> "sound"
            FeedbackMode.VIBRATE -> "vibrate"
            FeedbackMode.SILENT  -> "silent"
        }
        getSharedPreferences("flatrubik_settings", MODE_PRIVATE).edit().putString("feedback_mode", key).apply()
    }

    private fun doFeedback() {
        when (feedbackMode) {
            FeedbackMode.SOUND -> {
                try {
                    if (toneGen == null) toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
                    toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 30)
                } catch (e: Exception) { }
            }
            FeedbackMode.VIBRATE -> {
                val vib = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") vib.vibrate(18)
                }
            }
            FeedbackMode.SILENT -> {}
        }
    }

    private fun showFeedbackSettings() {
        val modes = arrayOf("🔊 Sound", "📳 Vibrate", "🔇 Silent")
        val current = when (feedbackMode) {
            FeedbackMode.SOUND   -> 0
            FeedbackMode.VIBRATE -> 1
            FeedbackMode.SILENT  -> 2
        }
        AlertDialog.Builder(this, R.style.WinDialog)
            .setTitle("Move Feedback")
            .setSingleChoiceItems(modes, current) { dialog, which ->
                saveFeedbackMode(when (which) {
                    1    -> FeedbackMode.VIBRATE
                    2    -> FeedbackMode.SILENT
                    else -> FeedbackMode.SOUND
                })
                dialog.dismiss()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(countdownTick)
        handler.removeCallbacks(timerTick)
        toneGen?.release()
        toneGen = null
    }
}
