/**
 The Triplet class represents a generic container that holds three values of different types.
 It provides getter methods to access the values.

 @param <T1> The type of the first value.
 @param <T2> The type of the second value.
 @param <T3> The type of the third value.
 */
public class Triplet<T1, T2, T3> {
    private T1 val1;
    private T2 val2;

    private T3 val3;

    /**
     Constructs a Triplet object with the specified values.
     @param val1 The first value.
     @param val2 The second value.
     @param val3 The third value.
     */
    public Triplet(T1 val1, T2 val2, T3 val3) {
        this.val1 = val1;
        this.val2 = val2;
        this.val3 = val3;
    }

    /**
     Returns the first value.
     @return The first value.
     */
    public T1 getVal1() {
        return val1;
    }

    /**

     Returns the second value.
     @return The second value.
     */
    public T2 getVal2() {
        return val2;
    }

    /**

     Returns the third value.
     @return The third value.
     */
    public T3 getVal3() {
        return val3;
    }

    public void setVal1(T1 val1) {this.val1 = val1;}

    public void setVal2(T2 val2) {this.val2 = val2;}

    public void setVal3(T3 val3) {this.val3 = val3;}

}
