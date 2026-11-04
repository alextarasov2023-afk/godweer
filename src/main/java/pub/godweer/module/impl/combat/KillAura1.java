package pub.godweer.module.impl.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.AttackEvent;
import pub.godweer.event.list.PlayerInputEvent;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.friend.FriendManager;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.MultiModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.rotation.Angle;
import pub.godweer.rotation.RayTraceUtils;
import pub.godweer.rotation.RotationHandler;
import pub.godweer.rotation.UBoxPoints;
import pub.godweer.util.player.MoveUtility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * KillAura1 — порт «Aura» из wonderfull: наводка в точку на 60 % высоты, один удар за
 * кулдаун 467 мс, удар только в засчитываемом падении, сброс спринта отжатием forward.
 * <p>
 * Режимы ротации (настройка Rotation):
 * <ul>
 *   <li>Smooth — дефолт: серверный угол доводится до цели неким не снапом шагом с
 *       инерцией и разгоном, камера остаётся у игрока;</li>
 *   <li>Snap — старый снап: серверный угол встаёт на цель мгновенно (аналог скорости
 *       360 в оригинале). «Client Look» — тот же снап, но в собственную камеру;</li>
 *   <li>Trigger — ротации нет вообще: наводишься сам, удар выходит, только когда твой
 *       настоящий прицел уже на цели.</li>
 * </ul>
 * <p>
 * Дальность удара ограничена серверным порогом: 3 блока из оригинала на 26.3 — это
 * ровно {@code Unfair Advantage(Reach-Hitbox)}, поэтому реальный удар не выходит за
 * 2.92 как в KillAura.
 */
public class KillAura1 extends Module {
    public static KillAura1 INSTANCE;

    public static final String ROT_SMOOTH = "Smooth";
    public static final String ROT_SNAP = "Snap";
    public static final String ROT_TRIGGER = "Trigger";

    // Профиль Smooth: тот же нелинейный доворот, что в KillAura, но без ручек в GUI.
    // Потолок скорости °/тик; 180° сводится за ~5 тиков, у цели шаг падает почти в ноль.
    private static final float SMOOTH_SPEED = 45.0F;
    private static final float SMOOTH_STRENGTH = 0.60F;
    private static final float SMOOTH_CURVE = 1.05F;
    private static final float SMOOTH_FALLOFF = 10.0F;
    private static final float SMOOTH_SMOOTHING = 0.35F;
    private static final float SMOOTH_RAMP_TICKS = 2.0F;
    private static final float SMOOTH_DEADZONE = 0.08F;
    private static final float SMOOTH_ACCEL = 0.32F;
    private static final float SMOOTH_MIN_STEP = 0.45F;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Кулдаун между ударами из оригинала. */
    private static final long ATTACK_COOLDOWN_MS = 467L;
    /** Заряд ЛЮБОГО удара по шкале, по которой сервер масштабирует урон. */
    private static final float MIN_CHARGE = 0.85F;
    /** Потолок дистанции: дальше ванильного порога удар — флаг на Reach-Hitbox. */
    private static final float MAX_SAFE_REACH = 2.92F;
    // Удар только в падении. Сколько тиков падения должно пройти до удара: пакет
    // позиции текущего тика уходит ПОСЛЕ PRE, поэтому в первом тику падения сервер
    // ещё видит свою копию «земля / fallDistance = 0» и удар обычным критом не
    // засчитает.
    private static final int CRIT_FALL_TICKS = 2;

    private final HeaderSetting targetHeader = header("Targets");
    private final MultiModeSetting targets = multiMode("Targets",
            List.of("Players", "Naked", "Invisible", "Mobs"),
            "Players", "Naked", "Invisible", "Animals", "Mobs");
    private final SliderSetting attackReach = slider("Attack Reach", 3.0f, 1.0f, 6.0f, 0.05f);
    private final SliderSetting aimRange = slider("Aim Range", 3.0f, 0.0f, 6.0f, 0.05f);
    private final SliderSetting glideAimRange = slider("Glide Aim Range", 50.0f, 10.0f, 100.0f, 0.05f);
    private final ModeSetting priority = mode("Priority", "Distance", "Distance", "Health", "Angle", "None");

    private final HeaderSetting attackHeader = header("Attack");
    private final CheckBox throughWalls = checkbox("Through Walls", true);
    private final CheckBox aimCheck = checkbox("Aim Check", false);
    private final CheckBox pauseWhileEating = checkbox("Pause while Eating", true);
    private final CheckBox sprintReset = checkbox("Sprint Reset", true);
    private final CheckBox unpressShield = checkbox("Unpress Shield", false);
    private final CheckBox axeShieldBreak = checkbox("Axe Shield Break", true);

    private final HeaderSetting rotationHeader = header("Rotation");
    private final ModeSetting rotation = mode("Rotation",
            ROT_SMOOTH, ROT_SMOOTH, ROT_SNAP, ROT_TRIGGER);
    private final CheckBox clientLook = checkbox("Client Look", false)
            .visible(this::isSnap);
    private final ModeSetting movement = mode("Movement", "None", "None", "Free", "Focus");

    private LivingEntity target;
    private long nextAttackAt = 0L;
    private int fallTicks = 0;
    private float velYaw = 0.0F;
    private float velPitch = 0.0F;
    private int engagedTicks = 0;
    private boolean needSprintReset = false;
    private boolean sprintResetDone = false;
    private int sprintResetTicks = 0;
    private boolean rotationOwned = false;

    public KillAura1() {
        super("KillAura1", "Аура с режимами ротации Smooth (тихий серверный доворот), Snap "
                + "(мгновенная наводка) и Trigger (без ротации, бьёт под твоим прицелом); "
                + "удар только в падении, сброс спринта отжатием вперёд", Category.COMBAT);
        INSTANCE = this;
    }

    /**
     * Окно, в котором KeyboardInputMixin гасит шаг вперёд. Именно отжатый forward, а
     * не setSprinting(false): подменённый флаг читается античитом как след софта.
     */
    public boolean sprintHoldActive() {
        return isEnabled() && needSprintReset;
    }

    @Override
    protected void onEnable() {
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
        nextAttackAt = 0L;
        fallTicks = 0;
        velYaw = 0.0F;
        velPitch = 0.0F;
        engagedTicks = 0;
        target = null;
    }

    @Override
    protected void onDisable() {
        target = null;
        fallTicks = 0;
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
        releaseRotation();
    }

    /** Своя атака закрепляет цель: бьём того, по кому уже ударили, а не ближайшего. */
    @EventTarget
    public void onAttack(AttackEvent event) {
        LocalPlayer player = player();
        if (!isEnabled() || player == null || level() == null) return;
        if (!(event.getTarget() instanceof LivingEntity living)) return;
        if (isValidTarget(living, player.getEyePosition())) target = living;
    }

    @EventTarget
    public void onInput(PlayerInputEvent event) {
        if (!isEnabled() || player() == null || target == null) return;
        // Коррекция имеет смысл только там, где серверный угол расходится с камерой.
        if (isTrigger()) return;
        if (movement.is("Focus")) {
            MoveUtility.fixMovementFocus(event, target);
        }
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        LocalPlayer player = player();
        if (!isEnabled() || event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (player == null || level() == null || gameMode() == null) return;

        // Флаг сброса живёт один тик: forward был погашен миксином в тике между этим PRE
        // и предыдущим, теперь считаем сброс сделанным и ждём ещё тик до удара.
        if (needSprintReset) {
            needSprintReset = false;
            sprintResetDone = true;
            sprintResetTicks = 0;
        }
        if (sprintResetDone) {
            sprintResetTicks++;
        }

        updateFallTicks(player);
        updateTarget(player);
        rotate(player);
        processAttack(player);
    }

    /** Подряд идущие тики падения; на земле счётчик обнуляется. */
    private void updateFallTicks(LocalPlayer player) {
        boolean falling = !player.onGround() && player.fallDistance > 0.0F;
        fallTicks = falling ? fallTicks + 1 : 0;
    }

    // ------------------------------------------------------------------ таргеты

    private void updateTarget(LocalPlayer player) {
        if (target != null && isValidTarget(target, player.getEyePosition())) return;
        target = findTarget(player);
    }

    private LivingEntity findTarget(LocalPlayer player) {
        Vec3 eyes = player.getEyePosition();
        double area = maxAimRange(player) + 1.0D;
        AABB box = player.getBoundingBox().inflate(area);

        List<LivingEntity> candidates = new ArrayList<>(
                level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player));
        for (Player other : level().players()) {
            if (other != player && other.getBoundingBox().intersects(box) && !candidates.contains(other)) {
                candidates.add(other);
            }
        }
        candidates.removeIf(e -> !isValidTarget(e, eyes));
        if (candidates.isEmpty()) return null;

        switch (priority.getValue()) {
            case "Distance" -> candidates.sort(Comparator
                    .comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(eyes)));
            case "Health" -> candidates.sort(Comparator.comparingDouble(LivingEntity::getHealth));
            case "Angle" -> candidates.sort(Comparator.comparingDouble(e -> angleError(e, player, eyes)));
            default -> {
            }
        }
        return candidates.get(0);
    }

    private boolean isValidTarget(LivingEntity entity, Vec3 eyes) {
        LocalPlayer player = player();
        if (player == null || entity == player) return false;
        if (!entity.isAlive() || entity.getHealth() <= 0.0F || entity.isRemoved()) return false;

        if (entity instanceof Player other) {
            if (FriendManager.getInstance().isFriend(other)) return false;
            if (!targets.isEnabled("Players")) return false;
            if (isNaked(other) && !targets.isEnabled("Naked")) return false;
            if (entity.isInvisible() && !targets.isEnabled("Invisible")) return false;
        } else if (entity instanceof Enemy) {
            if (!targets.isEnabled("Mobs")) return false;
        } else if (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof Villager) {
            // «Мирные» оригинала: пассивные, рыбы и жители — одна галка.
            if (!targets.isEnabled("Animals")) return false;
        } else {
            // Бронзовые стойки, големы, летучие мыши и прочее в оригинале не бьются.
            return false;
        }

        double maxAim = maxAimRange(player);
        if (RayTraceUtils.getClosestDistanceSqr(eyes, entity.getBoundingBox()) > maxAim * maxAim) {
            return false;
        }
        if (!throughWalls.getValue()
                && !RayTraceUtils.canSeePoint(eyes, aimPoint(entity))) {
            return false;
        }
        return true;
    }

    private double angleError(LivingEntity entity, LocalPlayer player, Vec3 eyes) {
        Angle aim = Angle.fromVec(eyes, entity.getBoundingBox().getCenter());
        return Math.abs(Mth.wrapDegrees(aim.getYaw() - sendYaw(player)))
                + Math.abs(aim.getPitch() - sendPitch(player));
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private double maxAimRange(LocalPlayer player) {
        return player.isFallFlying() ? glideAimRange.get() : attackReach.get() + aimRange.get();
    }

    // ------------------------------------------------------------------ ротация

    /** Точка наводки оригинала: 60 % высоты от ног, без упреждения и мультипоинта. */
    private static Vec3 aimPoint(LivingEntity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.6D, 0.0D);
    }

    public boolean isSmooth() {
        return rotation.is(ROT_SMOOTH);
    }

    public boolean isSnap() {
        return rotation.is(ROT_SNAP);
    }

    /** Trigger: клиент камерой не владеет — игрок наводится сам, удар только под прицелом. */
    public boolean isTrigger() {
        return rotation.is(ROT_TRIGGER);
    }

    private void rotate(LocalPlayer player) {
        if (target == null) {
            releaseRotation();
            return;
        }

        Angle aim = Angle.fromVec(player.getEyePosition(), aimPoint(target));

        if (isTrigger()) {
            // Хендлер не активизируем вовсе: он перехватывает мышь и подменяет sendYaw,
            // а прицел в этом режиме обязан быть ровно тот, что у игрока.
            if (RotationHandler.isActive()) RotationHandler.disengage("Smooth");
            rotationOwned = false;
            engagedTicks = 0;
            return;
        }

        if (clientLook.getValue()) {
            // Наводка собственной камерой: угол квантуем под GCD мыши, иначе снап
            // некруглыми долями градуса читается как софт.
            Angle snapped = aim.applyGCD();
            player.setYRot(snapped.getYaw());
            player.setXRot(snapped.getPitch());
            player.yHeadRot = snapped.getYaw();
            releaseRotation();
            return;
        }

        if (isSnap()) {
            RotationHandler.setRotation(aim.getYaw(), aim.getPitch());
            rotationOwned = true;
            engagedTicks = 0;
            velYaw = 0.0F;
            velPitch = 0.0F;
            return;
        }

        rotateSmooth(player, aim);
    }

    /**
     * Smooth: серверный угол доводится до цели не снапом, а шагом, который растёт с
     * остатком угла (степень SMOOTH_CURVE) и раскручивается за SMOOTH_RAMP_TICKS.
     * Инерция по каждой оси — экспоненциальное сглаживание; мёртвая зона гасит звон
     * на месте. Камера при этом остаётся там, куда смотрит игрок.
     */
    private void rotateSmooth(LocalPlayer player, Angle aim) {
        float curYaw = sendYaw(player);
        float curPitch = sendPitch(player);
        float deltaYaw = Mth.wrapDegrees(aim.getYaw() - curYaw);
        float deltaPitch = aim.getPitch() - curPitch;
        float error = (float) Math.hypot(deltaYaw, deltaPitch);

        if (error < SMOOTH_DEADZONE) {
            velYaw *= 0.35F;
            velPitch *= 0.35F;
            engagedTicks = 0;
            return;
        }

        double ramp = rampEase();
        float t = Mth.clamp(error / SMOOTH_FALLOFF, 0.0F, 1.0F);
        double factor = SMOOTH_STRENGTH * Math.pow(t, SMOOTH_CURVE);

        velYaw = Mth.lerp(SMOOTH_SMOOTHING, (float) (deltaYaw * factor * ramp), velYaw);
        velPitch = Mth.lerp(SMOOTH_SMOOTHING, (float) (deltaPitch * factor * ramp), velPitch);

        float floor = Math.min(SMOOTH_MIN_STEP, error * 0.25F);
        velYaw = floorStep(velYaw, deltaYaw, floor);
        velPitch = floorStep(velPitch, deltaPitch, floor);

        float limit = Math.min(Math.max(SMOOTH_SPEED, error * SMOOTH_ACCEL), SMOOTH_SPEED * 1.6F)
                * (float) ramp;
        velYaw = Mth.clamp(velYaw, -limit, limit);
        velPitch = Mth.clamp(velPitch, -limit, limit);

        if (Math.abs(velYaw) < 1.0E-4F && Math.abs(velPitch) < 1.0E-4F) return;

        RotationHandler.setRotation(curYaw + velYaw, curPitch + velPitch);
        rotationOwned = true;
        engagedTicks++;
    }

    /** Квадратичный ease-in разгона: первый тик почти ничего, дальше — доворот до силы. */
    private double rampEase() {
        double p = Math.min(1.0D, (engagedTicks + 1.0D) / SMOOTH_RAMP_TICKS);
        return p * p;
    }

    /** Пол скорости по оси: не форсируем, если остаток по этой оси меньше самого пола. */
    private static float floorStep(float step, float delta, float min) {
        float floor = Math.min(min, Math.abs(delta));
        if (floor <= 0.0F || Math.abs(step) >= floor) return step;
        float sign = step != 0.0F ? Math.signum(step) : Math.signum(delta);
        return sign * floor;
    }

    private void releaseRotation() {
        if (!rotationOwned) return;
        rotationOwned = false;
        RotationHandler.disengage(isSmooth() ? "Smooth" : "Instant");
    }

    // -------------------------------------------------------------------- удар

    private void processAttack(LocalPlayer player) {
        if (target == null) {
            nextAttackAt = System.currentTimeMillis();
            sprintResetDone = false;
            sprintResetTicks = 0;
            releaseRotation();
            return;
        }

        if (!shouldAttack(player) || System.currentTimeMillis() < nextAttackAt) return;

        if (pauseWhileEating.getValue() && (blocksAttackWhileUsing(player) || isEating(player))) {
            return;
        }

        if (sprintReset.getValue() && player.isSprinting() && !sprintResetDone) {
            needSprintReset = true;
            return;
        }
        if (sprintReset.getValue() && sprintResetDone && sprintResetTicks < 1) {
            return;
        }

        attack(player);
        sprintResetDone = false;
        sprintResetTicks = 0;
    }

    private boolean shouldAttack(LocalPlayer player) {
        // Trigger камерой не владеет, поэтому и проверка прицела идёт по настоящему
        // углу игрока: бьём, только когда сам навёлся.
        if (isTrigger() && !RayTraceUtils.isLookingAt(target, hitReach(),
                player.getYRot(), player.getXRot(), !throughWalls.getValue())) {
            return false;
        }
        // Бьём только в засчитываемом падении: на земле и в первом тике после отрыва
        // сервер считает удар обычным, крит-урон там просто не появляется.
        if (fallTicks < CRIT_FALL_TICKS) return false;
        if (player.getAttackStrengthScale(0.0F) < MIN_CHARGE) return false;

        if (aimCheck.getValue() && !RayTraceUtils.isLookingAt(target,
                attackReach.get() * 2.0F, sendYaw(player), sendPitch(player), false)) {
            return false;
        }

        Vec3 eyes = player.getEyePosition();
        Vec3 nearest = UBoxPoints.getClosestBoxPoint(eyes, target.getBoundingBox());
        return eyes.distanceTo(nearest) <= hitReach();
    }

    private void attack(LocalPlayer player) {
        InteractionHand unpressed = null;
        if (unpressShield.getValue()) {
            unpressed = ownShieldHand(player);
            if (unpressed != null) gameMode().releaseUsingItem(player);
        }

        boolean struck = false;
        if (axeShieldBreak.getValue() && target instanceof Player other && other.isBlocking()) {
            struck = axeStrike(player, other);
        }
        if (!struck) gameMode().attack(player, target);

        SwingAnimation swingAnim = player.getMainHandItem().getAttackAnimation();
        player.swing(InteractionHand.MAIN_HAND, swingAnim, false);

        if (unpressed != null && !player.isUsingItem()) {
            gameMode().useItem(player, unpressed);
        }

        nextAttackAt = System.currentTimeMillis() + ATTACK_COOLDOWN_MS;
    }

    /**
     * Удар топором по щиту: смена слота пакетом и возврат в том же тике — сервер
     * видит только carried-item, без видимого дёрганья хотбаром.
     */
    private boolean axeStrike(LocalPlayer player, Player blocking) {
        int axe = findAxeSlot();
        if (axe == -1) return false;

        int previous = player.getInventory().getSelectedSlot();
        if (axe != previous) setSlot(axe);
        gameMode().attack(player, blocking);
        if (axe != previous) setSlot(previous);
        return true;
    }

    // ------------------------------------------------------------------- утилиты

    private boolean blocksAttackWhileUsing(LocalPlayer player) {
        if (!player.isUsingItem()) return false;
        return !(unpressShield.getValue() && ownShieldHand(player) != null);
    }

    private InteractionHand ownShieldHand(LocalPlayer player) {
        if (!player.isUsingItem() || !player.getUseItem().is(Items.SHIELD)) return null;
        return player.getUsedItemHand();
    }

    private boolean isEating(LocalPlayer player) {
        if (!player.isUsingItem()) return false;
        ItemUseAnimation anim = player.getUseItem().getUseAnimation();
        return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
    }

    /** Дальность удара с серверной полкой. */
    private double hitReach() {
        return Math.min(attackReach.get(), MAX_SAFE_REACH);
    }

    /** Угол, который видит сервер: свой при тихой ротации, обычный при Client Look. */
    private static float sendYaw(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot();
    }

    private static float sendPitch(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();
    }

    private static int findAxeSlot() {
        LocalPlayer player = mc.player;
        if (player == null) return -1;
        List<ItemStack> hotbar = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < Math.min(9, hotbar.size()); i++) {
            if (hotbar.get(i).is(ItemTags.AXES)) return i;
        }
        return -1;
    }

    private void setSlot(int slot) {
        LocalPlayer player = player();
        if (player == null || slot < 0 || slot > 8 || player.connection == null) return;
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }
}
