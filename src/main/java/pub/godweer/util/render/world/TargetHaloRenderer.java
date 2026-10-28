package pub.godweer.util.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

public class TargetHaloRenderer {
    private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("godweer", "textures/particle/glow.png");

    public static void renderHalo(LivingEntity target, com.mojang.blaze3d.vertex.PoseStack matrices, float tickDelta, float globalAlpha) {
        // placeholder: no-op to keep compile; full implementation requires version-specific render code
    }
}