package pub.godweer.util.math.anim;

public final class Easings {

    public interface Easing {
        double ease(double value);
        default float easef(float value) { return (float) ease(value); }
    }

    public static final String[] NAMES = {
            "Linear", "QuadOut", "QuadInOut", "CircOut", "BackOut"
    };

    public static final Easing LINEAR = v -> v;
    public static final Easing QUAD_OUT = v -> 1.0D - Math.pow(1.0D - v, 2.0D);
    public static final Easing QUAD_IN_OUT = v -> v < 0.5D ? 2.0D * v * v : 1.0D - Math.pow(-2.0D * v + 2.0D, 2.0D) / 2.0D;
    public static final Easing CIRC_OUT = v -> Math.sqrt(1.0D - Math.pow(v - 1.0D, 2.0D));
    public static final Easing BACK_OUT = v -> 1.0D + 2.70158D * Math.pow(v - 1.0D, 3.0D) + 1.70158D * Math.pow(v - 1.0D, 2.0D);

    public static float ease(String name, float value) {
        if (name == null || name.equalsIgnoreCase("Linear")) return value;
        return switch (name) {
            case "QuadOut" -> QUAD_OUT.easef(value);
            case "QuadInOut" -> QUAD_IN_OUT.easef(value);
            case "CircOut" -> CIRC_OUT.easef(value);
            case "BackOut" -> BACK_OUT.easef(value);
            default -> value;
        };
    }
}