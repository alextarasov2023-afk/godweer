package pub.godweer.util.render;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Create by daun kvass
 */
public final class ShaderFallback {
    /**
     * 26.3 компилирует все пайплайны в SPIR-V, поэтому он требует:
     * 1) директиву #include вместо #moj_import;
     * 2) явные layout(location = N) на каждом in/out;
     * 3) #extension GL_ARB_separate_shader_objects (для GLSL 330).
     * Этот трансформер приводит класспас-версии шейдеров мода к формату 26.3.
     */
    private static final Pattern MOJ_IMPORT = Pattern.compile("^(\\s*)#moj_import\\s+<([\\w.-]+:[\\w./-]+)>\\s*$", Pattern.MULTILINE);
    private static final Pattern VERSION_LINE = Pattern.compile("^(\\s*#version\\s+[0-9]+[^\\n]*\\n)");
    private static final Pattern DECL_LINE = Pattern.compile("^(\\s*)((?:(?:flat|smooth|noperspective|centroid|patch|sample)\\s+)*)(in|out)(\\s+)(?!.*\\blayout\\s*\\()", Pattern.MULTILINE);

    private static final String SEPARATE_OBJECTS_EXTENSION = "#extension GL_ARB_separate_shader_objects : require";

    private static final Pattern BUILTIN_KEY_DYNAMIC_TRANSFORMS = Pattern.compile("dynamictransforms\\.glsl$");
    private static final Pattern BUILTIN_KEY_PROJECTION = Pattern.compile("projection\\.glsl$");

    private static final String BUILTIN_DYNAMIC_TRANSFORMS = """
            layout(std140) uniform DynamicTransforms {
                mat4 ModelViewMat;
                vec4 ColorModulator;
                vec3 ModelOffset;
                mat4 TextureMat;
            };
            """;

    private static final String BUILTIN_PROJECTION = """
            layout(std140) uniform Projection {
                mat4 ProjMat;
            };
            """;

    private ShaderFallback() {}

    /**
     * Оборачивает источник шейдеров так, чтобы godweer-шейдеры, отсутствующие
     * в базовом источнике (vanilla-only менеджер во время preloadUiShader,
     * либо пакет без нужных файлов), брались из classpath мода, а все
     * включаемые файлы — из classpath мода/встроенных fallback-констант.
     */
    public static ShaderSource wrap(ShaderSource source) {
        return new ShaderSource() {
            @Override
            public String getShader(Identifier shaderId, ShaderType shaderType) {
                String result = null;
                try {
                    result = source.getShader(shaderId, shaderType);
                } catch (Throwable ignored) {}
                if (result != null) {
                    return transform(result);
                }
                return load(shaderId, shaderType);
            }

            @Override
            public ShaderSource.CachedIncludeSource getInclude(Identifier includeId) {
                ShaderSource.CachedIncludeSource original = null;
                try {
                    original = source.getInclude(includeId);
                } catch (Throwable ignored) {}
                if (original != null) {
                    return original;
                }
                String text = loadIncludeFallback(includeId);
                return text == null ? null : ShaderSource.CachedIncludeSource.create(includeId, text);
            }

            @Override
            public void close() {
                try {
                    source.close();
                } catch (Throwable ignored) {}
            }
        };
    }

    /** Класспас-версия шейдера (только ns=godweer), уже приведённая к формату 26.3. */
    public static String load(Identifier shaderId, ShaderType shaderType) {
        if (!shaderId.getNamespace().equals("godweer")) {
            return null;
        }
        String extension = shaderType == ShaderType.VERTEX ? ".vsh" : ".fsh";
        String source = readClasspath(shaderId.getNamespace(), shaderId.getPath() + extension);
        if (source != null) {
            return transform(source);
        }
        return null;
    }

    /** Приводит source в соответствие с требованиями SPIR-V компилятора renderpearl. */
    public static String transform(String source) {
        if (source == null) {
            return null;
        }
        // 1. #moj_import -> #include
        Matcher moj = MOJ_IMPORT.matcher(source);
        StringBuilder normalized = new StringBuilder(source.length());
        while (moj.find()) {
            String replacement = moj.group(1) + "#include <" + moj.group(2) + ">";
            moj.appendReplacement(normalized, Matcher.quoteReplacement(replacement));
        }
        moj.appendTail(normalized);
        source = normalized.toString();

        // 2. Vulkan-семантика SPIR-V: gl_VertexID/gl_InstanceID переименованы
        source = source.replace("gl_VertexID", "gl_VertexIndex");
        source = source.replace("gl_InstanceID", "gl_InstanceIndex");

        // 2. счётчики стартуют выше уже занятых locations (файлы могут быть размечены частично)
        int maxIn = -1, maxOut = -1;
        Matcher locScan = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(-?\\d+)\\s*\\)\\s*(?:flat|smooth|noperspective|centroid|patch|sample\\s+)*(in|out)\\b").matcher(source);
        while (locScan.find()) {
            int value = Integer.parseInt(locScan.group(1));
            if (locScan.group(2).equals("in")) {
                if (value > maxIn) maxIn = value;
            } else {
                if (value > maxOut) maxOut = value;
            }
        }
        int[] inCounter = {maxIn + 1};
        int[] outCounter = {maxOut + 1};

        // 3. #extension после #version
        Matcher ver = VERSION_LINE.matcher(source);
        if (ver.find()) {
            source = source.substring(0, ver.end(1)) + SEPARATE_OBJECTS_EXTENSION + "\n" + source.substring(ver.end(1));
        }

        // 4. явные location для каждого объявления in/out (отдельные счётчики для in и out)
        Matcher decl = DECL_LINE.matcher(source);
        StringBuilder located = new StringBuilder(source.length());
        while (decl.find()) {
            int index = decl.group(3).equals("in") ? inCounter[0]++ : outCounter[0]++;
            String prefix = decl.group(1) + decl.group(2) + "layout(location = " + index + ") " + decl.group(3) + decl.group(4);
            decl.appendReplacement(located, Matcher.quoteReplacement(prefix));
        }
        decl.appendTail(located);
        return located.toString();
    }

    /** Класспас-версия включаемого файла; null, если такой нет. */
    public static String loadIncludeFallback(Identifier includeId) {
        String namespace = includeId.getNamespace();
        String path = includeId.getPath();
        // и "ui_common.glsl", и "shaders/include/ui_common.glsl" сводим к базовому имени
        String base = path;
        int includeMark = base.indexOf("shaders/include/");
        if (includeMark >= 0) {
            base = base.substring(includeMark + "shaders/include/".length());
        }
        if (base.endsWith(".glsl")) {
            base = base.substring(0, base.length() - 5);
        }
        if (namespace.equals("godweer")) {
            return readClasspath(namespace, "include/" + base + ".glsl");
        }
        if (namespace.equals("minecraft")) {
            if (BUILTIN_KEY_DYNAMIC_TRANSFORMS.matcher(path).find()) return BUILTIN_DYNAMIC_TRANSFORMS;
            if (BUILTIN_KEY_PROJECTION.matcher(path).find()) return BUILTIN_PROJECTION;
        }
        return null;
    }

    private static String readClasspath(String namespace, String path) {
        String resourcePath = "/assets/" + namespace + "/shaders/" + path;
        try (InputStream stream = ShaderFallback.class.getResourceAsStream(resourcePath)) {
            return stream != null ? new String(stream.readAllBytes(), StandardCharsets.UTF_8) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
