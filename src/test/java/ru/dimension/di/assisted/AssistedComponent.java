package ru.dimension.di.assisted;

import jakarta.inject.Inject;
import ru.dimension.di.Assisted;
import ru.dimension.di.beans.SingletonBean;

/**
 * A component that requires both DI-resolved and runtime-provided parameters.
 */
public class AssistedComponent {

  private final String name;
  private final int priority;
  private final SingletonBean singletonBean;

  @Inject
  public AssistedComponent(@Assisted String name,
                           @Assisted int priority,
                           SingletonBean singletonBean) {
    this.name = name;
    this.priority = priority;
    this.singletonBean = singletonBean;
  }

  public String getName() {
    return name;
  }

  public int getPriority() {
    return priority;
  }

  public SingletonBean getSingletonBean() {
    return singletonBean;
  }
}