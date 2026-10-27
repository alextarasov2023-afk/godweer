package pub.godweer.auxiliary.client.target;

import net.minecraft.world.entity.LivingEntity;
import pub.godweer.kitty.module.impl.combat.AuraModule;
import pub.godweer.auxiliary.client.clients.Theme;

/**
 * Create by daun kvass
 */
public final class TargetMarkers {
    public static final TargetMarkers INSTANCE = new TargetMarkers();

    private final AuraMarkerRenderer marker = new AuraMarkerRenderer();
    private final GhostTargetRenderer ghost = new GhostTargetRenderer();
    private final CircleTargetRenderer circle = new CircleTargetRenderer();
    private final CubesTargetRenderer cubes = new CubesTargetRenderer();

    public void render(AuraModule module, float tickDelta) {
        LivingEntity target = (module != null && module.isState()) ? module.getTarget() : null;

        boolean isGhosts = module != null && module.usesGhostTargetEsp();
        boolean isCircle = module != null && module.usesCircleTargetEsp();
        boolean isMarker = module != null && module.usesMarkerTargetEsp();
        boolean isCubes = module != null && module.usesCubesTargetEsp();

        int color = Theme.getAccentColor();

        this.marker.render(isMarker ? target : null, tickDelta, color);
        this.ghost.render(isGhosts ? target : null, tickDelta, color, 1.0F);
        this.circle.render(isCircle ? target : null, tickDelta, color);
        this.cubes.render(isCubes ? target : null, tickDelta, color);
    }

    public boolean hasActive() {
        return this.ghost.hasActive() || this.circle.hasActive() || this.cubes.hasActive() ;
    }

    public void release() {
        this.marker.release();
        this.ghost.reset();
        this.circle.reset();
        this.cubes.release();
    }
}