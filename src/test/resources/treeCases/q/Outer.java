package q;

import java.util.List;

public class Outer {
    private int a, b;

    public Outer() {
    }

    public Outer(int a) {
        this.a = a;
    }

    void run(int x) {
    }

    void run(long x) {
    }

    <T> void generic(List<String> xs, T t, String... rest) {
    }

    static {
    }

    class Inner {
        void go() {
            Runnable r = () -> {
            };
            new Object() {
                void hidden() {
                }
            };
            class Local {
            }
        }
    }

    enum Mode {
        ON, OFF;

        void flip() {
        }
    }

    interface Callback {
        void call();
    }

    record Point(int x, int y) {
    }
}
