package tk.therealsuji.vtopchennai.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "user_tasks")
public class UserTask {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "start_time")
    public String startTime; // "HH:mm"

    @ColumnInfo(name = "end_time")
    public String endTime; // "HH:mm"

    @ColumnInfo(name = "day_of_week")
    public int dayOfWeek; // Mon=1, Tue=2, Wed=3, Thu=4, Fri=5, Sat=6, Sun=0

    @ColumnInfo(name = "is_completed")
    public boolean isCompleted;

    @ColumnInfo(name = "is_alarm_enabled")
    public boolean isAlarmEnabled;

    @ColumnInfo(name = "is_college_class")
    public boolean isCollegeClass;

    @ColumnInfo(name = "course_code")
    public String courseCode;

    public UserTask() {
    }

    @Ignore
    public UserTask(String title, String startTime, String endTime, int dayOfWeek, boolean isAlarmEnabled, boolean isCollegeClass, String courseCode) {
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.dayOfWeek = dayOfWeek;
        this.isCompleted = false;
        this.isAlarmEnabled = isAlarmEnabled;
        this.isCollegeClass = isCollegeClass;
        this.courseCode = courseCode;
    }
}
