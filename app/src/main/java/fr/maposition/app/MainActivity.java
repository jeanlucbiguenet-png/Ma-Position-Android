
package fr.maposition.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Context;


import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 10;
    private static final int DATABASE_REQUEST = 20;

    private TextView info;

   public static File getDatabaseFile(Context activity) {
        File[] dirs = activity.getExternalFilesDirs(null);
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir != null && Environment.isExternalStorageRemovable(dir)) {
                    return new File(dir, "adresses.db");
                }
            }
        }
        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
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

        info = new TextView(this);
        info.setText("Sélectionnez la base BAN sur votre carte SD.");
        info.setTextSize(16);
        info.setPadding(0, 24, 0, 24);
        page.addView(info);

        Button select = new Button(this);
        select.setText("Choisir la base BAN");
        page.addView(select);

        Button start = new Button(this);
        start.setText("Afficher le bandeau");
        page.addView(start);

        Button stop = new Button(this);
        stop.setText("Masquer le bandeau");
        page.addView(stop);

        setContentView(page);

        select.setOnClickListener(v -> chooseDatabase());
        start.setOnClickListener(v -> prepareAndStart());
        stop.setOnClickListener(v ->
            stopService(new Intent(this, LocationOverlayService.class)));
    }

    private void chooseDatabase() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, DATABASE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == DATABASE_REQUEST && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            info.setText("Copie de la base BAN en cours...");

            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... unused) {
                    try {
                        File destination = getDatabaseFile(MainActivity.this);
                        if (destination == null) {
                            return "Carte SD introuvable.";
                        }

                        File folder = destination.getParentFile();
                        if (!folder.exists() && !folder.mkdirs()) {
                            return "Impossible de créer le dossier.";
                        }

                        try (InputStream input =
                                getContentResolver().openInputStream(uri);
                             FileOutputStream output =
                                new FileOutputStream(destination)) {

                            if (input == null) return "Lecture impossible.";

                            byte[] buffer = new byte[1024 * 1024];
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                output.write(buffer, 0, count);
                            }
                            output.getFD().sync();
                        }

                        return "Copie terminée. Vous pouvez démarrer.";
                    } catch (Exception e) {
                        return "Erreur pendant la copie : " + e.getMessage();
                    }
                }

                @Override
                protected void onPostExecute(String result) {
                    info.setText(result);
                }
            }.execute();
        }
    }

    private void prepareAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent overlay = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivity(overlay);
            return;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_REQUEST);
            return;
        }

        File database = getDatabaseFile(this);
        if (database == null || !database.exists()) {
            info.setText("Choisissez d'abord la base BAN.");
            chooseDatabase();
            return;
        }

        Intent service = new Intent(this, LocationOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);

        if (requestCode == LOCATION_REQUEST && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            prepareAndStart();
        }
    }
}
