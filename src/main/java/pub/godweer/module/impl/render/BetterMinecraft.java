package pub.godweer.module.impl.render;

import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.MultiModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Create by daun kvass
 */
public class BetterMinecraft extends Module {

    public static BetterMinecraft INSTANCE;
    public final MultiModeSetting modes = multiMode("Mode","Chat","BossBar","Ф6");
    public final CheckBox kinematicCamera = checkbox("Пьян Ф5", false).visible(()->modes.isEnabled("Ф6"));
    public final SliderSetting animSpeed = slider("Speed Animation", 250.0F, 100.0F, 600.0F, 10);

    private final Map<UUID, Float> bossBarAnimations = new HashMap<>();

    public BetterMinecraft() {
        super("BetterMinecraft", "Прикольное меняет некие веши в майне", Category.RENDER);
        INSTANCE = this;
    }

    public void renderBossBars(Map<UUID, LerpingBossEvent> events) {
        if (!isEnabled() || !modes.isEnabled("BossBar")|| events.isEmpty()) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        float currentY = 12.0F;

        for (Map.Entry<UUID, LerpingBossEvent> entry : events.entrySet()) {
            LerpingBossEvent bossEvent = entry.getValue();
            UUID id = entry.getKey();

            float targetProgress = bossEvent.getProgress();
            float currentProgress = bossBarAnimations.getOrDefault(id, targetProgress);
            currentProgress = currentProgress + (targetProgress - currentProgress) * 0.15F;
            bossBarAnimations.put(id, currentProgress);

            String name = bossEvent.getName().getString();
            float fontSize = 8.0F;
            float nameW = Fonts.SF_MEDIUM.getWidth(name, fontSize);

            float barW = 180.0F;
            float barH = 4.0F;
            float islandW = Math.max(barW + 20.0F, nameW + 24.0F);
            float islandH = 24.0F;

            float islandX = (screenWidth - islandW) / 2.0F;
            float islandY = currentY;

            Render2D.drawBlur(islandX, islandY, islandW, islandH, 5.0F, 0x80101216, 1.0F);
            Render2D.drawRoundedRectWithOutline(islandX, islandY, islandW, islandH, 5.0F, 0xDA13151D, 1.0F, 0x402A2D3A);

            Fonts.drawString(Fonts.SF_MEDIUM, name, (screenWidth - nameW) / 2.0F, islandY + 3.5F, fontSize, Theme.TEXT_MAIN);

            float barX = (screenWidth - barW) / 2.0F;
            float barY = islandY + 14.0F;
            Render2D.drawRoundedRect(barX, barY, barW, barH, 2.0F, 0x30FFFFFF);

            if (currentProgress > 0.005F) {
                float fillW = Math.max(barW * currentProgress, barH);
                int fillColor = getBossBarColor(bossEvent.getColor());
                Render2D.drawRoundedRect(barX, barY, fillW, barH, 2.0F, fillColor);
            }

            currentY += islandH + 6.0F;
            if (currentY >= mc.getWindow().getGuiScaledHeight() / 3.0F) break;
        }

        bossBarAnimations.keySet().retainAll(events.keySet());
    }

    private int getBossBarColor(BossEvent.BossBarColor color) {
        return switch (color) {
            case PINK -> 0xFFFF82AA;
            case BLUE -> 0xFF64B4FF;
            case RED -> 0xFFFF5555;
            case GREEN -> 0xFF64FF82;
            case YELLOW -> 0xFFFFE664;
            case PURPLE -> 0xFFB482FF;
            case WHITE -> 0xFFE6E6E6;
        };
    }
}