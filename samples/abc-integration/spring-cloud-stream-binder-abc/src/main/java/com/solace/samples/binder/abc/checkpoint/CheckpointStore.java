package com.solace.samples.binder.abc.checkpoint;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;

public abstract class CheckpointStore implements Flushable, Closeable, DisposableBean,
    InitializingBean {

  final ConcurrentMap<String, Object> store = new ConcurrentHashMap<>();


  public void put(String key, Object value) {
    store.put(key, value);
  }

  @Nullable
  public Object get(String key) {
    return store.get(key);
  }

  @Nullable
  public Object remove(String key) {
    return store.remove(key);
  }

  @Nullable
  public Object putIfAbsent(String key, Object value) {
    return store.putIfAbsent(key, value);
  }

  @Nullable
  public boolean replace(String key, Object oldValue, Object newValue) {
    return store.replace(key, oldValue, newValue);
  }

  public void load() throws IOException {
    store.clear();
  }

  @Override
  public void flush() throws IOException {
    // No-op for in-memory store, as data is already in memory and doesn't need to be flushed to disk or external storage.
  }

}
