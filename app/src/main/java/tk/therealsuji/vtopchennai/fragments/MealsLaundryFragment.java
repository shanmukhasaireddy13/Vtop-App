package tk.therealsuji.vtopchennai.fragments;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import tk.therealsuji.vtopchennai.fragments.dialogs.HostelDataCustomizerBottomSheet;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.MealsLaundryPagerAdapter;

public class MealsLaundryFragment extends Fragment {

    private static final String TAG = "MealsLaundryFragment";

    private AppBarLayout appBarLayout;
    private ViewPager2 viewPager;
    private TabLayoutMediator tabLayoutMediator;

    private JSONObject hostelData;
    private String currentMenuKey;
    private Calendar selectedCalendar = Calendar.getInstance();
    private int selectedMealIndex = 0;

    // View References for Meals
    private View cardDatePicker;
    private TextView textDayDateIndicator;
    private TextView textRelativeDayBadge;
    private View layoutDateActionsRow;
    private TextView textDateHint;
    private MaterialButton buttonTodayQuick;
    private MaterialButton buttonPickDate;
    private TabLayout tabLayoutMeals;
    private ImageView imageMealIcon;
    private TextView textMealTitle;
    private TextView textMealTiming;
    private TextView textMealStatusBadge;
    private LinearLayout layoutMealDishesContainer;
    private TextView textMealContent;
    private View layoutMealSpecialNotes;
    private TextView textMealSpecialNotes;

    // View References for Laundry
    private TextInputEditText editTextRoomNumber;
    private TextView textLaundryRoomBadge;
    private TextView textLaundryNextDate;
    private TextView textLaundryDayName;
    private TextView textLaundryDaysLeft;
    private TextView textLaundrySlotRange;
    private TextView textLaundryFollowingDate;
    private View layoutLaundrySetup;
    private View layoutLaundryActive;
    private MaterialButton buttonSaveRoom;
    private MaterialButton buttonEditRoom;

    private static final String[] DAY_KEYS = {
            "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"
    };

    public MealsLaundryFragment() {
        // Required empty public constructor
    }

    @Override
    public void onResume() {
        super.onResume();

        // Firebase Analytics Logging
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MealsLaundryFragment");
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "Meals & Laundry");
        FirebaseAnalytics.getInstance(this.requireContext()).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_meals_laundry, container, false);

        this.appBarLayout = root.findViewById(R.id.app_bar);
        this.viewPager = root.findViewById(R.id.view_pager_meals_laundry);
        LinearLayout header = root.findViewById(R.id.linear_layout_header);

        float pixelDensity = getResources().getDisplayMetrics().density;

        getParentFragmentManager().setFragmentResultListener("customInsets", getViewLifecycleOwner(), (requestKey, result) -> {
            int systemWindowInsetLeft = result.getInt("systemWindowInsetLeft");
            int systemWindowInsetTop = result.getInt("systemWindowInsetTop");
            int systemWindowInsetRight = result.getInt("systemWindowInsetRight");
            int bottomNavigationHeight = result.getInt("bottomNavigationHeight");
            if (bottomNavigationHeight == 0) {
                bottomNavigationHeight = (int) (80 * pixelDensity);
            }
            final int finalBottomNavigationHeight = bottomNavigationHeight;

            this.appBarLayout.setPadding(
                    systemWindowInsetLeft,
                    systemWindowInsetTop,
                    systemWindowInsetRight,
                    0
            );

            this.viewPager.setPageTransformer((page, position) -> page.setPadding(
                    systemWindowInsetLeft,
                    0,
                    systemWindowInsetRight,
                    (int) (finalBottomNavigationHeight + 20 * pixelDensity)
            ));

            getParentFragmentManager().setFragmentResult("customInsets2", result);
        });

        this.appBarLayout.addOnOffsetChangedListener((appBarLayout1, verticalOffset) -> {
            if (header != null && header.getHeight() > 0) {
                float alpha = 1 - ((float) (-1 * verticalOffset) / header.getHeight());
                header.setAlpha(alpha);
            }
        });

        View buttonCustomize = root.findViewById(R.id.button_customize_hostel_data);
        if (buttonCustomize != null) {
            buttonCustomize.setOnClickListener(v -> {
                HostelDataCustomizerBottomSheet.show(getParentFragmentManager(), () -> {
                    loadHostelData();
                    updateDayAndMenu();
                    updateLaundryUI();
                });
            });
        }

        loadHostelData();
        setupViewPager(root);

        return root;
    }

    private void loadHostelData() {
        try {
            Context context = getContext();
            if (context != null) {
                String customJson = SettingsRepository.getCustomHostelData(context);
                if (customJson != null && !customJson.trim().isEmpty()) {
                    hostelData = new JSONObject(customJson);
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            InputStream is = getResources().openRawResource(R.raw.hostel_data);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            hostelData = new JSONObject(json);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to load hostel data JSON", ex);
        }
    }

    private void setupViewPager(View root) {
        TabLayout tabLayoutMain = root.findViewById(R.id.tab_layout_main);

        MealsLaundryPagerAdapter adapter = new MealsLaundryPagerAdapter(this);
        viewPager.setAdapter(adapter);

        tabLayoutMediator = new TabLayoutMediator(tabLayoutMain, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setIcon(R.drawable.ic_restaurant);
                tab.setContentDescription(getString(R.string.tab_meals));
                TooltipCompat.setTooltipText(tab.view, getString(R.string.tab_meals));
            } else {
                tab.setIcon(R.drawable.ic_laundry);
                tab.setContentDescription(getString(R.string.tab_laundry));
                TooltipCompat.setTooltipText(tab.view, getString(R.string.tab_laundry));
            }
        });
        tabLayoutMediator.attach();
    }

    // Called by MealsLaundryPagerAdapter
    public void setupMealsView(View view) {
        cardDatePicker = view.findViewById(R.id.card_date_picker);
        textDayDateIndicator = view.findViewById(R.id.text_day_date_indicator);
        textRelativeDayBadge = view.findViewById(R.id.text_relative_day_badge);
        layoutDateActionsRow = view.findViewById(R.id.layout_date_actions_row);
        textDateHint = view.findViewById(R.id.text_date_hint);
        buttonTodayQuick = view.findViewById(R.id.button_today_quick);
        buttonPickDate = view.findViewById(R.id.button_pick_date);
        tabLayoutMeals = view.findViewById(R.id.tab_layout_meals);
        imageMealIcon = view.findViewById(R.id.image_meal_icon);
        textMealTitle = view.findViewById(R.id.text_meal_title);
        textMealTiming = view.findViewById(R.id.text_meal_timing);
        textMealStatusBadge = view.findViewById(R.id.text_meal_status_badge);
        layoutMealDishesContainer = view.findViewById(R.id.layout_meal_dishes_container);
        textMealContent = view.findViewById(R.id.text_meal_content);
        layoutMealSpecialNotes = view.findViewById(R.id.layout_meal_special_notes);
        textMealSpecialNotes = view.findViewById(R.id.text_meal_special_notes);

        // Date Picker Trigger
        View.OnClickListener openDatePickerListener = v -> showDatePickerDialog();
        if (cardDatePicker != null) cardDatePicker.setOnClickListener(openDatePickerListener);
        if (buttonPickDate != null) buttonPickDate.setOnClickListener(openDatePickerListener);

        if (buttonTodayQuick != null) {
            buttonTodayQuick.setOnClickListener(v -> {
                selectedCalendar = Calendar.getInstance();
                updateDayAndMenu();
            });
        }

        // Setup Meal Time Tabs with equal dimensions
        tabLayoutMeals.removeAllTabs();
        tabLayoutMeals.addTab(tabLayoutMeals.newTab().setText(R.string.tab_breakfast).setIcon(R.drawable.ic_breakfast));
        tabLayoutMeals.addTab(tabLayoutMeals.newTab().setText(R.string.tab_lunch).setIcon(R.drawable.ic_lunch));
        tabLayoutMeals.addTab(tabLayoutMeals.newTab().setText(R.string.tab_snacks).setIcon(R.drawable.ic_snacks));
        tabLayoutMeals.addTab(tabLayoutMeals.newTab().setText(R.string.tab_dinner).setIcon(R.drawable.ic_dinner));

        // Set initial meal selection based on current hour
        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        if (currentHour < 10) {
            selectedMealIndex = 0; // Breakfast
        } else if (currentHour < 15) {
            selectedMealIndex = 1; // Lunch
        } else if (currentHour < 18) {
            selectedMealIndex = 2; // Snacks
        } else {
            selectedMealIndex = 3; // Dinner
        }

        if (selectedMealIndex >= 0 && selectedMealIndex < tabLayoutMeals.getTabCount()) {
            TabLayout.Tab mealTab = tabLayoutMeals.getTabAt(selectedMealIndex);
            if (mealTab != null) mealTab.select();
        }

        tabLayoutMeals.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedMealIndex = tab.getPosition();
                renderMealContent();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        View buttonAiImportMeals = view.findViewById(R.id.button_ai_import_meals);
        if (buttonAiImportMeals != null) {
            buttonAiImportMeals.setOnClickListener(v -> {
                HostelDataCustomizerBottomSheet.show(getParentFragmentManager(), () -> {
                    loadHostelData();
                    updateDayAndMenu();
                    updateLaundryUI();
                });
            });
        }

        updateDayAndMenu();
    }

    private void showDatePickerDialog() {
        int year = selectedCalendar.get(Calendar.YEAR);
        int month = selectedCalendar.get(Calendar.MONTH);
        int day = selectedCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, selectedYear, selectedMonth, selectedDayOfMonth) -> {
                    selectedCalendar.set(Calendar.YEAR, selectedYear);
                    selectedCalendar.set(Calendar.MONTH, selectedMonth);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, selectedDayOfMonth);
                    updateDayAndMenu();
                },
                year,
                month,
                day
        );
        datePickerDialog.show();
    }

    private void updateDayAndMenu() {
        int weekOfYear = selectedCalendar.get(Calendar.WEEK_OF_YEAR);
        int menuNum = (weekOfYear % 2 == 0) ? 1 : 2;
        currentMenuKey = "menu_" + menuNum;

        // Format Date e.g. "Thursday, 27 Aug"
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMM", Locale.getDefault());
        String formattedDate = sdf.format(selectedCalendar.getTime());

        if (textDayDateIndicator != null) {
            textDayDateIndicator.setText(formattedDate);
        }

        Calendar todayCal = Calendar.getInstance();
        boolean isSameDay = (todayCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                && todayCal.get(Calendar.DAY_OF_YEAR) == selectedCalendar.get(Calendar.DAY_OF_YEAR));

        Calendar tomorrowCal = (Calendar) todayCal.clone();
        tomorrowCal.add(Calendar.DAY_OF_YEAR, 1);
        boolean isTomorrow = (tomorrowCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                && tomorrowCal.get(Calendar.DAY_OF_YEAR) == selectedCalendar.get(Calendar.DAY_OF_YEAR));

        Calendar yesterdayCal = (Calendar) todayCal.clone();
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);
        boolean isYesterday = (yesterdayCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                && yesterdayCal.get(Calendar.DAY_OF_YEAR) == selectedCalendar.get(Calendar.DAY_OF_YEAR));

        if (textRelativeDayBadge != null) {
            if (isSameDay) {
                textRelativeDayBadge.setText("Today's Menu");
            } else if (isTomorrow) {
                textRelativeDayBadge.setText("Tomorrow's Menu");
            } else if (isYesterday) {
                textRelativeDayBadge.setText("Yesterday's Menu");
            } else {
                SimpleDateFormat daySdf = new SimpleDateFormat("EEEE", Locale.getDefault());
                textRelativeDayBadge.setText(daySdf.format(selectedCalendar.getTime()) + "'s Menu");
            }
        }

        if (layoutDateActionsRow != null) {
            layoutDateActionsRow.setVisibility(isSameDay ? View.GONE : View.VISIBLE);
        }

        if (textDateHint != null && !isSameDay) {
            SimpleDateFormat monthYearSdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            textDateHint.setText("Viewing " + monthYearSdf.format(selectedCalendar.getTime()) + " • Menu " + menuNum + " rotation");
        }

        renderMealContent();
    }

    private void renderMealContent() {
        if (hostelData == null || currentMenuKey == null) return;

        int dayOfWeek = selectedCalendar.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday, 6 = Saturday
        boolean isWeekend = (dayOfWeek == 0 || dayOfWeek == 6);

        int mealTitleRes;
        int timingRes;
        int iconRes;
        String mealField;

        switch (selectedMealIndex) {
            case 0:
                mealTitleRes = R.string.tab_breakfast;
                timingRes = isWeekend ? R.string.breakfast_weekend_timings : R.string.breakfast_timings;
                iconRes = R.drawable.ic_breakfast;
                mealField = "breakfast";
                break;
            case 1:
                mealTitleRes = R.string.tab_lunch;
                timingRes = isWeekend ? R.string.lunch_weekend_timings : R.string.lunch_timings;
                iconRes = R.drawable.ic_lunch;
                mealField = "lunch";
                break;
            case 2:
                mealTitleRes = R.string.tab_snacks;
                timingRes = R.string.snacks_timings;
                iconRes = R.drawable.ic_snacks;
                mealField = "snacks";
                break;
            case 3:
            default:
                mealTitleRes = R.string.tab_dinner;
                timingRes = R.string.dinner_timings;
                iconRes = R.drawable.ic_dinner;
                mealField = "dinner";
                break;
        }

        if (textMealTitle != null) textMealTitle.setText(mealTitleRes);
        if (textMealTiming != null) textMealTiming.setText(timingRes);
        if (imageMealIcon != null) imageMealIcon.setImageResource(iconRes);

        if (layoutMealSpecialNotes != null && textMealSpecialNotes != null) {
            if (selectedMealIndex == 1) {
                layoutMealSpecialNotes.setVisibility(View.VISIBLE);
                textMealSpecialNotes.setText(R.string.lunch_notes);
            } else if (selectedMealIndex == 3) {
                layoutMealSpecialNotes.setVisibility(View.VISIBLE);
                textMealSpecialNotes.setText(R.string.dinner_notes);
            } else {
                layoutMealSpecialNotes.setVisibility(View.GONE);
            }
        }

        // Update status badge
        Calendar todayCal = Calendar.getInstance();
        boolean isSameDay = (todayCal.get(Calendar.YEAR) == selectedCalendar.get(Calendar.YEAR)
                && todayCal.get(Calendar.DAY_OF_YEAR) == selectedCalendar.get(Calendar.DAY_OF_YEAR));

        if (textMealStatusBadge != null) {
            if (isSameDay) {
                textMealStatusBadge.setText("TODAY");
            } else {
                textMealStatusBadge.setText("SCHEDULE");
            }
        }

        if (layoutMealDishesContainer == null) return;
        layoutMealDishesContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        String mealRaw = "";
        try {
            if (hostelData != null && hostelData.has("meals")) {
                JSONObject mealsObj = hostelData.getJSONObject("meals");
                if (mealsObj.has(currentMenuKey)) {
                    JSONObject menuObj = mealsObj.getJSONObject(currentMenuKey);
                    String dayKey = DAY_KEYS[dayOfWeek];
                    if (menuObj.has(dayKey)) {
                        JSONObject dayObj = menuObj.getJSONObject(dayKey);
                        mealRaw = dayObj.optString(mealField, "");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing meals data", e);
        }

        if (mealRaw == null || mealRaw.trim().isEmpty() || mealRaw.equalsIgnoreCase("Not available")) {
            View emptyView = getLayoutInflater().inflate(R.layout.layout_meal_empty_state, layoutMealDishesContainer, false);
            View buttonImport = emptyView.findViewById(R.id.button_empty_import_ai);
            if (buttonImport != null) {
                buttonImport.setOnClickListener(v -> {
                    HostelDataCustomizerBottomSheet.show(getParentFragmentManager(), () -> {
                        loadHostelData();
                        updateDayAndMenu();
                        updateLaundryUI();
                    });
                });
            }
            layoutMealDishesContainer.addView(emptyView);
            return;
        }

        String[] items = mealRaw.split(",\\s*");
        for (int i = 0; i < items.length; i++) {
            String dishName = items[i].trim();
            if (dishName.isEmpty()) continue;

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

            // Dish bullet icon
            ImageView bulletIcon = new ImageView(getContext());
            LinearLayout.LayoutParams bulletParams = new LinearLayout.LayoutParams(
                    (int) (18 * density), (int) (18 * density)
            );
            bulletParams.setMarginEnd((int) (10 * density));
            bulletIcon.setLayoutParams(bulletParams);
            bulletIcon.setImageResource(R.drawable.ic_restaurant);
            bulletIcon.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.secondary_75));

            // Dish Name
            TextView dishText = new TextView(getContext());
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f
            );
            dishText.setLayoutParams(textParams);
            dishText.setText(dishName);
            dishText.setTextSize(15);
            dishText.setTypeface(dishText.getTypeface(), Typeface.NORMAL);

            row.addView(bulletIcon);
            row.addView(dishText);

            // Special item highlight tag
            String lower = dishName.toLowerCase(Locale.ENGLISH);
            String tagLabel = null;
            if (lower.contains("chicken") || lower.contains("egg") || lower.contains("omelette") || lower.contains("fish")) {
                tagLabel = "NON-VEG";
            } else if (lower.contains("ice cream") || lower.contains("jamun") || lower.contains("halwa") || lower.contains("payasam") || lower.contains("cake")) {
                tagLabel = "DESSERT";
            } else if (lower.contains("shake") || lower.contains("lassi") || lower.contains("juice") || lower.contains("tea") || lower.contains("coffee") || lower.contains("milk")) {
                tagLabel = "BEVERAGE";
            } else if (lower.contains("biryani") || lower.contains("paneer") || lower.contains("dosa") || lower.contains("paratha") || lower.contains("poori")) {
                tagLabel = "SPECIAL";
            }

            if (tagLabel != null) {
                TextView tagView = new TextView(getContext());
                tagView.setText(tagLabel);
                tagView.setTextSize(9);
                tagView.setTypeface(tagView.getTypeface(), Typeface.BOLD);
                tagView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.background_pill_badge));
                tagView.setPadding((int) (8 * density), (int) (2 * density), (int) (8 * density), (int) (2 * density));
                row.addView(tagView);
            }

            layoutMealDishesContainer.addView(row);

            // Hairline separator between dishes
            if (i < items.length - 1) {
                View divider = new View(getContext());
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (int) (1 * density)
                );
                dividerParams.setMargins((int) (28 * density), 0, 0, 0);
                divider.setLayoutParams(dividerParams);
                divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_50));
                layoutMealDishesContainer.addView(divider);
            }
        }
    }

    // Called by MealsLaundryPagerAdapter
    public void setupLaundryView(View view) {
        editTextRoomNumber = view.findViewById(R.id.edit_text_room_number);
        textLaundryRoomBadge = view.findViewById(R.id.text_laundry_room_badge);
        textLaundryNextDate = view.findViewById(R.id.text_laundry_next_date);
        textLaundryDayName = view.findViewById(R.id.text_laundry_day_name);
        textLaundryDaysLeft = view.findViewById(R.id.text_laundry_days_left);
        textLaundrySlotRange = view.findViewById(R.id.text_laundry_slot_range);
        textLaundryFollowingDate = view.findViewById(R.id.text_laundry_following_date);
        layoutLaundrySetup = view.findViewById(R.id.layout_laundry_setup);
        layoutLaundryActive = view.findViewById(R.id.layout_laundry_active);
        buttonSaveRoom = view.findViewById(R.id.button_save_room);
        buttonEditRoom = view.findViewById(R.id.button_edit_room);

        SharedPreferences prefs = requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE);
        String savedRoom = prefs.getString("roomNumber", "");

        buttonSaveRoom.setOnClickListener(v -> {
            String room = editTextRoomNumber.getText() != null ? editTextRoomNumber.getText().toString().trim() : "";
            if (!room.isEmpty()) {
                prefs.edit().putString("roomNumber", room).apply();
                updateLaundryUI(room);
            } else {
                editTextRoomNumber.setError("Please enter your room number");
            }
        });

        buttonEditRoom.setOnClickListener(v -> {
            layoutLaundrySetup.setVisibility(View.VISIBLE);
            layoutLaundryActive.setVisibility(View.GONE);
            editTextRoomNumber.setText(prefs.getString("roomNumber", ""));
            editTextRoomNumber.requestFocus();
        });

        View buttonAiImportLaundry = view.findViewById(R.id.button_ai_import_laundry);
        if (buttonAiImportLaundry != null) {
            buttonAiImportLaundry.setOnClickListener(v -> {
                HostelDataCustomizerBottomSheet.show(getParentFragmentManager(), () -> {
                    loadHostelData();
                    updateDayAndMenu();
                    updateLaundryUI();
                });
            });
        }

        updateLaundryUI(savedRoom);
    }

    private void updateLaundryUI() {
        if (getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences("Settings", Context.MODE_PRIVATE);
        updateLaundryUI(prefs.getString("roomNumber", ""));
    }

    private void updateLaundryUI(String roomStr) {
        if (roomStr == null || roomStr.trim().isEmpty()) {
            if (layoutLaundrySetup != null) layoutLaundrySetup.setVisibility(View.VISIBLE);
            if (layoutLaundryActive != null) layoutLaundryActive.setVisibility(View.GONE);
        } else {
            if (layoutLaundrySetup != null) layoutLaundrySetup.setVisibility(View.GONE);
            if (layoutLaundryActive != null) layoutLaundryActive.setVisibility(View.VISIBLE);
            if (textLaundryRoomBadge != null) textLaundryRoomBadge.setText("Room " + roomStr);
            calculateNextLaundry(roomStr);
        }
    }

    private void calculateNextLaundry(String roomStr) {
        if (hostelData == null || textLaundryNextDate == null) return;
        if (roomStr == null || roomStr.trim().isEmpty()) {
            textLaundryNextDate.setText("Enter room number");
            textLaundryDaysLeft.setText("");
            return;
        }

        try {
            int roomNum = Integer.parseInt(roomStr.trim());
            JSONObject laundryObj = hostelData.optJSONObject("laundry");
            if (laundryObj == null || laundryObj.length() == 0) {
                textLaundryNextDate.setText("No Schedule");
                if (textLaundryDayName != null) textLaundryDayName.setText("Schedule Empty");
                if (textLaundryDaysLeft != null) textLaundryDaysLeft.setText("Empty");
                if (textLaundrySlotRange != null) textLaundrySlotRange.setText("Import schedule with AI");
                if (textLaundryFollowingDate != null) textLaundryFollowingDate.setText("Tap 'AI Import' below to add circular");
                return;
            }
            Calendar cal = Calendar.getInstance();

            int foundDay = -1;
            int daysLeft = -1;
            String matchedSlot = "";

            Calendar followingCal = null;

            // Search ahead up to 60 days to find both 1st (Next) and 2nd (Following) upcoming laundry drop-off dates
            for (int i = 0; i <= 60; i++) {
                Calendar checkCal = (Calendar) cal.clone();
                checkCal.add(Calendar.DAY_OF_MONTH, i);
                int d = checkCal.get(Calendar.DAY_OF_MONTH);
                String rangeStr = laundryObj.optString(String.valueOf(d), "");

                if (isRoomInRange(roomNum, rangeStr)) {
                    if (foundDay == -1) {
                        foundDay = d;
                        daysLeft = i;
                        matchedSlot = rangeStr;
                    } else if (followingCal == null) {
                        followingCal = checkCal;
                        break;
                    }
                }
            }

            if (foundDay == -1) {
                textLaundryNextDate.setText("Not scheduled");
                if (textLaundryDayName != null) textLaundryDayName.setText("");
                if (textLaundryDaysLeft != null) textLaundryDaysLeft.setText("No slot");
                if (textLaundrySlotRange != null) textLaundrySlotRange.setText("");
                if (textLaundryFollowingDate != null) textLaundryFollowingDate.setText("Continuous 6-day cycle");
            } else {
                String suffix = "th";
                if (foundDay % 10 == 1 && foundDay != 11) suffix = "st";
                else if (foundDay % 10 == 2 && foundDay != 12) suffix = "nd";
                else if (foundDay % 10 == 3 && foundDay != 13) suffix = "rd";

                Calendar targetCal = (Calendar) cal.clone();
                targetCal.add(Calendar.DAY_OF_MONTH, daysLeft);
                String monthName = targetCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
                String dayOfWeek = targetCal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());

                textLaundryNextDate.setText(String.format(Locale.getDefault(), "%d%s %s", foundDay, suffix, monthName));
                if (textLaundryDayName != null) {
                    textLaundryDayName.setText(dayOfWeek);
                }

                if (textLaundryDaysLeft != null) {
                    if (daysLeft == 0) {
                        textLaundryDaysLeft.setText("Today!");
                    } else if (daysLeft == 1) {
                        textLaundryDaysLeft.setText("Tomorrow");
                    } else {
                        textLaundryDaysLeft.setText(String.format(Locale.getDefault(), "in %d days", daysLeft));
                    }
                }

                if (textLaundrySlotRange != null) {
                    String cleanSlot = matchedSlot.replace("-", "–").trim();
                    textLaundrySlotRange.setText("Rooms: " + cleanSlot);
                }

                if (textLaundryFollowingDate != null) {
                    if (followingCal != null) {
                        int fDay = followingCal.get(Calendar.DAY_OF_MONTH);
                        String fSuffix = "th";
                        if (fDay % 10 == 1 && fDay != 11) fSuffix = "st";
                        else if (fDay % 10 == 2 && fDay != 12) fSuffix = "nd";
                        else if (fDay % 10 == 3 && fDay != 13) fSuffix = "rd";

                        String fMonth = followingCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
                        String fDayOfWeek = followingCal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
                        textLaundryFollowingDate.setText(String.format(Locale.getDefault(), "%s, %d%s %s", fDayOfWeek, fDay, fSuffix, fMonth));
                    } else {
                        textLaundryFollowingDate.setText("Continuous 6-day cycle");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing laundry data", e);
        }
    }

    private boolean isRoomInRange(int room, String rangeStr) {
        if (rangeStr == null || rangeStr.isEmpty()) return false;
        String[] parts = rangeStr.split("-");
        if (parts.length == 2) {
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                return room >= start && room <= end;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void onDestroyView() {
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
            tabLayoutMediator = null;
        }
        if (viewPager != null) {
            viewPager.setAdapter(null);
            viewPager = null;
        }
        appBarLayout = null;
        super.onDestroyView();
    }
}
