package ru.dimension.di.assisted;

import ru.dimension.di.Assisted;

/**
 * Factory interface with named @Assisted parameters to match constructor.
 */
public interface MultipleAssistedComponentFactory {
  MultipleAssistedComponent create(@Assisted("primary") String primaryName,
                                   @Assisted("secondary") String secondaryName);
}