package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, priority = -10000000)
public abstract class LivingEntityMixin implements IDecayEntity {
    @Shadow
    protected boolean dead;
    @Shadow
    protected int deathTime;
    @Unique
    private float decayAmount = 0.0f;
    @Unique
    private boolean superInvincible = false;
    @Unique
    private boolean decayRemoveBypass = false;
    @Unique
    private int decayHoldTicks;
    @Unique
    private boolean keepCurrentHealth = false;
    @Unique
    private float invincibleHealthValue = 20.0f;
    @Unique
    private boolean healBlocked = false;

    @Override
    public int getDecayHoldTicks() {
        return this.decayHoldTicks;
    }

    @Override
    public void setDecayHoldTicks(int ticks) {
        this.decayHoldTicks = ticks;
    }

    @Override
    public float getDecayAmount() {
        return this.decayAmount;
    }

    @Override
    public void setDecayAmount(float amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        float originalMax = (float) self.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (Float.isNaN(originalMax) || originalMax <= 0.0F) originalMax = 20.0F;
        this.decayAmount = Math.max(0.0f, Math.min(amount, originalMax));
        decay$syncToTracking();
    }

    @Override
    public void addDecayAmount(float amount) {
        this.setDecayAmount(this.decayAmount + amount);
        this.decayHoldTicks = 100;
    }

    @Override
    public boolean isHealBlocked() {
        return this.healBlocked;
    }

    @Override
    public void setHealBlocked(boolean val) {
        this.healBlocked = val;
        decay$syncToTracking();
    }

    @Override
    public boolean isSuperInvincible() {
        return this.superInvincible;
    }

    @Override
    public void setSuperInvincible(boolean val) {
        LivingEntity self = (LivingEntity) (Object) this;
        this.superInvincible = val;
        self.setInvulnerable(val);
        InvincibleHelper.setInvincible(self, val);
        if (val) {
            this.dead = false;
            this.deathTime = 0;
            self.setPose(Pose.STANDING);
            if (this.keepCurrentHealth) {
                this.invincibleHealthValue = Math.max(1.0f, Math.min(self.getHealth(), self.getMaxHealth()));
            } else {
                this.invincibleHealthValue = self.getMaxHealth();
            }
            self.setHealth(this.invincibleHealthValue);
        } else {
            this.dead = false;
            this.deathTime = 0;
        }
        decay$syncToTracking();
    }

    @Override
    public boolean isKeepCurrentHealth() {
        return this.keepCurrentHealth;
    }

    @Override
    public void setKeepCurrentHealth(boolean val) {
        this.keepCurrentHealth = val;
    }

    @Override
    public float getInvincibleHealthValue() {
        return this.invincibleHealthValue;
    }

    @Override
    public void setInvincibleHealthValue(float val) {
        this.invincibleHealthValue = val;
    }

    @Override
    public boolean isRemoveBypass() {
        return this.decayRemoveBypass;
    }

    @Override
    public void setRemoveBypass(boolean val) {
        this.decayRemoveBypass = val;
    }

    @Unique
    private void decay$syncToTracking() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level() != null && !self.level().isClientSide()) {
            ModMessages.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> self),
                    new ClientboundDecaySyncPacket(self.getId(), this.decayAmount, this.superInvincible, this.keepCurrentHealth, this.invincibleHealthValue, this.healBlocked)
            );
        }
    }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void decay$tickSafety(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.superInvincible) {
            if (this.dead || this.deathTime > 0) {
                this.dead = false;
                this.deathTime = 0;
                self.setPose(Pose.STANDING);
                self.setHealth(this.keepCurrentHealth ? this.invincibleHealthValue : self.getMaxHealth());
                decay$syncToTracking();
            }
        }
    }
}