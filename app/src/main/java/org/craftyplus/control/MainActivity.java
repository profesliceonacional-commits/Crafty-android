package org.craftyplus.control;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.InputType;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.util.*;


@SuppressLint("MissingPermission")
public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, ScanResult> devices = new LinkedHashMap<>();
    private final Map<Integer, byte[]> values = new HashMap<>();
    private final Deque<Runnable> operations = new ArrayDeque<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private boolean busy;
    private boolean scanning;
    private BluetoothGattCharacteristic pending;
    private interface ReadResult { void accept(byte[] value); }
    private ReadResult readResult;
    private TextView status, readings;
    private Button search, disconnect, refresh, temperature, boost, led, timer, vibration, locate;

    private final Runnable timeout = () -> {
        close();
        status.setText("Tiempo de espera agotado. Vuelve a conectar.");
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);
        scroll.addView(panel);
        setContentView(scroll);

        status = new TextView(this);
        status.setTextSize(20);
        status.setText("CRAFTY+ desconectado");
        panel.addView(status);
        search = button(panel, "Buscar CRAFTY+", this::search);
        disconnect = button(panel, "Desconectar", () -> {
            close();
            status.setText("CRAFTY+ desconectado");
        });
        readings = new TextView(this);
        readings.setTextSize(16);
        panel.addView(readings);
        refresh = button(panel, "Actualizar lecturas", this::refresh);
        temperature = button(panel, "Ajustar temperatura", () -> edit("Temperatura (°C)", 0x21, 40, 210, 10));
        boost = button(panel, "Ajustar Boost y Superboost", () -> edit("Incremento común (°C)", 0x31, 0, 30, 10));
        led = button(panel, "Ajustar brillo LED", () -> edit("Brillo (%)", 0x51, 0, 100, 1));
        timer = button(panel, "Ajustar apagado automático", () -> edit("Apagado (segundos)", 0x61, 30, 300, 1));
        vibration = button(panel, "Cambiar vibración del CRAFTY+", () -> toggleRegister(1));
        locate = button(panel, "Activar o desactivar localización", () -> toggleRegister(8));
        render();
    }

    private Button button(LinearLayout panel, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        panel.addView(button);
        return button;
    }

    private void search() {
        String[] permissions = Build.VERSION.SDK_INT >= 31
            ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
            : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(permissions, 1);
                return;
            }
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            status.setText("Activa Bluetooth para buscar el CRAFTY+.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            status.setText("La búsqueda Bluetooth no está disponible.");
            return;
        }
        devices.clear();
        scanning = true;
        status.setText("Buscando dispositivos cercanos…");
        render();
        try {
            scanner.startScan(Collections.emptyList(),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback);
            main.postDelayed(finishScan, 6000);
        } catch (IllegalStateException | SecurityException failure) {
            stopScan();
            status.setText("No se pudo iniciar la búsqueda Bluetooth.");
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 1 || results.length == 0) return;
        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                status.setText("Se requiere permiso para buscar dispositivos Bluetooth.");
                return;
            }
        }
        search();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            main.post(() -> {
                if (!scanning || result.getScanRecord() == null) return;
                ScanRecord record = result.getScanRecord();
                String name = record.getDeviceName();
                String normalized = name == null ? "" : name.toUpperCase(Locale.ROOT);
                boolean branded = normalized.contains("CRAFTY") || normalized.startsWith("S&B")
                    || normalized.startsWith("STORZ&BICKEL");
                List<ParcelUuid> services = record.getServiceUuids();
                boolean crafty = services != null && (services.contains(new ParcelUuid(uuid(1)))
                    || services.contains(new ParcelUuid(uuid(2))) || services.contains(new ParcelUuid(uuid(3))));
                if (branded || crafty) devices.put(result.getDevice().getAddress(), result);
            });
        }

        @Override public void onScanFailed(int error) {
            main.post(() -> {
                if (!scanning) return;
                stopScan();
                status.setText("Falló la búsqueda Bluetooth (" + error + ").");
            });
        }
    };

    private final Runnable finishScan = () -> {
        stopScan();
        if (devices.isEmpty()) {
            status.setText("No se encontró el CRAFTY+. Enciéndelo y vuelve a buscar.");
            return;
        }
        status.setText("Selecciona tu CRAFTY+.");
        List<ScanResult> candidates = new ArrayList<>(devices.values());
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < labels.length; i++) {
            ScanResult result = candidates.get(i);
            String name = result.getScanRecord().getDeviceName();
            labels[i] = (name == null ? "Dispositivo Bluetooth" : name) + " · " + result.getDevice().getAddress();
        }
        new AlertDialog.Builder(this).setTitle("Dispositivos cercanos")
            .setItems(labels, (dialog, index) -> connect(candidates.get(index).getDevice()))
            .setNegativeButton("Cancelar", (dialog, which) -> status.setText("CRAFTY+ desconectado"))
            .setOnCancelListener(dialog -> status.setText("CRAFTY+ desconectado")).show();
    };

    private void stopScan() {
        main.removeCallbacks(finishScan);
        if (scanning && scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (IllegalStateException | SecurityException ignored) {
                // Bluetooth may have been turned off while scanning.
            }
        }
        scanning = false;
        render();
    }

    private void connect(BluetoothDevice device) {
        close();
        status.setText("Conectando…");
        try {
            gatt = device.connectGatt(this, false, callbacks, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException failure) {
            status.setText("Permiso Bluetooth no disponible.");
        }
        if (gatt != null) main.postDelayed(timeout, 12000);
        else status.setText("No se pudo iniciar la conexión Bluetooth.");
        render();
    }

    private final BluetoothGattCallback callbacks = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt connection, int code, int state) {
            main.post(() -> {
                if (gatt != connection) return;
                if (code == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED
                    && connection.discoverServices()) return;
                close();
                status.setText("CRAFTY+ desconectado");
            });
        }

        @Override public void onServicesDiscovered(BluetoothGatt connection, int code) {
            main.post(() -> {
                if (gatt != connection) return;
                main.removeCallbacks(timeout);
                if (code != BluetoothGatt.GATT_SUCCESS || characteristic(0x11) == null) {
                    close();
                    status.setText("El dispositivo no expone el servicio CRAFTY.");
                    return;
                }
                status.setText("CRAFTY+ conectado");
                refresh();
            });
        }

        @Override public void onCharacteristicRead(BluetoothGatt connection,
                BluetoothGattCharacteristic characteristic, byte[] value, int code) {
            main.post(() -> complete(connection, characteristic, code, value));
        }

        @Override public void onCharacteristicRead(BluetoothGatt connection,
                BluetoothGattCharacteristic characteristic, int code) {
            byte[] value = characteristic.getValue();
            byte[] snapshot = value == null ? null : value.clone();
            main.post(() -> complete(connection, characteristic, code, snapshot));
        }

        @Override public void onCharacteristicWrite(BluetoothGatt connection,
                BluetoothGattCharacteristic characteristic, int code) {
            main.post(() -> complete(connection, characteristic, code, null));
        }
    };

    private static UUID uuid(int id) {
        return UUID.fromString(String.format(Locale.ROOT, "%08x-4c45-4b43-4942-265a524f5453", id));
    }

    private BluetoothGattCharacteristic characteristic(int id) {
        if (gatt == null) return null;
        BluetoothGattService service = gatt.getService(uuid(id & 0xf));
        return service == null ? null : service.getCharacteristic(uuid(id));
    }

    private boolean supports(int id, int property) {
        BluetoothGattCharacteristic item = characteristic(id);
        return item != null && (item.getProperties() & property) != 0;
    }

    private boolean writable(int id) {
        return supports(id, BluetoothGattCharacteristic.PROPERTY_WRITE);
    }

    private void queueReads() {
        for (int id : new int[]{0x11, 0x21, 0x31, 0x41, 0x51, 0x61, 0x71, 0x23, 0x1e3, 0x32, 0x1c3}) {
            if (supports(id, BluetoothGattCharacteristic.PROPERTY_READ)) {
                operations.add(() -> read(id, value -> {
                    if (value == null || value.length == 0) values.remove(id);
                    else values.put(id, value);
                }));
            }
        }
    }

    private void refresh() {
        queueReads();
        advance();
    }

    private void advance() {
        busy = !operations.isEmpty();
        render();
        if (busy) operations.removeFirst().run();
    }

    private void read(int id, ReadResult result) {
        pending = characteristic(id);
        readResult = result;
        if (pending != null && gatt.readCharacteristic(pending)) main.postDelayed(timeout, 7000);
        else complete(gatt, pending, -1, null);
    }

    private void write(int id, int number) {
        pending = characteristic(id);
        readResult = null;
        if (pending == null) {
            complete(gatt, null, -1, null);
            return;
        }
        byte[] payload = {(byte) number, (byte) (number >>> 8)};
        boolean started;
        if (Build.VERSION.SDK_INT >= 33) {
            started = gatt.writeCharacteristic(pending, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                == BluetoothStatusCodes.SUCCESS;
        } else {
            pending.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            started = pending.setValue(payload) && gatt.writeCharacteristic(pending);
        }
        if (started) main.postDelayed(timeout, 7000);
        else complete(gatt, pending, -1, null);
    }

    private void complete(BluetoothGatt connection, BluetoothGattCharacteristic item, int code, byte[] value) {
        if (gatt != connection || pending != item || !busy) return;
        main.removeCallbacks(timeout);
        ReadResult result = readResult;
        pending = null;
        readResult = null;
        if (result != null) {
            result.accept(code == BluetoothGatt.GATT_SUCCESS ? value : null);
        } else if (code != BluetoothGatt.GATT_SUCCESS) {
            operations.clear();
            status.setText("El CRAFTY+ no confirmó el ajuste.");
            queueReads();
        }
        advance();
    }

    private static int number(byte[] value) {
        if (value == null || value.length == 0) return -1;
        int low = value[0] & 0xff;
        return value.length == 1 ? low : low | ((value[1] & 0xff) << 8);
    }

    private int number(int id) {
        return number(values.get(id));
    }

    private void edit(String title, int id, int min, int max, int scale) {
        EditText entry = new EditText(this);
        entry.setInputType(InputType.TYPE_CLASS_NUMBER);
        entry.setHint(min + "–" + max);
        int previous = number(id);
        if (id == 0x21 && previous > 2100) previous = Math.round((previous - 320) / 1.8f);
        if (previous >= 0) entry.setText(Integer.toString(Math.round(previous / (float) scale)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(entry)
            .setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            int input;
            try {
                input = Integer.parseInt(entry.getText().toString().trim());
            } catch (NumberFormatException invalid) {
                input = -1;
            }
            if (input < min || input > max) {
                entry.setError("Ingresa un valor entre " + min + " y " + max);
                return;
            }
            dialog.dismiss();
            if (gatt == null || busy) return;
            int encoded = input * scale;
            status.setText("CRAFTY+ conectado");
            if (id == 0x61) operations.add(() -> write(0x1b3, 815));
            operations.add(() -> write(id, encoded));
            queueReads();
            advance();
        }));
        dialog.show();
    }

    private void toggleRegister(int mask) {
        status.setText("CRAFTY+ conectado");
        operations.add(() -> read(0x1c3, value -> {
            int register = number(value);
            if (register < 0) {
                status.setText("No se pudo leer el ajuste actual.");
                return;
            }
            operations.addFirst(() -> write(0x1c3, register ^ mask));
        }));
        queueReads();
        advance();
    }

    private String display(int id, String unit) {
        int value = number(id);
        return value < 0 ? "—" : value + unit;
    }

    private String degrees(int id) {
        int value = number(id);
        if (value < 0) return "—";
        float degrees = value / 10f;
        if (id == 0x21 && degrees > 210) degrees = (degrees - 32) / 1.8f;
        return String.format(Locale.forLanguageTag("es-CL"), "%.1f °C", degrees);
    }

    private void render() {
        boolean connected = characteristic(0x11) != null;
        boolean ready = connected && !busy;
        search.setEnabled(gatt == null && !scanning);
        disconnect.setEnabled(gatt != null || scanning);
        refresh.setEnabled(ready);
        temperature.setEnabled(ready && writable(0x21));
        boost.setEnabled(ready && writable(0x31));
        led.setEnabled(ready && writable(0x51));
        timer.setEnabled(ready && writable(0x61) && writable(0x1b3));
        int register = number(0x1c3);
        boolean canEditRegister = ready && register >= 0 && writable(0x1c3);
        vibration.setEnabled(canEditRegister);
        locate.setEnabled(canEditRegister);
        if (!connected) {
            readings.setText("");
            return;
        }
        byte[] firmware = values.get(0x32);
        String version = firmware == null ? "—" : new String(firmware, StandardCharsets.US_ASCII).trim();
        readings.setText("Temperatura actual: " + degrees(0x11) + "\nObjetivo: " + degrees(0x21)
            + "\nIncremento Boost / Superboost: " + degrees(0x31) + "\nBatería: " + display(0x41, " %")
            + "\nBrillo LED: " + display(0x51, " %") + "\nApagado automático: " + display(0x61, " s")
            + "\nTiempo restante: " + display(0x71, " s")
            + "\nVibración: " + (register < 0 ? "—" : (register & 1) == 0 ? "activada" : "desactivada")
            + "\nLocalización: " + (register < 0 ? "—" : (register & 8) != 0 ? "activada" : "desactivada")
            + "\nUso: " + display(0x23, " h") + " / " + display(0x1e3, " min")
            + "\nFirmware: " + version);
    }

    private void close() {
        main.removeCallbacks(timeout);
        operations.clear();
        values.clear();
        busy = false;
        pending = null;
        readResult = null;
        BluetoothGatt connection = gatt;
        gatt = null;
        if (connection != null) connection.close();
        stopScan();
    }

    @Override protected void onDestroy() {
        close();
        super.onDestroy();
    }
}
