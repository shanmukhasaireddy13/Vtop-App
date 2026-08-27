package tk.therealsuji.vtopchennai.adapters;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.CalendarEvent;

public class CalendarGridAdapter extends RecyclerView.Adapter<CalendarGridAdapter.ViewHolder> {

    private static final String[] HEADERS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private final int year;
    private final int month;
    private final Map<Integer, CalendarEvent> eventMap;
    private final String targetDate;

    private final int firstDayOfWeek;
    private final int totalDaysInMonth;
    private final int offset;

    public CalendarGridAdapter(int year, int month, List<CalendarEvent> events) {
        this(year, month, events, null);
    }

    public CalendarGridAdapter(int year, int month, List<CalendarEvent> events, String targetDate) {
        this.year = year;
        this.month = month;
        this.eventMap = new HashMap<>();
        this.targetDate = targetDate;
        for (CalendarEvent event : events) {
            if (event.day != null) {
                eventMap.put(event.day, event);
            }
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        this.firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // Sunday = 1, Saturday = 7
        this.totalDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        this.offset = 7 + (firstDayOfWeek - 1);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_item_calendar_cell, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();

        if (position < 7) {
            // Header cell
            holder.dayView.setText(HEADERS[position]);
            holder.dayView.setTextColor(MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, Color.GRAY));
            holder.dayView.setBackground(null);
            holder.subtextView.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            return;
        }

        if (position < offset) {
            // Empty padding cell
            holder.dayView.setText("");
            holder.dayView.setBackground(null);
            holder.subtextView.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
            return;
        }

        // Active day cell
        int dayNum = position - offset + 1;
        holder.dayView.setText(String.valueOf(dayNum));
        holder.dayView.setTextColor(MaterialColors.getColor(context, R.attr.colorOnSurface, Color.BLACK));
        holder.subtextView.setVisibility(View.GONE);

        // Check if this day is today
        Calendar today = Calendar.getInstance();
        boolean isToday = today.get(Calendar.YEAR) == year
                && today.get(Calendar.MONTH) + 1 == month
                && today.get(Calendar.DAY_OF_MONTH) == dayNum;

        // Check if there is an event for this day
        CalendarEvent event = eventMap.get(dayNum);
        boolean isSunday = ((position % 7) == 0);

        float density = context.getResources().getDisplayMetrics().density;
        float radius = 21 * density;

        int strokeColor = isToday ? MaterialColors.getColor(context, R.attr.colorPrimary, Color.BLUE) : Color.TRANSPARENT;
        int strokeWidth = isToday ? (int) (3 * density) : 0;

        if (event != null && event.event != null) {
            String evtLower = event.event.toLowerCase(Locale.ENGLISH);

            if (evtLower.contains("instructional day") || evtLower.contains("working day") || evtLower.contains("day order")) {
                // Working / Instructional Day - Green theme (Primary color)
                int bgColor = MaterialColors.getColor(context, R.attr.colorPrimaryContainer, Color.parseColor("#E8F5E9"));
                int textColor = MaterialColors.getColor(context, R.attr.colorOnPrimaryContainer, Color.parseColor("#1B5E20"));

                holder.dayView.setTextColor(textColor);
                holder.dayView.setBackground(createRoundedBackground(bgColor, radius, strokeColor, strokeWidth));

                // Extract short day order for subtext, e.g. "Wed Order"
                String subtext = "Working";
                if (evtLower.contains("monday")) subtext = "Mon Order";
                else if (evtLower.contains("tuesday")) subtext = "Tue Order";
                else if (evtLower.contains("wednesday")) subtext = "Wed Order";
                else if (evtLower.contains("thursday")) subtext = "Thu Order";
                else if (evtLower.contains("friday")) subtext = "Fri Order";
                else if (evtLower.contains("saturday")) subtext = "Sat Order";
                else if (evtLower.contains("sunday")) subtext = "Sun Order";

                holder.subtextView.setText(subtext);
                holder.subtextView.setTextColor(textColor);
                holder.subtextView.setVisibility(View.VISIBLE);

            } else if (evtLower.contains("holiday") || evtLower.contains("no class") || evtLower.contains("non-working")) {
                // Holiday - Red theme (Error color)
                int bgColor = MaterialColors.getColor(context, R.attr.colorErrorContainer, Color.parseColor("#FFEBEE"));
                int textColor = MaterialColors.getColor(context, R.attr.colorOnErrorContainer, Color.parseColor("#B71C1C"));

                holder.dayView.setTextColor(textColor);
                holder.dayView.setBackground(createRoundedBackground(bgColor, radius, strokeColor, strokeWidth));
                holder.subtextView.setText("Holiday");
                holder.subtextView.setTextColor(textColor);
                holder.subtextView.setVisibility(View.VISIBLE);
            } else {
                // Standard event - Gray theme (Secondary color)
                int bgColor = MaterialColors.getColor(context, R.attr.colorSecondaryContainer, Color.parseColor("#ECEFF1"));
                int textColor = MaterialColors.getColor(context, R.attr.colorOnSecondaryContainer, Color.parseColor("#37474F"));

                holder.dayView.setTextColor(textColor);
                holder.dayView.setBackground(createRoundedBackground(bgColor, radius, strokeColor, strokeWidth));
                holder.subtextView.setText("Event");
                holder.subtextView.setTextColor(textColor);
                holder.subtextView.setVisibility(View.VISIBLE);
            }

            // Click listener to show event description
            holder.itemView.setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                    .setTitle(String.format(Locale.ENGLISH, "%d/%d/%d", dayNum, month, year))
                    .setMessage(event.event)
                    .setPositiveButton("Close", null)
                    .show());
            holder.itemView.setClickable(true);

        } else if (isSunday) {
            // Default Sunday (Holiday)
            int bgColor = MaterialColors.getColor(context, R.attr.colorErrorContainer, Color.parseColor("#FFEBEE"));
            int textColor = MaterialColors.getColor(context, R.attr.colorOnErrorContainer, Color.parseColor("#B71C1C"));

            holder.dayView.setTextColor(textColor);
            holder.dayView.setBackground(createRoundedBackground(bgColor, radius, strokeColor, strokeWidth));
            holder.subtextView.setText("Sunday");
            holder.subtextView.setTextColor(textColor);
            holder.subtextView.setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        } else {
            // Standard day with no event
            holder.dayView.setTextColor(MaterialColors.getColor(context, R.attr.colorOnSurface, Color.BLACK));
            if (isToday) {
                holder.dayView.setBackground(createRoundedBackground(Color.TRANSPARENT, radius, strokeColor, strokeWidth));
            } else {
                holder.dayView.setBackground(null);
            }
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        }

        // Blinking animation if this day is the target date clicked from the banner
        boolean isBlinkTarget = targetDate != null && targetDate.equals(String.format(Locale.ENGLISH, "%04d-%02d-%02d", year, month, dayNum));
        if (isBlinkTarget) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(holder.dayView, "alpha", 1f, 0.2f, 1f);
            animator.setDuration(500);
            animator.setRepeatCount(4); // flash 4 times (total 5 blinks)
            animator.start();
        }
    }

    @Override
    public int getItemCount() {
        return 7 + (firstDayOfWeek - 1) + totalDaysInMonth;
    }

    private Drawable createRoundedBackground(int color, float radius, int strokeColor, int strokeWidth) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(radius);
        shape.setColor(color);
        if (strokeWidth > 0) {
            shape.setStroke(strokeWidth, strokeColor);
        }
        return shape;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView dayView;
        final TextView subtextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            dayView = itemView.findViewById(R.id.text_view_cell_day);
            subtextView = itemView.findViewById(R.id.text_view_cell_subtext);
        }
    }
}
