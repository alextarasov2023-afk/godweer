package pub.godweer.ui.clickgui;

import com.mojang.blaze3d.platform.InputConstants;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Create by daun kvass
 */
public class CustomTitleScreen extends Screen {
    /** Фон меню рисуется примитивами — сторонних картинок в этом экране больше нет. */
    private static final int BACKDROP = ColorUtil.rgba(9, 10, 14, 255);

    private boolean accountModalOpen = false;
    private float accountModalAnim = 0.0F;
    private String addAccountQuery = "";
    private boolean addInputFocused = false;
    private float accountScroll = 0.0F;
    private float maxAccountScroll = 0.0F;

    private float screenAlpha = 0.0F;
    private Screen targetScreen = null;

    /** Плоская палитра меню: единственный фон кнопок — белый пиксельной прозрачностью. */
    private static final int CARD_BG = ColorUtil.rgba(8, 9, 12, 150);
    private static final int CARD_OUTLINE = ColorUtil.rgba(255, 255, 255, 22);
    private static final int BTN_BG = ColorUtil.rgba(255, 255, 255, 8);
    private static final int BTN_BG_HOVER = ColorUtil.rgba(255, 255, 255, 20);

    private static final float COL_X = 32.0F;
    private static final float COL_W = 196.0F;
    private static final float BTN_H = 32.0F;
    private static final float BTN_GAP = 6.0F;
    private static final float CARD_PAD = 9.0F;
    private static final float LOGO_H = 62.0F;

    private record MenuButton(String label, IconUse icon) {}

    private static final MenuButton[] MENU = {
            new MenuButton("Single Player", IconUse.HOME),
            new MenuButton("Multi Player", IconUse.WORLD),
            new MenuButton("Alt Manager", IconUse.PERSONS),
            new MenuButton("Settings", IconUse.GEAR),
            new MenuButton("Quit", IconUse.EXIT)
    };

    private final float[] buttonHoverAnims = new float[MENU.length];

    public CustomTitleScreen() {
        super(Component.literal("Main Menu"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Ванильное меню не закрывается по ESC — иначе из главного экрана попадал бы в пустоту. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** Верх карточки меню: весь блок (лого + кнопки) центрирован по высоте экрана. */
    private float menuTop() {
        float block = LOGO_H + CARD_PAD * 2.0F
                + MENU.length * BTN_H + (MENU.length - 1) * BTN_GAP;
        // 58 — ниже аккаунт-плашки в левом углу, чтобы на маленьком окне блок не залезал на неё.
        return Math.max(58.0F, (this.height - block) / 2.0F);
    }

    @Override
    protected void init() {
        super.init();
        AccountManager.getInstance().applyActiveSession();
        this.screenAlpha = 0.0F;
        this.targetScreen = null;
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

        RenderExtend.enter2D(null, extractor, null);
        try {
            Render2DUtil.beginFrame();

            drawBackdrop(screenWidth, screenHeight);

            renderMenu(mouseX, mouseY);
            renderTopLeftAccount();

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

    private void renderMenu(int mouseX, int mouseY) {
        float cardY = menuTop() + LOGO_H;
        float cardH = CARD_PAD * 2.0F + MENU.length * BTN_H + (MENU.length - 1) * BTN_GAP;

        drawLogo(COL_X, menuTop());

        Render2D.drawRoundedRect(COL_X, cardY, COL_W, cardH, 12.0F, CARD_BG);
        Render2D.drawRoundedOutline(COL_X, cardY, COL_W, cardH, 12.0F, 1.0F, CARD_OUTLINE);

        boolean frozen = accountModalOpen || targetScreen != null;
        float bx = COL_X + CARD_PAD;
        float bw = COL_W - CARD_PAD * 2.0F;
        float y = cardY + CARD_PAD;

        for (int i = 0; i < MENU.length; i++) {
            boolean hovered = !frozen && mouseX >= bx && mouseX <= bx + bw && mouseY >= y && mouseY <= y + BTN_H;
            buttonHoverAnims[i] = Mth.clamp(buttonHoverAnims[i] + (hovered ? 0.14F : -0.14F), 0.0F, 1.0F);
            drawMenuButton(MENU[i], bx, y, bw, buttonHoverAnims[i]);
            y += BTN_H + BTN_GAP;
        }
    }

    /**
     * Кнопка меню: плоская подложка, тонкая акцентная рамка и штрих у левого края на
     * наведении, иконка и подпись сдвигаются на пару пикселей. Никаких градиентов —
     * читаемость даёт карточка, а не переливание фона.
     */
    private void drawMenuButton(MenuButton item, float x, float y, float w, float anim) {
        int accent = Theme.getAccentColor();

        Render2D.drawRoundedRect(x, y, w, BTN_H, 8.0F, ColorUtil.lerp(BTN_BG, BTN_BG_HOVER, anim));
        if (anim > 0.01F) {
            Render2D.drawRoundedOutline(x, y, w, BTN_H, 8.0F, 1.0F,
                    ColorUtil.multiplyAlpha(accent, 0.6F * anim));
            float barH = (BTN_H - 14.0F) * anim;
            Render2D.drawRoundedRect(x + 3.0F, y + (BTN_H - barH) / 2.0F, 2.5F, barH, 1.25F,
                    ColorUtil.multiplyAlpha(accent, anim));
        }

        float slide = anim * 2.5F;
        float iconH = 12.0F;
        float ix = x + 12.0F + slide;
        Fonts.drawIconFitted(item.icon(), ix, y + BTN_H / 2.0F, iconH,
                ColorUtil.lerp(Theme.TEXT_MUTED, accent, anim));

        float size = 11.5F;
        float tx = ix + Fonts.iconFittedWidth(item.icon(), iconH) + 9.0F;
        Render2D.pushScissor(tx, y, x + w - tx - 8.0F, BTN_H);
        Fonts.drawString(Fonts.SF_MEDIUM, Localization.get(item.label()), tx,
                y + (BTN_H - Fonts.SF_MEDIUM.textHeight(size)) / 2.0F + 0.5F, size,
                ColorUtil.lerp(Theme.TEXT_MAIN, ColorUtil.WHITE, anim));
        Render2D.popScissor();
    }

    private void drawLogo(float x, float y) {
        float nameSize = 26.0F;
        float baseline = y + 2.0F;
        Fonts.drawString(Fonts.SF_MEDIUM, Info.NAME, x, baseline, nameSize, ColorUtil.WHITE);

        float ruleY = baseline + Fonts.SF_MEDIUM.textHeight(nameSize) + 3.0F;
        Render2D.drawRoundedRect(x + 1.0F, ruleY,
                Math.min(Fonts.SF_MEDIUM.getWidth(Info.NAME, nameSize), 72.0F), 2.5F, 1.25F,
                Theme.getAccentColor());

        float subSize = 10.0F;
        Fonts.drawString(Fonts.SF_MEDIUM, Info.Version + "   ·   Minecraft 26.3", x + 1.0F,
                ruleY + 9.0F, subSize, Theme.TEXT_MUTED);
    }

    private void renderTopLeftAccount() {
        String curUser = this.minecraft.getUser().getName();
        float fontSize = 10.0F;
        float iconSize = 16.0F;
        float paddingX = 8.0F;
        float paddingY = 5.0F;
        float totalW = paddingX * 2.0F + iconSize + 7.0F + Fonts.SF_MEDIUM.getWidth(curUser, fontSize);
        float totalH = iconSize + paddingY * 2.0F;

        float x = COL_X;
        float y = 14.0F;

        Render2D.drawRoundedRect(x, y, totalW, totalH, 8.0F, CARD_BG);
        Render2D.drawRoundedOutline(x, y, totalW, totalH, 8.0F, 1.0F, CARD_OUTLINE);

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + curUser).getBytes(StandardCharsets.UTF_8));
        Identifier skin = DefaultPlayerSkin.get(uuid).body().texturePath();
        Render2D.drawHead(skin, x + paddingX, y + paddingY, iconSize, 3.0F);

        Fonts.drawString(Fonts.SF_MEDIUM, curUser, x + paddingX + iconSize + 7.0F,
                y + (totalH - Fonts.SF_MEDIUM.textHeight(fontSize)) / 2.0F + 0.5F, fontSize, Theme.TEXT_MAIN);
    }

    /**
     * Фон меню: сплошная база с лёгким тёплым сдвигом вниз по цвету и мягкие акцентные
     * засветы за карточками. Всё рисуется примитивами мода — картинок в этом экране нет.
     */
    private void drawBackdrop(int screenWidth, int screenHeight) {
        Render2D.drawGradientRound(0.0F, 0.0F, screenWidth, screenHeight, 0.0F,
                BACKDROP, BACKDROP,
                ColorUtil.rgba(18, 13, 11, 255), ColorUtil.rgba(11, 11, 16, 255));

        int accent = Theme.getAccentColor();

        float top = menuTop();
        float blockH = LOGO_H + CARD_PAD * 2.0F + MENU.length * BTN_H + (MENU.length - 1) * BTN_GAP;
        Render2D.drawShadow(COL_X, top, COL_W, blockH, 12.0F, 30.0F,
                ColorUtil.multiplyAlpha(accent, 0.13F));

        Render2D.drawShadow(screenWidth * 0.74F, screenHeight * 0.16F, 96.0F, 96.0F, 48.0F, 72.0F,
                ColorUtil.multiplyAlpha(accent, 0.07F));
    }

    private void renderAccountModal(int screenWidth, int screenHeight, int mouseX, int mouseY, float alpha) {
        Render2D.drawRect(0, 0, screenWidth, screenHeight, ColorUtil.multiplyAlpha(0x88000000, alpha));

        float modalW = 270.0F;
        float modalH = 310.0F;
        float modalX = (screenWidth - modalW) / 2.0F;
        float modalY = (screenHeight - modalH) / 2.0F;

        Render2D.drawRoundedRect(modalX, modalY, modalW, modalH, 8.0F, ColorUtil.multiplyAlpha(0xEE0A0B0F, alpha));
        Render2D.drawRoundedOutline(modalX, modalY, modalW, modalH, 8.0F, 0.8F,
                ColorUtil.multiplyAlpha(0xFF2D3D53, alpha));

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

        float clickX = COL_X + CARD_PAD;
        float clickW = COL_W - CARD_PAD * 2.0F;
        float firstY = menuTop() + LOGO_H + CARD_PAD;

        for (int i = 0; i < MENU.length; i++) {
            float y = firstY + i * (BTN_H + BTN_GAP);
            if (mouseX >= clickX && mouseX <= clickX + clickW && mouseY >= y && mouseY <= y + BTN_H) {
                switch (i) {
                    case 0 -> switchScreen(new SelectWorldScreen(this));
                    case 1 -> switchScreen(new JoinMultiplayerScreen(this));
                    case 2 -> {
                        this.accountModalOpen = true;
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
