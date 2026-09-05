package com.example.intervalalarm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class ReminderAdapter(
    private val reminders: MutableList<Reminder>,
    private val onEdit: (Reminder) -> Unit,
    private val onDelete: (Reminder) -> Unit,
    private val onToggle: (Reminder, Boolean) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvReminderName)
        val tvInterval: TextView = view.findViewById(R.id.tvReminderInterval)
        val tvFile: TextView = view.findViewById(R.id.tvReminderFile)
        val tvCountdown: TextView = view.findViewById(R.id.tvCountdown)
        val switchEnabled: SwitchMaterial = view.findViewById(R.id.switchEnabled)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reminder = reminders[position]
        val context = holder.itemView.context

        holder.tvName.text = reminder.name

        val unitText = when (reminder.intervalUnit) {
            0 -> "Sekunden"
            1 -> "Minuten"
            2 -> "Stunden"
            else -> "Minuten"
        }
        holder.tvInterval.text = "Alle ${reminder.intervalValue} $unitText"
        holder.tvFile.text = reminder.fileName

        holder.tvCountdown.text = buildCountdownText(context, reminder)

        // Schalter ohne Endlosschleife setzen
        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.isChecked = reminder.enabled
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggle(reminder, isChecked)
        }

        holder.btnEdit.setOnClickListener { onEdit(reminder) }
        holder.btnDelete.setOnClickListener { onDelete(reminder) }
    }

    override fun getItemCount(): Int = reminders.size

    /** Liest den von AlarmForegroundService gespeicherten nächsten
     *  Auslöse-Zeitpunkt und baut daraus einen "mm:ss"-Countdown-Text.
     *  Funktioniert unabhängig davon, ob Benachrichtigungen erlaubt sind. */
    private fun buildCountdownText(context: Context, reminder: Reminder): String {
        if (!reminder.enabled) return "Deaktiviert"

        val prefs = context.getSharedPreferences(
            AlarmForegroundService.PREFS_NAME, Context.MODE_PRIVATE
        )
        val key = AlarmForegroundService.nextAlarmKey(reminder.id)
        if (!prefs.contains(key)) return "Gestoppt"

        val nextTime = prefs.getLong(key, 0L)
        val remaining = (nextTime - System.currentTimeMillis()).coerceAtLeast(0L)
        val minutes = remaining / 60000
        val seconds = (remaining % 60000) / 1000

        return "Nächster Alarm in %02d:%02d".format(minutes, seconds)
    }
}
