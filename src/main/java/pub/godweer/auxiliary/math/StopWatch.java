package pub.godweer.auxiliary.math;

public class StopWatch {
    private long lastMS = System.currentTimeMillis();

    public void reset() {
        this.lastMS = System.currentTimeMillis();
    }

    public boolean isReached(long time) {
        return System.currentTimeMillis() - this.lastMS >= time;
    }

    public boolean finished(double delay) {
        return System.currentTimeMillis() - delay >= this.lastMS;
    }

    public boolean every(double delay) {
        if (finished(delay)) {
            reset();
            return true;
        }
        return false;
    }

    public void setTime(long time) {
        this.lastMS = time;
    }

    public long getTime() {
        return System.currentTimeMillis() - this.lastMS;
    }

    public boolean hasTimeElapsed() {
        return this.lastMS < System.currentTimeMillis();
    }
}