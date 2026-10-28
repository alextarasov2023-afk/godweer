package pub.godweer.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.ChatScreen;
import pub.godweer.IMinecraft;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render2DUtil;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.MouseInputEvent;
import pub.godweer.event.list.Render2DEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders HUD elements and provides interactive editing while chat is open:
 * - Drag with LMB to move (with smart center & edge alignment guides)
 * - Scroll wheel directly over any hovered HUD element to resize (55%..220%)
 * - Drag the bottom-right corner handle with LMB to smoothly resize
 * - Click MMB (middle mouse button) or RMB on a hovered HUD element to reset scale to 100%
 */
public final class HudManager implements IMinecraft {
    private static final HudManager INSTANCE = new HudManager();
    private static final float HANDLE_HIT_RADIUS = 7.0F;
    private static final float SNAP_THRESHOLD = 4.0F;

    private final List<HudElement> elements = new ArrayList<>();
    private final Map<String, Float> appearAnims = new HashMap<>();
    private final Map<String, Float> hoverAnims = new HashMap<>();

    private MusicHud musicHud;

    private HudElement dragged;
    private HudElement resizing;
    private float resizeStartMouseX;
    private float resizeStartMouseY;
    private float resizeStartScale;
    private float resizeBaseDiag;

    private HudElement hovered;
    private boolean snappedX;
    private boolean snappedY;

    private HudManager() {
        register(new WatermarkHud());
        register(new PlayerHud());
        register(new PotionsHud());
        register(new StaffHud());
        register(new HotkeysHud());
        register(new ServerItemsHud());
        this.musicHud = new MusicHud();
        register(musicHud);
    }

    public static HudManager getInstance() {
        return INSTANCE;
    }

    public void register(HudElement element) {
        elements.add(element);
    }

    public List<HudElement> getElements() {
        return elements;
    }

    /** @return the music HUD, which also listens for mouse clicks on its buttons */
    public MusicHud getMusicHud() {
        return musicHud;
    }

    /** Sets the hover state for music buttons based on the current cursor. */
    public void updateMusicHover() {
        // The hover state is now updated directly inside MusicHud.draw(),
        // so this method is a no-op kept for backwards compatibility.
    }

    public static boolean editMode() {
        return mc.gui != null && mc.gui.screen() instanceof ChatScreen;
    }

    public static double mouseX() {
        return mc.mouseHandler.xpos() / (double) mc.getWindow().getGuiScale();
    }

    public static double mouseY() {
        return mc.mouseHandler.ypos() / (double) mc.getWindow().getGuiScale();
    }

    private static boolean isOverResizeHandle(HudElement el, double mx, double my) {
        float hx = el.getX() + el.getScaledWidth();
        float hy = el.getY() + el.getScaledHeight();
        return Math.abs(mx - hx) <= HANDLE_HIT_RADIUS && Math.abs(my - hy) <= HANDLE_HIT_RADIUS;
    }

    private HudElement findHoveredElement(double mx, double my) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement el = elements.get(i);
            if (!el.isEnabled() || (!el.shouldRender() && !editMode())) continue;
            if (el.isHovered(mx, my) || isOverResizeHandle(el, mx, my)) {
                return el;
            }
        }
        return null;
    }

    /**
     * Direct mouse wheel scaling while hovering any HUD element in chat edit mode.
     * No keyboard keys needed.
     */
    public boolean handleScroll(double vertical) {
        if (!editMode() || vertical == 0.0D) return false;
        double mx = mouseX();
        double my = mouseY();
        HudElement target = hovered != null ? hovered : findHoveredElement(mx, my);
        if (target == null) return false;

        float step = (float) vertical * 0.05F;
        float next = Math.round((target.getScale() + step) * 20.0F) / 20.0F;
        target.setScale(next);

        clampInsideScreen(target);
        return true;
    }

    private static void clampInsideScreen(HudElement el) {
        if (mc == null || mc.getWindow() == null) return;
        float w = mc.getWindow().getGuiScaledWidth();
        float h = mc.getWindow().getGuiScaledHeight();
        float sw = el.getScaledWidth();
        float sh = el.getScaledHeight();
        el.setX(Math.max(0.0F, Math.min(Math.max(0.0F, w - sw), el.getX())));
        el.setY(Math.max(0.0F, Math.min(Math.max(0.0F, h - sh), el.getY())));
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc == null || mc.getWindow() == null) return;
        boolean edit = editMode();
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();

        snappedX = false;
        snappedY = false;

        if (!edit) {
            dragged = null;
            resizing = null;
        }

        if (resizing != null) {
            if (!resizing.isEnabled()) {
                resizing = null;
            } else {
                float dx = (float) mouseX() - resizeStartMouseX;
                float dy = (float) mouseY() - resizeStartMouseY;
                float delta = (dx + dy) / Math.max(60.0F, resizeBaseDiag);
                resizing.setScale(resizeStartScale + delta);
                clampInsideScreen(resizing);
            }
        } else if (dragged != null) {
            if (!dragged.isEnabled()) {
                dragged = null;
            } else {
                float nx = (float) mouseX() - dragged.grabDX;
                float ny = (float) mouseY() - dragged.grabDY;
                float sw = dragged.getScaledWidth();
                float sh = dragged.getScaledHeight();

                // Smart snap to screen center X / Y
                float centerTargetX = (screenW - sw) / 2.0F;
                if (Math.abs(nx - centerTargetX) <= SNAP_THRESHOLD) {
                    nx = centerTargetX;
                    snappedX = true;
                }
                float centerTargetY = (screenH - sh) / 2.0F;
                if (Math.abs(ny - centerTargetY) <= SNAP_THRESHOLD) {
                    ny = centerTargetY;
                    snappedY = true;
                }

                dragged.setX(Math.max(0.0F, Math.min(Math.max(0.0F, screenW - sw), nx)));
                dragged.setY(Math.max(0.0F, Math.min(Math.max(0.0F, screenH - sh), ny)));
            }
        }

        hovered = null;
        double mx = mouseX();
        double my = mouseY();
        if (edit) {
            if (resizing != null) {
                hovered = resizing;
            } else if (dragged != null) {
                hovered = dragged;
            } else {
                hovered = findHoveredElement(mx, my);
            }
        }

        // Draw center snap guides while dragging
        if (edit && dragged != null) {
            int accent = Theme.getAccentColor();
            if (snappedX) {
                Render2D.drawRect(screenW / 2.0F - 0.5F, 0.0F, 1.0F, screenH,
                        ColorUtil.withAlpha(accent, 130));
            }
            if (snappedY) {
                Render2D.drawRect(0.0F, screenH / 2.0F - 0.5F, screenW, 1.0F,
                        ColorUtil.withAlpha(accent, 130));
            }
        }

        for (HudElement el : elements) {
            if (!el.isEnabled()) {
                appearAnims.put(el.getId(), 0.0F);
                hoverAnims.put(el.getId(), 0.0F);
                continue;
            }
            boolean visible = el.shouldRender() || edit;
            float a = appearAnims.getOrDefault(el.getId(), 0.0F);
            a += ((visible ? 1.0F : 0.0F) - a) * 0.22F;
            appearAnims.put(el.getId(), a);
            if (a <= 0.02F) continue;

            var extractor = event.getGuiGraphicsExtractor();
            float hudScale = el.getScale();
            boolean hasHudScale = Math.abs(hudScale - 1.0F) > 0.001F;
            boolean hasAppear = a < 0.999F;
            if (extractor != null && (hasHudScale || hasAppear)) {
                Render2DUtil.flush();
                if (hasHudScale) {
                    extractor.pose().pushMatrix();
                    extractor.pose().translate(el.getX(), el.getY());
                    extractor.pose().scale(hudScale, hudScale);
                    extractor.pose().translate(-el.getX(), -el.getY());
                }
                if (hasAppear) {
                    float cx = el.getX() + el.getWidth() / 2.0F;
                    float cy = el.getY() + el.getHeight() / 2.0F;
                    float s = 0.92F + 0.08F * a;
                    extractor.pose().pushMatrix();
                    extractor.pose().translate(cx, cy + (1.0F - a) * -6.0F);
                    extractor.pose().scale(s, s);
                    extractor.pose().translate(-cx, -cy);
                    el.draw(event);
                    extractor.pose().popMatrix();
                } else {
                    el.draw(event);
                }
                if (hasHudScale) extractor.pose().popMatrix();
            } else {
                el.draw(event);
            }

            if (edit) {
                boolean isActive = (el == hovered || el == dragged || el == resizing);
                float ha = hoverAnims.getOrDefault(el.getId(), 0.0F);
                ha += ((isActive ? 1.0F : 0.0F) - ha) * 0.25F;
                hoverAnims.put(el.getId(), ha);

                int accent = Theme.getAccentColor();
                float ox = el.getX() - 1.5F;
                float oy = el.getY() - 1.5F;
                float ow = el.getScaledWidth() + 3.0F;
                float oh = el.getScaledHeight() + 3.0F;

                int borderCol = ColorUtil.lerp(
                        ColorUtil.rgba(255, 255, 255, 70),
                        ColorUtil.withAlpha(accent, 235),
                        ha
                );
                Render2D.drawRoundedOutline(ox, oy, ow, oh, 8.0F, 1.0F, borderCol);

                // Bottom-right corner resize grip handle
                float gripX = el.getX() + el.getScaledWidth();
                float gripY = el.getY() + el.getScaledHeight();
                boolean overHandle = el == resizing || (el == hovered && isOverResizeHandle(el, mx, my));
                float gripSize = overHandle ? 6.5F : 5.0F;
                int gripColor = overHandle
                        ? ColorUtil.withAlpha(accent, 255)
                        : ColorUtil.lerp(ColorUtil.rgba(220, 224, 240, 140), ColorUtil.withAlpha(accent, 240), ha);

                Render2D.drawRoundedRectWithOutline(
                        gripX - gripSize / 2.0F,
                        gripY - gripSize / 2.0F,
                        gripSize,
                        gripSize,
                        2.0F,
                        gripColor,
                        0.8F,
                        ColorUtil.rgba(10, 11, 16, 220)
                );

                // Compact floating badge above/below when hovered or resizing: "Staff • 115%"
                if (ha > 0.04F) {
                    int pct = Math.round(el.getScale() * 100.0F);
                    String badge = el.getName() + " · " + pct + "%";
                    float bFont = 6.5F;
                    float tw = Fonts.SF_MEDIUM.getWidth(badge, bFont);
                    float bw = tw + 10.0F;
                    float bh = 11.0F;
                    float bx = el.getX() + el.getScaledWidth() / 2.0F - bw / 2.0F;
                    bx = Math.max(3.0F, Math.min(screenW - bw - 3.0F, bx));
                    float by = el.getY() - bh - 3.5F;
                    if (by < 2.0F) {
                        by = el.getY() + el.getScaledHeight() + 3.5F;
                    }

                    Render2D.drawRoundedRectWithOutline(bx, by, bw, bh, 4.0F,
                            ColorUtil.rgba(12, 13, 20, Math.round(215 * ha)),
                            0.7F,
                            ColorUtil.withAlpha(accent, Math.round(140 * ha)));
                    Fonts.drawCenteredString(Fonts.SF_MEDIUM, badge, bx + bw / 2.0F,
                            by + (bh - Fonts.SF_MEDIUM.textHeight(bFont)) / 2.0F + 0.35F,
                            bFont, ColorUtil.rgba(242, 244, 252, Math.round(255 * ha)));
                }
            }
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (!editMode()) {
            dragged = null;
            resizing = null;
            return;
        }
        double mx = mouseX();
        double my = mouseY();

        if (event.getAction() == InputConstants.PRESS) {
            if (event.getButton() == InputConstants.MOUSE_BUTTON_LEFT) {
                // 1) Check bottom-right resize handle first
                for (int i = elements.size() - 1; i >= 0; i--) {
                    HudElement el = elements.get(i);
                    if (!el.isEnabled() || (!el.shouldRender() && !editMode())) continue;
                    if (isOverResizeHandle(el, mx, my)) {
                        resizing = el;
                        resizeStartMouseX = (float) mx;
                        resizeStartMouseY = (float) my;
                        resizeStartScale = el.getScale();
                        resizeBaseDiag = (float) Math.hypot(el.getWidth(), el.getHeight());
                        event.cancel();
                        return;
                    }
                }
                // 2) Otherwise drag the element
                for (int i = elements.size() - 1; i >= 0; i--) {
                    HudElement el = elements.get(i);
                    if (!el.isEnabled() || (!el.shouldRender() && !editMode())) continue;
                    if (el.isHovered(mx, my)) {
                        dragged = el;
                        el.grabDX = (float) mx - el.getX();
                        el.grabDY = (float) my - el.getY();
                        event.cancel();
                        return;
                    }
                }
            } else if (event.getButton() == InputConstants.MOUSE_BUTTON_RIGHT
                    || event.getButton() == InputConstants.MOUSE_BUTTON_MIDDLE) {
                // Right-click or Middle-click on an element resets its scale to 100%
                HudElement target = findHoveredElement(mx, my);
                if (target != null) {
                    target.setScale(1.0F);
                    clampInsideScreen(target);
                    event.cancel();
                }
            }
        } else if (event.getAction() == InputConstants.RELEASE && event.getButton() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragged = null;
            resizing = null;
        }
    }
}
