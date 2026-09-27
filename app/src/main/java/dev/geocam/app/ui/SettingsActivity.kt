package dev.geocam.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.geocam.app.R
import dev.geocam.app.settings.SettingsRepository

/**
 * Binds every overlay/quality/privacy toggle to [SettingsRepository].
 *
 * Each control writes straight through on change, so there is no save button
 * and no risk of the screen and the repository disagreeing.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var repo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)
        val settingsScroll = findViewById<android.view.View>(R.id.settingsScroll)
        ViewCompat.setOnApplyWindowInsetsListener(settingsScroll) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top + dp(16), view.paddingRight, systemBars.bottom + dp(16))
            insets
        }
        repo = SettingsRepository(this)

        bindCheckBox(R.id.gpsEnabled, { repo.gpsEnabled }, { repo.gpsEnabled = it })
        bindCheckBox(R.id.showPlaceName, { repo.showPlaceName }, { repo.showPlaceName = it })
        bindCheckBox(R.id.showCoordinates, { repo.showCoordinates }, { repo.showCoordinates = it })
        bindCheckBox(R.id.showDateTime, { repo.showDateTime }, { repo.showDateTime = it })
        bindCheckBox(R.id.showAccuracy, { repo.showAccuracy }, { repo.showAccuracy = it })
        bindCheckBox(R.id.showAltitude, { repo.showAltitude }, { repo.showAltitude = it })
        bindCheckBox(R.id.showHeading, { repo.showHeading }, { repo.showHeading = it })
        bindCheckBox(R.id.miniMap, { repo.miniMapEnabled }, { repo.miniMapEnabled = it })
        bindCheckBox(R.id.saveCleanCopy, { repo.saveCleanCopy }, { repo.saveCleanCopy = it })
        bindCheckBox(
            R.id.saveWithoutLocation,
            { repo.saveWithoutLocation },
            { repo.saveWithoutLocation = it }
        )

        bindCoordFormat()
        bindNote()
        bindQuality()
    }

    private fun bindCheckBox(id: Int, read: () -> Boolean, write: (Boolean) -> Unit) {
        val box = findViewById<CheckBox>(id)
        box.isChecked = read()
        box.setOnCheckedChangeListener { _, checked -> write(checked) }
    }

    private fun bindCoordFormat() {
        val decimal = findViewById<RadioButton>(R.id.coordDecimal)
        val dms = findViewById<RadioButton>(R.id.coordDms)
        if (repo.useDecimalCoords) decimal.isChecked = true else dms.isChecked = true
        decimal.setOnClickListener { repo.useDecimalCoords = true }
        dms.setOnClickListener { repo.useDecimalCoords = false }
    }

    private fun bindNote() {
        val note = findViewById<EditText>(R.id.customNote)
        note.setText(repo.customNote)
        note.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                repo.customNote = s?.toString().orEmpty()
            }
        })
    }

    private fun bindQuality() {
        val qualityBar = findViewById<SeekBar>(R.id.jpegQuality)
        val qualityLabel = findViewById<TextView>(R.id.jpegQualityLabel)
        qualityBar.progress = repo.jpegQuality.coerceIn(MIN_QUALITY, MAX_QUALITY)
        qualityLabel.text = getString(R.string.settings_jpeg_quality) + ": " + qualityBar.progress
        qualityBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                repo.jpegQuality = progress.coerceIn(MIN_QUALITY, MAX_QUALITY)
                qualityLabel.text = getString(R.string.settings_jpeg_quality) + ": " + progress
            }
        })

        // Index 0 is "full resolution"; every step is one power-of-two-ish bucket
        // of 512 px, so the user gets a coarse but predictable size cap.
        val edgeBar = findViewById<SeekBar>(R.id.maxLongEdge)
        val edgeLabel = findViewById<TextView>(R.id.maxLongEdgeLabel)
        val steps = EDGE_STEPS
        edgeBar.max = steps.size - 1
        val currentIndex = steps.indexOfFirst { it >= repo.maxLongEdge }.let { if (it < 0) 0 else it }
        edgeBar.progress = currentIndex
        fun edgeText(index: Int) = if (steps[index] <= 0) {
            getString(R.string.settings_max_long_edge) + ": " + getString(R.string.settings_full_resolution)
        } else {
            getString(R.string.settings_max_long_edge) + ": " + steps[index] + " px"
        }
        edgeLabel.text = edgeText(currentIndex)
        edgeBar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                repo.maxLongEdge = steps[progress]
                edgeLabel.text = edgeText(progress)
            }
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Saves the three branches of [android.widget.SeekBar.OnSeekBarChangeListener]. */
    private abstract class SimpleSeekBarListener : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    }

    private companion object {
        const val MIN_QUALITY = 30
        const val MAX_QUALITY = 100

        /** Long-edge caps in pixels; 0 means "keep full resolution". */
        val EDGE_STEPS = intArrayOf(
            0, 1024, 1440, 1920, 2560, 3072, 4096, 5120, 6144, 8192
        )
    }
}
