package pub.godweer.module.impl.movement;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.EventFirework;
import pub.godweer.event.list.GlidingVelocityEvent;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.SliderSetting;

/**
 * Движение на элитрах: чем планируем и как разгоняемся.
 * <p>
 * Оба хука уже есть в миксинах — LivingEntityMixin отдаёт скорость планирования после
 * ванильного затухания (0.99/0.98/0.99), FireworkRocketEntityMixin отдаёт силу буста
 * ракеты. Множитель применяется каждый тик, поэтому горизонталь всегда режется потолком:
 * без него 1.1x за тик превращается в гиперзвук через секунду.
 */
public class ElytraMotion extends Module {

    // Потолок горизонтальной скорости, блоков за тик (~ 60 б/с).
    private static final double MAX_GLIDE_SPEED = 3.0D;
    // Потолок вертикали, блоков за тик: выше — уже не планирование, а лифт.
    private static final double MAX_VERTICAL_SPEED = 0.6D;

    private final SliderSetting glideSpeed = slider("Glide Speed", 1.10f, 0.5f, 2.5f, 0.05f);
    // Подработка вертикали за тик: 0 = чистое планирование, больше = набирает высоту.
    private final SliderSetting lift = slider("Lift", 0.0f, 0.0f, 0.08f, 0.005f);
    private final SliderSetting boostPower = slider("Boost Power", 1.0f, 1.0f, 3.0f, 0.1f);
    private final CheckBox noFallDamage = checkbox("No Fall Damage", true);

    public ElytraMotion() {
        super("ElytraMotion", "Скорость планирования, подъём и сила буста на элитрах", Category.MOVEMENT);
    }

    @EventTarget
    public void onGlide(GlidingVelocityEvent event) {
        Vec3 velocity = event.getVelocity();
        double multiplier = glideSpeed.get();
        double rise = lift.get();

        double horizontal = Math.hypot(velocity.x, velocity.z);
        if (horizontal > 1.0E-4D) {
            double wanted = Math.min(horizontal * multiplier, MAX_GLIDE_SPEED);
            double scale = wanted / horizontal;
            velocity = new Vec3(velocity.x * scale, velocity.y, velocity.z * scale);
        }

        double y = Mth.clamp(velocity.y + rise, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
        event.set(new Vec3(velocity.x, y, velocity.z));
    }

    @EventTarget
    public void onFirework(EventFirework event) {
        LocalPlayer player = player();
        if (player == null || event.getBoostedEntity() != player) return;

        float multiplier = boostPower.get();
        if (multiplier != 1.0F) {
            event.setSpeed(event.getSpeed() * multiplier);
        }
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        LocalPlayer player = player();
        if (player == null) return;

        // Счётчик падения на элитрах — это будущий урон при выходе из полёта; на
        // самом планировании он ничего не даёт.
        if (noFallDamage.getValue() && player.isFallFlying()) {
            player.fallDistance = 0.0F;
        }
    }
}
