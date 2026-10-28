package pub.godweer.util.render.world;

import pub.godweer.module.impl.render.OutlineHands;

/**
 * Диспетчер OutlineHands: прячет конкретные рендеры за одним API,
 * чтобы GameRendererMixin не знал про режимы модуля.
 */
public final class OutlineHandsRenderer {
    private final GlowHandsRenderer glow = new GlowHandsRenderer();
    private final FireHandsRenderer fire = new FireHandsRenderer();
    private final OutlineRimRenderer rim = new OutlineRimRenderer();

    /** Этап 1: вызывается ДО открытия vanilla-пасса рук (encoder свободен). */
    public void prepareSnapshot(OutlineHands module) {
        if (module.isGlowStyle()) {
            this.glow.prepareSnapshot();
        } else if (module.isFireStyle()) {
            this.fire.prepareSnapshot(module);
        } else {
            this.rim.prepareSnapshot();
        }
    }

    /** Этап 3: вызывается ПОСЛЕ vanilla-пасса рук. */
    public void compose(OutlineHands module) {
        if (module.isGlowStyle()) {
            this.glow.compose(module);
        } else if (module.isFireStyle()) {
            this.fire.compose(module);
        } else {
            this.rim.compose(module);
        }
    }

    /** Безопасный старт огненного шлейфа. */
    public void resetTrails() {
        this.fire.resetTrails();
    }

    public void release() {
        this.glow.release();
        this.fire.release();
        this.rim.release();
    }
}
