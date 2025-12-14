package ru.dimension.di.assisted;

import jakarta.inject.Inject;
import ru.dimension.di.Assisted;
import ru.dimension.di.beans.PrototypeBean;
import ru.dimension.di.beans.SingletonBean;

/**
 * A component with multiple @Assisted parameters of the same type,
 * distinguished by @Assisted("name").
 */
public class MultipleAssistedComponent {

  private final String primaryName;
  private final String secondaryName;
  private final SingletonBean singletonBean;
  private final PrototypeBean prototypeBean;

  @Inject
  public MultipleAssistedComponent(@Assisted("primary") String primaryName,
                                   @Assisted("secondary") String secondaryName,
                                   SingletonBean singletonBean,
                                   PrototypeBean prototypeBean) {
    this.primaryName = primaryName;
    this.secondaryName = secondaryName;
    this.singletonBean = singletonBean;
    this.prototypeBean = prototypeBean;
  }

  public String getPrimaryName() {
    return primaryName;
  }

  public String getSecondaryName() {
    return secondaryName;
  }

  public SingletonBean getSingletonBean() {
    return singletonBean;
  }

  public PrototypeBean getPrototypeBean() {
    return prototypeBean;
  }
}