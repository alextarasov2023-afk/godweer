package pub.godweer.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.module.impl.render.ChamsHook;

/**
 * Плащ (и крылья-элитры того же слоя) — единственный слой тела, который не
 * перехватывает ни LivingEntityRendererMixin, ни HumanoidArmorLayerMixin:
 * он рисуется ванильным entitySolid уже после chams-тела, которое не пишет
 * глубину, и поэтому оказывается виден прямо через торс. См. ChamsHook.hidesCape.
 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void godweerChamsHideCape(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                      AvatarRenderState state, float f1, float f2, CallbackInfo ci) {
        if (ChamsHook.hidesCape(state)) ci.cancel();
    }
}
