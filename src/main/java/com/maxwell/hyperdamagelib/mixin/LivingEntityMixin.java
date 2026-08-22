package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
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
        if (val) {
            float currentHp = self.getHealth();
            if (Float.isNaN(currentHp) || currentHp <= 0.0F) {
                currentHp = self.getMaxHealth();
            } else {
                currentHp = Math.min(currentHp, self.getMaxHealth());
            }
            this.keepCurrentHealth = true;
            this.invincibleHealthValue = currentHp;
            this.superInvincible = true;
            self.setInvulnerable(true);
            this.dead = false;
            this.deathTime = 0;
            self.setPose(Pose.STANDING);
            try {
                DecayDamageUtil.BYPASS_DECAY.set(true);
                self.setHealth(this.invincibleHealthValue);
                self.getEntityData().set(LivingEntityAccessor.getDataHealthId(), this.invincibleHealthValue);
            } finally {
                DecayDamageUtil.BYPASS_DECAY.remove();
            }
        } else {
            this.superInvincible = false;
            self.setInvulnerable(false);
            this.keepCurrentHealth = false;
            this.decayAmount = 0.0F;
            this.decayHoldTicks = 0;
            this.dead = false;
            this.deathTime = 0;
            self.setPose(Pose.STANDING);
            try {
                DecayDamageUtil.BYPASS_DECAY.set(true);
                float targetHp = this.invincibleHealthValue > 0.0F ? this.invincibleHealthValue : self.getMaxHealth();
                self.setHealth(targetHp);
                self.getEntityData().set(LivingEntityAccessor.getDataHealthId(), targetHp);
            } finally {
                DecayDamageUtil.BYPASS_DECAY.remove();
            }
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

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void decay$lockHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (this.superInvincible) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void decay$lockDie(DamageSource source, CallbackInfo ci) {
        if (this.superInvincible) {
            ci.cancel();
        }

    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void decay$lockSetHealth(float nextHealth, CallbackInfo ci) {
        if (!DecayDamageUtil.BYPASS_DECAY.get() && this.superInvincible) {
            ci.cancel();
        }
    }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void decay$tickSafety(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.superInvincible) {
            if (this.dead || this.deathTime > 0 || ((LivingEntityAccessor) self).isDeadFlag()) {
                this.dead = false;
                ((LivingEntityAccessor) self).setDeadFlag(false);
                this.deathTime = 0;
                self.setPose(Pose.STANDING);
            }
            Float rawHp = self.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            if (rawHp == null || rawHp != this.invincibleHealthValue || rawHp <= 0.0F) {
                try {
                    DecayDamageUtil.BYPASS_DECAY.set(true);
                    self.setHealth(this.invincibleHealthValue);
                    self.getEntityData().set(LivingEntityAccessor.getDataHealthId(), this.invincibleHealthValue);
                } finally {
                    DecayDamageUtil.BYPASS_DECAY.remove();
                }
            }
        }
    }
}