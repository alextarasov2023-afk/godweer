package pub.godweer.util.game;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Create by daun kvass
 */
public class AHUtils {
    private static final Pattern PRICE_PATTERN = Pattern.compile("(?:Цена|Стоимость|Price):?\\s*\\$?([0-9\\s,.]+)(?:\\$|монет|руб)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUM_CLEAN_PATTERN = Pattern.compile("[^0-9]");


    public static boolean isAuction(AbstractContainerMenu menu, Component title) {
        if (title != null) {
            String str = title.getString().toLowerCase();
            if (str.contains("аукцион") || str.contains("рынок") || str.contains("auction") || str.contains("поиск:")) {
                return true;
            }
        }
        return false;
    }


    public static long getPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Long.MAX_VALUE;

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return Long.MAX_VALUE;

        for (Component line : lore.lines()) {
            String text = line.getString();
            Matcher matcher = PRICE_PATTERN.matcher(text);
            if (matcher.find()) {
                String rawPrice = matcher.group(1);
                String cleanPrice = NUM_CLEAN_PATTERN.matcher(rawPrice).replaceAll("");
                if (!cleanPrice.isEmpty()) {
                    try {
                        return Long.parseLong(cleanPrice);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return Long.MAX_VALUE;
    }
}