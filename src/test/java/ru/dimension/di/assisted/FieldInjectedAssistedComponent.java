package ru.dimension.di.assisted;

import jakarta.inject.Inject;
import ru.dimension.di.Assisted;
import ru.dimension.di.beans.PrototypeBean;
import ru.dimension.di.beans.SingletonBean;

/**
 * A component with @Assisted constructor params and @Inject field injection.
 */
public class FieldInjectedAssistedComponent {

  private final String name;

  @Inject
  private SingletonBean singletonBean;

  @Inject
  private PrototypeBean prototypeBean;

  @Inject
  public FieldInjectedAssistedComponent(@Assisted String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public SingletonBean getSingletonBean() {
    return singletonBean;
  }

  public PrototypeBean getPrototypeBean() {
    return prototypeBean;
  }
}