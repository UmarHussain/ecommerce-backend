package com.umar.ecommerce.catalog.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCache;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cold-load coalescing for one JVM. Thirty-two stripes bound the lock table.
 * A waiter gives up after two seconds and loads itself rather than waiting
 * without a limit. Another process has its own stripes; this is not a Redis lock.
 * {@code @Cacheable(sync = true)} is what calls {@link Cache#get(Object, Callable)}.
 */
final class CoalescingCacheManager implements CacheManager {

    private static final int STRIPES = 32;
    private static final long WAIT_MILLIS = 2_000;

    private final CacheManager delegate;
    private final ConcurrentHashMap<String, Cache> caches = new ConcurrentHashMap<>();

    CoalescingCacheManager(CacheManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, this::wrap);
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    private Cache wrap(String name) {
        Cache cache = delegate.getCache(name);
        if (cache == null) {
            return new NoOpCache(name);
        }
        return new CoalescingCache(cache);
    }

    static final class CoalescingCache implements Cache {

        private final Cache delegate;
        private final ReentrantLock[] stripes = new ReentrantLock[STRIPES];

        CoalescingCache(Cache delegate) {
            this.delegate = delegate;
            for (int index = 0; index < stripes.length; index++) {
                stripes[index] = new ReentrantLock();
            }
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            return delegate.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return delegate.get(key, type);
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            ValueWrapper cached = delegate.get(key);
            if (cached != null) {
                return cast(cached);
            }
            ReentrantLock stripe = stripes[Math.floorMod(String.valueOf(key).hashCode(), STRIPES)];
            boolean locked = false;
            try {
                locked = stripe.tryLock(WAIT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            try {
                ValueWrapper afterWait = delegate.get(key);
                if (afterWait != null) {
                    return cast(afterWait);
                }
                try {
                    T loaded = valueLoader.call();
                    delegate.put(key, loaded);
                    return loaded;
                } catch (Exception exception) {
                    throw new ValueRetrievalException(key, valueLoader, exception);
                }
            } finally {
                if (locked) {
                    stripe.unlock();
                }
            }
        }

        @Override
        public void put(Object key, Object value) {
            delegate.put(key, value);
        }

        @Override
        public void evict(Object key) {
            delegate.evict(key);
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @SuppressWarnings("unchecked")
        private static <T> T cast(ValueWrapper wrapper) {
            return (T) wrapper.get();
        }
    }
}
