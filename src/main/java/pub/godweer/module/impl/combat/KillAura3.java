package pub.godweer.module.impl.combat;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import pub.godweer.util.player.InventorySwaps;
import pub.godweer.util.player.MoveUtility;

import java.util.List;

/**
 * KillAura3 — третий перенос «Aura» (Fabrika-версия на 1.21.1).
 * <p>
 * Что в ней своё по сравнению с KillAura1/KillAura2:
 * <ul>
 *   <li>целевой угол перед отправкой квантуется под сетку чувствительности мыши
 *       (в оригинале roundToGcd; в {@link Angle#applyGCD()} та же формула);</li>
 *   <li>скорости ротации 360/360 с ускорением 50/50 за тик — то есть снап, и сюда он
 *       лёг одним {@link RotationHandler#setRotation}, без отдельного RotationStorage;</li>
 *   <li>на элитрах цель берётся по предсказанной позиции (позиция + дельта-движение),
 *       точка на 50 % высоты со сдвигом вперёд по вектору взгляда цели, а удар по такой
 *       паре идёт только ближе 5 блоков;</li>
 *   <li>коррекции четыре: None / Free / Focused / Full;</li>
 *   <li>откат считается по шкале заряда с запасом в 1.5 тика и порогом 0.9;</li>
 *   <li>удар только когда сервер засчитал бы крит ({@code canCritical} оригинала).</li>
 * </ul>
 * Дальность удара, как и в двух предыдущих: не выше 2.92 блоков — 3.0 из оригинала на
 * 26.3 это ровно {@code Unfair Advantage(Reach-Hitbox)}.
 */
public class KillAura3 extends Module {
    public static KillAura3 INSTANCE;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final long ATTACK_COOLDOWN_MS = 467L;
    /** Процент нужного захода удара: 90 % — это 0.9 отката из оригинальной спеки. */
    private static final float DEFAULT_COOLDOWN_PERCENT = 90.0F;
    /**
     * Отдельный порог для булавы. Её сила идёт не от полного отката, а от падения
     * (smash), поэтому ждать 90 % заряда смысла нет: по умолчанию 10 %.
     */
    private static final float DEFAULT_MACE_COOLDOWN_PERCENT = 10.0F;
    /** Шкала захода с запасом в 1.5 тика — как getAttackCooldownProgress(p, 1.5F) в спеке. */
    private static final float CHARGE_BASE_TICKS = 1.5F;
    private static final float MAX_SAFE_REACH = 2.92F;
    /** Центр хитбокса считается в дистанции, пока цель не дальше этого. */
    private static final double CENTER_DISTANCE = 3.0D;
    /** Оба на элитрах: удар только ближе этого расстояния до предсказанной точки. */
    private static final double BOTH_GLIDE_MAX = 5.0D;
    /** Тики падения до удара: пакет позиции текущего тика уходит после PRE. */
    private static final int CRIT_FALL_TICKS = 2;
    /** Предсказание цели на элитрах: вперёд на столько тиков и на столько же по её взгляду. */
    private static final double GLIDE_PREDICT_TICKS = 1.0D;
    private static final double GLIDE_FORWARD = 1.0D;

    private final HeaderSetting targetHeader = header("Targets");
    private final MultiModeSetting targets = multiMode("Targets",
            List.of("Players", "Naked", "Invisible", "Mobs"),
            "Players", "Naked", "Invisible", "Peaceful", "Mobs");
    private final SliderSetting attackRange = slider("Attack Range", 3.0f, 0.0f, 6.0f, 0.05f);
    private final SliderSetting aimRange = slider("Aim Range", 3.0f, 0.0f, 6.0f, 0.05f);
    private final SliderSetting glideRange = slider("Glide Range", 50.0f, 10.0f, 100.0f, 0.05f);
    private final ModeSetting priority = mode("Priority", "Distance", "Distance", "Health", "Angle", "None");

    private final HeaderSetting attackHeader = header("Attack");
    private final CheckBox throughWalls = checkbox("Through Walls", true);
    private final CheckBox raycastCheck = checkbox("Raycast Check", false);
    private final CheckBox noAttackWhileEating = checkbox("Pause while Using", true);
    private final SliderSetting cooldownPercent = slider("Cooldown",
            DEFAULT_COOLDOWN_PERCENT, 0.0f, 100.0f, 1.0f);
    private final SliderSetting maceCooldownPercent = slider("Mace Cooldown",
            DEFAULT_MACE_COOLDOWN_PERCENT, 0.0f, 100.0f, 1.0f);
    private final CheckBox sprintReset = checkbox("Sprint Reset", true);
    private final CheckBox lowerShield = checkbox("Release Shield", false);
    private final CheckBox shieldBreak = checkbox("Shield Break", true);

    private final HeaderSetting rotationHeader = header("Rotation");
    private final CheckBox clientLook = checkbox("Client Look", false);
    private final ModeSetting correction = mode("Movement", "None", "None", "Free", "Focused", "Full");

    private LivingEntity target;
    private long nextAttackAt = 0L;
    private int fallTicks = 0;
    private boolean needSprintReset = false;
    private boolean sprintResetDone = false;
    private int sprintResetTicks = 0;
    private boolean rotationOwned = false;

    public KillAura3() {
        super("KillAura3", "Аура с GCD-квантом целевого угла, предсказанием цели на элитрах, "
                + "четырьмя коррекциями движения и ударом только там, где засчитается крит",
                Category.COMBAT);
        INSTANCE = this;
    }

    /** Окно, в котором KeyboardInputMixin гасит шаг вперёд: сброс спринта без setSprinting. */
    public boolean sprintHoldActive() {
        return isEnabled() && needSprintReset;
    }

    @Override
    protected void onEnable() {
        target = null;
        nextAttackAt = 0L;
        fallTicks = 0;
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
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

    @EventTarget
    public void onAttack(AttackEvent event) {
        LocalPlayer player = player();
        if (!isEnabled() || player == null || level() == null) return;
        if (event.getTarget() instanceof LivingEntity living && isValidTarget(living, player)) {
            target = living;
        }
    }

    @EventTarget
    public void onInput(PlayerInputEvent event) {
        LocalPlayer player = player();
        if (!isEnabled() || player == null || target == null) return;

        switch (correction.getValue()) {
            // Free: идём туда, куда смотрит камера, а клавиши пересчитываются под
            // серверный угол. Без silent-ротации угла расхождения нет и коррекция не нужна.
            case "Free" -> MoveUtility.fixMovement(event, cameraYaw(player));
            // Focused: вжимание в цель относительно серверного угла.
            case "Focused" -> MoveUtility.fixMovementFocus(event, target);
            // Full: направление движения — ровно по yaw в центр хитбокса цели.
            case "Full" -> MoveUtility.fixMovementFocus(event, yawToBoxCenter(player, target));
            default -> {
            }
        }
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        LocalPlayer player = player();
        if (!isEnabled() || event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (player == null || level() == null || gameMode() == null) return;

        // Forward был погашен миксином в тике между этим PRE и предыдущим.
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

    // ------------------------------------------------------------------ таргеты

    /** Подряд идущие тики падения; на земле счётчик обнуляется. */
    private void updateFallTicks(LocalPlayer player) {
        boolean falling = !player.onGround() && player.fallDistance > 0.0F;
        fallTicks = falling ? fallTicks + 1 : 0;
    }

    private void updateTarget(LocalPlayer player) {
        if (target != null && isValidTarget(target, player)) return;
        target = findTarget(player);
    }

    /** Сортировка по приоритету; в None порядок тот, в котором мир отдаёт сущности. */
    private LivingEntity findTarget(LocalPlayer player) {
        double area = maxAimRange(player) + 1.0D;
        AABB box = player.getBoundingBox().inflate(area);
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player);

        LivingEntity best = null;
        double bestValue = Double.MAX_VALUE;
        String mode = priority.getValue();

        for (LivingEntity living : candidates) {
            if (!isValidTarget(living, player)) continue;
            if ("None".equals(mode)) return living;

            double value = switch (mode) {
                case "Health" -> living.getHealth();
                case "Angle" -> angleError(living, player);
                // Distance в оригинале считается до ЦЕНТРА хитбокса, а не до ближайшей точки.
                default -> player.getEyePosition().distanceToSqr(boxCenter(living));
            };
            if (value < bestValue) {
                bestValue = value;
                best = living;
            }
        }
        return best;
    }

    private boolean isValidTarget(LivingEntity entity, LocalPlayer player) {
        if (entity == null || entity == player) return false;
        if (!entity.isAlive() || entity.getHealth() <= 0.0F || entity.isRemoved()) return false;
        if (entity instanceof ArmorStand || entity instanceof IronGolem || entity instanceof Bat) return false;

        if (entity instanceof Player other) {
            if (!targets.isEnabled("Players")) return false;
            if (FriendManager.getInstance().isFriend(other)) return false;
            if (isNaked(other) && !targets.isEnabled("Naked")) return false;
            if (entity.isInvisible() && !targets.isEnabled("Invisible")) return false;
        } else if (entity instanceof Mob mob) {
            if (mob instanceof Enemy ? !targets.isEnabled("Mobs") : !targets.isEnabled("Peaceful")) {
                return false;
            }
        } else {
            return false;
        }

        Vec3 eyes = player.getEyePosition();
        double maxAim = maxAimRange(player);
        if (eyes.distanceToSqr(UBoxPoints.getClosestBoxPoint(eyes, entity.getBoundingBox())) > maxAim * maxAim) {
            return false;
        }
        // В оригинале видимость проверяется по макушке и по ближайшей точке хитбокса.
        if (!throughWalls.getValue() && !canSee(player, eyes, entity)) return false;
        return true;
    }

    private boolean canSee(LocalPlayer player, Vec3 eyes, LivingEntity entity) {
        if (RayTraceUtils.canSeePoint(eyes, entity.getEyePosition())) return true;
        return RayTraceUtils.canSeePoint(eyes, UBoxPoints.getClosestBoxPoint(eyes, entity.getBoundingBox()));
    }

    private double angleError(LivingEntity entity, LocalPlayer player) {
        Angle aim = Angle.fromVec(player.getEyePosition(), boxCenter(entity));
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
        return player.isFallFlying() ? glideRange.get() : attackRange.get() + aimRange.get();
    }

    // ------------------------------------------------------------------ ротация

    private static Vec3 boxCenter(LivingEntity entity) {
        return entity.getBoundingBox().getCenter();
    }

    private static float yawToBoxCenter(LocalPlayer player, LivingEntity entity) {
        Vec3 center = boxCenter(entity);
        double dx = center.x - player.getX();
        double dz = center.z - player.getZ();
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
    }

    /**
     * Точка наводки: обычная — 60 % высоты от ног; если оба летят на элитрах, цель
     * берётся по предсказанной позиции (позиция + дельта-движение на тик), на половине
     * высоты и со сдвигом вперёд по вектору взгляда цели.
     */
    private Vec3 aimPoint(LivingEntity entity, LocalPlayer player) {
        if (!(player.isFallFlying() && entity.isFallFlying())) {
            return entity.position().add(0.0D, entity.getBbHeight() * 0.6D, 0.0D);
        }

        Vec3 predicted = entity.position()
                .add(entity.getDeltaMovement().scale(GLIDE_PREDICT_TICKS))
                .add(entity.getLookAngle().scale(GLIDE_FORWARD));
        return predicted.add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    private void rotate(LocalPlayer player) {
        if (target == null) {
            releaseRotation();
            return;
        }

        // Скорости 360°/тик + ускорение в оригинале означают, что угол доводится за
        // один тик, поэтому здесь сразу целевой угол; квант под GCD — как roundToGcd.
        Angle aim = Angle.fromVec(player.getEyePosition(), aimPoint(target, player)).applyGCD();

        if (clientLook.getValue()) {
            player.setYRot(aim.getYaw());
            player.setXRot(aim.getPitch());
            player.yHeadRot = aim.getYaw();
            releaseRotation();
            return;
        }

        RotationHandler.setRotation(aim.getYaw(), aim.getPitch());
        rotationOwned = true;
    }

    private void releaseRotation() {
        if (!rotationOwned) return;
        rotationOwned = false;
        RotationHandler.disengage("Instant");
    }

    /** Камера игрока: свободный угол хендлера, пока он держит ротацию. */
    private static float cameraYaw(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getFreeYaw() : player.getYRot();
    }

    // -------------------------------------------------------------------- удар

    private void processAttack(LocalPlayer player) {
        if (target == null) {
            nextAttackAt = System.currentTimeMillis();
            needSprintReset = false;
            sprintResetDone = false;
            sprintResetTicks = 0;
            releaseRotation();
            return;
        }

        if (!shouldAttack(player) || System.currentTimeMillis() < nextAttackAt) return;
        if (blocksAttackWhileUsing(player)) return;

        if (sprintReset.getValue() && player.isSprinting() && !sprintResetDone) {
            needSprintReset = true;
            return;
        }
        if (sprintResetDone && sprintResetTicks < 1) {
            return;
        }

        attack(player);
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
    }

    private boolean shouldAttack(LocalPlayer player) {
        if (player.getAttackStrengthScale(CHARGE_BASE_TICKS) < requiredCharge(player)) return false;

        boolean bothGliding = player.isFallFlying() && target.isFallFlying();

        if (raycastCheck.getValue() && !bothGliding
                && !RayTraceUtils.isLookingAt(target, maxAimRange(player) * 2.0D,
                sendYaw(player), sendPitch(player), false)) {
            return false;
        }

        Vec3 eyes = player.getEyePosition();
        if (bothGliding) {
            if (eyes.distanceTo(aimPoint(target, player)) > BOTH_GLIDE_MAX) return false;
        } else {
            double dist = eyes.distanceTo(boxCenter(target));
            if (dist > CENTER_DISTANCE) {
                dist = eyes.distanceTo(UBoxPoints.getClosestBoxPoint(eyes, target.getBoundingBox()));
            }
            if (dist > hitReach()) return false;
        }

        return canCritical(player);
    }

    /**
     * canCritical оригинала: удар выходит критом только в настоящем падении. Без воды,
     * лавы, транспорта и слепоты. По 26.3 не проверено лишь «цепляние за паутину»: в
     * маппингах этого геттера нет, вместо него падение и так не засчитывается с нулём
     * fallDistance.
     */
    private boolean canCritical(LocalPlayer player) {
        if (fallTicks < CRIT_FALL_TICKS) return false;
        if (player.onGround() || player.fallDistance <= 0.0F) return false;
        if (player.isInWater() || player.isInLava()) return false;
        if (player.isPassenger()) return false;
        return !player.hasEffect(MobEffects.BLINDNESS);
    }

    private void attack(LocalPlayer player) {
        InteractionHand lowered = null;
        if (lowerShield.getValue()) {
            lowered = ownShieldHand(player);
            if (lowered != null) gameMode().releaseUsingItem(player);
        }

        boolean handled = false;
        if (shieldBreak.getValue() && isShieldBlocking(target)) {
            handled = axeStrike(player, target);
        }
        if (!handled) {
            gameMode().attack(player, target);
            swing(player);
        }

        if (lowered != null && !player.isUsingItem()) {
            gameMode().useItem(player, lowered);
        }

        nextAttackAt = System.currentTimeMillis() + ATTACK_COOLDOWN_MS;
    }

    /** Рука, в которой игрок держит блок щитом, или null. */
    private InteractionHand ownShieldHand(LocalPlayer player) {
        if (!player.isBlocking() || !player.getUseItem().is(Items.SHIELD)) return null;
        return player.getUsedItemHand();
    }

    private static void swing(LocalPlayer player) {
        SwingAnimation anim = player.getMainHandItem().getAttackAnimation();
        player.swing(InteractionHand.MAIN_HAND, anim, false);
    }

    private static boolean isShieldBlocking(LivingEntity entity) {
        return entity != null && entity.isBlocking() && entity.getUseItem().is(Items.SHIELD);
    }

    /**
     * Ломка щита топором: топор в хотбаре — тихая подмена слота вокруг удара; топор в
     * сетках — свап через открытый инвентарь, удар и обратный свап.
     */
    private boolean axeStrike(LocalPlayer player, LivingEntity blocking) {
        int hotbar = findAxe(0, 9);
        if (hotbar != -1) {
            return attackWithSlot(player, blocking, hotbar);
        }

        int inventory = findAxe(9, 36);
        if (inventory == -1) return false;
        // Свап живёт только в открытом инвентаре: контейнер 0 должен быть активен.
        if (!(screen() instanceof InventoryScreen)) return false;
        if (player.inventoryMenu.containerId != 0) return false;

        int selected = player.getInventory().getSelectedSlot();
        int swapSlot = selected == 8 ? 7 : 8;

        InventorySwaps.grimSwapToHotbar(player, inventory, swapSlot);
        try {
            return attackWithSlot(player, blocking, swapSlot);
        } finally {
            InventorySwaps.grimSwapToHotbar(player, inventory, swapSlot);
        }
    }

    private boolean attackWithSlot(LocalPlayer player, LivingEntity blocking, int slot) {
        int selected = player.getInventory().getSelectedSlot();
        if (slot != selected) setSlot(slot);
        gameMode().attack(player, blocking);
        swing(player);
        if (slot != selected) setSlot(selected);
        return true;
    }

    // ------------------------------------------------------------------- утилиты

    /** Используемый предмет мешает удару, кроме своего же щита при включённом Release Shield. */
    private boolean blocksAttackWhileUsing(LocalPlayer player) {
        if (!noAttackWhileEating.getValue()) return false;
        if (!player.isUsingItem()) return false;
        return !(lowerShield.getValue() && player.isBlocking()
                && player.getUseItem().is(Items.SHIELD));
    }

    /**
     * Нужный заход удара в долях шкалы, берётся из ползунка в процентах. У булавы свой:
     * её удар о землю сервер читает по падению, а не по полному откату, поэтому ждать
     * там 90 % бессмысленно.
     */
    private float requiredCharge(LocalPlayer player) {
        boolean mace = player.getMainHandItem().is(Items.MACE);
        return (mace ? maceCooldownPercent.get() : cooldownPercent.get()) / 100.0F;
    }

    private double hitReach() {
        return Math.min(attackRange.get(), MAX_SAFE_REACH);
    }

    private static float sendYaw(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot();
    }

    private static float sendPitch(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();
    }

    private static int findAxe(int from, int to) {
        LocalPlayer player = mc.player;
        if (player == null) return -1;
        List<ItemStack> stacks = player.getInventory().getNonEquipmentItems();
        for (int i = from; i < Math.min(to, stacks.size()); i++) {
            if (stacks.get(i).is(ItemTags.AXES)) return i;
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
