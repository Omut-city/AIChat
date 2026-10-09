package omut.aichat.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ThrowablesTest {

    @Test
    @DisplayName("Exception without a cause returns itself")
    void noCause() {
        RuntimeException ex = new RuntimeException("alone");
        assertThat(Throwables.rootCause(ex)).isSameAs(ex);
    }

    @Test
    @DisplayName("Single cause is unwrapped")
    void singleCause() {
        Throwable inner = new IllegalStateException("inner");
        Throwable outer = new RuntimeException("outer", inner);
        assertThat(Throwables.rootCause(outer)).isSameAs(inner);
    }

    @Test
    @DisplayName("Deep chain returns the deepest cause")
    void deepChain() {
        Throwable root = new IOException("io");
        Throwable middle = new RuntimeException("middle", root);
        Throwable outer = new IllegalStateException("outer", middle);
        assertThat(Throwables.rootCause(outer)).isSameAs(root);
    }

    @Test
    @DisplayName("Self-referencing cause does not loop forever")
    void selfReference() {
        RuntimeException self = new RuntimeException("loop") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertThat(Throwables.rootCause(self)).isSameAs(self);
    }

    @Test
    @DisplayName("Cycle of two stops on the second")
    void twoCycle() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        assertThat(Throwables.rootCause(b)).isSameAs(a);
    }
}