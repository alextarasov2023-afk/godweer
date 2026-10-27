package pub.godweer.kitty.module.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.KeyboardInputEvent;
import pub.godweer.enjoy.event.list.MouseInputEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.BindSetting;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.MultiModeSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Create by daun kvass
 */
public class AHHelper extends Module {
    public static AHHelper INSTANCE;

    public final ColorSetting cheapSlotColor = color("Price Cheap", ColorUtil.rgba(64, 255, 64, 130));
    public final ColorSetting goodSlotColor = color("Price good", ColorUtil.rgba(255, 255, 64, 130));
    public final CheckBox showPricePerUnit = checkbox("Price per unit", true);
    public final CheckBox ignoreDamaged = checkbox("Ignore broken", true);
    public final CheckBox ignoreThorns = checkbox("Ignore thorns", true);

    public final MultiModeSetting priorityEnchants = multiMode(
            "Priority Enchant",
            "Protection", "Mending", "Unbreaking", "Deep strider", "Feather Falling"
    );

    public final BindSetting searchItemBind = bind("Search Item in Hand", InputConstants.KEY_F);

    private static final Map<String, String> RUSSIAN_NAMES = new HashMap<>();

    public AHHelper() {
        super("AHHelper", "Умный помощник для аукциона", Category.MISC);
        INSTANCE = this;
        initDictionary();
    }

    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (event.getAction() == InputConstants.PRESS && searchItemBind.matches(event.getKey())) {
            searchItemInHand();
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (event.getAction() == InputConstants.PRESS && searchItemBind.matchesMouse(event.getButton())) {
            searchItemInHand();
        }
    }


    public boolean onScreenKey(int keyCode) {
        if (!isEnabled()) return false;
        if (searchItemBind.matches(keyCode)) {
            searchItemInHand();
            return true;
        }
        return false;
    }


    public void searchItemInHand() {
        if (mc.player == null || mc.getConnection() == null || screen() != null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) return;

        if (mc.gui.screen() != null) {
            mc.gui.setScreen(null);
        }

        String name = resolveRussianName(held);
        if (!name.isEmpty()) {
            String clean = cleanForSearch(name);
            if (!clean.isEmpty()) {
                mc.getConnection().sendCommand("ah search " + clean);
            }
        }
    }

    public boolean handleChatSell(String message) {
        if (!isEnabled() || mc.player == null || mc.getConnection() == null) return false;
        if (message == null) return false;

        Matcher m = Pattern.compile("^/ah sell (\\d+)\\s*!\\s*$", Pattern.CASE_INSENSITIVE).matcher(message.trim());
        if (m.matches()) {
            long pricePerItem = Long.parseLong(m.group(1));
            ItemStack held = mc.player.getMainHandItem();
            int count = held.isEmpty() ? 1 : held.getCount();
            long total = pricePerItem * count;

            mc.getConnection().sendCommand("ah sell " + total);
            return true;
        }
        return false;
    }

    public boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.has(DataComponents.CONTAINER) || stack.getItem().toString().toLowerCase().contains("shulker_box");
    }

    public boolean shouldIgnore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;

        if (ignoreDamaged.get() && stack.isDamaged()) {
            int remainingDurability = stack.getMaxDamage() - stack.getDamageValue();
            if (remainingDurability < 10) {
                return true;
            }
        }

        return ignoreThorns.get() && !isShulkerBox(stack) && hasThorns(stack);
    }

    public int getEnchantPriorityScore(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isShulkerBox(stack)) return 0;
        int score = 0;

        try {
            ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

            for (var entry : enchants.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                int level = entry.getIntValue();
                String path = holder.getRegisteredName().toLowerCase();

                if (path.contains("protection") && !path.contains("fire") && !path.contains("blast") && !path.contains("projectile") && priorityEnchants.isEnabled("Protection")) {
                    score += level * 10;
                }
                if (path.contains("mending") && priorityEnchants.isEnabled("Mending")) {
                    score += 50;
                }
                if (path.contains("unbreaking") && priorityEnchants.isEnabled("Unbreaking")) {
                    score += level * 5;
                }
                if (path.contains("depth_strider") && priorityEnchants.isEnabled("Deep strider")) {
                    score += level * 15;
                }
                if (path.contains("feather_falling") && priorityEnchants.isEnabled("Feather Falling")) {
                    score += level * 15;
                }
            }
        } catch (Throwable ignored) {}

        return score;
    }

    private boolean hasThorns(ItemStack stack) {
        if (isShulkerBox(stack)) return false;

        try {
            ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (var entry : enchants.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                String path = holder.getRegisteredName().toLowerCase();
                if (path.contains("thorns")) return true;
            }
        } catch (Throwable ignored) {}

        try {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    String str = stripFormatting(line.getString()).toLowerCase();
                    if (str.contains("шипы") || str.contains("thorns")) return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public String resolveRussianName(ItemStack stack) {
        try {
            if (isShulkerBox(stack)) return "шалкер";

            if (stack.has(DataComponents.CUSTOM_NAME)) {
                String custom = stripFormatting(stack.getHoverName().getString()).trim();
                if (!custom.isEmpty()) return custom;
            }

            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String mapped = RUSSIAN_NAMES.get(id.getPath());
            if (mapped != null) return mapped;

            return stripFormatting(stack.getItem().getName(stack).getString()).trim();
        } catch (Throwable t) {
            return "";
        }
    }

    public String cleanForSearch(String input) {
        if (input == null || input.isEmpty()) return "";
        String stripped = stripFormatting(input);
        String lower = stripped.toLowerCase();

        if (lower.contains("шалкер") || lower.contains("shulker")) return "шалкер";
        if (stripped.contains("TNT - TIER WHITE") || lower.contains("таер вайт")) return "таер вайт";
        if (stripped.contains("TNT - TIER BLACK") || lower.contains("таер блэк")) return "таер блэк";
        if (lower.contains("крушител")) return stripped;

        stripped = stripped.replaceAll("[\\[\\](){}【】『』「」〈〉《》«»]", "");
        StringBuilder sb = new StringBuilder();
        for (char c : stripped.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-') sb.append(c);
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    public static String stripFormatting(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private void initDictionary() {
      {
            RUSSIAN_NAMES.put("stone", "Камень");
            RUSSIAN_NAMES.put("granite", "Гранит");
            RUSSIAN_NAMES.put("polished_granite", "Полированный гранит");
            RUSSIAN_NAMES.put("diorite", "Диорит");
            RUSSIAN_NAMES.put("polished_diorite", "Полированный диорит");
            RUSSIAN_NAMES.put("andesite", "Андезит");
            RUSSIAN_NAMES.put("polished_andesite", "Полированный андезит");
            RUSSIAN_NAMES.put("deepslate", "Глубинный сланец");
            RUSSIAN_NAMES.put("cobbled_deepslate", "Глубинный сланец булыжник");
            RUSSIAN_NAMES.put("polished_deepslate", "Полированный глубинный сланец");
            RUSSIAN_NAMES.put("calcite", "Кальцит");
            RUSSIAN_NAMES.put("tuff", "Туф");
            RUSSIAN_NAMES.put("dripstone_block", "Натёчный блок");
            RUSSIAN_NAMES.put("grass_block", "Дёрн");
            RUSSIAN_NAMES.put("dirt", "Земля");
            RUSSIAN_NAMES.put("coarse_dirt", "Грубая земля");
            RUSSIAN_NAMES.put("podzol", "Подзол");
            RUSSIAN_NAMES.put("rooted_dirt", "Земля с корнями");
            RUSSIAN_NAMES.put("mud", "Грязь");
            RUSSIAN_NAMES.put("cobblestone", "Булыжник");
            RUSSIAN_NAMES.put("oak_planks", "Дубовые доски");
            RUSSIAN_NAMES.put("spruce_planks", "Еловые доски");
            RUSSIAN_NAMES.put("birch_planks", "Берёзовые доски");
            RUSSIAN_NAMES.put("jungle_planks", "Тропические доски");
            RUSSIAN_NAMES.put("acacia_planks", "Акациевые доски");
            RUSSIAN_NAMES.put("dark_oak_planks", "Доски из тёмного дуба");
            RUSSIAN_NAMES.put("mangrove_planks", "Мангровые доски");
            RUSSIAN_NAMES.put("cherry_planks", "Вишнёвые доски");
            RUSSIAN_NAMES.put("bamboo_planks", "Бамбуковые доски");
            RUSSIAN_NAMES.put("crimson_planks", "Багровые доски");
            RUSSIAN_NAMES.put("warped_planks", "Искажённые доски");
            RUSSIAN_NAMES.put("sand", "Песок");
            RUSSIAN_NAMES.put("red_sand", "Красный песок");
            RUSSIAN_NAMES.put("gravel", "Гравий");
            RUSSIAN_NAMES.put("sandstone", "Песчаник");
            RUSSIAN_NAMES.put("red_sandstone", "Красный песчаник");
            RUSSIAN_NAMES.put("uncraftable_potion", "зелье");
            RUSSIAN_NAMES.put("clay", "Глина");
            RUSSIAN_NAMES.put("bricks", "Кирпичи");
            RUSSIAN_NAMES.put("stone_bricks", "Каменные кирпичи");
            RUSSIAN_NAMES.put("mossy_stone_bricks", "Замшелые каменные кирпичи");
            RUSSIAN_NAMES.put("cracked_stone_bricks", "Потрескавшиеся каменные кирпичи");
            RUSSIAN_NAMES.put("chiseled_stone_bricks", "Резные каменные кирпичи");
            RUSSIAN_NAMES.put("mossy_cobblestone", "Замшелый булыжник");
            RUSSIAN_NAMES.put("obsidian", "Обсидиан");
            RUSSIAN_NAMES.put("crying_obsidian", "Плачущий обсидиан");
            RUSSIAN_NAMES.put("netherrack", "Адский камень");
            RUSSIAN_NAMES.put("soul_sand", "Песок душ");
            RUSSIAN_NAMES.put("soul_soil", "Почва душ");
            RUSSIAN_NAMES.put("basalt", "Базальт");
            RUSSIAN_NAMES.put("polished_basalt", "Полированный базальт");
            RUSSIAN_NAMES.put("smooth_basalt", "Гладкий базальт");
            RUSSIAN_NAMES.put("glowstone", "Светокамень");
            RUSSIAN_NAMES.put("nether_bricks", "Адские кирпичи");
            RUSSIAN_NAMES.put("nether_wart","Нарост");
            RUSSIAN_NAMES.put("glowstone_dust","светокаменная пыль");
            RUSSIAN_NAMES.put("spawner","спавнер");
            RUSSIAN_NAMES.put("red_nether_bricks", "Красные адские кирпичи");
            RUSSIAN_NAMES.put("end_stone", "Камень Края");
            RUSSIAN_NAMES.put("end_stone_bricks", "Кирпичи Края");
            RUSSIAN_NAMES.put("purpur_block", "Пурпурный блок");
            RUSSIAN_NAMES.put("purpur_pillar", "Пурпурный пилон");
            RUSSIAN_NAMES.put("prismarine", "Призмарин");
            RUSSIAN_NAMES.put("prismarine_bricks", "Призмариновые кирпичи");
            RUSSIAN_NAMES.put("dark_prismarine", "Тёмный призмарин");
            RUSSIAN_NAMES.put("sea_lantern", "Морской фонарь");
            RUSSIAN_NAMES.put("terracotta", "Терракота");
            RUSSIAN_NAMES.put("packed_mud", "Утрамбованная грязь");
            RUSSIAN_NAMES.put("mud_bricks", "Грязевые кирпичи");
            RUSSIAN_NAMES.put("quartz_block", "Кварцевый блок");
            RUSSIAN_NAMES.put("smooth_quartz", "Гладкий кварц");
            RUSSIAN_NAMES.put("villager_spawn_egg" ,"яйцо призыва жителя");
            RUSSIAN_NAMES.put("chiseled_quartz_block", "Резной кварцевый блок");
            RUSSIAN_NAMES.put("quartz_bricks", "Кварцевые кирпичи");
            RUSSIAN_NAMES.put("quartz_pillar", "Кварцевый пилон");
            RUSSIAN_NAMES.put("hay_block", "Блок сена");
            RUSSIAN_NAMES.put("bone_block", "Костяной блок");
            RUSSIAN_NAMES.put("blackstone", "Чернит");
            RUSSIAN_NAMES.put("polished_blackstone", "Полированный чернит");
            RUSSIAN_NAMES.put("polished_blackstone_bricks", "Полированные чернитовые кирпичи");
            RUSSIAN_NAMES.put("chiseled_polished_blackstone", "Резной полированный чернит");
            RUSSIAN_NAMES.put("gilded_blackstone", "Позолоченный чернит");
            RUSSIAN_NAMES.put("amethyst_block", "Аметистовый блок");
            RUSSIAN_NAMES.put("copper_block", "Медный блок");
            RUSSIAN_NAMES.put("cut_copper", "Резная медь");
            RUSSIAN_NAMES.put("oak_log", "Дубовое бревно");
            RUSSIAN_NAMES.put("spruce_log", "Еловое бревно");
            RUSSIAN_NAMES.put("birch_log", "Берёзовое бревно");
            RUSSIAN_NAMES.put("jungle_log", "Тропическое бревно");
            RUSSIAN_NAMES.put("acacia_log", "Акациевое бревно");
            RUSSIAN_NAMES.put("dark_oak_log", "Бревно тёмного дуба");
            RUSSIAN_NAMES.put("mangrove_log", "Мангровое бревно");
            RUSSIAN_NAMES.put("cherry_log", "Вишнёвое бревно");
            RUSSIAN_NAMES.put("crimson_stem", "Багровый стебель");
            RUSSIAN_NAMES.put("warped_stem", "Искажённый стебель");
            RUSSIAN_NAMES.put("bamboo_block", "Бамбуковый блок");
            RUSSIAN_NAMES.put("stripped_oak_log", "Обтёсанное дубовое бревно");
            RUSSIAN_NAMES.put("stripped_spruce_log", "Обтёсанное еловое бревно");
            RUSSIAN_NAMES.put("stripped_birch_log", "Обтёсанное берёзовое бревно");
            RUSSIAN_NAMES.put("stripped_jungle_log", "Обтёсанное тропическое бревно");
            RUSSIAN_NAMES.put("stripped_acacia_log", "Обтёсанное акациевое бревно");
            RUSSIAN_NAMES.put("stripped_dark_oak_log", "Обтёсанное бревно тёмного дуба");
            RUSSIAN_NAMES.put("stripped_mangrove_log", "Обтёсанное мангровое бревно");
            RUSSIAN_NAMES.put("stripped_cherry_log", "Обтёсанное вишнёвое бревно");
            RUSSIAN_NAMES.put("stripped_crimson_stem", "Обтёсанный багровый стебель");
            RUSSIAN_NAMES.put("stripped_warped_stem", "Обтёсанный искажённый стебель");
            RUSSIAN_NAMES.put("coal_ore", "Угольная руда");
            RUSSIAN_NAMES.put("deepslate_coal_ore", "Глубинная угольная руда");
            RUSSIAN_NAMES.put("iron_ore", "Железная руда");
            RUSSIAN_NAMES.put("deepslate_iron_ore", "Глубинная железная руда");
            RUSSIAN_NAMES.put("copper_ore", "Медная руда");
            RUSSIAN_NAMES.put("deepslate_copper_ore", "Глубинная медная руда");
            RUSSIAN_NAMES.put("gold_ore", "Золотая руда");
            RUSSIAN_NAMES.put("deepslate_gold_ore", "Глубинная золотая руда");
            RUSSIAN_NAMES.put("nether_gold_ore", "Адская золотая руда");
            RUSSIAN_NAMES.put("redstone_ore", "Руда красного камня");
            RUSSIAN_NAMES.put("deepslate_redstone_ore", "Глубинная руда красного камня");
            RUSSIAN_NAMES.put("emerald_ore", "Изумрудная руда");
            RUSSIAN_NAMES.put("deepslate_emerald_ore", "Глубинная изумрудная руда");
            RUSSIAN_NAMES.put("lapis_ore", "Руда лазурита");
            RUSSIAN_NAMES.put("deepslate_lapis_ore", "Глубинная руда лазурита");
            RUSSIAN_NAMES.put("diamond_ore", "Алмазная руда");
            RUSSIAN_NAMES.put("deepslate_diamond_ore", "Глубинная алмазная руда");
            RUSSIAN_NAMES.put("nether_quartz_ore", "Кварцевая руда Нижнего мира");
            RUSSIAN_NAMES.put("ancient_debris", "Древние обломки");
            RUSSIAN_NAMES.put("coal", "Уголь");
            RUSSIAN_NAMES.put("charcoal", "Древесный уголь");
            RUSSIAN_NAMES.put("raw_iron", "Необработанное железо");
            RUSSIAN_NAMES.put("raw_copper", "Необработанная медь");
            RUSSIAN_NAMES.put("raw_gold", "Необработанное золото");
            RUSSIAN_NAMES.put("iron_ingot", "Железный слиток");
            RUSSIAN_NAMES.put("copper_ingot", "Медный слиток");
            RUSSIAN_NAMES.put("gold_ingot", "Золотой слиток");
            RUSSIAN_NAMES.put("netherite_ingot", "Незеритовый слиток");
            RUSSIAN_NAMES.put("netherite_scrap", "Незеритовый лом");
            RUSSIAN_NAMES.put("diamond", "Алмаз");
            RUSSIAN_NAMES.put("emerald", "Изумруд");
            RUSSIAN_NAMES.put("lapis_lazuli", "Лазурит");
            RUSSIAN_NAMES.put("quartz", "Кварц Нижнего мира");
            RUSSIAN_NAMES.put("amethyst_shard", "Осколок аметиста");
            RUSSIAN_NAMES.put("iron_nugget", "Кусочек железа");
            RUSSIAN_NAMES.put("gold_nugget", "Кусочек золота");
            RUSSIAN_NAMES.put("redstone", "Красный камень");
            RUSSIAN_NAMES.put("flint", "Кремень");
            RUSSIAN_NAMES.put("stick", "Палка");
            RUSSIAN_NAMES.put("string", "Нить");
            RUSSIAN_NAMES.put("feather", "Перо");
            RUSSIAN_NAMES.put("gunpowder", "Порох");
            RUSSIAN_NAMES.put("leather", "Кожа");
            RUSSIAN_NAMES.put("rabbit_hide", "Кроличья шкурка");
            RUSSIAN_NAMES.put("slime_ball", "Слизь");
            RUSSIAN_NAMES.put("magma_cream", "Магмовый крем");
            RUSSIAN_NAMES.put("blaze_rod", "Огненный стержень");
            RUSSIAN_NAMES.put("blaze_powder", "Огненный порошок");
            RUSSIAN_NAMES.put("ender_pearl", "Эндер-жемчуг");
            RUSSIAN_NAMES.put("eye_of_ender", "Око Края");
            RUSSIAN_NAMES.put("nether_star", "Звезда Нижнего мира");
            RUSSIAN_NAMES.put("ghast_tear", "Слеза гаста");
            RUSSIAN_NAMES.put("phantom_membrane", "Мембрана фантома");
            RUSSIAN_NAMES.put("shulker_shell", "Панцирь шалкера");
            RUSSIAN_NAMES.put("heart_of_the_sea", "Сердце моря");
            RUSSIAN_NAMES.put("nautilus_shell", "Раковина наутилуса");
            RUSSIAN_NAMES.put("echo_shard", "Осколок эха");
            RUSSIAN_NAMES.put("disc_fragment_5", "Фрагмент диска");
            RUSSIAN_NAMES.put("honeycomb", "Медовые соты");
            RUSSIAN_NAMES.put("ink_sac", "Чернильный мешок");
            RUSSIAN_NAMES.put("glow_ink_sac", "Светящийся чернильный мешок");
            RUSSIAN_NAMES.put("bone_meal", "Костная мука");
            RUSSIAN_NAMES.put("bone", "Кость");
            RUSSIAN_NAMES.put("sugar", "Сахар");
            RUSSIAN_NAMES.put("paper", "Бумага");
            RUSSIAN_NAMES.put("book", "Книга");
            RUSSIAN_NAMES.put("experience_bottle", "Пузырёк опыта");
            RUSSIAN_NAMES.put("wooden_sword", "Деревянный меч");
            RUSSIAN_NAMES.put("stone_sword", "Каменный меч");
            RUSSIAN_NAMES.put("iron_sword", "Железный меч");
            RUSSIAN_NAMES.put("golden_sword", "Золотой меч");
            RUSSIAN_NAMES.put("diamond_sword", "Алмазный меч");
            RUSSIAN_NAMES.put("netherite_sword", "Незеритовый меч");
            RUSSIAN_NAMES.put("bow", "Лук");
            RUSSIAN_NAMES.put("crossbow", "Арбалет");
            RUSSIAN_NAMES.put("trident", "Трезубец");
            RUSSIAN_NAMES.put("mace", "Булава");
            RUSSIAN_NAMES.put("arrow", "Стрела");
            RUSSIAN_NAMES.put("spectral_arrow", "Спектральная стрела");
            RUSSIAN_NAMES.put("tipped_arrow", "Стрела с эффектом");
            RUSSIAN_NAMES.put("wooden_pickaxe", "Деревянная кирка");
            RUSSIAN_NAMES.put("stone_pickaxe", "Каменная кирка");
            RUSSIAN_NAMES.put("iron_pickaxe", "Железная кирка");
            RUSSIAN_NAMES.put("golden_pickaxe", "Золотая кирка");
            RUSSIAN_NAMES.put("diamond_pickaxe", "Алмазная кирка");
            RUSSIAN_NAMES.put("netherite_pickaxe", "Незеритовая кирка");
            RUSSIAN_NAMES.put("wooden_axe", "Деревянный топор");
            RUSSIAN_NAMES.put("stone_axe", "Каменный топор");
            RUSSIAN_NAMES.put("iron_axe", "Железный топор");
            RUSSIAN_NAMES.put("golden_axe", "Золотой топор");
            RUSSIAN_NAMES.put("diamond_axe", "Алмазный топор");
            RUSSIAN_NAMES.put("netherite_axe", "Незеритовый топор");
            RUSSIAN_NAMES.put("wooden_shovel", "Деревянная лопата");
            RUSSIAN_NAMES.put("stone_shovel", "Каменный лопата");
            RUSSIAN_NAMES.put("iron_shovel", "Железная лопата");
            RUSSIAN_NAMES.put("golden_shovel", "Золотая лопата");
            RUSSIAN_NAMES.put("diamond_shovel", "Алмазная лопата");
            RUSSIAN_NAMES.put("netherite_shovel", "Незеритовая лопата");
            RUSSIAN_NAMES.put("wooden_hoe", "Деревянная мотыга");
            RUSSIAN_NAMES.put("stone_hoe", "Каменная мотыга");
            RUSSIAN_NAMES.put("iron_hoe", "Железная мотыга");
            RUSSIAN_NAMES.put("golden_hoe", "Золотая мотыга");
            RUSSIAN_NAMES.put("diamond_hoe", "Алмазная мотыга");
            RUSSIAN_NAMES.put("netherite_hoe", "Незеритовая мотыга");
            RUSSIAN_NAMES.put("shears", "Ножницы");
            RUSSIAN_NAMES.put("flint_and_steel", "Огниво");
            RUSSIAN_NAMES.put("fishing_rod", "Удочка");
            RUSSIAN_NAMES.put("compass", "Компас");
            RUSSIAN_NAMES.put("recovery_compass", "Компас восстановления");
            RUSSIAN_NAMES.put("clock", "Часы");
            RUSSIAN_NAMES.put("spyglass", "Подзорная труба");
            RUSSIAN_NAMES.put("brush", "Кисть");
            RUSSIAN_NAMES.put("lead", "Поводок");
            RUSSIAN_NAMES.put("name_tag", "Бирка");
            RUSSIAN_NAMES.put("leather_helmet", "Кожаный шлем");
            RUSSIAN_NAMES.put("leather_chestplate", "Кожаная куртка");
            RUSSIAN_NAMES.put("leather_leggings", "Кожаные штаны");
            RUSSIAN_NAMES.put("leather_boots", "Кожаные ботинки");
            RUSSIAN_NAMES.put("chainmail_helmet", "Кольчужный шлем");
            RUSSIAN_NAMES.put("chainmail_chestplate", "Кольчужная кираса");
            RUSSIAN_NAMES.put("chainmail_leggings", "Кольчужные поножи");
            RUSSIAN_NAMES.put("chainmail_boots", "Кольчужные ботинки");
            RUSSIAN_NAMES.put("iron_helmet", "Железный шлем");
            RUSSIAN_NAMES.put("iron_chestplate", "Железная кираса");
            RUSSIAN_NAMES.put("iron_leggings", "Железные поножи");
            RUSSIAN_NAMES.put("iron_boots", "Железные ботинки");
            RUSSIAN_NAMES.put("golden_helmet", "Золотой шлем");
            RUSSIAN_NAMES.put("golden_chestplate", "Золотая кираса");
            RUSSIAN_NAMES.put("golden_leggings", "Золотые поножи");
            RUSSIAN_NAMES.put("golden_boots", "Золотые ботинки");
            RUSSIAN_NAMES.put("diamond_helmet", "Алмазный шлем");
            RUSSIAN_NAMES.put("diamond_chestplate", "Алмазная кираса");
            RUSSIAN_NAMES.put("diamond_leggings", "Алмазные поножи");
            RUSSIAN_NAMES.put("diamond_boots", "Алмазные ботинки");
            RUSSIAN_NAMES.put("netherite_helmet", "Незеритовый шлем");
            RUSSIAN_NAMES.put("netherite_chestplate", "Незеритовая кираса");
            RUSSIAN_NAMES.put("netherite_leggings", "Незеритовые поножи");
            RUSSIAN_NAMES.put("netherite_boots", "Незеритовые ботинки");
            RUSSIAN_NAMES.put("turtle_helmet", "Черепаший панцирь");
            RUSSIAN_NAMES.put("wither_skeleton_skull", "Череп визер-скелета");
            RUSSIAN_NAMES.put("elytra", "Элитры");
            RUSSIAN_NAMES.put("shield", "Щит");
            RUSSIAN_NAMES.put("totem_of_undying", "Тотем бессмертия");
            RUSSIAN_NAMES.put("apple", "Яблоко");
            RUSSIAN_NAMES.put("golden_apple", "Золотое яблоко");
            RUSSIAN_NAMES.put("enchanted_golden_apple", "Зачарованное золотое яблоко");
            RUSSIAN_NAMES.put("bread", "Хлеб");
            RUSSIAN_NAMES.put("cooked_porkchop", "Жареная свинина");
            RUSSIAN_NAMES.put("porkchop", "Сырая свинина");
            RUSSIAN_NAMES.put("cooked_beef", "Жареная говядина");
            RUSSIAN_NAMES.put("beef", "Сырая говядина");
            RUSSIAN_NAMES.put("cooked_chicken", "Жареная курятина");
            RUSSIAN_NAMES.put("chicken", "Сырая курятина");
            RUSSIAN_NAMES.put("cooked_mutton", "Жареная баранина");
            RUSSIAN_NAMES.put("mutton", "Сырая баранина");
            RUSSIAN_NAMES.put("cooked_cod", "Жареная треска");
            RUSSIAN_NAMES.put("cod", "Сырая треска");
            RUSSIAN_NAMES.put("cooked_salmon", "Жареный лосось");
            RUSSIAN_NAMES.put("salmon", "Сырой лосось");
            RUSSIAN_NAMES.put("tropical_fish", "Тропическая рыба");
            RUSSIAN_NAMES.put("pufferfish", "Иглобрюх");
            RUSSIAN_NAMES.put("cooked_rabbit", "Жареная крольчатина");
            RUSSIAN_NAMES.put("rabbit", "Сырая крольчатина");
            RUSSIAN_NAMES.put("rabbit_stew", "Тушёный кролик");
            RUSSIAN_NAMES.put("mushroom_stew", "Тушёные грибы");
            RUSSIAN_NAMES.put("beetroot_soup", "Свекольник");
            RUSSIAN_NAMES.put("suspicious_stew", "Подозрительное рагу");
            RUSSIAN_NAMES.put("baked_potato", "Печёный картофель");
            RUSSIAN_NAMES.put("potato", "Картофель");
            RUSSIAN_NAMES.put("poisonous_potato", "Ядовитый картофель");
            RUSSIAN_NAMES.put("carrot", "Морковь");
            RUSSIAN_NAMES.put("golden_carrot", "Золотая морковь");
            RUSSIAN_NAMES.put("beetroot", "Свёкла");
            RUSSIAN_NAMES.put("melon_slice", "Ломтик арбуза");
            RUSSIAN_NAMES.put("sweet_berries", "Сладкие ягоды");
            RUSSIAN_NAMES.put("glow_berries", "Светящиеся ягоды");
            RUSSIAN_NAMES.put("chorus_fruit", "Плод хоруса");
            RUSSIAN_NAMES.put("dried_kelp", "Сушёная ламинария");
            RUSSIAN_NAMES.put("cookie", "Печенье");
            RUSSIAN_NAMES.put("cake", "Торт");
            RUSSIAN_NAMES.put("pumpkin_pie", "Тыквенный пирог");
            RUSSIAN_NAMES.put("honey_bottle", "Бутылочка мёда");
            RUSSIAN_NAMES.put("dragon_head","голова дракона");
            RUSSIAN_NAMES.put("rotten_flesh", "Гнилая плоть");
            RUSSIAN_NAMES.put("spider_eye", "Паучий глаз");
            RUSSIAN_NAMES.put("fermented_spider_eye", "Ферментированный паучий глаз");
            RUSSIAN_NAMES.put("potion", "Зелье");
            RUSSIAN_NAMES.put("splash_potion", "Взрывное зелье");
            RUSSIAN_NAMES.put("lingering_potion", "Туманное зелье");
            RUSSIAN_NAMES.put("glass_bottle", "Стеклянная бутылочка");
            RUSSIAN_NAMES.put("water_bucket", "Ведро воды");
            RUSSIAN_NAMES.put("lava_bucket", "Ведро лавы");
            RUSSIAN_NAMES.put("milk_bucket", "Ведро молока");
            RUSSIAN_NAMES.put("bucket", "Ведро");
        }
    }
}
