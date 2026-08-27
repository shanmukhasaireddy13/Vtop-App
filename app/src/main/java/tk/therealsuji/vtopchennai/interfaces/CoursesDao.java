package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RoomWarnings;
import androidx.room.Transaction;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.models.Course;
import tk.therealsuji.vtopchennai.models.Slot;

@Dao
@SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
public interface CoursesDao {
    @Insert
    Completable insertCourses(List<Course> courses);

    @Insert
    Completable insertSlots(List<Slot> slots);

    @Query("DELETE FROM courses")
    Completable deleteAll();

    @Query("DELETE FROM courses")
    void deleteAllSync();

    @Insert
    void insertCoursesSync(List<Course> courses);

    @Insert
    void insertSlotsSync(List<Slot> slots);

    @Transaction
    default void replaceAll(List<Course> courses, List<Slot> slots) {
        deleteAllSync();
        insertCoursesSync(courses);
        insertSlotsSync(slots);
    }

    @Query("SELECT DISTINCT code FROM courses, slots WHERE slots.course_id = courses.id ORDER BY slot")
    Single<List<String>> getCourseCodes();

    @Query("SELECT title AS courseTitle, code AS courseCode, type AS courseType, faculty, slot, venue, attended AS attendanceAttended, total AS attendanceTotal, percentage AS attendancePercentage, details AS attendanceDetails " +
            "FROM slots, courses, attendance WHERE slots.id = :slotId AND courses.id = slots.course_id AND attendance.course_id = courses.id")
    Single<Course.AllData> getCourse(int slotId);

    @Query("SELECT title AS courseTitle, type AS courseType, faculty, slot, venue, attended AS attendanceAttended, total AS attendanceTotal, percentage AS attendancePercentage, details AS attendanceDetails " +
            "FROM courses LEFT JOIN slots ON slots.course_id = courses.id LEFT JOIN attendance ON attendance.course_id = courses.id WHERE code = :courseCode")
    Single<List<Course.AllData>> getCourse(String courseCode);

    @Query("SELECT * FROM slots")
    Single<List<Slot>> getAllSlots();

    @Query("SELECT * FROM courses")
    Single<List<Course>> getAllCourses();

    @Query("SELECT title AS courseTitle, code AS courseCode, type AS courseType, faculty, NULL AS slot, venue, attended AS attendanceAttended, total AS attendanceTotal, percentage AS attendancePercentage, details AS attendanceDetails " +
            "FROM courses INNER JOIN attendance ON attendance.course_id = courses.id")
    Single<List<Course.AllData>> getAllCoursesWithAttendance();
}
