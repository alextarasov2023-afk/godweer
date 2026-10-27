package pub.godweer.auxiliary.client.clients;

/**
 * Create by daun kvass
 */
public class ColorUtil {
    public static final int TRANSPARENT = 0x00000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;
    public static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    public static int lerp(int from, int to, float delta) {
        float t = clamp01(delta);
        return pack(
                Math.round(red(from) + (red(to) - red(from)) * t),
                Math.round(green(from) + (green(to) - green(from)) * t),
                Math.round(blue(from) + (blue(to) - blue(from)) * t),
                Math.round(alpha(from) + (alpha(to) - alpha(from)) * t)
        );
    }
    public static int fromHsv(float h, float s, float v, int alpha) {
        int rgb = java.awt.Color.HSBtoRGB(h, s, v);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static float[] toHsv(int color) {
        float[] hsv = new float[3];
        java.awt.Color.RGBtoHSB(red(color), green(color), blue(color), hsv);
        return hsv;
    }
    public static int pack(int red, int green, int blue, int alpha) {
        return (clamp255(alpha) << 24)
                | (clamp255(red) << 16)
                | (clamp255(green) << 8)
                | clamp255(blue);
    }
    public static int rgb(int red, int green, int blue) {
        return pack(red, green, blue, 255);
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int withAlpha(int color, int alpha) {
        return (clamp255(alpha) << 24) | (color & 0x00FFFFFF);
    }

    public static int withAlpha(int color, float alpha) {
        return withAlpha(color, toChannel(alpha));
    }

    public static int multiplyAlpha(int color, float factor) {
        return withAlpha(color, Math.round(alpha(color) * clamp01(factor)));
    }

    public static int applyAlpha(int color, float alpha) {
        return withAlpha(color, alpha);
    }


    private static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        return Math.min(value, 1f);
    }

    private static int clamp255(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 255);
    }
    public static float[] getRGBAFloat(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                ((color >> 24) & 0xFF) / 255.0f
        };
    }
    public static int interpolateColor(int color1, int color2, float factor) {
        if (factor <= 0.0F) return color1;
        if (factor >= 1.0F) return color2;

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    private static int toChannel(float value) {
        return clamp255(Math.round(clamp01(value) * 255f));
    }
}