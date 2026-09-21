package tk.therealsuji.vtopchennai.services;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Base64;
import android.view.ViewGroup;
import android.net.http.SslError;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.AppDatabase;
import tk.therealsuji.vtopchennai.helpers.NotificationHelper;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.interfaces.AttendanceDao;
import tk.therealsuji.vtopchennai.interfaces.CalendarDao;
import tk.therealsuji.vtopchennai.interfaces.CoursesDao;
import tk.therealsuji.vtopchennai.interfaces.ExamsDao;
import tk.therealsuji.vtopchennai.interfaces.MarksDao;
import tk.therealsuji.vtopchennai.interfaces.ReceiptsDao;
import tk.therealsuji.vtopchennai.interfaces.StaffDao;
import tk.therealsuji.vtopchennai.interfaces.TimetableDao;
import tk.therealsuji.vtopchennai.models.Attendance;
import tk.therealsuji.vtopchennai.models.CalendarEvent;
import tk.therealsuji.vtopchennai.models.Course;
import tk.therealsuji.vtopchennai.models.CumulativeMark;
import tk.therealsuji.vtopchennai.models.Exam;
import tk.therealsuji.vtopchennai.models.Mark;
import tk.therealsuji.vtopchennai.models.Receipt;
import tk.therealsuji.vtopchennai.models.Slot;
import tk.therealsuji.vtopchennai.models.Spotlight;
import tk.therealsuji.vtopchennai.models.Staff;
import tk.therealsuji.vtopchennai.models.Timetable;

public class VTOPService extends Service {
    public static final int CAPTCHA_DEFAULT = 1;
    public static final int CAPTCHA_GRECATPCHA = 2;

    private static final String END_SERVICE_ACTION = "END_SERVICE_ACTION";

    AppDatabase appDatabase;
    ServiceBinder serviceBinder;

    public VTOPService() {
        this.serviceBinder = new ServiceBinder(this);
    }
    boolean isWebViewDestroyed;
    Callback callback;
    Integer counter, maxProgress, progress;
    NotificationCompat.Builder notification;
    NotificationManager notificationManager;
    PageState pageState;
    SharedPreferences sharedPreferences;
    WebView webView;

    Map<Integer, Course> theoryCourses, labCourses, projectCourses;
    Map<String, CumulativeMark> cumulativeMarks;
    Map<String, Slot> theorySlots, labSlots, projectSlots;
    Map<String, String> semesters;
    String username, password, semesterID;
    CompositeDisposable compositeDisposable;

    private static final int TOTAL_PARALLEL_STREAMS = 8;
    private final java.util.concurrent.atomic.AtomicInteger completedStreams = new java.util.concurrent.atomic.AtomicInteger(0);

    private void onStreamComplete(String streamName) {
        int finished = completedStreams.incrementAndGet();
        updateProgress(null);
        if (finished >= TOTAL_PARALLEL_STREAMS) {
            finishUp();
        }
    }

    private void startParallelDownloads() {
        this.completedStreams.set(0);
        this.downloadTimetable();
        this.downloadAttendance();
        this.downloadMarks();
        this.downloadExamSchedule();
        this.downloadProctor();
        this.downloadSpotlight();
        this.downloadReceipts();
        this.downloadCalendar();
    }

    public void clearCallback() {
        this.callback = null;
    }

    @Override
    public void onCreate() {
        Intent endServiceIntent = new Intent(this, VTOPService.class);
        endServiceIntent.setAction(END_SERVICE_ACTION);
        PendingIntent endServicePendingIntent = PendingIntent.getService(
                this,
                0,
                endServiceIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT
        );

        NotificationHelper notificationHelper = new NotificationHelper(getApplicationContext());
        this.notificationManager = notificationHelper.getManager();

        this.notification = notificationHelper.notifySync(null, null);
        this.notification.addAction(R.drawable.ic_close, getString(R.string.cancel), endServicePendingIntent);
        this.notification.setOngoing(true);
        this.notification.setProgress(0, 0, true);

        this.appDatabase = AppDatabase.getInstance(getApplicationContext());
        this.sharedPreferences = SettingsRepository.getSharedPreferences(getApplicationContext());
        this.compositeDisposable = new CompositeDisposable();

        this.createWebView();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null && intent.getAction().equals(END_SERVICE_ACTION)) {
            this.endService(true);
            this.notificationManager.cancel(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD);  // In case the notification isn't removed for some reason
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                this.startForeground(
                        SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD,
                        this.notification.build(),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                );
            } else {
                this.startForeground(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, this.notification.build());
            }

            this.counter = 0;
            this.maxProgress = 13;

            SharedPreferences encryptedSharedPreferences = SettingsRepository.getEncryptedSharedPreferences(getApplicationContext());

            if (encryptedSharedPreferences == null) {
                error(102, "Failed to fetch credentials.");
            } else {
                this.username = encryptedSharedPreferences.getString("username", null);
                this.password = encryptedSharedPreferences.getString("password", null);

                long lastRefreshed = this.sharedPreferences.getLong("lastRefreshed", 0);
                long now = System.currentTimeMillis();
                boolean canReuseSession = (now - lastRefreshed < 1200000) && CookieManager.getInstance().hasCookies();

                if (canReuseSession) {
                    this.reloadPage("/content", false);
                } else {
                    this.reloadPage("", false);
                }
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        this.compositeDisposable.dispose();
        super.onDestroy();
    }

    /**
     * Function to create a fresh WebView
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        String authorisedUserAgent = this.sharedPreferences.getString("authorisedUserAgent", null);

        this.webView = new WebView(getApplicationContext());
        this.webView.addJavascriptInterface(this, "Android");
        
        WebSettings settings = this.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        if (authorisedUserAgent != null && !authorisedUserAgent.trim().isEmpty()) {
            settings.setUserAgentString(authorisedUserAgent);
        }
        
        this.webView.setBackgroundColor(Color.TRANSPARENT);
        this.webView.setHorizontalScrollBarEnabled(false);
        this.webView.setVerticalScrollBarEnabled(false);
        this.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Proceed on university certificate authority / captive portal SSL errors
                handler.proceed();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                /*
                 *  JSON response format
                 *  {
                 *      "page_type": "LANDING"|"HOME"|"LOGIN"|"BODY_NOT_READY"
                 *  }
                 */
                view.evaluateJavascript("(function() {" +
                        "try {" +
                        "    if (!document || !document.body || document.body.children.length === 0) {" +
                        "        return { page_type: 'BODY_NOT_READY' };" +
                        "    }" +
                        "    if (document.querySelector('input#authorizedIDX, input[name=\"authorizedID\"], #authorizedIDX')) {" +
                        "        return { page_type: 'HOME' };" +
                        "    }" +
                        "    if (document.querySelector('form#vtopLoginForm, form[name=\"vtopLoginForm\"], #vtopLoginForm, input[name=\"username\"], input[name=\"password\"], #captchaBlock, input[name=\"captchaStr\"]')) {" +
                        "        return { page_type: 'LOGIN' };" +
                        "    }" +
                        "    if (document.querySelector('#stdForm, form[name=\"stdForm\"], a[onclick*=\"submitForm\"], a[onclick*=\"stdForm\"], #student')) {" +
                        "        return { page_type: 'LANDING' };" +
                        "    }" +
                        "    return { page_type: 'LANDING' };" +
                        "} catch(e) {" +
                        "    return { page_type: 'BODY_NOT_READY' };" +
                        "}" +
                        "})();", responseString -> {
                    if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                        return;
                    }
                    try {
                        JSONObject response = new JSONObject(responseString);
                        String pageType = response.optString("page_type", "LANDING");

                        switch (pageType) {
                            case "LANDING":
                                if (counter >= 10) {
                                    error(101, "Couldn't connect to the server.");
                                    endService(true);
                                    return;
                                }

                                openSignIn();
                                ++counter;

                                pageState = PageState.LANDING;
                                break;
                            case "LOGIN":
                                if (pageState == PageState.LOGIN) {
                                    break;
                                }

                                getCaptchaType();
                                pageState = PageState.LOGIN;
                                break;
                            case "HOME":
                                if (pageState == PageState.HOME) {
                                    break;
                                }

                                getSemesters();
                                pageState = PageState.HOME;
                                break;
                            case "BODY_NOT_READY":
                                break;
                            default:
                                break;
                        }
                    } catch (Exception ignored) {
                    }
                });
            }
        });
    }

    /**
     * VTOP randomly blocks user agents (I'm guessing to prevent people from using this app).
     * If a user agent is blocked, a new authorised one is fetched from my server and stored in shared preferences.
     */
    private void updateUserAgent() {
        SettingsRepository.fetchAboutJson(false)
                .subscribe(new Observer<JSONObject>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull JSONObject about) {
                        try {
                            String defaultUA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";
                            String authorisedUserAgent = about.optString("authorisedUserAgent", defaultUA);
                            sharedPreferences.edit().putString("authorisedUserAgent", authorisedUserAgent).apply();
                            if (webView != null) {
                                webView.getSettings().setUserAgentString(authorisedUserAgent);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                        reloadPage("", true);
                    }
                });
    }

    /**
     * Function to brutally destroy the WebView
     */
    private void destroyWebView() {
        this.webView.onPause();
        this.webView.removeAllViews();
        this.webView.destroyDrawingCache();
        this.webView.pauseTimers();

        ViewGroup webViewParent = (ViewGroup) webView.getParent();
        if (webViewParent != null) {
            webViewParent.removeView(webView);
        }

        this.webView.destroy();
        this.isWebViewDestroyed = true;
    }

    /**
     * Function to terminate the download
     *
     * @param force If force is true, it will end the service no matter what
     */
    public void endService(boolean force) {
        if (!force && this.progress != -1) {
            return;
        }

        this.destroyWebView();
        stopSelf();
        stopForeground(true);

        try {
            this.callback.onServiceEnd();
        } catch (Exception ignored) {
        }
    }

    /**
     * Function to reload the page after clearing all cache and history.
     */
    private void reloadPage(String path, boolean destroySession) {
        if (this.isWebViewDestroyed) {
            this.createWebView();
            this.isWebViewDestroyed = false;
        }

        this.pageState = null;
        this.progress = -1;

        this.notification.setContentTitle(getString(R.string.server_connect));
        this.notification.setContentText(null);
        this.notification.setProgress(0, 0, true);
        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, this.notification.build());

        if (destroySession) {
            CookieManager.getInstance().removeAllCookies(null);
            this.webView.clearCache(true);
            this.webView.clearHistory();
        }

        String targetUrl = SettingsRepository.VTOP_BASE_URL;
        if (path != null && !path.trim().isEmpty()) {
            if (!targetUrl.endsWith("/") && !path.startsWith("/")) {
                targetUrl += "/" + path;
            } else if (targetUrl.endsWith("/") && path.startsWith("/")) {
                targetUrl += path.substring(1);
            } else {
                targetUrl += path;
            }
        }
        this.webView.loadUrl(targetUrl);
    }

    /**
     * Function to update the download progress in the notification
     *
     * @param currentDownload The string ID for the current download
     */
    private void updateProgress(Integer currentDownload) {
        this.notification.setProgress(maxProgress, ++progress, false);
        this.notification.setContentText(progress + " / " + maxProgress);

        if (currentDownload != null) {
            this.notification.setContentTitle(getString(currentDownload));
        }

        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, notification.build());
    }

    /**
     * Function to handle errors.
     */
    private void error(final int errorCode, final String errorMessage) {
        Toast.makeText(getApplicationContext(), "Error " + errorCode + ". " + errorMessage, Toast.LENGTH_SHORT).show();
        this.reloadPage("", true);

        // Firebase Crashlytics Logging
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.log("VTOP error " + errorCode + ". " + errorMessage);
    }

    /**
     * Function to open the login page.
     */
    private void openSignIn() {
        /*
         *  JSON response format
         *  {
         *      "success": true|false
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "try {" +
                "    var form = document.getElementById('stdForm') || document.querySelector('#stdForm, form[action*=\"prelogin\"]');" +
                "    if (form) {" +
                "        if (typeof submitForm === 'function') {" +
                "            submitForm('stdForm');" +
                "        } else {" +
                "            form.submit();" +
                "        }" +
                "        return { success: true, method: 'submit' };" +
                "    }" +
                "    var studentImg = document.getElementById('student') || document.querySelector('.cardStudent a, a[onclick*=\"stdForm\"]');" +
                "    if (studentImg) {" +
                "        studentImg.click();" +
                "        return { success: true, method: 'click' };" +
                "    }" +
                "    return { success: false, error: 'Form not found' };" +
                "} catch(e) {" +
                "    return { success: false, error: e.toString() };" +
                "}" +
                "})();", responseString -> {
            try {
                if (responseString != null && !responseString.equals("null")) {
                    JSONObject res = new JSONObject(responseString);
                    if (!res.optBoolean("success", false)) {
                        this.reloadPage("", false);
                    }
                }
            } catch (Exception e) {
                error(103, e.getLocalizedMessage());
            }
        });
    }

    /**
     * Function to get the type of captcha (Default Captcha / Google reCaptcha).
     */
    private void getCaptchaType() {
        /*
         *  JSON response format
         *
         *  {
         *      "captcha_type": "DEFAULT"|"GRECAPTCHA"
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "try {" +
                "    var gResp = document.querySelector('input[id=\"gResponse\"], input[name=\"gResponse\"], #gResponse, textarea[name=\"g-recaptcha-response\"]');" +
                "    if (gResp) {" +
                "        return { captcha_type: 'GRECAPTCHA' };" +
                "    }" +
                "    return { captcha_type: 'DEFAULT' };" +
                "} catch(e) {" +
                "    return { captcha_type: 'DEFAULT' };" +
                "}" +
                "})();", responseString -> {
            try {
                JSONObject response = new JSONObject(responseString);
                this.notification.setContentTitle(getString(R.string.captcha_wait));
                this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, notification.build());

                if (response.getString("captcha_type").equals("DEFAULT")) {
                    getCaptcha();
                } else {
                    executeCaptcha();
                }
            } catch (Exception e) {
                error(104, e.getLocalizedMessage());
            }
        });
    }

    /**
     * For Default Captcha
     * Function to get the captcha from the portal's sign in page and load it into the ImageView.
     */
    private void getCaptcha() {
        /*
         *  JSON response format
         *
         *  {
         *      "captcha": "data:image/png:base64, ContinuousGibberishText...."
         *  }
         */
        this.webView.evaluateJavascript("(function() {" +
                "try {" +
                "    var img = document.querySelector('#captchaBlock img, img[src^=\"data:image\"], img[alt*=\"captcha\" i], #captchaimg, img[src*=\"captcha\" i]');" +
                "    if (img && img.src) {" +
                "        return { captcha: img.src };" +
                "    }" +
                "    if (typeof $ !== 'undefined' && $('#captchaBlock img').length) {" +
                "        return { captcha: $('#captchaBlock img').get(0).src };" +
                "    }" +
                "    return { captcha: '' };" +
                "} catch(e) {" +
                "    return { captcha: '', error: e.toString() };" +
                "}" +
                "})();", responseString -> {
            try {
                JSONObject response = new JSONObject(responseString);
                String captchaVal = response.optString("captcha", "");
                if (captchaVal.contains(",")) {
                    String base64Captcha = captchaVal.split(",")[1];
                    byte[] decodedString = Base64.decode(base64Captcha, Base64.DEFAULT);
                    Bitmap decodedImage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                    try {
                        this.callback.onRequestCaptcha(CAPTCHA_DEFAULT, decodedImage, null);
                    } catch (Exception ignored) {
                        this.endService(true);
                    }
                } else {
                    error(105, "Captcha image unavailable");
                }
            } catch (Exception e) {
                error(105, e.getLocalizedMessage());
            }
        });
    }

    /**
     * For Google reCaptcha
     * Function to override the default onSubmit function and execute the captcha.
     */
    private void executeCaptcha() {
        try {
            this.callback.onRequestCaptcha(CAPTCHA_GRECATPCHA, null, this.webView);
        } catch (Exception ignored) {
            this.endService(true);
            return;
        }

        /*
            Overriding the existing onSubmit function and attempting to render the reCaptcha
         */
        webView.evaluateJavascript("function callBuiltValidation(token) {" +
                "    Android.signIn(token);" +
                "}" +
                "(function() {" +
                "var executeInterval = setInterval(function() {" +
                "    try {" +   // typeof grecaptcha != 'undefined' always returns true for some reason
                "        grecaptcha.execute();" +
                "        clearInterval(executeInterval);" +
                "    } catch (err) {" +
                "    }" +
                "}, 500);" +
                "})();", value -> {
        });
    }

    /**
     * Function to sign in to the portal
     */
    @JavascriptInterface
    public void signIn(final String captcha) {
        this.notification.setContentTitle(getString(R.string.sign_in_attempt));
        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, notification.build());

        new Handler(getApplicationContext().getMainLooper())
                .post(() -> {
                    try {
                        callback.onCaptchaComplete();
                    } catch (Exception ignored) {
                    }

                    ViewGroup webViewParent = (ViewGroup) webView.getParent();
                    if (webViewParent != null) {
                        webViewParent.removeView(webView);
                    }

                    /*
                     *  JSON response format
                     *  {
                     *      "authorized": true|false,
                     *      "error_message": null,
                     *      "error_code": 0
                     *  }
                     */
                    String cleanU = username != null ? username.replaceAll("'", "\\\\'") : "";
                    String cleanP = password != null ? password.replaceAll("'", "\\\\'") : "";
                    String cleanC = captcha != null ? captcha.replaceAll("'", "\\\\'") : "";

                    webView.evaluateJavascript("(function() {" +
                            "if (typeof captchaInterval != 'undefined') clearInterval(captchaInterval);" +
                            "if (typeof executeInterval != 'undefined') clearInterval(executeInterval);" +
                            "var uInput = document.querySelector('#vtopLoginForm [name=\"username\"], input[name=\"username\"], input[name=\"uname\"]');" +
                            "var pInput = document.querySelector('#vtopLoginForm [name=\"password\"], input[name=\"password\"], input[name=\"passwd\"]');" +
                            "var cInput = document.querySelector('#vtopLoginForm [name=\"captchaStr\"], input[name=\"captchaStr\"], input[name=\"captcha\"]');" +
                            "var gInput = document.querySelector('#vtopLoginForm [name=\"gResponse\"], input[name=\"gResponse\"], #gResponse');" +
                            "if (uInput) uInput.value = '" + cleanU + "';" +
                            "if (pInput) pInput.value = '" + cleanP + "';" +
                            "if (cInput) cInput.value = '" + cleanC + "';" +
                            "if (gInput) gInput.value = '" + cleanC + "';" +
                            "if (typeof $ !== 'undefined') {" +
                            "    $('#vtopLoginForm [name=\"username\"]').val('" + cleanU + "');" +
                            "    $('#vtopLoginForm [name=\"password\"]').val('" + cleanP + "');" +
                            "    $('#vtopLoginForm [name=\"captchaStr\"]').val('" + cleanC + "');" +
                            "    $('#vtopLoginForm [name=\"gResponse\"]').val('" + cleanC + "');" +
                            "}" +
                            "var response = {" +
                            "    authorised: false," +
                            "    error_message: null," +
                            "    error_code: 0" +
                            "};" +
                            "var form = document.querySelector('#vtopLoginForm') || document.forms['vtopLoginForm'] || document.querySelector('form');" +
                            "var formData = '';" +
                            "if (form) {" +
                            "    var elements = form.elements;" +
                            "    var pairs = [];" +
                            "    for (var i = 0; i < elements.length; i++) {" +
                            "        var el = elements[i];" +
                            "        if (el.name && !el.disabled && el.type !== 'submit' && el.type !== 'button') {" +
                            "            pairs.push(encodeURIComponent(el.name) + '=' + encodeURIComponent(el.value));" +
                            "        }" +
                            "    }" +
                            "    formData = pairs.join('&');" +
                            "}" +
                            "var resText = '';" +
                            "if (typeof $ !== 'undefined' && $.ajax) {" +
                            "    $.ajax({" +
                            "        type : 'POST'," +
                            "        url : '/vtop/login'," +
                            "        data : ($('#vtopLoginForm').length ? $('#vtopLoginForm').serialize() : formData)," +
                            "        async: false," +
                            "        success : function(res) {" +
                            "            resText = res;" +
                            "        }" +
                            "    });" +
                            "} else {" +
                            "    var xhr = new XMLHttpRequest();" +
                            "    xhr.open('POST', '/vtop/login', false);" +
                            "    xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');" +
                            "    xhr.send(formData);" +
                            "    resText = xhr.responseText;" +
                            "}" +
                            "if (resText && resText.search('___INTERNAL___RESPONSE___') == -1) {" +
                            "    var pageOutline = document.querySelector('#page_outline');" +
                            "    if (pageOutline) {" +
                            "        pageOutline.innerHTML = resText;" +
                            "    } else if (typeof $ !== 'undefined') {" +
                            "        $('#page_outline').html(resText);" +
                            "    }" +
                            "    if (resText.indexOf('authorizedIDX') !== -1) {" +
                            "        response.authorised = true;" +
                            "        return response;" +
                            "    }" +
                            "    var pageContent = resText.toLowerCase();" +
                            "    var invalidCaptchaRegex = new RegExp(/invalid\\s*captcha/);" +
                            "    var invalidCredentialsRegex = new RegExp(/invalid\\s*(user\\s*name|login\\s*id|user\\s*id)\\s*\\/\\s*password/);" +
                            "    var accountLockedRegex = new RegExp(/account\\s*is\\s*locked/);" +
                            "    var maxFailAttemptsRegex = new RegExp(/maximum\\s*fail\\s*attempts\\s*reached/);" +
                            "    if (invalidCaptchaRegex.test(pageContent)) {" +
                            "        response.error_message = 'Invalid Captcha';" +
                            "        response.error_code = 1;" +
                            "    } else if(invalidCredentialsRegex.test(pageContent)) {" +
                            "        response.error_message = 'Invalid Username / Password';" +
                            "        response.error_code = 2;" +
                            "    } else if(accountLockedRegex.test(pageContent)) {" +
                            "        response.error_message = 'Your Account is Locked';" +
                            "        response.error_code = 3;" +
                            "    } else if(maxFailAttemptsRegex.test(pageContent)) {" +
                            "        response.error_message = 'Maximum login attempts reached, open VTOP in your browser to reset your password';" +
                            "        response.error_code = 4;" +
                            "    } else {" +
                            "        response.error_message = 'Unknown error';" +
                            "        response.error_code = 5;" +
                            "    }" +
                            "}" +
                            "return response;" +
                            "})();", responseString -> {
                        try {
                            JSONObject response = new JSONObject(responseString);
                            boolean isAuthorised = response.getBoolean("authorised");

                            if (isAuthorised) {
                                this.reloadPage("/content", false);
                            } else {
                                String errorMessage = response.getString("error_message");
                                Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT).show();

                                int errorCode = response.getInt("error_code");
                                if (errorCode == 1) {
                                    this.reloadPage("", false);
                                } else {
                                    if (errorCode == 2) {
                                        try {
                                            this.callback.onForceSignOut();
                                        } catch (Exception ignored) {}
                                    }

                                    this.endService(true);
                                }
                            }
                        } catch (Exception e) {
                            error(106, e.getLocalizedMessage());
                        }
                    });
                });
    }

    /**
     * Function to get a list of the semesters. These semesters are obtained from the Timetable page.
     */
    private void getSemesters() {
        /*
         *  JSON response format
         *
         *  {
         *      "semesters": [
         *          {
         *              "name": "Fall Semester 2020-21",
         *              "id": "CH2020211"
         *          },
         *          {
         *              "name": "Winter Semester 2020-21",
         *              "id": "CH2020215"
         *          },
         *          ...
         *      ]
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';" +
                "var response = {};" +
                "try {" +
                "    $.ajax({" +
                "        type : 'POST'," +
                "        url : 'academics/common/StudentTimeTableChn'," +
                "        data : data," +
                "        async: false," +
                "        success : function(res) {" +
                "            if (!res) return;" +
                "            if (res.toLowerCase().includes('not authorized')) {" +
                "                response.error_code = 1;" +
                "                response.error_message = 'Unauthorised user agent';" +
                "            } else if (res.toLowerCase().includes('time table')) {" +
                "                var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "                var semSelect = doc.getElementById('semesterSubId');" +
                "                if (!semSelect) return;" +
                "                var options = semSelect.getElementsByTagName('option');" +
                "                var semesters = [];" +
                "                for(var i = 0; i < options.length; ++i) {" +
                "                    if(!options[i].value) {" +
                "                        continue;" +
                "                    }" +
                "                    var semester = {" +
                "                        name: options[i].innerText," +
                "                        id: options[i].value" +
                "                    };" +
                "                    semesters.push(semester);" +
                "                }" +
                "                response.semesters = semesters;" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error_message = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                error(201, "Empty response from server.");
                return;
            }
            try {
                JSONObject response = new JSONObject(responseString);

                if (response.has("error_code")) {
                    if (response.getInt("error_code") == 1) {
                        Toast.makeText(getApplicationContext(), "Error " + 107 + ". Unauthorised user agent, attempting to update. Report a bug if this issue prevails.", Toast.LENGTH_SHORT).show();
                        updateUserAgent();
                        return;
                    }
                }

                JSONArray semesterArray = response.optJSONArray("semesters");
                this.semesters = new HashMap<>();

                if (semesterArray != null) {
                    for (int i = 0; i < semesterArray.length(); ++i) {
                        JSONObject semesterObject = semesterArray.getJSONObject(i);
                        this.semesters.put(semesterObject.getString("name"), semesterObject.getString("id"));
                    }
                }

                try {
                    this.notification.setContentTitle(getString(R.string.semester_wait));
                    this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, notification.build());

                    String[] semesters = this.semesters.keySet().toArray(new String[0]);
                    this.callback.onRequestSemester(semesters);
                } catch (Exception ignored) {
                    this.endService(true);
                }
            } catch (Exception e) {
                error(201, e.getLocalizedMessage());
            }
        });
    }

    /**
     * Function to set the semester ID based on the semester selected.
     */
    public void setSemester(String semester) {
        if (this.semesters != null) {
            this.semesterID = this.semesters.get(semester);
        }
        getName();
    }

    /**
     * Function to save the name of the user in SharedPreferences.
     */
    private void getName() {
        updateProgress(R.string.downloading_profile);

        /*
         *  JSON response format
         *
         *  {
         *      "name": "JOHN DOE"
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';" +
                "var response = {};" +
                "try {" +
                "    $.ajax({" +
                "        type : 'POST'," +
                "        url : 'studentsRecord/StudentProfileAllView'," +
                "        data : data," +
                "        async: false," +
                "        success: function(res) {" +
                "            if(res && res.toLowerCase().includes('personal information')) {" +
                "                var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "                var cells = doc.getElementsByTagName('td');" +
                "                for(var i = 0; i < cells.length; ++i) {" +
                "                    var key = cells[i].innerText.toLowerCase();" +
                "                    if(key.includes('student') && key.includes('name')) {" +
                "                        if (i + 1 < cells.length) {" +
                "                            response.name = cells[++i].innerHTML;" +
                "                        }" +
                "                        break;" +
                "                    }" +
                "                }" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error_message = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString != null && !responseString.equals("null") && !responseString.trim().isEmpty()) {
                    JSONObject response = new JSONObject(responseString);
                    if (response.has("name") && !response.isNull("name")) {
                        sharedPreferences.edit().putString("name", response.optString("name", "")).apply();
                    }
                }
                this.getCreditsCGPA();
            } catch (Exception e) {
                this.getCreditsCGPA();
            }
        });
    }

    /**
     * Function to sve the earned credits and CGPA in SharedPreferences.
     */
    private void getCreditsCGPA() {
        this.updateProgress(null);

        /*
         *  JSON response format
         *
         *  {
         *      "cgpa": 8.58
         *      "total_credits": 64
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';" +
                "var response = {};" +
                "try {" +
                "    $.ajax({" +
                "        type : 'POST'," +
                "        url : 'examinations/examGradeView/StudentGradeHistory'," +
                "        data : data," +
                "        async: false," +
                "        success: function(res) {" +
                "            if (!res) return;" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var tables = doc.getElementsByTagName('table');" +
                "            for (var i = tables.length - 1; i >= 0 ; --i) {" +
                "                var rows = tables[i].getElementsByTagName('tr');" +
                "                if (!rows || rows.length === 0) continue;" +
                "                var headings = rows[0].getElementsByTagName('td');" +
                "                if (!headings || headings.length === 0) headings = rows[0].getElementsByTagName('th');" +
                "                if (!headings || headings.length === 0) continue;" +
                "                if (headings[0].innerText.toLowerCase().includes('credits')) {" +
                "                    var creditsIndex, cgpaIndex;" +
                "                    for (var j = 0; j < headings.length; ++j) {" +
                "                        var heading = headings[j].innerText.toLowerCase();" +
                "                        if (heading.includes('earned')) {" +
                "                            creditsIndex = j + headings.length;" +
                "                        } else if (heading.includes('cgpa')) {" +
                "                            cgpaIndex = j + headings.length;" +
                "                        }" +
                "                    }" +
                "                    var cells = tables[i].getElementsByTagName('td');" +
                "                    if (cgpaIndex !== undefined && cgpaIndex < cells.length) {" +
                "                        response.cgpa = parseFloat(cells[cgpaIndex].innerText) || 0;" +
                "                    }" +
                "                    if (creditsIndex !== undefined && creditsIndex < cells.length) {" +
                "                        response.total_credits = parseFloat(cells[creditsIndex].innerText) || 0;" +
                "                    }" +
                "                    break;" +
                "                }" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error_message = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString != null && !responseString.equals("null") && !responseString.trim().isEmpty()) {
                    JSONObject response = new JSONObject(responseString);
                    if (response.has("cgpa") && !response.isNull("cgpa")) {
                        this.sharedPreferences.edit().putFloat("cgpa", (float) response.optDouble("cgpa", 0.0)).apply();
                    }
                    if (response.has("total_credits") && !response.isNull("total_credits")) {
                        this.sharedPreferences.edit().putFloat("totalCredits", (float) response.optDouble("total_credits", 0.0)).apply();
                    }
                }
                this.downloadCourses();
            } catch (Exception e) {
                this.downloadCourses();
            }
        });
    }

    /**
     * Function to download the course info from the timetable page.
     */
    private void downloadCourses() {
        this.updateProgress(R.string.downloading_courses);

        /*
         *  JSON response format
         *
         *  {
         *      "courses": [
         *          {
         *              "code": "CSE1001",
         *              "title": "Problem Solving and Programming",
         *              "type": "lab"|"project"|"theory",
         *              "credits": 3,
         *              "slots": [
         *                  "L45",
         *                  "L46"
         *              ],
         *              "venue": "AB2 - 015",
         *              "faculty": "JOHN DOE"
         *          },
         *          ...
         *      ]
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = '_csrf=' + $('input[name=\"_csrf\"]').val() + '&semesterSubId=' + '" + semesterID + "' + '&authorizedID=' + $('#authorizedIDX').val();" +
                "var response = {" +
                "    courses: []" +
                "};" +
                "try {" +
                "    $.ajax({" +
                "        type : 'POST'," +
                "        url : 'processViewTimeTable'," +
                "        data : data," +
                "        async: false," +
                "        success : function(res) {" +
                "            if (!res) return;" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var studentDetailsList = doc.getElementById('studentDetailsList');" +
                "            if (!studentDetailsList) return;" +
                "            var tables = studentDetailsList.getElementsByTagName('table');" +
                "            if (!tables || tables.length === 0) return;" +
                "            var table = tables[0];" +
                "            var headings = table.getElementsByTagName('th');" +
                "            if (!headings || headings.length === 0) return;" +
                "            var courseIndex, creditsIndex, slotVenueIndex, facultyIndex;" +
                "            for(var i = 0; i < headings.length; ++i) {" +
                "                var heading = headings[i].innerText.toLowerCase();" +
                "                if (heading == 'course') {" +
                "                    courseIndex = i;" +
                "                } else if (heading == 'l t p j c') {" +
                "                    creditsIndex = i;" +
                "                } else if (heading.includes('slot')) {" +
                "                    slotVenueIndex = i;" +
                "                } else if (heading.includes('faculty')) {" +
                "                    facultyIndex = i;" +
                "                }" +
                "            }" +
                "            var cells = table.getElementsByTagName('td');" +
                "            if (!cells || cells.length === 0) return;" +
                "            var headingOffset = (headings[0] && headings[0].innerText.toLowerCase().includes('invoice')) ? -1 : 0;" +
                "            var cellOffset = (cells[0] && cells[0].innerText.toLowerCase().includes('invoice')) ? 1 : 0;" +
                "            var offset = headingOffset + cellOffset;" +
                "            while (courseIndex !== undefined && creditsIndex !== undefined && slotVenueIndex !== undefined && facultyIndex !== undefined &&" +
                "                   courseIndex + offset < cells.length && creditsIndex + offset < cells.length && slotVenueIndex + offset < cells.length && facultyIndex + offset < cells.length) {" +
                "                var course = {};" +
                "                var rawCourse = cells[courseIndex + offset].innerText.replace(/\\t/g,'').replace(/\\n/g,' ');" +
                "                var rawCourseType = rawCourse.split('(').slice(-1)[0].toLowerCase();" +
                "                var rawCredits = cells[creditsIndex + offset].innerText.replace(/\\t/g,'').replace(/\\n/g,' ').trim().split(' ');" +
                "                var rawSlotVenue = cells[slotVenueIndex + offset].innerText.replace(/\\t/g,'').replace(/\\n/g,'').split('-');" +
                "                var rawFaculty = cells[facultyIndex + offset].innerText.replace(/\\t/g,'').replace(/\\n/g,'').split('-');" +
                "                course.code = rawCourse.split('-')[0].trim();" +
                "                course.title = rawCourse.split('-').slice(1).join('-').split('(')[0].trim();" +
                "                course.type = (rawCourseType.includes('lab')) ? 'lab' : ((rawCourseType.includes('project')) ? 'project' : 'theory');" +
                "                course.credits = parseInt(rawCredits[rawCredits.length - 1]) || 0;" +
                "                course.slots = rawSlotVenue[0].trim().split('+');" +
                "                course.venue = rawSlotVenue.slice(1, rawSlotVenue.length).join(' - ').trim();" +
                "                course.faculty = rawFaculty[0].trim();" +
                "                response.courses.push(course);" +
                "                courseIndex += headings.length + headingOffset;" +
                "                creditsIndex += headings.length + headingOffset;" +
                "                slotVenueIndex += headings.length + headingOffset;" +
                "                facultyIndex += headings.length + headingOffset;" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error_message = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                error(401, "Empty response");
                return;
            }
            try {
                JSONObject response = new JSONObject(responseString);
                JSONArray courseArray = response.optJSONArray("courses");
                if (courseArray == null) courseArray = new JSONArray();

                List<Course> courses = new ArrayList<>();
                List<Slot> slots = new ArrayList<>();

                this.theorySlots = new HashMap<>();
                this.labSlots = new HashMap<>();
                this.projectSlots = new HashMap<>();

                this.theoryCourses = new HashMap<>();
                this.labCourses = new HashMap<>();
                this.projectCourses = new HashMap<>();

                for (int i = 0, slotId = 1; i < courseArray.length(); ++i) {
                    JSONObject courseObject = courseArray.getJSONObject(i);
                    Course course = new Course();

                    course.id = i + 1;
                    course.code = this.getStringValue(courseObject, "code");
                    course.title = this.getStringValue(courseObject, "title");
                    course.type = this.getStringValue(courseObject, "type");
                    if (course.type == null) course.type = "theory";
                    course.credits = this.getIntegerValue(courseObject, "credits");
                    course.venue = this.getStringValue(courseObject, "venue");
                    course.faculty = this.getStringValue(courseObject, "faculty");

                    courses.add(course);

                    Map<String, Slot> slotReference;

                    if (course.type.equals("lab")) {
                        slotReference = this.labSlots;
                        this.labCourses.put(course.id, course);
                    } else if (course.type.equals("project")) {
                        slotReference = this.projectSlots;
                        this.projectCourses.put(course.id, course);
                    } else {
                        slotReference = this.theorySlots;
                        this.theoryCourses.put(course.id, course);
                    }

                    JSONArray slotsArray = courseObject.optJSONArray("slots");
                    if (slotsArray != null) {
                        for (int j = 0; j < slotsArray.length(); ++j, ++slotId) {
                            Slot slot = new Slot();

                            slot.id = slotId;
                            slot.slot = slotsArray.getString(j);
                            slot.courseId = course.id;

                            slots.add(slot);
                            slotReference.put(slot.slot, slot);
                        }
                    }
                }

                CoursesDao coursesDao = this.appDatabase.coursesDao();
                Completable.fromAction(() -> coursesDao.replaceAll(courses, slots))
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                startParallelDownloads();
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(402, e.getLocalizedMessage());
                            }
                        });
            } catch (Exception e) {
                error(401, e.getLocalizedMessage());
            }
        });
    }

    /**
     * Function to download the timetable.
     */
    private void downloadTimetable() {
        updateProgress(R.string.downloading_timetable);

        /*
         *  JSON response format
         *
         *  {
         *      "lab": [
         *          {
         *              "start_time": "08:00",
         *              "end_time": "08:50",
         *              "sunday": null,
         *              "monday": null",
         *              "tuesday": null,
         *              "wednesday": null,
         *              "thursday": null,
         *              "friday": null,
         *              "saturday": null,
         *          },
         *          ...
         *      ],
         *      "theory": [
         *          {
         *              "start_time": "08:00",
         *              "end_time": "08:50",
         *              "sunday": null,
         *              "monday": "A1",
         *              "tuesday": "B1",
         *              "wednesday": null,
         *              "thursday": "D1",
         *              "friday": "E1",
         *              "saturday": "F1",
         *          },
         *          ...
         *      ]
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = '_csrf=' + $('input[name=\"_csrf\"]').val() + '&semesterSubId=' + '" + semesterID + "' + '&authorizedID=' + $('#authorizedIDX').val();" +
                "var response = {" +
                "    lab: []," +
                "    theory: []" +
                "};" +
                "try {" +
                "    $.ajax({" +
                "        type : 'POST'," +
                "        url : 'processViewTimeTable'," +
                "        data : data," +
                "        async: false," +
                "        success : function(res) {" +
                "            if (!res) return;" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var studentDetails = doc.getElementById('getStudentDetails');" +
                "            if (studentDetails) {" +
                "                var spans = studentDetails.getElementsByTagName('span');" +
                "                if(spans.length > 0 && spans[0].innerText.toLowerCase().includes('no record(s) found')) {" +
                "                   return;" +
                "                }" +
                "            }" +
                "            var timeTableStyle = doc.getElementById('timeTableStyle');" +
                "            if (!timeTableStyle) return;" +
                "            var cells = timeTableStyle.getElementsByTagName('td');" +
                "            if (!cells || cells.length === 0) return;" +
                "            var key, type;" +
                "            for (var i = 0, j = 0; i < cells.length; ++i) {" +
                "                var content = cells[i].innerText.toUpperCase();" +
                "                if (content.includes('THEORY')) {" +
                "                    type = 'theory';" +
                "                    j = 0;" +
                "                    continue;" +
                "                } else if (content.includes('LAB')) {" +
                "                    type = 'lab';" +
                "                    j = 0;" +
                "                    continue;" +
                "                } else if (content.includes('START')) {" +
                "                    key = 'start';" +
                "                    continue;" +
                "                } else if (content.includes('END')) {" +
                "                    key = 'end';" +
                "                    continue;" +
                "                } else if (content.includes('SUN')) {" +
                "                    key = 'sunday';" +
                "                    continue;" +
                "                } else if (content.includes('MON')) {" +
                "                    key = 'monday';" +
                "                    continue;" +
                "                } else if (content.includes('TUE')) {" +
                "                    key = 'tuesday';" +
                "                    continue;" +
                "                } else if (content.includes('WED')) {" +
                "                    key = 'wednesday';" +
                "                    continue;" +
                "                } else if (content.includes('THU')) {" +
                "                    key = 'thursday';" +
                "                    continue;" +
                "                } else if (content.includes('FRI')) {" +
                "                    key = 'friday';" +
                "                    continue;" +
                "                } else if (content.includes('SAT')) {" +
                "                    key = 'saturday';" +
                "                    continue;" +
                "                } else if (content.includes('LUNCH')) {" +
                "                    continue;" +
                "                }" +
                "                if (key == 'start') {" +
                "                    response[type].push({ start_time: content.trim() });" +
                "                } else if (key == 'end') {" +
                "                    if (response[type] && j < response[type].length) {" +
                "                        response[type][j++].end_time = content.trim();" +
                "                    }" +
                "                } else if (content.split('-').length > 1) {" +
                "                    if (response[type] && j < response[type].length) {" +
                "                        response[type][j++][key] = content.split('-')[0].trim();" +
                "                    }" +
                "                } else {" +
                "                    if (response[type] && j < response[type].length) {" +
                "                        response[type][j++][key] = null;" +
                "                    }" +
                "                }" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error_message = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                error(501, "Empty response");
                onStreamComplete("Timetable");
                return;
            }
            try {
                JSONObject response = new JSONObject(responseString);
                JSONArray labArray = response.optJSONArray("lab");
                JSONArray theoryArray = response.optJSONArray("theory");
                if (labArray == null) labArray = new JSONArray();
                if (theoryArray == null) theoryArray = new JSONArray();

                SettingsRepository.clearNotificationPendingIntents(this.getApplicationContext());

                List<Timetable> timetable = new ArrayList<>();

                /*
                    Used for converting 12-hour to 24-hour if necessary
                 */
                SimpleDateFormat hour24 = new SimpleDateFormat("HH:mm", Locale.ENGLISH);
                SimpleDateFormat hour12 = new SimpleDateFormat("h:mm a", Locale.ENGLISH);

                for (int i = 0; i < labArray.length() && i < theoryArray.length(); ++i) {
                    JSONObject labObject = labArray.getJSONObject(i);
                    JSONObject theoryObject = theoryArray.getJSONObject(i);

                    Timetable lab = new Timetable();
                    Timetable theory = new Timetable();

                    lab.id = i * 2 + 1;
                    lab.startTime = this.getStringValue(labObject, "start_time");
                    lab.endTime = this.getStringValue(labObject, "end_time");
                    lab.sunday = this.getSlotId(this.getStringValue(labObject, "sunday"), Course.TYPE_LAB);
                    lab.monday = this.getSlotId(this.getStringValue(labObject, "monday"), Course.TYPE_LAB);
                    lab.tuesday = this.getSlotId(this.getStringValue(labObject, "tuesday"), Course.TYPE_LAB);
                    lab.wednesday = this.getSlotId(this.getStringValue(labObject, "wednesday"), Course.TYPE_LAB);
                    lab.thursday = this.getSlotId(this.getStringValue(labObject, "thursday"), Course.TYPE_LAB);
                    lab.friday = this.getSlotId(this.getStringValue(labObject, "friday"), Course.TYPE_LAB);
                    lab.saturday = this.getSlotId(this.getStringValue(labObject, "saturday"), Course.TYPE_LAB);

                    theory.id = i * 2 + 2;
                    theory.startTime = this.getStringValue(theoryObject, "start_time");
                    theory.endTime = this.getStringValue(theoryObject, "end_time");
                    theory.sunday = this.getSlotId(this.getStringValue(theoryObject, "sunday"), Course.TYPE_THEORY);
                    theory.monday = this.getSlotId(this.getStringValue(theoryObject, "monday"), Course.TYPE_THEORY);
                    theory.tuesday = this.getSlotId(this.getStringValue(theoryObject, "tuesday"), Course.TYPE_THEORY);
                    theory.wednesday = this.getSlotId(this.getStringValue(theoryObject, "wednesday"), Course.TYPE_THEORY);
                    theory.thursday = this.getSlotId(this.getStringValue(theoryObject, "thursday"), Course.TYPE_THEORY);
                    theory.friday = this.getSlotId(this.getStringValue(theoryObject, "friday"), Course.TYPE_THEORY);
                    theory.saturday = this.getSlotId(this.getStringValue(theoryObject, "saturday"), Course.TYPE_THEORY);

                    /*
                        Formatting time in 24-hour in-case it's given in 12-hour format because VIT
                        thought it would be a good idea to use both 12-hour and 24-hour formats

                        This conversion works under the assumption that there will not be any classes
                        after 20:00 and before 08:00. If the time is less than 08:00, the time is in
                        a 12-hour format and has to be converted
                     */
                    String[] timings = {lab.startTime, lab.endTime, theory.startTime, theory.endTime};
                    for (int j = 0; j < timings.length; ++j) {
                        try {
                            if (timings[j] != null) {
                                Date time = hour24.parse(timings[j]);
                                Date hourStart = hour24.parse("08:00");

                                if (time != null && hourStart != null && time.before(hourStart)) {
                                    time = hour12.parse(timings[j] + " PM");
                                    if (time != null) {
                                        timings[j] = hour24.format(time);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    lab.startTime = timings[0];
                    lab.endTime = timings[1];
                    theory.startTime = timings[2];
                    theory.endTime = timings[3];

                    timetable.add(lab);
                    timetable.add(theory);

                    try {
                        SettingsRepository.setTimetableNotifications(this.getApplicationContext(), lab);
                        SettingsRepository.setTimetableNotifications(this.getApplicationContext(), theory);
                    } catch (Exception ignored) {
                    }
                }

                TimetableDao timetableDao = appDatabase.timetableDao();
                Completable.fromAction(() -> timetableDao.replaceAll(timetable))
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                SettingsRepository.rescheduleDndAlarms(getApplicationContext());
                                onStreamComplete("Timetable");
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(502, e.getLocalizedMessage());
                                onStreamComplete("Timetable");
                            }
                        });
            } catch (Exception e) {
                error(501, e.getLocalizedMessage());
                onStreamComplete("Timetable");
            }
        });
    }

    /**
     * Function to download the attendance.
     */
    private void downloadAttendance() {
        updateProgress(R.string.downloading_attendance);

        /*
         *  JSON response format
         *
         *  {
         *      "attendance": [
         *          {
         *              "slot": "L45",
         *              "course_type": "Lab Only"
         *              "attended": 81,
         *              "total": 83,
         *              "percentage": 98
         *          },
         *          ...
         *      ]
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var response = {" +
                "    attendance: []" +
                "};" +
                "try {" +
                "    var csrfEl = document.querySelector('input[name=\"_csrf\"]');" +
                "    var csrf = csrfEl ? csrfEl.value : '';" +
                "    var authIdEl = document.getElementById('authorizedIDX') || document.querySelector('input[name=\"authorizedID\"]');" +
                "    var authId = authIdEl ? authIdEl.value : '';" +
                "    var semesterSubId = '" + semesterID + "';" +
                "    $.ajax({" +
                "        type : 'POST'," +
                "        url : 'processViewStudentAttendance'," +
                "        data : '_csrf=' + csrf + '&semesterSubId=' + semesterSubId + '&authorizedID=' + authId," +
                "        async: false," +
                "        success : function(res) {" +
                "            try {" +
                "                var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "                var table = doc.getElementById('getStudentDetails') || doc.getElementsByTagName('table')[0];" +
                "                if (!table) return;" +
                "                var headings = table.getElementsByTagName('th');" +
                "                if (!headings || headings.length === 0) headings = table.getElementsByTagName('td');" +
                "                var courseTypeIndex = -1, slotIndex = -1, attendedIndex = -1, totalIndex = -1, percentageIndex = -1;" +
                "                for (var i = 0; i < headings.length; ++i) {" +
                "                    var heading = (headings[i].innerText || '').toLowerCase();" +
                "                    if (heading.includes('course') && heading.includes('type')) {" +
                "                        courseTypeIndex = i;" +
                "                    } else if (heading.includes('slot')) {" +
                "                        slotIndex = i;" +
                "                    } else if (heading.includes('attended')) {" +
                "                        attendedIndex = i;" +
                "                    } else if (heading.includes('total')) {" +
                "                        totalIndex = i;" +
                "                    } else if (heading.includes('percentage')) {" +
                "                        percentageIndex = i;" +
                "                    }" +
                "                }" +
                "                var rows = table.getElementsByTagName('tr');" +
                "                for (var r = 1; r < rows.length; r++) {" +
                "                    try {" +
                "                        var rowCells = rows[r].getElementsByTagName('td');" +
                "                        if (rowCells.length > Math.max(courseTypeIndex, slotIndex) && slotIndex >= 0) {" +
                "                            var courseTypeText = courseTypeIndex >= 0 ? (rowCells[courseTypeIndex].innerText || '').trim() : '';" +
                "                            var slotText = (rowCells[slotIndex].innerText || '').trim().split('+')[0].trim();" +
                "                            var attendedRaw = attendedIndex >= 0 ? parseInt(rowCells[attendedIndex].innerText.trim()) : NaN;" +
                "                            var totalRaw = totalIndex >= 0 ? parseInt(rowCells[totalIndex].innerText.trim()) : NaN;" +
                "                            var attended = isNaN(attendedRaw) ? null : attendedRaw;" +
                "                            var total = isNaN(totalRaw) ? null : totalRaw;" +
                "                            var percentage = (attended !== null && total !== null && total > 0) ? Math.ceil((attended * 100) / total) : 0;" +
                "                            var classLogs = [];" +
                "                            var onClickMatch = rows[r].innerHTML.match(/processViewAttendanceDetail\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]/);" +
                "                            if (onClickMatch) {" +
                "                                var classId = onClickMatch[1];" +
                "                                var slotName = onClickMatch[2];" +
                "                                var params = '_csrf=' + csrf + '&classId=' + classId + '&slotName=' + slotName + '&authorizedID=' + authId + '&x=' + new Date().toUTCString();" +
                "                                try {" +
                "                                    $.ajax({" +
                "                                        type: 'POST'," +
                "                                        url: 'processViewAttendanceDetail'," +
                "                                        data: params," +
                "                                        async: false," +
                "                                        success: function(detailRes) {" +
                "                                            try {" +
                "                                                var detailDoc = new DOMParser().parseFromString(detailRes, 'text/html');" +
                "                                                var detailTable = detailDoc.getElementsByTagName('table')[0];" +
                "                                                if (detailTable) {" +
                "                                                    var detailHeadings = detailTable.getElementsByTagName('th');" +
                "                                                    var dateIndex = -1, detailSlotIndex = -1, timingIndex = -1, statusIndex = -1;" +
                "                                                    for (var h = 0; h < detailHeadings.length; h++) {" +
                "                                                        var hText = (detailHeadings[h].innerText || '').toLowerCase();" +
                "                                                        if (hText.includes('date')) {" +
                "                                                            dateIndex = h;" +
                "                                                        } else if (hText.includes('slot')) {" +
                "                                                            detailSlotIndex = h;" +
                "                                                        } else if (hText.includes('timing')) {" +
                "                                                            timingIndex = h;" +
                "                                                        } else if (hText.includes('status')) {" +
                "                                                            statusIndex = h;" +
                "                                                        }" +
                "                                                    }" +
                "                                                    var detailRows = detailTable.getElementsByTagName('tr');" +
                "                                                    var calcAttended = 0;" +
                "                                                    var calcTotal = 0;" +
                "                                                    for (var dr = 1; dr < detailRows.length; dr++) {" +
                "                                                        var drCells = detailRows[dr].getElementsByTagName('td');" +
                "                                                        if (drCells.length > 0) {" +
                "                                                            var logDate = (dateIndex >= 0 && dateIndex < drCells.length) ? (drCells[dateIndex].innerText || '').trim() : '';" +
                "                                                            var logSlot = (detailSlotIndex >= 0 && detailSlotIndex < drCells.length) ? (drCells[detailSlotIndex].innerText || '').trim() : '';" +
                "                                                            var logTiming = (timingIndex >= 0 && timingIndex < drCells.length) ? (drCells[timingIndex].innerText || '').trim() : '';" +
                "                                                            var logStatus = (statusIndex >= 0 && statusIndex < drCells.length) ? (drCells[statusIndex].innerText || '').trim() : '';" +
                "                                                            var statusText = logStatus.toLowerCase();" +
                "                                                            if (!logDate || !logStatus || statusText.includes('no attendance') || statusText.includes('not posted')) {" +
                "                                                                continue;" +
                "                                                            }" +
                "                                                            classLogs.push({" +
                "                                                                date: logDate," +
                "                                                                slot: logSlot," +
                "                                                                timing: logTiming," +
                "                                                                status: logStatus" +
                "                                                            });" +
                "                                                            if (statusText.includes('virtual') || statusText.includes('medical') || statusText.includes('ml')) {" +
                "                                                                continue;" +
                "                                                            }" +
                "                                                            calcTotal++;" +
                "                                                            if (statusText !== 'absent') {" +
                "                                                                calcAttended++;" +
                "                                                            }" +
                "                                                        }" +
                "                                                    }" +
                "                                                    if (calcTotal > 0) {" +
                "                                                        attended = calcAttended;" +
                "                                                        total = calcTotal;" +
                "                                                        percentage = Math.ceil((calcAttended * 100) / calcTotal);" +
                "                                                    } else {" +
                "                                                        attended = null;" +
                "                                                        total = null;" +
                "                                                        percentage = 0;" +
                "                                                    }" +
                "                                                }" +
                "                                            } catch(e4) {}" +
                "                                        }" +
                "                                    });" +
                "                                } catch(e3) {}" +
                "                            }" +
                "                            response.attendance.push({" +
                "                                slot: slotText," +
                "                                course_type: courseTypeText," +
                "                                attended: attended," +
                "                                total: total," +
                "                                percentage: percentage," +
                "                                details: classLogs" +
                "                            });" +
                "                        }" +
                "                    } catch(e2) {}" +
                "                }" +
                "            } catch(e1) {}" +
                "        }" +
                "    });" +
                "} catch(e0) {" +
                "    response.error = e0.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                    onStreamComplete("Attendance");
                    return;
                }
                JSONObject response = new JSONObject(responseString);
                JSONArray attendanceArray = response.optJSONArray("attendance");
                if (attendanceArray == null) {
                    attendanceArray = new JSONArray();
                }
                List<Attendance> attendance = new ArrayList<>();

                int attendedClasses = 0;
                int totalClasses = 0;

                for (int i = 0; i < attendanceArray.length(); ++i) {
                    JSONObject attendanceObject = attendanceArray.getJSONObject(i);
                    Attendance attendanceItem = new Attendance();

                    int courseType = Course.TYPE_THEORY;

                    String rawCourseType = attendanceObject.optString("course_type", "");
                    if (rawCourseType.toLowerCase().contains("lab")) {
                        courseType = Course.TYPE_LAB;
                    }

                    String slot = attendanceObject.optString("slot", "");
                    attendanceItem.id = i + 1;
                    attendanceItem.courseId = this.getCourseId(slot, courseType);
                    attendanceItem.attended = this.getIntegerValue(attendanceObject, "attended");
                    attendanceItem.total = this.getIntegerValue(attendanceObject, "total");

                    if (attendanceObject.has("details") && !attendanceObject.isNull("details")) {
                        attendanceItem.details = attendanceObject.optJSONArray("details") != null
                                ? attendanceObject.optJSONArray("details").toString()
                                : "[]";
                    } else {
                        attendanceItem.details = "[]";
                    }

                    if (attendanceItem.attended != null && attendanceItem.total != null && attendanceItem.total != 0) {
                        attendanceItem.percentage = (int) Math.ceil((attendanceItem.attended * 100.0) / attendanceItem.total);
                        attendedClasses += attendanceItem.attended;
                        totalClasses += attendanceItem.total;
                    } else {
                        // No attendance posted yet — explicitly null out percentage so it
                        // isn't left at some stale / scraped non-zero value.
                        attendanceItem.percentage = null;
                        attendanceItem.attended = null;
                        attendanceItem.total = null;
                    }

                    attendance.add(attendanceItem);
                }

                int overallAttendance = 0;

                if (totalClasses != 0) {
                    overallAttendance = (attendedClasses * 100) / totalClasses;
                }

                sharedPreferences.edit().putInt("overallAttendance", overallAttendance).apply();

                AttendanceDao attendanceDao = appDatabase.attendanceDao();
                Completable.fromAction(() -> attendanceDao.replaceAll(attendance))
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                onStreamComplete("Attendance");
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(602, e.getLocalizedMessage());
                                onStreamComplete("Attendance");
                            }
                        });
            } catch (Exception e) {
                error(601, e.getLocalizedMessage());
                onStreamComplete("Attendance");
            }
        });
    }

    /**
     * Function to download the marks.
     */
    private void downloadMarks() {
        updateProgress(R.string.downloading_marks);

        /*
         *  JSON response format
         *
         *  {
         *      "marks": [
         *          {
         *              "slot": "A1",
         *              "course_type": "Theory Only",
         *              "title": "CAT 1",
         *              "score": 26,
         *              "max_score": 30,
         *              "weightage": 13,
         *              "max_weightage": 15,
         *              "average": null
         *              "status": "Present"
         *          },
         *          ...
         *      ]
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var response = {" +
                "    marks: []" +
                "};" +
                "try {" +
                "    var csrfEl = document.querySelector('input[name=\"_csrf\"]');" +
                "    var csrf = csrfEl ? csrfEl.value : '';" +
                "    var authIdEl = document.getElementById('authorizedIDX') || document.querySelector('input[name=\"authorizedID\"]');" +
                "    var authId = authIdEl ? authIdEl.value : '';" +
                "    var data = 'semesterSubId=' + '" + semesterID + "' + '&authorizedID=' + authId + '&_csrf=' + csrf;" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url : 'examinations/doStudentMarkView'," +
                "        data : data," +
                "        async: false," +
                "        success: function(res) {" +
                "            if (!res || res.toLowerCase().includes('no data found')) {" +
                "                return;" +
                "            }" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var table = doc.getElementById('fixedTableContainer') || doc.getElementsByTagName('table')[0];" +
                "            if (!table) return;" +
                "            var rows = table.getElementsByTagName('tr');" +
                "            if (!rows || rows.length === 0) return;" +
                "            var headings = rows[0].getElementsByTagName('td');" +
                "            if (!headings || headings.length === 0) headings = rows[0].getElementsByTagName('th');" +
                "            if (!headings || headings.length === 0) return;" +
                "            var courseTypeIndex = -1, slotIndex = -1;" +
                "            for (var i = 0; i < headings.length; ++i) {" +
                "                var heading = (headings[i].innerText || '').toLowerCase();" +
                "                if (heading.includes('course') && heading.includes('type')) {" +
                "                    courseTypeIndex = i;" +
                "                } else if (heading.includes('slot')) {" +
                "                    slotIndex = i;" +
                "                }" +
                "            }" +
                "            for (var i = 1; i < rows.length; ++i) {" +
                "                var rowCells = rows[i].getElementsByTagName('td');" +
                "                if (!rowCells || rowCells.length <= Math.max(courseTypeIndex, slotIndex)) continue;" +
                "                var rawCourseType = courseTypeIndex >= 0 ? (rowCells[courseTypeIndex].innerText || '').trim().toLowerCase() : '';" +
                "                var courseType = (rawCourseType.includes('lab')) ? 'lab' : ((rawCourseType.includes('project')) ? 'project' : 'theory');" +
                "                var slot = slotIndex >= 0 ? (rowCells[slotIndex].innerText || '').split('+')[0].trim() : '';" +
                "                var nextRow = rows[++i];" +
                "                if (!nextRow) break;" +
                "                var innerTable = nextRow.getElementsByTagName('table')[0];" +
                "                if (!innerTable) continue;" +
                "                var innerRows = innerTable.getElementsByTagName('tr');" +
                "                if (!innerRows || innerRows.length === 0) continue;" +
                "                var innerHeadings = innerRows[0].getElementsByTagName('td');" +
                "                if (!innerHeadings || innerHeadings.length === 0) innerHeadings = innerRows[0].getElementsByTagName('th');" +
                "                if (!innerHeadings || innerHeadings.length === 0) continue;" +
                "                var titleIndex = -1, scoreIndex = -1, maxScoreIndex = -1, weightageIndex = -1, maxWeightageIndex = -1, averageIndex = -1, statusIndex = -1;" +
                "                for (var j = 0; j < innerHeadings.length; ++j) {" +
                "                    var innerHeading = (innerHeadings[j].innerText || '').toLowerCase();" +
                "                    if (innerHeading.includes('title')) {" +
                "                        titleIndex = j + innerHeadings.length;" +
                "                    } else if (innerHeading.includes('max')) {" +
                "                        maxScoreIndex = j + innerHeadings.length;" +
                "                    } else if (innerHeading.includes('%')) {" +
                "                        maxWeightageIndex = j + innerHeadings.length;" +
                "                    } else if (innerHeading.includes('status')) {" +
                "                        statusIndex = j + innerHeadings.length;" +
                "                    } else if (innerHeading.includes('scored')) {" +
                "                        scoreIndex = j + innerHeadings.length;" +
                "                    } else if (innerHeading.includes('weightage') && innerHeading.includes('mark')) {" +
                "                        weightageIndex = j + innerHeadings.length;" +
                "                    } else if (innerHeading.includes('average')) {" +
                "                        averageIndex = j + innerHeadings.length;" +
                "                    }" +
                "                }" +
                "                var innerCells = innerTable.getElementsByTagName('td');" +
                "                while(titleIndex >= 0 && titleIndex < innerCells.length && scoreIndex >= 0 && scoreIndex < innerCells.length && maxScoreIndex >= 0 && maxScoreIndex < innerCells.length && weightageIndex >= 0 && weightageIndex < innerCells.length && maxWeightageIndex >= 0 && maxWeightageIndex < innerCells.length && averageIndex >= 0 && averageIndex < innerCells.length && statusIndex >= 0 && statusIndex < innerCells.length) {" +
                "                    var mark = {};" +
                "                    mark.slot = slot;" +
                "                    mark.course_type = courseType;" +
                "                    mark.title = (innerCells[titleIndex].innerText || '').trim();" +
                "                    mark.score = parseFloat(innerCells[scoreIndex].innerText) || 0;" +
                "                    mark.max_score = parseFloat(innerCells[maxScoreIndex].innerText) || null;" +
                "                    mark.weightage = parseFloat(innerCells[weightageIndex].innerText) || 0;" +
                "                    mark.max_weightage = parseFloat(innerCells[maxWeightageIndex].innerText) || null;" +
                "                    mark.average = parseFloat(innerCells[averageIndex].innerText) || null;" +
                "                    mark.status = (innerCells[statusIndex].innerText || '').trim();" +
                "                    response.marks.push(mark);" +
                "                    titleIndex += innerHeadings.length;" +
                "                    scoreIndex += innerHeadings.length;" +
                "                    maxScoreIndex += innerHeadings.length;" +
                "                    weightageIndex += innerHeadings.length;" +
                "                    maxWeightageIndex += innerHeadings.length;" +
                "                    averageIndex += innerHeadings.length;" +
                "                    statusIndex += innerHeadings.length;" +
                "                }" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                    downloadGrades();
                    return;
                }
                JSONObject response = new JSONObject(responseString);
                JSONArray marksArray = response.optJSONArray("marks");
                if (marksArray == null) {
                    marksArray = new JSONArray();
                }
                Map<Integer, Mark> marks = new HashMap<>();

                this.cumulativeMarks = new HashMap<>();

                for (int i = 0, j = 0; i < marksArray.length(); ++i) {
                    JSONObject markObject = marksArray.getJSONObject(i);
                    Mark mark = new Mark();

                    int courseType = Course.TYPE_THEORY;

                    String rawCourseType = markObject.optString("course_type", "theory");
                    if (rawCourseType.equals("lab")) {
                        courseType = Course.TYPE_LAB;
                    } else if (rawCourseType.equals("project")) {
                        courseType = Course.TYPE_PROJECT;
                    }

                    mark.id = i + 1;
                    mark.courseId = this.getCourseId(markObject.optString("slot"), courseType);
                    mark.title = this.getStringValue(markObject, "title");
                    mark.score = this.getDoubleValue(markObject, "score");
                    mark.maxScore = this.getDoubleValue(markObject, "max_score");
                    mark.weightage = this.getDoubleValue(markObject, "weightage");
                    mark.maxWeightage = this.getDoubleValue(markObject, "max_weightage");
                    mark.average = this.getDoubleValue(markObject, "average");
                    mark.status = this.getStringValue(markObject, "status");

                    String courseCode = this.getCourseCode(mark.courseId, courseType);
                    Integer courseCredits = this.getCourseCredits(mark.courseId, courseType);

                    if (courseCode != null) {
                        if (!this.cumulativeMarks.containsKey(courseCode)) {
                            this.cumulativeMarks.put(courseCode, new CumulativeMark(++j));
                        }

                        CumulativeMark cm = this.cumulativeMarks.get(courseCode);
                        if (cm != null) {
                            cm.courseCode = courseCode;
                            cm.addWeightage(mark.weightage, mark.maxWeightage, courseType, courseCredits);
                        }
                    }

                    // Generating a unique hash signature to keep a track of read marks
                    mark.signature = ((courseCode != null ? courseCode : "") + rawCourseType + (mark.title != null ? mark.title : "") + (mark.score != null ? mark.score : 0.0)).hashCode();
                    marks.put(mark.signature, mark);
                }

                for (Map.Entry<String, CumulativeMark> entry : this.cumulativeMarks.entrySet()) {
                    CumulativeMark cumulativeMark = entry.getValue();
                    if (cumulativeMark == null) continue;

                    Double theoryTotal = cumulativeMark.theoryTotal != null ? cumulativeMark.theoryTotal : 0.0;
                    Double labTotal = cumulativeMark.labTotal != null ? cumulativeMark.labTotal : 0.0;
                    Double projectTotal = cumulativeMark.projectTotal != null ? cumulativeMark.projectTotal : 0.0;

                    Double theoryMax = cumulativeMark.theoryMax != null ? cumulativeMark.theoryMax : 0.0;
                    Double labMax = cumulativeMark.labMax != null ? cumulativeMark.labMax : 0.0;
                    Double projectMax = cumulativeMark.projectMax != null ? cumulativeMark.projectMax : 0.0;

                    int theoryCredits = cumulativeMark.theoryCredits;
                    int labCredits = cumulativeMark.labCredits;
                    int projectCredits = cumulativeMark.projectCredits;
                    int totalCredits = theoryCredits + labCredits + projectCredits;

                    if (totalCredits > 0) {
                        double grandTotal = (theoryTotal * theoryCredits + labTotal * labCredits + projectTotal * projectCredits) / totalCredits;
                        double grandMax = (theoryMax * theoryCredits + labMax * labCredits + projectMax * projectCredits) / totalCredits;
                        cumulativeMark.grandTotal = grandTotal;
                        cumulativeMark.grandMax = grandMax;
                    } else {
                        cumulativeMark.grandTotal = theoryTotal + labTotal + projectTotal;
                        cumulativeMark.grandMax = theoryMax + labMax + projectMax;
                    }
                }

                if (marks.isEmpty()) {
                    downloadGrades();
                    return;
                }

                appDatabase.marksDao()
                        .insertMarks(marks)
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                downloadGrades();
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(702, e.getLocalizedMessage());
                                downloadGrades();
                            }
                        });
            } catch (Exception e) {
                error(701, e.getLocalizedMessage());
                downloadGrades();
            }
        });
    }

    /**
     * Function to download the grades.
     */
    private void downloadGrades() {
        updateProgress(null);

        /*
         *  JSON response format
         *
         *  {
         *      "grades": [
         *          {
         *              "course_code": "CSE1001",
         *              "grade": "S"
         *          },
         *          ...
         *      ],
         *      "gpa": 8.58
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var response = {" +
                "    grades: []," +
                "    gpa: null" +
                "};" +
                "try {" +
                "    var csrfEl = document.querySelector('input[name=\"_csrf\"]');" +
                "    var csrf = csrfEl ? csrfEl.value : '';" +
                "    var authIdEl = document.getElementById('authorizedIDX') || document.querySelector('input[name=\"authorizedID\"]');" +
                "    var authId = authIdEl ? authIdEl.value : '';" +
                "    var data = 'semesterSubId=' + '" + semesterID + "' + '&authorizedID=' + authId + '&_csrf=' + csrf;" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url : 'examinations/examGradeView/doStudentGradeView'," +
                "        data : data," +
                "        async: false," +
                "        success: function(res) {" +
                "            if (!res || res.toLowerCase().includes('no records')) {" +
                "                return;" +
                "            }" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var table = doc.getElementsByTagName('table')[0];" +
                "            if (!table) return;" +
                "            var headings = table.getElementsByTagName('th');" +
                "            if (!headings || headings.length === 0) headings = table.getElementsByTagName('td');" +
                "            if (!headings || headings.length === 0) return;" +
                "            var courseCodeIndex = -1, gradeIndex = -1, creditsIndex = -1, creditsSpan = 1;" +
                "            for (var i = 0; i < headings.length; ++i) {" +
                "                var heading = (headings[i].innerText || '').toLowerCase();" +
                "                if (heading.includes('code')) {" +
                "                    courseCodeIndex = i;" +
                "                } else if (heading.includes('credits')) {" +
                "                    creditsIndex = i;" +
                "                    creditsSpan = headings[i].colSpan || 1;" +
                "                } else if (heading.includes('grade')) {" +
                "                    gradeIndex = i;" +
                "                }" +
                "            }" +
                "            if (creditsIndex >= 0 && courseCodeIndex > creditsIndex) {" +
                "                courseCodeIndex += creditsSpan - 1;" +
                "            }" +
                "            if (creditsIndex >= 0 && gradeIndex > creditsIndex) {" +
                "                gradeIndex += creditsSpan - 1;" +
                "            }" +
                "            var cells = table.getElementsByTagName('td');" +
                "            while(courseCodeIndex >= 0 && courseCodeIndex < cells.length && gradeIndex >= 0 && gradeIndex < cells.length) {" +
                "                var grade = {};" +
                "                grade.course_code = (cells[courseCodeIndex].innerText || '').trim();" +
                "                grade.grade = (cells[gradeIndex].innerText || '').trim();" +
                "                response.grades.push(grade);" +
                "                courseCodeIndex += headings.length - 1;" +
                "                gradeIndex += headings.length - 1;" +
                "            }" +
                "            if (cells && cells.length > 0) {" +
                "                var lastCellText = (cells[cells.length - 1].innerText || '').trim();" +
                "                if (lastCellText.includes(':')) {" +
                "                    var gpaParts = lastCellText.split(':');" +
                "                    if (gpaParts.length > 1) {" +
                "                        response.gpa = gpaParts[1].trim();" +
                "                    }" +
                "                }" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                    onStreamComplete("MarksAndGrades");
                    return;
                }
                JSONObject response = new JSONObject(responseString);
                JSONArray gradesArray = response.optJSONArray("grades");

                if (gradesArray != null && this.cumulativeMarks != null) {
                    for (int i = 0; i < gradesArray.length(); ++i) {
                        JSONObject gradesObject = gradesArray.getJSONObject(i);
                        String courseCode = this.getStringValue(gradesObject, "course_code");

                        if (courseCode != null && this.cumulativeMarks.containsKey(courseCode)) {
                            CumulativeMark cm = this.cumulativeMarks.get(courseCode);
                            if (cm != null) {
                                cm.grade = this.getStringValue(gradesObject, "grade");
                            }
                        }
                    }
                }

                if (response.has("gpa") && !response.isNull("gpa")) {
                    String gpa = response.optString("gpa", "");
                    if (!gpa.isEmpty()) {
                        this.sharedPreferences.edit().putString("gpa", gpa).apply();
                    }
                }

                List<CumulativeMark> cumulativeMarks = this.cumulativeMarks != null
                        ? new ArrayList<>(this.cumulativeMarks.values())
                        : new ArrayList<>();
                MarksDao marksDao = this.appDatabase.marksDao();

                marksDao.deleteCumulativeMarks()
                        .andThen(cumulativeMarks.isEmpty() ? Completable.complete() : marksDao.insertCumulativeMarks(cumulativeMarks))
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                onStreamComplete("MarksAndGrades");
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(802, e.getLocalizedMessage());
                                onStreamComplete("MarksAndGrades");
                            }
                        });
            } catch (Exception e) {
                error(801, e.getLocalizedMessage());
                onStreamComplete("MarksAndGrades");
            }
        });
    }

    /**
     * Function to download the exam schedule.
     */
    private void downloadExamSchedule() {
        updateProgress(R.string.downloading_exam_schedule);

        /*
         *  JSON response format
         *
         *  {
         *      "FAT": [
         *          {
         *              "slot": "A1",
         *              "date": "01-JAN-2020",
         *              "start_time": "9:30 AM",
         *              "end_time": "12:30 PM",
         *              "venue": "DB-101",
         *              "seat_location": "R1C1",
         *              "seat_number": 1
         *          },
         *          ...
         *      ],
         *      ...
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var response = {};" +
                "try {" +
                "    var csrfEl = document.querySelector('input[name=\"_csrf\"]');" +
                "    var csrf = csrfEl ? csrfEl.value : '';" +
                "    var authIdEl = document.getElementById('authorizedIDX') || document.querySelector('input[name=\"authorizedID\"]');" +
                "    var authId = authIdEl ? authIdEl.value : '';" +
                "    var data = 'semesterSubId=' + '" + semesterID + "' + '&authorizedID=' + authId + '&_csrf=' + csrf;" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url: 'examinations/doSearchExamScheduleForStudent'," +
                "        data: data," +
                "        async: false," +
                "        success: function(res) {" +
                "            if (!res || res.toLowerCase().includes('not found')) {" +
                "                return;" +
                "            }" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var trs = doc.getElementsByTagName('tr');" +
                "            if (!trs || trs.length === 0) return;" +
                "            var columns = trs[0].getElementsByTagName('td');" +
                "            if (!columns || columns.length === 0) columns = trs[0].getElementsByTagName('th');" +
                "            if (!columns || columns.length === 0) return;" +
                "            var slotIndex = -1, dateIndex = -1, timingIndex = -1, venueIndex = -1, locationIndex = -1, numberIndex = -1;" +
                "            for (var i = 0; i < columns.length; ++i) {" +
                "                var heading = (columns[i].innerText || '').toLowerCase();" +
                "                if (heading.includes('slot')) {" +
                "                    slotIndex = i;" +
                "                } else if (heading.includes('date')) {" +
                "                    dateIndex = i;" +
                "                } else if (heading.includes('exam') && heading.includes('time')) {" +
                "                    timingIndex = i;" +
                "                } else if (heading.includes('venue')) {" +
                "                    venueIndex = i;" +
                "                } else if (heading.includes('location')) {" +
                "                    locationIndex = i;" +
                "                } else if (heading.includes('seat') && heading.includes('no.')) {" +
                "                    numberIndex = i;" +
                "                }" +
                "            }" +
                "            var examTitle = '', exam = {}, cells = doc.getElementsByTagName('td');" +
                "            for (var i = columns.length; i < cells.length; ++i) {" +
                "                if (cells[i].colSpan > 1) {" +
                "                    examTitle = (cells[i].innerText || '').trim();" +
                "                    if (examTitle) {" +
                "                        response[examTitle] = [];" +
                "                    }" +
                "                    continue;" +
                "                }" +
                "                if (!examTitle) continue;" +
                "                var index = (i - Object.keys(response).length) % columns.length;" +
                "                if (index == slotIndex) {" +
                "                    exam.slot = (cells[i].innerText || '').trim().split('+')[0];" +
                "                } else if (index == dateIndex) {" +
                "                    var date = (cells[i].innerText || '').trim().toUpperCase();" +
                "                    exam.date = date == '' ? null : date;" +
                "                } else if (index == timingIndex) {" +
                "                    var timings = (cells[i].innerText || '').trim().split('-');" +
                "                    if (timings.length == 2) {" +
                "                        exam.start_time = timings[0].trim();" +
                "                        exam.end_time = timings[1].trim();" +
                "                    } else {" +
                "                        exam.start_time = null;" +
                "                        exam.end_time = null;" +
                "                    }" +
                "                } else if (index == venueIndex) {" +
                "                    var venue = (cells[i].innerText || '').trim();" +
                "                    exam.venue = venue.replace(/-/g,'') == '' ? null : venue;" +
                "                } else if (index == locationIndex) {" +
                "                    var location = (cells[i].innerText || '').trim();" +
                "                    exam.seat_location = location.replace(/-/g,'') == '' ? null : location;" +
                "                } else if (index == numberIndex) {" +
                "                    var number = (cells[i].innerText || '').trim();" +
                "                    exam.seat_number = number.replace(/-/g,'') == '' ? null : parseInt(number);" +
                "                }" +
                "                if (Object.keys(exam).length == 7) {" +
                "                    if (response[examTitle]) {" +
                "                        response[examTitle].push(exam);" +
                "                    }" +
                "                    exam = {};" +
                "                }" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                    onStreamComplete("ExamSchedule");
                    return;
                }
                JSONObject response = new JSONObject(responseString);
                Iterator<String> keys = response.keys();
                List<Exam> exams = new ArrayList<>();

                SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
                SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH);

                int index = 1;

                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key.equals("error")) continue;
                    JSONArray examsArray = response.optJSONArray(key);
                    if (examsArray == null) continue;

                    for (int i = 0; i < examsArray.length(); ++i) {
                        JSONObject examObject = examsArray.getJSONObject(i);
                        Exam exam = new Exam();
                        Pattern pattern = Pattern.compile("\\d");
                        Matcher matcher = pattern.matcher(key);

                        exam.id = ++index;
                        exam.courseId = getCourseId(examObject.optString("slot", ""), Course.TYPE_THEORY);
                        exam.title = key;

                        if (matcher.find()) {
                            exam.title = new StringBuilder(key).insert(matcher.start(), " ").toString().trim().replaceAll(" +", " ");
                        }

                        String dateStr = getStringValue(examObject, "date");
                        String startTimeStr = getStringValue(examObject, "start_time");
                        String endTimeStr = getStringValue(examObject, "end_time");

                        if (dateStr != null && !dateStr.isEmpty()) {
                            try {
                                if (startTimeStr != null && !startTimeStr.isEmpty()) {
                                    Date parsed = dateTimeFormat.parse(dateStr + " " + startTimeStr);
                                    if (parsed != null) exam.startTime = parsed.getTime();
                                } else {
                                    Date parsed = dateFormat.parse(dateStr);
                                    if (parsed != null) exam.startTime = parsed.getTime();
                                }
                            } catch (Exception ignored) {}

                            try {
                                if (endTimeStr != null && !endTimeStr.isEmpty()) {
                                    Date parsed = dateTimeFormat.parse(dateStr + " " + endTimeStr);
                                    if (parsed != null) exam.endTime = parsed.getTime();
                                }
                            } catch (Exception ignored) {}
                        }

                        exam.venue = getStringValue(examObject, "venue");
                        exam.seatLocation = getStringValue(examObject, "seat_location");
                        exam.seatNumber = getIntegerValue(examObject, "seat_number");

                        exams.add(exam);
                    }
                }

                SettingsRepository.setExamNotifications(this.getApplicationContext(), exams);

                ExamsDao examsDao = appDatabase.examsDao();
                Completable.fromAction(() -> examsDao.replaceAll(exams))
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                onStreamComplete("ExamSchedule");
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(902, e.getLocalizedMessage());
                                onStreamComplete("ExamSchedule");
                            }
                        });
            } catch (Exception e) {
                error(901, e.getLocalizedMessage());
                onStreamComplete("ExamSchedule");
            }
        });
    }

    /**
     * Function to download the proctor info. (1 / 2 - Staff info)
     */
    private void downloadProctor() {
        updateProgress(R.string.downloading_staff);
        /*
         *  JSON response format
         *
         *  {
         *      "proctor" [
         *          {
         *              "key": "",
         *              "value": ""
         *          },
         *          ...
         *      ]
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var response = {" +
                "    proctor: []" +
                "};" +
                "try {" +
                "    var csrfEl = document.querySelector('input[name=\"_csrf\"]');" +
                "    var csrf = csrfEl ? csrfEl.value : '';" +
                "    var authIdEl = document.getElementById('authorizedIDX') || document.querySelector('input[name=\"authorizedID\"]');" +
                "    var authId = authIdEl ? authIdEl.value : '';" +
                "    var winImageEl = document.getElementById('winImage') || document.querySelector('input[name=\"winImage\"]');" +
                "    var winImage = winImageEl ? winImageEl.value : '';" +
                "    var data = 'verifyMenu=true&winImage=' + winImage + '&authorizedID=' + authId + '&_csrf=' + csrf + '&nocache=@(new Date().getTime())';" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url : 'proctor/viewProctorDetails'," +
                "        data : data," +
                "        async: false," +
                "        success: function(res) {" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var showDetails = doc.getElementById('showDetails');" +
                "            if (showDetails) {" +
                "                var cells = showDetails.getElementsByTagName('td');" +
                "                for (var i = 0; i < cells.length; ++i) {" +
                "                    if (cells[i].innerHTML.includes('img')) {" +
                "                        continue;" +
                "                    }" +
                "                    var record = {};" +
                "                    record.key = cells[i].innerText ? cells[i].innerText.trim() : null;" +
                "                    if (i + 1 < cells.length) {" +
                "                        i++;" +
                "                        record.value = cells[i].innerText ? cells[i].innerText.trim() : null;" +
                "                    } else {" +
                "                        record.value = null;" +
                "                    }" +
                "                    response.proctor.push(record);" +
                "                }" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                    downloadDeanHOD(0);
                    return;
                }
                JSONObject response = new JSONObject(responseString);
                JSONArray proctorArray = response.optJSONArray("proctor");
                if (proctorArray == null) proctorArray = new JSONArray();
                List<Staff> staff = new ArrayList<>();

                for (int i = 0; i < proctorArray.length(); ++i) {
                    JSONObject proctorObject = proctorArray.getJSONObject(i);
                    Staff staffItem = new Staff();

                    staffItem.id = i + 1;
                    staffItem.type = "proctor";
                    staffItem.key = this.getStringValue(proctorObject, "key");
                    staffItem.value = this.getStringValue(proctorObject, "value");

                    staff.add(staffItem);
                }

                StaffDao staffDao = appDatabase.staffDao();
                Completable.fromAction(() -> staffDao.replaceAll(staff))
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                downloadDeanHOD(staff.size());
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(1002, e.getLocalizedMessage());
                                downloadDeanHOD(staff.size());
                            }
                        });
            } catch (Exception e) {
                error(1001, e.getLocalizedMessage());
                downloadDeanHOD(0);
            }
        });
    }

    /**
     * Function to download the HOD & Dean info. (2 / 2 - Staff info)
     */
    private void downloadDeanHOD(final int lastIndex) {
        updateProgress(null);
        /*
         *  JSON response format
         *
         *  {
         *      "dean": [
         *          {
         *              "key": "",
         *              "value": ""
         *          },
         *          ...
         *      ],
         *      "hod": [
         *          {
         *              "key": "",
         *              "value": ""
         *          },
         *          ...
         *      ],
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var response = {};" +
                "try {" +
                "    var csrfEl = document.querySelector('input[name=\"_csrf\"]');" +
                "    var csrf = csrfEl ? csrfEl.value : '';" +
                "    var authIdEl = document.getElementById('authorizedIDX') || document.querySelector('input[name=\"authorizedID\"]');" +
                "    var authId = authIdEl ? authIdEl.value : '';" +
                "    var winImageEl = document.getElementById('winImage') || document.querySelector('input[name=\"winImage\"]');" +
                "    var winImage = winImageEl ? winImageEl.value : '';" +
                "    var data = 'verifyMenu=true&winImage=' + winImage + '&authorizedID=' + authId + '&_csrf=' + csrf + '&nocache=@(new Date().getTime())';" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url : 'hrms/viewHodDeanDetails'," +
                "        data : data," +
                "        async: false," +
                "        success: function(res) {" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var tables = doc.getElementsByTagName('table');" +
                "            var headings = doc.getElementsByTagName('h3');" +
                "            for (var i = 0; i < tables.length; ++i) {" +
                "                var heading = (i < headings.length && headings[i].innerText) ? headings[i].innerText.toLowerCase().trim() : ('staff_' + i);" +
                "                var cells = tables[i].getElementsByTagName('td');" +
                "                response[heading] = [];" +
                "                for (var j = 0; j < cells.length; ++j) {" +
                "                    if(cells[j].innerHTML.includes('img')) {" +
                "                        continue;" +
                "                    }" +
                "                    var record = {};" +
                "                    record.key = cells[j].innerText ? cells[j].innerText.trim() : null;" +
                "                    if (j + 1 < cells.length) {" +
                "                        j++;" +
                "                        record.value = cells[j].innerText ? cells[j].innerText.trim() : null;" +
                "                    } else {" +
                "                        record.value = null;" +
                "                    }" +
                "                    response[heading].push(record);" +
                "                }" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                    onStreamComplete("Staff");
                    return;
                }
                JSONObject response = new JSONObject(responseString);
                Iterator<String> keys = response.keys();
                List<Staff> staff = new ArrayList<>();
                int index = lastIndex;

                while (keys.hasNext()) {
                    String staffType = keys.next();
                    if (staffType.equals("error")) continue;
                    JSONArray staffArray = response.optJSONArray(staffType);
                    if (staffArray == null) continue;

                    if (staffType.contains("dean")) {
                        staffType = "dean";
                    } else if (staffType.contains("hod")) {
                        staffType = "hod";
                    } else {
                        staffType = staffType.toLowerCase();
                    }

                    for (int i = 0; i < staffArray.length(); ++i) {
                        JSONObject staffObject = staffArray.getJSONObject(i);
                        Staff staffItem = new Staff();

                        staffItem.id = ++index;
                        staffItem.type = staffType;
                        staffItem.key = this.getStringValue(staffObject, "key");
                        staffItem.value = this.getStringValue(staffObject, "value");

                        staff.add(staffItem);
                    }
                }

                StaffDao staffDao = appDatabase.staffDao();
                staffDao
                        .insert(staff)
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                onStreamComplete("Staff");
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(1004, e.getLocalizedMessage());
                                onStreamComplete("Staff");
                            }
                        });
            } catch (Exception e) {
                error(1003, e.getLocalizedMessage());
                onStreamComplete("Staff");
            }
        });
    }

    /**
     * Function to download the spotlight.
     */
    private void downloadSpotlight() {
        updateProgress(R.string.downloading_spotlight);

        /*
         *  JSON response format
         *
         *  {
         *      "spotlight": [
         *          {
         *              "announcement": "",
         *              "category": "",
         *              "link": null
         *          },
         *          ...
         *      ]
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var response = {" +
                "    spotlight: []" +
                "};" +
                "try {" +
                "    var csrfEl = document.querySelector('input[name=\"_csrf\"]');" +
                "    var csrf = csrfEl ? csrfEl.value : '';" +
                "    var authIdEl = document.getElementById('authorizedIDX') || document.querySelector('input[name=\"authorizedID\"]');" +
                "    var authId = authIdEl ? authIdEl.value : '';" +
                "    var data = '_csrf=' + csrf + '&authorizedID=' + authId + '&x=';" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url : 'home'," +
                "        data : data," +
                "        async: false," +
                "        success: function(res) {" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var sheets = doc.getElementsByClassName('offcanvas');" +
                "            for(var i = 0; i < sheets.length; ++i) {" +
                "                var header = sheets[i].getElementsByClassName('offcanvas-header')[0];" +
                "                if (!header) continue;" +
                "                var title = header.getElementsByTagName('span')[0];" +
                "                if (title === undefined) {" +
                "                    continue;" +
                "                }" +
                "                var category = title.textContent || '';" +
                "                var body = sheets[i].getElementsByClassName('offcanvas-body')[0];" +
                "                if (!body) continue;" +
                "                var announcements = body.getElementsByTagName('li');" +
                "                for(var j = 0; j < announcements.length; ++j) {" +
                "                    var spotlightItem = {};" +
                "                    spotlightItem.category = category;" +
                "                    spotlightItem.announcement = (announcements[j].textContent || '').replace(/\\t/g,'').replace(/\\n/g,' ').trim();" +
                "                    var aTags = announcements[j].getElementsByTagName('a');" +
                "                    if (aTags.length == 0) {" +
                "                        spotlightItem.link = null;" +
                "                    } else {" +
                "                        var link = aTags[0];" +
                "                        if(link.getAttribute('onclick')) {" +
                "                            var onclickStr = link.getAttribute('onclick');" +
                "                            var parts = onclickStr.split('\\'');" +
                "                            spotlightItem.link = parts.length > 1 ? parts[1] : null;" +
                "                        } else {" +
                "                            spotlightItem.link = link.href;" +
                "                        }" +
                "                    }" +
                "                    response.spotlight.push(spotlightItem);" +
                "                }" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                    onStreamComplete("Spotlight");
                    return;
                }
                JSONObject response = new JSONObject(responseString);
                JSONArray spotlightArray = response.optJSONArray("spotlight");
                if (spotlightArray == null) spotlightArray = new JSONArray();
                Map<Integer, Spotlight> spotlight = new HashMap<>();

                for (int i = 0; i < spotlightArray.length(); ++i) {
                    JSONObject spotlightObject = spotlightArray.getJSONObject(i);
                    Spotlight spotlightItem = new Spotlight();

                    spotlightItem.id = i + 1;
                    spotlightItem.announcement = this.getStringValue(spotlightObject, "announcement");
                    spotlightItem.category = this.getStringValue(spotlightObject, "category");
                    spotlightItem.link = this.getStringValue(spotlightObject, "link");

                    // Generating a unique hash signature to keep a track of read announcements
                    spotlightItem.signature = ((spotlightItem.announcement != null ? spotlightItem.announcement : "") + (spotlightItem.link != null ? spotlightItem.link : "")).hashCode();
                    spotlight.put(spotlightItem.signature, spotlightItem);
                }

                appDatabase.spotlightDao()
                        .insert(spotlight)
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                onStreamComplete("Spotlight");
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(1102, e.getLocalizedMessage());
                                onStreamComplete("Spotlight");
                            }
                        });
            } catch (Exception e) {
                error(1101, e.getLocalizedMessage());
                onStreamComplete("Spotlight");
            }
        });
    }

    /**
     * Function to download the payment receipts.
     */
    private void downloadReceipts() {
        updateProgress(R.string.downloading_receipts);

        /*
         *  JSON response format
         *
         *  {
         *      "receipts": [
         *          {
         *              "number": "10067",
         *              "amount": 97500,
         *              "date": "14-AUG-2020"
         *          }
         *      ]
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var response = {" +
                "    receipts: []" +
                "};" +
                "try {" +
                "    var csrfEl = document.querySelector('input[name=\"_csrf\"]');" +
                "    var csrf = csrfEl ? csrfEl.value : '';" +
                "    var authIdEl = document.getElementById('authorizedIDX') || document.querySelector('input[name=\"authorizedID\"]');" +
                "    var authId = authIdEl ? authIdEl.value : '';" +
                "    var winImageEl = document.getElementById('winImage') || document.querySelector('input[name=\"winImage\"]');" +
                "    var winImage = winImageEl ? winImageEl.value : '';" +
                "    var data = 'verifyMenu=true&winImage=' + winImage + '&authorizedID=' + authId + '&_csrf=' + csrf + '&nocache=@(new Date().getTime())';" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url : 'p2p/getReceiptsApplno'," +
                "        data : data," +
                "        async: false," +
                "        success: function(res) {" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var trs = doc.getElementsByTagName('tr');" +
                "            if (!trs || trs.length === 0) return;" +
                "            var headings = trs[0].getElementsByTagName('td');" +
                "            if (!headings || headings.length === 0) headings = trs[0].getElementsByTagName('th');" +
                "            if (!headings || headings.length === 0) return;" +
                "            var cells = doc.getElementsByTagName('td');" +
                "            var receiptIndex = -1, amountIndex = -1, dateIndex = -1;" +
                "            for(var i = 0; i < headings.length; ++i) {" +
                "                var heading = (headings[i].innerText || '').toLowerCase();" +
                "                if(heading.includes('receipt')) {" +
                "                    receiptIndex = i + headings.length;" +
                "                } else if (heading.includes('date')) {" +
                "                    dateIndex = i + headings.length;" +
                "                } else if (heading.includes('amount')) {" +
                "                    amountIndex = i + headings.length;" +
                "                }" +
                "            }" +
                "            while (receiptIndex >= 0 && receiptIndex < cells.length && amountIndex >= 0 && amountIndex < cells.length && dateIndex >= 0 && dateIndex < cells.length) {" +
                "                var receipt = {};" +
                "                receipt.number = parseInt(cells[receiptIndex].innerText.trim()) || null;" +
                "                receipt.amount = parseFloat(cells[amountIndex].innerText.trim()) || 0;" +
                "                receipt.date = (cells[dateIndex].innerText || '').trim();" +
                "                response.receipts.push(receipt);" +
                "                receiptIndex += headings.length;" +
                "                amountIndex += headings.length;" +
                "                dateIndex += headings.length;" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString == null || responseString.equals("null") || responseString.trim().isEmpty()) {
                    checkDues();
                    return;
                }
                JSONObject response = new JSONObject(responseString);
                JSONArray receiptsArray = response.optJSONArray("receipts");
                if (receiptsArray == null) receiptsArray = new JSONArray();
                List<Receipt> receipts = new ArrayList<>();
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);

                for (int i = 0; i < receiptsArray.length(); ++i) {
                    JSONObject receiptsObject = receiptsArray.getJSONObject(i);
                    Receipt receipt = new Receipt();

                    // If this is true, there's a web scrapping issue
                    if (receiptsObject.isNull("number") || !receiptsObject.has("number")) {
                        continue;
                    }

                    String receiptDateString = this.getStringValue(receiptsObject, "date");
                    Date receiptDate = null;
                    if (receiptDateString != null) {
                        try {
                            receiptDate = dateFormat.parse(receiptDateString);
                        } catch (Exception ignored) {}
                    }

                    receipt.number = receiptsObject.optInt("number");
                    receipt.amount = this.getDoubleValue(receiptsObject, "amount");
                    receipt.date = receiptDate != null ? receiptDate.getTime() : 0;

                    receipts.add(receipt);
                }

                ReceiptsDao receiptsDao = appDatabase.receiptsDao();
                Completable.fromAction(() -> receiptsDao.replaceAll(receipts))
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                checkDues();
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(1202, e.getLocalizedMessage());
                                checkDues();
                            }
                        });
            } catch (Exception e) {
                error(1201, e.getLocalizedMessage());
                checkDues();
            }
        });
    }

    /**
     * Function to check for any payment dues.
     */
    private void checkDues() {
        updateProgress(null);

        /*
         *  JSON response format
         *
         *  {
         *      "due_payments": true|false
         *  }
         */
        webView.evaluateJavascript("(function() {" +
                "var response = {};" +
                "try {" +
                "    var csrfEl = document.querySelector('input[name=\"_csrf\"]');" +
                "    var csrf = csrfEl ? csrfEl.value : '';" +
                "    var authIdEl = document.getElementById('authorizedIDX') || document.querySelector('input[name=\"authorizedID\"]');" +
                "    var authId = authIdEl ? authIdEl.value : '';" +
                "    var winImageEl = document.getElementById('winImage') || document.querySelector('input[name=\"winImage\"]');" +
                "    var winImage = winImageEl ? winImageEl.value : '';" +
                "    var data = 'verifyMenu=true&winImage=' + winImage + '&authorizedID=' + authId + '&_csrf=' + csrf + '&nocache=@(new Date().getTime())';" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url : 'p2p/Payments'," +
                "        data : data," +
                "        async: false," +
                "        success: function(res) {" +
                "            if (res && res.toLowerCase().includes('no payment dues')) {" +
                "                response.due_payments = false;" +
                "            } else {" +
                "                response.due_payments = true;" +
                "            }" +
                "        }" +
                "    });" +
                "} catch(e) {" +
                "    response.error = e.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                if (responseString != null && !responseString.equals("null") && !responseString.trim().isEmpty()) {
                    JSONObject response = new JSONObject(responseString);
                    boolean duePayments = response.optBoolean("due_payments", false);

                    if (duePayments) {
                        sharedPreferences.edit().putBoolean("duePayments", true).apply();
                    } else {
                        sharedPreferences.edit().remove("duePayments").apply();
                    }
                }
            } catch (Exception e) {
                error(1203, e.getLocalizedMessage());
            }

            onStreamComplete("Receipts");
        });
    }

    /**
     * Function to download the academic calendar from VTOP.
     *
     * Flow:
     *   1. POST academics/common/CalendarPreview  → page load (no data needed)
     *   2. POST getDateForSemesterPreview         → get semester start/end date
     *   3. POST processViewCalendar (per month)   → fetch monthly calendar HTML
     *
     * JSON response format built in JS:
     * {
     *   "events": [
     *     { "date": "2026-07-01", "day": 1, "month": 7, "year": 2026, "event": "Working Day" },
     *     ...
     *   ]
     * }
     */
    private void downloadCalendar() {
        updateProgress(R.string.downloading_calendar);

        webView.evaluateJavascript("(function() {" +
                "var response = { events: [] };" +
                "try {" +
                "    var csrf = $('input[name=\"_csrf\"]').val();" +
                "    var authId = $('#authorizedIDX').val() || '';" +
                "    var semSubId = '" + semesterID + "';" +
                "    var monthNames = ['JAN','FEB','MAR','APR','MAY','JUN','JUL','AUG','SEP','OCT','NOV','DEC'];" +
                "    function parseVtopDate(str) {" +
                "        if (!str) return null;" +
                "        str = str.trim().toUpperCase();" +
                "        var parts = str.split('-');" +
                "        if (parts.length < 3) return null;" +
                "        var d = parseInt(parts[0]), mon = monthNames.indexOf(parts[1]), y = parseInt(parts[2]);" +
                "        if (isNaN(d) || mon < 0 || isNaN(y)) return null;" +
                "        return new Date(y, mon, d);" +
                "    }" +
                // Step 1: Load CalendarPreview and collect initial available month options if any
                "    var calPreviewHtml = '';" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url: 'academics/common/CalendarPreview'," +
                "        data: 'verifyMenu=true&authorizedID=' + authId + '&_csrf=' + csrf + '&nocache=@(new Date().getTime())'," +
                "        async: false," +
                "        success: function(res) { calPreviewHtml = res || ''; }" +
                "    });" +
                // Step 2: Get semester date range from getDateForSemesterPreview
                "    var startDate = null, endDate = null;" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url: 'getDateForSemesterPreview'," +
                "        data: '_csrf=' + csrf + '&paramReturnId=getDateForSemesterPreview&semSubId=' + semSubId + '&authorizedID=' + authId + '&x=' + new Date().toUTCString()," +
                "        async: false," +
                "        success: function(res) {" +
                "            try {" +
                // Try JSON first (some VTOP versions return JSON)
                "                var json = JSON.parse(res);" +
                "                var keys = Object.keys(json);" +
                "                for (var k = 0; k < keys.length; k++) {" +
                "                    var key = keys[k].toLowerCase();" +
                "                    var val = json[keys[k]];" +
                "                    if (key.includes('from') || key.includes('start')) startDate = val;" +
                "                    else if (key.includes('to') || key.includes('end')) endDate = val;" +
                "                }" +
                "            } catch(ej) {" +
                // Not JSON — parse as HTML
                "                try {" +
                "                    var doc = new DOMParser().parseFromString(res, 'text/html');" +
                // Look for <input> with from/to/start/end in name
                "                    var inputs = doc.getElementsByTagName('input');" +
                "                    for (var i = 0; i < inputs.length; i++) {" +
                "                        var nm = (inputs[i].name || inputs[i].id || '').toLowerCase();" +
                "                        var val = inputs[i].value;" +
                "                        if (val && (nm.includes('from') || nm.includes('start'))) startDate = val;" +
                "                        else if (val && (nm.includes('to') || nm.includes('end'))) endDate = val;" +
                "                    }" +
                // Fallback: scan all text for DD-MMM-YYYY
                "                    if (!startDate || !endDate) {" +
                "                        var text = doc.body ? doc.body.innerText : res;" +
                "                        var matches = text.match(/\\d{2}-[A-Z]{3}-\\d{4}/gi);" +
                "                        if (matches && matches.length >= 2) {" +
                "                            startDate = matches[0];" +
                "                            endDate = matches[matches.length - 1];" +
                "                        }" +
                "                    }" +
                "                } catch(eh) {}" +
                "            }" +
                "        }" +
                "    });" +
                // If still no dates, fall back to smart guess based on semSubId
                "    if (!startDate || !endDate) {" +
                "        var yearMatch = semSubId.match(/\\d{4}/);" +
                "        var baseYear = yearMatch ? parseInt(yearMatch[0]) : new Date().getFullYear();" +
                "        var isWinter = semSubId.endsWith('5') || semSubId.endsWith('05') || semSubId.toLowerCase().includes('winter') || semSubId.toLowerCase().includes('win');" +
                "        if (isWinter) {" +
                "            startDate = '01-DEC-' + baseYear;" +
                "            endDate   = '31-MAY-' + (baseYear + 1);" +
                "        } else {" +
                "            startDate = '01-JUL-' + baseYear;" +
                "            endDate   = '31-DEC-' + baseYear;" +
                "        }" +
                "    }" +
                // Step 3: Scrape each month
                "    var start = parseVtopDate(startDate);" +
                "    var end   = parseVtopDate(endDate);" +
                "    if (!start) start = new Date();" +
                "    if (!end)   end   = start;" +
                "    var cur = new Date(start.getFullYear(), start.getMonth(), 1);" +
                "    var endMonth = new Date(end.getFullYear(), end.getMonth(), 1);" +
                "    while (cur <= endMonth) {" +
                // IIFE to capture cur's month/year for the async-safe closure
                "        (function(mm, yyyy) {" +
                "            var calDate = '01-' + monthNames[mm - 1] + '-' + yyyy;" +
                "            $.ajax({" +
                "                type: 'POST'," +
                "                url: 'processViewCalendar'," +
                "                data: '_csrf=' + csrf + '&calDate=' + calDate + '&semSubId=' + semSubId + '&classGroupId=ALL&authorizedID=' + authId + '&x=' + new Date().toUTCString()," +
                "                async: false," +
                "                success: function(res) {" +
                "                    try {" +
                "                        var doc2 = new DOMParser().parseFromString(res, 'text/html');" +
                "                        var tds = doc2.getElementsByTagName('td');" +
                "                        for (var t = 0; t < tds.length; t++) {" +
                "                            try {" +
                // The ACTUAL structure (from real HTML):
                // <td>
                //   <span style="...">1</span>   <- day number (empty string for padding cells)
                //   <span style="color:green">Instructional Day</span>  <- event type
                //   <span style="color:#eb556e">(Working Day)</span>     <- event detail
                // </td>
                "                                var spans = tds[t].getElementsByTagName('span');" +
                "                                if (spans.length === 0) continue;" +
                // spans[0] is always the day number span
                "                                var dayText = spans[0].innerText.trim();" +
                "                                if (!dayText) continue;" +  // blank = padding cell
                "                                var dayNum = parseInt(dayText);" +
                "                                if (isNaN(dayNum) || dayNum < 1 || dayNum > 31) continue;" +
                // Collect all non-empty spans[1..n] as the event description
                "                                var eventParts = [];" +
                "                                for (var s = 1; s < spans.length; s++) {" +
                "                                    var part = spans[s].innerText.trim();" +
                "                                    if (part) eventParts.push(part);" +
                "                                }" +
                "                                var eventLabel = eventParts.join(' ');" +
                "                                var dateStr = yyyy + '-' + String(mm).padStart(2,'0') + '-' + String(dayNum).padStart(2,'0');" +
                "                                response.events.push({" +
                "                                    date: dateStr," +
                "                                    day: dayNum," +
                "                                    month: mm," +
                "                                    year: yyyy," +
                "                                    event: eventLabel || null" +
                "                                });" +
                "                            } catch(et) {}" +
                "                        }" +
                "                    } catch(ec) {}" +
                "                }" +
                "            });" +
                "        })(cur.getMonth() + 1, cur.getFullYear());" +
                "        cur.setMonth(cur.getMonth() + 1);" +
                "    }" +
                "} catch(e0) {" +
                "    response.error = e0.message;" +
                "}" +
                "return response;" +
                "})();", responseString -> {
            try {
                JSONObject response = new JSONObject(responseString);
                JSONArray eventsArray = response.optJSONArray("events");

                if (eventsArray == null || eventsArray.length() == 0) {
                    // Calendar page may not be available for this semester; skip gracefully
                    onStreamComplete("Calendar");
                    return;
                }

                List<CalendarEvent> events = new ArrayList<>();
                for (int i = 0; i < eventsArray.length(); ++i) {
                    JSONObject obj = eventsArray.getJSONObject(i);
                    CalendarEvent ev = new CalendarEvent();
                    ev.date  = this.getStringValue(obj, "date");
                    ev.event = this.getStringValue(obj, "event");
                    ev.day   = this.getIntegerValue(obj, "day");
                    ev.month = this.getIntegerValue(obj, "month");
                    ev.year  = this.getIntegerValue(obj, "year");
                    events.add(ev);
                }

                CalendarDao calendarDao = appDatabase.calendarDao();
                Completable.fromAction(() -> calendarDao.replaceAll(events))
                        .subscribeOn(Schedulers.single())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new CompletableObserver() {
                            @Override
                            public void onSubscribe(@NonNull Disposable d) {
                                compositeDisposable.add(d);
                            }

                            @Override
                            public void onComplete() {
                                onStreamComplete("Calendar");
                            }

                            @Override
                            public void onError(@NonNull Throwable e) {
                                error(1302, e.getLocalizedMessage());
                                onStreamComplete("Calendar");
                            }
                        });
            } catch (Exception e) {
                error(1301, e.getLocalizedMessage());
                onStreamComplete("Calendar");
            }
        });
    }

    /**
     * Function to make final changes before signing the user in.
     */
    private void finishUp() {
        this.notification.setContentTitle(getString(R.string.completing_sync));
        this.notification.setProgress(0, 0, true);
        this.notification.setContentText(null);

        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, this.notification.build());

        this.sharedPreferences.edit().putBoolean("isVTOPSignedIn", true).apply();
        this.sharedPreferences.edit().putLong("lastRefreshed", Calendar.getInstance().getTimeInMillis()).apply();

        // Firebase Analytics Logging
        FirebaseAnalytics.getInstance(this).logEvent("data_sync", new Bundle());

        try {
            this.callback.onComplete();
        } catch (Exception ignored) {
        }

        this.endService(true);
    }

    /**
     * Function to get the slot ID using the slot.
     *
     * @param slot Ex: "A1", "L21", etc.
     * @return The slot ID
     */
    private Integer getSlotId(String slot, int courseType) {
        if (slot == null) {
            return null;
        }
        switch (courseType) {
            case Course.TYPE_LAB:
                if (this.labSlots == null || !this.labSlots.containsKey(slot)) {
                    return null;
                }
                Slot s = this.labSlots.get(slot);
                return s != null ? s.id : null;
            case Course.TYPE_PROJECT:
                if (this.projectSlots == null || !this.projectSlots.containsKey(slot)) {
                    return null;
                }
                Slot s2 = this.projectSlots.get(slot);
                return s2 != null ? s2.id : null;
            default:
                if (this.theorySlots == null || !this.theorySlots.containsKey(slot)) {
                    return null;
                }
                Slot s3 = this.theorySlots.get(slot);
                return s3 != null ? s3.id : null;
        }
    }

    /**
     * Function to get the course ID using the slot.
     *
     * @param slot Ex: "A1", "L21", etc.
     * @return The course ID
     */
    private Integer getCourseId(String slot, int courseType) {
        if (slot == null) {
            return null;
        }
        switch (courseType) {
            case Course.TYPE_LAB:
                if (this.labSlots == null || !this.labSlots.containsKey(slot)) {
                    return null;
                }
                Slot s = this.labSlots.get(slot);
                return s != null ? s.courseId : null;
            case Course.TYPE_PROJECT:
                if (this.projectSlots == null || !this.projectSlots.containsKey(slot)) {
                    return null;
                }
                Slot s2 = this.projectSlots.get(slot);
                return s2 != null ? s2.courseId : null;
            default:
                if (this.theorySlots == null || !this.theorySlots.containsKey(slot)) {
                    return null;
                }
                Slot s3 = this.theorySlots.get(slot);
                return s3 != null ? s3.courseId : null;
        }
    }

    /**
     * Function to get the course credits using the course ID.
     *
     * @param courseId The course ID as saved in the database
     * @return The number of credits for that course
     */
    private Integer getCourseCredits(Integer courseId, int courseType) {
        if (courseId == null) {
            return null;
        }
        switch (courseType) {
            case Course.TYPE_LAB:
                if (this.labCourses == null || !this.labCourses.containsKey(courseId)) {
                    return null;
                }
                Course c = this.labCourses.get(courseId);
                return c != null ? c.credits : null;
            case Course.TYPE_PROJECT:
                if (this.projectCourses == null || !this.projectCourses.containsKey(courseId)) {
                    return null;
                }
                Course c2 = this.projectCourses.get(courseId);
                return c2 != null ? c2.credits : null;
            default:
                if (this.theoryCourses == null || !this.theoryCourses.containsKey(courseId)) {
                    return null;
                }
                Course c3 = this.theoryCourses.get(courseId);
                return c3 != null ? c3.credits : null;
        }
    }

    /**
     * Function to get the course code using the course ID.
     *
     * @param courseId The course ID as saved in the database
     * @return The course code of that course
     */
    private String getCourseCode(Integer courseId, int courseType) {
        if (courseId == null) {
            return null;
        }
        switch (courseType) {
            case Course.TYPE_LAB:
                if (this.labCourses == null || !this.labCourses.containsKey(courseId)) {
                    return null;
                }
                Course c = this.labCourses.get(courseId);
                return c != null ? c.code : null;
            case Course.TYPE_PROJECT:
                if (this.projectCourses == null || !this.projectCourses.containsKey(courseId)) {
                    return null;
                }
                Course c2 = this.projectCourses.get(courseId);
                return c2 != null ? c2.code : null;
            default:
                if (this.theoryCourses == null || !this.theoryCourses.containsKey(courseId)) {
                    return null;
                }
                Course c3 = this.theoryCourses.get(courseId);
                return c3 != null ? c3.code : null;
        }
    }

    /**
     * Function to get the String value from a JSON object using a key.
     *
     * @param jsonObject The JSON object to be used
     * @param key        The key to be used to get the value
     * @return The value stored or null if the key wasn't present
     */
    private String getStringValue(JSONObject jsonObject, String key) throws JSONException {
        if (!jsonObject.has(key) || jsonObject.isNull(key)) {
            return null;
        }

        return jsonObject.getString(key);
    }

    /**
     * Function to get the Integer value from a JSON object using a key.
     *
     * @param jsonObject The JSON object to be used
     * @param key        The key to be used to get the value
     * @return The value stored or null if the key wasn't present
     */
    private Integer getIntegerValue(JSONObject jsonObject, String key) throws JSONException {
        if (!jsonObject.has(key) || jsonObject.isNull(key)) {
            return null;
        }

        return jsonObject.getInt(key);
    }

    /**
     * Function to get the Double value from a JSON object using a key.
     *
     * @param jsonObject The JSON object to be used
     * @param key        The key to be used to get the value
     * @return The value stored or null if the key wasn't present
     */
    private Double getDoubleValue(JSONObject jsonObject, String key) throws JSONException {
        if (!jsonObject.has(key) || jsonObject.isNull(key)) {
            return null;
        }

        return jsonObject.getDouble(key);
    }

    public interface Callback {
        void onRequestCaptcha(int captchaType, Bitmap bitmap, WebView webView);

        void onCaptchaComplete();

        void onRequestSemester(String[] semesters);

        void onServiceEnd();

        void onForceSignOut();

        void onComplete();
    }

    public static class ServiceBinder extends Binder {
        private final java.lang.ref.WeakReference<VTOPService> serviceRef;

        public ServiceBinder(VTOPService service) {
            this.serviceRef = new java.lang.ref.WeakReference<>(service);
        }

        public VTOPService getService() {
            return serviceRef.get();
        }

        public void setCallback(Callback mCallback) {
            VTOPService service = serviceRef.get();
            if (service != null) {
                service.callback = mCallback;
            }
        }
    }

    private enum PageState {LANDING, LOGIN, HOME}
}

/*
 * Error codes
 *
 * Error 101    Errors connecting to the server
 * Error 102    Error fetching user credentials
 * Error 103    Error opening sign in page
 * Error 104    Error getting captcha type
 * Error 105    Error getting default captcha image
 * Error 106    Error while attempting to sign in
 * Error 107    Unauthorised user agent used
 *
 * Error 201    Error fetching list of semesters
 *
 * Error 301    Error fetching user's name
 * Error 302    Error fetching user's credits & GPA
 *
 * Error 401    Error downloading courses
 * Error 402    Error saving courses to the database
 *
 * Error 501    Error downloading timetable
 * Error 502    Error saving timetable to the database
 *
 * Error 601    Error downloading attendance
 * Error 602    Error saving attendance to the database
 *
 * Error 701    Error downloading marks
 * Error 702    Error saving marks to the database
 *
 * Error 801    Error downloading grades
 * Error 802    Error saving grades to the database
 *
 * Error 901    Error downloading exam schedule
 * Error 902    Error saving exam schedule to the database
 *
 * Error 1001   Error downloading proctor info
 * Error 1002   Error saving proctor info to the database
 * Error 1003   Error downloading dean & hod info
 * Error 1004   Error saving dean & hod info to the database
 *
 * Error 1101   Error downloading spotlight
 * Error 1102   Error saving spotlight to the database
 *
 * Error 1201   Error downloading receipts
 * Error 1202   Error saving receipts to the database
 * Error 1203   Error checking for due payments
 *
 * Error 1301   Error downloading academic calendar
 * Error 1302   Error saving academic calendar to the database
 */
