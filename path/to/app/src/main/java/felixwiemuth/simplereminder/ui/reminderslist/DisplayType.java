public class DisplayType {
    // existing code

    public static int getReminderTypeIcon(String reminderType) {
        if (reminderType.equals("notification")) {
            return R.drawable.ic_notification;
        } else if (reminderType.equals("nagging")) {
            return R.drawable.ic_nagging;
        } else if (reminderType.equals("alarm")) {
            return R.drawable.ic_alarm;
        } else {
            return R.drawable.ic_reminder_type;
        }
    }
}