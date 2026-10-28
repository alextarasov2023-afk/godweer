package pub.godweer.module.impl.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.util.player.InventorySwaps;
import pub.godweer.util.player.MoveBlockUtility;

import java.util.List;

/**
 * Create by daun kvass
 */
public final class AutoTotem extends Module {

    private static AutoTotem INSTANCE;

    private static final int CHESTPLATE_SLOT = 6;
    private static final int OFFHAND_BUTTON = 40;

    private final HeaderSetting hys = header("Main");
    public final ModeSetting mode = mode("Bypass", "GrimPacket", "GrimPacket", "Matrix", "Default");
    public final SliderSetting health = slider("Health", 10.0f, 1.0f, 20.0f, 0.5f);
    public final CheckBox swapBack = checkbox("Back", true);
    public final CheckBox saveEnchanted = checkbox("Save enchanted", true);
    public final CheckBox noswapinvg = checkbox("No swap in Screen",false);
    private final HeaderSetting ss = header("Checks");
    public final CheckBox checkUsing = checkbox("Check using", true);
    public final CheckBox elytraCheck = checkbox("Check elytra", true);
    public final SliderSetting elytraHealth = slider("health in elytra", 16.0f, 1.0f, 20.0f, 0.5f);
    public final CheckBox crystalCheck = checkbox("Check Crystal", true);
    public final SliderSetting crystalDistance = slider("Distance crystal", 6.0f, 1.0f, 12.0f, 0.5f);
    public final CheckBox anchorCheck = checkbox("CheckAnchor", false);
    public final SliderSetting anchordistance = slider("Distance Anchor", 4.0f, 1.0f, 12.0f, 0.5f);

    private enum State {
        IDLE,
        STOP_SPRINT,
        EXECUTE,
        COOLDOWN
    }

    private State state = State.IDLE;
    private int cooldownTicks = 0;

    private Item previousItem = Items.AIR;
    private boolean previousWasEnchanted = false;
    private boolean needSwapBack = false;
    private int targetSlot = -1;

    public AutoTotem() {
        super("AutoTotem", "Автоматически берет тотем в левую руку", Category.COMBAT);
        INSTANCE = this;
    }


    public static boolean isSwapping() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.state != State.IDLE;
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        cooldownTicks = 0;
        targetSlot = -1;
        previousItem = Items.AIR;
        previousWasEnchanted = false;
        needSwapBack = false;
        MoveBlockUtility.unblock(this);
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (!inGame() || player() == null || mc.gameMode == null) {
            reset();
            return;
        }

        LocalPlayer player = player();

        if (mc.gui.screen() != null && noswapinvg.getValue()) {
            return;
        }

        if (checkUsing.getValue() && player.isUsingItem()) {
            return;
        }

        boolean dangerous = isDangerous(player);
        ItemStack offhand = player.getOffhandItem();
        boolean offhandHasTotem = offhand.is(Items.TOTEM_OF_UNDYING);

        if (state == State.IDLE) {
            if (dangerous) {
                if (!offhandHasTotem) {
                    int totemSlot = findTotemSlot(player);
                    if (totemSlot != -1) {
                        if (swapBack.getValue() && !needSwapBack && !offhand.isEmpty()) {
                            previousItem = offhand.getItem();
                            previousWasEnchanted = offhand.isEnchanted();
                            needSwapBack = true;
                        }

                        performSwap(player, totemSlot);
                        return;
                    }
                } else if (saveEnchanted.getValue() && offhand.isEnchanted()) {
                    int normalSlot = findNormalTotemSlot(player);
                    if (normalSlot != -1) {
                        if (swapBack.getValue() && !needSwapBack) {
                            previousItem = Items.TOTEM_OF_UNDYING;
                            previousWasEnchanted = true;
                            needSwapBack = true;
                        }

                        performSwap(player, normalSlot);
                        return;
                    }
                }
            } else {
                if (swapBack.getValue() && needSwapBack && offhandHasTotem) {
                    if (previousItem != null && previousItem != Items.AIR) {
                        int returnSlot = findItemSlot(player, previousItem, previousWasEnchanted);
                        if (returnSlot != -1) {
                            performSwap(player, returnSlot);
                        }
                    }
                    needSwapBack = false;
                    previousItem = Items.AIR;
                    previousWasEnchanted = false;
                    return;
                } else if (!offhandHasTotem) {
                    needSwapBack = false;
                    previousItem = Items.AIR;
                    previousWasEnchanted = false;
                }
            }
        }

        switch (state) {
            case STOP_SPRINT -> {
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }

                MoveBlockUtility.freeze(this, 4);
                MoveBlockUtility.blockSprint(this, 5);

                state = State.EXECUTE;
            }

            case EXECUTE -> {
                if (targetSlot != -1) {
                    mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, targetSlot, OFFHAND_BUTTON, ContainerInput.SWAP, player);
                    targetSlot = -1;
                }

                cooldownTicks = 2;
                state = State.COOLDOWN;
            }

            case COOLDOWN -> {
                MoveBlockUtility.freeze(this, 2);

                if (--cooldownTicks <= 0) {
                    state = State.IDLE;
                    MoveBlockUtility.unblock(this);
                }
            }

            case IDLE -> {
            }
        }
    }

    private void performSwap(LocalPlayer player, int slot) {
        if (slot == -1) return;

        if (mode.is("GrimPacket")) {
            InventorySwaps.grimSwapsOffHand(player, slot);
            state = State.COOLDOWN;
        } else if (mode.is("Matrix")) {
            if (slot >= InventoryMenu.USE_ROW_SLOT_START && slot < InventoryMenu.USE_ROW_SLOT_END) {
                int hotbarIndex = slot - InventoryMenu.USE_ROW_SLOT_START;
                InventorySwaps.performHotbarFSwap(player, hotbarIndex);
                state = State.COOLDOWN;
            } else {
                targetSlot = slot;
                state = State.STOP_SPRINT;
            }
        } else {
            targetSlot = slot;
            state = State.STOP_SPRINT;
        }
    }

    private boolean isDangerous(LocalPlayer player) {
        if (crystalCheck.getValue() && isCrystalNear(player, crystalDistance.getValue())) {
            return true;
        }

        if (anchorCheck.getValue() && isAnchorNear(player, anchordistance.getValue())) {
            return true;
        }

        float totalHealth = player.getHealth() + player.getAbsorptionAmount();

        boolean isWearingElytra = player.inventoryMenu.getSlot(CHESTPLATE_SLOT).getItem().is(Items.ELYTRA);
        if (elytraCheck.getValue() && (player.isFallFlying() || isWearingElytra)) {
            if (totalHealth <= elytraHealth.getValue()) {
                return true;
            }
        }

        return totalHealth <= health.getValue();
    }

    private int findTotemSlot(LocalPlayer player) {
        int normalSlot = -1;
        int enchantedSlot = -1;

        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
            ItemStack stack = player.inventoryMenu.getSlot(i).getItem();
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                if (stack.isEnchanted()) {
                    if (enchantedSlot == -1) enchantedSlot = i;
                } else {
                    if (normalSlot == -1) normalSlot = i;
                }
            }
        }

        return normalSlot != -1 ? normalSlot : enchantedSlot;
    }

    private int findNormalTotemSlot(LocalPlayer player) {
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
            ItemStack stack = player.inventoryMenu.getSlot(i).getItem();
            if (stack.is(Items.TOTEM_OF_UNDYING) && !stack.isEnchanted()) {
                return i;
            }
        }
        return -1;
    }

    private int findEnchantedTotemSlot(LocalPlayer player) {
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
            ItemStack stack = player.inventoryMenu.getSlot(i).getItem();
            if (stack.is(Items.TOTEM_OF_UNDYING) && stack.isEnchanted()) {
                return i;
            }
        }
        return -1;
    }

    private int findItemSlot(LocalPlayer player, Item item, boolean mustBeEnchanted) {
        if (item == null || item == Items.AIR) return -1;

        if (item == Items.TOTEM_OF_UNDYING && mustBeEnchanted) {
            int enchantedTotem = findEnchantedTotemSlot(player);
            if (enchantedTotem != -1) return enchantedTotem;
        }

        int fallback = -1;
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
            ItemStack stack = player.inventoryMenu.getSlot(i).getItem();
            if (stack.is(item)) {
                if (mustBeEnchanted) {
                    if (stack.isEnchanted()) return i;
                } else {
                    if (!stack.isEnchanted()) return i;
                    if (fallback == -1) fallback = i;
                }
            }
        }
        return fallback != -1 ? fallback : -1;
    }

    private boolean isCrystalNear(LocalPlayer player, double radius) {
        if (mc.level == null) return false;
        List<EndCrystal> crystals = mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(radius)
        );
        return !crystals.isEmpty();
    }

    private boolean isAnchorNear(LocalPlayer player, double radius) {
        if (mc.level == null) return false;

        if (mc.level.dimension() == Level.NETHER) {
            return false;
        }

        int minX = Mth.floor(player.getX() - radius);
        int maxX = Mth.floor(player.getX() + radius);
        int minY = Mth.floor(player.getY() - radius);
        int maxY = Mth.floor(player.getY() + radius);
        int minZ = Mth.floor(player.getZ() - radius);
        int maxZ = Mth.floor(player.getZ() + radius);

        double radiusSq = radius * radius;

        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radiusSq) {
                BlockState state = mc.level.getBlockState(pos);
                if (state.is(Blocks.RESPAWN_ANCHOR)) {
                    return true;
                }
            }
        }
        return false;
    }
}