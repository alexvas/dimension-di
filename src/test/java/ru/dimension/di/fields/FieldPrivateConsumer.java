package ru.dimension.di.fields;

import jakarta.inject.Inject;
import ru.dimension.di.beans.SingletonBean;

public class FieldPrivateConsumer {
  @Inject private SingletonBean singleton;

  public FieldPrivateConsumer() {}

  public SingletonBean getSingleton() {
    return singleton;
  }
}