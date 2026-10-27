package pub.godweer.kitty.module.settings.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.kitty.module.settings.BindMode;
import pub.godweer.kitty.module.settings.Setting;
import pub.godweer.kitty.module.settings.SettingRenderer;
import pub.godweer.kitty.module.settings.render.BindRenderer;
import pub.godweer.auxiliary.client.persiki.KeyUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Create by daun kvass
 */
public final class BindSetting extends Setting<List<Integer>> {
    public static final int UNBOUND = KeyUtil.UNBOUND;

    private record Meta(BindMode mode, boolean visible) {
        static final Meta DEFAULT = new Meta(BindMode.TOGGLE, true);

        Meta withMode(BindMode mode) {
            return new Meta(mode, this.visible);
        }

        Meta withVisible(boolean visible) {
            return new Meta(this.mode, visible);
        }
    }

    private List<Meta> meta = List.of();

    public BindSetting(String name) {
        this(name, List.of());
    }

    public BindSetting(String name, int defaultValue) {
        this(name, !isValidKey(defaultValue) ? List.of() : List.of(defaultValue));
    }

    public BindSetting(String name, List<Integer> defaultValue) {
        super(name, normalizeList(defaultValue));
    }

    public static boolean isValidKey(int bindCode) {
        return bindCode != UNBOUND && bindCode != 0 && bindCode != 0;
    }

    public static int key(int keyCode) {
        return keyCode;
    }

    public static int mouse(int button) {
        return KeyUtil.fromMouseButton(button);
    }

    public static boolean isMouse(int bindCode) {
        return KeyUtil.isMouseButton(bindCode);
    }

    public static boolean isKeyboard(int bindCode) {
        return isValidKey(bindCode) && !isMouse(bindCode);
    }

    public static int rawButton(int bindCode) {
        return KeyUtil.toMouseButton(bindCode);
    }

    public boolean isBound() {
        if (getValue() == null || getValue().isEmpty()) return false;
        for (int code : getValue()) {
            if (isValidKey(code)) return true;
        }
        return false;
    }

    public int size() {
        return getValue().size();
    }

    public boolean isEmpty() {
        return !isBound();
    }

    public boolean hasIndex(int index) {
        return index >= 0 && index < getValue().size();
    }

    public int get(int index) {
        return getValue().get(index);
    }

    public boolean contains(int bindCode) {
        return getValue().contains(bindCode);
    }

    public boolean matches(int keyCode) {
        return isValidKey(keyCode) && matchesCode(key(keyCode));
    }

    public boolean matchesMouse(int button) {
        return matchesCode(mouse(button));
    }

    public boolean matchesCode(int bindCode) {
        return isValidKey(bindCode) && getValue().contains(bindCode);
    }

    public void setSingle(int bindCode) {
        Meta preserved = getMeta(0);
        int normalized = normalizeCode(bindCode);
        setValue(normalized == UNBOUND ? List.of() : List.of(normalized));
        this.meta = getValue().isEmpty() ? List.of() : List.of(preserved);
    }

    public void clear() {
        setValue(List.of());
        this.meta = List.of();
    }

    public BindMode getMode(int index, BindMode fallback) {
        if (!hasIndex(index)) {
            return fallback == null ? BindMode.TOGGLE : fallback;
        }
        return getMeta(index).mode();
    }

    public BindMode getModeForCode(int bindCode, BindMode fallback) {
        int index = getValue().indexOf(bindCode);
        return getMode(index, fallback);
    }

    public void setMode(int index, BindMode mode) {
        if (!hasIndex(index) || mode == null) {
            return;
        }
        updateMeta(index, getMeta(index).withMode(mode));
    }

    public void setAllModes(BindMode mode) {
        if (mode == null || getValue().isEmpty()) {
            this.meta = List.of();
            return;
        }

        List<Meta> bindMeta = new ArrayList<>();
        for (Meta entry : alignedMeta()) {
            bindMeta.add(entry.withMode(mode));
        }
        this.meta = List.copyOf(bindMeta);
    }

    public boolean isVisibleAt(int index) {
        return !hasIndex(index) || getMeta(index).visible();
    }

    public void setVisibleAt(int index, boolean visible) {
        if (!hasIndex(index)) {
            return;
        }
        updateMeta(index, getMeta(index).withVisible(visible));
    }

    public JsonElement writeModes() {
        JsonArray array = new JsonArray();
        for (Meta entry : alignedMeta()) {
            array.add(entry.mode().name());
        }
        return array;
    }

    public void readModes(JsonElement element) {
        List<BindMode> bindModes = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                BindMode mode = readMode(item);
                if (mode != null) {
                    bindModes.add(mode);
                }
            }
        } else if (element != null && !element.isJsonNull()) {
            BindMode mode = readMode(element);
            if (mode != null) {
                bindModes.add(mode);
            }
        }

        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        for (int i = 0; i < bindMeta.size() && i < bindModes.size(); i++) {
            bindMeta.set(i, bindMeta.get(i).withMode(bindModes.get(i)));
        }
        this.meta = List.copyOf(bindMeta);
    }

    public JsonElement writeVisibility() {
        JsonArray array = new JsonArray();
        for (Meta entry : alignedMeta()) {
            array.add(entry.visible());
        }
        return array;
    }

    public void readVisibility(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return;
        }

        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < bindMeta.size() && i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item != null && item.isJsonPrimitive()) {
                bindMeta.set(i, bindMeta.get(i).withVisible(item.getAsBoolean()));
            }
        }
        this.meta = List.copyOf(bindMeta);
    }

    public String getDisplayValue() {
        return isBound() ? getDisplayValue(0) : "NONE";
    }

    public String getDisplayValue(int index) {
        if (!hasIndex(index)) {
            return "NONE";
        }
        return KeyUtil.getKeyName(get(index));
    }

    private BindMode readMode(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }

        try {
            return BindMode.valueOf(element.getAsString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Meta getMeta(int index) {
        return index >= 0 && index < this.meta.size() ? this.meta.get(index) : Meta.DEFAULT;
    }

    private void updateMeta(int index, Meta entry) {
        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        bindMeta.set(index, entry);
        this.meta = List.copyOf(bindMeta);
    }

    private List<Meta> alignedMeta() {
        return fitMeta(this.meta, getValue().size());
    }

    private List<Meta> fitMeta(List<Meta> source, int size) {
        if (size <= 0) {
            return List.of();
        }

        List<Meta> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Meta entry = source != null && i < source.size() ? source.get(i) : null;
            result.add(entry == null ? Meta.DEFAULT : entry);
        }
        return List.copyOf(result);
    }

    private static List<Integer> normalizeList(List<Integer> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<Integer> result = new ArrayList<>();
        for (Integer code : list) {
            if (code != null && isValidKey(code)) {
                result.add(code);
            }
        }
        return List.copyOf(result);
    }

    private int normalizeCode(Integer value) {
        if (value == null || !isValidKey(value)) {
            return UNBOUND;
        }
        return value;
    }

    @Override
    public SettingRenderer<?> createRenderer() {
        return new BindRenderer(this);
    }
}