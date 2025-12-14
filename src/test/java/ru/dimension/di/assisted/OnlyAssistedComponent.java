package ru.dimension.di.assisted;

import jakarta.inject.Inject;
import ru.dimension.di.Assisted;

/**
 * A component with only @Assisted parameters (no DI dependencies).
 */
public class OnlyAssistedComponent {

  private final String value;
  private final int number;

  @Inject
  public OnlyAssistedComponent(@Assisted String value,
                               @Assisted int number) {
    this.value = value;
    this.number = number;
  }

  public String getValue() {
    return value;
  }

  public int getNumber() {
    return number;
  }
}