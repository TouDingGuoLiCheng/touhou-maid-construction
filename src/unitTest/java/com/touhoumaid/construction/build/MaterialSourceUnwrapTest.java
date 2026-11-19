package com.touhoumaid.construction.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class MaterialSourceUnwrapTest {
    private static final class StubTerminal implements MaterialSource {
        @Override
        public boolean hasMaterial(String blockId) {
            return false;
        }

        @Override
        public boolean tryConsume(String blockId) {
            return false;
        }

        @Override
        public void markDirty() {
        }
    }

    private static final class StubWrapper implements MaterialSource, MaterialSourceWrapper {
        private final MaterialSource inner;

        private StubWrapper(MaterialSource inner) {
            this.inner = inner;
        }

        @Override
        public MaterialSource inner() {
            return inner;
        }

        @Override
        public boolean hasMaterial(String blockId) {
            return inner.hasMaterial(blockId);
        }

        @Override
        public boolean tryConsume(String blockId) {
            return inner.tryConsume(blockId);
        }

        @Override
        public void markDirty() {
            inner.markDirty();
        }
    }

    @Test
    void unwrapPeelsNestedWrappersInAnyOrder() {
        StubTerminal terminal = new StubTerminal();
        MaterialSource wrapped = new StubWrapper(new StubWrapper(terminal));
        assertSame(terminal, StockIndexedMaterialSource.unwrapTerminal(wrapped));
    }

    @Test
    void unwrapReturnsBareTerminalUnchanged() {
        StubTerminal terminal = new StubTerminal();
        assertSame(terminal, StockIndexedMaterialSource.unwrapTerminal(terminal));
    }

    @Test
    void stockIndexedWrapStillUnwrapsToTerminal() {
        StubTerminal terminal = new StubTerminal();
        MaterialSource wrapped = StockIndexedMaterialSource.wrap(terminal);
        assertSame(terminal, StockIndexedMaterialSource.unwrapTerminal(wrapped));
    }
}
