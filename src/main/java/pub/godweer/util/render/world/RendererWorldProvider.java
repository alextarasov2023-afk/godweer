package pub.godweer.util.render.world;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/**
 * Create by daun kvass
 */
public record RendererWorldProvider(LevelRenderState levelRenderState, CameraRenderState cameraRenderState, float tickDelta) { }