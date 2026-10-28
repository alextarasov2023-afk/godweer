package pub.godweer.util.render.world;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Create by daun kvass
 */
public interface AffectedWorlds {

    boolean active();

    void render(RendererWorldProvider context);

    default void release() {
    }

    static <F> AffectedWorlds direct(Supplier<F> gate, BiConsumer<F, RendererWorldProvider> render) {
        return new AffectedWorlds() {
            private F feature;

            @Override
            public boolean active() {
                this.feature = gate.get();
                return this.feature != null;
            }

            @Override
            public void render(RendererWorldProvider context) {
                render.accept(this.feature, context);
            }
        };
    }

    static <F, R> AffectedWorlds lazy(Supplier<F> gate, Supplier<R> factory, RenderCall<F, R> render, Consumer<R> release) {
        return new AffectedWorlds() {
            private F feature;
            private R renderer;

            @Override
            public boolean active() {
                this.feature = gate.get();
                return this.feature != null;
            }

            @Override
            public void render(RendererWorldProvider context) {
                if (this.renderer == null) {
                    this.renderer = factory.get();
                }
                render.render(this.feature, this.renderer, context);
            }

            @Override
            public void release() {
                if (this.renderer != null) {
                    release.accept(this.renderer);
                    this.renderer = null;
                }
            }
        };
    }

    @FunctionalInterface
    interface RenderCall<F, R> {
        void render(F feature, R renderer, RendererWorldProvider context);
    }
}