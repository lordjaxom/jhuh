package de.hinundhergestellt.jhuh.core.task;

import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.commons.lang3.exception.UncheckedInterruptedException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Futures {

    public static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        } catch (ExecutionException e) {
            throw new UncheckedExecutionException(e);
        }
    }

    private Futures() {
    }
}
