package felixwiemuth.simplereminder;

import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;

public class AddReminderDialogActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_reminder_dialog);

        final AutoCompleteTextView nameTextView = findViewById(R.id.nameTextView);

        final TimePicker timePicker = findViewById(R.id.timePicker);
        timePicker.setIs24HourView(true);

        Button addButton = findViewById(R.id.addButton);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int hour;
                int minute;
                boolean tomorrow = false;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    hour = timePicker.getHour();
                    minute = timePicker.getMinute();
                } else {
                    hour = timePicker.getCurrentHour();
                    minute = timePicker.getCurrentMinute();
                }

                Calendar time = Calendar.getInstance();
                time.set(Calendar.HOUR_OF_DAY, hour);
                time.set(Calendar.MINUTE, minute);
                time.set(Calendar.SECOND, 0);

                if (time.compareTo(Calendar.getInstance()) <= 0) { // TODO might want to add margin if ReminderManager does not send out reminder when time has already elapsed
                    time.add(Calendar.DAY_OF_MONTH, 1);  //TODO test: does it wrap over midnight?
                    tomorrow = true;
                }

                ReminderManager.addReminder(AddReminderDialogActivity.this, time.getTimeInMillis(), nameTextView.getText().toString());
                String text = getString(R.string.toast_reminder_added_for);
                if (tomorrow) {
                    text += getString(R.string.toast_reminder_added_tomorrow);
                }
                text += DateFormat.getTimeInstance(DateFormat.SHORT).format(time.getTime());
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(AddReminderDialogActivity.this, text, duration);
                toast.show();

                finish(); //TODO change animation
            }
        });
    }
}
