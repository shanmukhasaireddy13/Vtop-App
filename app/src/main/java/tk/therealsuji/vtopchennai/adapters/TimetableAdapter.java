package tk.therealsuji.vtopchennai.adapters;

import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.interfaces.CalendarDao;
import tk.therealsuji.vtopchennai.interfaces.TimetableDao;
import tk.therealsuji.vtopchennai.models.CalendarEvent;
import tk.therealsuji.vtopchennai.models.Timetable;

/**
 * ┬─── Timetable Hierarchy
 * ├─ {@link tk.therealsuji.vtopchennai.fragments.HomeFragment}
 * ├─ {@link TimetableAdapter}      - ViewPager2 (Current File)
 * ╰→ {@link TimetableItemAdapter}  - RecyclerView
 */
public class TimetableAdapter extends RecyclerView.Adapter<TimetableAdapter.ViewHolder> {
    Context applicationContext;

    @NonNull
    @Override
    public TimetableAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        this.applicationContext = context.getApplicationContext();

        RecyclerView timetableView = new RecyclerView(context);
        ViewGroup.LayoutParams timetableParams = new ViewGroup.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        timetableView.setLayoutParams(timetableParams);
        timetableView.setLayoutManager(new LinearLayoutManager(context));
        timetableView.setClipToPadding(false);

        return new ViewHolder(timetableView);
    }

    @Override
    public void onBindViewHolder(@NonNull TimetableAdapter.ViewHolder holder, int position) {
        RecyclerView timetableView = (RecyclerView) holder.itemView;

        AppDatabase appDatabase = AppDatabase.getInstance(this.applicationContext);
        TimetableDao timetableDao = appDatabase.timetableDao();
        CalendarDao calendarDao = appDatabase.calendarDao();
        int day = holder.getAdapterPosition();

        // TEST TOGGLE: Set to true to mock Saturday as a Wednesday Day Order instructional day
        boolean DEBUG_TEST_DAY_ORDER = false;

        // Calculate the exact date string for the tab's day in the current week
        Calendar targetCal = Calendar.getInstance();
        int currentDayOfWeek = DEBUG_TEST_DAY_ORDER ? Calendar.SATURDAY : targetCal.get(Calendar.DAY_OF_WEEK); // Sunday = 1, Saturday = 7
        int targetDayOfWeek = day + 1; // Tab day: Sunday = 1, Saturday = 7
        targetCal.add(Calendar.DAY_OF_YEAR, targetDayOfWeek - currentDayOfWeek);

        int year = targetCal.get(Calendar.YEAR);
        int month = targetCal.get(Calendar.MONTH) + 1;
        int dayOfMonth = targetCal.get(Calendar.DAY_OF_MONTH);
        String dateStr = String.format(Locale.ENGLISH, "%04d-%02d-%02d", year, month, dayOfMonth);

        calendarDao
                .getByDate(dateStr)
                .flatMap(events -> {
                    List<CalendarEvent> finalEvents = events;
                    if (DEBUG_TEST_DAY_ORDER && day == 6) {
                        // Simulating a Saturday with a Wednesday Day Order event
                        CalendarEvent mockEvent = new CalendarEvent();
                        mockEvent.date = dateStr;
                        mockEvent.event = "Instructional Day - Wednesday Day Order";
                        finalEvents = new java.util.ArrayList<>();
                        finalEvents.add(mockEvent);
                    }

                    int queryDay = day;
                    String activeDayOrder = null;
                    if (finalEvents.size() > 0) {
                        CalendarEvent event = finalEvents.get(0);
                        if (event.event != null) {
                            activeDayOrder = event.event; // Always show the banner
                            String evtLower = event.event.toLowerCase(Locale.ENGLISH);
                            if (evtLower.contains("instructional day") || evtLower.contains("working day") || evtLower.contains("day order")) {
                                if (evtLower.contains("monday")) queryDay = 1;
                                else if (evtLower.contains("tuesday")) queryDay = 2;
                                else if (evtLower.contains("wednesday")) queryDay = 3;
                                else if (evtLower.contains("thursday")) queryDay = 4;
                                else if (evtLower.contains("friday")) queryDay = 5;
                                else if (evtLower.contains("saturday")) queryDay = 6;
                                else if (evtLower.contains("sunday")) queryDay = 0;
                            }
                        }
                    }
                    final String finalActiveDayOrder = activeDayOrder;
                    return timetableDao.get(queryDay)
                            .map(list -> new Pair<>(list, finalActiveDayOrder));
                })
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<Pair<List<Timetable.AllData>, String>>() {
                    @Override
                    public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {

                    }

                    @Override
                    public void onSuccess(@io.reactivex.rxjava3.annotations.NonNull Pair<List<Timetable.AllData>, String> result) {
                        List<Timetable.AllData> timetable = result.first;
                        String dayOrderMessage = result.second;

                        if (timetable.size() == 0) {
                            timetableView.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_NO_TIMETABLE));
                            return;
                        }

                        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
                        int status = TimetableItemAdapter.STATUS_FUTURE;

                        if (day < dayOfWeek) {
                            status = TimetableItemAdapter.STATUS_PAST;
                        } else if (day == dayOfWeek) {
                            status = TimetableItemAdapter.STATUS_PRESENT;
                        }

                        timetableView.setAdapter(new TimetableItemAdapter(timetable, status, dayOrderMessage, dateStr));
                    }

                    @Override
                    public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        timetableView.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_ERROR, "Error: " + e.getLocalizedMessage()));
                    }
                });
    }

    @Override
    public int getItemCount() {
        return 7;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
