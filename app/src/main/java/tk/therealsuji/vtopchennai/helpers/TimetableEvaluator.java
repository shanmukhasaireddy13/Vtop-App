package tk.therealsuji.vtopchennai.helpers;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import tk.therealsuji.vtopchennai.models.CalendarEvent;
import tk.therealsuji.vtopchennai.models.Exam;
import tk.therealsuji.vtopchennai.models.Timetable;

public class TimetableEvaluator {

    public static class ParsedClass implements Comparable<ParsedClass> {
        public long startTimeMillis;
        public long endTimeMillis;

        public ParsedClass(long startTimeMillis, long endTimeMillis) {
            this.startTimeMillis = startTimeMillis;
            this.endTimeMillis = endTimeMillis;
        }

        @Override
        public int compareTo(ParsedClass o) {
            return Long.compare(this.startTimeMillis, o.startTimeMillis);
        }
    }

    /**
     * Helper overload for basic parsing without events/exams.
     */
    public static List<ParsedClass> parseClasses(List<Timetable> timetableList, long currentTimeMillis) {
        return parseClasses(timetableList, null, null, currentTimeMillis);
    }

    /**
     * Parses raw Timetable classes, filters them based on Holidays/Exam periods in the academic calendar,
     * applies Day Orders, and appends Exam events directly as classes.
     */
    public static List<ParsedClass> parseClasses(
            List<Timetable> timetableList, 
            List<CalendarEvent> calendarEvents, 
            List<Exam> exams, 
            long currentTimeMillis) {
        List<ParsedClass> parsedClasses = new ArrayList<>();
        
        SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
        SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

        // 1. Process regular timetable classes for the next 7 days
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            Calendar targetCal = Calendar.getInstance();
            targetCal.setTimeInMillis(currentTimeMillis);
            targetCal.add(Calendar.DATE, dayOffset);
            
            // Reset to start of day
            targetCal.set(Calendar.HOUR_OF_DAY, 0);
            targetCal.set(Calendar.MINUTE, 0);
            targetCal.set(Calendar.SECOND, 0);
            targetCal.set(Calendar.MILLISECOND, 0);

            String targetDateStr = isoDateFormat.format(targetCal.getTime());
            
            // Check calendar event for this date
            CalendarEvent targetEvent = null;
            if (calendarEvents != null) {
                for (CalendarEvent ev : calendarEvents) {
                    if (targetDateStr.equals(ev.date)) {
                        targetEvent = ev;
                        break;
                    }
                }
            }

            int dayOfWeekToUse = targetCal.get(Calendar.DAY_OF_WEEK) - 1; // 0=Sunday, 1=Monday
            boolean isHolidayOrExam = false;

            if (targetEvent != null && targetEvent.event != null) {
                String evtLower = targetEvent.event.toLowerCase(Locale.ENGLISH);
                
                // Holiday or Exam period: suspend normal classes
                if (evtLower.contains("holiday") || evtLower.contains("no class") || 
                    evtLower.contains("non-working") || evtLower.contains("cat") || 
                    evtLower.contains("fat") || evtLower.contains("exam") || 
                    evtLower.contains("term end") || evtLower.contains("revision")) {
                    isHolidayOrExam = true;
                } else if (evtLower.contains("instructional day") || evtLower.contains("working day") || evtLower.contains("day order")) {
                    // Check Day Orders
                    if (evtLower.contains("monday")) dayOfWeekToUse = 1;
                    else if (evtLower.contains("tuesday")) dayOfWeekToUse = 2;
                    else if (evtLower.contains("wednesday")) dayOfWeekToUse = 3;
                    else if (evtLower.contains("thursday")) dayOfWeekToUse = 4;
                    else if (evtLower.contains("friday")) dayOfWeekToUse = 5;
                    else if (evtLower.contains("saturday")) dayOfWeekToUse = 6;
                    else if (evtLower.contains("sunday")) dayOfWeekToUse = 0;
                }
            } else {
                // Default Sunday is a holiday
                if (dayOfWeekToUse == 0) {
                    isHolidayOrExam = true;
                }
            }

            if (isHolidayOrExam) {
                continue;
            }

            for (Timetable timetable : timetableList) {
                Integer[] slots = {
                        timetable.sunday,
                        timetable.monday,
                        timetable.tuesday,
                        timetable.wednesday,
                        timetable.thursday,
                        timetable.friday,
                        timetable.saturday
                };

                if (dayOfWeekToUse >= 0 && dayOfWeekToUse < slots.length && slots[dayOfWeekToUse] != null) {
                    try {
                        Calendar classStart = (Calendar) targetCal.clone();
                        classStart.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timetable.startTime.split(":")[0]));
                        classStart.set(Calendar.MINUTE, Integer.parseInt(timetable.startTime.split(":")[1]));

                        Calendar classEnd = (Calendar) targetCal.clone();
                        classEnd.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timetable.endTime.split(":")[0]));
                        classEnd.set(Calendar.MINUTE, Integer.parseInt(timetable.endTime.split(":")[1]));

                        parsedClasses.add(new ParsedClass(classStart.getTimeInMillis(), classEnd.getTimeInMillis()));
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Add exam schedules as classes
        if (exams != null) {
            for (Exam exam : exams) {
                if (exam.startTime != null && exam.endTime != null) {
                    if (exam.endTime >= currentTimeMillis) {
                        parsedClasses.add(new ParsedClass(exam.startTime, exam.endTime));
                    }
                }
            }
        }

        Collections.sort(parsedClasses);
        return parsedClasses;
    }

    public static boolean isClassActiveOrStartingSoon(
            List<Timetable> rawTimetable, 
            List<CalendarEvent> calendarEvents, 
            List<Exam> exams, 
            long lowerBound, 
            long upperBound, 
            long currentTimeMillis) {
        List<ParsedClass> classes = parseClasses(rawTimetable, calendarEvents, exams, currentTimeMillis);
        for (ParsedClass pc : classes) {
            if (pc.startTimeMillis <= upperBound && pc.endTimeMillis >= lowerBound) {
                return true;
            }
        }
        return false;
    }

    public static ParsedClass getNextImmediateClass(
            List<Timetable> rawTimetable, 
            List<CalendarEvent> calendarEvents, 
            List<Exam> exams, 
            long currentTimeMillis) {
        List<ParsedClass> classes = parseClasses(rawTimetable, calendarEvents, exams, currentTimeMillis);
        for (ParsedClass pc : classes) {
            if (pc.startTimeMillis > currentTimeMillis) {
                return pc;
            }
        }
        return null;
    }

    public static ParsedClass getCurrentActiveClass(
            List<Timetable> rawTimetable, 
            List<CalendarEvent> calendarEvents, 
            List<Exam> exams, 
            long currentTimeMillis) {
        List<ParsedClass> classes = parseClasses(rawTimetable, calendarEvents, exams, currentTimeMillis);
        for (ParsedClass pc : classes) {
            if (pc.startTimeMillis <= currentTimeMillis && pc.endTimeMillis >= currentTimeMillis) {
                return pc;
            }
        }
        return null;
    }

    // Deprecated compat helpers:
    public static boolean isClassActiveOrStartingSoon(List<Timetable> rawTimetable, long lowerBound, long upperBound, long currentTimeMillis) {
        return isClassActiveOrStartingSoon(rawTimetable, null, null, lowerBound, upperBound, currentTimeMillis);
    }

    public static ParsedClass getNextImmediateClass(List<Timetable> rawTimetable, long currentTimeMillis) {
        return getNextImmediateClass(rawTimetable, null, null, currentTimeMillis);
    }

    public static ParsedClass getCurrentActiveClass(List<Timetable> rawTimetable, long currentTimeMillis) {
        return getCurrentActiveClass(rawTimetable, null, null, currentTimeMillis);
    }
}
