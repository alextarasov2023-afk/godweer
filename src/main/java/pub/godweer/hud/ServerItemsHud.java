package pub.godweer.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import pub.godweer.Client;
import pub.godweer.IMinecraft;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render2DUtil;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.module.impl.misc.ServerHelper;
import pub.godweer.kitty.module.settings.impl.BindSetting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ряд пилюль с иконкой расходника и его биндом — чтобы не держать в голове, что на что висит.
 * Пилюли — те же кейкапы, что в Binds-HUD: акцентная подложка + тонкий акцентный контур.
 */
public final class ServerItemsHud extends HudElement implements IMinecraft {

    private static final float H = 22.0F;
    private static final float RADIUS = 5.0F;
    private static final float GAP = 5.0F;
    private static final float PAD_X = 5.0F;
    private static final float ICON = 14.0F;
    private static final float TEXT = 7.5F;

    private final Map<String, Float> anim = new HashMap<>();
    private final Map<String, Float> flash = new HashMap<>();
    private final Map<String, Integer> lastCount = new HashMap<>();

    public ServerItemsHud() {
        super("server_items", "Server Items", 120.0F, 320.0F, 200.0F, H);
    }

    private static ServerHelper module() {
        Client client = Client.getInstance();
        return client == null || client.moduleManager == null ? null : client.moduleManager.serverHelper;
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        ServerHelper helper = module();
        return helper != null && helper.isEnabled() && mc.player != null;
    }

    @Override
    public void draw(Render2DEvent event) {
        ServerHelper helper = module();
        List<ServerHelper.HudItem> entries = helper == null ? List.of() : helper.hudEntries();
        GuiGraphicsExtractor extractor = event.getGuiGraphicsExtractor();
        int accent = Theme.getAccentColor();

        float totalW = 0.0F;
        for (ServerHelper.HudItem item : entries) {
            float a = anim.getOrDefault(item.key(), 0.0F);
            totalW += (pillWidth(bindLabel(item)) + GAP) * a;
        }
        if (totalW > GAP) totalW -= GAP;
        this.width = Math.max(40.0F, totalW);
        this.height = H;

        float cx = x;
        for (ServerHelper.HudItem item : entries) {
            int count = helper == null ? 0 : helper.countItem(item.key());
            String label = bindLabel(item);
            float w = pillWidth(label);

            float a = anim.getOrDefault(item.key(), 0.0F);
            a += (1.0F - a) * 0.24F;
            anim.put(item.key(), a);
            if (a <= 0.03F) continue;

            float previous = flash.getOrDefault(item.key(), 0.0F);
            int before = lastCount.getOrDefault(item.key(), count);
            // Вспышка только на реальном расходе: подобрали — молчит.
            float f = previous + (count < before ? 1.0F : 0.0F - previous) * 0.3F;
            flash.put(item.key(), f);
            lastCount.put(item.key(), count);

            float pw = w * a;
            boolean empty = count <= 0;

            Render2D.drawRoundedRectWithOutline(cx, y, pw, H, RADIUS,
                    ColorUtil.withAlpha(accent, Math.round((empty ? 12 : 30) * a)),
                    0.65F,
                    ColorUtil.withAlpha(accent, Math.round((empty ? 45 : 95) * a)));

            if (f > 0.02F) {
                Render2D.drawRoundedOutline(cx - 1.0F, y - 1.0F, pw + 2.0F, H + 2.0F, RADIUS + 1.0F, 1.0F,
                        ColorUtil.withAlpha(accent, Math.round(200 * f)));
            }

            if (extractor != null && pw > ICON + PAD_X * 2.0F) {
                Render2DUtil.flush();
                float iconX = cx + PAD_X + (ICON - 16.0F) / 2.0F;
                float iconY = y + (H - ICON) / 2.0F + (ICON - 16.0F) / 2.0F;
                extractor.pose().pushMatrix();
                extractor.pose().translate(iconX, iconY);
                float sc = ICON / 16.0F;
                extractor.pose().scale(sc, sc);
                extractor.item(item.icon(), 0, 0);
                extractor.pose().popMatrix();
                if (empty) {
                    Render2D.drawRoundedRect(cx + PAD_X - 1.0F, y + (H - ICON) / 2.0F - 1.0F,
                            ICON + 2.0F, ICON + 2.0F, 3.0F, ColorUtil.rgba(10, 11, 17, Math.round(160 * a)));
                }
            }

            Fonts.drawString(Fonts.SF_MEDIUM, label, cx + PAD_X + ICON + 4.0F,
                    y + (H - Fonts.SF_MEDIUM.textHeight(TEXT)) / 2.0F + 0.35F, TEXT,
                    ColorUtil.multiplyAlpha(empty ? HudStyle.TEXT_DIM : HudStyle.TEXT_PRIMARY, a));

            cx += pw + GAP;
        }
    }

    private static String bindLabel(ServerHelper.HudItem item) {
        BindSetting bind = item.bind();
        return bind == null || !bind.isBound() ? "—" : bind.getDisplayValue();
    }

    private static float pillWidth(String label) {
        return PAD_X + ICON + 4.0F + Fonts.SF_MEDIUM.getWidth(label, TEXT) + PAD_X;
    }
}
