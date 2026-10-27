package pub.godweer.kitty.module.impl.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import pub.godweer.auxiliary.player.InventorySwaps;
import pub.godweer.auxiliary.player.InventoryUtil;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.GameTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ModeSetting;

/**
 * Create by daun kvass
 */
public final class AutoTool extends Module {
    public static AutoTool INSTANCE;

    private static final int HOTBAR_SIZE = 9;
    private static final int INVENTORY_SIZE = 36;
    private static final int SILENT_HOTBAR_SLOT = 7;

    public final CheckBox useInventory = checkbox("Use Inventory", true);
    public final ModeSetting mode = mode("Mode", "Silent", "Silent", "Normal");

    private boolean isSwapped = false;
    private int originalHotbarSlot = -1;
    private int swappedInventorySlot = -1;
    private int swappedHotbarSlot = -1;
    private int silentServerSlot = -1;
    private BlockPos currentBreakingPos = null;

    public AutoTool() {
        super("AutoTool", "Автоматически выбирает лучший инструмент для копания", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    protected void onDisable() {
        restore();
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isCreative() || mc.gui.screen() != null) {
            restore();
            return;
        }

        if (!isBreakingBlock()) {
            restore();
            return;
        }

        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (pos.equals(currentBreakingPos)) {
            return;
        }

        if (originalHotbarSlot == -1) {
            originalHotbarSlot = player.getInventory().getSelectedSlot();
        }

        int bestSlot = findBestToolSlot(player, state);
        if (bestSlot == -1) {
            return;
        }

        currentBreakingPos = pos;

        if (mode.is("Silent")) {
            if (bestSlot < HOTBAR_SIZE) {
                setSilentCarriedSlot(player, bestSlot);
            } else if (useInventory.getValue()) {
                InventorySwaps.grimSwapToHotbar(player, bestSlot, SILENT_HOTBAR_SLOT);
                swappedInventorySlot = bestSlot;
                swappedHotbarSlot = SILENT_HOTBAR_SLOT;
                isSwapped = true;
                setSilentCarriedSlot(player, SILENT_HOTBAR_SLOT);
            }
        }
    }

    private void setSilentCarriedSlot(LocalPlayer player, int hotbarSlot) {
        if (hotbarSlot == player.getInventory().getSelectedSlot()) {
            restoreSilentServerSlot(player);
            return;
        }

        if (this.silentServerSlot != hotbarSlot && player.connection != null) {
            player.connection.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
            this.silentServerSlot = hotbarSlot;
        }
    }

    private boolean isBreakingBlock() {
        return mc.options.keyAttack.isDown()
                && mc.hitResult instanceof BlockHitResult blockHit
                && mc.level != null
                && !mc.level.isEmptyBlock(blockHit.getBlockPos());
    }

    public int findBestToolSlot(LocalPlayer player, BlockState state) {
        int limit = useInventory.getValue() ? INVENTORY_SIZE : HOTBAR_SIZE;
        int bestSlot = -1;
        float bestSpeed = 1.0F;

        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    public float calculateDigSpeed(Player player, ItemStack stack, BlockState state) {
        float speed = stack.getDestroySpeed(state);

        if (speed > 1.0F && mc.level != null) {
            try {
                Holder.Reference<Enchantment> efficiency = mc.level.registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.EFFICIENCY);

                int efficiencyLevel = EnchantmentHelper.getItemEnchantmentLevel(efficiency, stack);
                if (efficiencyLevel > 0 && !stack.isEmpty()) {
                    speed += (float) (efficiencyLevel * efficiencyLevel + 1);
                }
            } catch (Exception ignored) {
            }
        }

        if (player.hasEffect(MobEffects.HASTE)) {
            MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
            if (haste != null) {
                speed *= 1.0F + (float) (haste.getAmplifier() + 1) * 0.2F;
            }
        }

        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            MobEffectInstance fatigue = player.getEffect(MobEffects.MINING_FATIGUE);
            if (fatigue != null) {
                float k = switch (fatigue.getAmplifier()) {
                    case 0 -> 0.3F;
                    case 1 -> 0.09F;
                    case 2 -> 0.0027F;
                    default -> 8.1E-4F;
                };
                speed *= k;
            }
        }

        if (player.isEyeInFluid(FluidTags.WATER)) {
            boolean hasAquaAffinity = false;
            if (mc.level != null) {
                try {
                    Holder.Reference<Enchantment> aqua = mc.level.registryAccess()
                            .lookupOrThrow(Registries.ENCHANTMENT)
                            .getOrThrow(Enchantments.AQUA_AFFINITY);

                    hasAquaAffinity = EnchantmentHelper.getItemEnchantmentLevel(aqua, player.getItemBySlot(EquipmentSlot.HEAD)) > 0;
                } catch (Exception ignored) {
                }
            }

            if (!hasAquaAffinity) {
                speed /= 5.0F;
            }
        }

        if (!player.onGround()) {
            speed /= 5.0F;
        }

        return Math.max(speed, 0.0F);
    }

    private void restore() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            restoreSilentServerSlot(player);

            if (isSwapped && swappedInventorySlot != -1 && swappedHotbarSlot != -1) {
                if (mode.is("Silent")) {
                    InventorySwaps.grimSwapToHotbar(player, swappedInventorySlot, swappedHotbarSlot);
                } else {
                    InventoryUtil.swapsinHotbars(swappedInventorySlot, swappedHotbarSlot);
                }
            }

            if (mode.is("Normal") && originalHotbarSlot != -1) {
                player.getInventory().setSelectedSlot(originalHotbarSlot);
            }
        }

        isSwapped = false;
        swappedInventorySlot = -1;
        swappedHotbarSlot = -1;
        originalHotbarSlot = -1;
        currentBreakingPos = null;
    }

    private void restoreSilentServerSlot(LocalPlayer player) {
        if (this.silentServerSlot == -1 || player.connection == null) return;
        player.connection.send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
        this.silentServerSlot = -1;
    }
}