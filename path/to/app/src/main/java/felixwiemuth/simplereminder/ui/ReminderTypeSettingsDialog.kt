import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ReminderTypeSettingsDialog(private val context: Context, private val reminderType: String) : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reminder_type_settings)

        // existing code

        btnReminderTypeNotification.setOnClickListener {
            // existing code
        }

        btnReminderTypeNagging.setOnClickListener {
            // existing code
        }

        btnReminderTypeAlarm.setOnClickListener {
            // existing code
        }
    }
}