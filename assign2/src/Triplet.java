public class Triplet<T1, T2, T3> {
    private final T1 val1;
    private final T2 val2;

    private final T3 val3;

    public Triplet(T1 val1, T2 val2, T3 val3) {
        this.val1 = val1;
        this.val2 = val2;
        this.val3 = val3;
    }

    public T1 getVal1() {
        return val1;
    }

    public T2 getVal2() {
        return val2;
    }

    public T3 getVal3() {
        return val3;
    }

}
