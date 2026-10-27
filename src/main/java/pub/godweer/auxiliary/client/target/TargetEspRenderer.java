package pub.godweer.auxiliary.client.target;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import pub.godweer.auxiliary.client.Annstable;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.module.impl.render.TargetEsp;

/**
 * Target ESP.
 * Marker: rotating 2D sprite in the GUI pass, centered on the target.
 * Lightning: bolts crawling around the target in the world pass.
 * Both flash red while the target is taking damage (Target HUD rules).
 */
public final class TargetEspRenderer {

    public static final Identifier MARKER_TEXTURE = Identifier.fromNamespaceAndPath("godweer", "images/world/esp/marker.png");

    // Мировой радиус маркера: спрайт соответствует "кружку" этого размера в мире.
    // Экранный размер считается честной проекцией, дальше/ближе — только перспектива.
    private static final float MARKER_WORLD_RADIUS = 0.9F;

    // damage flash — same colour and timing as Target HUD
    private static final int HURT_TINT = 0xFFFF4040;

    // world-pass renderers — вызываются из TargetEsp.onRender3D (Render3DEvent)
    private final LightningEspRenderer lightning = new LightningEspRenderer();

    // shared hurt-flash state (Target HUD style)
    private LivingEntity lastHurtTarget;
    private int lastHurtTime;
    private float blush;
    private long lastFrameNanos;

    public void render(Render2DEvent event, TargetEsp module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || event.getGuiGraphicsExtractor() == null) return;

        LivingEntity target = module.resolveTarget();
        if (target == null) return;

        float tickDelta = event.getDeltaTracker() != null
                ? event.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 1.0F;

        if (module.usesMarker()) {
            renderMarker(event, mc, target, tickDelta, hurtBlend(module.getAccentColor(), target));
        }
    }

    private void renderMarker(Render2DEvent event, Minecraft mc, LivingEntity target, float tickDelta,
                              int color) {
        double height = Math.max(0.4D, target.getBbHeight());
        Vec3 center = Render3DUtil.interpolatedPosition(target, tickDelta).add(0.0D, height * 0.5D, 0.0D);
        Render3DUtil.ScreenPoint sp = Render3DUtil.projectToScreen(mc, center);
        if (sp == null || mc.gameRenderer == null) return;

        float dist = (float) Math.max(0.1D, mc.gameRenderer.mainCamera().position().distanceTo(center));
        float fade = Mth.clamp(1.0F - dist / 70.0F, 0.2F, 1.0F);
        int tint = ColorUtil.withAlpha(color, Math.round(ColorUtil.alpha(color) * fade));

        float time = (float) (System.currentTimeMillis() % 40000L) / 1000.0F;
        float size = projectedMarkerSize(mc, center);
        if (size <= 1.0F) return;
        size *= 1.0F + 0.04F * Mth.sin(time * 2.0F);

        // Spins a full circle from time to time, then turns back.
        float rotation = Mth.sin(time * 0.5F) * 360.0F;

        Matrix3x2fStack pose = event.getGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(sp.x(), sp.y());
        pose.rotate((float) Math.toRadians(rotation));
        Render2D.drawTexture(MARKER_TEXTURE, -size / 2.0F, -size / 2.0F, size, size, 0.0F, tint);
        pose.popMatrix();
    }

    /**
     * Честный экранный размер маркера: проецируем центр цели и тот же центр,
     * смещённый на фиксированный мировой радиус (пробу — горизонтально вбок
     * от камеры и вертикально), и берём максимальный пиксельный размах.
     * Размер убывает строго с дистанцией и совпадает по перспективе
     * со всей 3D-сценой: тот же FOV, тот же aspect, тот же масштаб окна.
     */
    private float projectedMarkerSize(Minecraft mc, Vec3 center) {
        Render3DUtil.ScreenPoint c = Render3DUtil.projectToScreen(mc, center);
        if (c == null) return 0.0F;

        Camera camera = mc.gameRenderer.mainCamera();
        double yawRad = Math.toRadians(camera.yRot());
        // горизонтальная проба: при взгляде вдоль неё стабильно работает
        // вертикальная, поэтому берём максимум из двух проб
        double ox = Math.cos(yawRad);
        double oz = Math.sin(yawRad);

        float horizontal = pairSize(c,
                Render3DUtil.projectToScreen(mc, center.add(ox * MARKER_WORLD_RADIUS, 0.0D, oz * MARKER_WORLD_RADIUS)));
        float vertical = pairSize(c,
                Render3DUtil.projectToScreen(mc, center.add(0.0D, MARKER_WORLD_RADIUS, 0.0D)));
        float size = Math.max(horizontal, vertical);
        if (size <= 0.0F) return 0.0F;

        // Пол опустошения маркера — не больше четверти экрана при сверхблизкой цели.
        float cap = mc.getWindow().getGuiScaledHeight() * 0.28F;
        return Mth.clamp(size, 2.0F, cap);
    }

    private float pairSize(Render3DUtil.ScreenPoint a, Render3DUtil.ScreenPoint b) {
        if (b == null) return 0.0F;
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Мировой проход (Render3DEvent): молнии.
     * Маркер (2D) живёт в render() через Render2DEvent.
     * Кубы (Cubes) рисуются своим HaloTargetRenderer из TargetEsp.onRender3D.
     */
    public void renderWorld3D(TargetEsp module, float tickDelta, Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) {
            this.lightning.release();
            return;
        }
        if (module.usesLightning()) {
            this.lightning.render(module, tickDelta);
        } else {
            this.lightning.release();
        }
    }

    // ---- damage flash (Target HUD rules) ----

    private float updateBlush(LivingEntity target) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? (1.0F / 60.0F) : (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0.0F, 0.1F);

        if (target != lastHurtTarget) {
            lastHurtTarget = target;
            lastHurtTime = 0;
            blush = 0.0F;
        }
        int hurt = Math.max(0, target.hurtTime);
        if (hurt > lastHurtTime) {
            blush = 1.0F;
        }
        lastHurtTime = hurt;
        blush = Math.max(blush - dt * 2.4F, Annstable.factor(target));
        blush = Mth.clamp(blush, 0.0F, 1.0F);
        return blush;
    }

    private int hurtBlend(int color, LivingEntity target) {
        float b = updateBlush(target);
        return b > 0.02F ? ColorUtil.lerp(color, HURT_TINT, b) : color;
    }

    public void release() {
        lastHurtTarget = null;
        lastHurtTime = 0;
        blush = 0.0F;
        lastFrameNanos = 0L;
        this.lightning.release();
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }
}