package com.nathandev.localagent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Optional model file downloads. Does NOT replace or modify Codex's SarahEngine.
 * DownloadManager survives activity closes; model inference is not yet connected.
 */
public final class ModelDownloadCenter {
    private ModelDownloadCenter() { }

    private static final class Model {
        final String id, title, info, filename, url, sha;
        Model(String i, String t, String d, String f, String u, String h) {
            id=i; title=t; info=d; filename=f; url=u; sha=h;
        }
    }

    private static final Model[] MODELS = new Model[] {
        new Model("qwen", "Sarah · Texte", "Qwen3 0.6B · GGUF Q4_K_M · 484 Mo",
            "Qwen3-0.6B.Q4_K_M.gguf",
            "https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B.Q4_K_M.gguf",
            "7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d"),
        new Model("whisper", "Yohan · Transcription", "Whisper tiny multilingue · GGML · 78 Mo",
            "ggml-tiny.bin",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin",
            "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21")
    };

    public static void show(Activity activity, boolean dark) {
        final int bg = dark ? 0xff121622 : Color.WHITE;
        final int fg = dark ? Color.WHITE : 0xff0f172a;
        final int mute = dark ? 0xff8b95a5 : 0xff64748b;
        final int card = dark ? 0xff1a2030 : 0xfff2f4f8;
        final SharedPreferences prefs = activity.getSharedPreferences("saraiya_model_downloads", Context.MODE_PRIVATE);
        final DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Set<String> verifying = new HashSet<String>();
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        body.setPadding(pad, pad, pad, pad);
        body.setBackgroundColor(bg);

        TextView title = label(activity, "✦ Installer les modèles", 22, fg, true);
        body.addView(title);
        TextView subtitle = label(activity,
            "L'application reste légère : les modèles sont facultatifs et sont téléchargés en arrière-plan sur Wi-Fi.", 12, mute, false);
        subtitle.setPadding(0, dp(activity, 8), 0, dp(activity, 16));
        body.addView(subtitle);

        Button all = button(activity, "Installer le pack léger", 0xff2c6cf5);
        body.addView(all);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(activity, 12), 0, 0);
        body.addView(list);
        TextView warning = label(activity,
            "Fichiers vérifiés par SHA-256. Le téléchargement ne signifie pas que l'inférence IA ou Whisper sont déjà activés dans Sarah Engine.", 11, mute, false);
        warning.setPadding(0, dp(activity, 12), 0, 0);
        body.addView(warning);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        final AlertDialog dialog = new AlertDialog.Builder(activity).setView(scroll)
            .setNegativeButton("Fermer", null).create();
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            if (!dialog.isShowing() || activity.isFinishing()) return;
            list.removeAllViews();
            for (Model m : MODELS) {
                File target = destination(activity, m);
                boolean good = target != null && target.isFile() &&
                    prefs.getBoolean("verified_" + m.id, false);
                LinearLayout item = new LinearLayout(activity);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setPadding(dp(activity, 13), dp(activity, 13), dp(activity, 13), dp(activity, 13));
                item.setBackground(shape(card, dp(activity, 19)));
                item.addView(label(activity, m.title, 15, fg, true));
                item.addView(label(activity, m.info, 11, mute, false));
                String status = good ? "✓ Modèle téléchargé et vérifié" : status(manager, prefs.getLong("job_" + m.id, -1L));
                if (verifying.contains(m.id)) status = "Vérification SHA-256 en cours…";
                if (!good && "Transfert terminé".equals(status) && target != null && target.exists() && !verifying.contains(m.id)) {
                    verify(activity, m, prefs, handler, verifying, refresh[0]);
                    status = "Vérification SHA-256 en cours…";
                }
                item.addView(label(activity, status, 12, good ? 0xff32b695 : mute, true));
                Button install = button(activity, good ? "Installé ✓" : "Installer", good ? 0xff63708c : 0xff2c6cf5);
                install.setEnabled(!good);
                install.setOnClickListener(v -> {
                    start(activity, manager, prefs, m, verifying, handler, refresh[0]);
                    refresh[0].run();
                });
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(activity, 43));
                bp.topMargin = dp(activity, 9);
                item.addView(install, bp);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
                ip.bottomMargin = dp(activity, 10);
                list.addView(item, ip);
            }
            handler.postDelayed(refresh[0], 1500);
        };
        all.setOnClickListener(v -> {
            for (Model model : MODELS) start(activity, manager, prefs, model, verifying, handler, refresh[0]);
            refresh[0].run();
        });
        dialog.setOnDismissListener(d -> handler.removeCallbacks(refresh[0]));
        dialog.show();
        refresh[0].run();
    }

    private static void start(Activity a, DownloadManager dm, SharedPreferences prefs, Model m,
                              Set<String> verifying, Handler handler, Runnable refresh) {
        if (dm == null) { Toast.makeText(a, "Gestionnaire de téléchargement indisponible", Toast.LENGTH_LONG).show(); return; }
        File file = destination(a, m);
        if (file == null) { Toast.makeText(a, "Stockage indisponible", Toast.LENGTH_LONG).show(); return; }
        if (file.exists()) {
            verify(a, m, prefs, handler, verifying, refresh);
            return;
        }
        long prior = prefs.getLong("job_" + m.id, -1L);
        String state = status(dm, prior);
        if (prior >= 0 && (state.startsWith("Téléchargement") || state.startsWith("En attente"))) return;
        try {
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(m.url));
            req.setTitle("Saraiya · " + m.title);
            req.setDescription("Téléchargement du modèle sur Wi-Fi");
            req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
            req.setAllowedOverRoaming(false);
            req.setDestinationInExternalFilesDir(a, "models", m.filename);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            long id = dm.enqueue(req);
            prefs.edit().putLong("job_" + m.id, id).putBoolean("verified_" + m.id, false).apply();
            Toast.makeText(a, "Téléchargement lancé en arrière-plan sur Wi-Fi", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(a, "Téléchargement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String status(DownloadManager dm, long id) {
        if (dm == null || id < 0) return "Non installé";
        Cursor c = null;
        try {
            c = dm.query(new DownloadManager.Query().setFilterById(id));
            if (c == null || !c.moveToFirst()) return "Non installé";
            int state = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (state == DownloadManager.STATUS_SUCCESSFUL) return "Transfert terminé";
            if (state == DownloadManager.STATUS_FAILED) return "Échec : retenter le téléchargement";
            if (state == DownloadManager.STATUS_PAUSED) return "En attente du Wi-Fi";
            if (state == DownloadManager.STATUS_PENDING) return "En attente du téléchargement";
            long got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long size = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            return size > 0 ? "Téléchargement : " + (got * 100 / size) + "%" : "Téléchargement en cours…";
        } catch (Exception e) { return "Non installé"; }
        finally { if (c != null) c.close(); }
    }

    private static void verify(Activity a, Model m, SharedPreferences prefs, Handler handler,
                               Set<String> verifying, Runnable refresh) {
        if (verifying.contains(m.id)) return;
        verifying.add(m.id);
        new Thread(() -> {
            boolean valid = false;
            File file = destination(a, m);
            try (FileInputStream input = new FileInputStream(file)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[32768];
                int n;
                while ((n = input.read(buffer)) >= 0) digest.update(buffer, 0, n);
                StringBuilder hex = new StringBuilder();
                for (byte b : digest.digest()) hex.append(String.format(Locale.US, "%02x", b & 255));
                valid = hex.toString().equalsIgnoreCase(m.sha);
            } catch (Exception ignored) { }
            final boolean ok = valid;
            handler.post(() -> {
                verifying.remove(m.id);
                prefs.edit().putBoolean("verified_" + m.id, ok).apply();
                if (!ok) {
                    if (file != null) file.delete();
                    prefs.edit().remove("job_" + m.id).apply();
                    Toast.makeText(a, "Modèle invalide : téléchargement à recommencer", Toast.LENGTH_LONG).show();
                }
                if (!a.isFinishing()) refresh.run();
            });
        }).start();
    }
    private static File destination(Activity a, Model m) {
        File dir = a.getExternalFilesDir("models");
        return dir == null ? null : new File(dir, m.filename);
    }
    private static TextView label(Activity a, String s, int size, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s); t.setTextColor(color); t.setTextSize(size);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private static GradientDrawable shape(int c, int r) {
        GradientDrawable d = new GradientDrawable(); d.setColor(c); d.setCornerRadius(r); return d;
    }
    private static Button button(Activity a, String s, int color) {
        Button b = new Button(a); b.setAllCaps(false); b.setText(s);
        b.setTextColor(Color.WHITE); b.setTextSize(13);
        b.setBackground(shape(color, dp(a, 22))); return b;
    }
    private static int dp(Activity a, int v) {
        return (int) (a.getResources().getDisplayMetrics().density * v + .5f);
    }
}
