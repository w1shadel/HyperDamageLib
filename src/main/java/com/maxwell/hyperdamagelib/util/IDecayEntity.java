package com.maxwell.hyperdamagelib.util;

public interface IDecayEntity {
    boolean isSuperInvincible();
    void setSuperInvincible(boolean val);

    boolean isRemoveBypass();
    void setRemoveBypass(boolean val);

    boolean isKeepCurrentHealth();
    void setKeepCurrentHealth(boolean val);

    float getInvincibleHealthValue();
    void setInvincibleHealthValue(float val);

    boolean isHealBlocked();
    void setHealBlocked(boolean val);
}