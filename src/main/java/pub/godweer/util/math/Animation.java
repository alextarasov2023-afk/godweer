package pub.godweer.util.math;

import lombok.Getter;

/**
 * Create by daun kvass
 */
@Getter
public class Animation {
    private float value;
    private float target;
    private float speed;

    public Animation(float initialValue, float speed) {
        this.value = initialValue;
        this.target = initialValue;
        this.speed = speed;
    }

    public void update() {
        this.value = MathUtil.lerp(this.value, this.target, this.speed);
        if (Math.abs(this.target - this.value) < 0.0001F) {
            this.value = this.target;
        }
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public void setValue(float value) {
        this.value = value;
        this.target = value;
    }

    public boolean isFinished() {
        return Math.abs(this.target - this.value) < 0.0001F;
    }
}