package s;

import java.util.List;

@Marker
public class User extends Base implements Api {
    private List<Plain> plains;
    private Mode mode = Mode.ON;

    static {
        Base.COUNT = 1;
    }

    public User() {
        super(1);
    }

    @Override
    public void call() {
        Object o = new Plain();
        Plain p = (Plain) o;
        Class<?> c = Base.class;
        int v = value;
        Runnable r = () -> new Base();
        Api a = new Api() {
            public void call() {
                int x = Base.COUNT;
            }
        };
    }

    enum Mode {
        ON, OFF
    }
}
