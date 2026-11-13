package cl.craftyplus.control;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Publicly documented community-level BLE interoperability information; not manufacturer SDK. */
final class CraftyCodec {
    static final String SUFFIX = "-4c45-4b43-4942-265a524f5453";
    static final UUID CONTROL = uuid(0x01);
    static final UUID INFO = uuid(0x02);
    static final UUID STATUS = uuid(0x03);

    static final UUID TEMP_NOW = uuid(0x11);
    static final UUID TEMP_SET = uuid(0x21);
    static final UUID BOOST = uuid(0x31);
    static final UUID BATTERY = uuid(0x41);
    static final UUID BRIGHTNESS = uuid(0x51);
    static final UUID AUTO_OFF = uuid(0x61);
    static final UUID AUTO_REMAINING = uuid(0x71);
    static final UUID HEAT_ON = uuid(0x81);
    static final UUID HEAT_OFF = uuid(0x91);

    static final UUID FIRMWARE = uuid(0x32);
    static final UUID SERIAL = uuid(0x52);
    static final UUID BLE_FIRMWARE = uuid(0x72);

    static final UUID HOURS = uuid(0x23);
    static final UUID MINUTES = uuid(0x1e3);
    static final UUID PROJECT = uuid(0x93);
    static final UUID STATUS2 = uuid(0x1c3);
    static final UUID SECURITY = uuid(0x1b3);
    static final UUID FACTORY_RESET = uuid(0x1d3);

    static final int VIBRATION_DISABLED = 0x0001;
    static final int CHARGE_LED_DISABLED = 0x0002;
    static final int FIND_DEVICE = 0x0008;
    static final int PERMANENT_BLE = 0x1000; // map from community Crafty protocol
    static final int HEATER_ACTIVE = 0x0010;
    static final int BOOST_ACTIVE = 0x0020;
    static final int SUPERBOOST_ACTIVE = 0x0040;

    static UUID uuid(int prefix) {
        return UUID.fromString(String.format(java.util.Locale.US, "%08x%s", prefix, SUFFIX));
    }

    static byte[] u16(int value) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array();
    }

    static int readU16(byte[] data) {
        if (data == null || data.length == 0) return -1;
        if (data.length == 1) return data[0] & 0xff;
        return (data[0] & 0xff) | ((data[1] & 0xff) << 8);
    }

    static int readTemp(byte[] data) {
        int raw = readU16(data);
        return raw < 0 ? -1 : Math.round(raw / 10f);
    }

    static int readTarget(byte[] data) {
        int temp = readTemp(data);
        return temp > 210 ? Math.round((temp - 32f) / 1.8f) : temp;
    }

    static int between(int value, int min, int max) { return Math.min(max, Math.max(min, value)); }
    static int withBit(int reg, int bit, boolean set) { return set ? (reg | bit) : (reg & ~bit); }
    static boolean isSet(int reg, int bit) { return (reg & bit) != 0; }

    static String readText(byte[] data) {
        if (data == null) return "";
        return new String(data, StandardCharsets.UTF_8).replace("\u0000", "").trim();
    }

    static String readBleVersion(byte[] data) {
        if (data == null || data.length < 3) return "—";
        return "V" + (data[0] & 0xff) + "." + (data[1] & 0xff) + "." + (data[2] & 0xff);
    }

    private CraftyCodec() {}
}
