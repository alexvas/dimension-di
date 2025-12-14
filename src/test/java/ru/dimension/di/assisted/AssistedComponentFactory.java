package ru.dimension.di.assisted;

/**
 * Factory interface for creating AssistedComponent instances.
 */
public interface AssistedComponentFactory {
  AssistedComponent create(String name, int priority);
}