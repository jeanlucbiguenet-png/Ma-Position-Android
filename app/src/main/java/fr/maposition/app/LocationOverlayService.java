package fr.maposition.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;F
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

public class LocationOverlayService extends Service implements LocationListener {

    private static final String CHANNEL_ID = "maposition_location";
    private WindowManager windowManager;
    private TextView bubble;
    private LocationManager locationManager;
private File routesDatabaseFile;
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Ma Position")
                .setContentText("Localisation active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
        } else {
            notification = new Notification.Builder(this)
                .setContentTitle("Ma Position")
                .setContentText("Localisation active")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
        }

        startForeground(1, notification);
        showBubble();
        startLocationUpdates();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Localisation Ma Position",
                NotificationManager.IMPORTANCE_LOW
            );
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
        }
    }

    private void showBubble() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
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
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 80;

        try {
            windowManager.addView(bubble, params);

            bubble.setOnTouchListener(new android.view.View.OnTouchListener() {
                private int startX, startY;
                private float touchX, touchY;

                @Override
                public boolean onTouch(android.view.View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startX = params.x;
                        startY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        params.x = startX + (int) (event.getRawX() - touchX);
                        params.y = startY + (int) (event.getRawY() - touchY);
                        try {
                            windowManager.updateViewLayout(bubble, params);
                        } catch (Exception ignored) { }
                        return true;
                    }
                    return true;
                }
            });
        } catch (Exception e) {
            stopSelf();
        }
    }

    private void startLocationUpdates() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {

                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 5000L, 10f, this
                );

                Location last = locationManager.getLastKnownLocation(
                    LocationManager.GPS_PROVIDER
                );

                if (last != null) onLocationChanged(last);
            }
        } catch (Exception e) {
            setBubbleText("GPS indisponible");
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        new Thread(() -> {
            String result = findAddress(location);
            new android.os.Handler(getMainLooper()).post(
                () -> setBubbleText(result)
            );
        }).start();
    }
private String findRoad(Location location) {
     File file = MainActivity.getRoutesDatabaseFile(this);

    if (file == null || !file.exists()) {
        return null;
    }

    SQLiteDatabase database = null;
    Cursor cursor = null;

    try {
        database = SQLiteDatabase.openDatabase(
            file.getAbsolutePath(),
            null,
            SQLiteDatabase.OPEN_READONLY
        );

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        double x = 6378137.0 * Math.toRadians(lon);
        double y = 6378137.0 * Math.log(
            Math.tan(Math.PI / 4.0 + Math.toRadians(lat) / 2.0)
        );

        cursor = database.rawQuery(
            "SELECT name, ref, geom FROM routes",
            null
        );

        double bestDistance = Double.MAX_VALUE;
        String bestRoad = null;

        while (cursor.moveToNext()) {
            String name = cursor.getString(0);
            String ref = cursor.getString(1);
            String geom = cursor.getString(2);

            if (geom == null) continue;

            String[] points = geom
                .replace("LINESTRING (", "")
                .replace(")", "")
                .split(",");

            for (String point : points) {
                String[] coordinates = point.trim().split("\\s+");
                if (coordinates.length < 2) continue;

                double rx = Double.parseDouble(coordinates[0]);
                double ry = Double.parseDouble(coordinates[1]);

                double distance = Math.hypot(x - rx, y - ry);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestRoad = (ref != null && !ref.isEmpty())
                        ? ref
                        : name;
                }
            }
        }

        if (bestDistance <= 100 && bestRoad != null) {
            return bestRoad;
        }

        return null;

    } catch (Exception e) {
        return null;
    } finally {
        if (cursor != null) cursor.close();
        if (database != null) database.close();
    }
}
   


    private String findAddress(Location location) {
        File databaseFile = MainActivity.getDatabaseFile(this);

        if (databaseFile == null || !databaseFile.exists()) {
            return "Base BAN introuvable";
        }

        SQLiteDatabase database = null;
        Cursor cursor = null;

        try {
            database = SQLiteDatabase.openDatabase(
                databaseFile.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY
            );

            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            double cosLatitude = Math.cos(Math.toRadians(latitude));

            String bestNumber = null;
            String bestRep = null;
            String bestStreet = null;
            String bestCity = null;
            double bestDistance = Double.MAX_VALUE;

            double[] radii = {0.0005, 0.001, 0.002, 0.005, 0.01};

            for (double radius : radii) {
                double latRadius = radius;
                double lonRadius = radius / Math.max(0.1, cosLatitude);

                cursor = database.rawQuery(
                    "SELECT numero, rep, nom_voie, nom_commune, lat, lon " +
                    "FROM adresses " +
                    "WHERE lat BETWEEN ? AND ? " +
                    "AND lon BETWEEN ? AND ? LIMIT 5000",
                    new String[] {
                        String.valueOf(latitude - latRadius),
                        String.valueOf(latitude + latRadius),
                        String.valueOf(longitude - lonRadius),
                        String.valueOf(longitude + lonRadius)
                    }
                );

                while (cursor.moveToNext()) {
                    double addressLat = cursor.getDouble(4);
                    double addressLon = cursor.getDouble(5);

                    float[] distance = new float[1];
                    Location.distanceBetween(
                        latitude, longitude,
                        addressLat, addressLon,
                        distance
                    );

                    if (distance[0] < bestDistance) {
                        bestDistance = distance[0];
                        bestNumber = cursor.getString(0);
                        bestRep = cursor.getString(1);
                        bestStreet = cursor.getString(2);
                        bestCity = cursor.getString(3);
                    }
                }

                cursor.close();
                cursor = null;

               if (bestDistance <= 50) break;
            }

           if (bestStreet == null || bestDistance > 50) {
    String road = findRoad(location);

    if (road != null) {
        return road;
    }

    return bestCity != null ? bestCity : "Adresse non disponible";
}
            }

            StringBuilder result = new StringBuilder();

            if (bestNumber != null && !bestNumber.isEmpty()) {
                result.append(bestNumber);
                if (bestRep != null && !bestRep.isEmpty()) {
                    result.append(" ").append(bestRep);
                }
                result.append(" ");
            }

            result.append(bestStreet);

            if (bestCity != null && !bestCity.isEmpty()) {
                result.append("\n").append(bestCity);
            }

            return result.toString();

        } catch (Exception e) {
            return "Erreur lecture base BAN";
        } finally {
            if (cursor != null) cursor.close();
            if (database != null) database.close();
        }
    }

    private void setBubbleText(String value) {
        if (bubble != null) bubble.setText(value);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) {
        setBubbleText("Activez le GPS");
    }

    @Override
    public void onDestroy() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (Exception ignored) { }
        }

        if (windowManager != null && bubble != null) {
            try {
                windowManager.removeView(bubble);
            } catch (Exception ignored) { }
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
