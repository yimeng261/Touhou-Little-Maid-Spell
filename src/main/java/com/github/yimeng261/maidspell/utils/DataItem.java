package com.github.yimeng261.maidspell.utils;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

public class DataItem{
    EntityMaid maid;
    float amount;
    boolean isCanceled;
    public DataItem(EntityMaid maid, float amount, boolean isCanceled) {
        this.maid = maid;
        this.amount = amount;
        this.isCanceled = isCanceled;
    }
    public DataItem(EntityMaid maid, float amount) {
        this.maid = maid;
        this.amount = amount;
        this.isCanceled = false;
    }
    public EntityMaid getMaid() {
        return maid;
    }
    public float getAmount() {
        return amount;
    }
    public boolean isCanceled() {
        return isCanceled;
    }
    public void setCanceled(boolean isCanceled) {
        this.isCanceled = isCanceled;
    }
    public void setAmount(float amount) {
        this.amount = amount;
    }
    public void setMaid(EntityMaid maid) {
        this.maid = maid;
    }
}