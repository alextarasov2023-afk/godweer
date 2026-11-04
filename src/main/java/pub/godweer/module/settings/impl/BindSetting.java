package pub.godweer.module.settings.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.module.settings.BindMode;
import pub.godweer.module.settings.Setting;
import pub.godweer.module.settings.SettingRenderer;
import pub.godweer.module.settings.render.BindRenderer;
import pub.godweer.util.game.KeyUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Create by daun kvass
 */
public final class BindSetting extends Setting<List<Integer>> {
    public static final int UNBOUND = KeyUtil.UNBOUND;

    /** Сколько клавиш можно навесить на один бинд: лишние вытесняют самые старые. */
    public static final int MAX_KEYS = 3;

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
    /**
     * Общий бинд: на одной клавише может висеть несколько таких, и Binds.detach()
     * у них клавишу не отбирает. Так живут бинды предметов ServerHelper — выбор
     * между ними делает колесо на зажатой клавише.
     */
    private boolean exclusive = true;

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
        return bindCode != UNBOUND && bindCode > 0;
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

    /** Бинд перестаёт участвовать в захвате клавиши (см. Binds). */
    public BindSetting allowShared() {
        this.exclusive = false;
        return this;
    }

    public boolean isExclusive() {
        return exclusive;
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

    /**
     * Навешивает ещё одну клавишу, не затирая остальные: уже связанный код
     * переставляется в конец и сохраняет свой режим, новый добавляется, а самый
     * старый вытесняется при переполнении.
     */
    public boolean addCode(int bindCode) {
        int normalized = normalizeCode(bindCode);
        if (normalized == UNBOUND) {
            return false;
        }

        List<Integer> codes = new ArrayList<>(getValue());
        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        int existing = codes.indexOf(normalized);
        if (existing != -1) {
            Meta preserved = bindMeta.get(existing);
            codes.remove(existing);
            bindMeta.remove(existing);
            codes.add(normalized);
            bindMeta.add(preserved);
        } else {
            if (codes.size() >= MAX_KEYS) {
                codes.remove(0);
                bindMeta.remove(0);
            }
            codes.add(normalized);
            bindMeta.add(Meta.DEFAULT);
        }

        apply(codes, bindMeta);
        return true;
    }

    public void removeAt(int index) {
        List<Integer> codes = new ArrayList<>(getValue());
        if (index < 0 || index >= codes.size()) {
            return;
        }
        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        codes.remove(index);
        if (index < bindMeta.size()) {
            bindMeta.remove(index);
        }
        apply(codes, bindMeta);
    }

    /** Переключает TOGGLE/HOLD у последней навешенной клавиши. */
    public BindMode toggleLastMode() {
        int index = size() - 1;
        if (index < 0) {
            return BindMode.TOGGLE;
        }
        BindMode next = getMode(index, BindMode.TOGGLE) == BindMode.HOLD ? BindMode.TOGGLE : BindMode.HOLD;
        setMode(index, next);
        return next;
    }

    public List<String> modeNames() {
        List<String> names = new ArrayList<>();
        for (Meta entry : alignedMeta()) {
            names.add(entry.mode().name());
        }
        return names;
    }

    public void setModeNames(List<String> names) {
        List<Meta> bindMeta = new ArrayList<>(alignedMeta());
        if (names != null) {
            for (int i = 0; i < bindMeta.size() && i < names.size(); i++) {
                BindMode mode = readMode(names.get(i));
                if (mode != null) {
                    bindMeta.set(i, bindMeta.get(i).withMode(mode));
                }
            }
        }
        this.meta = List.copyOf(bindMeta);
    }

    private void apply(List<Integer> codes, List<Meta> bindMeta) {
        setValue(List.copyOf(codes));
        this.meta = bindMeta.isEmpty() ? List.of() : List.copyOf(bindMeta);
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

    /** Все навешенные клавиши в одну строку; HOLD помечается префиксом H:. */
    public String getDisplayValue() {
        if (!isBound()) {
            return "NONE";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < size(); i++) {
            if (i > 0) {
                text.append(" / ");
            }
            if (getMode(i, BindMode.TOGGLE) == BindMode.HOLD) {
                text.append("H:");
            }
            text.append(KeyUtil.getKeyName(get(i)));
        }
        return text.toString();
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
        return readMode(element.getAsString());
    }

    private BindMode readMode(String name) {
        if (name == null) {
            return null;
        }

        try {
            return BindMode.valueOf(name);
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