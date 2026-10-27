package pub.godweer.kitty.sosiski.command.impl;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import pub.godweer.Client;
import pub.godweer.enjoy.event.EventManager;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.sosiski.command.Command;
import pub.godweer.auxiliary.client.persiki.ChatUtil;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.MsdfFont;

import java.util.List;

/**
 * Create by daun kvass
 */
public class GpsCommand extends Command {

    private static final Identifier POINTER_TEXTURE = Identifier.fromNamespaceAndPath("godweer", "images/ui/pointer.png");
    private static final float POINTER_SIZE = 15.0F;
    private static final float TOP_OFFSET = 85.0F;

    private static boolean active = false;
    private static String targetName = null;
    private static double targetX = 0;
    private static double targetZ = 0;

    public GpsCommand() {
        super("gps", "Установка метки навигатора");
        EventManager.register(this);
    }

    @Override
    public List<String> aliases() {
        return List.of("waypoint", "point", "nav");
    }

    private String getPrefix() {
        if (Client.INSTANCE != null && Client.INSTANCE.commandManager != null) {
            return Client.INSTANCE.commandManager.getPrefix();
        }
        return ".";
    }

    @Override
    public void build(LiteralArgumentBuilder<Object> builder) {
        builder.executes(context -> {
            String prefix = getPrefix();
            ChatUtil.info("Инфа GPs");
            ChatUtil.entry(prefix + "gps x z", "Поставить метку на координаты");
            ChatUtil.entry(prefix + "gps название x z", "Поставить именованную метку");
            ChatUtil.entry(prefix + "gps off / clear", "Отключить GPS");
            return 1;
        });

        LiteralArgumentBuilder<Object> offArg = LiteralArgumentBuilder.literal("off");
        offArg.executes(context -> clearGps());
        builder.then(offArg);

        LiteralArgumentBuilder<Object> clearArg = LiteralArgumentBuilder.literal("clear");
        clearArg.executes(context -> clearGps());
        builder.then(clearArg);

        LiteralArgumentBuilder<Object> stopArg = LiteralArgumentBuilder.literal("stop");
        stopArg.executes(context -> clearGps());
        builder.then(stopArg);

        builder.then(RequiredArgumentBuilder.<Object, Double>argument("x", DoubleArgumentType.doubleArg())
                .then(RequiredArgumentBuilder.<Object, Double>argument("z", DoubleArgumentType.doubleArg())
                        .executes(context -> {
                            double x = DoubleArgumentType.getDouble(context, "x");
                            double z = DoubleArgumentType.getDouble(context, "z");
                            setGps(null, x, z);
                            return 1;
                        }))
        );

        builder.then(RequiredArgumentBuilder.<Object, String>argument("name", StringArgumentType.word())
                .then(RequiredArgumentBuilder.<Object, Double>argument("x", DoubleArgumentType.doubleArg())
                        .then(RequiredArgumentBuilder.<Object, Double>argument("z", DoubleArgumentType.doubleArg())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    double x = DoubleArgumentType.getDouble(context, "x");
                                    double z = DoubleArgumentType.getDouble(context, "z");
                                    setGps(name, x, z);
                                    return 1;
                                })))
        );
    }

    private static int setGps(String name, double x, double z) {
        targetName = name;
        targetX = x;
        targetZ = z;
        active = true;

        if (name != null) {
            ChatUtil.info("GPS установлен на §a" + name + " §7(X: " + (int) x + ", Z: " + (int) z + ")");
        } else {
            ChatUtil.info("GPS установлен на §7(X: " + (int) x + ", Z: " + (int) z + ")");
        }
        return 1;
    }

    private static int clearGps() {
        active = false;
        targetName = null;
        ChatUtil.info("GPS навигатор §cотключен§7.");
        return 1;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!active) {
            return;
        }

        Minecraft mc = event.getClient();
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }

        var extractor = event.getGuiGraphicsExtractor();
        if (extractor == null) {
            return;
        }

        float tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 playerPos = Render3DUtil.interpolatedPosition(mc.player, tickDelta);
        float playerYaw = mc.player.getViewYRot(tickDelta);

        double dx = targetX - playerPos.x;
        double dz = targetZ - playerPos.z;

        double yawToPoint = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double relativeYawRad = Math.toRadians(yawToPoint - playerYaw);

        float centerX = mc.getWindow().getGuiScaledWidth() / 2.0F;
        float centerY = TOP_OFFSET;
        float halfSize = POINTER_SIZE / 2.0F;

        Matrix3x2fStack pose = extractor.pose();

        pose.pushMatrix();
        pose.translate(centerX, centerY);
        pose.rotate((float) relativeYawRad);

        Render2D.drawTexture(POINTER_TEXTURE, -halfSize, -halfSize, POINTER_SIZE, POINTER_SIZE, 0.0F, 0xFFFFFFFF);

        pose.popMatrix();

        MsdfFont font = Fonts.SF_MEDIUM;

        if (targetName != null && !targetName.isEmpty()) {
            float nameSize = 9.0F;
            float nameWidth = font.getWidth(targetName, nameSize);
            Fonts.drawString(font, targetName, centerX - nameWidth / 2.0F, centerY - halfSize - 9.0F, nameSize, ColorUtil.rgba(220, 220, 220, 255));
        }

        double distance2D = Math.hypot(dx, dz);
        String distText = (int) Math.round(distance2D) + "m";

        float distSize = 9.0F;
        float distWidth = font.getWidth(distText, distSize);
        Fonts.drawString(font, distText, centerX - distWidth / 2.0F, centerY + halfSize + 3.0F, distSize, ColorUtil.rgba(255, 255, 255, 255));
    }
}