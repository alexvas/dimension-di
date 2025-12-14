package ru.dimension.di.assisted;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import ru.dimension.di.Assisted;
import ru.dimension.di.beans.SingletonBean;
import ru.dimension.di.named.EventListener;

/**
 * A component with both @Assisted and @Named parameters.
 */
public class NamedAssistedComponent {

  private final String componentId;
  private final SingletonBean singletonBean;
  private final EventListener eventListener;

  @Inject
  public NamedAssistedComponent(@Assisted String componentId,
                                SingletonBean singletonBean,
                                @Named("eventListener") EventListener eventListener) {
    this.componentId = componentId;
    this.singletonBean = singletonBean;
    this.eventListener = eventListener;
  }

  public String getComponentId() {
    return componentId;
  }

  public SingletonBean getSingletonBean() {
    return singletonBean;
  }

  public EventListener getEventListener() {
    return eventListener;
  }
}