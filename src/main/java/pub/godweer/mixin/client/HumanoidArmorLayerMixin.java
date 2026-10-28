package pub.godweer.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pub.godweer.module.impl.render.ChamsHook;

/**
 * Paints armor with the chams RenderTypes (body already handled
 * via LivingEntityRendererMixin). Two passes when Through Walls is on:
 * first the depth-ignoring ghost pass, then the normal depth-tested pass.
 *
 * With Chams -> Hide Armor the layer is not submitted at all, so the painted
 * body stays a single clean silhouette. renderArmorPiece has exactly one draw
 * call in 26.3 (EquipmentLayerRenderer.renderLayers), so skipping it here skips
 * every armor piece, trims included.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

    @WrapOperation(
            method = "renderArmorPiece",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/EquipmentLayerRenderer;renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;II)V"
            )
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void godweerChamsArmor(EquipmentLayerRenderer instance,
                                   EquipmentClientInfo.LayerType layerType,
                                   ResourceKey<?> assetKey,
                                   Model<?> armorModel,
                                   Object rawState,
                                   ItemStack stack,
                                   PoseStack poseStack,
                                   SubmitNodeCollector collector,
                                   int light,
                                   int outline,
                                   Operation<Void> original) {
        if (rawState instanceof LivingEntityRenderState state) {
            if (ChamsHook.hidesArmor(state)) return;
            RenderType normal = ChamsHook.normalType(state);
            if (normal != null) {
                Integer tint = ChamsHook.tint(state);
                if (tint != null) {
                    RenderType through = ChamsHook.throughType(state);
                    Integer throughTint = ChamsHook.throughTint(state);
                    if (through != null && throughTint != null) {
                        godweerSubmitChams(collector, (Model) armorModel, state, poseStack, through, light, throughTint);
                    }
                    godweerSubmitChams(collector, (Model) armorModel, state, poseStack, normal, light, tint);
                    return;
                }
            }
        }
        original.call(instance, layerType, assetKey, armorModel, rawState, stack, poseStack, collector, light, outline);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void godweerSubmitChams(SubmitNodeCollector collector,
                                           Model armorModel,
                                           LivingEntityRenderState state,
                                           PoseStack poseStack,
                                           RenderType renderType,
                                           int light,
                                           int tint) {
        collector.order(state.outlineColor).submitModel(
                armorModel,
                state,
                poseStack,
                renderType,
                light,
                LivingEntityRenderer.getOverlayCoords(state, 0.0F),
                tint,
                null,
                state.outlineColor
        );
    }
}
