package com.alhowail.field;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.print.PdfPrinter;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Native helpers for the Alhowail Field app:
 *  - getLocation: Android's own location service, including the "mock location" (fake GPS) flag
 *  - printHtml:   prints HTML with Android's print system (Save as PDF / share)
 */
@CapacitorPlugin(
    name = "AlhowailNative",
    permissions = {
        @Permission(alias = "location", strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION })
    }
)
public class AlhowailNativePlugin extends Plugin {

    private WebView printView;   // kept alive until the print job has been handed to Android

    @PluginMethod
    public void getLocation(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "locationPermissionResult");
            return;
        }
        readLocation(call);
    }

    @PermissionCallback
    private void locationPermissionResult(PluginCall call) {
        if (getPermissionState("location") == PermissionState.GRANTED) readLocation(call);
        else call.reject("Location permission denied", "GPS_DENIED");
    }

    private void readLocation(final PluginCall call) {
        final LocationManager lm = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { call.reject("No location service", "NO_GPS"); return; }
        final boolean gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        final boolean netOn = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gpsOn && !netOn) { call.reject("Location is turned off", "NO_GPS"); return; }

        final int timeout = call.getInt("timeout", 20000);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Location[] best = { null };
        final boolean[] done = { false };

        final LocationListener listener = new LocationListener() {
            @Override public void onLocationChanged(Location loc) {
                if (done[0] || loc == null) return;
                if (best[0] == null || (loc.hasAccuracy() && loc.getAccuracy() < best[0].getAccuracy())) best[0] = loc;
                if (isMock(loc) || (loc.hasAccuracy() && loc.getAccuracy() <= 25)) finish(lm, this, call, best[0], done, handler);
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(String provider) { }
            @Override public void onProviderDisabled(String provider) { }
        };

        try {
            if (gpsOn) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
            if (netOn) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper());
        } catch (SecurityException e) {
            call.reject("Location permission denied", "GPS_DENIED");
            return;
        }

        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (done[0]) return;
                if (best[0] != null) finish(lm, listener, call, best[0], done, handler);
                else { done[0] = true; lm.removeUpdates(listener); call.reject("Could not get the location in time", "GPS_TIMEOUT"); }
            }
        }, timeout);
    }

    private void finish(LocationManager lm, LocationListener listener, PluginCall call, Location loc, boolean[] done, Handler handler) {
        if (done[0]) return;
        done[0] = true;
        lm.removeUpdates(listener);
        handler.removeCallbacksAndMessages(null);
        JSObject r = new JSObject();
        r.put("lat", loc.getLatitude());
        r.put("lng", loc.getLongitude());
        r.put("accuracy", loc.hasAccuracy() ? loc.getAccuracy() : 9999);
        r.put("mock", isMock(loc));
        r.put("provider", loc.getProvider());
        call.resolve(r);
    }

    @SuppressWarnings("deprecation")
    private static boolean isMock(Location loc) {
        if (Build.VERSION.SDK_INT >= 31) return loc.isMock();
        return loc.isFromMockProvider();
    }

    @PluginMethod
    public void printHtml(final PluginCall call) {
        final String html = call.getString("html");
        final String name = call.getString("name", "Alhowail");
        if (html == null || html.isEmpty()) { call.reject("Nothing to print"); return; }
        getActivity().runOnUiThread(new Runnable() {
            @Override public void run() {
                final boolean[] printed = { false };
                WebView wv = new WebView(getActivity());
                wv.getSettings().setJavaScriptEnabled(false);
                wv.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView view, String url) {
                        if (printed[0]) return;
                        printed[0] = true;
                        PrintManager pm = (PrintManager) getActivity().getSystemService(Context.PRINT_SERVICE);
                        PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(name);
                        pm.print(name, adapter, new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
                        call.resolve();
                    }
                });
                printView = wv;
                wv.loadDataWithBaseURL("https://fonts.googleapis.com/", html, "text/html", "UTF-8", null);
            }
        });
    }

    /* ---------- PDF: build the file from HTML, then share (WhatsApp / any app) or save to Downloads ---------- */
    private void makePdf(final String html, final String name, final PdfPrinter.Done done) {
        getActivity().runOnUiThread(new Runnable() {
            @Override public void run() {
                final boolean[] started = { false };
                final WebView wv = new WebView(getActivity());
                wv.getSettings().setJavaScriptEnabled(false);
                wv.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView view, String url) {
                        if (started[0]) return;
                        started[0] = true;
                        final File dir = new File(getContext().getCacheDir(), "pdf");
                        dir.mkdirs();
                        final File out = new File(dir, safeName(name) + ".pdf");
                        // small delay so web fonts (Arabic) finish painting
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { @Override public void run() {
                            PrintAttributes attrs = new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build();
                            PdfPrinter.write(view.createPrintDocumentAdapter(name), attrs, out, done);
                        } }, 700);
                    }
                });
                printView = wv;
                wv.loadDataWithBaseURL("https://fonts.googleapis.com/", html, "text/html", "UTF-8", null);
            }
        });
    }

    private static String safeName(String n) {
        String s = n == null ? "Alhowail" : n.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        return s.isEmpty() ? "Alhowail" : (s.length() > 90 ? s.substring(0, 90) : s);
    }

    @PluginMethod
    public void sharePdf(final PluginCall call) {
        final String html = call.getString("html");
        final String name = call.getString("name", "Alhowail");
        final String target = call.getString("target", "any");
        if (html == null || html.isEmpty()) { call.reject("Nothing to share"); return; }
        makePdf(html, name, new PdfPrinter.Done() {
            @Override public void ok(File file) {
                try {
                    Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("application/pdf");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent chosen = null;
                    if ("whatsapp".equals(target)) {
                        for (String pkg : new String[] { "com.whatsapp", "com.whatsapp.w4b" }) {
                            Intent w = new Intent(send); w.setPackage(pkg);
                            if (w.resolveActivity(getContext().getPackageManager()) != null) { chosen = w; break; }
                        }
                    }
                    if (chosen == null) chosen = Intent.createChooser(send, name);
                    getActivity().startActivity(chosen);
                    JSObject r = new JSObject(); r.put("shared", true); call.resolve(r);
                } catch (Exception e) { call.reject("Could not share the PDF", "SHARE_FAILED"); }
            }
            @Override public void fail(String why) { call.reject("Could not create the PDF: " + why, "PDF_FAILED"); }
        });
    }

    @PluginMethod
    public void savePdf(final PluginCall call) {
        final String html = call.getString("html");
        final String name = call.getString("name", "Alhowail");
        if (html == null || html.isEmpty()) { call.reject("Nothing to save"); return; }
        makePdf(html, name, new PdfPrinter.Done() {
            @Override public void ok(File file) {
                try {
                    String fileName = safeName(name) + ".pdf";
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                        v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                        v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        Uri dest = getContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                        if (dest == null) throw new Exception("no destination");
                        try (InputStream in = new FileInputStream(file); OutputStream os = getContext().getContentResolver().openOutputStream(dest)) {
                            byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                        }
                        JSObject r = new JSObject(); r.put("saved", true); r.put("where", "Downloads/" + fileName); call.resolve(r);
                    } else {
                        // Android 9 and older: let the user pick where to save / which app to open it with
                        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
                        Intent view = new Intent(Intent.ACTION_VIEW);
                        view.setDataAndType(uri, "application/pdf");
                        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        getActivity().startActivity(Intent.createChooser(view, fileName));
                        JSObject r = new JSObject(); r.put("saved", true); r.put("where", "chooser"); call.resolve(r);
                    }
                } catch (Exception e) { call.reject("Could not save the PDF", "SAVE_FAILED"); }
            }
            @Override public void fail(String why) { call.reject("Could not create the PDF: " + why, "PDF_FAILED"); }
        });
    }
}
