package pub.godweer.util.game;

import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.PacketEvent;

/**
 * Create by daun kvass
 */
public final class Tps {
    public static final Tps INSTANCE =new Tps();
    private static final float DEFAULT_TPS = 20.0F;
    private static final float MIN_TPS = 1.0F;
    private static final float SAMPLE_WEIGHT = 0.25F;
    private static final long SAMPLE_STALE_NANOS = 10_000_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private volatile boolean frozen;
    private volatile float configuredTps = DEFAULT_TPS;
    private volatile float observedTps = DEFAULT_TPS;
    private volatile int validSamples;
    private volatile long lastGameTime = Long.MIN_VALUE;
    private volatile long lastTimePacketNanos;
    private volatile long lastValidSampleNanos;
    @EventTarget
    public void onPacketReceive(PacketEvent event) {
        if (event.getType() != PacketEvent.Type.RECEIVE) {
            return;
        }
        if (event.getPacket() instanceof ClientboundTickingStatePacket packet) {
            this.configuredTps = Math.max(MIN_TPS, packet.tickRate());
            this.frozen = packet.isFrozen();
            if (this.validSamples == 0) {
                this.observedTps = this.configuredTps;
            }
            return;
        }
        if (event.getPacket() instanceof ClientboundSetTimePacket packet) {
            observeTimePacket(packet.gameTime(), System.nanoTime());
        }
    }
    public float effectiveTps() {
        if (this.frozen) {
            return 0.0F;
        }
        long now = System.nanoTime();
        if (this.validSamples == 0 || now - this.lastValidSampleNanos > SAMPLE_STALE_NANOS) {
            return this.configuredTps;
        }
        return Math.clamp(this.observedTps, MIN_TPS, this.configuredTps);
    }
    private void observeTimePacket(long gameTime, long now) {
        if (this.lastGameTime != Long.MIN_VALUE && gameTime > this.lastGameTime) {
            long gameTicks = gameTime - this.lastGameTime;
            long elapsedNanos = now - this.lastTimePacketNanos;
            if (gameTicks <= 2_000L && elapsedNanos > 0L) {
                float sample = (float) (gameTicks * (double) NANOS_PER_SECOND / elapsedNanos);
                float upperBound = Math.max(MIN_TPS, this.configuredTps);
                if (sample >= MIN_TPS * 0.5F && sample <= upperBound * 1.25F) {
                    sample = Math.clamp(sample, MIN_TPS, upperBound);
                    this.observedTps = this.validSamples == 0
                            ? sample
                            : this.observedTps + (sample - this.observedTps) * SAMPLE_WEIGHT;
                    this.validSamples++;
                    this.lastValidSampleNanos = now;
                }
            }
        }
        this.lastGameTime = gameTime;
        this.lastTimePacketNanos = now;
    }

}
