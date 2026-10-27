package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.client.target.GhostTargetRenderer;
import pub.godweer.auxiliary.client.target.HaloTargetRenderer;
import pub.godweer.auxiliary.client.target.RingTargetRenderer;
import pub.godweer.auxiliary.client.target.TargetEspRenderer;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.enjoy.event.list.Render3DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.impl.combat.KillAura;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * Target ESP: highlights the target with a themed 2D marker in its center,
 * lightning bolts, scattered cubes, souls, or a ring sweeping the hitbox from
 * top to bottom with a motion trail (Ring).
 */
public final class TargetEsp extends Module {

    public static final String MODE_MARKER = "Marker";
    public static final String MODE_LIGHTNING = "Lightning";
    public static final String MODE_CUBES = "Cubes";
    public static final String MODE_GHOST = "Ghost";
    public static final String MODE_RING = "Ring";

    private static final long TARGET_HOLD_MS = 350L;
    private static final double TARGET_RANGE = 32.0D;
    private static final double TARGET_RANGE_SQ = TARGET_RANGE * TARGET_RANGE;

    public final ModeSetting mode = mode("Режим", MODE_MARKER, MODE_MARKER, MODE_LIGHTNING, MODE_CUBES, MODE_GHOST, MODE_RING);

    public final CheckBox themeColor = checkbox("Color From Theme", true);
    public final ColorSetting color = color("Color", ColorUtil.rgba(255, 170, 45, 255)).visible(() -> !themeColor.getValue());

    public final SliderSetting lightningCount = slider("Молнии: Количество", 3.0f, 1.0f, 8.0f, 1.0f).visible(this::usesLightning);
    public final SliderSetting lightningWidth = slider("Молнии: Толщина", 1.0f, 0.4f, 2.5f, 0.1f).visible(this::usesLightning);
    public final CheckBox lightningHurt = checkbox("Молнии: Вспышка при уроне", true).visible(this::usesLightning);

    public final SliderSetting ghostSize = slider("Души: Размер", 1.35f, 0.5f, 3.0f, 0.05f).visible(this::usesGhost);

    public final SliderSetting ringSpeed = slider("Кольцо: Скорость", 1.0f, 0.2f, 4.0f, 0.05f).visible(this::usesRing);
    public final SliderSetting ringTrail = slider("Кольцо: Шлейф (сек)", 0.35f, 0.03f, 1.1f, 0.01f).visible(this::usesRing);
    public final SliderSetting ringThickness = slider("Кольцо: Толщина", 0.07f, 0.01f, 0.3f, 0.01f).visible(this::usesRing);
    public final SliderSetting ringRadius = slider("Кольцо: Отступ", 0.2f, 0.0f, 1.0f, 0.02f).visible(this::usesRing);
    public final SliderSetting ringSegments = slider("Кольцо: Сегменты", 48.0f, 12.0f, 96.0f, 1.0f).visible(this::usesRing);

    private final TargetEspRenderer renderer = new TargetEspRenderer();
    private final HaloTargetRenderer halo = new HaloTargetRenderer();
    private final GhostTargetRenderer ghost = new GhostTargetRenderer();
    private final RingTargetRenderer ring = new RingTargetRenderer();

    private LivingEntity heldTarget;
    private long heldAt;

    public TargetEsp() {
        super("TargetEsp", "Подсвечивает цель маркером, душами, молниями или кольцом со шлейфом", Category.RENDER);
    }

    public LivingEntity resolveTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return null;

        LivingEntity live = liveTarget(mc);
        long now = System.currentTimeMillis();
        if (live != null) {
            this.heldTarget = live;
            this.heldAt = now;
            return live;
        }

        // mc.hitResult обновляется раз в тик: при стрейфе цель выпадает на 1-2 тика.
        // Держим того же моба короткое окно, чтобы эффект не мигал и оставался на нём.
        LivingEntity held = this.heldTarget;
        if (held != null && now - this.heldAt <= TARGET_HOLD_MS && valid(held, mc)) {
            return held;
        }
        this.heldTarget = null;
        return null;
    }

    private LivingEntity liveTarget(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
                && valid(le, mc)) {
            return le;
        }
        KillAura killAura = KillAura.INSTANCE;
        if (killAura != null && killAura.isEnabled() && valid(killAura.getTarget(), mc)) {
            return killAura.getTarget();
        }
        return null;
    }

    private static boolean valid(LivingEntity entity, Minecraft mc) {
        return entity != null && entity.isAlive() && !entity.isDeadOrDying()
                && entity != mc.player && mc.player.distanceToSqr(entity) <= TARGET_RANGE_SQ;
    }

    public int getAccentColor() {
        return themeColor.getValue() ? Theme.getAccentColor() : color.getValue();
    }

    public TargetEspRenderer getRenderer() {
        return this.renderer;
    }

    public boolean hasTarget() {
        return resolveTarget() != null;
    }

    public boolean usesMarker() {
        return mode.getValue().equalsIgnoreCase(MODE_MARKER);
    }

    public boolean usesLightning() {
        return mode.getValue().equalsIgnoreCase(MODE_LIGHTNING);
    }

    public boolean usesCubes() {
        return mode.getValue().equalsIgnoreCase(MODE_CUBES);
    }

    public boolean usesGhost() {
        return mode.getValue().equalsIgnoreCase(MODE_GHOST);
    }

    public boolean usesRing() {
        return mode.getValue().equalsIgnoreCase(MODE_RING);
    }

    @Override
    protected void onDisable() {
        this.halo.release();
        this.ghost.reset();
        this.ring.release();
        this.heldTarget = null;
        this.heldAt = 0L;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        // Мировой проход: души, молнии, кубы-халo рисуются в 3D вокруг цели.
        if (event.getDeltaTracker() == null) return;
        float tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        this.renderer.renderWorld3D(this, tickDelta, event.getClient());
        if (usesCubes()) {
            this.halo.render(this, tickDelta);
        } else {
            this.halo.release();
        }
        if (usesRing()) {
            this.ring.render(this, tickDelta);
        } else {
            this.ring.release();
        }
        if (usesGhost()) {
            int ghostColor = getAccentColor();
            this.ghost.render(resolveTarget(), tickDelta, ghostColor, this.ghostSize.getValue().floatValue());
        } else {
            this.ghost.reset();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (event.getGuiGraphicsExtractor() == null) return;
        this.renderer.render(event, this);
    }
}