package pub.godweer.kitty.display.mainmenu.popup;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.hud.Notifications;
import pub.godweer.hud.ServerGui;
import pub.godweer.kitty.display.mainmenu.PanelRefractions;
import pub.godweer.kitty.sosiski.friend.FriendManager;

import java.util.ArrayList;
import java.util.List;

import static pub.godweer.IMinecraft.mc;

/**
 * Окно друзей: список, добавление по имени, добавление игрока под прицелом и удаление.
 * Друзей обходят боевые модули, поэтому список должен быть виден прямо в гуи.
 */
public class FriendsModal implements Modal {

    private static final float W = 330.0F;
    private static final float H = 250.0F;
    private static final float PAD = 14.0F;
    private static final float ROW = 24.0F;
    private static final float GAP = 4.0F;
    private static final float BTN = 18.0F;
    private static final float FIELD_H = 20.0F;

    private final ModalShell.Field nameField = new ModalShell.Field();
    private final List<String> friends = new ArrayList<>();

    private int scroll = 0;
    private int visibleRows = 1;
    private boolean finished = false;

    private void refresh() {
        friends.clear();
        for (String name : FriendManager.getInstance().getFriends()) {
            friends.add(name);
        }
        friends.sort(String.CASE_INSENSITIVE_ORDER);
        if (visibleRows > 0) {
            scroll = Math.max(0, Math.min(scroll, friends.size() - visibleRows));
        }
    }

    private void add(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            Notifications.push("Введите имя игрока");
            return;
        }
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        if (FriendManager.getInstance().isFriend(name)) {
            Notifications.push(name + " уже в друзьях");
            nameField.clear();
            refresh();
            return;
        }
        FriendManager.getInstance().addFriend(name);
        nameField.clear();
        refresh();
        Notifications.push(name + " — друг");
    }

    private void addTarget() {
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Player other)) {
            Notifications.push("Под прицелом никто не стоит");
            return;
        }
        add(other.getGameProfile().name());
    }

    @Override
    public void render(int mouseX, int mouseY, float screenWidth, float screenHeight, float alpha) {
        if (alpha <= 0.01F) return;

        ModalShell.Window win = ModalShell.centered(screenWidth, screenHeight, W, H);
        refresh();

        ModalShell.drawWindow(win, "Друзья", friends.size() + " шт.", alpha, mouseX, mouseY);

        float innerX = win.x() + PAD;
        float innerW = win.w() - PAD * 2.0F;

        // ----- ввод имени + добавление -----
        float topY = win.y() + 32.0F;
        float addW = 20.0F;
        float targetW = 96.0F;
        float fieldW = innerW - (addW + targetW + GAP * 2.0F);

        nameField.render(innerX, topY, fieldW, FIELD_H, "Имя игрока", alpha);
        float cursor = innerX + fieldW + GAP;
        ModalShell.iconButton(cursor, topY + (FIELD_H - addW) / 2.0F, addW, ModalShell.Icon.ADD,
                ModalShell.over(cursor, topY, addW, FIELD_H, mouseX, mouseY), false, alpha);
        cursor += addW + GAP;
        ModalShell.button(cursor, topY, targetW, FIELD_H, "Добавить цель", true,
                ModalShell.over(cursor, topY, targetW, FIELD_H, mouseX, mouseY), alpha);

        // ----- постраничная навигация -----
        float headerY = topY + FIELD_H + 6.0F;
        boolean canUp = scroll > 0;
        boolean canDown = scroll + visibleRows < friends.size();
        float downX = win.x() + win.w() - PAD - BTN;
        float upX = downX - BTN - 4.0F;
        ModalShell.iconButton(upX, headerY, BTN, ModalShell.Icon.UP,
                canUp && ModalShell.over(upX, headerY, BTN, BTN, mouseX, mouseY), false, alpha);
        ModalShell.iconButton(downX, headerY, BTN, ModalShell.Icon.DOWN,
                canDown && ModalShell.over(downX, headerY, BTN, BTN, mouseX, mouseY), false, alpha);
        Fonts.drawString(Fonts.SF_MEDIUM, "Не бьём, не таргетим", innerX,
                headerY + (BTN - Fonts.SF_MEDIUM.textHeight(ModalShell.SMALL)) / 2.0F,
                ModalShell.SMALL, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));

        // ----- список -----
        float listTop = headerY + BTN + 6.0F;
        float listBottom = win.y() + win.h() - 12.0F;
        visibleRows = Math.max(1, (int) ((listBottom - listTop + GAP) / (ROW + GAP)));
        scroll = Math.max(0, Math.min(scroll, friends.size() - visibleRows));

        Render2D.pushScissor(innerX, listTop, innerW, listBottom - listTop);
        for (int i = scroll; i < Math.min(friends.size(), scroll + visibleRows); i++) {
            String name = friends.get(i);
            float rowY = listTop + (i - scroll) * (ROW + GAP);
            boolean hovered = ModalShell.over(innerX, rowY, innerW, ROW, mouseX, mouseY);

            Render2D.drawRoundedRect(innerX, rowY, innerW, ROW, 4.0F,
                    ColorUtil.multiplyAlpha(hovered ? 0xFF1D2030 : 0xFF15171F, alpha));

            float removeX = innerX + innerW - BTN - 2.0F;
            Fonts.drawString(Fonts.SF_MEDIUM, ServerGui.ellipsize(name, removeX - innerX - 14.0F, ModalShell.LABEL),
                    innerX + 10.0F, rowY + (ROW - Fonts.SF_MEDIUM.textHeight(ModalShell.LABEL)) / 2.0F + 0.35F,
                    ModalShell.LABEL, ColorUtil.multiplyAlpha(Theme.TEXT_MAIN, alpha));

            ModalShell.iconButton(removeX, rowY + (ROW - BTN) / 2.0F, BTN, ModalShell.Icon.REMOVE,
                    ModalShell.over(removeX, rowY, BTN, ROW, mouseX, mouseY), false, alpha);
        }
        Render2D.popScissor();

        if (friends.isEmpty()) {
            Fonts.drawString(Fonts.SF_MEDIUM, "Список пуст", innerX, listTop + 6.0F,
                    ModalShell.LABEL, ColorUtil.multiplyAlpha(Theme.TEXT_MUTED, alpha));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        ModalShell.Window win = ModalShell.centered(screenW, screenH, W, H);
        float innerX = win.x() + PAD;
        float innerW = win.w() - PAD * 2.0F;

        if (ModalShell.overClose(win, mouseX, mouseY)) {
            close();
            return true;
        }

        float topY = win.y() + 32.0F;
        float addW = 20.0F;
        float targetW = 96.0F;
        float fieldW = innerW - (addW + targetW + GAP * 2.0F);

        if (nameField.mouseClicked(mouseX, mouseY, innerX, topY, fieldW, FIELD_H)) return true;

        float cursor = innerX + fieldW + GAP;
        if (ModalShell.over(cursor, topY, addW, FIELD_H, mouseX, mouseY)) {
            add(nameField.value());
            return true;
        }
        cursor += addW + GAP;
        if (ModalShell.over(cursor, topY, targetW, FIELD_H, mouseX, mouseY)) {
            addTarget();
            return true;
        }

        float headerY = topY + FIELD_H + 6.0F;
        float downX = win.x() + win.w() - PAD - BTN;
        float upX = downX - BTN - 4.0F;
        if (ModalShell.over(upX, headerY, BTN, BTN, mouseX, mouseY)) {
            scroll = Math.max(0, scroll - 1);
            return true;
        }
        if (ModalShell.over(downX, headerY, BTN, BTN, mouseX, mouseY)) {
            scroll = Math.min(Math.max(0, friends.size() - visibleRows), scroll + 1);
            return true;
        }

        float listTop = headerY + BTN + 6.0F;
        for (int i = scroll; i < Math.min(friends.size(), scroll + visibleRows); i++) {
            float rowY = listTop + (i - scroll) * (ROW + GAP);
            if (!ModalShell.over(innerX, rowY, innerW, ROW, mouseX, mouseY)) continue;

            float removeX = innerX + innerW - BTN - 2.0F;
            if (ModalShell.over(removeX, rowY, BTN, ROW, mouseX, mouseY)) {
                String name = friends.get(i);
                FriendManager.getInstance().removeFriend(name);
                refresh();
                Notifications.push(name + " больше не друг");
            }
            return true;
        }

        nameField.blur();
        return true;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (nameField.isFocused()) {
                nameField.blur();
                return true;
            }
            close();
            return true;
        }
        if (nameField.keyPressed(keyCode)) return true;
        if (keyCode == InputConstants.KEY_RETURN) {
            add(nameField.value());
            return true;
        }
        return true;
    }

    @Override
    public void charTyped(int codePoint) {
        nameField.charTyped(codePoint);
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    private void close() {
        this.finished = true;
        PanelRefractions.openModal(null);
    }
}
