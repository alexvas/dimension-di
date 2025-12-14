package ru.dimension.di.assisted;

import jakarta.inject.Inject;
import ru.dimension.di.Assisted;
import ru.dimension.di.beans.SingletonBean;

/**
 * A component with primitive @Assisted parameters.
 */
public class PrimitiveAssistedComponent {

  private final int count;
  private final double ratio;
  private final boolean enabled;
  private final SingletonBean singletonBean;

  @Inject
  public PrimitiveAssistedComponent(@Assisted int count,
                                    @Assisted double ratio,
                                    @Assisted boolean enabled,
                                    SingletonBean singletonBean) {
    this.count = count;
    this.ratio = ratio;
    this.enabled = enabled;
    this.singletonBean = singletonBean;
  }

  public int getCount() {
    return count;
  }

  public double getRatio() {
    return ratio;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public SingletonBean getSingletonBean() {
    return singletonBean;
  }
}