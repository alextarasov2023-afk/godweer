package pub.godweer.auxiliary.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import pub.godweer.enjoy.mixin.accessor.GuiGraphicsExtractorAccessor;
import pub.godweer.auxiliary.RenderExtend;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Create by daun kvass
 */
public final class Render2DUtil {
    private static final List<GuiElementRenderState> QUEUED = new ArrayList<>(128);
    private static final Deque<ScreenRectangle> SCISSORS = new ArrayDeque<>();

    public static void beginFrame() {
        QUEUED.clear();
        SCISSORS.clear();
    }

    public static void pushScissor(float x, float y, float width, float height) {
        int scissorX = Math.round(x);
        int scissorY = Math.round(y);
        int scissorWidth = Math.max(0, Math.round(x + width) - scissorX);
        int scissorHeight = Math.max(0, Math.round(y + height) - scissorY);
        ScreenRectangle next = new ScreenRectangle(scissorX, scissorY, scissorWidth, scissorHeight);

        if (!SCISSORS.isEmpty()) {
            next = SCISSORS.peek().intersection(next);
            if (next == null) next = ScreenRectangle.empty();
        }

        if (next.width() > 0 && next.height() > 0) {
            int left = Math.max(0, next.left());
            int top = Math.max(0, next.top());
            int clampedWidth = next.right() - left;
            int clampedHeight = next.bottom() - top;
            next = clampedWidth > 0 && clampedHeight > 0
                    ? new ScreenRectangle(left, top, clampedWidth, clampedHeight)
                    : ScreenRectangle.empty();
        }

        if (next.width() > 0 && next.height() > 0) {
            var window = Minecraft.getInstance().getWindow();
            ScreenRectangle screen = new ScreenRectangle(0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight());
            if (screen.intersection(next) == null) {
                next = ScreenRectangle.empty();
            }
        }

        SCISSORS.push(next);
    }

    public static void popScissor() {
        if (!SCISSORS.isEmpty()) {
            SCISSORS.pop();
        }
    }

    public static ScreenRectangle currentScissor() {
        return SCISSORS.peek();
    }

    public static boolean hasEmptyScissor() {
        ScreenRectangle scissor = currentScissor();
        return scissor != null && (scissor.width() <= 0 || scissor.height() <= 0);
    }

    public static void flush() {
        GuiGraphicsExtractor extractor = RenderExtend.currentGuiGraphicsExtractor();
        if (extractor == null) {
            QUEUED.clear();
            return;
        }

        GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor) extractor;
        for (GuiElementRenderState state : QUEUED) {
            accessor.getGuiRenderState().addGuiElement(state);
        }
        QUEUED.clear();
    }

    public static void queue(GuiElementRenderState state) {
        if (hasEmptyScissor()) return;
        QUEUED.add(state);
    }
}