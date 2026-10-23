package com.github.yimeng261.maidspell.compat.irons_spellbooks.entity.winefox;

/**
 * 星之魔女的权威生命与最大生命。未写入过时读取方得到调用方给的回退值。
 *
 * <p>先写数值再置就绪标记，读取方先读标记再读数值，因此跨线程读取也不会读到未写入的值。
 */
final class WinefoxHealthState {
    private volatile float health;
    private volatile boolean healthSet;
    private volatile float maxHealth;
    private volatile boolean maxHealthSet;

    float getHealth(float fallback) {
        return this.healthSet ? this.health : fallback;
    }

    void setHealth(float value) {
        this.health = value;
        this.healthSet = true;
    }

    float getMaxHealth(float fallback) {
        return this.maxHealthSet ? this.maxHealth : fallback;
    }

    void setMaxHealth(float value) {
        this.maxHealth = value;
        this.maxHealthSet = true;
    }
}
