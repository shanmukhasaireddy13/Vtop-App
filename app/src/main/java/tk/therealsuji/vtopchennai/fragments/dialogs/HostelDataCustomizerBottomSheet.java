package tk.therealsuji.vtopchennai.fragments.dialogs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class HostelDataCustomizerBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "HostelDataCustomizerBottomSheet";

    public interface OnHostelDataUpdatedListener {
        void onHostelDataUpdated();
    }

    private OnHostelDataUpdatedListener listener;
    private TextInputLayout textInputLayoutJson;
    private TextInputEditText editTextJson;

    public static HostelDataCustomizerBottomSheet newInstance() {
        return new HostelDataCustomizerBottomSheet();
    }

    public void setOnHostelDataUpdatedListener(OnHostelDataUpdatedListener listener) {
        this.listener = listener;
    }

    public static void show(FragmentManager fragmentManager, OnHostelDataUpdatedListener listener) {
        if (fragmentManager.findFragmentByTag(TAG) == null) {
            HostelDataCustomizerBottomSheet fragment = newInstance();
            fragment.setOnHostelDataUpdatedListener(listener);
            fragment.show(fragmentManager, TAG);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_bottom_sheet_hostel_data, container, false);

        textInputLayoutJson = view.findViewById(R.id.text_input_layout_json);
        editTextJson = view.findViewById(R.id.edit_text_json);

        // Populate with active custom JSON or default bundled JSON
        loadInitialJson();

        view.findViewById(R.id.button_close_customizer).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.button_copy_ai_prompt).setOnClickListener(v -> {
            copyToClipboard("VTOP AI Prompt", SettingsRepository.getAiPromptTemplate());
            Toast.makeText(requireContext(), "AI Prompt copied! Send it with your menu photo to ChatGPT / Gemini.", Toast.LENGTH_LONG).show();
        });

        view.findViewById(R.id.button_copy_json_format).setOnClickListener(v -> {
            String sampleFormat = "{\n  \"laundry\": {\n    \"1\": \"101 - 322\",\n    \"2\": \"\",\n    \"3\": \"323 - 514\"\n  },\n  \"meals\": {\n    \"menu_1\": {\n      \"monday\": {\n        \"breakfast\": \"Idli, Sambar, Chutney\",\n        \"lunch\": \"Rice, Dal, Curd\",\n        \"snacks\": \"Samosa, Tea\",\n        \"dinner\": \"Chapati, Paneer\"\n      }\n    }\n  }\n}";
            copyToClipboard("Hostel JSON Format", sampleFormat);
            Toast.makeText(requireContext(), "JSON schema template copied!", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.button_paste_clipboard).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    String clean = cleanJsonText(text.toString());
                    editTextJson.setText(clean);
                    textInputLayoutJson.setError(null);
                    Toast.makeText(requireContext(), "Pasted from clipboard!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), "Clipboard is empty.", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.button_reset_default).setOnClickListener(v -> {
            SettingsRepository.resetCustomHostelData(requireContext());
            if (listener != null) {
                listener.onHostelDataUpdated();
            }
            Toast.makeText(requireContext(), "Reset to official August 2026 schedule.", Toast.LENGTH_SHORT).show();
            dismiss();
        });

        view.findViewById(R.id.button_save_hostel_data).setOnClickListener(v -> validateAndSave());

        return view;
    }

    private void loadInitialJson() {
        Context context = getContext();
        if (context == null) return;

        String customJson = SettingsRepository.getCustomHostelData(context);
        if (customJson != null && !customJson.trim().isEmpty()) {
            try {
                JSONObject obj = new JSONObject(customJson);
                editTextJson.setText(obj.toString(2));
                return;
            } catch (Exception ignored) {
                editTextJson.setText(customJson);
                return;
            }
        }

        try {
            InputStream is = getResources().openRawResource(R.raw.hostel_data);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String rawJson = new String(buffer, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(rawJson);
            editTextJson.setText(obj.toString(2));
        } catch (Exception ignored) {
        }
    }

    private void validateAndSave() {
        if (editTextJson == null || textInputLayoutJson == null) return;

        String input = editTextJson.getText() != null ? editTextJson.getText().toString() : "";
        String cleaned = cleanJsonText(input);

        if (cleaned.isEmpty()) {
            textInputLayoutJson.setError("JSON content cannot be empty.");
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject(cleaned);
            if (!jsonObject.has("meals") && !jsonObject.has("laundry")) {
                textInputLayoutJson.setError("JSON must contain at least 'meals' or 'laundry' key.");
                return;
            }

            textInputLayoutJson.setError(null);
            SettingsRepository.saveCustomHostelData(requireContext(), jsonObject.toString());

            if (listener != null) {
                listener.onHostelDataUpdated();
            }

            Toast.makeText(requireContext(), "Hostel schedule updated successfully!", Toast.LENGTH_SHORT).show();
            dismiss();
        } catch (Exception e) {
            textInputLayoutJson.setError("Invalid JSON format: " + e.getMessage());
        }
    }

    private String cleanJsonText(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        // Remove markdown code blocks if present (e.g. ```json ... ```)
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private void copyToClipboard(String label, String text) {
        Context context = getContext();
        if (context == null) return;
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }
}
