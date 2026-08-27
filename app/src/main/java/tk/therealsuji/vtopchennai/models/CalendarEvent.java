package tk.therealsuji.vtopchennai.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Represents a single day entry in the academic calendar.
 * Each row corresponds to one day that has a label/event in the VTOP calendar.
 */
@Entity(tableName = "calendar_events")
public class CalendarEvent {

    /** Auto-generated primary key. */
    @PrimaryKey(autoGenerate = true)
    public int id;

    /**
     * The date of the event in ISO format: "YYYY-MM-DD".
     * e.g. "2026-07-01"
     */
    @ColumnInfo(name = "date")
    public String date;

    /**
     * The event/day label as displayed in the VTOP calendar.
     * e.g. "Working Day", "Holiday", "CAT 1", "Revision", etc.
     */
    @ColumnInfo(name = "event")
    public String event;

    /**
     * The day-of-month number (1–31) for convenience in UI rendering.
     */
    @ColumnInfo(name = "day")
    public Integer day;

    /**
     * Month number (1–12).
     */
    @ColumnInfo(name = "month")
    public Integer month;

    /**
     * 4-digit year.
     */
    @ColumnInfo(name = "year")
    public Integer year;
}
