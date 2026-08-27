package tk.therealsuji.vtopchennai.interfaces;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import tk.therealsuji.vtopchennai.models.CalendarEvent;

@Dao
public interface CalendarDao {

    @Insert
    Completable insert(List<CalendarEvent> events);

    @Query("DELETE FROM calendar_events")
    Completable deleteAll();

    @Query("DELETE FROM calendar_events")
    void deleteAllSync();

    @Insert
    void insertSync(List<CalendarEvent> events);

    @Transaction
    default void replaceAll(List<CalendarEvent> events) {
        deleteAllSync();
        insertSync(events);
    }

    /** Returns all calendar events ordered chronologically. */
    @Query("SELECT * FROM calendar_events ORDER BY year, month, day")
    Single<List<CalendarEvent>> getAll();

    /** Returns calendar events for a specific month/year. */
    @Query("SELECT * FROM calendar_events WHERE month = :month AND year = :year ORDER BY day")
    Single<List<CalendarEvent>> getByMonth(int month, int year);

    /** Returns calendar events for a specific date (YYYY-MM-DD). */
    @Query("SELECT * FROM calendar_events WHERE date = :date")
    Single<List<CalendarEvent>> getByDate(String date);

    class MonthYear {
        public Integer month;
        public Integer year;
    }

    @Query("SELECT DISTINCT month, year FROM calendar_events ORDER BY year, month")
    Single<List<MonthYear>> getUniqueMonths();
}
