package de.hinundhergestellt.jhuh.vendors.shopify;

import com.shopify.admin.types.PageInfo;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.lang.Nullable;

import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.function.Function.identity;

class PagingIterator<T> implements Iterator<Stream<T>> {

    static <T> Stream<T> stream(Function<String, Pair<Stream<T>, PageInfo>> function) {
        var iterator = new PagingIterator<>(function);
        return StreamSupport
                .stream(spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .flatMap(identity());
    }

    private final Function<String, Pair<Stream<T>, PageInfo>> function;

    private @Nullable PageInfo current;

    private PagingIterator(Function<String, Pair<Stream<T>, PageInfo>> function) {
        this.function = function;
    }

    @Override
    public boolean hasNext() {
        return Optional.ofNullable(current)
                .map(PageInfo::getHasNextPage)
                .orElse(true);
    }

    @Override
    public Stream<T> next() {
        var after = Optional.ofNullable(current)
                .map(PageInfo::getEndCursor)
                .orElse(null);
        var result = function.apply(after);
        current = result.getRight();
        return result.getLeft();
    }
}
