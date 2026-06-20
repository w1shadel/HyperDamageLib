package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.DecayForceKillHelper;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Debug(export = true)
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
    private boolean csp$isLoginIncomplete() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer player) {
            return player.connection == null;
        }
        return self.tickCount <= 0;
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
        this.decayAmount = Math.max(0.0f, Math.min(amount, self.getMaxHealth()));
        float cappedMax = Math.max(0.0f, self.getMaxHealth() - this.decayAmount);
        if (self.getHealth() > cappedMax) {
            try {
                DecayDamageUtil.BYPASS_DECAY.set(true);
                self.setHealth(cappedMax);
            } finally {
                DecayDamageUtil.BYPASS_DECAY.remove();
            }
        }
        csp$syncToTracking();
    }

    @Override
    public boolean isSuperInvincible() {
        return this.superInvincible;
    }

    @Override
    public void setSuperInvincible(boolean val) {
        this.superInvincible = val;
        LivingEntity self = (LivingEntity) (Object) this;
        self.setInvulnerable(val);
        if (val) {
            self.setHealth(self.getMaxHealth());
        }
        csp$syncToTracking();
    }

    @Override
    public void addDecayAmount(float amount) {
        this.decayAmount = Math.max(0.0f, this.decayAmount + amount);
        this.decayHoldTicks = 100;
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
    private void csp$syncToTracking() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level() != null && !self.level().isClientSide()) {
            ModMessages.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> self),
                    new ClientboundDecaySyncPacket(self.getId(), this.decayAmount, this.superInvincible)
            );
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void csp$preventHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (this.superInvincible) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void csp$preventDie(DamageSource source, CallbackInfo ci) {
        if (csp$isLoginIncomplete()) {
            ci.cancel();
            return;
        }
        if (this.superInvincible) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float csp$modifySetHealthArg(float value) {
        if (csp$isLoginIncomplete()) {
            if (value < 0.0f || Float.isInfinite(value) || Float.isNaN(value)) {
                return 20.0f;
            }
            return value;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.superInvincible) {
            return self.getMaxHealth();
        }
        float cappedMax = Math.max(0.0f, self.getMaxHealth() - this.decayAmount);
        return Math.min(value, cappedMax);
    }

    @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true)
    private void csp$adjustIsAlive(CallbackInfoReturnable<Boolean> cir) {
        if (csp$isLoginIncomplete()) {
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
    private void csp$adjustIsDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
        if (csp$isLoginIncomplete()) {
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
    private void csp$adjustHealthReturn(CallbackInfoReturnable<Float> cir) {
        if (csp$isLoginIncomplete()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.superInvincible) {
            cir.setReturnValue(self.getMaxHealth());
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
    private void csp$tickDecayDeath(CallbackInfo ci) {
        if (csp$isLoginIncomplete()) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (this.superInvincible) {
            this.dead = false;
            this.deathTime = 0;
            self.setHealth(self.getMaxHealth());
        }
        if (!self.level().isClientSide() && this.decayAmount >= self.getMaxHealth()) {
            if (!this.decayDeathTriggered && !self.dead) {
                this.decayDeathTriggered = true;
                DecayForceKillHelper.decayForceKill(self);
            }
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void csp$saveDecay(CompoundTag nbt, CallbackInfo ci) {
        nbt.putFloat("decay_amount", this.decayAmount);
        nbt.putBoolean("super_invincible", this.superInvincible);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void csp$loadDecay(CompoundTag nbt, CallbackInfo ci) {
        if (nbt.contains("decay_amount")) {
            this.decayAmount = nbt.getFloat("decay_amount");
        }
        if (nbt.contains("super_invincible")) {
            this.superInvincible = nbt.getBoolean("super_invincible");
        }
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void csp$preventDropAllDeathLoot(DamageSource source, CallbackInfo ci) {
        if (this.superInvincible) {
            ci.cancel();
        }
    }
}