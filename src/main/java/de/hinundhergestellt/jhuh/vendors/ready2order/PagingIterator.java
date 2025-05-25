package de.hinundhergestellt.jhuh.vendors.ready2order;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.function.Function.identity;

public class PagingIterator<T> implements Iterator<Stream<T>> {

    static <T> Stream<T> stream(Function<Integer, Stream<T>> function) {
        var iterator = new PagingIterator<>(function);
        return StreamSupport
                .stream(spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .flatMap(identity());
    }

    private final Function<Integer, Stream<T>> function;

    private int page = 1;
    private boolean exhausted;

    private PagingIterator(Function<Integer, Stream<T>> function) {
        this.function = function;
    }

    @Override
    public boolean hasNext() {
        return !exhausted;
    }

    @Override
    public Stream<T> next() {
        var next = function.apply(page++).toList();
        if (next.isEmpty()) {
            exhausted = true;
        }
        return next.stream();
    }
}
