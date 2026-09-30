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
}
