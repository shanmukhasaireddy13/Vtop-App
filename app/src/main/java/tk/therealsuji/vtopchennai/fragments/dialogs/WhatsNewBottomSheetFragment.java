package tk.therealsuji.vtopchennai.fragments.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import tk.therealsuji.vtopchennai.BuildConfig;
import tk.therealsuji.vtopchennai.R;

public class WhatsNewBottomSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "WhatsNewBottomSheetFragment";

    public static WhatsNewBottomSheetFragment newInstance() {
        return new WhatsNewBottomSheetFragment();
    }

    public static void show(FragmentManager fragmentManager) {
        if (fragmentManager.findFragmentByTag(TAG) == null) {
            newInstance().show(fragmentManager, TAG);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_bottom_sheet_whats_new, container, false);

        TextView textVersionTag = view.findViewById(R.id.text_version_tag);
        if (textVersionTag != null) {
            textVersionTag.setText(String.format("Version %s • Latest Release", BuildConfig.VERSION_NAME));
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
}
