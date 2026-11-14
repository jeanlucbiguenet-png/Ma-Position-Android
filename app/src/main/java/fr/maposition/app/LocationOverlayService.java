package fr.maposition.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

public class LocationOverlayService extends Service implements LocationListener {
    private static final String CHANNEL_ID = "maposition_location";
    private WindowManager windowManager;
    private TextView bubble;
    private LocationManager locationManager;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Ma Position").setContentText("Localisation active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation).build();
        } else {
            notification = new Notification.Builder(this)
                .setContentTitle("Ma Position").setContentText("Localisation active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation).build();
        }
        startForeground(1, notification);
        showBubble();
        startLocationUpdates();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Localisation Ma Position", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private void showBubble() {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        bubble = new TextView(this);
        bubble.setText("Recherche de votre position…");
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setTextSize(16);
        bubble.setPadding(22, 16, 22, 16);
        bubble.setBackgroundColor(0xEE203040);
        bubble.setGravity(Gravity.CENTER);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 80;
        try {
            windowManager.addView(bubble, params);
            bubble.setOnTouchListener(new android.view.View.OnTouchListener() {
                private int startX, startY;
                private float touchX, touchY;
                @Override public boolean onTouch(android.view.View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startX = params.x;
                        startY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        params.x = startX + (int)(event.getRawX() - touchX);
                        params.y = startY + (int)(event.getRawY() - touchY);
                        try { windowManager.updateViewLayout(bubble, params); } catch (Exception ignored) { }
                        return true;
                    }
                    return true;
                }
            });
        } catch (Exception e) { stopSelf(); }
    }

    private void startLocationUpdates() {
        try {
            locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f, this);
                Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last != null) onLocationChanged(last);
            }
        } catch (Exception e) { setBubbleText("GPS indisponible"); }
    }

    @Override public void onLocationChanged(Location location) {
        new Thread(() -> {
            String result;
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address a = addresses.get(0);
                    String street = a.getThoroughfare();
                    String number = a.getSubThoroughfare();
                    String city = a.getLocality();
                    if (city == null) city = a.getSubAdminArea();
                    StringBuilder text = new StringBuilder();
                    if (number != null) text.append(number).append(" ");
                    if (street != null) text.append(street);
                    if (text.length() == 0) text.append("Adresse non disponible");
                    if (city != null && !city.isEmpty()) text.append("\n").append(city);
                    result = text.toString();
                } else result = "Adresse non disponible";
            } catch (Exception e) { result = "Recherche de l'adresse…"; }
            final String display = result;
            new android.os.Handler(getMainLooper()).post(() -> setBubbleText(display));
        }).start();
    }

    private void setBubbleText(String value) { if (bubble != null) bubble.setText(value); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { setBubbleText("Activez le GPS"); }

    @Override public void onDestroy() {
        if (locationManager != null) try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        if (windowManager != null && bubble != null) try { windowManager.removeView(bubble); } catch (Exception ignored) { }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
