package fr.maposition.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 10;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(28, 36, 28, 28);
        page.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Ma Position");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        page.addView(title);

        TextView info = new TextView(this);
        info.setText("Affiche la rue et la commune dans un petit bandeau flottant.");
        info.setTextSize(17);
        info.setPadding(0, 24, 0, 24);
        page.addView(info);

        Button start = new Button(this);
        start.setText("Afficher le bandeau");
        page.addView(start);
        Button stop = new Button(this);
        stop.setText("Masquer le bandeau");
        page.addView(stop);

        TextView note = new TextView(this);
        note.setText("Autorisez le GPS et l'affichage par-dessus les autres applications.");
        note.setTextSize(14);
        note.setPadding(0, 24, 0, 0);
        page.addView(note);
        setContentView(page);

        start.setOnClickListener(v -> prepareAndStart());
        stop.setOnClickListener(v -> stopService(new Intent(this, LocationOverlayService.class)));
    }

    private void prepareAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent overlay = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(overlay);
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        startOverlayService();
    }

    private void startOverlayService() {
        Intent service = new Intent(this, LocationOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) prepareAndStart();
    }
}
