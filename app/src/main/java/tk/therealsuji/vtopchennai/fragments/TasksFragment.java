package tk.therealsuji.vtopchennai.fragments;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.TasksAdapter;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.interfaces.TimetableDao;
import tk.therealsuji.vtopchennai.interfaces.UserTaskDao;
import tk.therealsuji.vtopchennai.models.Timetable;
import tk.therealsuji.vtopchennai.models.UserTask;
import tk.therealsuji.vtopchennai.receivers.TasksNotificationReceiver;

public class TasksFragment extends Fragment implements TasksAdapter.OnTaskActionListener {

    private TabLayout tabLayoutDays;
    private RecyclerView recyclerViewTasks;
    private LinearLayout layoutEmptyState;
    private FloatingActionButton fabAddTask;
    private MaterialButton buttonGenerateTemplate;
    private MaterialButton buttonAddTaskEmpty;

    private TasksAdapter adapter;
    private final List<UserTask> taskList = new ArrayList<>();
    private int selectedDayOfWeek = 1; // Default to Monday (1). Sunday is 0.

    private AppDatabase db;
    private UserTaskDao userTaskDao;
    private TimetableDao timetableDao;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = AppDatabase.getInstance(requireContext());
        userTaskDao = db.userTaskDao();
        timetableDao = db.timetableDao();

        // Bind Views
        tabLayoutDays = view.findViewById(R.id.tab_layout_days);
        recyclerViewTasks = view.findViewById(R.id.recycler_view_tasks);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        fabAddTask = view.findViewById(R.id.fab_add_task);
        buttonGenerateTemplate = view.findViewById(R.id.button_generate_template);
        buttonAddTaskEmpty = view.findViewById(R.id.button_add_task_empty);

        // Setup Day Tabs
        setupDayTabs();

        // Setup Recycler View
        recyclerViewTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TasksAdapter(taskList, this);
        recyclerViewTasks.setAdapter(adapter);

        // Click Handlers
        fabAddTask.setOnClickListener(v -> showAddOrEditTaskDialog(null));
        buttonAddTaskEmpty.setOnClickListener(v -> showAddOrEditTaskDialog(null));
        buttonGenerateTemplate.setOnClickListener(v -> generateDefaultDayTemplate());

        // Select current day by default
        selectCurrentDay();

        View appBar = view.findViewById(R.id.app_bar);
        getParentFragmentManager().setFragmentResultListener("customInsets", getViewLifecycleOwner(), (requestKey, result) -> {
            int systemWindowInsetLeft = result.getInt("systemWindowInsetLeft");
            int systemWindowInsetTop = result.getInt("systemWindowInsetTop");
            int systemWindowInsetRight = result.getInt("systemWindowInsetRight");
            int systemWindowInsetBottom = result.getInt("systemWindowInsetBottom");
            int bottomNavigationHeight = result.getInt("bottomNavigationHeight");
            float pixelDensity = getResources().getDisplayMetrics().density;
            if (bottomNavigationHeight == 0) {
                bottomNavigationHeight = (int) (80 * pixelDensity);
            }

            if (appBar != null) {
                appBar.setPadding(
                        systemWindowInsetLeft,
                        systemWindowInsetTop,
                        systemWindowInsetRight,
                        0
                );
            }

            if (recyclerViewTasks != null) {
                recyclerViewTasks.setPaddingRelative(
                        systemWindowInsetLeft + (int) (16 * pixelDensity),
                        (int) (16 * pixelDensity),
                        systemWindowInsetRight + (int) (16 * pixelDensity),
                        bottomNavigationHeight + systemWindowInsetBottom + (int) (16 * pixelDensity)
                );
            }

            if (layoutEmptyState != null) {
                layoutEmptyState.setPadding(
                        systemWindowInsetLeft + (int) (24 * pixelDensity),
                        (int) (24 * pixelDensity),
                        systemWindowInsetRight + (int) (24 * pixelDensity),
                        bottomNavigationHeight + systemWindowInsetBottom + (int) (24 * pixelDensity)
                );
            }

            if (fabAddTask != null) {
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams layoutParams = 
                        (androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams) fabAddTask.getLayoutParams();
                layoutParams.bottomMargin = bottomNavigationHeight + systemWindowInsetBottom + (int) (16 * pixelDensity);
                layoutParams.leftMargin = systemWindowInsetLeft + (int) (16 * pixelDensity);
                layoutParams.rightMargin = systemWindowInsetRight + (int) (16 * pixelDensity);
                fabAddTask.setLayoutParams(layoutParams);
            }
        });
    }

    private void setupDayTabs() {
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String day : days) {
            tabLayoutDays.addTab(tabLayoutDays.newTab().setText(day));
        }

        tabLayoutDays.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // Map Mon=0 -> 1, Tue=1 -> 2, ..., Sat=5 -> 6, Sun=6 -> 0
                int position = tab.getPosition();
                if (position == 6) {
                    selectedDayOfWeek = 0;
                } else {
                    selectedDayOfWeek = position + 1;
                }
                loadTasksForSelectedDay();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void selectCurrentDay() {
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        int tabPosition;

        // Map Calendar day constants to tab index: Mon=2 -> index 0, ..., Sun=1 -> index 6
        if (day == Calendar.SUNDAY) {
            tabPosition = 6;
            selectedDayOfWeek = 0;
        } else {
            tabPosition = day - 2;
            selectedDayOfWeek = day - 1;
        }

        TabLayout.Tab tab = tabLayoutDays.getTabAt(tabPosition);
        if (tab != null) {
            tab.select();
        } else {
            loadTasksForSelectedDay();
        }
    }

    private void loadTasksForSelectedDay() {
        compositeDisposable.add(
                userTaskDao.getTasksForDay(selectedDayOfWeek)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(tasks -> {
                            taskList.clear();
                            taskList.addAll(tasks);
                            adapter.notifyDataSetChanged();

                            if (tasks.isEmpty()) {
                                recyclerViewTasks.setVisibility(View.GONE);
                                layoutEmptyState.setVisibility(View.VISIBLE);
                            } else {
                                recyclerViewTasks.setVisibility(View.VISIBLE);
                                layoutEmptyState.setVisibility(View.GONE);
                            }
                        }, throwable -> {
                            Toast.makeText(getContext(), "Error loading tasks", Toast.LENGTH_SHORT).show();
                        })
        );
    }

    private void generateDefaultDayTemplate() {
        // Fetch college timetable for this day first, then combine with standard default routine
        compositeDisposable.add(
                timetableDao.get(selectedDayOfWeek)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(collegeSlots -> {
                            List<UserTask> templateTasks = new ArrayList<>();

                            // Standard Default Routine (Wake up to Sleep)
                            templateTasks.add(new UserTask("Wake Up & Morning Routine", "07:00", "08:00", selectedDayOfWeek, true, false, null));
                            templateTasks.add(new UserTask("Breakfast", "08:15", "08:45", selectedDayOfWeek, true, false, null));

                            // Add college classes if any exist
                            for (Timetable.AllData slot : collegeSlots) {
                                templateTasks.add(new UserTask(
                                        slot.courseCode + " - Class",
                                        slot.startTime,
                                        slot.endTime,
                                        selectedDayOfWeek,
                                        true,
                                        true,
                                        slot.courseCode
                                ));
                            }

                            templateTasks.add(new UserTask("Lunch Break", "13:00", "14:00", selectedDayOfWeek, true, false, null));
                            templateTasks.add(new UserTask("Evening Routine / Refreshment", "17:00", "18:00", selectedDayOfWeek, false, false, null));
                            templateTasks.add(new UserTask("Self Study & Assignments", "18:30", "20:30", selectedDayOfWeek, true, false, null));
                            templateTasks.add(new UserTask("Dinner", "20:45", "21:30", selectedDayOfWeek, false, false, null));
                            templateTasks.add(new UserTask("Leisure / Reading", "21:45", "22:45", selectedDayOfWeek, false, false, null));
                            templateTasks.add(new UserTask("Sleep Routine", "23:00", "07:00", selectedDayOfWeek, true, false, null));

                            // Save all template tasks to Room
                            compositeDisposable.add(
                                    userTaskDao.insertAll(templateTasks)
                                            .subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(() -> {
                                                // After inserting, load tasks and schedule alarms
                                                loadTasksForSelectedDay();
                                                for (UserTask task : templateTasks) {
                                                    scheduleTaskAlarm(task);
                                                }
                                                Toast.makeText(getContext(), "Day schedule pre-populated!", Toast.LENGTH_SHORT).show();
                                            }, throwable -> {
                                                Toast.makeText(getContext(), "Error creating template", Toast.LENGTH_SHORT).show();
                                            })
                            );

                        }, throwable -> {
                            // If college slots query fails, still generate the default routine
                            List<UserTask> templateTasks = new ArrayList<>();
                            templateTasks.add(new UserTask("Wake Up & Morning Routine", "07:00", "08:00", selectedDayOfWeek, true, false, null));
                            templateTasks.add(new UserTask("Breakfast", "08:15", "08:45", selectedDayOfWeek, true, false, null));
                            templateTasks.add(new UserTask("Lunch Break", "13:00", "14:00", selectedDayOfWeek, true, false, null));
                            templateTasks.add(new UserTask("Evening Routine / Refreshment", "17:00", "18:00", selectedDayOfWeek, false, false, null));
                            templateTasks.add(new UserTask("Self Study & Assignments", "18:30", "20:30", selectedDayOfWeek, true, false, null));
                            templateTasks.add(new UserTask("Dinner", "20:45", "21:30", selectedDayOfWeek, false, false, null));
                            templateTasks.add(new UserTask("Sleep Routine", "23:00", "07:00", selectedDayOfWeek, true, false, null));

                            compositeDisposable.add(
                                    userTaskDao.insertAll(templateTasks)
                                            .subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(() -> {
                                                loadTasksForSelectedDay();
                                                for (UserTask task : templateTasks) {
                                                    scheduleTaskAlarm(task);
                                                }
                                                Toast.makeText(getContext(), "Default schedule pre-populated!", Toast.LENGTH_SHORT).show();
                                            }, t -> Toast.makeText(getContext(), "Error creating default template", Toast.LENGTH_SHORT).show())
                            );
                        })
        );
    }

    private void showAddOrEditTaskDialog(@Nullable UserTask taskToEdit) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_task, null);
        
        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        TextInputEditText editTextTitle = dialogView.findViewById(R.id.edit_text_task_title);
        MaterialButton buttonStartTime = dialogView.findViewById(R.id.button_start_time);
        MaterialButton buttonEndTime = dialogView.findViewById(R.id.button_end_time);
        MaterialSwitch switchAlarm = dialogView.findViewById(R.id.switch_alarm);
        MaterialButton buttonCancel = dialogView.findViewById(R.id.button_dialog_cancel);
        MaterialButton buttonSave = dialogView.findViewById(R.id.button_dialog_save);

        final String[] startTime = {"07:00"};
        final String[] endTime = {"08:00"};

        if (taskToEdit != null) {
            dialogTitle.setText("Edit Task");
            editTextTitle.setText(taskToEdit.title);
            startTime[0] = taskToEdit.startTime;
            endTime[0] = taskToEdit.endTime;
            buttonStartTime.setText(taskToEdit.startTime);
            buttonEndTime.setText(taskToEdit.endTime);
            switchAlarm.setChecked(taskToEdit.isAlarmEnabled);
        }

        // Time Picker Listeners
        buttonStartTime.setOnClickListener(v -> {
            String[] parts = startTime[0].split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            TimePickerDialog picker = new TimePickerDialog(getContext(), (view, hourOfDay, min) -> {
                startTime[0] = String.format(Locale.ENGLISH, "%02d:%02d", hourOfDay, min);
                buttonStartTime.setText(startTime[0]);
            }, hour, minute, true);
            picker.show();
        });

        buttonEndTime.setOnClickListener(v -> {
            String[] parts = endTime[0].split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            TimePickerDialog picker = new TimePickerDialog(getContext(), (view, hourOfDay, min) -> {
                endTime[0] = String.format(Locale.ENGLISH, "%02d:%02d", hourOfDay, min);
                buttonEndTime.setText(endTime[0]);
            }, hour, minute, true);
            picker.show();
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.Theme_Material3_DayNight)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        buttonCancel.setOnClickListener(v -> dialog.dismiss());

        buttonSave.setOnClickListener(v -> {
            String title = editTextTitle.getText() != null ? editTextTitle.getText().toString().trim() : "";
            if (title.isEmpty()) {
                editTextTitle.setError("Title cannot be empty");
                return;
            }

            if (taskToEdit != null) {
                // Edit mode
                taskToEdit.title = title;
                taskToEdit.startTime = startTime[0];
                taskToEdit.endTime = endTime[0];
                taskToEdit.isAlarmEnabled = switchAlarm.isChecked();

                compositeDisposable.add(
                        userTaskDao.update(taskToEdit)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> {
                                    if (taskToEdit.isAlarmEnabled) {
                                        scheduleTaskAlarm(taskToEdit);
                                    } else {
                                        cancelTaskAlarm(taskToEdit);
                                    }
                                    loadTasksForSelectedDay();
                                    dialog.dismiss();
                                    Toast.makeText(getContext(), "Task updated!", Toast.LENGTH_SHORT).show();
                                }, t -> Toast.makeText(getContext(), "Error updating task", Toast.LENGTH_SHORT).show())
                );
            } else {
                // New task mode
                UserTask newTask = new UserTask(
                        title,
                        startTime[0],
                        endTime[0],
                        selectedDayOfWeek,
                        switchAlarm.isChecked(),
                        false,
                        null
                );

                compositeDisposable.add(
                        userTaskDao.insert(newTask)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(() -> {
                                    // Retrieve tasks again to get auto-generated ID for alarms
                                    loadTasksAndScheduleAlarm(newTask);
                                    dialog.dismiss();
                                    Toast.makeText(getContext(), "Task added!", Toast.LENGTH_SHORT).show();
                                }, t -> Toast.makeText(getContext(), "Error saving task", Toast.LENGTH_SHORT).show())
                );
            }
        });

        dialog.show();
    }

    private void loadTasksAndScheduleAlarm(UserTask taskToSchedule) {
        compositeDisposable.add(
                userTaskDao.getTasksForDay(selectedDayOfWeek)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(tasks -> {
                            taskList.clear();
                            taskList.addAll(tasks);
                            adapter.notifyDataSetChanged();

                            recyclerViewTasks.setVisibility(View.VISIBLE);
                            layoutEmptyState.setVisibility(View.GONE);

                            // Find the freshly added task to obtain its autogenerated ID
                            for (UserTask task : tasks) {
                                if (task.title.equals(taskToSchedule.title) && task.startTime.equals(taskToSchedule.startTime)) {
                                    scheduleTaskAlarm(task);
                                    break;
                                }
                            }
                        }, throwable -> {})
        );
    }

    // Task Actions callbacks
    @Override
    public void onCompleteToggled(UserTask task, boolean isCompleted) {
        task.isCompleted = isCompleted;
        compositeDisposable.add(
                userTaskDao.update(task)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            // Update list silently
                            int position = taskList.indexOf(task);
                            if (position != -1) {
                                adapter.notifyItemChanged(position);
                            }
                        }, throwable -> {})
        );
    }

    @Override
    public void onAlarmToggled(UserTask task, boolean isAlarmEnabled) {
        task.isAlarmEnabled = isAlarmEnabled;
        compositeDisposable.add(
                userTaskDao.update(task)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            if (isAlarmEnabled) {
                                scheduleTaskAlarm(task);
                            } else {
                                cancelTaskAlarm(task);
                            }
                            int position = taskList.indexOf(task);
                            if (position != -1) {
                                adapter.notifyItemChanged(position);
                            }
                        }, throwable -> {})
        );
    }

    @Override
    public void onDeleteClicked(UserTask task) {
        compositeDisposable.add(
                userTaskDao.delete(task)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            cancelTaskAlarm(task);
                            loadTasksForSelectedDay();
                            Toast.makeText(getContext(), "Task deleted", Toast.LENGTH_SHORT).show();
                        }, throwable -> {})
        );
    }

    @Override
    public void onEditClicked(UserTask task) {
        showAddOrEditTaskDialog(task);
    }

    // Alarm scheduling helpers
    private void scheduleTaskAlarm(UserTask task) {
        if (!task.isAlarmEnabled) return;

        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(requireContext(), TasksNotificationReceiver.class);
        intent.putExtra("task_id", task.id);
        intent.putExtra("task_title", task.title);
        intent.putExtra("task_time", task.startTime + " - " + task.endTime);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                task.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar calendar = Calendar.getInstance();
        int targetDay;
        if (task.dayOfWeek == 0) {
            targetDay = Calendar.SUNDAY;
        } else {
            targetDay = task.dayOfWeek + 1; // 1 -> Calendar.MONDAY (2), etc.
        }

        calendar.set(Calendar.DAY_OF_WEEK, targetDay);
        String[] timeParts = task.startTime.split(":");
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0]));
        calendar.set(Calendar.MINUTE, Integer.parseInt(timeParts[1]));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.WEEK_OF_YEAR, 1);
        }

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY * 7,
                pendingIntent
        );
    }

    private void cancelTaskAlarm(UserTask task) {
        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(requireContext(), TasksNotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                task.id,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    @Override
    public void onDestroyView() {
        tabLayoutDays = null;
        recyclerViewTasks = null;
        layoutEmptyState = null;
        fabAddTask = null;
        buttonGenerateTemplate = null;
        buttonAddTaskEmpty = null;
        adapter = null;
        db = null;
        userTaskDao = null;
        timetableDao = null;
        super.onDestroyView();
        compositeDisposable.clear();
    }
}
