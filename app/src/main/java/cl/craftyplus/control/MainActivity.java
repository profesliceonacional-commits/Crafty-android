package cl.craftyplus.control;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Offline Android app for a Crafty+ USB-C via native BLE. Unofficial beta. */
public final class MainActivity extends Activity implements CraftyBle.Listener {
    private static final int PERMISSION_REQUEST = 42;

    private static final int BG = Color.parseColor("#EEF2F7");
    private static final int SURFACE = Color.parseColor("#FFFFFF");
    private static final int SURFACE_SOFT = Color.parseColor("#F8FAFC");
    private static final int PRIMARY = Color.parseColor("#0F766E");
    private static final int PRIMARY_DARK = Color.parseColor("#115E59");
    private static final int SECONDARY = Color.parseColor("#4F46E5");
    private static final int INK = Color.parseColor("#0F172A");
    private static final int MUTED = Color.parseColor("#64748B");
    private static final int BORDER = Color.parseColor("#DDE6EE");
    private static final int SUCCESS_BG = Color.parseColor("#DCFCE7");
    private static final int SUCCESS_FG = Color.parseColor("#166534");
    private static final int WARNING_BG = Color.parseColor("#FEF3C7");
    private static final int WARNING_FG = Color.parseColor("#92400E");
    private static final int OFF_BG = Color.parseColor("#E2E8F0");
    private static final int OFF_FG = Color.parseColor("#475569");

    private CraftyBle controller;
    private CraftyBle.State last = new CraftyBle.State();

    private TextView connectionLabel, temperatureNow, batteryLabel, modeLabel, targetLabel,
            boostLabel, boostDescription, ledLabel, autoOffLabel, infoLabel, noticeLabel,
            connectionCaption, heroSubtitle;
    private SeekBar targetSeek, boostSeek, brightnessSeek, offSeek;
    private Switch vibrateSwitch, chargeLedSwitch, permanentSwitch, fahrenheitSwitch, phoneVibrationSwitch, phoneSoundSwitch;
    private boolean fahrenheit, phoneVibration, phoneSound;
    private Button scanButton, disconnectButton, refreshButton, targetApply,
            boostApply, brightnessApply, offApply, heaterButton, findButton, factoryReset;
    private boolean painting;
    private boolean draggingTarget, draggingBoost, draggingBrightness, draggingOff;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controller = new CraftyBle(this, this);
        fahrenheit = getPreferences(MODE_PRIVATE).getBoolean("fahrenheit", false);
        phoneVibration = getPreferences(MODE_PRIVATE).getBoolean("phoneVibration", false);
        phoneSound = getPreferences(MODE_PRIVATE).getBoolean("phoneSound", false);
        buildUi();
        render(last);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private GradientDrawable roundedFill(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        return bg;
    }

    private GradientDrawable roundedStroke(int fill, int stroke, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(dp(radiusDp));
        bg.setStroke(dp(1), stroke);
        return bg;
    }

    private TextView label(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void add(LinearLayout parent, View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(topDp);
        parent.addView(v, lp);
    }

    private LinearLayout card(LinearLayout root, String eyebrow, String title) {
        LinearLayout card = vertical();
        card.setBackground(roundedStroke(SURFACE, BORDER, 22));
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        root.addView(card, lp);
        if (eyebrow != null) {
            TextView e = label(eyebrow.toUpperCase(), 11, SECONDARY, true);
            card.addView(e);
        }
        if (title != null) {
            TextView h = label(title, 20, INK, true);
            add(card, h, eyebrow == null ? 0 : 4);
        }
        return card;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.WHITE : INK);
        b.setBackground(primary ? roundedFill(PRIMARY, 14) : roundedStroke(SURFACE_SOFT, BORDER, 14));
        b.setMinHeight(dp(48));
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        return b;
    }

    @SuppressWarnings("deprecation")
    private Switch toggle(String s) {
        Switch sw = new Switch(this);
        sw.setText(s);
        sw.setTextColor(INK);
        sw.setTextSize(15);
        sw.setPadding(0, dp(10), 0, dp(10));
        return sw;
    }

    private SeekBar seek(int max, int progress) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(progress);
        return sb;
    }

    private SeekBar.OnSeekBarChangeListener seekListener(Runnable onMove, int index) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) onMove.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { setDragging(index, true); }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { setDragging(index, false); }
        };
    }

    private void setDragging(int index, boolean value) {
        if (index == 0) draggingTarget = value;
        if (index == 1) draggingBoost = value;
        if (index == 2) draggingBrightness = value;
        if (index == 3) draggingOff = value;
    }

    private LinearLayout pillRow(String leftText, String rightText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView left = label(leftText, 12, PRIMARY_DARK, true);
        left.setBackground(roundedFill(Color.parseColor("#D1FAE5"), 999));
        left.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(-2, -2);
        lp1.rightMargin = dp(8);
        row.addView(left, lp1);

        TextView right = label(rightText, 12, SECONDARY, true);
        right.setBackground(roundedFill(Color.parseColor("#E0E7FF"), 999));
        right.setPadding(dp(10), dp(6), dp(10), dp(6));
        row.addView(right);
        return row;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(24), dp(16), dp(30));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = label("Crafty+ Control", 30, INK, true);
        root.addView(title);
        add(root, label("Una interfaz más limpia para tu CRAFTY+ USB-C", 14, MUTED, false), 4);
        add(root, pillRow("Bluetooth LE", "Android nativo"), 12);

        LinearLayout header = card(root, "Estado", "Tu dispositivo y conexión");
        connectionLabel = label("Desconectado", 14, OFF_FG, true);
        connectionLabel.setBackground(roundedFill(OFF_BG, 999));
        connectionLabel.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.addView(connectionLabel);

        connectionCaption = label("Conecta tu CRAFTY+ para empezar a controlar temperatura, boost y brillo.", 13, MUTED, false);
        connectionCaption.setLineSpacing(dp(3), 1f);
        add(header, connectionCaption, 10);

        noticeLabel = label("Asegúrate de que el vaporizador esté encendido y con batería suficiente.", 13, MUTED, false);
        noticeLabel.setLineSpacing(dp(3), 1f);
        add(header, noticeLabel, 6);

        scanButton = button("Buscar y conectar CRAFTY+", true);
        add(header, scanButton, 14);
        scanButton.setOnClickListener(v -> startScanWithPermissions());

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        add(header, headerRow, 10);
        disconnectButton = button("Desconectar", false);
        refreshButton = button("Actualizar datos", false);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(48), 1);
        left.rightMargin = dp(8);
        headerRow.addView(disconnectButton, left);
        headerRow.addView(refreshButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        disconnectButton.setOnClickListener(v -> controller.disconnect());
        refreshButton.setOnClickListener(v -> controller.refresh());

        LinearLayout temperatureCard = card(root, "Panel principal", "Temperatura y sesión");
        LinearLayout hero = vertical();
        hero.setBackground(roundedFill(PRIMARY, 24));
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        add(temperatureCard, hero, 14);

        heroSubtitle = label("Temperatura actual", 13, Color.parseColor("#D1FAE5"), true);
        hero.addView(heroSubtitle);
        temperatureNow = label("— °C", 44, Color.WHITE, true);
        add(hero, temperatureNow, 8);
        modeLabel = label("Esperando lecturas", 14, Color.parseColor("#E6FFFB"), false);
        add(hero, modeLabel, 4);
        batteryLabel = label("Batería: —", 14, Color.WHITE, true);
        add(hero, batteryLabel, 14);

        targetLabel = label("Objetivo: 180 °C", 15, INK, true);
        add(temperatureCard, targetLabel, 18);
        targetSeek = seek(170, 140);
        add(temperatureCard, targetSeek, 6);
        targetSeek.setOnSeekBarChangeListener(seekListener(() -> {
            targetLabel.setText("Objetivo: " + temperatureString(40 + targetSeek.getProgress()));
            updateBoostText();
        }, 0));
        targetApply = button("Guardar temperatura", true);
        add(temperatureCard, targetApply, 6);
        targetApply.setOnClickListener(v -> controller.setTarget(40 + targetSeek.getProgress()));

        boostLabel = label("Boost: +15 °C", 15, INK, true);
        add(temperatureCard, boostLabel, 20);
        boostSeek = seek(30, 15);
        add(temperatureCard, boostSeek, 5);
        boostSeek.setOnSeekBarChangeListener(seekListener(this::updateBoostText, 1));
        boostDescription = label("", 12, MUTED, false);
        boostDescription.setLineSpacing(dp(3), 1f);
        add(temperatureCard, boostDescription, 3);
        boostApply = button("Guardar boost", false);
        add(temperatureCard, boostApply, 8);
        boostApply.setOnClickListener(v -> controller.setBoost(boostSeek.getProgress()));

        LinearLayout controls = card(root, "Acciones", "Equipo");
        heaterButton = button("Encender calefactor", true);
        add(controls, heaterButton, 12);
        heaterButton.setOnClickListener(v -> {
            boolean on = CraftyCodec.isSet(last.projectRegister, CraftyCodec.HEATER_ACTIVE);
            if (on) {
                controller.heater(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("¿Encender calefactor?")
                        .setMessage("El CRAFTY+ empezará a calentarse. Mantén el equipo en una superficie segura y supervisa su funcionamiento. No lo uses mientras carga por USB-C.")
                        .setPositiveButton("Encender", (dialog, which) -> controller.heater(true))
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        });

        vibrateSwitch = toggle("Vibración del CRAFTY+");
        add(controls, vibrateSwitch, 12);
        vibrateSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (!painting) controller.setStatusBit(CraftyCodec.VIBRATION_DISABLED, !checked);
        });
        chargeLedSwitch = toggle("LED de carga activo");
        add(controls, chargeLedSwitch, 0);
        chargeLedSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (!painting) controller.setStatusBit(CraftyCodec.CHARGE_LED_DISABLED, !checked);
        });
        permanentSwitch = toggle("Bluetooth permanente");
        add(controls, permanentSwitch, 0);
        permanentSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (!painting) {
                new AlertDialog.Builder(this)
                        .setTitle("Bluetooth permanente")
                        .setMessage("Mantener la radio Bluetooth activa puede consumir batería aun con el equipo apagado. ¿Cambiar esta opción?")
                        .setPositiveButton("Aplicar", (d, w) -> controller.setStatusBit(CraftyCodec.PERMANENT_BLE, checked))
                        .setNegativeButton("Cancelar", (d, w) -> render(last))
                        .show();
            }
        });
        findButton = button("Localizar CRAFTY+ (vibrar / LED)", false);
        add(controls, findButton, 12);
        findButton.setOnClickListener(v -> controller.findDevice());

        LinearLayout additional = card(root, "Ajustes", "Brillo y apagado");
        ledLabel = label("Brillo LED: 100 %", 15, INK, true);
        add(additional, ledLabel, 12);
        brightnessSeek = seek(100, 100);
        add(additional, brightnessSeek, 4);
        brightnessSeek.setOnSeekBarChangeListener(seekListener(() ->
                ledLabel.setText("Brillo LED: " + brightnessSeek.getProgress() + " %"), 2));
        brightnessApply = button("Guardar brillo", false);
        add(additional, brightnessApply, 6);
        brightnessApply.setOnClickListener(v -> controller.setBrightness(brightnessSeek.getProgress()));

        autoOffLabel = label("Apagado automático: 2 min", 15, INK, true);
        add(additional, autoOffLabel, 18);
        offSeek = seek(4, 1);
        add(additional, offSeek, 4);
        offSeek.setOnSeekBarChangeListener(seekListener(() ->
                autoOffLabel.setText("Apagado automático: " + (offSeek.getProgress() + 1) + " min"), 3));
        offApply = button("Guardar apagado automático", false);
        add(additional, offApply, 6);
        offApply.setOnClickListener(v -> controller.setAutoOff((offSeek.getProgress() + 1) * 60));

        LinearLayout phone = card(root, "Preferencias", "Del teléfono");
        fahrenheitSwitch = toggle("Mostrar temperatura en °F");
        fahrenheitSwitch.setChecked(fahrenheit);
        add(phone, fahrenheitSwitch, 10);
        fahrenheitSwitch.setOnCheckedChangeListener((v, checked) -> {
            fahrenheit = checked;
            getPreferences(MODE_PRIVATE).edit().putBoolean("fahrenheit", checked).apply();
            render(last);
        });

        phoneVibrationSwitch = toggle("Vibración del teléfono al alcanzar objetivo");
        phoneVibrationSwitch.setChecked(phoneVibration);
        add(phone, phoneVibrationSwitch, 6);
        phoneVibrationSwitch.setOnCheckedChangeListener((v, checked) -> {
            phoneVibration = checked;
            getPreferences(MODE_PRIVATE).edit().putBoolean("phoneVibration", checked).apply();
        });

        phoneSoundSwitch = toggle("Aviso sonoro del teléfono");
        phoneSoundSwitch.setChecked(phoneSound);
        add(phone, phoneSoundSwitch, 4);
        phoneSoundSwitch.setOnCheckedChangeListener((v, checked) -> {
            phoneSound = checked;
            getPreferences(MODE_PRIVATE).edit().putBoolean("phoneSound", checked).apply();
        });

        LinearLayout info = card(root, "Información", "Datos del dispositivo");
        infoLabel = label("Conecta tu equipo para consultar firmware, número de serie y horas de uso.", 14, MUTED, false);
        infoLabel.setLineSpacing(dp(4), 1.0f);
        add(info, infoLabel, 12);
        factoryReset = button("Restablecer ajustes de fábrica", false);
        add(info, factoryReset, 14);
        factoryReset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Restablecer CRAFTY+")
                .setMessage("Se perderán los cambios de temperatura y preferencias guardados en el equipo. Esta acción no se puede deshacer. ¿Continuar?")
                .setPositiveButton("Restablecer", (d, which) -> controller.factoryReset())
                .setNegativeButton("Cancelar", null).show());

        TextView footer = label("Versión beta no oficial. Interfaz nativa con Bluetooth LE, sin anuncios ni cuenta. Las actualizaciones de firmware no están incluidas en esta app.", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setLineSpacing(dp(3), 1f);
        add(root, footer, 22);
    }

    private void paintStatusChip(boolean connected, boolean connecting) {
        if (connected) {
            connectionLabel.setTextColor(SUCCESS_FG);
            connectionLabel.setBackground(roundedFill(SUCCESS_BG, 999));
        } else if (connecting) {
            connectionLabel.setTextColor(WARNING_FG);
            connectionLabel.setBackground(roundedFill(WARNING_BG, 999));
        } else {
            connectionLabel.setTextColor(OFF_FG);
            connectionLabel.setBackground(roundedFill(OFF_BG, 999));
        }
    }

    private String temperatureString(int c) {
        if (c < 0) return "—";
        return fahrenheit ? Math.round(c * 1.8f + 32) + " °F" : c + " °C";
    }

    private void reachedTargetAlert() {
        if (phoneVibration) {
            try {
                Vibrator v;
                if (Build.VERSION.SDK_INT >= 31) {
                    VibratorManager mgr = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                    v = mgr == null ? null : mgr.getDefaultVibrator();
                } else {
                    v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                }
                if (v != null && v.hasVibrator()) {
                    v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } catch (Exception ignored) { }
        }
        if (phoneSound) {
            try {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(tone::release, 500);
            } catch (Exception ignored) { }
        }
    }

    private void updateBoostText() {
        int boost = boostSeek.getProgress();
        int target = 40 + targetSeek.getProgress();
        boostLabel.setText("Boost: +" + (fahrenheit ? Math.round(boost * 1.8f) + " °F" : boost + " °C"));
        boostDescription.setText("Boost aprox. " + temperatureString(Math.min(210, target + boost))
                + " · Superboost aprox. " + temperatureString(Math.min(210, target + 2 * boost))
                + " (límite " + temperatureString(210) + ")");
    }

    private String showNum(int v, String suffix) { return v < 0 ? "—" : v + suffix; }

    private void render(CraftyBle.State s) {
        CraftyBle.State previous = last;
        last = s;
        if (previous.connected && previous.statusRegister >= 0 && s.connected && s.statusRegister >= 0
                && !CraftyCodec.isSet(previous.statusRegister, 4)
                && CraftyCodec.isSet(s.statusRegister, 4)) {
            reachedTargetAlert();
        }

        boolean ready = s.connected;
        boolean settingsReady = ready && s.statusRegister >= 0;

        connectionLabel.setText(s.connecting ? "Conectando…" : ready ? "Conectado · " + s.deviceName : "Desconectado");
        paintStatusChip(ready, s.connecting);

        connectionCaption.setText(ready
                ? "Controla tu sesión en tiempo real y ajusta el dispositivo desde el teléfono."
                : "Conecta tu CRAFTY+ para empezar a controlar temperatura, boost y brillo.");

        disconnectButton.setEnabled(ready || s.connecting);
        refreshButton.setEnabled(ready);
        scanButton.setEnabled(!s.connecting);

        temperatureNow.setText(temperatureString(s.current));
        batteryLabel.setText("Batería: " + showNum(s.battery, " %")
                + (s.autoRemainingSeconds >= 0 ? "   ·   Apagado en " + s.autoRemainingSeconds + " s" : ""));

        if (s.projectRegister >= 0) {
            String mode = CraftyCodec.isSet(s.projectRegister, CraftyCodec.SUPERBOOST_ACTIVE) ? "Superboost activo" :
                    CraftyCodec.isSet(s.projectRegister, CraftyCodec.BOOST_ACTIVE) ? "Boost activo" :
                    CraftyCodec.isSet(s.projectRegister, CraftyCodec.HEATER_ACTIVE) ? "Calentando" : "En reposo";
            modeLabel.setText(mode);
        } else {
            modeLabel.setText(ready ? "Leyendo el estado…" : "Conecta el equipo para ver el estado");
        }

        heaterButton.setText(CraftyCodec.isSet(s.projectRegister, CraftyCodec.HEATER_ACTIVE)
                ? "Detener calefactor" : "Encender calefactor");
        heaterButton.setEnabled(ready && s.projectRegister >= 0);
        targetApply.setEnabled(ready && s.target >= 0);
        boostApply.setEnabled(ready && s.boost >= 0);
        brightnessApply.setEnabled(ready && s.brightness >= 0);
        offApply.setEnabled(ready && s.autoOffSeconds >= 0);
        factoryReset.setEnabled(settingsReady);
        findButton.setEnabled(settingsReady && s.firmware.matches("(?i)^V?0?[3-9].*"));

        if (!draggingTarget && s.target >= 40 && s.target <= 210) {
            targetSeek.setProgress(s.target - 40);
            targetLabel.setText("Objetivo: " + temperatureString(s.target));
        }
        if (!draggingBoost && s.boost >= 0 && s.boost <= 30) {
            boostSeek.setProgress(s.boost);
        }
        if (!draggingBrightness && s.brightness >= 0 && s.brightness <= 100) {
            brightnessSeek.setProgress(s.brightness);
            ledLabel.setText("Brillo LED: " + s.brightness + " %");
        }
        if (!draggingOff && s.autoOffSeconds >= 60) {
            int minutes = CraftyCodec.between(Math.round(s.autoOffSeconds / 60f), 1, 5);
            offSeek.setProgress(minutes - 1);
            autoOffLabel.setText("Apagado automático: " + minutes + " min");
        }
        updateBoostText();

        painting = true;
        vibrateSwitch.setEnabled(settingsReady);
        chargeLedSwitch.setEnabled(settingsReady);
        permanentSwitch.setEnabled(settingsReady);
        if (settingsReady) {
            vibrateSwitch.setChecked(!CraftyCodec.isSet(s.statusRegister, CraftyCodec.VIBRATION_DISABLED));
            chargeLedSwitch.setChecked(!CraftyCodec.isSet(s.statusRegister, CraftyCodec.CHARGE_LED_DISABLED));
            permanentSwitch.setChecked(CraftyCodec.isSet(s.statusRegister, CraftyCodec.PERMANENT_BLE));
        } else {
            vibrateSwitch.setChecked(false);
            chargeLedSwitch.setChecked(false);
            permanentSwitch.setChecked(false);
        }
        painting = false;

        infoLabel.setText("Firmware: " + s.firmware
                + "\nFirmware BLE: " + s.bleFirmware
                + "\nNúmero de serie: " + s.serial
                + "\nHoras de uso: " + showNum(s.hours, " h") + (s.minutes >= 0 ? " " + s.minutes + " min" : ""));
    }

    private void startScanWithPermissions() {
        String[] permissions = Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        boolean needs = false;
        for (String perm : permissions) {
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) needs = true;
        }
        if (needs) {
            requestPermissions(permissions, PERMISSION_REQUEST);
            return;
        }
        if (!controller.bluetoothReady()) {
            new AlertDialog.Builder(this).setTitle("Bluetooth desactivado")
                    .setMessage("Activa Bluetooth para detectar tu CRAFTY+. En Android 11 o anterior activa también Ubicación.")
                    .setPositiveButton("Abrir ajustes", (d, w) -> startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        controller.scan();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code != PERMISSION_REQUEST) return;
        boolean accepted = grants.length > 0;
        for (int result : grants) accepted &= result == PackageManager.PERMISSION_GRANTED;
        if (accepted) startScanWithPermissions();
        else Toast.makeText(this, "Se requieren permisos Bluetooth para conectar.", Toast.LENGTH_LONG).show();
    }

    @Override public void onState(CraftyBle.State s) { render(s); }

    @Override public void onMessage(String message) {
        noticeLabel.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public void onScanFinished(List<BluetoothDevice> devices, List<String> names) {
        if (devices.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("CRAFTY+ no encontrado")
                    .setMessage("Comprueba que el dispositivo esté encendido, cargado y no conectado a otra aplicación. En Android 11 o anterior, activa Ubicación y vuelve a intentar.")
                    .setPositiveButton("Reintentar", (d, w) -> startScanWithPermissions())
                    .setNegativeButton("Cerrar", null).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Elige tu CRAFTY+")
                .setItems(names.toArray(new String[0]), (dialog, which) -> controller.connect(devices.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override protected void onDestroy() {
        if (controller != null) controller.disconnect();
        super.onDestroy();
    }
}
