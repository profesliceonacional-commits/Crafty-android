package cl.craftyplus.control;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Native BLE/GATT controller. No Chrome, internet, device data collection or firmware flash. */
final class CraftyBle {
    interface Listener {
        void onState(State state);
        void onMessage(String message);
        void onScanFinished(List<BluetoothDevice> devices, List<String> names);
    }

    static final class State {
        boolean connected;
        boolean connecting;
        String deviceName = "CRAFTY+";
        String firmware = "—";
        String bleFirmware = "—";
        String serial = "—";
        int current = -1;
        int target = -1;
        int boost = -1;
        int battery = -1;
        int brightness = -1;
        int autoOffSeconds = -1;
        int autoRemainingSeconds = -1;
        int hours = -1;
        int minutes = -1;
        int projectRegister = -1;
        int statusRegister = -1;

        State copy() {
            State c = new State();
            c.connected = connected;
            c.connecting = connecting;
            c.deviceName = deviceName;
            c.firmware = firmware;
            c.bleFirmware = bleFirmware;
            c.serial = serial;
            c.current = current;
            c.target = target;
            c.boost = boost;
            c.battery = battery;
            c.brightness = brightness;
            c.autoOffSeconds = autoOffSeconds;
            c.autoRemainingSeconds = autoRemainingSeconds;
            c.hours = hours;
            c.minutes = minutes;
            c.projectRegister = projectRegister;
            c.statusRegister = statusRegister;
            return c;
        }
    }

    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int READ = 1, WRITE = 2, NOTIFY = 3, STATUS_BIT = 4;
    private static final long OP_TIMEOUT_MS = 6500L;
    private final Activity activity;
    private final Listener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final State state = new State();
    private final ArrayDeque<Operation> operations = new ArrayDeque<>();
    private final LinkedHashMap<String, BluetoothDevice> scanResults = new LinkedHashMap<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private Operation current;
    private boolean scanning;
    private Runnable operationTimeout;

    private static final class Operation {
        final int type;
        final UUID uuid;
        final byte[] data;
        final int bit;
        final boolean setBit;
        byte[] written;
        Operation(int type, UUID uuid, byte[] data, int bit, boolean setBit) {
            this.type = type; this.uuid = uuid; this.data = data; this.bit = bit; this.setBit = setBit;
        }
        static Operation read(UUID uuid) { return new Operation(READ, uuid, null, 0, false); }
        static Operation notifyOp(UUID uuid) { return new Operation(NOTIFY, uuid, null, 0, false); }
        static Operation write(UUID uuid, byte[] bytes) { return new Operation(WRITE, uuid, bytes, 0, false); }
        static Operation bit(int bit, boolean set) { return new Operation(STATUS_BIT, CraftyCodec.STATUS2, null, bit, set); }
    }

    CraftyBle(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    private void message(String s) { listener.onMessage(s); }
    private void update() { listener.onState(state.copy()); }

    private BluetoothAdapter adapter() {
        BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    boolean bluetoothReady() {
        BluetoothAdapter a = adapter();
        return a != null && a.isEnabled();
    }

    @SuppressLint("MissingPermission")
    void scan() {
        if (scanning) return;
        if (!bluetoothReady()) { message("Activa Bluetooth antes de buscar."); return; }
        BluetoothAdapter a = adapter();
        scanner = a == null ? null : a.getBluetoothLeScanner();
        if (scanner == null) { message("El teléfono no puede iniciar la búsqueda BLE."); return; }
        scanResults.clear();
        scanning = true;
        message("Buscando tu CRAFTY+… mantenlo encendido y cerca del teléfono.");
        try {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(null, settings, callbackScan);
            ui.postDelayed(this::stopScanAndShow, 9000);
        } catch (SecurityException e) {
            scanning = false;
            message("Falta permiso Bluetooth para buscar dispositivos.");
        } catch (Exception e) {
            scanning = false;
            message("No se pudo buscar: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScanAndShow() {
        if (!scanning) return;
        scanning = false;
        if (scanner != null) {
            try { scanner.stopScan(callbackScan); } catch (Exception ignored) { }
        }
        List<BluetoothDevice> devices = new ArrayList<>(scanResults.values());
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, BluetoothDevice> entry : scanResults.entrySet()) {
            BluetoothDevice d = entry.getValue();
            String name = d.getName();
            if (name == null || name.isEmpty()) name = "CRAFTY (sin nombre)";
            names.add(name + " · " + d.getAddress());
        }
        listener.onScanFinished(devices, names);
    }

    private final ScanCallback callbackScan = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            ui.post(() -> collectResult(result));
        }
        @Override public void onScanFailed(int errorCode) {
            ui.post(() -> {
                if (scanning) {
                    message("Error de búsqueda BLE (" + errorCode + ").");
                    stopScanAndShow();
                }
            });
        }
    };

    @SuppressLint("MissingPermission")
    private void collectResult(ScanResult result) {
        if (!scanning || result == null || result.getDevice() == null) return;
        BluetoothDevice d = result.getDevice();
        String name = d.getName();
        ScanRecord record = result.getScanRecord();
        if (record != null && (name == null || name.isEmpty())) name = record.getDeviceName();
        boolean isCrafty = false;
        if (name != null) {
            String n = name.toLowerCase(java.util.Locale.ROOT);
            isCrafty = n.contains("storz&bickel") || n.contains("storz & bickel") || n.contains("crafty");
            if (n.contains("volcano") || n.contains("s&b vy") || n.contains("s&b vz") || n.contains("venty") || n.contains("veazy")) {
                isCrafty = false;
            }
        }
        if (record != null && record.getServiceUuids() != null) {
            for (ParcelUuid uuid : record.getServiceUuids()) {
                if (uuid.getUuid().equals(CraftyCodec.CONTROL) || uuid.getUuid().equals(CraftyCodec.INFO)
                        || uuid.getUuid().equals(CraftyCodec.STATUS)) isCrafty = true;
            }
        }
        if (isCrafty) scanResults.put(d.getAddress(), d);
    }

    @SuppressLint("MissingPermission")
    void connect(BluetoothDevice device) {
        if (device == null) return;
        stopScanSilently();
        disconnect();
        state.connecting = true;
        state.deviceName = device.getName() != null ? device.getName() : "CRAFTY+";
        update();
        try {
            gatt = device.connectGatt(activity, false, callbackGatt, BluetoothDevice.TRANSPORT_LE);
            message("Conectando con " + state.deviceName + "…");
        } catch (SecurityException e) {
            state.connecting = false;
            update();
            message("Falta permiso para conectar por Bluetooth.");
        } catch (Exception e) {
            state.connecting = false;
            update();
            message("Error de conexión: " + e.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScanSilently() {
        if (!scanning) return;
        scanning = false;
        if (scanner != null) {
            try { scanner.stopScan(callbackScan); } catch (Exception ignored) { }
        }
    }

    @SuppressLint("MissingPermission")
    void disconnect() {
        stopScanSilently();
        operations.clear();
        current = null;
        if (operationTimeout != null) ui.removeCallbacks(operationTimeout);
        if (gatt != null) {
            try { gatt.disconnect(); } catch (Exception ignored) { }
            try { gatt.close(); } catch (Exception ignored) { }
            gatt = null;
        }
        resetState();
        update();
    }

    private void resetState() {
        state.connected = false;
        state.connecting = false;
        state.current = -1;
        state.target = -1;
        state.boost = -1;
        state.battery = -1;
        state.brightness = -1;
        state.autoOffSeconds = -1;
        state.autoRemainingSeconds = -1;
        state.hours = -1;
        state.minutes = -1;
        state.projectRegister = -1;
        state.statusRegister = -1;
        state.firmware = "—";
        state.bleFirmware = "—";
        state.serial = "—";
    }

    private final BluetoothGattCallback callbackGatt = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            ui.post(() -> {
                if (g != gatt) return;
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    message("Conexión BLE establecida. Leyendo parámetros…");
                    try {
                        if (!g.discoverServices()) message("No se pudo consultar servicios BLE.");
                    } catch (SecurityException e) { message("Permiso Bluetooth insuficiente."); }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    disconnect();
                    message("El CRAFTY+ se desconectó (estado " + status + ").");
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            ui.post(() -> {
                if (g != gatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS || g.getService(CraftyCodec.CONTROL) == null
                        || g.getService(CraftyCodec.INFO) == null || g.getService(CraftyCodec.STATUS) == null) {
                    message("Este equipo no expone los servicios BLE esperados del CRAFTY+. No se enviarán comandos.");
                    disconnect();
                    return;
                }
                state.connecting = false;
                state.connected = true;
                update();
                initialReads();
                message("CRAFTY+ conectado. Ajustes listos al terminar la lectura.");
            });
        }
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
            byte[] value = ch.getValue();
            handleRead(g, ch.getUuid(), value, status);
        }
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic ch, byte[] value, int status) {
            handleRead(g, ch.getUuid(), value, status);
        }
        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
            ui.post(() -> {
                if (g != gatt || current == null || !current.uuid.equals(ch.getUuid())) return;
                if (status == BluetoothGatt.GATT_SUCCESS && current.type == STATUS_BIT && current.written != null) {
                    state.statusRegister = CraftyCodec.readU16(current.written);
                    update();
                }
                if (status != BluetoothGatt.GATT_SUCCESS) message("El equipo rechazó un ajuste (" + status + ").");
                finishOperation();
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            ui.post(() -> {
                if (g != gatt || current == null || current.type != NOTIFY) return;
                finishOperation();
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            consumeNotification(g, ch.getUuid(), ch.getValue());
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch, byte[] value) {
            consumeNotification(g, ch.getUuid(), value);
        }
    };

    private void handleRead(BluetoothGatt g, UUID uuid, byte[] value, int status) {
        byte[] data = value == null ? null : value.clone();
        ui.post(() -> {
            if (g != gatt || current == null || current.type != READ || !current.uuid.equals(uuid)) return;
            if (status == BluetoothGatt.GATT_SUCCESS && data != null) consume(uuid, data);
            finishOperation();
        });
    }

    private void consumeNotification(BluetoothGatt g, UUID uuid, byte[] value) {
        byte[] bytes = value == null ? null : value.clone();
        ui.post(() -> { if (g == gatt && bytes != null) consume(uuid, bytes); });
    }

    private void consume(UUID uuid, byte[] v) {
        int n = CraftyCodec.readU16(v);
        if (uuid.equals(CraftyCodec.TEMP_NOW)) state.current = CraftyCodec.readTemp(v);
        else if (uuid.equals(CraftyCodec.TEMP_SET)) state.target = CraftyCodec.readTarget(v);
        else if (uuid.equals(CraftyCodec.BOOST)) state.boost = CraftyCodec.readTemp(v);
        else if (uuid.equals(CraftyCodec.BATTERY)) state.battery = n;
        else if (uuid.equals(CraftyCodec.BRIGHTNESS)) state.brightness = n;
        else if (uuid.equals(CraftyCodec.AUTO_OFF)) state.autoOffSeconds = n;
        else if (uuid.equals(CraftyCodec.AUTO_REMAINING)) state.autoRemainingSeconds = n;
        else if (uuid.equals(CraftyCodec.PROJECT)) state.projectRegister = n;
        else if (uuid.equals(CraftyCodec.STATUS2)) state.statusRegister = n;
        else if (uuid.equals(CraftyCodec.HOURS)) state.hours = n;
        else if (uuid.equals(CraftyCodec.MINUTES)) state.minutes = n;
        else if (uuid.equals(CraftyCodec.FIRMWARE)) state.firmware = CraftyCodec.readText(v);
        else if (uuid.equals(CraftyCodec.BLE_FIRMWARE)) state.bleFirmware = CraftyCodec.readBleVersion(v);
        else if (uuid.equals(CraftyCodec.SERIAL)) {
            String text = CraftyCodec.readText(v);
            state.serial = text.substring(0, Math.min(8, text.length()));
        }
        update();
    }

    private void initialReads() {
        for (UUID uuid : new UUID[]{CraftyCodec.FIRMWARE, CraftyCodec.SERIAL, CraftyCodec.BLE_FIRMWARE,
                CraftyCodec.TEMP_NOW, CraftyCodec.TEMP_SET, CraftyCodec.BOOST, CraftyCodec.BATTERY,
                CraftyCodec.BRIGHTNESS, CraftyCodec.AUTO_OFF, CraftyCodec.AUTO_REMAINING,
                CraftyCodec.PROJECT, CraftyCodec.STATUS2, CraftyCodec.HOURS, CraftyCodec.MINUTES}) {
            operations.add(Operation.read(uuid));
        }
        for (UUID uuid : new UUID[]{CraftyCodec.TEMP_NOW, CraftyCodec.BATTERY, CraftyCodec.AUTO_REMAINING,
                CraftyCodec.PROJECT, CraftyCodec.STATUS2}) {
            operations.add(Operation.notifyOp(uuid));
        }
        pump();
    }

    void refresh() {
        if (!state.connected) return;
        for (UUID uuid : new UUID[]{CraftyCodec.TEMP_NOW, CraftyCodec.TEMP_SET, CraftyCodec.BOOST,
                CraftyCodec.BATTERY, CraftyCodec.BRIGHTNESS, CraftyCodec.AUTO_OFF,
                CraftyCodec.AUTO_REMAINING, CraftyCodec.PROJECT, CraftyCodec.STATUS2,
                CraftyCodec.HOURS, CraftyCodec.MINUTES}) operations.add(Operation.read(uuid));
        pump();
    }

    void setTarget(int c) { writeAndRead(CraftyCodec.TEMP_SET, CraftyCodec.u16(CraftyCodec.between(c, 40, 210) * 10)); }
    void setBoost(int offset) { writeAndRead(CraftyCodec.BOOST, CraftyCodec.u16(CraftyCodec.between(offset, 0, 30) * 10)); }
    void setBrightness(int pct) { writeAndRead(CraftyCodec.BRIGHTNESS, CraftyCodec.u16(CraftyCodec.between(pct, 0, 100))); }

    void setAutoOff(int seconds) {
        if (!state.connected) return;
        operations.add(Operation.write(CraftyCodec.SECURITY, CraftyCodec.u16(815)));
        operations.add(Operation.write(CraftyCodec.AUTO_OFF, CraftyCodec.u16(CraftyCodec.between(seconds, 30, 300))));
        operations.add(Operation.read(CraftyCodec.AUTO_OFF));
        pump();
    }

    void setStatusBit(int bit, boolean enabled) {
        if (!state.connected) return;
        operations.add(Operation.bit(bit, enabled));
        operations.add(Operation.read(CraftyCodec.STATUS2));
        pump();
    }

    void heater(boolean on) { writeAndRead(on ? CraftyCodec.HEAT_ON : CraftyCodec.HEAT_OFF,
            CraftyCodec.u16(0), CraftyCodec.PROJECT); }

    void findDevice() { setStatusBit(CraftyCodec.FIND_DEVICE, true); }

    void factoryReset() {
        if (!state.connected) return;
        operations.add(Operation.write(CraftyCodec.SECURITY, CraftyCodec.u16(1000)));
        operations.add(Operation.write(CraftyCodec.FACTORY_RESET, new byte[]{0}));
        for (UUID uuid : new UUID[]{CraftyCodec.TEMP_SET, CraftyCodec.BOOST, CraftyCodec.BRIGHTNESS,
                CraftyCodec.AUTO_OFF, CraftyCodec.STATUS2}) operations.add(Operation.read(uuid));
        pump();
    }

    private void writeAndRead(UUID uuid, byte[] data) { writeAndRead(uuid, data, uuid); }
    private void writeAndRead(UUID uuid, byte[] data, UUID readBack) {
        if (!state.connected) return;
        operations.add(Operation.write(uuid, data));
        operations.add(Operation.read(readBack));
        pump();
    }

    private BluetoothGattCharacteristic characteristic(UUID uuid) {
        if (gatt == null) return null;
        UUID service = CraftyCodec.CONTROL;
        if (uuid.equals(CraftyCodec.FIRMWARE) || uuid.equals(CraftyCodec.SERIAL)
                || uuid.equals(CraftyCodec.BLE_FIRMWARE)) service = CraftyCodec.INFO;
        else if (uuid.equals(CraftyCodec.HOURS) || uuid.equals(CraftyCodec.MINUTES)
                || uuid.equals(CraftyCodec.PROJECT) || uuid.equals(CraftyCodec.STATUS2)
                || uuid.equals(CraftyCodec.SECURITY) || uuid.equals(CraftyCodec.FACTORY_RESET)) {
            service = CraftyCodec.STATUS;
        }
        BluetoothGattService s = gatt.getService(service);
        return s == null ? null : s.getCharacteristic(uuid);
    }

    @SuppressLint("MissingPermission")
    private void pump() {
        if (current != null || gatt == null || !state.connected) return;
        while (!operations.isEmpty()) {
            Operation op = operations.removeFirst();
            BluetoothGattCharacteristic c = characteristic(op.uuid);
            if (c == null) continue; // firmware variants can omit optional characteristics
            current = op;
            boolean started = false;
            try {
                if (op.type == READ) {
                    started = gatt.readCharacteristic(c);
                } else if (op.type == WRITE || op.type == STATUS_BIT) {
                    if (op.type == STATUS_BIT) {
                        if (state.statusRegister < 0) {
                            current = null;
                            message("Espera a que se lean los ajustes del dispositivo.");
                            continue;
                        }
                        op.written = CraftyCodec.u16(CraftyCodec.withBit(state.statusRegister, op.bit, op.setBit));
                    } else op.written = op.data;
                    c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                    if (Build.VERSION.SDK_INT >= 33) {
                        started = gatt.writeCharacteristic(c, op.written,
                                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS;
                    } else {
                        c.setValue(op.written);
                        started = gatt.writeCharacteristic(c);
                    }
                } else if (op.type == NOTIFY) {
                    if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) {
                        current = null;
                        continue;
                    }
                    BluetoothGattDescriptor cccd = c.getDescriptor(CCCD);
                    if (cccd == null) { current = null; continue; }
                    if (!gatt.setCharacteristicNotification(c, true)) { current = null; continue; }
                    if (Build.VERSION.SDK_INT >= 33) {
                        started = gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                == BluetoothGatt.GATT_SUCCESS;
                    } else {
                        cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        started = gatt.writeDescriptor(cccd);
                    }
                }
            } catch (Exception e) {
                current = null;
                message("BLE: " + e.getMessage());
                continue;
            }
            if (!started) { current = null; continue; }
            final Operation waitingFor = op;
            operationTimeout = () -> {
                if (current == waitingFor) {
                    message("Un comando BLE agotó el tiempo de espera; continuando.");
                    finishOperation();
                }
            };
            ui.postDelayed(operationTimeout, OP_TIMEOUT_MS);
            return;
        }
    }

    private void finishOperation() {
        if (operationTimeout != null) ui.removeCallbacks(operationTimeout);
        operationTimeout = null;
        current = null;
        pump();
    }
}
