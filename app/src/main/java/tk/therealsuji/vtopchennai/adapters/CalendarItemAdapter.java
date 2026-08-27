package tk.therealsuji.vtopchennai.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.CalendarEvent;

/**
 * ┬─── Calendar Hierarchy
 * ├─ {@link tk.therealsuji.vtopchennai.fragments.RecyclerViewFragment}
 * ╰→ {@link CalendarItemAdapter}   - RecyclerView (Current File)
 */
public class CalendarItemAdapter extends RecyclerView.Adapter<CalendarItemAdapter.ViewHolder> {

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private final List<CalendarEvent> events;

    public CalendarItemAdapter(List<CalendarEvent> events) {
        this.events = events;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout itemView = (LinearLayout) LayoutInflater
                .from(parent.getContext())
                .inflate(R.layout.layout_item_calendar, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(events.get(position));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView dayView;
        private final TextView eventView;
        private final TextView dateView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            dayView   = itemView.findViewById(R.id.text_view_day);
            eventView = itemView.findViewById(R.id.text_view_event);
            dateView  = itemView.findViewById(R.id.text_view_date);
        }

        public void bind(CalendarEvent event) {
            // Day number badge
            dayView.setText(event.day != null ? String.valueOf(event.day) : "-");

            // Event label
            String label = event.event != null ? event.event : "";
            eventView.setText(label);

            // Full date string, e.g. "1 July 2026"
            String monthName = (event.month != null && event.month >= 1 && event.month <= 12)
                    ? MONTH_NAMES[event.month - 1]
                    : "";
            String day  = event.day   != null ? String.valueOf(event.day)  : "";
            String year = event.year  != null ? String.valueOf(event.year) : "";
            dateView.setText(String.format(Locale.ENGLISH, "%s %s %s", day, monthName, year).trim());
        }
    }
}
