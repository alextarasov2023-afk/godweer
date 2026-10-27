package pub.godweer.enjoy.mixin.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pub.godweer.kitty.module.impl.player.AutoTool;
import pub.godweer.kitty.module.impl.render.Removals;

/**
 * Create by daun kvass
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Shadow
    protected abstract BlockState asState();
    @Shadow public abstract float getDestroySpeed(BlockGetter blockGetter, BlockPos blockPos);
    @Shadow public abstract boolean requiresCorrectToolForDrops();

    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void onGetDestroyProgress(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        AutoTool autoTool = AutoTool.INSTANCE;
        if (autoTool != null && autoTool.isEnabled() && autoTool.mode.is("Silent") && player == Minecraft.getInstance().player) {
            BlockState state = (BlockState) (Object) this;
            int bestSlot = autoTool.findBestToolSlot((LocalPlayer) player, state);

            if (bestSlot != -1) {
                ItemStack bestTool = player.getInventory().getItem(bestSlot);
                float hardness = this.getDestroySpeed(level, pos);

                if (hardness < 0.0F) {
                    cir.setReturnValue(0.0F);
                    return;
                }

                boolean correctTool = !this.requiresCorrectToolForDrops() || bestTool.isCorrectToolForDrops(state);
                int divider = correctTool ? 30 : 100;

                float digSpeed = autoTool.calculateDigSpeed(player, bestTool, state) / hardness / (float) divider;
                cir.setReturnValue(digSpeed);
            }
        }
    }
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void onGetRenderShape(CallbackInfoReturnable<RenderShape> cir) {
        if (Removals.INSTANCE != null && Removals.INSTANCE.isFoliage(this.asState())) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }
}