package com.winthier.claim;

public enum Trust {
    OWNER(5),
    SUB(4),
    BUILD(3),
    CHEST(2),
    USE(1),
    NONE(0);

    private int level;

    Trust(int level) {
        this.level = level;
    }

    public boolean implies(Trust other) {
        return this.level >= other.level;
    }
}
