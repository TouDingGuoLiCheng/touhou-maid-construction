package com.touhoumaid.construction.build;

/** Marks a {@link MaterialSource} that delegates to an inner vault source. */
public interface MaterialSourceWrapper {
    MaterialSource inner();
}
