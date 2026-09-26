package ru.elmarto.lysy;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int PICK_GAME_HTML = 4101;
    private WebView webView;
    private File installedGame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersive();

        installedGame = new File(new File(getFilesDir(), "gate1"), "index.html");

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setBackgroundColor(0xFF151515);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                this::handleAndroidBack
            );
        }

        if (installedGame.isFile() && installedGame.length() > 1024 * 1024) {
            loadInstalledGame();
        } else {
            webView.loadUrl("file:///android_asset/index.html");
            webView.postDelayed(this::chooseGameHtml, 450);
        }
    }

    private void chooseGameHtml() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_GAME_HTML);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_GAME_HTML || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri source = data.getData();
        webView.evaluateJavascript(
            "document.body.setAttribute('data-copying','1');" +
            "var s=document.getElementById('status');if(s)s.textContent='Копирую игру внутрь приложения…';",
            null
        );

        new Thread(() -> importGame(source)).start();
    }

    private void importGame(Uri source) {
        File dir = installedGame.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        File tmp = new File(dir, "index.html.tmp");

        try (InputStream in = getContentResolver().openInputStream(source);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IllegalStateException("Cannot open selected file");
            byte[] buffer = new byte[1024 * 1024];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                total += read;
            }
            out.flush();

            if (total < 5L * 1024L * 1024L) {
                throw new IllegalStateException("Selected file is too small");
            }

            if (installedGame.exists() && !installedGame.delete()) {
                throw new IllegalStateException("Cannot replace previous game");
            }
            if (!tmp.renameTo(installedGame)) {
                throw new IllegalStateException("Cannot finalize imported game");
            }

            runOnUiThread(this::loadInstalledGame);
        } catch (Exception e) {
            if (tmp.exists()) tmp.delete();
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            runOnUiThread(() -> webView.evaluateJavascript(
                "var s=document.getElementById('status');if(s)s.textContent=" + jsQuote("Ошибка импорта: " + msg) + ";",
                null
            ));
        }
    }

    private static String jsQuote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'";
    }

    private void loadInstalledGame() {
        webView.loadUrl(Uri.fromFile(installedGame).toString());
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void handleAndroidBack() {
        if (webView == null) {
            finish();
            return;
        }
        webView.evaluateJavascript(
            "(function(){try{" +
            "if(window.LysyGateApp&&typeof window.LysyGateApp.handleBack==='function')" +
            "return !!window.LysyGateApp.handleBack();" +
            "}catch(e){} return false;})()",
            value -> {
                if (!"true".equals(value)) finish();
            }
        );
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            handleAndroidBack();
        } else {
            super.onBackPressed();
        }
    }

    private void forceGamePause() {
        if (webView == null) return;
        webView.evaluateJavascript(
            "(function(){try{" +
            "if(window.LysyGateApp&&typeof window.LysyGateApp.forcePause==='function')" +
            "{window.LysyGateApp.forcePause();return;}" +
            "if(window.__LYSY_RUNTIME__&&typeof window.__LYSY_RUNTIME__.setPaused==='function')" +
            "{window.__LYSY_RUNTIME__.setPaused(true);}" +
            "}catch(e){}})()",
            null
        );
    }

    @Override
    protected void onPause() {
        forceGamePause();
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.resumeTimers();
            webView.onResume();
        }
        enterImmersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
