package pub.godweer.util.render.world;

import pub.godweer.Client;
import pub.godweer.util.render.world.*;
import pub.godweer.module.impl.render.*;
import pub.godweer.util.target.TargetEspRenderer;
import pub.godweer.util.target.LightningEspRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Create by daun kvass
 */
public final class MiasmWorlds {
    private static final List<AffectedWorlds> Affected = new ArrayList<>();

    private MiasmWorlds() {
    }

    public static void processed() {
        if (!Affected.isEmpty()) {
            return;
        }

        register(AffectedWorlds.lazy(
                () -> gate(Client.INSTANCE.moduleManager.getAmbience(), Ambience::usesSky),
                AmbienceRenderer::new,
                (feature, renderer, context) -> renderer.renderSky(feature, context.cameraRenderState()),
                AmbienceRenderer::release
        ));
        register(AffectedWorlds.lazy(
                () -> {
                    ShaderSky sky = Client.INSTANCE.moduleManager.getModule(ShaderSky.class);
                    return (sky != null && sky.isState()) ? sky : null;
                },
                ShaderSkyRenderer::new,
                (feature, renderer, context) -> renderer.render(feature, context.cameraRenderState()),
                ShaderSkyRenderer::release
        ));
        register(AffectedWorlds.lazy(
                () -> {
                    Rain rain = Client.INSTANCE.moduleManager.getRain();
                    boolean weatherDisabled = Removals.INSTANCE != null && Removals.INSTANCE.isWeatherDisabled();
                    return rain != null && rain.isState() && !weatherDisabled ? rain : null;
                },
                RainRenderer::new,
                (module, renderer, context) -> renderer.render(module, context.tickDelta()),
                RainRenderer::release
        ));

    }

    public static void register(AffectedWorlds effect) {
        Affected.add(effect);
    }

    public static void render(RendererWorldProvider context) {
        if (Affected.isEmpty()) {
            processed();
        }
        for (AffectedWorlds afffect : Affected) {
            if (afffect.active()) {
                afffect.render(context);
            } else {
                afffect.release();
            }
        }
    }

    private static <F> F gate(F module, java.util.function.Predicate<F> enabled) {
        return module != null && enabled.test(module) ? module : null;
    }
}