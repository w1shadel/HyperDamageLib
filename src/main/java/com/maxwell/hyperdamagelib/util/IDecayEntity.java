package com.maxwell.hyperdamagelib.util;

public interface IDecayEntity {
    default float getDecayAmount() {
        return 0.0F;
    }

    default void setDecayAmount(float amount) {}

    default void addDecayAmount(float amount) {}

    default boolean isSuperInvincible() {
        return false;
    }

    default void setSuperInvincible(boolean val) {}

    default boolean isRemoveBypass() {
        return false;
    }

    default void setRemoveBypass(boolean val) {}

    default void subtractTrueHP(float amount) {}

    default int getDecayHoldTicks() {
        return 0;
    }

    default void setDecayHoldTicks(int ticks) {}


    default boolean isKeepCurrentHealth() {
        return false;
    }

    default void setKeepCurrentHealth(boolean val) {}

    default float getInvincibleHealthValue() {
        return 20.0F;
    }

    default void setInvincibleHealthValue(float val) {}
}