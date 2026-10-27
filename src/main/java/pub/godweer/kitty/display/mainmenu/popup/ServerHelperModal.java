package pub.godweer.kitty.display.mainmenu.popup;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.kitty.display.mainmenu.PanelRefractions;
import pub.godweer.kitty.module.impl.misc.ServerHelper;
import pub.godweer.kitty.module.settings.impl.BindSetting;
import pub.godweer.auxiliary.RenderExtend;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.Render2DUtil;

import java.util.ArrayList;
import java.util.List;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public class ServerHelperModal implements Modal {

    private final ServerHelper module;
    private int currentTab = 0;
    private final String[] tabs = {"Ft Предметы", "Ft Зелья", "HolyWorld"};

    private BindSetting listeningBind = null;
    private boolean finished = false;

    private static class BindEntry {
        final String title;
        final ItemStack stack;
        final BindSetting setting;

        BindEntry(String title, Item item, BindSetting setting) {
            this.title = title;
            this.stack = new ItemStack(item);
            this.setting = setting;
        }
    }

    private final List<BindEntry> ftItems = new ArrayList<>();
    private final List<BindEntry> ftPotions = new ArrayList<>();
    private final List<BindEntry> hwItems = new ArrayList<>();

    public ServerHelperModal(ServerHelper module) {
        this.module = module;

        ftItems.add(new BindEntry("Божья Аура", Items.PHANTOM_MEMBRANE, module.ftGodAura));
        ftItems.add(new BindEntry("Дезориентация", Items.ENDER_EYE, module.ftDisorient));
        ftItems.add(new BindEntry("Огненный смерч", Items.FIRE_CHARGE, module.ftFireSwirl));
        ftItems.add(new BindEntry("Трапка", Items.NETHERITE_SCRAP, module.ftTrap));
        ftItems.add(new BindEntry("Снежок заморозки", Items.SNOWBALL, module.ftSnow));
        ftItems.add(new BindEntry("Явная пыль", Items.SUGAR, module.ftDust));
        ftItems.add(new BindEntry("Пласт", Items.DRIED_KELP, module.ftPlast));

        ftPotions.add(new BindEntry("Святая вода", Items.SPLASH_POTION, module.ftHolyWater));
        ftPotions.add(new BindEntry("Зелье Гнева", Items.SPLASH_POTION, module.ftAnger));
        ftPotions.add(new BindEntry("Зелье Паладина", Items.SPLASH_POTION, module.ftPaladin));
        ftPotions.add(new BindEntry("Зелье Ассасина", Items.SPLASH_POTION, module.ftAssassin));
        ftPotions.add(new BindEntry("Зелье Радиации", Items.SPLASH_POTION, module.ftRadiation));
        ftPotions.add(new BindEntry("Снотворное", Items.SPLASH_POTION, module.ftSleep));
        ftPotions.add(new BindEntry("Хлопушка", Items.SPLASH_POTION, module.ftClapper));

        hwItems.add(new BindEntry("Ком снега", Items.SNOWBALL, module.hwSnow));
        hwItems.add(new BindEntry("Стан", Items.NETHER_STAR, module.hwStun));
        hwItems.add(new BindEntry("Тыква Джека", Items.JACK_O_LANTERN, module.hwPumpkin));
        hwItems.add(new BindEntry("Взрывная трапка", Items.PRISMARINE_SHARD, module.hwExplosiveTrap));
        hwItems.add(new BindEntry("Трапка", Items.POPPED_CHORUS_FRUIT, module.hwNormalTrap));
        hwItems.add(new BindEntry("Огненный шар", Items.FIRE_CHARGE, module.hwFire));
    }

    @Override
    public void render(int mouseX, int mouseY, float screenWidth, float screenHeight, float alpha) {
        if (alpha <= 0.01F) return;


        float windowW = 440.0F;
        float windowH = 210.0F;
        float windowX = (screenWidth - windowW) / 2.0F;
        float windowY = (screenHeight - windowH) / 2.0F;

        Render2D.drawBlur(windowX, windowY, windowW, windowH, 8.0F, ColorUtil.multiplyAlpha(0xFF101216, alpha), alpha);

        Fonts.drawString(Fonts.SF_MEDIUM, "ServerHelper · бинды предметов", windowX + 14.0F, windowY + 12.0F, 10.0F,
                ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, alpha));

        float closeBtnSize = 14.0F;
        float closeBtnX = windowX + windowW - closeBtnSize - 12.0F;
        float closeBtnY = windowY + 10.0F;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnSize && mouseY >= closeBtnY && mouseY <= closeBtnY + closeBtnSize;
        Fonts.drawString(Fonts.SF_MEDIUM, "Ч", closeBtnX + 2.0F, closeBtnY + 2.0F, 9.0F,
                ColorUtil.multiplyAlpha(closeHover ? 0xFFFF5555 : Theme.TEXT_MUTED, alpha));

        float tabX = windowX + 14.0F;
        float tabY = windowY + 28.0F;
        float tabH = 18.0F;

        for (int i = 0; i < tabs.length; i++) {
            float tabW = Fonts.SF_MEDIUM.getWidth(tabs[i], 8.5F) + 12.0F;
            boolean selected = (currentTab == i);
            boolean hovered = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH;

            int bg = selected ? Theme.getAccentColor() : (hovered ? 0xFF222532 : 0xFF181A24);
            Render2D.drawRoundedRect(tabX, tabY, tabW, tabH, 4.0F, ColorUtil.multiplyAlpha(bg, alpha));
            Fonts.drawString(Fonts.SF_MEDIUM, tabs[i], tabX + 6.0F, tabY + 4.5F, 8.5F,
                    ColorUtil.multiplyAlpha(selected ? 0xFFFFFFFF : Theme.TEXT_MUTED, alpha));

            tabX += tabW + 5.0F;
        }

        List<BindEntry> currentList = switch (currentTab) {
            case 1 -> ftPotions;
            case 2 -> hwItems;
            default -> ftItems;
        };

        float startY = windowY + 54.0F;
        float cardW = (windowW - 28.0F - 8.0F) / 2.0F;
        float cardH = 26.0F;

        for (int i = 0; i < currentList.size(); i++) {
            BindEntry entry = currentList.get(i);
            int col = i % 2;
            int row = i / 2;

            float cardX = windowX + 14.0F + col * (cardW + 8.0F);
            float cardY = startY + row * (cardH + 6.0F);

            Render2D.drawRoundedRect(cardX, cardY, cardW, cardH, 4.0F, ColorUtil.multiplyAlpha(0xFF191B24, alpha));

            boolean isListening = (listeningBind == entry.setting);
            String bindText = isListening ? "..." : entry.setting.getDisplayValue();

            float btnW = Math.max(48.0F, Fonts.SF_MEDIUM.getWidth(bindText, 8.0F) + 10.0F);
            float btnH = 16.0F;
            float btnX = cardX + cardW - btnW - 4.0F;
            float btnY = cardY + 5.0F;

            int btnBg = isListening ? ColorUtil.withAlpha(Theme.getAccentColor(),120) : 0xFF242735;
            Render2D.drawRoundedRect(btnX, btnY, btnW, btnH, 3.0F, ColorUtil.multiplyAlpha(btnBg, alpha));
        }

        Render2DUtil.flush();

        GuiGraphicsExtractor extractor = RenderExtend.currentGuiGraphicsExtractor();
        if (extractor != null) {
            for (int i = 0; i < currentList.size(); i++) {
                BindEntry entry = currentList.get(i);
                int col = i % 2;
                int row = i / 2;

                float cardX = windowX + 14.0F + col * (cardW + 8.0F);
                float cardY = startY + row * (cardH + 6.0F);

                extractor.item(entry.stack, (int) (cardX + 5.0F), (int) (cardY + 5.0F));
            }
        }

        for (int i = 0; i < currentList.size(); i++) {
            BindEntry entry = currentList.get(i);
            int col = i % 2;
            int row = i / 2;

            float cardX = windowX + 14.0F + col * (cardW + 8.0F);
            float cardY = startY + row * (cardH + 6.0F);

            Fonts.drawString(Fonts.SF_MEDIUM, entry.title, cardX + 25.0F, cardY + 8.5F, 8.5F,
                    ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, alpha));

            boolean isListening = (listeningBind == entry.setting);
            String bindText = isListening ? "..." : entry.setting.getDisplayValue();

            float btnW = Math.max(48.0F, Fonts.SF_MEDIUM.getWidth(bindText, 8.0F) + 10.0F);
            float btnX = cardX + cardW - btnW - 4.0F;
            float btnY = cardY + 5.0F;

            float textW = Fonts.SF_MEDIUM.getWidth(bindText, 8.0F);
            Fonts.drawString(Fonts.SF_MEDIUM, bindText, btnX + (btnW - textW) / 2.0F, btnY + 4.0F, 8.0F,
                    ColorUtil.multiplyAlpha(isListening ? 0xFFFFFFFF : Theme.TEXT_MUTED, alpha));
        }

        Render2DUtil.flush();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();

        float windowW = 440.0F;
        float windowH = 210.0F;
        float windowX = (screenW - windowW) / 2.0F;
        float windowY = (screenH - windowH) / 2.0F;

        if (listeningBind != null) {
            listeningBind.setSingle(BindSetting.mouse(button));
            listeningBind = null;
            return true;
        }

        float closeBtnSize = 14.0F;
        float closeBtnX = windowX + windowW - closeBtnSize - 12.0F;
        float closeBtnY = windowY + 10.0F;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnSize && mouseY >= closeBtnY && mouseY <= closeBtnY + closeBtnSize) {
            close();
            return true;
        }

        float tabX = windowX + 14.0F;
        float tabY = windowY + 28.0F;
        float tabH = 18.0F;

        for (int i = 0; i < tabs.length; i++) {
            float tabW = Fonts.SF_MEDIUM.getWidth(tabs[i], 8.5F) + 12.0F;
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + tabH) {
                currentTab = i;
                listeningBind = null;
                return true;
            }
            tabX += tabW + 5.0F;
        }

        List<BindEntry> currentList = switch (currentTab) {
            case 1 -> ftPotions;
            case 2 -> hwItems;
            default -> ftItems;
        };

        float startY = windowY + 54.0F;
        float cardW = (windowW - 28.0F - 8.0F) / 2.0F;
        float cardH = 26.0F;

        for (int i = 0; i < currentList.size(); i++) {
            BindEntry entry = currentList.get(i);
            int col = i % 2;
            int row = i / 2;

            float cardX = windowX + 14.0F + col * (cardW + 8.0F);
            float cardY = startY + row * (cardH + 6.0F);

            String bindText = (listeningBind == entry.setting) ? "..." : entry.setting.getDisplayValue();
            float btnW = Math.max(48.0F, Fonts.SF_MEDIUM.getWidth(bindText, 8.0F) + 10.0F);
            float btnH = 16.0F;
            float btnX = cardX + cardW - btnW - 4.0F;
            float btnY = cardY + 5.0F;

            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                    listeningBind = (listeningBind == entry.setting) ? null : entry.setting;
                } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                    entry.setting.clear();
                    listeningBind = null;
                }
                return true;
            }
        }

        return true;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listeningBind != null) {
            if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_DELETE) {
                listeningBind.clear();
            } else {
                listeningBind.setSingle(BindSetting.key(keyCode));
            }
            listeningBind = null;
            return true;
        }

        if (keyCode == InputConstants.KEY_ESCAPE) {
            close();
            return true;
        }

        return true;
    }

    @Override
    public void charTyped(int codePoint) {
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    private void close() {
        this.finished = true;
        PanelRefractions.openModal(null);
        PanelRefractions.close(mc);
    }
}