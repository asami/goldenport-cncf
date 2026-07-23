package org.goldenport.cncf.entity;

/*
 * @since   Jul. 24, 2026
 * @version Jul. 24, 2026
 * @author  ASAMI, Tomoharu
 */
public final class EntityConcurrencyToken {
    public static final EntityConcurrencyToken LEGACY =
        new EntityConcurrencyToken(0L);
    public static final EntityConcurrencyToken INITIAL =
        new EntityConcurrencyToken(1L);

    private final long value;

    private EntityConcurrencyToken(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException(
                "EntityConcurrencyToken must be non-negative"
            );
        }
        this.value = value;
    }

    static EntityConcurrencyToken createInternal(long value) {
        return new EntityConcurrencyToken(value);
    }

    static long storageValueInternal(EntityConcurrencyToken token) {
        return token.value;
    }

    public String print() {
        return Long.toString(value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EntityConcurrencyToken &&
            value == ((EntityConcurrencyToken) other).value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }
}
