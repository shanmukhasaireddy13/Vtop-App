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

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.adapters.MealsLaundryPagerAdapter;
import tk.therealsuji.vtopchennai.helpers.FeatureFlagsRepository;

public class MealsLaundryFragment extends Fragment {

    private static final String TAG = "MealsLaundryFragment";

    // --- Domain Models & Enums ---

    private enum MainTab {
        MEALS(R.drawable.ic_restaurant, R.string.tab_meals),
        LAUNDRY(R.drawable.ic_laundry, R.string.tab_laundry);

        final int iconRes;
        final int labelRes;

        MainTab(int iconRes, int labelRes) {
            this.iconRes = iconRes;
            this.labelRes = labelRes;
        }
    }

    public enum MealType {
        BREAKFAST(0, R.string.tab_breakfast, R.drawable.ic_breakfast, "breakfast", R.string.breakfast_timings, R.string.breakfast_weekend_timings, 0, 10),
        LUNCH(1, R.string.tab_lunch, R.drawable.ic_lunch, "lunch", R.string.lunch_timings, R.string.lunch_weekend_timings, R.string.lunch_notes, 15),
        SNACKS(2, R.string.tab_snacks, R.drawable.ic_snacks, "snacks", R.string.snacks_timings, R.string.snacks_timings, 0, 18),
        DINNER(3, R.string.tab_dinner, R.drawable.ic_dinner, "dinner", R.string.dinner_timings, R.string.dinner_timings, R.string.dinner_notes, 24);

        public final int index;
        public final int titleRes;
        public final int iconRes;
        public final String key;
        public final int weekdayTimingRes;
        public final int weekendTimingRes;
        public final int notesRes;
        public final int hourCutoff;

        MealType(int index, int titleRes, int iconRes, String key, int weekdayTimingRes, int weekendTimingRes, int notesRes, int hourCutoff) {
            this.index = index;
            this.titleRes = titleRes;
            this.iconRes = iconRes;
            this.key = key;
            this.weekdayTimingRes = weekdayTimingRes;
            this.weekendTimingRes = weekendTimingRes;
            this.notesRes = notesRes;
            this.hourCutoff = hourCutoff;
        }

        public static MealType fromHour(int hour) {
            for (MealType meal : values()) {
                if (hour < meal.hourCutoff) return meal;
            }
            return DINNER;
        }

        public static MealType fromIndex(int index) {
            for (MealType meal : values()) {
                if (meal.index == index) return meal;
            }
            return BREAKFAST;
        }
    }

    private enum DishCategory {
        NON_VEG("NON-VEG", "chicken", "egg", "omelette", "fish", "mutton", "prawn"),
        DESSERT("DESSERT", "ice cream", "jamun", "halwa", "payasam", "cake", "kheer"),
        BEVERAGE("BEVERAGE", "shake", "lassi", "juice", "tea", "coffee", "milk"),
        SPECIAL("SPECIAL", "biryani", "paneer", "dosa", "paratha", "poori", "fried rice");

        final String label;
        final String[] keywords;

        DishCategory(String label, String... keywords) {
            this.label = label;
            this.keywords = keywords;
        }

        static String findTag(String dishName) {
            if (dishName == null) return null;
            String lower = dishName.toLowerCase(Locale.ENGLISH);
            for (DishCategory cat : values()) {
                for (String kw : cat.keywords) {
                    if (lower.contains(kw)) return cat.label;
                }
            }
            return null;
        }
    }

    private static final String[] DAY_KEYS = {
            "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"
    };

    // --- View State & References ---

    private AppBarLayout appBarLayout;
    private ViewPager2 viewPager;
    private TabLayoutMediator tabLayoutMediator;

    private JSONObject hostelData;
    private String currentMenuKey;
    private Calendar selectedCalendar = Calendar.getInstance();
    private MealType selectedMeal = MealType.BREAKFAST;

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

    public MealsLaundryFragment() {
        // Required empty public constructor
    }

    @Override
    public void onResume() {
        super.onResume();
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
            updateCustomizerVisibility(buttonCustomize);
            FeatureFlagsRepository.addListener(() -> updateCustomizerVisibility(buttonCustomize));
            buttonCustomize.setOnClickListener(v -> openCustomizerSheet());
        }

        loadHostelData();
        setupViewPager(root);

        return root;
    }

    private void updateCustomizerVisibility(View buttonCustomize) {
        if (buttonCustomize == null || getContext() == null) return;
        buttonCustomize.setVisibility(FeatureFlagsRepository.isAiCustomizerEnabled(getContext()) ? View.VISIBLE : View.GONE);
    }

    private void openCustomizerSheet() {
        HostelDataCustomizerBottomSheet.show(getParentFragmentManager(), () -> {
            loadHostelData();
            updateDayAndMenu();
            updateLaundryUI();
        });
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

        try (InputStream is = getResources().openRawResource(R.raw.hostel_data)) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            hostelData = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            Log.e(TAG, "Failed to load hostel data JSON", ex);
        }
    }

    private void setupViewPager(View root) {
        TabLayout tabLayoutMain = root.findViewById(R.id.tab_layout_main);
        MealsLaundryPagerAdapter adapter = new MealsLaundryPagerAdapter(this);
        viewPager.setAdapter(adapter);

        tabLayoutMediator = new TabLayoutMediator(tabLayoutMain, viewPager, (tab, position) -> {
            if (position >= 0 && position < MainTab.values().length) {
                MainTab mainTab = MainTab.values()[position];
                tab.setIcon(mainTab.iconRes);
                tab.setContentDescription(getString(mainTab.labelRes));
                TooltipCompat.setTooltipText(tab.view, getString(mainTab.labelRes));
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
        layoutMealSpecialNotes = view.findViewById(R.id.layout_meal_special_notes);
        textMealSpecialNotes = view.findViewById(R.id.text_meal_special_notes);

        View.OnClickListener openDatePicker = v -> showDatePickerDialog();
        if (cardDatePicker != null) cardDatePicker.setOnClickListener(openDatePicker);
        if (buttonPickDate != null) buttonPickDate.setOnClickListener(openDatePicker);

        if (buttonTodayQuick != null) {
            buttonTodayQuick.setOnClickListener(v -> {
                selectedCalendar = Calendar.getInstance();
                updateDayAndMenu();
            });
        }

        // Setup Meal Time Tabs from MealType enum
        tabLayoutMeals.removeAllTabs();
        for (MealType meal : MealType.values()) {
            tabLayoutMeals.addTab(tabLayoutMeals.newTab().setText(meal.titleRes).setIcon(meal.iconRes));
        }

        selectedMeal = MealType.fromHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        TabLayout.Tab activeTab = tabLayoutMeals.getTabAt(selectedMeal.index);
        if (activeTab != null) activeTab.select();

        tabLayoutMeals.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedMeal = MealType.fromIndex(tab.getPosition());
                renderMealContent();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        View buttonAiImportMeals = view.findViewById(R.id.button_ai_import_meals);
        if (buttonAiImportMeals != null) {
            buttonAiImportMeals.setOnClickListener(v -> openCustomizerSheet());
        }

        updateDayAndMenu();
    }

    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, day) -> {
                    selectedCalendar.set(Calendar.YEAR, year);
                    selectedCalendar.set(Calendar.MONTH, month);
                    selectedCalendar.set(Calendar.DAY_OF_MONTH, day);
                    updateDayAndMenu();
                },
                selectedCalendar.get(Calendar.YEAR),
                selectedCalendar.get(Calendar.MONTH),
                selectedCalendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void updateDayAndMenu() {
        int dayOfMonth = selectedCalendar.get(Calendar.DAY_OF_MONTH);

        // Resolve menu key from meal_schedule if present
        String menuKey = null;
        if (hostelData != null && hostelData.has("meal_schedule")) {
            JSONObject scheduleObj = hostelData.optJSONObject("meal_schedule");
            if (scheduleObj != null && scheduleObj.has(String.valueOf(dayOfMonth))) {
                menuKey = scheduleObj.optString(String.valueOf(dayOfMonth), "");
            }
        }

        // Fallback for legacy JSON or missing date mapping
        if (menuKey == null || menuKey.isEmpty()) {
            int weekOfYear = selectedCalendar.get(Calendar.WEEK_OF_YEAR);
            int menuNum = (weekOfYear % 2 == 0) ? 1 : 2;
            menuKey = "menu_" + menuNum;
        }

        currentMenuKey = menuKey;
        String menuDisplay = formatMenuDisplayName(currentMenuKey);

        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMM", Locale.getDefault());
        if (textDayDateIndicator != null) {
            textDayDateIndicator.setText(sdf.format(selectedCalendar.getTime()));
        }

        Calendar todayCal = Calendar.getInstance();
        boolean isSameDay = isSameDay(todayCal, selectedCalendar);

        if (textRelativeDayBadge != null) {
            textRelativeDayBadge.setText(getRelativeDayLabel(selectedCalendar, todayCal) + " • " + menuDisplay);
        }

        if (layoutDateActionsRow != null) {
            layoutDateActionsRow.setVisibility(isSameDay ? View.GONE : View.VISIBLE);
        }

        if (textDateHint != null && !isSameDay) {
            SimpleDateFormat monthYearSdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
            textDateHint.setText("Viewing " + monthYearSdf.format(selectedCalendar.getTime()) + " • " + menuDisplay);
        }

        renderMealContent();
    }

    private String formatMenuDisplayName(String menuKey) {
        if (menuKey == null || menuKey.isEmpty()) return "Menu";
        if (menuKey.equalsIgnoreCase("menu_1")) return "Menu 1";
        if (menuKey.equalsIgnoreCase("menu_2")) return "Menu 2";
        if (menuKey.startsWith("menu_")) return "Menu " + menuKey.substring(5);
        if (menuKey.startsWith("special_")) return "Special " + menuKey.substring(8);

        String[] parts = menuKey.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.length() > 0) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String getRelativeDayLabel(Calendar target, Calendar today) {
        if (isSameDay(target, today)) return "Today's Menu";

        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        if (isSameDay(target, tomorrow)) return "Tomorrow's Menu";

        Calendar yesterday = (Calendar) today.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (isSameDay(target, yesterday)) return "Yesterday's Menu";

        return new SimpleDateFormat("EEEE", Locale.getDefault()).format(target.getTime()) + "'s Menu";
    }

    private static boolean isSameDay(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    private void renderMealContent() {
        if (hostelData == null || currentMenuKey == null) return;

        int dayOfWeek = selectedCalendar.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday, 6 = Saturday
        boolean isWeekend = (dayOfWeek == 0 || dayOfWeek == 6);

        if (textMealTitle != null) textMealTitle.setText(selectedMeal.titleRes);
        if (textMealTiming != null) {
            textMealTiming.setText(isWeekend ? selectedMeal.weekendTimingRes : selectedMeal.weekdayTimingRes);
        }
        if (imageMealIcon != null) imageMealIcon.setImageResource(selectedMeal.iconRes);

        if (layoutMealSpecialNotes != null && textMealSpecialNotes != null) {
            if (selectedMeal.notesRes != 0) {
                layoutMealSpecialNotes.setVisibility(View.VISIBLE);
                textMealSpecialNotes.setText(selectedMeal.notesRes);
            } else {
                layoutMealSpecialNotes.setVisibility(View.GONE);
            }
        }

        if (textMealStatusBadge != null) {
            textMealStatusBadge.setText(isSameDay(Calendar.getInstance(), selectedCalendar) ? "TODAY" : "SCHEDULE");
        }

        if (layoutMealDishesContainer == null) return;
        layoutMealDishesContainer.removeAllViews();

        String mealRaw = extractMealRaw(dayOfWeek);
        if (mealRaw == null || mealRaw.trim().isEmpty() || mealRaw.equalsIgnoreCase("Not available")) {
            renderEmptyState();
            return;
        }

        renderDishesList(mealRaw);
    }

    private String extractMealRaw(int dayOfWeek) {
        try {
            if (hostelData != null && hostelData.has("meals")) {
                JSONObject mealsObj = hostelData.getJSONObject("meals");
                if (mealsObj.has(currentMenuKey)) {
                    JSONObject menuObj = mealsObj.getJSONObject(currentMenuKey);
                    String dayKey = DAY_KEYS[dayOfWeek];
                    if (menuObj.has(dayKey)) {
                        return menuObj.getJSONObject(dayKey).optString(selectedMeal.key, "");
                    } else if (menuObj.has(selectedMeal.key)) {
                        // Direct single-day menu definition (e.g. special events)
                        return menuObj.optString(selectedMeal.key, "");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting meal data", e);
        }
        return "";
    }

    private void renderEmptyState() {
        View emptyView = getLayoutInflater().inflate(R.layout.layout_meal_empty_state, layoutMealDishesContainer, false);
        View buttonImport = emptyView.findViewById(R.id.button_empty_import_ai);
        if (buttonImport != null) {
            buttonImport.setOnClickListener(v -> openCustomizerSheet());
        }
        layoutMealDishesContainer.addView(emptyView);
    }

    private void renderDishesList(String mealRaw) {
        float density = getResources().getDisplayMetrics().density;
        String[] items = mealRaw.split(",\\s*");

        for (int i = 0; i < items.length; i++) {
            String dishName = items[i].trim();
            if (dishName.isEmpty()) continue;

            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

            ImageView bulletIcon = new ImageView(getContext());
            LinearLayout.LayoutParams bulletParams = new LinearLayout.LayoutParams(
                    (int) (18 * density), (int) (18 * density)
            );
            bulletParams.setMarginEnd((int) (10 * density));
            bulletIcon.setLayoutParams(bulletParams);
            bulletIcon.setImageResource(R.drawable.ic_restaurant);
            bulletIcon.setImageTintList(ContextCompat.getColorStateList(requireContext(), R.color.secondary_75));

            TextView dishText = new TextView(getContext());
            dishText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            dishText.setText(dishName);
            dishText.setTextSize(15);
            dishText.setTypeface(dishText.getTypeface(), Typeface.NORMAL);

            row.addView(bulletIcon);
            row.addView(dishText);

            String tag = DishCategory.findTag(dishName);
            if (tag != null) {
                TextView tagView = new TextView(getContext());
                tagView.setText(tag);
                tagView.setTextSize(9);
                tagView.setTypeface(tagView.getTypeface(), Typeface.BOLD);
                tagView.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.background_pill_badge));
                tagView.setPadding((int) (8 * density), (int) (2 * density), (int) (8 * density), (int) (2 * density));
                row.addView(tagView);
            }

            layoutMealDishesContainer.addView(row);

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
            buttonAiImportLaundry.setOnClickListener(v -> openCustomizerSheet());
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
                String suffix = getDayNumberSuffix(foundDay);
                Calendar targetCal = (Calendar) cal.clone();
                targetCal.add(Calendar.DAY_OF_MONTH, daysLeft);
                String monthName = targetCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
                String dayOfWeek = targetCal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());

                textLaundryNextDate.setText(String.format(Locale.getDefault(), "%d%s %s", foundDay, suffix, monthName));
                if (textLaundryDayName != null) textLaundryDayName.setText(dayOfWeek);

                if (textLaundryDaysLeft != null) {
                    if (daysLeft == 0) textLaundryDaysLeft.setText("Today!");
                    else if (daysLeft == 1) textLaundryDaysLeft.setText("Tomorrow");
                    else textLaundryDaysLeft.setText(String.format(Locale.getDefault(), "in %d days", daysLeft));
                }

                if (textLaundrySlotRange != null) {
                    textLaundrySlotRange.setText("Rooms: " + matchedSlot.replace("-", "–").trim());
                }

                if (textLaundryFollowingDate != null) {
                    if (followingCal != null) {
                        int fDay = followingCal.get(Calendar.DAY_OF_MONTH);
                        String fSuffix = getDayNumberSuffix(fDay);
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

    private static String getDayNumberSuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        switch (day % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
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
