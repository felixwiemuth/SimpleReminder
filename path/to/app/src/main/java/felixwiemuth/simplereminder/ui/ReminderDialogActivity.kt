import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton

class ReminderDialogActivity : AppCompatActivity() {

    // existing code

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_dialog)

        // existing code

        btnReminderType.setOnLongClickListener {
            // existing code
            val dialog = ReminderTypeSettingsDialog(this, reminderType)
            dialog.show()
            true
        }
    }
}