package pub.godweer.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * Своя анимация удара руки от первого лица.
 *
 * Ванильный swingArm симметричен (синус на подъём и на спад) и всегда прошивает
 * позу сэндвичем YP +45 / YP -45. Здесь своей модель: собственный таймер (длительность
 * в миллисекундах, а не кадры ванили), собственный профиль взмаха (быстрый замах,
 * плавный возврат, опциональный перелёт) и поза, собранная из смещений и эйлеровых
 * углов вокруг руки. Поэтому режимы не похожи ни на ванильный, ни друг на друга.
 */
public final class SwingAnimation extends Module {

    public static SwingAnimation INSTANCE;

    public final ModeSetting mode = mode("Режим", "Flick",
            "Flick", "Punch", "Slash", "Whip", "Spin", "Stab", "Bounce", "Shake", "Custom");
    public final SliderSetting strength = slider("Сила", 1.0F, 0.0F, 2.5F, 0.05F);
    public final SliderSetting duration = slider("Длительность, мс", 240.0F, 60.0F, 900.0F, 10.0F);
    public final SliderSetting speed = slider("Скорость", 1.0F, 0.25F, 3.0F, 0.05F);
    public final SliderSetting snap = slider("Резкость замаха", 1.6F, 0.4F, 3.0F, 0.05F);
    public final SliderSetting recover = slider("Возврат, доля", 0.45F, 0.15F, 0.85F, 0.05F);
    public final SliderSetting overshoot = slider("Перелёт", 0.0F, 0.0F, 0.6F, 0.05F);

    public final CheckBox leftHand = checkbox("Левая рука", true);
    public final CheckBox keepHeight = checkbox("Не опускать руку", true);
    public final CheckBox noEat = checkbox("Без анимации еды", false);

    public final HeaderSetting customHeader = header("Custom (путь руки)");
    public final SliderSetting customX = slider("Смещение X", 0.0F, -0.6F, 0.6F, 0.01F);
    public final SliderSetting customY = slider("Смещение Y", 0.0F, -0.6F, 0.6F, 0.01F);
    public final SliderSetting customZ = slider("Смещение Z", -0.3F, -0.6F, 0.6F, 0.01F);
    public final SliderSetting customYaw = slider("Поворот Y, °", 0.0F, -120.0F, 120.0F, 1.0F);
    public final SliderSetting customPitch = slider("Поворот X, °", -60.0F, -120.0F, 120.0F, 1.0F);
    public final SliderSetting customRoll = slider("Поворот Z, °", -30.0F, -180.0F, 180.0F, 1.0F);

    private final long[] startedAtNs = new long[2];
    private final float[] lastInput = new float[2];
    private boolean hookLogged;

    public SwingAnimation() {
        super("SwingAnimation", "Своя анимация удара руки", Category.RENDER);
        INSTANCE = this;
    }

    /** Левая рука анимируется только когда её включили, иначе её ведёт ваниль. */
    public boolean handles(HumanoidArm arm) {
        return isEnabled() && (arm != HumanoidArm.LEFT || leftHand.get());
    }

    @Override
    protected void onDisable() {
        this.startedAtNs[0] = 0L;
        this.startedAtNs[1] = 0L;
        this.lastInput[0] = 0.0F;
        this.lastInput[1] = 0.0F;
        this.hookLogged = false;
    }

    /**
     * Вызывается вместо ванильного swingArm: рука уже стоит в своей обычной позе,
     * поэтому здесь мы только добавляем своё смещение и свой поворот.
     */
    public void applySwing(float vanillaProgress, PoseStack pose, int invert, HumanoidArm arm) {
        int slot = arm == HumanoidArm.RIGHT ? 0 : 1;
        float u = timeline(slot, vanillaProgress);
        if (u <= 0.0F || u >= 1.0F) return;

        if (!this.hookLogged) {
            this.hookLogged = true;
            System.out.println("[GodWeer] SwingAnimation: swingArm подменён, анимация идёт");
        }

        float k = stroke(u) * this.strength.get();
        Pose p = pose(k, u);

        pose.translate((float) invert * p.x, p.y, p.z);
        pose.rotateDegrees(Axis.ZP, (float) invert * p.roll);
        pose.rotateDegrees(Axis.XP, p.pitch);
        pose.rotateDegrees(Axis.YP, (float) invert * p.yaw);
        if (p.scale != 1.0F) pose.scale(p.scale, p.scale, p.scale);
    }

    /** Смещение и эйлеры режима; всё домножено на амплитуду k, поэтому сила 0 = покой. */
    private Pose pose(float k, float u) {
        Pose p = new Pose();
        switch (this.mode.getValue()) {
            case "Punch" -> {
                p.z = -0.42F * k;
                p.pitch = -14.0F * k;
                p.scale = 1.0F - 0.06F * k;
            }
            case "Slash" -> {
                // диагональный рубящий: угол идёт от - к + внутри одного взмаха
                float sweep = (u < 0.5F ? u * 2.0F : 2.0F - u * 2.0F) - 0.5F;
                p.yaw = 70.0F * sweep * this.strength.get();
                p.roll = -40.0F * k;
                p.z = -0.16F * k;
            }
            case "Whip" -> {
                p.yaw = -58.0F * k;
                p.roll = -26.0F * k;
                p.x = 0.1F * k;
            }
            case "Spin" -> {
                p.roll = -300.0F * eased(u) * this.strength.get();
                p.z = -0.1F * k;
                p.scale = 1.0F + 0.1F * k;
            }
            case "Stab" -> {
                p.z = -0.5F * k;
                p.y = 0.06F * k;
                p.pitch = -8.0F * k;
            }
            case "Bounce" -> {
                p.y = 0.26F * k;
                p.scale = 1.0F + 0.3F * k;
                p.pitch = -18.0F * k;
            }
            case "Shake" -> {
                p.pitch = -34.0F * k;
                p.roll = 16.0F * Mth.sin(u * (float) (Math.PI * 2.0D) * 4.0F) * (1.0F - u)
                        * this.strength.get();
                p.x = 0.05F * k;
            }
            case "Custom" -> {
                p.x = this.customX.get() * k;
                p.y = this.customY.get() * k;
                p.z = this.customZ.get() * k;
                p.yaw = this.customYaw.get() * k;
                p.pitch = this.customPitch.get() * k;
                p.roll = this.customRoll.get() * k;
            }
            default -> {
                p.roll = -62.0F * k;
                p.yaw = 24.0F * k;
                p.z = -0.12F * k;
                p.y = 0.04F * k;
            }
        }
        return p;
    }

    /**
     * Профиль взмаха 0..1 по собственному времени: замах короче возврата, форма
     * задаётся «Резкостью», а «Перелёт» добавляет проходишь мимо нуля на выходе.
     */
    private float stroke(float u) {
        float back = Mth.clamp(this.recover.get(), 0.05F, 0.95F);
        float forward = 1.0F - back;
        float k = u < forward ? u / forward : 1.0F - (u - forward) / back;
        k = Mth.clamp(k, 0.0F, 1.0F);
        float shaped = eased(k);
        if (this.overshoot.get() > 0.0F && u > forward) {
            // на возврате уходим ниже нуля: короткий обратный хлыст вместо линейного спада
            float tail = Mth.clamp((u - forward) / back, 0.0F, 1.0F);
            shaped -= this.overshoot.get() * Mth.sin(tail * (float) Math.PI) * 0.5F;
        }
        return Mth.clamp(shaped, -0.5F, 1.35F);
    }

    /** p -> 1 - (1 - p)^n : резкий старт при n > 1, растянутый при n < 1. */
    private float eased(float p) {
        float n = Mth.clamp(this.snap.get(), 0.4F, 3.0F);
        return p >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(1.0F - p, n);
    }

    /**
     * Своя библиотека времени: старт свинга ловится по тому, что ванильный прогресс
     * пополз снизу вверх, дальше считается только в миллисекундах. Скорость и
     * длительность поэтому работают во всех режимах, а не только в Custom.
     */
    private float timeline(int slot, float vanilla) {
        float in = Mth.clamp(vanilla, 0.0F, 1.0F);
        float prev = this.lastInput[slot];
        if (in > 0.001F && (prev <= 0.001F || in < prev - 0.25F)) {
            this.startedAtNs[slot] = System.nanoTime();
        }
        this.lastInput[slot] = in;

        long began = this.startedAtNs[slot];
        if (began == 0L) return 0.0F;

        double rate = Math.max(0.25D, this.speed.get());
        long nanos = (long) (Math.max(60.0D, this.duration.get()) * 1_000_000.0D / rate);
        return Mth.clamp((System.nanoTime() - began) / (float) nanos, 0.0F, 1.0F);
    }

    private static final class Pose {
        float x;
        float y;
        float z;
        float yaw;
        float pitch;
        float roll;
        float scale = 1.0F;
    }
}
