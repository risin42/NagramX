package tw.nekomimi.nekogram.config;

import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import tw.nekomimi.nekogram.NekoConfig;

@SuppressWarnings({"unchecked", "unused"})
public class ConfigItem {
    public static final int configTypeBool = 0;
    public static final int configTypeInt = 1;
    public static final int configTypeString = 2;
    public static final int configTypeSetInt = 3;
    public static final int configTypeMapIntInt = 4;
    public static final int configTypeLong = 5;
    public static final int configTypeFloat = 6;
    public static final int configTypeBoolLinkInt = 7;

    public final String key;
    public final int type;
    public final Object defaultValue;

    public Object value;

    public ConfigItem(String key, int type, Object defaultValue) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getKey() {
        return key;
    }

    // Read config

    public boolean Bool() {
        return (boolean) value;
    }

    public int Int() {
        return (int) value;
    }

    public Long Long() {
        return (Long) value;
    }

    public Float Float() {
        return (Float) value;
    }

    public String String() {
        return value.toString();
    }

    public HashSet<Integer> SetInt() {
        return (HashSet<Integer>) value;
    }

    public HashMap<Integer, Integer> MapIntInt() {
        return (HashMap<Integer, Integer>) value;
    }

    public boolean SetIntContains(Integer v) {
        return ((HashSet<Integer>) value).contains(v);
    }


    public void changed(Object o) {
        value = o;
    }

    // Write config
    // Note: no type checking here

    public boolean toggleConfigBool() {
        value = !this.Bool();
        saveConfig();
        return this.Bool(); // return value after toggle
    }

    public void setConfigBool(boolean v) {
        value = v;
        saveConfig();
    }

    public void setConfigInt(int v) {
        value = v;
        saveConfig();
    }

    public void setConfigLong(Long v) {
        value = v;
        saveConfig();
    }

    public void setConfigFloat(Float v) {
        value = v;
        saveConfig();
    }

    public void setConfigString(String v) {
        value = Objects.requireNonNullElse(v, "");
        saveConfig();
    }

    public void setConfigSetInt(HashSet<Integer> v) {
        value = v;
        saveConfig();
    }

    public void setConfigMapInt(HashMap<Integer, Integer> v) {
        value = v;
        saveConfig();
    }

    // save one item
    public void saveConfig() {
        synchronized (NekoConfig.sync) {
            try {
                SharedPreferences.Editor editor = NekoConfig.getPreferences().edit();

                if (this.type == configTypeBool) {
                    editor.putBoolean(this.key, (boolean) this.value);
                }
                if (this.type == configTypeInt) {
                    editor.putInt(this.key, (int) this.value);
                }
                if (this.type == configTypeLong) {
                    editor.putLong(this.key, (Long) this.value);
                }
                if (this.type == configTypeFloat) {
                    editor.putFloat(this.key, (Float) this.value);
                }
                if (this.type == configTypeString) {
                    editor.putString(this.key, this.value.toString());
                }
                if (this.type == configTypeSetInt) {
                    HashSet<String> ss = new HashSet<>();
                    for (Integer n : (Set<Integer>) this.value) {
                        ss.add(Integer.toString(n));
                    }
                    editor.putStringSet(this.key, ss);
                }
                if (this.type == configTypeMapIntInt) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos);
                    oos.writeObject(this.value);
                    oos.close();
                    editor.putString(this.key, Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT));
                }

                editor.apply();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public Object checkConfigFromString(String value) {
        try {
            return switch (type) {
                case configTypeBool -> Boolean.parseBoolean(value);
                case configTypeInt -> Integer.parseInt(value);
                case configTypeString -> value;
                case configTypeLong -> Long.parseLong(value);
                case configTypeFloat -> Float.parseFloat(value);
                default -> null;
            };
        } catch (Exception ignored) {}
        return null;
    }
}
