package ru.dimension.di.fields;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.concurrent.ScheduledExecutorService;
import ru.dimension.di.named.EventListener;

public class NamedFieldConsumer {
  @Inject @Named("eventListener")
  private EventListener listener;

  @Inject @Named("executorService")
  ScheduledExecutorService executor;

  public EventListener getEventListener() { return listener; }
  public ScheduledExecutorService getExecutor() { return executor; }
}