package net.minecraft.client.renderer.rendertype;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import pub.godweer.util.render.pipeline.PiplinePost;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GodWeer chams RenderTypes. Lives in the vanilla package to access
 * the package-private RenderType.create.
 *
 * Every style in {@link PiplinePost#CHAMS_STYLES} has a depth-tested pass and
 * a depth-ignoring "through walls" twin (same fragment shader, ALWAYS_PASS
 * depth compare). Types are built on first use: the pipelines themselves are
 * already registered by the time anything asks for a RenderType.
 */
public final class ChamsRenderTypes {

    private static final Map<String, RenderType> TYPES = new ConcurrentHashMap<>();

    public static RenderType of(String style, boolean through) {
        return TYPES.computeIfAbsent(style + (through ? "|through" : "|normal"), key -> {
            PiplinePost.ChamsPasses passes = PiplinePost.CHAMS_ENTITY.get(style);
            if (passes == null) {
                throw new IllegalArgumentException("unknown chams style: " + style);
            }
            RenderPipeline pipeline = through ? passes.through() : passes.normal();
            // Any render type whose pipeline blends is routed to the OIT pass when
            // Transparency is on "shader", and that pass throws for pipelines with no
            // OIT set. Forcing the solid model phase keeps the classic blended draw —
            // the same escape vanilla uses for wolf_armor_cracks.
            return RenderType.create("godweer_chams_" + style + (through ? "_through" : ""),
                    RenderSetup.builder(pipeline).withForcedSolidModelPhase().createRenderSetup());
        });
    }

    private ChamsRenderTypes() {
    }
}
