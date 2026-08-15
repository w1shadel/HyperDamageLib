package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
    private boolean decayDeathTriggered = false;
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

    @Unique
    private boolean decay$isLoginIncomplete() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player) {
            return player.connection == null;
        }
        return self.tickCount <= 0;
    }

    @Unique
    private float decay$getTargetInvincibleHealth() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.keepCurrentHealth) {
            return this.invincibleHealthValue;
        }
        return self.getMaxHealth();
    }

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
        float originalMax = (float) self.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH
        );
        if (Float.isNaN(originalMax)) {
            originalMax = 20.0F;
        } else if (Float.isInfinite(originalMax)) {
            originalMax = 1000000.0F;
        }
        if (Float.isNaN(amount)) {
            amount = 0.0F;
        } else if (Float.isInfinite(amount)) {
            amount = originalMax;
        }
        this.decayAmount = Math.max(0.0f, Math.min(amount, originalMax));
        float cappedMax = originalMax - getDecayAmount();
        if (Float.isNaN(cappedMax) || cappedMax < 0.0F) {
            cappedMax = 0.0F;
        }
        float realHealth = self.getEntityData().get(LivingEntityAccessor.getDataHealthId());
        if (Float.isNaN(realHealth)) {
            realHealth = 0.0F;
        }
        if (realHealth > cappedMax || Float.isInfinite(realHealth)) {
            try {
                DecayDamageUtil.BYPASS_DECAY.set(true);
                self.setHealth(cappedMax);
            } finally {
                DecayDamageUtil.BYPASS_DECAY.remove();
            }
        }
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

        // ★ トランスフォーマー用のUUIDセットと完全に連動させる！
        InvincibleHelper.setInvincible(self, val);

        float currentRealHealth = self.getEntityData().get(LivingEntityAccessor.getDataHealthId());
        if (val) {
            if (this.keepCurrentHealth) {
                this.invincibleHealthValue = Math.max(1.0f, Math.min(currentRealHealth, self.getMaxHealth()));
            } else {
                this.invincibleHealthValue = self.getMaxHealth();
            }
            self.setHealth(this.invincibleHealthValue);
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

    @Override
    public void subtractTrueHP(float amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.superInvincible) return;
        DamageSource erosionSource = DecayDamageUtil.getErosionSource(self.level(), null);
        self.hurt(erosionSource, amount);
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
    private void decay$preventHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (this.superInvincible) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void decay$preventDie(DamageSource source, CallbackInfo ci) {
        if (decay$isLoginIncomplete()) {
            ci.cancel();
            return;
        }
        if (this.superInvincible) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float decay$modifySetHealthArg(float value) {
        if (Float.isNaN(value)) {
            value = 0.0F;
        }
        if (value < 0.0F) {
            value = 0.0F;
        }
        if (decay$isLoginIncomplete()) {
            return value;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity dummy) {
            if (!dummy.isRemoveBypass()) {
                return dummy.getMaxHealth();
            }
        }
        if (this.superInvincible) {
            if (com.maxwell.hyperdamagelib.util.DecayDamageUtil.FORCE_DAMAGE.get()) {
                return value;
            }
            return decay$getTargetInvincibleHealth();
        }
        float currentHealth = self.getEntityData().get(LivingEntityAccessor.getDataHealthId());
        if (Float.isNaN(currentHealth)) currentHealth = 0.0F;
        float maxHp = self.getMaxHealth();
        if (Float.isNaN(maxHp) || Float.isInfinite(maxHp)) maxHp = 1000000.0F;
        float cappedMax = Math.max(0.0f, maxHp - this.decayAmount);
        if (this.decayHoldTicks > 0 || this.decayAmount > 0.0f) {
            if (value > currentHealth) {
                return Math.min(currentHealth, cappedMax);
            }
        }
        return Math.min(value, cappedMax);
    }

    @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true)
    private void decay$adjustIsAlive(CallbackInfoReturnable<Boolean> cir) {
        if (decay$isLoginIncomplete()) {
            return;
        }
        if (this.superInvincible) {
            cir.setReturnValue(true);
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.decayAmount >= self.getMaxHealth()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
    private void decay$adjustIsDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
        if (decay$isLoginIncomplete()) {
            return;
        }
        if (this.superInvincible) {
            cir.setReturnValue(false);
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.decayAmount >= self.getMaxHealth()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void decay$adjustHealthReturn(CallbackInfoReturnable<Float> cir) {
        if (decay$isLoginIncomplete()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.superInvincible) {
            cir.setReturnValue(decay$getTargetInvincibleHealth());
            return;
        }
        if (this.decayAmount >= self.getMaxHealth()) {
            cir.setReturnValue(-Float.MAX_VALUE);
        } else if (this.decayAmount > 0.0f) {
            float original = self.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            float cappedMax = Math.max(0.0f, self.getMaxHealth() - this.decayAmount);
            cir.setReturnValue(Math.min(original, cappedMax));
        }
    }

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void decay$tickDecayDeath(CallbackInfo ci) {
        if (decay$isLoginIncomplete()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity dummy) {
            if (!dummy.isRemoveBypass()) {
                this.dead = false;
                this.deathTime = 0;
                if (self.getPose() == Pose.DYING) {
                    self.setPose(Pose.STANDING);
                }
                self.setHealth(self.getMaxHealth());
            }
        }
        if (this.superInvincible) {
            if (this.dead || this.deathTime > 0) {
                this.dead = false;
                this.deathTime = 0;
                self.setPose(Pose.STANDING);
                self.setHealth(decay$getTargetInvincibleHealth());
                decay$syncToTracking();

            } else {
                self.setHealth(decay$getTargetInvincibleHealth());
            }
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void decay$saveDecay(CompoundTag nbt, CallbackInfo ci) {
        nbt.putFloat("decay_amount", this.decayAmount);
        nbt.putBoolean("super_invincible", this.superInvincible);
        nbt.putBoolean("keep_current_health", this.keepCurrentHealth);
        nbt.putFloat("invincible_health_value", this.invincibleHealthValue);
        nbt.putBoolean("heal_blocked", this.healBlocked);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void decay$loadDecay(CompoundTag nbt, CallbackInfo ci) {
        if (nbt.contains("decay_amount")) {
            this.decayAmount = nbt.getFloat("decay_amount");
        }
        if (nbt.contains("super_invincible")) {
            this.superInvincible = nbt.getBoolean("super_invincible");
        }
        if (nbt.contains("keep_current_health")) {
            this.keepCurrentHealth = nbt.getBoolean("keep_current_health");
        }
        if (nbt.contains("invincible_health_value")) {
            this.invincibleHealthValue = nbt.getFloat("invincible_health_value");
        }
        if (nbt.contains("heal_blocked")) {
            this.healBlocked = nbt.getBoolean("heal_blocked");
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void decay$preventDropAllDeathLoot(DamageSource source, CallbackInfo ci) {
        if (this.superInvincible) {
            ci.cancel();
        }
    }

    @Inject(method = "getMaxHealth", at = @At("RETURN"), cancellable = true)
    private void decay$adjustMaxHealthReturn(CallbackInfoReturnable<Float> cir) {
        if (decay$isLoginIncomplete()) {
            return;
        }
        if (this.superInvincible) {
            return;
        }
        float originalMax = cir.getReturnValue();
        float cappedMax = Math.max(1.0f, originalMax - this.decayAmount);
        cir.setReturnValue(cappedMax);
    }
}