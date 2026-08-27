package tk.therealsuji.vtopchennai.fragments.dialogs;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.noties.markwon.Markwon;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import tk.therealsuji.vtopchennai.BuildConfig;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class WhatsNewBottomSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "WhatsNewBottomSheetFragment";
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    private String versionName;
    private String releaseNotes;

    public static WhatsNewBottomSheetFragment newInstance() {
        return new WhatsNewBottomSheetFragment();
    }

    public static WhatsNewBottomSheetFragment newInstance(String versionName, String releaseNotes) {
        WhatsNewBottomSheetFragment fragment = new WhatsNewBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString("versionName", versionName);
        args.putString("releaseNotes", releaseNotes);
        fragment.setArguments(args);
        return fragment;
    }

    public static void show(FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null) {
            newInstance().show(fragmentManager, TAG);
        }
    }

    public static void show(FragmentManager fragmentManager, String versionName, String releaseNotes) {
        if (fragmentManager.findFragmentByTag(TAG) == null) {
            newInstance(versionName, releaseNotes).show(fragmentManager, TAG);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_bottom_sheet_whats_new, container, false);

        Bundle args = getArguments();
        if (args != null) {
            this.versionName = args.getString("versionName", null);
            this.releaseNotes = args.getString("releaseNotes", null);
        }

        if (this.versionName == null || this.versionName.isEmpty()) {
            this.versionName = BuildConfig.VERSION_NAME;
        }

        TextView textVersionTag = view.findViewById(R.id.text_version_tag);
        if (textVersionTag != null) {
            textVersionTag.setText(String.format("Version %s • Release Changelog", this.versionName));
        }

        TextView textReleaseNotes = view.findViewById(R.id.text_release_notes);
        ProgressBar progressBar = view.findViewById(R.id.progress_loading_notes);
        Markwon markwon = Markwon.create(requireContext());

        if (this.releaseNotes != null && !this.releaseNotes.isEmpty()) {
            markwon.setMarkdown(textReleaseNotes, this.releaseNotes);
        } else {
            Context context = getContext();
            String cachedNotes = context != null ? SettingsRepository.getLatestReleaseNotes(context) : null;
            if (cachedNotes != null && !cachedNotes.isEmpty()) {
                markwon.setMarkdown(textReleaseNotes, cachedNotes);
            } else {
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                compositeDisposable.add(SettingsRepository.fetchAboutJson(true).subscribe(about -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    String notes = about.optString("releaseNotes", "");
                    if (notes.isEmpty()) {
                        notes = "### What's New in v" + BuildConfig.VERSION_NAME + "\n\n* Parallel Multi-Stream Data Sync\n* Smart Session Persistence (bypasses captchas on quick refreshes)\n* Updated August 2026 Mess Menu & Dynamic Meal Timings\n* Continuous 60-Day Laundry Tracker\n* Direct In-App APK Auto-Updater";
                    }
                    if (textReleaseNotes != null && getContext() != null) {
                        markwon.setMarkdown(textReleaseNotes, notes);
                    }
                }, error -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (textReleaseNotes != null && getContext() != null) {
                        markwon.setMarkdown(textReleaseNotes, "### What's New in v" + BuildConfig.VERSION_NAME + "\n\n* Bug fixes, performance improvements, and hostel schedule updates.");
                    }
                }));
            }
        }

        View.OnClickListener dismissListener = v -> dismiss();

        View buttonClose = view.findViewById(R.id.button_close_whats_new);
        if (buttonClose != null) {
            buttonClose.setOnClickListener(dismissListener);
        }

        View buttonExplore = view.findViewById(R.id.button_explore);
        if (buttonExplore != null) {
            buttonExplore.setOnClickListener(dismissListener);
        }

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        compositeDisposable.clear();
    }
}
