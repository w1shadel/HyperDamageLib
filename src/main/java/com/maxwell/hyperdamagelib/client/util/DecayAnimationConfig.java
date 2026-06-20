package com.maxwell.hyperdamagelib.client.util;

public class DecayAnimationConfig {
    private final DecayItemAnimationRegistry.IItemAnimator animator;
    private final boolean hasAura;
    private final int r;
    private final int g;
    private final int b;
    private final int a;
    private final float auraScale;
    private final float auraRotateSpeed;
    private final float auraPulseSpeed;
    private final float auraPulseAmplitude;
    private final AuraShape auraShape;
    private final boolean hasSmoke;
    private final int smokeCount;
    private final float smokeSize;
    private final long smokeLifetime;

    private DecayAnimationConfig(Builder builder) {
        this.animator = builder.animator;
        this.hasAura = builder.hasAura;
        this.r = builder.r;
        this.g = builder.g;
        this.b = builder.b;
        this.a = builder.a;
        this.auraScale = builder.auraScale;
        this.auraRotateSpeed = builder.auraRotateSpeed;
        this.auraPulseSpeed = builder.auraPulseSpeed;
        this.auraPulseAmplitude = builder.auraPulseAmplitude;
        this.auraShape = builder.auraShape;
        this.hasSmoke = builder.hasSmoke;
        this.smokeCount = builder.smokeCount;
        this.smokeSize = builder.smokeSize;
        this.smokeLifetime = builder.smokeLifetime;
    }

    public static Builder builder() {
        return new Builder();
    }

    public DecayItemAnimationRegistry.IItemAnimator getAnimator() {
        return animator;
    }

    public boolean hasAura() {
        return hasAura;
    }

    public int getRed() {
        return r;
    }

    public int getGreen() {
        return g;
    }

    public int getBlue() {
        return b;
    }

    public int getAlpha() {
        return a;
    }

    public float getAuraScale() {
        return auraScale;
    }

    public float getAuraRotateSpeed() {
        return auraRotateSpeed;
    }

    public float getAuraPulseSpeed() {
        return auraPulseSpeed;
    }

    public float getAuraPulseAmplitude() {
        return auraPulseAmplitude;
    }

    public AuraShape getAuraShape() {
        return auraShape;
    }

    public boolean hasSmoke() {
        return hasSmoke;
    }

    public int getSmokeCount() {
        return smokeCount;
    }

    public float getSmokeSize() {
        return smokeSize;
    }

    public long getSmokeLifetime() {
        return smokeLifetime;
    }

    public enum AuraShape {
        SQUARE,
        DIAMOND,
        TRIANGLE,
        OCTAGON,
        BLACK_HOLE
    }

    public static class Builder {
        private DecayItemAnimationRegistry.IItemAnimator animator;
        private boolean hasAura = false;
        private int r = 255;
        private int g = 255;
        private int b = 255;
        private int a = 128;
        private float auraScale = 0.44F;
        private float auraRotateSpeed = -40.0F;
        private float auraPulseSpeed = 4.0F;
        private float auraPulseAmplitude = 0.12F;
        private AuraShape auraShape = AuraShape.SQUARE;
        private boolean hasSmoke = false;
        private int smokeCount = 6;
        private float smokeSize = 1.0F;
        private long smokeLifetime = 2500L;

        public Builder animator(DecayItemAnimationRegistry.IItemAnimator animator) {
            this.animator = animator;
            return this;
        }

        public Builder aura(boolean hasAura) {
            this.hasAura = hasAura;
            return this;
        }

        public Builder auraColor(int r, int g, int b, int a) {
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            return this;
        }

        public Builder auraScale(float auraScale) {
            this.auraScale = auraScale;
            return this;
        }

        public Builder auraRotateSpeed(float auraRotateSpeed) {
            this.auraRotateSpeed = auraRotateSpeed;
            return this;
        }

        public Builder auraPulse(float pulseSpeed, float pulseAmplitude) {
            this.auraPulseSpeed = pulseSpeed;
            this.auraPulseAmplitude = pulseAmplitude;
            return this;
        }

        public Builder auraShape(AuraShape auraShape) {
            this.auraShape = auraShape;
            return this;
        }

        public Builder smoke(boolean hasSmoke) {
            this.hasSmoke = hasSmoke;
            return this;
        }

        public Builder smokeDensity(int count) {
            this.smokeCount = count;
            return this;
        }

        public Builder smokeSize(float scale) {
            this.smokeSize = scale;
            return this;
        }

        public Builder smokeLifetime(long lifetimeMs) {
            this.smokeLifetime = lifetimeMs;
            return this;
        }

        public DecayAnimationConfig build() {
            if (this.animator == null) {
                this.animator = (poseStack, displayContext, stack, time) -> {
                };
            }
            return new DecayAnimationConfig(this);
        }
    }
}