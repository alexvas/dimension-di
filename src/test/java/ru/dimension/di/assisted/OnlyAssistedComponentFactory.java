package ru.dimension.di.assisted;

/**
 * Factory interface for OnlyAssistedComponent.
 */
public interface OnlyAssistedComponentFactory {
  OnlyAssistedComponent create(String value, int number);
}