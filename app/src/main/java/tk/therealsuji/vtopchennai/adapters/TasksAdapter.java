package tk.therealsuji.vtopchennai.adapters;

import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.models.UserTask;

public class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.ViewHolder> {

    private final List<UserTask> tasks;
    private final OnTaskActionListener listener;

    public interface OnTaskActionListener {
        void onCompleteToggled(UserTask task, boolean isCompleted);
        void onAlarmToggled(UserTask task, boolean isAlarmEnabled);
        void onDeleteClicked(UserTask task);
        void onEditClicked(UserTask task);
    }

    public TasksAdapter(List<UserTask> tasks, OnTaskActionListener listener) {
        this.tasks = tasks;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_item_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserTask task = tasks.get(position);
        holder.bind(task, listener);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardTask;
        CheckBox checkboxComplete;
        View viewTypeIndicator;
        TextView textTaskTime;
        TextView textTaskTitle;
        MaterialCardView cardTaskTag;
        TextView textTaskTag;
        MaterialButton buttonAlarmToggle;
        MaterialButton buttonDeleteTask;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardTask = itemView.findViewById(R.id.card_task);
            checkboxComplete = itemView.findViewById(R.id.checkbox_complete);
            viewTypeIndicator = itemView.findViewById(R.id.view_type_indicator);
            textTaskTime = itemView.findViewById(R.id.text_task_time);
            textTaskTitle = itemView.findViewById(R.id.text_task_title);
            cardTaskTag = itemView.findViewById(R.id.card_task_tag);
            textTaskTag = itemView.findViewById(R.id.text_task_tag);
            buttonAlarmToggle = itemView.findViewById(R.id.button_alarm_toggle);
            buttonDeleteTask = itemView.findViewById(R.id.button_delete_task);
        }

        private int resolveColorAttr(int attr, int defaultColorRes) {
            TypedValue typedValue = new TypedValue();
            if (itemView.getContext().getTheme().resolveAttribute(attr, typedValue, true)) {
                return typedValue.data;
            }
            return itemView.getContext().getResources().getColor(defaultColorRes, null);
        }

        public void bind(UserTask task, OnTaskActionListener listener) {
            textTaskTitle.setText(task.title);
            textTaskTime.setText(task.startTime + " - " + task.endTime);

            // Handle completion state & styling
            checkboxComplete.setOnCheckedChangeListener(null);
            checkboxComplete.setChecked(task.isCompleted);

            if (task.isCompleted) {
                textTaskTitle.setPaintFlags(textTaskTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                textTaskTitle.setAlpha(0.5f);
                textTaskTime.setAlpha(0.5f);
                cardTask.setAlpha(0.7f);
            } else {
                textTaskTitle.setPaintFlags(textTaskTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                textTaskTitle.setAlpha(1.0f);
                textTaskTime.setAlpha(1.0f);
                cardTask.setAlpha(1.0f);
            }

            checkboxComplete.setOnCheckedChangeListener((buttonView, isChecked) -> {
                listener.onCompleteToggled(task, isChecked);
            });

            // Handle alarm button toggles
            if (task.isAlarmEnabled) {
                buttonAlarmToggle.setIconResource(R.drawable.ic_notifications);
                int primaryColor = resolveColorAttr(R.attr.colorPrimary, android.R.color.holo_blue_dark);
                buttonAlarmToggle.setIconTint(ColorStateList.valueOf(primaryColor));
            } else {
                buttonAlarmToggle.setIconResource(R.drawable.ic_clock);
                int mutedColor = resolveColorAttr(R.attr.colorOnSurfaceVariant, android.R.color.darker_gray);
                buttonAlarmToggle.setIconTint(ColorStateList.valueOf(mutedColor));
            }

            buttonAlarmToggle.setOnClickListener(v -> {
                listener.onAlarmToggled(task, !task.isAlarmEnabled);
            });

            // Type category tag decoration
            if (task.isCollegeClass) {
                int colorPrimary = resolveColorAttr(R.attr.colorPrimary, android.R.color.holo_blue_dark);
                viewTypeIndicator.setBackgroundTintList(ColorStateList.valueOf(colorPrimary));
                cardTaskTag.setVisibility(View.VISIBLE);
                textTaskTag.setText(task.courseCode != null ? "COLLEGE CLASS - " + task.courseCode : "COLLEGE CLASS");

                int containerColor = resolveColorAttr(R.attr.colorPrimaryContainer, android.R.color.holo_blue_light);
                int textColor = resolveColorAttr(R.attr.colorOnPrimaryContainer, android.R.color.white);
                cardTaskTag.setCardBackgroundColor(ColorStateList.valueOf(containerColor));
                textTaskTag.setTextColor(textColor);
            } else {
                int colorTertiary = resolveColorAttr(R.attr.colorTertiary, android.R.color.holo_orange_dark);
                viewTypeIndicator.setBackgroundTintList(ColorStateList.valueOf(colorTertiary));
                cardTaskTag.setVisibility(View.VISIBLE);
                textTaskTag.setText("CUSTOM TASK");

                int containerColor = resolveColorAttr(R.attr.colorTertiaryContainer, android.R.color.holo_orange_light);
                int textColor = resolveColorAttr(R.attr.colorOnTertiaryContainer, android.R.color.white);
                cardTaskTag.setCardBackgroundColor(ColorStateList.valueOf(containerColor));
                textTaskTag.setTextColor(textColor);
            }

            cardTask.setOnClickListener(v -> listener.onEditClicked(task));
            buttonDeleteTask.setOnClickListener(v -> listener.onDeleteClicked(task));
        }
    }
}
