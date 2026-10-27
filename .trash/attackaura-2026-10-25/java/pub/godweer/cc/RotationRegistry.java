package pub.godweer.cc;

import pub.godweer.cc.rotations.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Create by daun kvass
 */
public final class RotationRegistry {
    private static final Map<String, AuraRotation> ROTATIONS = new LinkedHashMap<>();

    static {
        register(new SmoothRotation());
        register(new NoneRotation());
        register(new LinearRotation());
        register(new MatrixRotation());
        register(new SpookyTime());
        register(new FuntimeRotation());
        register(new BuilderRotation());
    }

    public static void register(AuraRotation rotation) {
        ROTATIONS.put(rotation.getName().toLowerCase(), rotation);
    }

    public static AuraRotation get(String name) {
        return ROTATIONS.getOrDefault(name.toLowerCase(), ROTATIONS.values().iterator().next());
    }

    public static String[] getNames() {
        return ROTATIONS.values().stream().map(AuraRotation::getName).toArray(String[]::new);
    }
}