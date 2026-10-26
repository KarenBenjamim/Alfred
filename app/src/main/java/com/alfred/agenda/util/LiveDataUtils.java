package com.alfred.agenda.util;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;

import java.util.function.Function;

/** Pequenos utilitários de LiveData escritos em Java puro. */
public final class LiveDataUtils {

    private LiveDataUtils() {
    }

    /**
     * Sempre que "source" muda, troca a LiveData observada pela gerada por "fn".
     * Ex.: quando o dia muda, passa a observar os registros do novo dia.
     */
    public static <X, Y> LiveData<Y> switchMap(LiveData<X> source, Function<X, LiveData<Y>> fn) {
        MediatorLiveData<Y> result = new MediatorLiveData<>();
        result.addSource(source, new Observer<X>() {
            private LiveData<Y> current;

            @Override
            public void onChanged(X value) {
                LiveData<Y> next = fn.apply(value);
                if (current == next) return;
                if (current != null) result.removeSource(current);
                current = next;
                if (current != null) result.addSource(current, result::setValue);
            }
        });
        return result;
    }
}
