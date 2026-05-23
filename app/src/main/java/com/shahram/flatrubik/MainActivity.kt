package com.shahram.flatrubik

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.shahram.flatrubik.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(
                left  = bars.left  + (22 * density).toInt(),
                top   = bars.top   + (48 * density).toInt(),
                right = bars.right + (22 * density).toInt(),
                bottom = bars.bottom + (32 * density).toInt()
            )
            insets
        }

        val prefs = getSharedPreferences("flatrubik_records_v1", MODE_PRIVATE)

        binding.cardsContainer.removeAllViews()
        for (level in LEVELS) {
            val card = createLevelCard(level, prefs)
            binding.cardsContainer.addView(card)
        }
    }

    private fun createLevelCard(level: Level, prefs: android.content.SharedPreferences): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_level_card, binding.cardsContainer, false)

        card.findViewById<TextView>(R.id.tvEmoji).text = level.emoji
        card.findViewById<TextView>(R.id.tvName).text = level.name
        card.findViewById<TextView>(R.id.tvSub).text = level.sub

        val moves = prefs.getInt("${level.id}_moves", 0)
        val time = prefs.getString("${level.id}_time", "") ?: ""
        val tvBest = card.findViewById<TextView>(R.id.tvBestVal)
        if (moves > 0) {
            tvBest.text = "$moves · $time"
            tvBest.setTextColor(level.accentColor)
        } else {
            tvBest.text = "— —"
            tvBest.setTextColor(0x37FFFFFF.toInt())
        }

        // Emoji background tinted with accent color
        val emojiBg = card.findViewById<View>(R.id.emojiContainer)
        val bg = ContextCompat.getDrawable(this, R.drawable.emoji_bg)?.mutate()
        bg?.setTint(level.accentColor and 0x00FFFFFF or 0x30000000)
        emojiBg.background = bg

        card.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra("levelId", level.id)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Press state animation
        card.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(120).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
                    if (event.action == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            event.action == MotionEvent.ACTION_DOWN
        }

        return card
    }
}
