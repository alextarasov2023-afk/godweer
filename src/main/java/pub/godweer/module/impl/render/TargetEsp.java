package pub.godweer.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.target.BloomTargetRenderer;
import pub.godweer.util.target.GhostTargetRenderer;
import pub.godweer.util.target.HaloTargetRenderer;
import pub.godweer.util.target.TargetEspRenderer;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.event.list.Render3DEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.impl.combat.KillAura;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.ColorSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.friend.FriendManager;

/**
 * Target ESP: highlights the current target with a themed 2D marker in its center,
 * lightning bolts, scattered cubes, souls or a bloom cloud.
 * Цель берётся только из KillAura или с наводки (прицел на модели).
 */
public final class TargetEsp extends Module {

    public static final String MODE_MARKER = "Marker";
    public static final String MODE_LIGHTNING = "Lightning";
    public static final String MODE_CUBES = "Cubes";
    public static final String MODE_GHOST = "Ghost";
    public static final String MODE_BLOOM = "Bloom";

    public final ModeSetting mode = mode("Режим", MODE_MARKER,
            MODE_MARKER, MODE_LIGHTNING, MODE_CUBES, MODE_GHOST, MODE_BLOOM);

    public final CheckBox themeColor = checkbox("Color From Theme", true);
    public final ColorSetting color = color("Color", ColorUtil.rgba(255, 170, 45, 255)).visible(() -> !themeColor.getValue());

    // Разряды рождаются на корпусе, поэтому «Количество» — это сколько дуг живёт
    // одновременно: эффект держится пачкой, по одной молнии он читается как глюк.
    public final SliderSetting lightningCount = slider("Молнии: Количество", 8.0f, 1.0f, 16.0f, 1.0f).visible(this::usesLightning);
    public final SliderSetting lightningSpeed = slider("Молнии: Частота", 1.0f, 0.2f, 4.0f, 0.05f).visible(this::usesLightning);
    // Длина канала в шкале настройки: 6.5 = ~0.85 блока от точки старта. Держим старую
    // шкалу, чтобы уже сохранённые конфиги дали ровно тот размер, что в эталоне.
    public final SliderSetting lightningLength = slider("Молнии: Длина", 6.5f, 1.5f, 12.0f, 0.1f).visible(this::usesLightning);
    // Насколько старт канала отстоит от поверхности хитбокса (в той же шкале).
    public final SliderSetting lightningRadius = slider("Молнии: Разброс", 1.35f, 0.1f, 4.0f, 0.05f).visible(this::usesLightning);
    // Доля длины канала, на которую середина пролёта отводится вбок.
    public final SliderSetting lightningJitter = slider("Молнии: Излом", 0.55f, 0.0f, 1.5f, 0.05f).visible(this::usesLightning);
    public final SliderSetting lightningWidth = slider("Молнии: Толщина", 1.0f, 0.4f, 2.5f, 0.1f).visible(this::usesLightning);
    public final CheckBox lightningGlow = checkbox("Молнии: Узлы и вспышка", true).visible(this::usesLightning);
    public final CheckBox lightningHurt = checkbox("Молнии: Вспышка при уроне", true).visible(this::usesLightning);

    public final SliderSetting ghostSize = slider("Души: Размер", 1.35f, 0.5f, 3.0f, 0.05f).visible(this::usesGhost);
    public final SliderSetting ghostLength = slider("Души: Длина", 60.0f, 12.0f, 96.0f, 1.0f).visible(this::usesGhost);

    public final SliderSetting bloomCount = slider("Свечение: Частицы", 64.0f, 8.0f, 96.0f, 1.0f).visible(this::usesBloom);
    public final SliderSetting bloomRadius = slider("Свечение: Радиус", 0.75f, 0.15f, 2.5f, 0.05f).visible(this::usesBloom);
    public final SliderSetting bloomHeight = slider("Свечение: Запас высоты", 1.15f, 0.0f, 3.0f, 0.05f).visible(this::usesBloom);
    public final SliderSetting bloomSpeed = slider("Свечение: Скорость", 1.0f, 0.1f, 4.0f, 0.05f).visible(this::usesBloom);
    public final SliderSetting bloomSize = slider("Свечение: Размер", 0.055f, 0.01f, 0.2f, 0.005f).visible(this::usesBloom);
    public final CheckBox bloomHurt = checkbox("Свечение: Вспышка при уроне", true).visible(this::usesBloom);

    private final TargetEspRenderer renderer = new TargetEspRenderer();
    private final HaloTargetRenderer halo = new HaloTargetRenderer();
    private final GhostTargetRenderer ghost = new GhostTargetRenderer();
    private final BloomTargetRenderer bloom = new BloomTargetRenderer();

    // Цель считается один раз на кадр: иначе души/маркер рисуются по разным
    // результатам поиска и эффект начинает «дрожать» между проходами.
    private boolean frameResolved;
    private LivingEntity frameTarget;

    public TargetEsp() {
        super("TargetEsp", "Подсвечивает цель ауры или цель наводки", Category.RENDER);
    }

    /**
     * Цель кадра: то, что выбрала KillAura, а если аура выключена или цели у неё
     * нет — во что смотрит прицел. Других источников нет: эффект появляется только
     * вместе с реальной целью.
     */
    public LivingEntity resolveTarget() {
        if (this.frameResolved) return this.frameTarget;
        this.frameResolved = true;
        this.frameTarget = computeTarget();
        return this.frameTarget;
    }

    private LivingEntity computeTarget() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null || mc.level == null) return null;

        LivingEntity target = aura(mc, player);
        return target != null ? target : crosshair(mc, player);
    }

    private static LivingEntity crosshair(Minecraft mc, LocalPlayer player) {
        if (!(mc.hitResult instanceof EntityHitResult hit)) return null;
        if (!(hit.getEntity() instanceof LivingEntity living)) return null;
        return acceptable(living, player) ? living : null;
    }

    private LivingEntity aura(Minecraft mc, LocalPlayer player) {
        KillAura killAura = KillAura.INSTANCE;
        if (killAura == null || !killAura.isEnabled()) return null;
        LivingEntity target = killAura.getTarget();
        return acceptable(target, player) ? target : null;
    }

    private static boolean acceptable(LivingEntity entity, LocalPlayer player) {
        if (entity == null || entity == player) return false;
        if (entity.isRemoved() || !entity.isAlive() || entity.isDeadOrDying()) return false;
        if (entity.isSpectator()) return false;
        if (entity instanceof Player other) {
            FriendManager friends = FriendManager.getInstance();
            if (friends != null && friends.isFriend(other)) return false;
        }
        return player.distanceTo(entity) <= 64.0D;
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

    /** Маркер рисуем при любом режиме, кроме тех что сами рисуют в мире. */
    public boolean usesMarker() {
        return !(usesLightning() || usesCubes() || usesGhost() || usesBloom());
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

    public boolean usesBloom() {
        return mode.getValue().equalsIgnoreCase(MODE_BLOOM);
    }

    @Override
    protected void onDisable() {
        this.halo.release();
        this.ghost.reset();
        this.bloom.reset();
        this.renderer.release();
        this.frameResolved = false;
        this.frameTarget = null;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        // Мировой проход: души, молнии, свечение, кубы-халo рисуются в 3D вокруг цели.
        if (event.getDeltaTracker() == null) return;
        float tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        this.frameResolved = false;
        this.frameTarget = null;
        this.renderer.renderWorld3D(this, tickDelta, event.getClient());
        if (usesCubes()) {
            this.halo.render(this, tickDelta);
        } else {
            this.halo.release();
        }
        if (usesGhost()) {
            int ghostColor = getAccentColor();
            this.ghost.render(resolveTarget(), tickDelta, ghostColor,
                    this.ghostSize.getValue().floatValue(), this.ghostLength.getValue().intValue());
        } else {
            this.ghost.reset();
        }
        if (usesBloom()) {
            this.bloom.render(this, tickDelta, resolveTarget(), getAccentColor());
        } else {
            this.bloom.reset();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (event.getGuiGraphicsExtractor() == null) return;
        this.renderer.render(event, this);
    }
}