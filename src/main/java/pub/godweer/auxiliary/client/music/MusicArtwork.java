package pub.godweer.auxiliary.client.music;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Converts album art bytes into a renderable GPU texture.
 *
 * <p>A single texture slot is used and re-uploaded only when the artwork
 * actually changes, so no GPU resources are leaked while scrolling through
 * tracks.
 */
public final class MusicArtwork {

    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("godweer", "music/artwork");

    private static String currentHash = "";
    private static byte[] currentBytes = new byte[0];

    private MusicArtwork() {
    }

    /**
     * Returns the texture id for the given artwork, uploading it when needed.
     * Must be called from the render thread.
     */
    public static Identifier textureFor(byte[] artwork) {
        if (artwork == null || artwork.length == 0) return null;

        // Fast path: the very same array instance means the artwork is unchanged.
        if (artwork == currentBytes) {
            return currentHash.isEmpty() ? null : TEXTURE_ID;
        }

        String hash = hash(artwork);
        if (hash.equals(currentHash)) {
            currentBytes = artwork;
            return TEXTURE_ID;
        }

        try {
            NativeImage image = NativeImage.read(artwork);
            if (image == null) return null;

            // DynamicTexture takes ownership of the image and uploads it.
            // TextureManager.register closes the previously registered texture.
            DynamicTexture texture = new DynamicTexture(() -> "godweer/music/artwork", image);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);

            currentHash = hash;
            currentBytes = artwork;
            return TEXTURE_ID;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Hash of the artwork currently uploaded, used to detect track changes. */
    public static String currentHash() {
        return currentHash;
    }

    public static byte[] currentArtwork() {
        return currentBytes;
    }

    /** Drops the cached artwork (used when the HUD is disabled). */
    public static void clear() {
        currentHash = "";
        currentBytes = new byte[0];
    }

    private static String hash(byte[] data) {
        // FNV-1a over the bytes - fast and good enough for change detection.
        long hash = 0xcbf29ce484222325L;
        for (byte b : data) {
            hash ^= (b & 0xFF);
            hash *= 0x100000001b3L;
        }
        return Long.toHexString(hash) + ":" + data.length;
    }
}
