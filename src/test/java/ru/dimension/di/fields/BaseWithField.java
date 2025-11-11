package ru.dimension.di.fields;

import jakarta.inject.Inject;
import ru.dimension.di.beans.SingletonBean;

public class BaseWithField {
  @Inject protected SingletonBean baseSingleton;

  public SingletonBean getBaseSingleton() {
    return baseSingleton;
  }
}