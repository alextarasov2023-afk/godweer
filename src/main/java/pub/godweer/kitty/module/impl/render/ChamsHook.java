package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.ChamsRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import pub.godweer.auxiliary.client.clients.ColorUtil;

import java.util.List;
import java.util.Map;

/**
 * Target matching, style choice and tint packing for the shader chams (see
 * LivingEntityRendererMixin and HumanoidArmorLayerMixin).
 *
 * The chams pipelines have no per-entity uniforms, so everything the shader
 * needs per frame goes through the vertex tint, which reaches the fragment
 * shader as vertexColor: rgb = colour * pulse, alpha = animation phase
 * (opacity for glass).
 *
 * Style choice is resolved once per call from the render state: a hand-picked
 * style for everyone, or for Random a style taken from the entity id so it
 * never changes while the entity lives, or for Cycle the timer position plus
 * that same id offset, so bodies do not all switch in lockstep.
 */
public final class ChamsHook {

    // Скорость фазы у каждого шейдера своя: полосам hologram нужен быстрый бег,
    // туманностям cosmos — медленное ползущее смещение.
    private static final Map<String, Float> PHASE_RATE = Map.ofEntries(
            Map.entry("glow", 0.35f),
            Map.entry("plasma", 0.30f),
            Map.entry("lava", 0.20f),
            Map.entry("hologram", 0.55f),
            Map.entry("glitch", 0.40f),
            Map.entry("aurora", 0.26f),
            Map.entry("spectrum", 0.45f),
            Map.entry("cosmos", 0.16f),
            Map.entry("toxic", 0.32f));

    private static final float DEFAULT_RATE = 0.30f;

    private static final double GHOST_MIN_DISTANCE_SQ = 2.0D * 2.0D;

    private ChamsHook() {
    }

    public static RenderType renderType(LivingEntityRenderState state) {
        Chams m = target(state);
        return m == null ? null : type(style(m, state), false);
    }

    public static RenderType throughType(LivingEntityRenderState state) {
        Chams m = target(state);
        if (m == null || !ghostAllowed(m, state)) return null;
        return type(style(m, state), true);
    }

    public static RenderType normalType(LivingEntityRenderState state) {
        Chams m = target(state);
        return m == null ? null : type(style(m, state), false);
    }

    /**
     * Рисуем ли броню этого существа вообще. Chams красит тело своим шейдером, а слой
     * брони поверх него перечитывает модель тем же шейдером — силуэт становится двойным
     * и «бронированный» читается как обычная модель. Со включённым Hide Armor слой
     * брони не подаётся ни в обычном, ни в ghost проходе: остаётся только тело.
     */
    public static boolean hidesArmor(LivingEntityRenderState state) {
        Chams m = target(state);
        return m != null && m.isHideArmor();
    }

    public static Integer tint(LivingEntityRenderState state) {
        Chams m = target(state);
        return m == null ? null : tint(m, style(m, state), false);
    }

    public static Integer throughTint(LivingEntityRenderState state) {
        Chams m = target(state);
        if (m == null || !ghostAllowed(m, state)) return null;
        return tint(m, style(m, state), true);
    }

    private static RenderType type(String style, boolean through) {
        return ChamsRenderTypes.of(style, through);
    }

    /** Имя файла шейдера для этого существа в этом кадре. */
    private static String style(Chams m, LivingEntityRenderState state) {
        if (!m.isRandom() && !m.isCycle()) return m.fixedStyle();

        List<String> styles = m.shaderStyles();
        int offset = Math.floorMod(identity(state), styles.size());
        if (m.isRandom()) return styles.get(offset);

        int step = (int) (System.currentTimeMillis() / 1000.0 / Math.max(0.5, m.getCycleSeconds()));
        return styles.get(Math.floorMod(step + offset, styles.size()));
    }

    /** Стабильный номер существа: id игрока, иначе тип моба. */
    private static int identity(EntityRenderState state) {
        if (state instanceof AvatarRenderState avatar) return avatar.id;
        return state.entityType != null ? System.identityHashCode(state.entityType) : 0;
    }

    private static int tint(Chams m, String style, boolean ghost) {
        if ("glass".equals(style)) return glassTint(m, ghost);
        return animatedTint(m, style, ghost);
    }

    private static int glassTint(Chams m, boolean ghost) {
        int c = m.getColor();
        float k = ghost ? m.getGhostStrength() : 1.0F;
        // Floored opacity: below ~0.4 of glass the body is indistinguishable from
        // the background and looks like the entity disappeared when the camera
        // turns. The ghost keeps a lower floor so it stays weaker than the solid.
        float alpha = Math.max(ghost ? 0.34F : 0.5F, m.getOpacity()) * (ghost ? 0.55F : 1.0F);
        return ColorUtil.rgba(
                scale(ColorUtil.red(c), k),
                scale(ColorUtil.green(c), k),
                scale(ColorUtil.blue(c), k),
                Math.round(alpha * 255.0F));
    }

    private static int animatedTint(Chams m, String style, boolean ghost) {
        double t = System.currentTimeMillis() / 1000.0;
        float dim = ghost ? m.getGhostStrength() : 1.0F;
        float pulse = (0.8F + 0.2F * (float) Math.sin(t * 3.4)) * m.getIntensity() * dim;
        float rate = PHASE_RATE.getOrDefault(style, DEFAULT_RATE);
        int phase = (int) Math.round(((t * m.getSpeed() * rate) % 1.0) * 255.0);
        int c = m.getColor();
        return ColorUtil.rgba(
                scale(ColorUtil.red(c), pulse),
                scale(ColorUtil.green(c), pulse),
                scale(ColorUtil.blue(c), pulse),
                phase & 0xFF);
    }

    private static int scale(int channel, float k) {
        return Math.max(0, Math.min(255, Math.round(channel * k)));
    }

    /**
     * The ghost pass ignores depth, so a painted body that the camera is inside
     * (or pressed right against) floods the screen and everything looks gone.
     * Such close entities keep only the depth-tested pass.
     */
    private static boolean ghostAllowed(Chams m, LivingEntityRenderState state) {
        return m.isThroughWalls() && state.distanceToCameraSq > GHOST_MIN_DISTANCE_SQ;
    }

    private static Chams target(LivingEntityRenderState state) {
        Chams m = Chams.INSTANCE;
        if (m == null || !m.isEnabled() || state == null) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;

        float range = m.getRange();
        if (state.distanceToCameraSq > (double) range * range) return null;

        if (state instanceof AvatarRenderState avatar) {
            if (!m.showPlayers()) return null;
            // AvatarRenderState.id is the rendered entity's id (Avatar.getId()),
            // so this identifies the local player's own render state.
            boolean self = avatar.id == mc.player.getId();
            // First person: the own model is off-screen anyway, so painting it only
            // risks the shell covering the view. Third person (F5) keeps Self.
            if (self && mc.options.getCameraType().isFirstPerson()) return null;
            return m.showSelf() || !self ? m : null;
        }
        return m.showMobs() ? m : null;
    }
}
