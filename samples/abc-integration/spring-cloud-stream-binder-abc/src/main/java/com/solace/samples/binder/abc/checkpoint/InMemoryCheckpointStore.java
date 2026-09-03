package com.solace.samples.binder.abc.checkpoint;

import java.io.IOException;

public class InMemoryCheckpointStore extends CheckpointStore {

  @Override
  public void close() throws IOException {
    flush();
  }



  @Override
  public void destroy() throws Exception {
    flush();
  }

  @Override
  public void afterPropertiesSet() throws Exception {

  }
}
