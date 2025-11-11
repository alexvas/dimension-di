package ru.dimension.di.fields;

import jakarta.inject.Inject;
import ru.dimension.di.beans.PrototypeBean;
import ru.dimension.di.beans.SingletonBean;

public class FieldConsumer {
  @Inject public SingletonBean singletonBean;
  @Inject public PrototypeBean firstBean;
  @Inject public PrototypeBean secondBean;

  public FieldConsumer() {}
}