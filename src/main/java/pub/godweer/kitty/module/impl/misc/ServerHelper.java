package pub.godweer.kitty.module.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.KeyboardInputEvent;
import pub.godweer.enjoy.event.list.MouseInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.display.mainmenu.PanelRefractions;
import pub.godweer.kitty.display.mainmenu.popup.ServerHelperModal;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.impl.combat.AutoTotem;
import pub.godweer.kitty.module.settings.impl.BindSetting;
import pub.godweer.kitty.module.settings.impl.ButtonSetting;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.auxiliary.player.InventoryUtil;
import pub.godweer.auxiliary.player.MoveBlockUtility;
import pub.godweer.hud.Notifications;

import java.util.*;

/**
 * Create by daun kvass
 */
public final class ServerHelper extends Module {

    private static final int HOTBAR_SIZE = 9;

    public final ModeSetting server = mode("Server", "FunTime", "FunTime", "HolyWorld");
    public final ModeSetting useMode = mode("Use Mode", "Packet", "Packet", "Normal");
    public final CheckBox swapBack = checkbox("Swap Back", true);

    public final ButtonSetting openMenuBtn = button("Настроить бинды", () -> {
        PanelRefractions.openModal(new ServerHelperModal(this));
    });
    public final BindSetting ftDisorient = new BindSetting("Дезориентация");
    public final BindSetting ftDust      = new BindSetting("Явная пыль");
    public final BindSetting ftPlast     = new BindSetting("Пласт");
    public final BindSetting ftSnow      = new BindSetting("Снежок (ФТ)");
    public final BindSetting ftGodAura   = new BindSetting("Божья Аура");
    public final BindSetting ftTrap      = new BindSetting("Трапка (ФТ)");
    public final BindSetting ftFireSwirl = new BindSetting("Огненный смерч");
    public final BindSetting ftHolyWater = new BindSetting("Святая вода");
    public final BindSetting ftAnger     = new BindSetting("Зелье Гнева");
    public final BindSetting ftPaladin   = new BindSetting("Зелье Паладина");
    public final BindSetting ftAssassin  = new BindSetting("Зелье Ассасина");
    public final BindSetting ftRadiation = new BindSetting("Зелье Радиации");
    public final BindSetting ftSleep     = new BindSetting("Снотворное");
    public final BindSetting ftClapper   = new BindSetting("Хлопушка");
    public final BindSetting hwExplosiveTrap = new BindSetting("Взрывная трапка");
    public final BindSetting hwNormalTrap    = new BindSetting("Трапка (ХВ)");
    public final BindSetting hwStun          = new BindSetting("Стан");
    public final BindSetting hwSnow          = new BindSetting("Ком снега");
    public final BindSetting hwFire          = new BindSetting("Огненный шар");
    public final BindSetting hwPumpkin       = new BindSetting("Тыква Джека");

    private final Map<String, ItemInfo> itemConfigs = new HashMap<>();
    private final Map<String, BindSetting> bindByKey = new HashMap<>();

    private enum State {
        IDLE,
        STOP_SPRINT,
        SWAP_AND_USE,
        COOLDOWN
    }

    private State state = State.IDLE;
    private ItemInfo pendingInfo = null;
    private int cooldownTicks = 0;

    public ServerHelper() {
        super("ServerHelper", "Использование предметов по биндам", Category.COMBAT);
        setupConfigs();
        // Бинды предметов живут в модалке, а не в списке настроек, но сохраняться
        // в конфиг обязаны — поэтому регистрируем их скрытыми.
        for (BindSetting bind : List.of(
                ftDisorient, ftDust, ftPlast, ftSnow, ftGodAura, ftTrap, ftFireSwirl,
                ftHolyWater, ftAnger, ftPaladin, ftAssassin, ftRadiation, ftSleep, ftClapper,
                hwExplosiveTrap, hwNormalTrap, hwStun, hwSnow, hwFire, hwPumpkin)) {
            register(bind);
            bind.visible(() -> false);
        }
    }

    private static class EffectReq {
        final Holder<MobEffect> effect;
        final int minAmp;

        EffectReq(Holder<MobEffect> effect, int minAmp) {
            this.effect = effect;
            this.minAmp = minAmp;
        }
    }

    private static class ItemInfo {
        final Item item;
        final String displayName;
        final List<String> loreKeywords;
        final List<String> names;
        final List<EffectReq> effectReqs;

        ItemInfo(Item item, String displayName, List<String> loreKeywords, List<String> names, List<EffectReq> effectReqs) {
            this.item = item;
            this.displayName = displayName;
            this.loreKeywords = loreKeywords;
            this.names = names;
            this.effectReqs = effectReqs;
        }
    }

    private void setupConfigs() {
        itemConfigs.put("ft_disorient", new ItemInfo(Items.ENDER_EYE, "Дезориентация", List.of("чем ближе цель"), List.of("дезориентация"), null));
        itemConfigs.put("ft_dust", new ItemInfo(Items.SUGAR, "Явная пыль", List.of("световая вспышка", "свечение", "слепота"), List.of("явная пыль"), null));
        itemConfigs.put("ft_plast", new ItemInfo(Items.DRIED_KELP, "Пласт", List.of("нерушимая стена"), List.of("пласт"), null));
        itemConfigs.put("ft_snow", new ItemInfo(Items.SNOWBALL, "Снежок заморозки", List.of("ледяная сфера"), List.of("снежок заморозка", "снежок"), null));
        itemConfigs.put("ft_god_aura", new ItemInfo(Items.PHANTOM_MEMBRANE, "Божья Аура", List.of("божественная аура"), List.of("божья аура"), null));
        itemConfigs.put("ft_trap", new ItemInfo(Items.NETHERITE_SCRAP, "Трапка", List.of("нерушимая клетка"), List.of("трапка"), null));
        itemConfigs.put("ft_fire_swirl", new ItemInfo(Items.FIRE_CHARGE, "Огненный смерч", List.of("огненная волна"), List.of("огненный смерч"), null));
        itemConfigs.put("ft_holy_water", new ItemInfo(Items.SPLASH_POTION, "Святая вода", null, List.of("святая вода"), List.of(new EffectReq(MobEffects.REGENERATION, 1), new EffectReq(MobEffects.INVISIBILITY, 0))));
        itemConfigs.put("ft_anger", new ItemInfo(Items.SPLASH_POTION, "Зелье Гнева", null, List.of("зелье гнева"), List.of(new EffectReq(MobEffects.STRENGTH, 3), new EffectReq(MobEffects.SLOWNESS, 2))));
        itemConfigs.put("ft_paladin", new ItemInfo(Items.SPLASH_POTION, "Зелье Паладина", null, List.of("зелье паладина"), List.of(new EffectReq(MobEffects.RESISTANCE, 0), new EffectReq(MobEffects.FIRE_RESISTANCE, 0))));
        itemConfigs.put("ft_assassin", new ItemInfo(Items.SPLASH_POTION, "Зелье Ассасина", null, List.of("зелье ассасина"), List.of(new EffectReq(MobEffects.STRENGTH, 2), new EffectReq(MobEffects.SPEED, 1))));
        itemConfigs.put("ft_radiation", new ItemInfo(Items.SPLASH_POTION, "Зелье Радиации", null, List.of("зелье радиации"), List.of(new EffectReq(MobEffects.POISON, 0), new EffectReq(MobEffects.WITHER, 0))));
        itemConfigs.put("ft_sleep", new ItemInfo(Items.SPLASH_POTION, "Снотворное", null, List.of("снотворное"), List.of(new EffectReq(MobEffects.WEAKNESS, 0), new EffectReq(MobEffects.MINING_FATIGUE, 0))));
        itemConfigs.put("ft_clapper", new ItemInfo(Items.SPLASH_POTION, "Хлопушка", null, List.of("хлопушка"), List.of(new EffectReq(MobEffects.SLOWNESS, 8), new EffectReq(MobEffects.BLINDNESS, 8))));
        itemConfigs.put("hw_exp_trap", new ItemInfo(Items.PRISMARINE_SHARD, "Взрывная трапка", null, List.of("взрывная трапка"), null));
        itemConfigs.put("hw_norm_trap", new ItemInfo(Items.POPPED_CHORUS_FRUIT, "Трапка", null, List.of("трапка"), null));
        itemConfigs.put("hw_stun", new ItemInfo(Items.NETHER_STAR, "Стан", null, List.of("стан"), null));
        itemConfigs.put("hw_snow", new ItemInfo(Items.SNOWBALL, "Ком снега", null, List.of("ком снега", "снежок"), null));
        itemConfigs.put("hw_pumpkin", new ItemInfo(Items.JACK_O_LANTERN, "Тыква Джека", null, List.of("джек", "тыква"), null));
        itemConfigs.put("hw_fire", new ItemInfo(Items.FIRE_CHARGE, "Огненный шар", null, List.of("огненный шар"), null));

        bindByKey.put("ft_disorient", ftDisorient);
        bindByKey.put("ft_dust", ftDust);
        bindByKey.put("ft_plast", ftPlast);
        bindByKey.put("ft_snow", ftSnow);
        bindByKey.put("ft_god_aura", ftGodAura);
        bindByKey.put("ft_trap", ftTrap);
        bindByKey.put("ft_fire_swirl", ftFireSwirl);
        bindByKey.put("ft_holy_water", ftHolyWater);
        bindByKey.put("ft_anger", ftAnger);
        bindByKey.put("ft_paladin", ftPaladin);
        bindByKey.put("ft_assassin", ftAssassin);
        bindByKey.put("ft_radiation", ftRadiation);
        bindByKey.put("ft_sleep", ftSleep);
        bindByKey.put("ft_clapper", ftClapper);
        bindByKey.put("hw_exp_trap", hwExplosiveTrap);
        bindByKey.put("hw_norm_trap", hwNormalTrap);
        bindByKey.put("hw_stun", hwStun);
        bindByKey.put("hw_snow", hwSnow);
        bindByKey.put("hw_pumpkin", hwPumpkin);
        bindByKey.put("hw_fire", hwFire);
    }

    // Порядок пилюль в HUD: сначала то, что жмётся чаще всего. Зелья в HUD не
    // выводим — их семь штук, панель превратилась бы в простыню.
    private static final List<String> FT_HUD = List.of(
            "ft_god_aura", "ft_disorient", "ft_fire_swirl", "ft_trap", "ft_snow", "ft_dust", "ft_plast");
    private static final List<String> HW_HUD = List.of(
            "hw_snow", "hw_stun", "hw_pumpkin", "hw_exp_trap", "hw_norm_trap", "hw_fire");

    public record HudItem(String key, String title, ItemStack icon, BindSetting bind) {}

    private List<HudItem> hudCache = List.of();
    private String hudCacheServer = "";

    /** Предметы текущего сервера для HUD: иконка, название и ключ для подсчёта. */
    public List<HudItem> hudEntries() {
        String current = server.getValue();
        if (!current.equals(hudCacheServer)) {
            List<HudItem> list = new ArrayList<>();
            for (String key : server.is("FunTime") ? FT_HUD : HW_HUD) {
                ItemInfo info = itemConfigs.get(key);
                if (info != null) list.add(new HudItem(key, info.displayName, new ItemStack(info.item), bindByKey.get(key)));
            }
            hudCache = list;
            hudCacheServer = current;
        }
        return hudCache;
    }

    /** Сколько штук конкретного предмета лежит в инвентаре, хотбаре и второй руке. */
    public int countItem(String key) {
        ItemInfo info = itemConfigs.get(key);
        LocalPlayer player = player();
        if (info == null || player == null) return 0;
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (matchesInfo(stack, info)) total += stack.getCount();
        }
        ItemStack offhand = player.getOffhandItem();
        if (matchesInfo(offhand, info)) total += offhand.getCount();
        return total;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        pendingInfo = null;
        cooldownTicks = 0;
        MoveBlockUtility.unblock(this);
    }

    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (AutoTotem.isSwapping()) return;
        if (event.getAction() != InputConstants.PRESS || mc.gui.screen() != null || PanelRefractions.isOpen() || state != State.IDLE) return;
        handleInput(event.getKey(), false);
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (AutoTotem.isSwapping()) return;
        if (event.getAction() != InputConstants.PRESS || mc.gui.screen() != null || PanelRefractions.isOpen() || state != State.IDLE) return;
        if (handleInput(event.getButton(), true)) {
            event.cancel();
        }
    }

    private boolean handleInput(int keyOrButton, boolean isMouse) {
        if (AutoTotem.isSwapping()) return false;

        if (server.is("FunTime")) {
            if (matches(ftDisorient, keyOrButton, isMouse)) return queueItem("ft_disorient");
            if (matches(ftDust, keyOrButton, isMouse)) return queueItem("ft_dust");
            if (matches(ftPlast, keyOrButton, isMouse)) return queueItem("ft_plast");
            if (matches(ftSnow, keyOrButton, isMouse)) return queueItem("ft_snow");
            if (matches(ftGodAura, keyOrButton, isMouse)) return queueItem("ft_god_aura");
            if (matches(ftTrap, keyOrButton, isMouse)) return queueItem("ft_trap");
            if (matches(ftFireSwirl, keyOrButton, isMouse)) return queueItem("ft_fire_swirl");

            if (matches(ftHolyWater, keyOrButton, isMouse)) return queueItem("ft_holy_water");
            if (matches(ftAnger, keyOrButton, isMouse)) return queueItem("ft_anger");
            if (matches(ftPaladin, keyOrButton, isMouse)) return queueItem("ft_paladin");
            if (matches(ftAssassin, keyOrButton, isMouse)) return queueItem("ft_assassin");
            if (matches(ftRadiation, keyOrButton, isMouse)) return queueItem("ft_radiation");
            if (matches(ftSleep, keyOrButton, isMouse)) return queueItem("ft_sleep");
            if (matches(ftClapper, keyOrButton, isMouse)) return queueItem("ft_clapper");
        } else if (server.is("HolyWorld")) {
            if (matches(hwExplosiveTrap, keyOrButton, isMouse)) return queueItem("hw_exp_trap");
            if (matches(hwNormalTrap, keyOrButton, isMouse)) return queueItem("hw_norm_trap");
            if (matches(hwStun, keyOrButton, isMouse)) return queueItem("hw_stun");
            if (matches(hwSnow, keyOrButton, isMouse)) return queueItem("hw_snow");
            if (matches(hwFire, keyOrButton, isMouse)) return queueItem("hw_fire");
            if (matches(hwPumpkin, keyOrButton, isMouse)) return queueItem("hw_pumpkin");
        }
        return false;
    }

    private boolean matches(BindSetting setting, int keyOrBtn, boolean isMouse) {
        return isMouse ? setting.matchesMouse(keyOrBtn) : setting.matches(keyOrBtn);
    }

    private boolean queueItem(String key) {
        if (AutoTotem.isSwapping()) return false;

        ItemInfo info = itemConfigs.get(key);
        if (info == null || !inGame() || player() == null) return false;

        LocalPlayer player = player();

        int hotbarSlot = findInHotbar(player, info);
        if (hotbarSlot != -1) {
            useFromHotbar(player, hotbarSlot);
            announce(key, info);
            return true;
        }

        int invSlot = findInMainInventory(player, info);
        if (invSlot != -1) {
            if (useMode.is("Packet")) {
                performPacketSwapUse(player, invSlot);
            } else {
                pendingInfo = info;
                state = State.STOP_SPRINT;
            }
            announce(key, info);
            return true;
        }

        return false;
    }

    private void announce(String key, ItemInfo info) {
        Notifications.itemUse(info.displayName, Math.max(0, countItem(key) - 1));
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (!inGame() || player() == null || mc.gameMode == null) {
            reset();
            return;
        }

        if (AutoTotem.isSwapping()) {
            reset();
            return;
        }

        LocalPlayer player = player();

        switch (state) {
            case STOP_SPRINT -> {
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }

                if (player.onGround()) {
                    player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                }

                MoveBlockUtility.freeze(this, 4);
                MoveBlockUtility.blockSprint(this, 5);

                state = State.SWAP_AND_USE;
            }

            case SWAP_AND_USE -> {
                if (player.onGround()) {
                    player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                }

                if (pendingInfo != null) {
                    int invSlot = findInMainInventory(player, pendingInfo);
                    if (invSlot != -1) {
                        int selectedHotbar = player.getInventory().getSelectedSlot();

                        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
                        InventoryUtil.useDirect(player);
                        if (swapBack.getValue()) {
                            mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
                        }
                    }
                }

                cooldownTicks = 2;
                state = State.COOLDOWN;
            }

            case COOLDOWN -> {
                MoveBlockUtility.freeze(this, 2);
                if (player.onGround()) {
                    player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                }

                if (--cooldownTicks <= 0) {
                    state = State.IDLE;
                    pendingInfo = null;
                    MoveBlockUtility.unblock(this);
                }
            }

            case IDLE -> {
            }
        }
    }

    private void useFromHotbar(LocalPlayer player, int hotbarSlot) {
        int previousSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(hotbarSlot);
        InventoryUtil.useDirect(player);
        if (swapBack.getValue()) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
    }

    private void performPacketSwapUse(LocalPlayer player, int invSlot) {
        boolean wasSprinting = player.isSprinting();
        if (wasSprinting && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }

        int selectedHotbar = player.getInventory().getSelectedSlot();
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
        InventoryUtil.useDirect(player);
        if (swapBack.getValue()) {
            mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
        }

        if (wasSprinting && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
    }

    private int findInHotbar(LocalPlayer player, ItemInfo info) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matchesInfo(stack, info)) return i;
        }
        return -1;
    }

    private int findInMainInventory(LocalPlayer player, ItemInfo info) {
        return InventoryUtil.findInventorySlot(player, stack -> matchesInfo(stack, info));
    }

    private boolean matchesInfo(ItemStack stack, ItemInfo info) {
        if (stack == null || stack.isEmpty() || !stack.is(info.item)) return false;

        if (info.effectReqs != null && !info.effectReqs.isEmpty()) {
            return matchesPotion(stack, info.effectReqs);
        }

        if (info.loreKeywords != null && !info.loreKeywords.isEmpty()) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    String clean = cleanText(line.getString());
                    for (String kw : info.loreKeywords) {
                        if (clean.contains(kw.toLowerCase())) return true;
                    }
                }
            }
        }

        if (info.names != null && !info.names.isEmpty()) {
            String name = cleanText(stack.getHoverName().getString());
            for (String alias : info.names) {
                if (name.contains(alias.toLowerCase())) return true;
            }
            return false;
        }

        return true;
    }

    private boolean matchesPotion(ItemStack stack, List<EffectReq> reqs) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;

        Map<Holder<MobEffect>, Integer> activeEffects = new HashMap<>();
        for (MobEffectInstance instance : contents.customEffects()) {
            activeEffects.put(instance.getEffect(), instance.getAmplifier());
        }

        if (activeEffects.isEmpty()) return false;

        for (EffectReq req : reqs) {
            Integer amp = activeEffects.get(req.effect);
            if (amp == null || amp < req.minAmp) return false;
        }
        return true;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.toLowerCase().replaceAll("§[0-9a-fk-or]", "");
    }
}
