package ru.dimension.di;

import java.lang.annotation.*;

/**
 * Marks a constructor parameter as "assisted" - provided by the caller
 * at creation time rather than resolved from the DI container.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Assisted {
  /**
   * Optional name to distinguish multiple parameters of the same type.
   */
  String value() default "";
}