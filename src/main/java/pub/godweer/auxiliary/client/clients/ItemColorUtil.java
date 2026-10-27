package pub.godweer.auxiliary.client.clients;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Create by daun kvass
 */
public final class ItemColorUtil {
    private static final Map<Item, Integer> COLOR_CACHE = new ConcurrentHashMap<>();

    private ItemColorUtil() {}

    public static int getItemColor(ItemStack stack, int fallbackColor) {
        if (stack == null || stack.isEmpty() || stack.is(Items.AIR)) {
            return fallbackColor;
        }

        Item item = stack.getItem();
        if (stack.has(DataComponents.DYED_COLOR) || stack.has(DataComponents.POTION_CONTENTS)) {
            return calculateItemColor(stack, fallbackColor);
        }

        return COLOR_CACHE.computeIfAbsent(item, k -> calculateItemColor(stack, fallbackColor));
    }

    private static int calculateItemColor(ItemStack stack, int fallbackColor) {
        try {
            DyedItemColor dyedColor = stack.get(DataComponents.DYED_COLOR);
            if (dyedColor != null) {
                return 0xFF000000 | dyedColor.rgb();
            }

            PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
            if (potionContents != null) {
                int potionColor = potionContents.getColor();
                if (potionColor != -1 && (potionColor & 0xFFFFFF) != 0) {
                    return 0xFF000000 | potionColor;
                }
            }

            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();

            return getKeywordColor(path, fallbackColor);
        } catch (Exception ignored) {
            return fallbackColor;
        }
    }

    private static int getKeywordColor(String name, int fallback) {
        if (name.contains("diamond")) return ColorUtil.rgba(45, 235, 215, 255);
        if (name.contains("netherite") || name.contains("stick")) return ColorUtil.rgba(90, 75, 85, 255);
        if (name.contains("gold")) return ColorUtil.rgba(255, 215, 0, 255);
        if (name.contains("emerald")) return ColorUtil.rgba(25, 225, 90, 255);
        if (name.contains("redstone")) return ColorUtil.rgba(255, 35, 20, 255);
        if (name.contains("lapis")) return ColorUtil.rgba(35, 80, 220, 255);
        if (name.contains("amethyst")) return ColorUtil.rgba(185, 100, 245, 255);
        if (name.contains("copper")) return ColorUtil.rgba(230, 120, 85, 255);
        if (name.contains("iron") || name.contains("chainmail")) return ColorUtil.rgba(220, 220, 220, 255);
        if (name.contains("totem")) return ColorUtil.rgba(235, 190, 40, 255);
        if (name.contains("mace")) return ColorUtil.rgba(160, 130, 100, 255);
        if (name.contains("wind_charge") || name.contains("breeze")) return ColorUtil.rgba(145, 230, 255, 255);
        if (name.contains("ender") || name.contains("eye")) return ColorUtil.rgba(20, 140, 125, 255);
        if (name.contains("blaze") || name.contains("fire") || name.contains("lava") || name.contains("magma")) return ColorUtil.rgba(255, 130, 20, 255);
        if (name.contains("chorus") || name.contains("purpur") || name.contains("shulker")) return ColorUtil.rgba(170, 95, 170, 255);
        if (name.contains("enchant") || name.contains("experience")) return ColorUtil.rgba(205, 70, 255, 255);
        if (name.contains("star") || name.contains("beacon")) return ColorUtil.rgba(230, 255, 255, 255);
        if (name.contains("tnt") || name.contains("firework")) return ColorUtil.rgba(255, 50, 50, 255);
        if (name.contains("apple")) return ColorUtil.rgba(230, 35, 35, 255);
        if (name.contains("slime")) return ColorUtil.rgba(120, 200, 95, 255);
        if (name.contains("honey")) return ColorUtil.rgba(255, 170, 0, 255);
        if (name.contains("glowstone") || name.contains("torch") || name.contains("lantern")) return ColorUtil.rgba(255, 220, 90, 255);
        if (name.contains("obsidian") || name.contains("crying")) return ColorUtil.rgba(80, 45, 130, 255);
        if (name.contains("prismarine")) return ColorUtil.rgba(85, 170, 155, 255);
        if (name.contains("water") || name.contains("ice")) return ColorUtil.rgba(60, 150, 255, 255);
        if (name.contains("poison") || name.contains("spider")) return ColorUtil.rgba(140, 180, 40, 255);

        return fallback;
    }
}