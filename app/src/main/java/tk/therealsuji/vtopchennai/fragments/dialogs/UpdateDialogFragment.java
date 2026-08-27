package tk.therealsuji.vtopchennai.fragments.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.noties.markwon.Markwon;
import tk.therealsuji.vtopchennai.BuildConfig;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class UpdateDialogFragment extends DialogFragment {

    private String versionName;
    private String releaseNotes;
    private String downloadUrl;

    private View layoutDownloadProgress;
    private LinearProgressIndicator progressBarDownload;
    private TextView textDownloadStatus;
    private TextView textDownloadPercentage;
    private TextView textDownloadBytes;
    private Button buttonUpdate;
    private Button buttonCancel;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDownloading = false;

    public UpdateDialogFragment() {
        // Required empty public constructor
    }

    public static UpdateDialogFragment newInstance(String versionName, String releaseNotes) {
        return newInstance(versionName, releaseNotes, null);
    }

    public static UpdateDialogFragment newInstance(String versionName, String releaseNotes, String downloadUrl) {
        Bundle args = new Bundle();
        UpdateDialogFragment fragment = new UpdateDialogFragment();

        args.putString("versionName", versionName);
        args.putString("releaseNotes", releaseNotes);
        args.putString("downloadUrl", downloadUrl);

        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogFragment = inflater.inflate(R.layout.layout_dialog_update, container, false);
        Bundle args = getArguments();

        if (args != null) {
            this.versionName = args.getString("versionName", "");
            this.releaseNotes = args.getString("releaseNotes", "");
            this.downloadUrl = args.getString("downloadUrl", "");
        }

        TextView description = dialogFragment.findViewById(R.id.text_view_description);
        description.setText(Html.fromHtml(this.requireContext().getString(R.string.update_message, this.versionName), Html.FROM_HTML_MODE_LEGACY));

        TextView releaseNotesView = dialogFragment.findViewById(R.id.text_view_release_notes);
        Markwon markwon = Markwon.create(this.requireContext());
        markwon.setMarkdown(releaseNotesView, this.releaseNotes);

        layoutDownloadProgress = dialogFragment.findViewById(R.id.layout_download_progress);
        progressBarDownload = dialogFragment.findViewById(R.id.progress_bar_download);
        textDownloadStatus = dialogFragment.findViewById(R.id.text_download_status);
        textDownloadPercentage = dialogFragment.findViewById(R.id.text_download_percentage);
        textDownloadBytes = dialogFragment.findViewById(R.id.text_download_bytes);
        buttonUpdate = dialogFragment.findViewById(R.id.button_update);
        buttonCancel = dialogFragment.findViewById(R.id.button_cancel);

        buttonCancel.setOnClickListener(view -> {
            if (!isDownloading) {
                dismiss();
            }
        });

        buttonUpdate.setOnClickListener(view -> startDirectDownloadAndInstall());

        return dialogFragment;
    }

    private void startDirectDownloadAndInstall() {
        if (isDownloading) return;
        isDownloading = true;

        buttonUpdate.setEnabled(false);
        buttonUpdate.setText("Downloading Update...");
        buttonCancel.setEnabled(false);
        if (layoutDownloadProgress != null) {
            layoutDownloadProgress.setVisibility(View.VISIBLE);
        }

        executorService.execute(() -> {
            Context context = getContext();
            if (context == null) return;

            String targetUrl = downloadUrl;
            if (targetUrl == null || targetUrl.isEmpty()) {
                targetUrl = "https://github.com/shanmukhasaireddy13/Vtop-App/releases/download/" + versionName + "/app-debug.apk";
            }

            File downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir == null) {
                downloadsDir = context.getCacheDir();
            }
            File apkFile = new File(downloadsDir, "VTOP-" + versionName + ".apk");

            HttpURLConnection connection = null;
            InputStream input = null;
            OutputStream output = null;
            boolean downloadSuccess = false;

            try {
                connection = openConnectionWithRedirects(targetUrl);
                int fileLength = connection.getContentLength();

                input = new BufferedInputStream(connection.getInputStream(), 8192);
                output = new FileOutputStream(apkFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                long lastUpdateTime = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);

                    long currentTime = System.currentTimeMillis();
                    if (fileLength > 0 && currentTime - lastUpdateTime > 100) {
                        lastUpdateTime = currentTime;
                        final int progress = (int) (total * 100 / fileLength);
                        final double currentMB = total / (1024.0 * 1024.0);
                        final double totalMB = fileLength / (1024.0 * 1024.0);

                        mainHandler.post(() -> {
                            if (progressBarDownload != null) progressBarDownload.setProgress(progress);
                            if (textDownloadPercentage != null) textDownloadPercentage.setText(progress + "%");
                            if (textDownloadBytes != null) {
                                textDownloadBytes.setText(String.format(Locale.ENGLISH, "%.1f MB / %.1f MB", currentMB, totalMB));
                            }
                        });
                    }
                }

                output.flush();
                downloadSuccess = true;
            } catch (Exception e) {
                downloadSuccess = false;
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (IOException ignored) {}
                if (connection != null) connection.disconnect();
            }

            final boolean success = downloadSuccess;
            mainHandler.post(() -> {
                if (getContext() == null) return;

                if (success && apkFile.exists() && apkFile.length() > 0) {
                    if (textDownloadStatus != null) {
                        textDownloadStatus.setText("Download complete! Launching installer...");
                    }
                    if (progressBarDownload != null) {
                        progressBarDownload.setProgress(100);
                    }
                    if (textDownloadPercentage != null) {
                        textDownloadPercentage.setText("100%");
                    }

                    launchInstaller(apkFile);
                } else {
                    Toast.makeText(requireContext(), "In-app download failed. Switching to system downloader...", Toast.LENGTH_SHORT).show();
                    SettingsRepository.downloadAndInstallUpdate(requireContext(), versionName, downloadUrl);
                    dismiss();
                }
            });
        });
    }

    private void launchInstaller(File apkFile) {
        Context context = getContext();
        if (context == null) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(context, "Please allow VTOP to install updates", Toast.LENGTH_LONG).show();
                    Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.getPackageName()));
                    startActivity(permissionIntent);
                }
            }

            Uri apkUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(installIntent);
            dismiss();
        } catch (Exception e) {
            Toast.makeText(context, "Error opening installer. Please install from Downloads folder.", Toast.LENGTH_LONG).show();
            dismiss();
        }
    }

    private HttpURLConnection openConnectionWithRedirects(String initialUrl) throws IOException {
        String url = initialUrl;
        HttpURLConnection conn = null;
        int redirects = 0;

        while (redirects < 6) {
            URL targetUrl = new URL(url);
            conn = (HttpURLConnection) targetUrl.openConnection();
            conn.setRequestProperty("User-Agent", "VTOP-App/" + BuildConfig.VERSION_NAME);
            conn.setRequestProperty("Accept", "*/*");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null && !newUrl.isEmpty()) {
                    conn.disconnect();
                    url = newUrl;
                    redirects++;
                    continue;
                }
            }
            break;
        }

        return conn;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
