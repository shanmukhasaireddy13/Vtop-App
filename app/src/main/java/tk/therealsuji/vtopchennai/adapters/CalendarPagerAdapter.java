package tk.therealsuji.vtopchennai.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.interfaces.CalendarDao;
import tk.therealsuji.vtopchennai.models.CalendarEvent;

/**
 * Pager adapter that displays a monthly grid calendar for each page in the ViewPager2.
 */
public class CalendarPagerAdapter extends RecyclerView.Adapter<CalendarPagerAdapter.ViewHolder> {

    private final List<CalendarDao.MonthYear> uniqueMonths;
    private final String targetDate;
    private Context applicationContext;

    public CalendarPagerAdapter(List<CalendarDao.MonthYear> uniqueMonths) {
        this(uniqueMonths, null);
    }

    public CalendarPagerAdapter(List<CalendarDao.MonthYear> uniqueMonths, String targetDate) {
        this.uniqueMonths = uniqueMonths;
        this.targetDate = targetDate;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        this.applicationContext = context.getApplicationContext();

        RecyclerView gridView = new RecyclerView(context);
        ViewGroup.LayoutParams gridViewParams = new ViewGroup.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        gridView.setLayoutParams(gridViewParams);
        gridView.setLayoutManager(new GridLayoutManager(context, 7));
        gridView.setClipToPadding(false);

        // Adjust padding to give a neat look
        float pixelDensity = context.getResources().getDisplayMetrics().density;
        gridView.setPadding(0, (int) (16 * pixelDensity), 0, 0);

        return new ViewHolder(gridView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecyclerView gridView = (RecyclerView) holder.itemView;
        CalendarDao.MonthYear monthYear = uniqueMonths.get(position);

        AppDatabase appDatabase = AppDatabase.getInstance(this.applicationContext);
        CalendarDao calendarDao = appDatabase.calendarDao();

        calendarDao
                .getByMonth(monthYear.month, monthYear.year)
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<List<CalendarEvent>>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                    }

                    @Override
                    public void onSuccess(@NonNull List<CalendarEvent> events) {
                        gridView.setAdapter(new CalendarGridAdapter(monthYear.year, monthYear.month, events, targetDate));
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        gridView.setAdapter(new EmptyStateAdapter(EmptyStateAdapter.TYPE_ERROR, "Error: " + e.getLocalizedMessage()));
                    }
                });
    }

    @Override
    public int getItemCount() {
        return uniqueMonths.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
