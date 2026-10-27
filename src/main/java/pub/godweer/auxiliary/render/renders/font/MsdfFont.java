package pub.godweer.auxiliary.render.renders.font;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Create by daun kvass
 */
public final class MsdfFont {
    private static final Gson GSON = new Gson();
    private static final Map<Identifier, MsdfFont> CACHE = new ConcurrentHashMap<>();

    private final Identifier textureId;
    private final float distanceRange;
    private final int atlasWidth;
    private final int atlasHeight;
    private final float atlasSize;
    private final float lineHeightEm;
    private final float ascenderEm;
    private final float descenderEm;
    private final Int2ObjectMap<Glyph> glyphs;
    private final Long2FloatMap kerning;
    private final Glyph fallbackGlyph;
    private TextureSetup textureSetup;

    private MsdfFont(Identifier metadataId, FontFile file) {
        this.textureId = metadataId.withPath(path -> path.replace(".json", ".png"));
        this.distanceRange = file.atlas != null ? (float) file.atlas.distanceRange : 2.0F;
        this.atlasWidth = file.atlas != null ? file.atlas.width : 512;
        this.atlasHeight = file.atlas != null ? file.atlas.height : 512;
        this.atlasSize = file.atlas != null ? (float) file.atlas.size : 32.0F;
        this.lineHeightEm = file.metrics != null ? (float) file.metrics.lineHeight : 1.0F;
        this.ascenderEm = file.metrics != null ? (float) file.metrics.ascender : 0.8F;
        this.descenderEm = file.metrics != null ? (float) file.metrics.descender : -0.2F;

        int glyphCount = file.glyphs != null ? file.glyphs.size() : 0;
        this.glyphs = new Int2ObjectOpenHashMap<>(glyphCount);
        if (file.glyphs != null) {
            for (RawGlyph raw : file.glyphs) {
                this.glyphs.put(raw.unicode, Glyph.bake(raw, this.atlasWidth, this.atlasHeight));
            }
        }
        this.fallbackGlyph = this.glyphs.getOrDefault('?', this.glyphs.getOrDefault(' ', new Glyph(0.5F, null, 0, 0, 0, 0)));

        this.kerning = new Long2FloatOpenHashMap();
        this.kerning.defaultReturnValue(0.0F);
        if (file.kerning != null) {
            for (KerningPair pair : file.kerning) {
                this.kerning.put(packKerningKey(pair.unicode1, pair.unicode2), (float) pair.advance);
            }
        }
    }

    public static MsdfFont load(Identifier metadataId) {
        return CACHE.computeIfAbsent(metadataId, MsdfFont::readFont);
    } public float textHeight(float size) {
        return (this.ascenderEm - this.descenderEm) * size;
    }
    public String trimToWidth(String text, float maxWidth, float size) {
        return trimToWidth(text, maxWidth, size, 0.0F);
    }

    public float centeredTextY(float centerY, float size) {
        return centerY - textHeight(size) * 0.5F;
    }
    public float lineHeight(float size) { return this.lineHeightEm * size; }
    public float ascender(float size) { return this.ascenderEm * size; }
    public float descender(float size) { return this.descenderEm * size; }
    public float distanceRange() { return this.distanceRange; }
    public int atlasWidth() { return this.atlasWidth; }
    public int atlasHeight() { return this.atlasHeight; }
    public float atlasSize() { return this.atlasSize; }
    public float localPxPerSdfUnit(float size) { return this.distanceRange * size / this.atlasSize; }

    public Glyph glyph(int codePoint) {
        Glyph g = this.glyphs.get(codePoint);
        return g != null ? g : this.fallbackGlyph;
    }

    public float kerning(int left, int right) {
        return this.kerning.get(packKerningKey(left, right));
    }

    public int getGlyphCount() {
        return this.glyphs.size();
    }
    public float measureWidth(String text, float size, float letterSpacing) {
        return shape(text).maxWidth(size, letterSpacing);
    }
    public float getWidth(String text, float size) {
        return shape(text).maxWidth(size, 0.0F);
    }

    public Paragraph shape(String text) {
        if (text == null || text.isEmpty()) return new Paragraph(List.of(), false);
        List<Line> lines = new ArrayList<>(1);
        for (String lineStr : text.split("\n", -1)) {
            lines.add(shapeLine(lineStr.replace("\r", "")));
        }
        return new Paragraph(List.copyOf(lines), false);
    }

    private Line shapeLine(String text) {
        int len = text.length();
        Glyph[] gArr = new Glyph[len];
        float[] penEm = new float[len];
        int[] codePoints = new int[len];

        int count = 0;
        float pen = 0.0F;
        int prev = -1;

        for (int i = 0; i < len; ) {
            int cp = text.codePointAt(i);
            int charLen = Character.charCount(cp);

            Glyph g = glyph(cp);
            if (prev != -1) pen += kerning(prev, cp);

            gArr[count] = g;
            penEm[count] = pen;
            codePoints[count] = cp;
            pen += g.advanceEm();
            count++;
            prev = cp;
            i += charLen;
        }
        return new Line(text, gArr, penEm, codePoints, count, pen);
    }

    public TextureSetup textureSetup() {
        if (this.textureSetup == null) {
            DynamicTexture texture = new DynamicTexture(() -> this.textureId.toString(), readAtlasImage(this.textureId));
            Minecraft.getInstance().getTextureManager().register(this.textureId, texture);
            this.textureSetup = TextureSetup.singleTexture(
                    texture.getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false)
            );
        }
        return this.textureSetup;
    }

    public Identifier textureId() {
        return this.textureId;
    }

    public String trimToWidth(String text, float maxWidth, float size, float letterSpacing) {
        if (text == null || text.isEmpty() || maxWidth <= 0.0F) return "";
        if (measureWidth(text, size, letterSpacing) <= maxWidth) return text;

        StringBuilder sb = new StringBuilder();
        float penEm = 0.0F;
        int prev = -1;
        int count = 0;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int charLen = Character.charCount(cp);

            Glyph g = glyph(cp);
            float stepPen = penEm;
            if (prev != -1) stepPen += kerning(prev, cp);
            stepPen += g.advanceEm();

            float currentWidth = stepPen * size + letterSpacing * count;
            if (currentWidth > maxWidth) {
                break;
            }

            penEm = stepPen;
            sb.appendCodePoint(cp);
            prev = cp;
            count++;
            i += charLen;
        }
        return sb.toString();
    }

    private static InputStream openStream(Identifier id) {
        String p1 = "/assets/" + id.getNamespace() + "/" + id.getPath();
        InputStream s = MsdfFont.class.getResourceAsStream(p1);
        if (s != null) return s;

        s = Thread.currentThread().getContextClassLoader().getResourceAsStream(p1.startsWith("/") ? p1.substring(1) : p1);
        if (s != null) return s;

        try {
            return Minecraft.getInstance().getResourceManager().open(id);
        } catch (Throwable ignored) {}

        return null;
    }

    private static MsdfFont readFont(Identifier id) {
        try {
            InputStream stream = openStream(id);
            if (stream == null) {
                System.err.println("[GodWeer] Font file not found: " + id + " - иконки/шрифт будут квадратами");
                return new MsdfFont(id, new FontFile());
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                FontFile file = GSON.fromJson(reader, FontFile.class);
                MsdfFont font = new MsdfFont(id, file != null ? file : new FontFile());
                System.out.println("[GodWeer] Font loaded: " + id + " glyphs=" + font.getGlyphCount()
                        + " atlas=" + font.atlasWidth() + "x" + font.atlasHeight());
                return font;
            }
        } catch (Throwable t) {
            System.err.println("[GodWeer] Error parsing font: " + id + " (" + t.getMessage() + ")");
            return new MsdfFont(id, new FontFile());
        }
    }

    private static NativeImage readAtlasImage(Identifier id) {
        InputStream stream = openStream(id);
        if (stream == null) {
            System.err.println("[GodWeer] Atlas image NOT FOUND: " + id + " - иконки будут квадратами");
            return fallbackAtlas();
        }
        try {
            NativeImage image = NativeImage.read(stream);
            System.out.println("[GodWeer] Atlas loaded: " + id + " " + image.getWidth() + "x" + image.getHeight());
            return image;
        } catch (Throwable t) {
            System.err.println("[GodWeer] Atlas read FAILED: " + id + " (" + t + ") - иконки будут квадратами");
        }
        return fallbackAtlas();
    }

    private static NativeImage fallbackAtlas() {
        NativeImage dummy = new NativeImage(NativeImage.Format.RGBA, 16, 16, false);
        dummy.fillRect(0, 0, 16, 16, 0xFFFFFFFF);
        return dummy;
    }

    private static long packKerningKey(int l, int r) {
        return ((long) l << 32) | (r & 0xFFFFFFFFL);
    }

    public record Paragraph(List<Line> lines, boolean missingGlyphs) {
        public float maxWidth(float size, float spacing) {
            float max = 0.0F;
            for (Line l : lines) max = Math.max(max, l.width(size, spacing));
            return max;
        }
    }

    public static final class Line {
        private final String text;
        private final Glyph[] glyphs;
        private final float[] penEm;
        private final int[] codePoints;
        private final int glyphCount;
        private final float widthEm;

        private Line(String text, Glyph[] glyphs, float[] penEm, int[] codePoints, int glyphCount, float widthEm) {
            this.text = text; this.glyphs = glyphs; this.penEm = penEm; this.codePoints = codePoints; this.glyphCount = glyphCount; this.widthEm = widthEm;
        }
        public int glyphCount() { return this.glyphCount; }
        public Glyph glyphAt(int i) { return this.glyphs[i]; }
        public float penX(int i, float size, float spacing) { return this.penEm[i] * size + spacing * i; }
        public float width(float size, float spacing) { return this.widthEm * size + spacing * Math.max(0, this.glyphCount - 1); }
    }

    public static final class Glyph {
        private final float advanceEm;
        private final Bounds planeBounds;
        private final float u0, v0, u1, v1;

        private Glyph(float advanceEm, Bounds planeBounds, float u0, float v0, float u1, float v1) {
            this.advanceEm = advanceEm; this.planeBounds = planeBounds; this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
        }

        private static Glyph bake(RawGlyph raw, int aw, int ah) {
            Bounds plane = raw.planeBounds != null ? new Bounds((float) raw.planeBounds.left, (float) raw.planeBounds.bottom, (float) raw.planeBounds.right, (float) raw.planeBounds.top) : null;
            float u0 = 0, v0 = 0, u1 = 0, v1 = 0;
            if (plane != null && raw.atlasBounds != null) {
                u0 = (float) (raw.atlasBounds.left / aw);
                v0 = (float) (1.0D - raw.atlasBounds.top / ah);
                u1 = (float) (raw.atlasBounds.right / aw);
                v1 = (float) (1.0D - raw.atlasBounds.bottom / ah);
            }
            return new Glyph((float) raw.advance, plane, u0, v0, u1, v1);
        }

        public float advanceEm() { return this.advanceEm; }
        public Bounds planeBounds() { return this.planeBounds; }
        public float u0() { return this.u0; }
        public float v0() { return this.v0; }
        public float u1() { return this.u1; }
        public float v1() { return this.v1; }
    }

    public record Bounds(float left, float bottom, float right, float top) {}
    private static final class FontFile { Atlas atlas; Metrics metrics; List<RawGlyph> glyphs; List<KerningPair> kerning; }
    private static final class Atlas { double distanceRange = 2.0; int width = 512, height = 512; double size = 32; }
    private static final class Metrics { double lineHeight = 1.0, ascender = 0.8, descender = -0.2; }
    private static final class RawGlyph { int unicode; double advance; RawBounds planeBounds, atlasBounds; }
    private static final class RawBounds { double left, bottom, right, top; }
    private static final class KerningPair { @SerializedName("unicode1") int unicode1; @SerializedName("unicode2") int unicode2; double advance; }
}