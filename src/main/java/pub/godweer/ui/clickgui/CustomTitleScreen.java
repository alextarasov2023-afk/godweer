package pub.godweer.ui.clickgui;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hh.ru.Info;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import pub.godweer.util.RenderExtend;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.localization.Localization;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;
import pub.godweer.util.render.Render2DUtil;
import pub.godweer.account.AccountManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Create by daun kvass
 */
public class CustomTitleScreen extends Screen {
    private static String as = "images/ui/title/title";
    private static final Identifier[] BACKGROUNDS = new Identifier[]{
            Identifier.fromNamespaceAndPath("godweer", as+".png"),
            Identifier.fromNamespaceAndPath("godweer", as+"2.png"),
            Identifier.fromNamespaceAndPath("godweer", as+"3.png"),
            Identifier.fromNamespaceAndPath("godweer", as+"4.png"),
            Identifier.fromNamespaceAndPath("godweer", as+"5.png"),
            Identifier.fromNamespaceAndPath("godweer", as+"6.png")
    };
    private static int currentBgIndex = 0;

    private static final File WALLPAPER_FILE = new File(new File(System.getProperty("user.home"), "femboy"), "wallpaper.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean accountModalOpen = false;
    private float accountModalAnim = 0.0F;
    private String addAccountQuery = "";
    private boolean addInputFocused = false;
    private float accountScroll = 0.0F;
    private float maxAccountScroll = 0.0F;

    private boolean bgSelectorOpen = false;
    private float bgSelectorAnim = 0.0F;

    private float screenAlpha = 0.0F;
    private Screen targetScreen = null;

    private final float[] buttonHoverAnims = new float[5];

    static {
        loadWallpaper();
    }

    public CustomTitleScreen() {
        super(Component.literal("Main Menu"));
    }

    @Override
    protected void init() {
        super.init();
        AccountManager.getInstance().applyActiveSession();
        loadWallpaper();
        this.screenAlpha = 0.0F;
        this.targetScreen = null;
    }

    public static void loadWallpaper() {
        if (!WALLPAPER_FILE.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(WALLPAPER_FILE), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("wallpaperIndex")) {
                int index = root.get("wallpaperIndex").getAsInt();
                if (index >= 0 && index < BACKGROUNDS.length) {
                    currentBgIndex = index;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveWallpaper() {
        try {
            if (!WALLPAPER_FILE.getParentFile().exists()) {
                WALLPAPER_FILE.getParentFile().mkdirs();
            }
            JsonObject root = new JsonObject();
            root.addProperty("wallpaperIndex", currentBgIndex);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(WALLPAPER_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void switchScreen(Screen screen) {
        this.targetScreen = screen;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int screenWidth = this.width;
        int screenHeight = this.height;

        if (this.targetScreen != null) {
            this.screenAlpha = Math.max(0.0F, this.screenAlpha - 0.08F);
            if (this.screenAlpha <= 0.01F) {
                this.minecraft.gui.setScreen(this.targetScreen);
                return;
            }
        } else {
            this.screenAlpha = Math.min(1.0F, this.screenAlpha + 0.08F);
        }

        this.accountModalAnim = Mth.clamp(this.accountModalAnim + (accountModalOpen ? 0.08F : -0.08F), 0.0F, 1.0F);
        this.bgSelectorAnim = Mth.clamp(this.bgSelectorAnim + (bgSelectorOpen ? 0.08F : -0.08F), 0.0F, 1.0F);

        RenderExtend.enter2D(null, extractor, null);
        try {
            Render2DUtil.beginFrame();

            Render2D.drawTexture(BACKGROUNDS[currentBgIndex], 0, 0, screenWidth, screenHeight, 0.0F, 0xFFFFFFFF);

            float rightPanelW = 240.0F;
            Render2D.drawGradientRound(screenWidth - rightPanelW, 0, rightPanelW, screenHeight, 0.0F,
                    0x00000000, 0x900B0C10, 0x900B0C10, 0x00000000);
            renderRightButtons(screenWidth, screenHeight, mouseX, mouseY);

            renderTopLeftAccount(screenWidth, screenHeight);

            renderWallpaperButton(screenWidth, screenHeight, mouseX, mouseY);

            if (this.bgSelectorAnim > 0.001F) {
                renderWallpaperModal(extractor, screenWidth, screenHeight, mouseX, mouseY, this.bgSelectorAnim);
            }

            if (this.accountModalAnim > 0.001F) {
                renderAccountModal(screenWidth, screenHeight, mouseX, mouseY, this.accountModalAnim);
            }

            if (this.screenAlpha < 0.999F) {
                int fadeOverlay = ColorUtil.rgba(0, 0, 0, (int) ((1.0F - this.screenAlpha) * 255));
                Render2D.drawRect(0, 0, screenWidth, screenHeight, fadeOverlay);
            }

            Render2DUtil.flush();
        } finally {
            RenderExtend.exit2D();
        }
    }

    private void renderRightButtons(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        float btnW = 160.0F;
        float btnH = 28.0F;
        float spacing = 7.0F;

        String[] titles = {"Single Player", "Multi Player", "Alt Manager", "Settings", "Quit"};

        float totalH = (titles.length * btnH) + ((titles.length - 1) * spacing);
        float startX = screenWidth / 2.5f;
        float startY = (screenHeight - totalH) / 2.0F + 10.0F;

        float wave = (float) (Math.sin(System.currentTimeMillis() / 1150.0) * 0.5 + 0.5);
        int animatedTitleColor = ColorUtil.interpolateColor(ColorUtil.WHITE, Theme.getAccentColor(), wave);

        Fonts.drawCenteredString(Fonts.SF_MEDIUM, Info.NAME, startX + (btnW / 2.0F), startY - 26.0F, 16.0F, animatedTitleColor);

        for (int i = 0; i < titles.length; i++) {
            float y = startY + i * (btnH + spacing);
            boolean hovered = !accountModalOpen && !bgSelectorOpen && targetScreen == null
                    && mouseX >= startX && mouseX <= startX + btnW && mouseY >= y && mouseY <= y + btnH;

            buttonHoverAnims[i] = Mth.clamp(buttonHoverAnims[i] + (hovered ? 0.12F : -0.12F), 0.0F, 1.0F);
            float hAnim = buttonHoverAnims[i];

            int bgCol = ColorUtil.lerp(0x55111218, Theme.getAccentWithAlpha(50), hAnim);
            Render2D.drawRoundedRect(startX, y, btnW, btnH, 5.0F, bgCol);

            int textCol = ColorUtil.lerp(Theme.TEXT_MAIN, ColorUtil.WHITE, hAnim);
            String localizedTitle = Localization.get(titles[i]);
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, localizedTitle, startX + (btnW / 2.0F), y + (btnH - 10.0F) / 2.0F + 1.0F, 10.0F, textCol);
        }
    }

    private void renderTopLeftAccount(int screenWidth, int screenHeight) {
        String curUser = this.minecraft.getUser().getName();
        float fontSize = 9.5F;
        float textW = Fonts.SF_MEDIUM.getWidth(curUser, fontSize);
        float iconSize = 14.0F;
        float paddingX = 8.0F;
        float paddingY = 4.5F;
        float totalW = iconSize + 6.0F + textW + (paddingX * 2);
        float totalH = iconSize + (paddingY * 2);

        float x = 12.0F;
        float y = 12.0F;

        Render2D.drawRoundedRect(x, y, totalW, totalH, 6.0F, 0x66111218);

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + curUser).getBytes(StandardCharsets.UTF_8));
        Identifier skin = DefaultPlayerSkin.get(uuid).body().texturePath();
        Render2D.drawHead(skin, x + paddingX, y + paddingY, iconSize, 2.0F);

        Fonts.drawString(Fonts.SF_MEDIUM, curUser, x + paddingX + iconSize + 6.0F, y + paddingY + 2.5F, fontSize, Theme.TEXT_MAIN);
    }

    private void renderWallpaperButton(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        float btnW = 54.0F;
        float btnH = 22.0F;
        float btnX = screenWidth - btnW - 12.0F;
        float btnY = screenHeight - btnH - 10.0F;

        boolean hovered = !accountModalOpen && targetScreen == null && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        int bgCol = hovered || bgSelectorOpen ? Theme.getAccentWithAlpha(120) : 0x66111218;

        Render2D.drawRoundedRect(btnX, btnY, btnW, btnH, 5.0F, bgCol);

        Fonts.drawCenteredString(Fonts.SF_MEDIUM, Localization.get("Wallpaper"), btnX + (btnW / 2.0F), btnY + 6.0F, 9.5F, Theme.TEXT_MAIN);
    }

    private void renderWallpaperModal(GuiGraphicsExtractor extractor, int screenWidth, int screenHeight, int mouseX, int mouseY, float alpha) {
        float modalW = 180.0F;
        float modalH = 110.0F;
        float modalX = screenWidth - modalW - 12.0F;
        float modalY = screenHeight - modalH - 38.0F;

        if (Theme.getBackgroundMode().equalsIgnoreCase("Blur")) {
            Render2D.drawBlur(modalX, modalY, modalW, modalH, 10, ColorUtil.WHITE, 0.1f);
        }

        Render2D.drawRoundedRect(modalX, modalY, modalW, modalH, 7.0F, ColorUtil.multiplyAlpha(0xEE111218, alpha));
        Fonts.drawString(Fonts.SF_MEDIUM, Localization.get("Wallpaper"), modalX + 12.0F, modalY + 6.0F, 10.5F, ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, alpha));

        float previewW = 124.0F;
        float previewH = 70.0F;
        float previewX = modalX + (modalW - previewW) / 2.0F;
        float previewY = modalY + 20.0F;

        Render2D.drawTexture(BACKGROUNDS[currentBgIndex], previewX, previewY, previewW, previewH, 0.0F, ColorUtil.multiplyAlpha(0xFFFFFFFF, alpha));

        float arrowSize = 18.0F;
        float arrowY = previewY + (previewH - arrowSize) / 2.0F;
        float arrowCy = arrowY + (arrowSize / 2.0F);

        float leftArrowX = previewX - arrowSize - 6.0F;
        float leftCx = leftArrowX + (arrowSize / 2.0F);
        boolean leftHover = mouseX >= leftArrowX && mouseX <= leftArrowX + arrowSize && mouseY >= arrowY && mouseY <= arrowY + arrowSize;
        Render2D.drawRoundedRect(leftArrowX, arrowY, arrowSize, arrowSize, 4.0F, ColorUtil.multiplyAlpha(leftHover ? 0xFF2A2D3D : 0x551E202C, alpha));
        int leftCol = ColorUtil.multiplyAlpha(leftHover ? ColorUtil.WHITE : Theme.TEXT_MUTED, alpha);
        drawRotatedIcon(extractor, IconUse.UP, leftCx, arrowCy, 9.0F, -90.0F, leftCol);

        float rightArrowX = previewX + previewW + 6.0F;
        float rightCx = rightArrowX + (arrowSize / 2.0F);
        boolean rightHover = mouseX >= rightArrowX && mouseX <= rightArrowX + arrowSize && mouseY >= arrowY && mouseY <= arrowY + arrowSize;
        Render2D.drawRoundedRect(rightArrowX, arrowY, arrowSize, arrowSize, 4.0F, ColorUtil.multiplyAlpha(rightHover ? 0xFF2A2D3D : 0x551E202C, alpha));
        int rightCol = ColorUtil.multiplyAlpha(rightHover ? ColorUtil.WHITE : Theme.TEXT_MUTED, alpha);
        drawRotatedIcon(extractor, IconUse.UP, rightCx, arrowCy, 9.0F, 90.0F, rightCol);

        String pageInfo = (currentBgIndex + 1) + " / " + BACKGROUNDS.length;
        Fonts.drawCenteredString(Fonts.SF_MEDIUM, pageInfo, modalX + (modalW / 2.0F), previewY + previewH + 8.0F, 9.0F, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));
    }

    private void drawRotatedIcon(GuiGraphicsExtractor extractor, IconUse icon, float cx, float cy, float size, float angleDeg, int color) {
        var pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(angleDeg));
        Fonts.drawIcon(icon, -size / 2.0F, -size / 2.0F, size, color);
        pose.popMatrix();
    }

    private void renderAccountModal(int screenWidth, int screenHeight, int mouseX, int mouseY, float alpha) {
        Render2D.drawRect(0, 0, screenWidth, screenHeight, ColorUtil.multiplyAlpha(0x88000000, alpha));

        float modalW = 270.0F;
        float modalH = 310.0F;
        float modalX = (screenWidth - modalW) / 2.0F;
        float modalY = (screenHeight - modalH) / 2.0F;

        if (Theme.getBackgroundMode().equalsIgnoreCase("Blur")) {
            Render2D.drawBlur(modalX, modalY, modalW, modalH, 10, ColorUtil.WHITE, 0.1f);
        }
        Render2D.drawRoundedRect(modalX, modalY, modalW, modalH, 8.0F, ColorUtil.multiplyAlpha(0xEE111218, alpha));

        Fonts.drawString(Fonts.SF_MEDIUM, Localization.get("Account Manager"), modalX + 12.0F, modalY + 12.0F, 12.0F, ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, alpha));

        float closeX = modalX + modalW - 22.0F;
        float closeY = modalY + 11.0F;
        boolean closeHover = mouseX >= closeX - 2 && mouseX <= closeX + 12 && mouseY >= closeY - 2 && mouseY <= closeY + 12;
        Fonts.drawIcon(IconUse.CROSS, closeX, closeY, 10.0F, ColorUtil.multiplyAlpha(closeHover ? 0xFFEF4444 : Theme.TEXT_MUTED, alpha));

        float inputX = modalX + 10.0F;
        float inputY = modalY + 34.0F;
        float inputW = modalW - 20.0F;
        float inputH = 22.0F;

        Render2D.drawRoundedRect(inputX, inputY, inputW, inputH, 4.0F, ColorUtil.multiplyAlpha(0x551E202C, alpha));
        if (addInputFocused) {
            Render2D.drawRoundedOutline(inputX, inputY, inputW, inputH, 4.0F, 1.0F, ColorUtil.multiplyAlpha(Theme.getAccentColor(), alpha));
        }

        boolean blink = (System.currentTimeMillis() / 450) % 2 == 0;
        String displayInput = addAccountQuery.isEmpty() ? Localization.get("Enter nickname...") : addAccountQuery + (addInputFocused && blink ? "|" : "");
        int inputCol = addAccountQuery.isEmpty() ? 0xFF65687A : Theme.TEXT_MAIN;
        Fonts.drawString(Fonts.SF_MEDIUM, displayInput, inputX + 8.0F, inputY + 6.5F, 9.5F, ColorUtil.multiplyAlpha(inputCol, alpha));

        float addBtnX = inputX + inputW - 18.0F;
        float addBtnY = inputY + 5.5F;
        boolean addBtnHover = mouseX >= addBtnX - 3 && mouseX <= addBtnX + 14 && mouseY >= inputY && mouseY <= inputY + inputH;
        Fonts.drawIcon(IconUse.ADD, addBtnX, addBtnY, 10.0F, ColorUtil.multiplyAlpha(addBtnHover ? Theme.getAccentColor() : ColorUtil.WHITE, alpha));

        float listX = modalX + 10.0F;
        float listY = inputY + inputH + 8.0F;
        float listW = modalW - 20.0F;
        float listH = modalH - (listY - modalY) - 10.0F;

        Render2D.pushScissor(listX, listY, listW, listH);

        List<String> accounts = AccountManager.getInstance().getSortedAccounts();
        String currentName = this.minecraft.getUser().getName();

        float cardH = 28.0F;
        float cardY = listY - this.accountScroll;

        for (String acc : accounts) {
            boolean isCur = acc.equalsIgnoreCase(currentName);
            boolean isFav = AccountManager.getInstance().isFavorite(acc);
            boolean inScissor = mouseY >= listY && mouseY <= listY + listH;
            boolean hovered = inScissor && mouseX >= listX && mouseX <= listX + listW && mouseY >= cardY && mouseY <= cardY + cardH;

            int cardBg;
            if (isCur) {
                cardBg = ColorUtil.lerp(0xFF181A26, Theme.getAccentColor(), 0.28F);
            } else if (isFav) {
                cardBg = hovered ? 0x803A2E0D : 0x5033280B;
            } else {
                cardBg = hovered ? 0xFF1C1E2A : 0x50181A26;
            }

            Render2D.drawRoundedRect(listX, cardY, listW, cardH, 4.0F, ColorUtil.multiplyAlpha(cardBg, alpha));

            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + acc).getBytes(StandardCharsets.UTF_8));
            Identifier skin = DefaultPlayerSkin.get(uuid).body().texturePath();
            Render2D.drawHead(skin, listX + 5.0F, cardY + 5.0F, 18.0F, 2.0F);

            String nameText = Fonts.SF_MEDIUM.trimToWidth(acc, listW - 90.0F, 10.0F);
            Fonts.drawString(Fonts.SF_MEDIUM, nameText, listX + 28.0F, cardY + 8.5F, 10.0F,
                    ColorUtil.multiplyAlpha(isCur ? ColorUtil.WHITE : (isFav ? 0xFFFFF1AA : Theme.TEXT_MAIN), alpha));

            float linkX = listX + listW - 58.0F;
            float linkY = cardY + 8.5F;
            boolean linkHover = inScissor && mouseX >= linkX && mouseX <= linkX + 12 && mouseY >= linkY && mouseY <= linkY + 12;
            int linkCol = linkHover ? ColorUtil.WHITE : Theme.TEXT_MUTED;
            Fonts.drawIcon(IconUse.LINK, linkX, linkY, 10.0F, ColorUtil.multiplyAlpha(linkCol, alpha));

            float starX = listX + listW - 38.0F;
            float starY = cardY + 8.5F;
            boolean starHover = inScissor && mouseX >= starX && mouseX <= starX + 12 && mouseY >= starY && mouseY <= starY + 12;
            int starCol = isFav ? 0xFFFFD700 : (starHover ? 0xFFFFF275 : Theme.TEXT_MUTED);
            Fonts.drawIcon(IconUse.STAR, starX, starY, 10.0F, ColorUtil.multiplyAlpha(starCol, alpha));

            float delX = listX + listW - 18.0F;
            float delY = cardY + 8.5F;
            boolean delHover = inScissor && mouseX >= delX && mouseX <= delX + 12 && mouseY >= delY && mouseY <= delY + 12;
            int delCol = delHover ? 0xFFEF4444 : Theme.TEXT_MUTED;
            Fonts.drawIcon(IconUse.CROSS, delX, delY, 10.0F, ColorUtil.multiplyAlpha(delCol, alpha));

            cardY += cardH + 4.0F;
        }

        if (accounts.isEmpty()) {
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, Localization.get("Account list is empty"), listX + (listW / 2.0F), listY + (listH / 2.0F) - 5.0F, 10.0F, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));
        }

        Render2D.popScissor();

        float totalHeight = (cardY + this.accountScroll) - listY;
        this.maxAccountScroll = Math.max(0.0F, totalHeight - listH);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || this.targetScreen != null) {
            return super.mouseClicked(event, bl);
        }

        double mouseX = event.x();
        double mouseY = event.y();
        int screenWidth = this.width;
        int screenHeight = this.height;

        if (this.bgSelectorOpen) {
            float modalW = 180.0F;
            float modalH = 110.0F;
            float modalX = screenWidth - modalW - 12.0F;
            float modalY = screenHeight - modalH - 38.0F;

            if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
                float btnW = 54.0F;
                float btnH = 22.0F;
                float btnX = screenWidth - btnW - 12.0F;
                float btnY = screenHeight - btnH - 10.0F;
                if (!(mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH)) {
                    this.bgSelectorOpen = false;
                }
            } else {
                float previewW = 124.0F;
                float previewH = 70.0F;
                float previewX = modalX + (modalW - previewW) / 2.0F;
                float previewY = modalY + 20.0F;
                float arrowSize = 18.0F;
                float arrowY = previewY + (previewH - arrowSize) / 2.0F;

                float leftArrowX = previewX - arrowSize - 6.0F;
                if (mouseX >= leftArrowX && mouseX <= leftArrowX + arrowSize && mouseY >= arrowY && mouseY <= arrowY + arrowSize) {
                    currentBgIndex = (currentBgIndex - 1 + BACKGROUNDS.length) % BACKGROUNDS.length;
                    saveWallpaper();
                    return true;
                }

                float rightArrowX = previewX + previewW + 6.0F;
                if (mouseX >= rightArrowX && mouseX <= rightArrowX + arrowSize && mouseY >= arrowY && mouseY <= arrowY + arrowSize) {
                    currentBgIndex = (currentBgIndex + 1) % BACKGROUNDS.length;
                    saveWallpaper();
                    return true;
                }
                return true;
            }
        }

        float bgBtnW = 54.0F;
        float bgBtnH = 22.0F;
        float bgBtnX = screenWidth - bgBtnW - 12.0F;
        float bgBtnY = screenHeight - bgBtnH - 10.0F;
        if (!accountModalOpen && mouseX >= bgBtnX && mouseX <= bgBtnX + bgBtnW && mouseY >= bgBtnY && mouseY <= bgBtnY + bgBtnH) {
            this.bgSelectorOpen = !this.bgSelectorOpen;
            return true;
        }

        if (this.accountModalOpen) {
            float modalW = 270.0F;
            float modalH = 310.0F;
            float modalX = (screenWidth - modalW) / 2.0F;
            float modalY = (screenHeight - modalH) / 2.0F;

            if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
                this.accountModalOpen = false;
                this.addInputFocused = false;
                return true;
            }

            float closeX = modalX + modalW - 22.0F;
            float closeY = modalY + 11.0F;
            if (mouseX >= closeX - 2 && mouseX <= closeX + 14 && mouseY >= closeY - 2 && mouseY <= closeY + 14) {
                this.accountModalOpen = false;
                this.addInputFocused = false;
                return true;
            }

            float inputX = modalX + 10.0F;
            float inputY = modalY + 34.0F;
            float inputW = modalW - 20.0F;
            float inputH = 22.0F;

            if (mouseX >= inputX && mouseX <= inputX + inputW && mouseY >= inputY && mouseY <= inputY + inputH) {
                float addBtnX = inputX + inputW - 18.0F;
                if (mouseX >= addBtnX - 3) {
                    confirmAddAccount();
                } else {
                    this.addInputFocused = true;
                }
                return true;
            } else {
                this.addInputFocused = false;
            }

            float listX = modalX + 10.0F;
            float listY = inputY + inputH + 8.0F;
            float listW = modalW - 20.0F;
            float listH = modalH - (listY - modalY) - 10.0F;

            if (mouseY >= listY && mouseY <= listY + listH && mouseX >= listX && mouseX <= listX + listW) {
                float cardH = 28.0F;
                float cardY = listY - this.accountScroll;

                for (String acc : AccountManager.getInstance().getSortedAccounts()) {
                    if (mouseY >= cardY && mouseY <= cardY + cardH) {
                        float linkX = listX + listW - 58.0F;
                        float starX = listX + listW - 38.0F;
                        float delX = listX + listW - 18.0F;
                        float iconY = cardY + 8.5F;

                        if (mouseX >= linkX && mouseX <= linkX + 14 && mouseY >= iconY && mouseY <= iconY + 14) {
                            this.minecraft.keyboardHandler.setClipboard(acc);
                            return true;
                        }

                        if (mouseX >= starX && mouseX <= starX + 14 && mouseY >= iconY && mouseY <= iconY + 14) {
                            AccountManager.getInstance().toggleFavorite(acc);
                            return true;
                        }

                        if (mouseX >= delX && mouseX <= delX + 14 && mouseY >= iconY && mouseY <= iconY + 14) {
                            AccountManager.getInstance().removeAccount(acc);
                            return true;
                        }

                        AccountManager.getInstance().setSession(acc);
                        return true;
                    }
                    cardY += cardH + 4.0F;
                }
            }
            return true;
        }

        float btnW = 160.0F;
        float btnH = 28.0F;
        float spacing = 7.0F;
        float totalH = (5 * btnH) + (4 * spacing);
        float startX = screenWidth / 2.5f;
        float startY = (screenHeight - totalH) / 2.0F + 10.0F;

        for (int i = 0; i < 5; i++) {
            float y = startY + i * (btnH + spacing);
            if (mouseX >= startX && mouseX <= startX + btnW && mouseY >= y && mouseY <= y + btnH) {
                switch (i) {
                    case 0 -> switchScreen(new SelectWorldScreen(this));
                    case 1 -> switchScreen(new JoinMultiplayerScreen(this));
                    case 2 -> {
                        this.accountModalOpen = true;
                        this.bgSelectorOpen = false;
                        this.addAccountQuery = "";
                    }
                    case 3 -> switchScreen(new OptionsScreen(this, this.minecraft.options));
                    case 4 -> switchScreen(new ConfirmScreen(
                            (confirmed) -> {
                                if (confirmed) this.minecraft.stop();
                                else this.minecraft.gui.setScreen(this);
                            },
                            Component.literal(Localization.get("Quit")),
                            Component.literal(Localization.get("Are you sure you want to quit?"))
                    ));
                }
                return true;
            }
        }

        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.accountModalOpen) {
            this.accountScroll = Mth.clamp(this.accountScroll - (float) verticalAmount * 16.0F, 0.0F, this.maxAccountScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();

        if (this.bgSelectorOpen && (event.isEscape() || keyCode == InputConstants.KEY_ESCAPE)) {
            this.bgSelectorOpen = false;
            return true;
        }

        if (this.accountModalOpen) {
            if (event.isEscape() || keyCode == InputConstants.KEY_ESCAPE) {
                this.accountModalOpen = false;
                this.addInputFocused = false;
                return true;
            }

            if (this.addInputFocused) {
                if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                    confirmAddAccount();
                    return true;
                }
                if (keyCode == InputConstants.KEY_BACKSPACE && !this.addAccountQuery.isEmpty()) {
                    this.addAccountQuery = this.addAccountQuery.substring(0, this.addAccountQuery.length() - 1);
                    return true;
                }
                if (event.hasControlDown() && keyCode == InputConstants.KEY_V) {
                    String paste = this.minecraft.keyboardHandler.getClipboard();
                    if (paste != null) {
                        paste = paste.replaceAll("[^a-zA-Z0-9_]", "");
                        String res = this.addAccountQuery + paste;
                        if (res.length() > 16) res = res.substring(0, 16);
                        this.addAccountQuery = res;
                    }
                    return true;
                }

                if (keyCode >= InputConstants.KEY_A && keyCode <= InputConstants.KEY_Z) {
                    if (this.addAccountQuery.length() < 16) {
                        char c = (char) ('a' + (keyCode - InputConstants.KEY_A));
                        if (event.hasShiftDown()) c = Character.toUpperCase(c);
                        this.addAccountQuery += c;
                    }
                    return true;
                }

                if (keyCode >= InputConstants.KEY_0 && keyCode <= InputConstants.KEY_9) {
                    if (this.addAccountQuery.length() < 16) {
                        this.addAccountQuery += (char) ('0' + (keyCode - InputConstants.KEY_0));
                    }
                    return true;
                }

                if (keyCode == InputConstants.KEY_MINUS && event.hasShiftDown()) {
                    if (this.addAccountQuery.length() < 16) {
                        this.addAccountQuery += "_";
                    }
                    return true;
                }
            }
            return true;
        }

        return super.keyPressed(event);
    }

    private void confirmAddAccount() {
        String name = this.addAccountQuery.trim();
        if (!name.isEmpty()) {
            AccountManager.getInstance().addAccount(name);
            AccountManager.getInstance().setSession(name);
            this.addAccountQuery = "";
        }
    }
}
