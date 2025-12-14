package ru.dimension.di.assisted;

/**
 * Factory interface for creating NamedAssistedComponent instances.
 */
public interface NamedAssistedComponentFactory {
  NamedAssistedComponent create(String componentId);
}