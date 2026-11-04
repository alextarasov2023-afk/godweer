package pub.godweer.module.impl.combat;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
 * KillAura2 — второй перенос «Aura»: наводка снапом (SMOOTH со скоростями 360), точка
 * на 60 % высоты, на элитрах упреждение по смешанному вектору взгляда цели.
 * <p>
 * Отличие от KillAura1: поиск цели одним проходом по приоритету, проверка дистанции от
 * центра хитбокса (ближе 3 блоков) или от ближайшей точки, шкала заряда с запасом в
 * 1.5 тика и полный путь ломки щита — топор достаётся из инвентаря свапом и бьётся в
 * тихой подмене слота.
 */
public class KillAura2 extends Module {
    public static KillAura2 INSTANCE;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final long ATTACK_COOLDOWN_MS = 467L;
    /** Заряд удара; шкала берётся с запасом в 1.5 тика, как в оригинале. */
    private static final float MIN_CHARGE = 0.85F;
    /** Дальше ванильного порога удар — это флаг Reach-Hitbox. */
    private static final float MAX_SAFE_REACH = 2.92F;
    /** Центр хитбокса берём в расчёт дистанции, пока цель не дальше этого. */
    private static final double CENTER_DISTANCE = 3.0D;

    private final HeaderSetting targetHeader = header("Targets");
    private final MultiModeSetting targets = multiMode("Targets",
            List.of("Players", "Naked", "Invisible", "Mobs"),
            "Players", "Naked", "Invisible", "Peaceful", "Mobs");
    private final SliderSetting attackRange = slider("Attack Range", 3.0f, 0.0f, 6.0f, 0.05f);
    private final SliderSetting aimRange = slider("Aim Range", 3.0f, 0.0f, 6.0f, 0.05f);
    private final SliderSetting glideDistance = slider("Glide Distance", 50.0f, 10.0f, 100.0f, 0.05f);
    private final ModeSetting priority = mode("Priority", "Distance", "Distance", "Health", "Angle", "None");

    private final HeaderSetting attackHeader = header("Attack");
    private final CheckBox throughWalls = checkbox("Through Walls", true);
    private final CheckBox checkAim = checkbox("Check Aim", false);
    private final CheckBox noAttackWhileEating = checkbox("Pause while Using", true);
    private final CheckBox sprintReset = checkbox("Sprint Reset", true);
    private final CheckBox releaseShield = checkbox("Release Shield", false);
    private final CheckBox shieldBreak = checkbox("Shield Break", true);

    private final HeaderSetting rotationHeader = header("Rotation");
    private final CheckBox clientLook = checkbox("Client Look", false);
    private final ModeSetting movement = mode("Movement", "None", "None", "Free", "Focus");

    private LivingEntity target;
    private long nextAttackAt = 0L;
    private boolean needSprintReset = false;
    private boolean sprintResetDone = false;
    private int sprintResetTicks = 0;
    private InteractionHand shieldHand = null;
    private boolean rotationOwned = false;

    public KillAura2() {
        super("KillAura2", "Снап-аура с ломкой щита топором из инвентаря и наводкой "
                + "по смешанному вектору на элитрах", Category.COMBAT);
        INSTANCE = this;
    }

    /** Окно, в котором KeyboardInputMixin гасит шаг вперёд: сброс спринта без setSprinting. */
    public boolean sprintHoldActive() {
        return isEnabled() && needSprintReset;
    }

    @Override
    protected void onEnable() {
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
        nextAttackAt = 0L;
        shieldHand = null;
        target = null;
    }

    @Override
    protected void onDisable() {
        target = null;
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
        shieldHand = null;
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
        if (!isEnabled() || player() == null || target == null) return;
        if (movement.is("Focus")) {
            MoveUtility.fixMovementFocus(event, target);
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

        updateTarget(player);
        rotate(player);
        processAttack(player);
    }

    // ------------------------------------------------------------------ таргеты

    private void updateTarget(LocalPlayer player) {
        if (target != null && isValidTarget(target, player)) return;
        target = findTarget(player);
    }

    /** Один проход по живым сущностям: победитель по выбранному приоритету. */
    private LivingEntity findTarget(LocalPlayer player) {
        double range = maxAimRange(player) + 1.0D;
        AABB area = player.getBoundingBox().inflate(range);
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player);

        LivingEntity best = null;
        double bestValue = Double.MAX_VALUE;
        String mode = priority.getValue();

        for (LivingEntity living : candidates) {
            if (!isValidTarget(living, player)) continue;
            if (best == null || "None".equals(mode)) {
                best = living;
                if ("None".equals(mode)) break;
                continue;
            }

            double value = switch (mode) {
                case "Health" -> living.getHealth();
                case "Angle" -> angleError(living, player);
                default -> squaredDistanceToHitbox(living, player);
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
            if (FriendManager.getInstance().isFriend(other)) return false;
            if (!targets.isEnabled("Players")) return false;
            if (isNaked(other) && !targets.isEnabled("Naked")) return false;
            if (entity.isInvisible() && !targets.isEnabled("Invisible")) return false;
        } else if (entity instanceof Mob mob) {
            // Monster == hostile, всё остальное живое — «мирные» оригинала.
            if (mob instanceof Enemy ? !targets.isEnabled("Mobs") : !targets.isEnabled("Peaceful")) {
                return false;
            }
        } else {
            return false;
        }

        Vec3 eyes = player.getEyePosition();
        double maxAim = maxAimRange(player);
        if (eyes.distanceTo(UBoxPoints.getClosestBoxPoint(eyes, entity.getBoundingBox())) > maxAim) {
            return false;
        }
        if (!throughWalls.getValue() && !RayTraceUtils.canSeePoint(eyes, aimPoint(entity, player))) {
            return false;
        }
        return true;
    }

    private double squaredDistanceToHitbox(LivingEntity entity, LocalPlayer player) {
        Vec3 eyes = player.getEyePosition();
        return eyes.distanceToSqr(UBoxPoints.getClosestBoxPoint(eyes, entity.getBoundingBox()));
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
        return player.isFallFlying() ? glideDistance.get() : attackRange.get() + aimRange.get();
    }

    // ------------------------------------------------------------------ ротация

    private static Vec3 boxCenter(LivingEntity entity) {
        return entity.getBoundingBox().getCenter();
    }

    /**
     * Точка оригинала: 60 % высоты от ног. На элитрах к ней добавляется половина
     * смешанного вектора взгляда цели — упреждение по тому, куда она смотрит.
     */
    private Vec3 aimPoint(LivingEntity entity, LocalPlayer player) {
        Vec3 base = entity.position().add(0.0D, entity.getBbHeight() * 0.6D, 0.0D);
        if (!player.isFallFlying()) return base;

        Vec3 mixed = entity.getViewVector(1.0F).add(entity.getViewVector(0.5F)).normalize();
        return base.add(mixed.scale(0.5D));
    }

    private void rotate(LocalPlayer player) {
        if (target == null) {
            releaseRotation();
            return;
        }

        Angle aim = Angle.fromVec(player.getEyePosition(), aimPoint(target, player));
        if (clientLook.getValue()) {
            Angle snapped = aim.applyGCD();
            player.setYRot(snapped.getYaw());
            player.setXRot(snapped.getPitch());
            player.yHeadRot = snapped.getYaw();
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
        if (player.getAttackStrengthScale(1.5F) < MIN_CHARGE) return false;

        boolean bothGliding = player.isFallFlying() && target.isFallFlying();
        if (checkAim.getValue() && !bothGliding && !RayTraceUtils.isLookingAt(target,
                maxAimRange(player) * 2.0D, sendYaw(player), sendPitch(player), false)) {
            return false;
        }

        Vec3 eyes = player.getEyePosition();
        Vec3 center = boxCenter(target);
        double centerDistance = eyes.distanceTo(center);
        Vec3 point = centerDistance > CENTER_DISTANCE
                ? UBoxPoints.getClosestBoxPoint(eyes, target.getBoundingBox())
                : center;
        return eyes.distanceTo(point) <= hitReach();
    }

    private void attack(LocalPlayer player) {
        if (releaseShield.getValue() && player.isBlocking() && player.getUseItem().is(Items.SHIELD)) {
            shieldHand = player.getUsedItemHand();
            gameMode().releaseUsingItem(player);
        } else {
            shieldHand = null;
        }

        boolean struck = false;
        if (shieldBreak.getValue() && isShieldBlocking(target)) {
            struck = axeStrike(player, target);
        }
        if (!struck) gameMode().attack(player, target);

        SwingAnimation swingAnim = player.getMainHandItem().getAttackAnimation();
        player.swing(InteractionHand.MAIN_HAND, swingAnim, false);

        if (shieldHand != null) {
            restoreShieldBlocking(player);
            shieldHand = null;
        }

        nextAttackAt = System.currentTimeMillis() + ATTACK_COOLDOWN_MS;
    }

    private static boolean isShieldBlocking(LivingEntity entity) {
        return entity != null && entity.isBlocking() && entity.getUseItem().is(Items.SHIELD);
    }

    /**
     * Ломка щита топором. Сначала тихая подмена слота, если топор в хотбаре; если его
     * там нет — свап из инвентаря в свободный слот хотбара, удар и обратный свап.
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
        if (slot != selected) setSlot(selected);
        return true;
    }

    private void restoreShieldBlocking(LocalPlayer player) {
        ItemStack stack = player.getItemInHand(shieldHand);
        if (stack.is(Items.SHIELD) && !player.isUsingItem()) {
            gameMode().useItem(player, shieldHand);
        }
    }

    // ------------------------------------------------------------------- утилиты

    private boolean blocksAttackWhileUsing(LocalPlayer player) {
        if (!noAttackWhileEating.getValue()) return false;
        if (!player.isUsingItem()) return false;
        // Свой щит при включённом Release Shield бить не мешает: отожмём и ударим.
        return !(releaseShield.getValue() && player.isBlocking()
                && player.getUseItem().is(Items.SHIELD));
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
