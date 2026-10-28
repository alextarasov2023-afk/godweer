package pub.godweer.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.rendertype.RenderType;
import pub.godweer.module.impl.render.ChamsHook;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void godweerChamsRenderType(LivingEntityRenderState renderState, boolean b1, boolean b2, boolean b3,
                                        CallbackInfoReturnable<RenderType> cir) {
        RenderType chams = ChamsHook.renderType(renderState);
        if (chams != null) cir.setReturnValue(chams);
    }

    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
    private void godweerChamsTint(LivingEntityRenderState renderState, CallbackInfoReturnable<Integer> cir) {
        Integer tint = ChamsHook.tint(renderState);
        if (tint != null) cir.setReturnValue(tint);
    }

    /**
     * Through-walls ghost pass of the chams body: wraps the vanilla
     * submitModel call and, when Through Walls is on, emits an extra
     * depth-ignoring draw before the normal depth-tested one. The main
     * render type is always the normal (depth-tested) chams type.
     */
    @WrapOperation(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"
            )
    )
    @SuppressWarnings({"unchecked", "rawtypes", "RedundantCast"})
    private void godweerChamsBodySecondPass(SubmitNodeCollector collector,
                                            Model model,
                                            Object state,
                                            com.mojang.blaze3d.vertex.PoseStack poseStack,
                                            RenderType renderType,
                                            int i1,
                                            int i2,
                                            int i3,
                                            net.minecraft.client.renderer.texture.UvMapping uvMapping,
                                            int outline,
                                            Operation<Void> original) {
        if (state instanceof LivingEntityRenderState living) {
            RenderType through = ChamsHook.throughType(living);
            Integer throughTint = ChamsHook.throughTint(living);
            if (through != null && throughTint != null) {
                collector.order(living.outlineColor).submitModel(
                        model,
                        living,
                        poseStack,
                        through,
                        i1,
                        i2,
                        throughTint,
                        null,
                        outline
                );
            }
        }
        original.call(collector, model, state, poseStack, renderType, i1, i2, i3, uvMapping, outline);
    }
}
