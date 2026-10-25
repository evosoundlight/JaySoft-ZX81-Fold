package com.zx81fold.emulator;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.RenderProcessGoneDetail;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;
import java.io.OutputStream;

/**
 * ZX81 Fold first Android alpha.
 * Keeps the existing ZX84 / ZX81 ROM web runtime in one WebView, so changing
 * screen geometry does not recreate the Z80 or lose its emulated RAM.
 * All program loading/saving stays local through the Android document picker.
 */
public final class MainActivity extends Activity {
    private static final int PICK_ROM_PROGRAM = 101;
    private static final int SAVE_PROGRAM = 102;
    private static final String HOME = "file:///android_asset/index.html";
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingSave;
    private String pendingName;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        installCrashReporter();
        SharedPreferences previous = getSharedPreferences("zx81_crash", MODE_PRIVATE);
        String oldCrash = previous.getString("last_crash", null);
        if (oldCrash != null && !oldCrash.isEmpty()) {
            previous.edit().remove("last_crash").apply();
            showDiagnostic("Previous crash report", oldCrash);
            return;
        }
        startEmulator();
    }

    private void startEmulator() {
        try {
            initialiseWebView();
        } catch (Throwable failure) {
            Log.e("ZX81Fold", "App failed to start", failure);
            showDiagnostic("Android startup error", Log.getStackTraceString(failure));
        }
    }

    private void initialiseWebView() {
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Do not request WindowInsetsController here: on Android 17 the
        // DecorView does not exist until we have installed our content view.
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        setContentView(web);
        // Schedule immersive mode once the WebView is attached to the window.
        // On first startup this avoids PhoneWindow.getInsetsController() NPE.
        web.post(this::applyImmersive);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setDefaultTextEncodingName("UTF-8");
        s.setMediaPlaybackRequiresUserGesture(true);

        // The only JS-exposed operation is to open the Android system Save As dialog.
        // Keep navigation locked to local bundled assets; don't enable INTERNET.
        web.addJavascriptInterface(new LocalSaveBridge(), "ZX81Android");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                String report = "Android WebView renderer stopped. Did it crash? " + detail.didCrash()
                    + "\nRenderer priority: " + detail.rendererPriorityAtExit()
                    + "\nSystem: Android " + Build.VERSION.RELEASE
                    + " (API " + Build.VERSION.SDK_INT + ")";
                Log.e("ZX81Fold", report);
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) parent.removeView(view);
                view.destroy();
                if (web == view) web = null;
                showDiagnostic("WebView renderer stopped", report);
                return true;
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(HOME.equals(url)) view.evaluateJavascript(
                    "document.documentElement.classList.add('native-apk');", null);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !HOME.equals(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url) {
                return !HOME.equals(url);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view,
                       ValueCallback<Uri[]> callback,FileChooserParams params) {
                if(fileCallback!=null){fileCallback.onReceiveValue(null);}
                fileCallback=callback;
                Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.setType("*/*");
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(pick,PICK_ROM_PROGRAM);
                } catch(ActivityNotFoundException ex){
                    fileCallback.onReceiveValue(null);
                    fileCallback=null;
                    Toast.makeText(MainActivity.this,"No file picker found",Toast.LENGTH_LONG).show();
                }
                return true;
            }
            @Override public boolean onJsAlert(WebView view,String url,String message,JsResult result){
                new AlertDialog.Builder(MainActivity.this).setTitle("ZX81 Fold")
                    .setMessage(message).setPositiveButton("OK",(d,w)->result.confirm())
                    .setOnCancelListener(d->result.cancel()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView view,String url,String message,JsResult result){
                new AlertDialog.Builder(MainActivity.this).setTitle("ZX81 Fold")
                    .setMessage(message).setPositiveButton("Yes",(d,w)->result.confirm())
                    .setNegativeButton("No",(d,w)->result.cancel())
                    .setOnCancelListener(d->result.cancel()).show();
                return true;
            }
            @Override public boolean onJsPrompt(WebView view,String url,String message,
                        String defaultValue,JsPromptResult result){
                EditText input=new EditText(MainActivity.this);
                input.setSingleLine(true);
                input.setText(defaultValue==null?"":defaultValue);
                input.setSelectAllOnFocus(true);
                new AlertDialog.Builder(MainActivity.this).setTitle(message)
                    .setView(input).setPositiveButton("OK",(d,w)->result.confirm(input.getText().toString()))
                    .setNegativeButton("Cancel",(d,w)->result.cancel())
                    .setOnCancelListener(d->result.cancel()).show();
                return true;
            }
        });
        web.loadUrl(HOME);
    }

    // Diagnostic alpha: preserve the Android stack trace when the Java process fails.
    // On the next launch the report appears before the ZX81 WebView is created.
    private void installCrashReporter() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            try {
                getSharedPreferences("zx81_crash", MODE_PRIVATE).edit()
                    .putString("last_crash", "Thread: " + thread.getName() + "\n"
                        + Log.getStackTraceString(failure))
                    .commit();
            } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, failure);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    private void showDiagnostic(String heading, String report) {
        // Avoid WebView for reporting a WebView failure.
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(22, 24, 22, 24);
        page.setBackgroundColor(Color.rgb(25, 25, 25));
        TextView title = new TextView(this);
        title.setText("ZX81 FOLD - DIAGNOSTICS\n" + heading);
        title.setTextSize(20);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, 20);
        page.addView(title);
        TextView details = new TextView(this);
        details.setText("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT
            + ")\n\n" + report);
        details.setTextColor(Color.WHITE);
        details.setTextSize(12);
        details.setTextIsSelectable(true);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(details);
        page.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button copy = new Button(this);
        copy.setText("COPY ERROR REPORT");
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("ZX81 Fold error", details.getText()));
            toast("Error copied. Paste it into our ChatGPT conversation.");
        });
        page.addView(copy);
        Button retry = new Button(this);
        retry.setText("TRY TO OPEN ZX81");
        retry.setOnClickListener(v -> startEmulator());
        page.addView(retry);
        setContentView(page);
    }

    private void applyImmersive(){
        // Window.getInsetsController() can throw an NPE before a DecorView
        // exists (observed on Samsung Android 17 / API 37). Work from the
        // attached decor view instead and safely skip until it is available.
        View decor = getWindow().peekDecorView();
        if (decor == null || !decor.isAttachedToWindow()) return;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    // Fold and rotation are configuration changes, not an emulator reboot.
    @Override public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        applyImmersive();
        if(web!=null){
            web.requestLayout();
            web.evaluateJavascript("window.dispatchEvent(new Event('resize'));",null);
        }
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus)applyImmersive();
    }

    // A content:// URI from ACTION_OPEN_DOCUMENT goes to <input type=file>.
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_ROM_PROGRAM){
            if(fileCallback!=null){
                Uri uri=resultCode==RESULT_OK&&data!=null?data.getData():null;
                fileCallback.onReceiveValue(uri!=null?new Uri[]{uri}:null);
                fileCallback=null;
            }
        } else if(requestCode==SAVE_PROGRAM){
            byte[] save=pendingSave;pendingSave=null;pendingName=null;
            if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null&&save!=null){
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                    if(out==null)throw new Exception("Cannot open destination");
                    out.write(save);
                    toast("Cassette saved");
                } catch(Exception ex){toast("Save failed: "+ex.getMessage());}
            }
        }
        applyImmersive();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private final class LocalSaveBridge {
        // Called only by a direct tap on BASIC Workshop PASTE/COPY.
        // The local WebView cannot always use navigator.clipboard on file://.
        @JavascriptInterface public String readClipboard(){
            try {
                ClipboardManager board=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(board==null||!board.hasPrimaryClip())return "";
                ClipData data=board.getPrimaryClip();
                if(data==null||data.getItemCount()==0)return "";
                CharSequence value=data.getItemAt(0).coerceToText(MainActivity.this);
                if(value==null)return "";
                String text=value.toString();
                return text.length()>30000?text.substring(0,30000):text;
            } catch(Exception ignored){return "";}
        }
        @JavascriptInterface public void writeClipboard(String text){
            if(text==null||text.length()>30000)return;
            runOnUiThread(()->{
                ClipboardManager board=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(board!=null)board.setPrimaryClip(ClipData.newPlainText("ZX81 BASIC",text));
            });
        }
        @JavascriptInterface public void saveFile(String filename,String encoded){
            // The bridge is called on a WebView thread, not the Android UI thread.
            if(encoded==null || encoded.length()>2000000)return;
            try{
                byte[] data=Base64.decode(encoded,Base64.DEFAULT);
                runOnUiThread(()->startSavePicker(filename,data));
            }catch(IllegalArgumentException ex){runOnUiThread(()->toast("Tape data invalid"));}
        }
    }
    private void startSavePicker(String requested,byte[] data){
        if(pendingSave!=null){toast("Finish the previous recording first");return;}
        pendingSave=data;
        String safe=(requested==null?"zx81_program.p":requested)
            .replaceAll("[^a-zA-Z0-9 _().-]","_");
        if(!safe.toLowerCase(java.util.Locale.ROOT).endsWith(".p"))safe+=".p";
        pendingName=safe;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/octet-stream");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,safe);
        try{startActivityForResult(intent,SAVE_PROGRAM);}
        catch(ActivityNotFoundException ex){pendingSave=null;pendingName=null;toast("No Save As file picker found");}
    }

    @Override public void onBackPressed(){
        // Close the recorder/help first; otherwise explicitly warn before
        // ending a session, since a real ZX81 loses unsaved RAM on power off.
        if(web!=null){
            web.evaluateJavascript("(function(){var t=document.getElementById('tapeOverlay');"+
                "if(t&&t.classList.contains('show')){document.getElementById('tapeClose').click();return 'closed';}"+
                "var n=document.getElementById('dialog');"+
                "if(n&&n.classList.contains('open')){document.getElementById('close').click();return 'closed';}"+
                "return 'exit';})()", value->{
                    if(value!=null&&value.contains("closed"))return;
                    new AlertDialog.Builder(this).setTitle("Switch off ZX81?")
                        .setMessage("Unsaved programs and variables will be lost.")
                        .setPositiveButton("Power off",(d,w)->finish())
                        .setNegativeButton("Keep running",(d,w)->{}).show();
                });
            return;
        }
        super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(isFinishing()&&web!=null){
            web.removeJavascriptInterface("ZX81Android");
            web.destroy();
            web=null;
        }
        super.onDestroy();
    }
}