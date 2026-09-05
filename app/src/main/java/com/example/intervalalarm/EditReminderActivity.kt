package com.example.intervalalarm

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.intervalalarm.databinding.ActivityEditReminderBinding

class EditReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditReminderBinding
    private lateinit var repository: ReminderRepository

    private var reminderId: String? = null
    private var currentFileUri: String? = null
    private var currentFileName: String = "Keine Datei gewählt"

    // Für den Testton in diesem Bildschirm
    private var testPlayer: MediaPlayer? = null
    private val testHandler = Handler(Looper.getMainLooper())
    private var testStopCallback: Runnable? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            currentFileUri = it.toString()
            currentFileName = getFileName(it)
            binding.tvSelectedFile.text = currentFileName
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ReminderRepository(this)

        reminderId = intent.getStringExtra("reminder_id")

        if (reminderId != null) {
            loadReminder(reminderId!!)
        }

        setupUI()
    }

    private fun loadReminder(id: String) {
        val reminders = repository.loadReminders()
        val reminder = reminders.find { it.id == id } ?: return

        binding.etName.setText(reminder.name)
        binding.etIntervalValue.setText(reminder.intervalValue.toString())
        binding.spinnerUnit.setSelection(reminder.intervalUnit)

        currentFileUri = reminder.fileUri
        currentFileName = reminder.fileName
        binding.tvSelectedFile.text = currentFileName

        binding.seekBarVolume.progress = reminder.volume
        binding.tvVolumePercent.text = "${reminder.volume}%"

        binding.cbLimitDuration.isChecked = reminder.limitDuration
        binding.etMaxSeconds.setText(reminder.maxSeconds.toString())
        binding.etMaxSeconds.isEnabled = reminder.limitDuration

        binding.cbTimeWindow.isChecked = reminder.useTimeWindow
        binding.btnStartTime.text = String.format("%02d:%02d", reminder.startHour, reminder.startMinute)
        binding.btnEndTime.text = String.format("%02d:%02d", reminder.endHour, reminder.endMinute)
        binding.layoutTimeWindow.visibility = if (reminder.useTimeWindow) View.VISIBLE else View.GONE

        binding.cbCustomNotification.isChecked = reminder.customNotification
        binding.etNotificationText.setText(reminder.notificationText)
        binding.etNotificationText.isEnabled = reminder.customNotification
    }

    private fun setupUI() {
        binding.btnSelectFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("audio/*"))
        }

        binding.btnTestPlay.setOnClickListener { startTestPlayback() }
        binding.btnStopTest.setOnClickListener { stopTestPlayback() }

        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvVolumePercent.text = "$progress%"
                testPlayer?.let {
                    val v = progress / 100f
                    it.setVolume(v, v)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.cbLimitDuration.setOnCheckedChangeListener { _, isChecked ->
            binding.etMaxSeconds.isEnabled = isChecked
        }

        binding.cbTimeWindow.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutTimeWindow.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnStartTime.setOnClickListener {
            val parts = binding.btnStartTime.text.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(this, { _, hour, minute ->
                binding.btnStartTime.text = String.format("%02d:%02d", hour, minute)
            }, h, m, true).show()
        }

        binding.btnEndTime.setOnClickListener {
            val parts = binding.btnEndTime.text.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 22
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(this, { _, hour, minute ->
                binding.btnEndTime.text = String.format("%02d:%02d", hour, minute)
            }, h, m, true).show()
        }

        binding.cbCustomNotification.setOnCheckedChangeListener { _, isChecked ->
            binding.etNotificationText.isEnabled = isChecked
        }

        binding.btnSave.setOnClickListener { saveReminder() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    /** Spielt die aktuell gewählte Datei testweise ab – mit der gerade
     *  eingestellten Lautstärke und, falls angehakt, der Abspieldauer-
     *  Begrenzung. So kann man vor dem Speichern prüfen, ob die Datei
     *  überhaupt funktioniert. */
    private fun startTestPlayback() {
        val fileUri = currentFileUri
        if (fileUri.isNullOrBlank()) {
            Toast.makeText(this, "Bitte zuerst eine Audiodatei wählen", Toast.LENGTH_SHORT).show()
            return
        }

        stopTestPlayback()

        try {
            val volume = binding.seekBarVolume.progress / 100f
            testPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(this@EditReminderActivity, Uri.parse(fileUri))
                setVolume(volume, volume)
                setOnCompletionListener { stopTestPlayback() }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@EditReminderActivity, "Datei konnte nicht abgespielt werden", Toast.LENGTH_SHORT).show()
                    stopTestPlayback()
                    true
                }
                prepare()
                start()
            }

            binding.btnTestPlay.visibility = View.GONE
            binding.btnStopTest.visibility = View.VISIBLE

            if (binding.cbLimitDuration.isChecked) {
                val maxSeconds = binding.etMaxSeconds.text.toString().toIntOrNull() ?: 10
                val callback = Runnable { stopTestPlayback() }
                testStopCallback = callback
                testHandler.postDelayed(callback, maxSeconds * 1000L)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Datei konnte nicht abgespielt werden: ${e.message}", Toast.LENGTH_LONG).show()
            stopTestPlayback()
        }
    }

    private fun stopTestPlayback() {
        testStopCallback?.let { testHandler.removeCallbacks(it) }
        testStopCallback = null

        testPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        testPlayer = null

        binding.btnTestPlay.visibility = View.VISIBLE
        binding.btnStopTest.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        // Testton nicht weiterlaufen lassen, wenn man den Bildschirm verlässt
        stopTestPlayback()
    }

    private fun saveReminder() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Bitte einen Namen eingeben", Toast.LENGTH_SHORT).show()
            return
        }

        val intervalValue = binding.etIntervalValue.text.toString().toIntOrNull() ?: 5
        if (intervalValue <= 0) {
            Toast.makeText(this, "Intervall muss größer als 0 sein", Toast.LENGTH_SHORT).show()
            return
        }

        val startParts = binding.btnStartTime.text.split(":")
        val endParts = binding.btnEndTime.text.split(":")

        val reminders = repository.loadReminders().toMutableList()

        if (reminderId != null) {
            // Bestehende Erinnerung aktualisieren
            val index = reminders.indexOfFirst { it.id == reminderId }
            if (index >= 0) {
                val r = reminders[index]
                r.name = name
                r.intervalValue = intervalValue
                r.intervalUnit = binding.spinnerUnit.selectedItemPosition
                r.fileUri = currentFileUri
                r.fileName = currentFileName
                r.volume = binding.seekBarVolume.progress
                r.limitDuration = binding.cbLimitDuration.isChecked
                r.maxSeconds = binding.etMaxSeconds.text.toString().toIntOrNull() ?: 10
                r.useTimeWindow = binding.cbTimeWindow.isChecked
                r.startHour = startParts.getOrNull(0)?.toIntOrNull() ?: 8
                r.startMinute = startParts.getOrNull(1)?.toIntOrNull() ?: 0
                r.endHour = endParts.getOrNull(0)?.toIntOrNull() ?: 22
                r.endMinute = endParts.getOrNull(1)?.toIntOrNull() ?: 0
                r.customNotification = binding.cbCustomNotification.isChecked
                r.notificationText = binding.etNotificationText.text.toString()
            }
        } else {
            // Neue Erinnerung
            val newReminder = Reminder(
                name = name,
                intervalValue = intervalValue,
                intervalUnit = binding.spinnerUnit.selectedItemPosition,
                fileUri = currentFileUri,
                fileName = currentFileName,
                volume = binding.seekBarVolume.progress,
                limitDuration = binding.cbLimitDuration.isChecked,
                maxSeconds = binding.etMaxSeconds.text.toString().toIntOrNull() ?: 10,
                useTimeWindow = binding.cbTimeWindow.isChecked,
                startHour = startParts.getOrNull(0)?.toIntOrNull() ?: 8,
                startMinute = startParts.getOrNull(1)?.toIntOrNull() ?: 0,
                endHour = endParts.getOrNull(0)?.toIntOrNull() ?: 22,
                endMinute = endParts.getOrNull(1)?.toIntOrNull() ?: 0,
                customNotification = binding.cbCustomNotification.isChecked,
                notificationText = binding.etNotificationText.text.toString()
            )
            reminders.add(newReminder)
        }

        repository.saveReminders(reminders)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun getFileName(uri: Uri): String {
        var result = "Unbekannte Datei"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) result = cursor.getString(index)
            }
        }
        return result
    }
}
