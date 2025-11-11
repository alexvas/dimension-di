package ru.dimension.di.fields;

import jakarta.inject.Inject;
import ru.dimension.di.beans.PrototypeBean;

public class SubWithField extends BaseWithField {
  @Inject public PrototypeBean subPrototype;
}