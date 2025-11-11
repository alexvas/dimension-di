package ru.dimension.di.fields;

import jakarta.inject.Inject;
import ru.dimension.di.beans.SingletonBean;

public class FinalFieldConsumer {
  @Inject public final SingletonBean singleton;

  public FinalFieldConsumer() {
    this.singleton = null; // to compile; DI will try to inject and must fail
  }
}