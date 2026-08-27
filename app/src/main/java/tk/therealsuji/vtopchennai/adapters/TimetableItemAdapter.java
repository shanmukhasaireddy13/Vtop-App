package tk.therealsuji.vtopchennai.adapters;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import tk.therealsuji.vtopchennai.fragments.ViewPagerFragment;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.interfaces.CoursesDao;
import tk.therealsuji.vtopchennai.interfaces.ExamsDao;
import tk.therealsuji.vtopchennai.models.Course;
import tk.therealsuji.vtopchennai.models.Timetable;

/**
 * ┬─── Timetable Hierarchy
 * ├─ {@link tk.therealsuji.vtopchennai.fragments.HomeFragment}
 * ├─ {@link TimetableAdapter}      - ViewPager2
 * ╰→ {@link TimetableItemAdapter}  - RecyclerView(Current File)
 */
public class TimetableItemAdapter extends RecyclerView.Adapter<TimetableItemAdapter.ViewHolder> {
    public static final int STATUS_PAST = 1;
    public static final int STATUS_PRESENT = 2;
    public static final int STATUS_FUTURE = 3;

    private static final int TYPE_BANNER = 0;
    private static final int TYPE_CLASS = 1;

    private final List<Timetable.AllData> timetable;
    private final int status;
    private final String dayOrderMessage;
    private final String targetDate;

    public TimetableItemAdapter(List<Timetable.AllData> timetable, int status, String dayOrderMessage, String targetDate) {
        this.timetable = timetable;
        this.status = status;
        this.dayOrderMessage = dayOrderMessage;
        this.targetDate = targetDate;
    }

    @Override
    public int getItemViewType(int position) {
        if (dayOrderMessage != null && position == 0) {
            return TYPE_BANNER;
        }
        return TYPE_CLASS;
    }

    @NonNull
    @Override
    public TimetableItemAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_BANNER) {
            View banner = LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.layout_item_timetable_banner, parent, false);
            return new ViewHolder(banner);
        }

        RelativeLayout timetableItem = (RelativeLayout) LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.layout_item_timatable, parent, false);

        return new ViewHolder(timetableItem);
    }

    @Override
    public void onBindViewHolder(@NonNull TimetableItemAdapter.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_BANNER) {
            TextView messageText = holder.itemView.findViewById(R.id.text_view_banner_message);
            messageText.setText(dayOrderMessage);

            String msgLower = dayOrderMessage.toLowerCase(Locale.ENGLISH);
            int bgAttr, textAttr;
            if (msgLower.contains("holiday") || msgLower.contains("no class") || msgLower.contains("non-working")) {
                bgAttr = R.attr.colorErrorContainer;
                textAttr = R.attr.colorOnErrorContainer;
            } else if (msgLower.contains("cat") || msgLower.contains("fat") || msgLower.contains("exam") || msgLower.contains("term end")) {
                bgAttr = R.attr.colorTertiaryContainer;
                textAttr = R.attr.colorOnTertiaryContainer;
            } else {
                bgAttr = R.attr.colorPrimaryContainer;
                textAttr = R.attr.colorOnPrimaryContainer;
            }

            Context ctx = holder.itemView.getContext();
            int bgColor = MaterialColors.getColor(ctx, bgAttr, android.graphics.Color.LTGRAY);
            int textColor = MaterialColors.getColor(ctx, textAttr, android.graphics.Color.BLACK);

            holder.itemView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
            messageText.setTextColor(textColor);

            ImageView icon = holder.itemView.findViewById(R.id.image_view_banner_icon);
            if (icon != null) {
                icon.setImageTintList(android.content.res.ColorStateList.valueOf(textColor));
            }

            holder.itemView.setOnClickListener(view -> {
                Context context = view.getContext();
                FragmentActivity activity = null;
                while (context instanceof ContextWrapper) {
                    if (context instanceof FragmentActivity) {
                        activity = (FragmentActivity) context;
                        break;
                    }
                    context = ((ContextWrapper) context).getBaseContext();
                }
                if (activity != null) {
                    Bundle extraArgs = new Bundle();
                    extraArgs.putString("target_date", targetDate);
                    SettingsRepository.openViewPagerFragment(
                            activity,
                            R.string.academic_calendar,
                            ViewPagerFragment.TYPE_CALENDAR,
                            extraArgs
                    );
                }
            });
        } else {
            int timetablePosition = dayOrderMessage != null ? position - 1 : position;
            holder.setTimetableItem(this.timetable.get(timetablePosition), this.status);
        }
    }

    @Override
    public int getItemCount() {
        return dayOrderMessage != null ? timetable.size() + 1 : timetable.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private static final int MULTIPLYING_FACTOR = 5;

        RelativeLayout timetableItem;
        ProgressBar classProgress;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            if (itemView instanceof RelativeLayout) {
                this.timetableItem = (RelativeLayout) itemView;
                this.classProgress = this.timetableItem.findViewById(R.id.progress_bar_timetable);
            }
        }

        public void setTimetableItem(Timetable.AllData timetableItem, int status) {
            ImageView courseType = this.timetableItem.findViewById(R.id.image_view_course_type);
            TextView courseCode = this.timetableItem.findViewById(R.id.text_view_course_code);

            @DrawableRes int courseTypeId = R.drawable.ic_theory;

            if (timetableItem.courseType.equals("lab")) {
                courseTypeId = R.drawable.ic_lab;
            }

            courseType.setImageDrawable(ContextCompat.getDrawable(this.timetableItem.getContext(), courseTypeId));
            courseCode.setText(timetableItem.courseCode);
            setTimings(timetableItem.startTime, timetableItem.endTime, status);

            float cgpa = SettingsRepository.getCGPA(this.timetableItem.getContext());

            if (cgpa < 9 && timetableItem.attendancePercentage != null && timetableItem.attendancePercentage < 75) {
                ImageView endDrawable = this.timetableItem.findViewById(R.id.image_view_failed_attendance);
                endDrawable.setImageDrawable(ContextCompat.getDrawable(this.timetableItem.getContext(), R.drawable.ic_feedback));
                DrawableCompat.setTint(
                        DrawableCompat.wrap(endDrawable.getDrawable()),
                        MaterialColors.getColor(endDrawable, R.attr.colorError)
                );
            }

            this.classProgress.setOnClickListener(view -> this.onClick(timetableItem.slotId));
        }

        private void onClick(int slotId) {
            Context context = this.timetableItem.getContext();

            AppDatabase appDatabase = AppDatabase.getInstance(context.getApplicationContext());
            CoursesDao coursesDao = appDatabase.coursesDao();

            coursesDao
                    .getCourse(slotId)
                    .subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new SingleObserver<Course.AllData>() {
                        @Override
                        public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                        }

                        @Override
                        public void onSuccess(@io.reactivex.rxjava3.annotations.NonNull Course.AllData course) {
                            SettingsRepository.showCourseInfoBottomSheet(context, course);
                        }

                        @Override
                        public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        }
                    });
        }

        @SuppressLint("SetTextI18n")
        private void setTimings(String startTime, String endTime, int status) {
            try {
                ((TextView) this.timetableItem.findViewById(R.id.text_view_timings)).setText(
                        SettingsRepository.getSystemFormattedTime(this.timetableItem.getContext(), startTime) +
                                " - " + SettingsRepository.getSystemFormattedTime(this.timetableItem.getContext(), endTime)
                );
            } catch (Exception ignored) {
            }

            Calendar calendarFirstHourToday = Calendar.getInstance();
            Calendar calendarLastHourToday = Calendar.getInstance();
            calendarFirstHourToday.set(Calendar.HOUR_OF_DAY, 0);
            calendarFirstHourToday.set(Calendar.MINUTE, 0);
            calendarLastHourToday.set(Calendar.HOUR_OF_DAY, 23);
            calendarLastHourToday.set(Calendar.MINUTE, 59);

            AppDatabase appDatabase = AppDatabase.getInstance(this.timetableItem.getContext().getApplicationContext());
            ExamsDao examsDao = appDatabase.examsDao();

            examsDao
                    .isExamsOngoing(calendarFirstHourToday.getTimeInMillis(), calendarLastHourToday.getTimeInMillis())
                    .subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new SingleObserver<Boolean>() {
                        @Override
                        public void onSubscribe(@io.reactivex.rxjava3.annotations.NonNull Disposable d) {
                        }

                        @Override
                        public void onSuccess(@io.reactivex.rxjava3.annotations.NonNull Boolean isOngoing) {
                            if (isOngoing) {
                                return;
                            }

                            SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);

                            try {
                                Date startTimeDate = hour24.parse(startTime);
                                Date endTimeDate = hour24.parse(endTime);

                                if (startTimeDate != null && endTimeDate != null) {
                                    if (status == STATUS_PAST) {
                                        classProgress.setProgress(100);
                                    } else if (status == STATUS_PRESENT) {
                                        Date now = hour24.parse(hour24.format(Calendar.getInstance().getTime()));

                                        if (now == null) {
                                            return;
                                        }

                                        if (now.after(endTimeDate)) {
                                            classProgress.setProgress(100);
                                        } else if (now.after(startTimeDate)) {
                                            long duration = endTimeDate.getTime() - startTimeDate.getTime();
                                            long durationComplete = now.getTime() - startTimeDate.getTime();
                                            long durationPending = endTimeDate.getTime() - now.getTime();

                                            setMaxClassProgress(duration);
                                            setClassProgressComplete(durationComplete);
                                            setClassProgressPending(durationPending, 0);
                                        } else {
                                            long duration = endTimeDate.getTime() - startTimeDate.getTime();
                                            long durationPending = startTimeDate.getTime() - now.getTime();

                                            setMaxClassProgress(duration);
                                            setClassProgressPending(duration, durationPending);
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        }
                    });
        }

        private void setClassProgressPending(long duration, long delay) {
            ObjectAnimator objectAnimator = ObjectAnimator.ofInt(
                    this.classProgress,
                    "progress",
                    this.classProgress.getProgress(),
                    this.classProgress.getMax()
            );
            objectAnimator.setDuration(duration);
            objectAnimator.setInterpolator(new LinearInterpolator());
            objectAnimator.setStartDelay(delay);
            objectAnimator.start();
        }

        private void setClassProgressComplete(long duration) {
            int minutes = (int) duration / (1000 * 60);
            this.classProgress.setProgress(minutes * MULTIPLYING_FACTOR, true);
        }

        private void setMaxClassProgress(long duration) {
            int minutes = (int) duration / (1000 * 60);
            this.classProgress.setMax(minutes * MULTIPLYING_FACTOR);
        }
    }
}
