package com.maxwell.hyperdamagelib.client.util;

import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecayEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class DecayClientEffectHelper {
    public static int glitchTicks = 0;
    public static float glitchIntensity = 0.0f;
    public static float currentScaleX = 1.0f;
    public static float currentScaleY = 1.0f;
    public static float currentSpinSpeed = 0.0f;
    public static float accumulatedRotation = 0.0f;
    public static float currentWaveAmplitude = 0.0f;
    public static float currentWaveSpeed = 0.0f;
    private static float scaleXVelocity = 0.0f;
    private static float scaleYVelocity = 0.0f;
    private static float rotationVelocity = 0.0f;
    public static float targetScaleX = 1.0f;
    public static float targetScaleY = 1.0f;
    public static float targetSpinSpeed = 0.0f;
    public static float targetWaveAmplitude = 0.0f;
    public static float targetWaveSpeed = 0.0f;

    private DecayClientEffectHelper() {
    }

    public static void sendAdvancedEffect(Player player, int duration, float intensity, float scaleX, float scaleY, float spinSpeed, float waveAmp, float waveSpeed) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModMessages.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new ClientboundDecayEffectPacket(duration, intensity, scaleX, scaleY, spinSpeed, waveAmp, waveSpeed)
            );
        }
    }

    public static void startPermanentEffect(Player player, float intensity, float scaleX, float scaleY, float spinSpeed, float waveAmp, float waveSpeed) {
        sendAdvancedEffect(player, -1, intensity, scaleX, scaleY, spinSpeed, waveAmp, waveSpeed);
    }

    public static void stopAdvancedEffect(Player player) {
        sendAdvancedEffect(player, 0, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f);
    }

    public static void sendStretchEffect(Player player, int duration, float scaleX, float scaleY) {
        sendAdvancedEffect(player, duration, 0.5f, scaleX, scaleY, 0.0f, 0.0f, 0.0f);
    }

    public static void sendSpinEffect(Player player, int duration, float spinSpeed, float waveAmp) {
        sendAdvancedEffect(player, duration, 0.8f, 1.0f, 1.0f, spinSpeed, waveAmp, 0.01f);
    }

    @OnlyIn(Dist.CLIENT)
    public static void triggerCustomEffect(int duration, float intensity, float scaleX, float scaleY, float spinSpeed, float waveAmp, float waveSpeed) {
        glitchTicks = duration;
        glitchIntensity = intensity;
        targetScaleX = scaleX;
        targetScaleY = scaleY;
        targetSpinSpeed = spinSpeed;
        targetWaveAmplitude = waveAmp;
        targetWaveSpeed = waveSpeed;
    }

    @OnlyIn(Dist.CLIENT)
    public static void clientTick() {
        if (glitchTicks > 0) {
            glitchTicks--;
            if (glitchTicks == 0) {
                glitchIntensity = 0.0f;
                targetScaleX = 1.0f;
                targetScaleY = 1.0f;
                targetSpinSpeed = 0.0f;
                targetWaveAmplitude = 0.0f;
                targetWaveSpeed = 0.0f;
            } else {
                targetScaleX += (1.0f - targetScaleX) * 0.08f;
                targetScaleY += (1.0f - targetScaleY) * 0.08f;
                targetSpinSpeed += (0.0f - targetSpinSpeed) * 0.05f;
                targetWaveAmplitude += (0.0f - targetWaveAmplitude) * 0.05f;
                targetWaveSpeed += (0.0f - targetWaveSpeed) * 0.05f;
            }
        } else if (glitchTicks == -1) {
        } else {
            targetScaleX += (1.0f - targetScaleX) * 0.15f;
            targetScaleY += (1.0f - targetScaleY) * 0.15f;
            targetSpinSpeed += (0.0f - targetSpinSpeed) * 0.15f;
            targetWaveAmplitude += (0.0f - targetWaveAmplitude) * 0.15f;
            targetWaveSpeed += (0.0f - targetWaveSpeed) * 0.15f;
        }
        float k = 0.22f;
        float damping = 0.65f;
        float forceScaleX = -k * (currentScaleX - targetScaleX);
        scaleXVelocity = (scaleXVelocity + forceScaleX) * damping;
        currentScaleX += scaleXVelocity;
        float forceScaleY = -k * (currentScaleY - targetScaleY);
        scaleYVelocity = (scaleYVelocity + forceScaleY) * damping;
        currentScaleY += scaleYVelocity;
        currentSpinSpeed += (targetSpinSpeed - currentSpinSpeed) * 0.2f;
        if (Math.abs(currentSpinSpeed) > 0.01f) {
            accumulatedRotation += currentSpinSpeed;
            accumulatedRotation = normalizeAngle(accumulatedRotation);
            rotationVelocity = currentSpinSpeed;
        } else {
            float forceRot = -k * (accumulatedRotation - 0.0f);
            rotationVelocity = (rotationVelocity + forceRot) * damping;
            accumulatedRotation += rotationVelocity;
        }
        currentWaveAmplitude += (targetWaveAmplitude - currentWaveAmplitude) * 0.2f;
        currentWaveSpeed += (targetWaveSpeed - currentWaveSpeed) * 0.2f;
    }

    public static float normalizeAngle(float angle) {
        float normalized = angle % 360.0f;
        if (normalized > 180.0f) {
            normalized -= 360.0f;
        } else if (normalized < -180.0f) {
            normalized += 360.0f;
        }
        return normalized;
    }
}