package ru.dimension.di;

import jakarta.inject.Inject;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class DINamedFallbackTest {

  @BeforeEach
  void setUp() {
    ServiceLocator.clear();
  }

  // Simple test types
  static class Foo {
    final String id;
    Foo(String id) { this.id = id; }
  }

  static class Bar {
    @Inject Bar() {}
  }

  @Test
  @DisplayName("Unnamed get() falls back to the single named binding")
  void unnamedFallsBackToSingleNamed() {
    DimensionDI.builder()
        .provideNamed(Foo.class, "only", () -> new Foo("only-one"))
        .buildAndInit();

    // There is no unnamed binding for Foo, but there is exactly one named.
    Foo foo = ServiceLocator.get(Foo.class);

    assertNotNull(foo);
    assertEquals("only-one", foo.id);
  }

  @Test
  @DisplayName("Fallback does not trigger when unnamed binding exists")
  void noFallbackWhenUnnamedExists() {
    DimensionDI.builder()
        .provide(Foo.class, () -> new Foo("unnamed"))
        .provideNamed(Foo.class, "named", () -> new Foo("named"))
        .buildAndInit();

    Foo foo = ServiceLocator.get(Foo.class);

    // Must use unnamed binding, not named
    assertEquals("unnamed", foo.id);
  }

  @Test
  @DisplayName("Fallback does not choose between multiple named bindings")
  void noFallbackWhenMultipleNamed() {
    DimensionDI.builder()
        .provideNamed(Foo.class, "a", () -> new Foo("A"))
        .provideNamed(Foo.class, "b", () -> new Foo("B"))
        .buildAndInit();

    IllegalStateException ex = assertThrows(
        IllegalStateException.class,
        () -> ServiceLocator.get(Foo.class),
        "Multiple named bindings without an unnamed one must be treated as ambiguous"
    );

    assertTrue(ex.getMessage().contains("No provider registered"),
               "Should fail with 'no provider' for the unnamed key");
  }

  @Test
  @DisplayName("Explicit named lookup never falls back to unnamed binding")
  void explicitNamedDoesNotFallback() {
    DimensionDI.builder()
        .provide(Bar.class, () -> new Bar())  // unnamed only
        .buildAndInit();

    // unnamed works
    assertNotNull(ServiceLocator.get(Bar.class));

    // named must fail – no implicit fallback from named -> unnamed
    assertThrows(IllegalStateException.class,
                 () -> ServiceLocator.get(Bar.class, "x"));
  }

  @Test
  @DisplayName("Explicit named lookup uses matching named binding when both unnamed and named exist")
  void explicitNamedUsesNamedIfPresent() {
    DimensionDI.builder()
        .provide(Foo.class, () -> new Foo("unnamed"))
        .provideNamed(Foo.class, "special", () -> new Foo("special"))
        .buildAndInit();

    Foo foo = ServiceLocator.get(Foo.class, "special");

    assertEquals("special", foo.id);
  }
}