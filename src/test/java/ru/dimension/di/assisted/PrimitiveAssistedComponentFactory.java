package ru.dimension.di.assisted;

/**
 * Factory interface for creating PrimitiveAssistedComponent instances.
 */
public interface PrimitiveAssistedComponentFactory {
  PrimitiveAssistedComponent create(int count, double ratio, boolean enabled);
}