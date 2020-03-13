package fr.sigma.box;

import java.io.Serializable;

public class Pair<T, L> implements Serializable {

    private static final long serialVersionUID = -5059418779041905807L;

    public T first;
    public L second;

    public Pair(T first, L second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return String.format("{%s} {%s}", first, second);
    }
}
