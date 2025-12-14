package ru.dimension.di;

import org.junit.jupiter.api.*;
import ru.dimension.di.assisted.*;
import ru.dimension.di.beans.PrototypeBean;
import ru.dimension.di.beans.SingletonBean;
import ru.dimension.di.named.DefaultEventListenerImpl;
import ru.dimension.di.named.EventListener;
import ru.dimension.di.named.SpecialEventListenerImpl;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Assisted Injection functionality in Dimension-DI.
 * Covers both direct creation via ServiceLocator.create() and factory-based creation.
 */
class DIAssistedInjectionTest {

  private static final String TEST_BEANS_PACKAGE = "ru.dimension.di.beans";
  private static final String ASSISTED_PACKAGE = "ru.dimension.di.assisted";
  private static final String NAMED_PACKAGE = "ru.dimension.di.named";

  @BeforeEach
  void setUp() {
    ServiceLocator.clear();
  }

  // =========================================================================
  // Direct Creation Tests (ServiceLocator.create)
  // =========================================================================

  @Nested
  @DisplayName("Direct Creation with ServiceLocator.create()")
  class DirectCreationTests {

    @Test
    @DisplayName("Create instance with assisted and DI parameters")
    void createWithAssistedAndDIParameters() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      AssistedComponent component = ServiceLocator.create(
          AssistedComponent.class, "TestComponent", 42);

      assertNotNull(component);
      assertEquals("TestComponent", component.getName());
      assertEquals(42, component.getPriority());
      assertNotNull(component.getSingletonBean());

      // Verify singleton is shared
      SingletonBean directSingleton = ServiceLocator.get(SingletonBean.class);
      assertSame(directSingleton, component.getSingletonBean());
    }

    @Test
    @DisplayName("Create multiple instances - DI singletons are shared")
    void multipleInstancesShareSingletons() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      AssistedComponent c1 = ServiceLocator.create(AssistedComponent.class, "First", 1);
      AssistedComponent c2 = ServiceLocator.create(AssistedComponent.class, "Second", 2);

      assertNotSame(c1, c2);
      assertEquals("First", c1.getName());
      assertEquals("Second", c2.getName());

      // Both should share the same singleton
      assertSame(c1.getSingletonBean(), c2.getSingletonBean());
    }

    @Test
    @DisplayName("Create with primitive types")
    void createWithPrimitiveTypes() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      PrimitiveAssistedComponent component = ServiceLocator.create(
          PrimitiveAssistedComponent.class, 100, 3.14, true);

      assertNotNull(component);
      assertEquals(100, component.getCount());
      assertEquals(3.14, component.getRatio(), 0.001);
      assertTrue(component.isEnabled());
      assertNotNull(component.getSingletonBean());
    }

    @Test
    @DisplayName("Create with only assisted parameters (no DI deps)")
    void createWithOnlyAssistedParameters() {
      DimensionDI.builder().buildAndInit();

      OnlyAssistedComponent component = ServiceLocator.create(
          OnlyAssistedComponent.class, "hello", 999);

      assertNotNull(component);
      assertEquals("hello", component.getValue());
      assertEquals(999, component.getNumber());
    }

    @Test
    @DisplayName("Create performs field injection after construction")
    void createPerformsFieldInjection() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      FieldInjectedAssistedComponent component = ServiceLocator.create(
          FieldInjectedAssistedComponent.class, "WithFields");

      assertNotNull(component);
      assertEquals("WithFields", component.getName());
      assertNotNull(component.getSingletonBean(), "Field-injected singleton should not be null");
      assertNotNull(component.getPrototypeBean(), "Field-injected prototype should not be null");
    }

    @Test
    @DisplayName("Missing assisted argument throws exception")
    void missingAssistedArgumentThrows() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      assertThrows(IllegalArgumentException.class, () ->
          ServiceLocator.create(AssistedComponent.class, "OnlyName"));
    }

    @Test
    @DisplayName("Create with @Named DI dependencies")
    void createWithNamedDependencies() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE, NAMED_PACKAGE)
          .bind(EventListener.class, DefaultEventListenerImpl.class)
          .bindNamed(EventListener.class, "eventListener", SpecialEventListenerImpl.class)
          .buildAndInit();

      NamedAssistedComponent component = ServiceLocator.create(
          NamedAssistedComponent.class, "comp-123");

      assertNotNull(component);
      assertEquals("comp-123", component.getComponentId());
      assertNotNull(component.getSingletonBean());
      assertNotNull(component.getEventListener());
      assertEquals(SpecialEventListenerImpl.class, component.getEventListener().getClass());
    }
  }

  // =========================================================================
  // Factory Creation Tests (ServiceLocator.createFactory)
  // =========================================================================

  @Nested
  @DisplayName("Factory Creation with ServiceLocator.createFactory()")
  class FactoryCreationTests {

    @Test
    @DisplayName("Create factory and use it to create instances")
    void createFactoryAndUse() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      AssistedComponentFactory factory = ServiceLocator.createFactory(
          AssistedComponentFactory.class, AssistedComponent.class);

      assertNotNull(factory);

      AssistedComponent c1 = factory.create("Component1", 10);
      AssistedComponent c2 = factory.create("Component2", 20);

      assertNotNull(c1);
      assertNotNull(c2);
      assertNotSame(c1, c2);

      assertEquals("Component1", c1.getName());
      assertEquals(10, c1.getPriority());
      assertEquals("Component2", c2.getName());
      assertEquals(20, c2.getPriority());

      // Singletons are shared
      assertSame(c1.getSingletonBean(), c2.getSingletonBean());
    }

    @Test
    @DisplayName("Factory with primitive parameters")
    void factoryWithPrimitives() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      PrimitiveAssistedComponentFactory factory = ServiceLocator.createFactory(
          PrimitiveAssistedComponentFactory.class, PrimitiveAssistedComponent.class);

      PrimitiveAssistedComponent component = factory.create(50, 2.5, false);

      assertEquals(50, component.getCount());
      assertEquals(2.5, component.getRatio(), 0.001);
      assertFalse(component.isEnabled());
    }

    @Test
    @DisplayName("Factory with named assisted parameters")
    void factoryWithNamedAssistedParams() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      MultipleAssistedComponentFactory factory = ServiceLocator.createFactory(
          MultipleAssistedComponentFactory.class, MultipleAssistedComponent.class);

      MultipleAssistedComponent component = factory.create("PrimaryValue", "SecondaryValue");

      assertEquals("PrimaryValue", component.getPrimaryName());
      assertEquals("SecondaryValue", component.getSecondaryName());
      assertNotNull(component.getSingletonBean());
      assertNotNull(component.getPrototypeBean());
    }

    @Test
    @DisplayName("Factory triggers field injection")
    void factoryTriggersFieldInjection() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      FieldInjectedAssistedComponentFactory factory = ServiceLocator.createFactory(
          FieldInjectedAssistedComponentFactory.class, FieldInjectedAssistedComponent.class);

      FieldInjectedAssistedComponent component = factory.create("FactoryCreated");

      assertEquals("FactoryCreated", component.getName());
      assertNotNull(component.getSingletonBean());
      assertNotNull(component.getPrototypeBean());
    }

    @Test
    @DisplayName("Factory inferred from return type")
    void factoryInferredFromReturnType() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      // Use overload that infers target class from return type
      AssistedComponentFactory factory = ServiceLocator.createFactory(AssistedComponentFactory.class);

      AssistedComponent component = factory.create("Inferred", 77);

      assertEquals("Inferred", component.getName());
      assertEquals(77, component.getPriority());
    }

    @Test
    @DisplayName("Invalid factory interface throws exception")
    void invalidFactoryInterfaceThrows() {
      DimensionDI.builder().buildAndInit();

      // Not an interface
      assertThrows(IllegalArgumentException.class, () ->
          ServiceLocator.createFactory(String.class, AssistedComponent.class));
    }

    @Test
    @DisplayName("Factory with multiple abstract methods throws exception")
    void multipleAbstractMethodsThrows() {
      DimensionDI.builder().buildAndInit();

      assertThrows(IllegalArgumentException.class, () ->
          ServiceLocator.createFactory(InvalidMultiMethodFactory.class, AssistedComponent.class));
    }

    @Test
    @DisplayName("Factory parameter mismatch throws exception")
    void factoryParameterMismatchThrows() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      assertThrows(IllegalArgumentException.class, () ->
          ServiceLocator.createFactory(MismatchedFactory.class, AssistedComponent.class));
    }

    // Invalid factory interfaces for testing
    interface InvalidMultiMethodFactory {
      AssistedComponent create(String name, int priority);
      AssistedComponent createAnother(String name);
    }

    interface MismatchedFactory {
      AssistedComponent create(String name); // Missing 'priority' parameter
    }
  }

  // =========================================================================
  // DimensionDI.Builder.bindFactory() Tests
  // =========================================================================

  @Nested
  @DisplayName("Factory Binding with DimensionDI.Builder.bindFactory()")
  class FactoryBindingTests {

    @Test
    @DisplayName("Bind factory and retrieve via ServiceLocator.get()")
    void bindFactoryAndRetrieve() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .bindFactory(AssistedComponentFactory.class, AssistedComponent.class)
          .buildAndInit();

      AssistedComponentFactory factory = ServiceLocator.get(AssistedComponentFactory.class);

      assertNotNull(factory);

      AssistedComponent component = factory.create("BoundFactory", 123);
      assertEquals("BoundFactory", component.getName());
      assertEquals(123, component.getPriority());
    }

    @Test
    @DisplayName("Bound factory is singleton")
    void boundFactoryIsSingleton() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .bindFactory(AssistedComponentFactory.class, AssistedComponent.class)
          .buildAndInit();

      AssistedComponentFactory f1 = ServiceLocator.get(AssistedComponentFactory.class);
      AssistedComponentFactory f2 = ServiceLocator.get(AssistedComponentFactory.class);

      assertSame(f1, f2, "Factory should be a singleton");
    }

    @Test
    @DisplayName("Bind factory with inferred target class")
    void bindFactoryWithInferredTarget() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .bindFactory(AssistedComponentFactory.class) // Target inferred from return type
          .buildAndInit();

      AssistedComponentFactory factory = ServiceLocator.get(AssistedComponentFactory.class);
      AssistedComponent component = factory.create("InferredBinding", 456);

      assertEquals("InferredBinding", component.getName());
    }

    @Test
    @DisplayName("Multiple factory bindings work correctly")
    void multipleFactoryBindings() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .bindFactory(AssistedComponentFactory.class, AssistedComponent.class)
          .bindFactory(PrimitiveAssistedComponentFactory.class, PrimitiveAssistedComponent.class)
          .bindFactory(OnlyAssistedComponentFactory.class, OnlyAssistedComponent.class)
          .buildAndInit();

      AssistedComponentFactory f1 = ServiceLocator.get(AssistedComponentFactory.class);
      PrimitiveAssistedComponentFactory f2 = ServiceLocator.get(PrimitiveAssistedComponentFactory.class);
      OnlyAssistedComponentFactory f3 = ServiceLocator.get(OnlyAssistedComponentFactory.class);

      assertNotNull(f1);
      assertNotNull(f2);
      assertNotNull(f3);

      assertEquals("Test", f1.create("Test", 1).getName());
      assertEquals(99, f2.create(99, 1.0, true).getCount());
      assertEquals("Value", f3.create("Value", 42).getValue());
    }

    @Test
    @DisplayName("Factory with @Named dependencies")
    void factoryWithNamedDependencies() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE, NAMED_PACKAGE)
          .bind(EventListener.class, DefaultEventListenerImpl.class)
          .bindNamed(EventListener.class, "eventListener", SpecialEventListenerImpl.class)
          .bindFactory(NamedAssistedComponentFactory.class, NamedAssistedComponent.class)
          .buildAndInit();

      NamedAssistedComponentFactory factory = ServiceLocator.get(NamedAssistedComponentFactory.class);
      NamedAssistedComponent component = factory.create("named-comp");

      assertEquals("named-comp", component.getComponentId());
      assertEquals(SpecialEventListenerImpl.class, component.getEventListener().getClass());
    }
  }

  // =========================================================================
  // Error Handling Tests
  // =========================================================================

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Requesting @Assisted class directly throws descriptive error")
    void directRequestOfAssistedClassThrows() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE, ASSISTED_PACKAGE)
          .buildAndInit();

      IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
          ServiceLocator.get(AssistedComponent.class));

      assertTrue(ex.getMessage().contains("@Assisted"));
      assertTrue(ex.getMessage().contains("factory"));
    }

    @Test
    @DisplayName("Factory with unused parameter throws exception")
    void factoryWithUnusedParameterThrows() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      interface ExtraParamFactory {
        AssistedComponent create(String name, int priority, String extraUnused);
      }

      assertThrows(IllegalArgumentException.class, () ->
          ServiceLocator.createFactory(ExtraParamFactory.class, AssistedComponent.class));
    }

    @Test
    @DisplayName("Mismatched @Assisted names throw exception")
    void mismatchedAssistedNamesThrow() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .buildAndInit();

      // Factory params have different names than constructor @Assisted params
      interface WrongNamesFactory {
        MultipleAssistedComponent create(
            @Assisted("wrong1") String a,
            @Assisted("wrong2") String b);
      }

      assertThrows(IllegalArgumentException.class, () ->
          ServiceLocator.createFactory(WrongNamesFactory.class, MultipleAssistedComponent.class));
    }
  }

  // =========================================================================
  // Integration Tests
  // =========================================================================

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Factory-created objects work with existing DI graph")
    void factoryIntegratesWithDIGraph() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE, NAMED_PACKAGE)
          .bind(EventListener.class, DefaultEventListenerImpl.class)
          .bindNamed(EventListener.class, "eventListener", SpecialEventListenerImpl.class)
          .bindFactory(AssistedComponentFactory.class, AssistedComponent.class)
          .bindFactory(NamedAssistedComponentFactory.class, NamedAssistedComponent.class)
          .buildAndInit();

      // Get singletons directly
      SingletonBean singleton = ServiceLocator.get(SingletonBean.class);

      // Create via factories
      AssistedComponentFactory f1 = ServiceLocator.get(AssistedComponentFactory.class);
      NamedAssistedComponentFactory f2 = ServiceLocator.get(NamedAssistedComponentFactory.class);

      AssistedComponent c1 = f1.create("Comp1", 1);
      AssistedComponent c2 = f1.create("Comp2", 2);
      NamedAssistedComponent c3 = f2.create("Named1");

      // All should share the same singleton
      assertSame(singleton, c1.getSingletonBean());
      assertSame(singleton, c2.getSingletonBean());
      assertSame(singleton, c3.getSingletonBean());

      // Named dependency resolved correctly
      assertEquals(SpecialEventListenerImpl.class, c3.getEventListener().getClass());
    }

    @Test
    @DisplayName("Factory works with prototype scope dependencies")
    void factoryWithPrototypeDependencies() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .bindFactory(MultipleAssistedComponentFactory.class, MultipleAssistedComponent.class)
          .buildAndInit();

      MultipleAssistedComponentFactory factory = ServiceLocator.get(MultipleAssistedComponentFactory.class);

      MultipleAssistedComponent c1 = factory.create("P1", "S1");
      MultipleAssistedComponent c2 = factory.create("P2", "S2");

      // Singleton is shared
      assertSame(c1.getSingletonBean(), c2.getSingletonBean());

      // Prototype is different for each factory call
      assertNotSame(c1.getPrototypeBean(), c2.getPrototypeBean());
    }

    @Test
    @DisplayName("Complex scenario: multiple factories and regular beans")
    void complexScenarioMultipleFactoriesAndBeans() {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE, NAMED_PACKAGE)
          .bind(EventListener.class, DefaultEventListenerImpl.class)
          .bindNamed(EventListener.class, "eventListener", SpecialEventListenerImpl.class)
          .bindFactory(AssistedComponentFactory.class)
          .bindFactory(PrimitiveAssistedComponentFactory.class)
          .bindFactory(FieldInjectedAssistedComponentFactory.class)
          .buildAndInit();

      // Get regular beans
      SingletonBean singleton = ServiceLocator.get(SingletonBean.class);
      PrototypeBean proto1 = ServiceLocator.get(PrototypeBean.class);
      PrototypeBean proto2 = ServiceLocator.get(PrototypeBean.class);
      assertNotSame(proto1, proto2);

      // Get factories
      AssistedComponentFactory f1 = ServiceLocator.get(AssistedComponentFactory.class);
      PrimitiveAssistedComponentFactory f2 = ServiceLocator.get(PrimitiveAssistedComponentFactory.class);
      FieldInjectedAssistedComponentFactory f3 = ServiceLocator.get(FieldInjectedAssistedComponentFactory.class);

      // Create via factories
      AssistedComponent ac = f1.create("AC", 100);
      PrimitiveAssistedComponent pac = f2.create(42, 3.14, true);
      FieldInjectedAssistedComponent fiac = f3.create("FIAC");

      // Verify all share singleton
      assertSame(singleton, ac.getSingletonBean());
      assertSame(singleton, pac.getSingletonBean());
      assertSame(singleton, fiac.getSingletonBean());

      // Verify factory-created components have correct values
      assertEquals("AC", ac.getName());
      assertEquals(42, pac.getCount());
      assertEquals("FIAC", fiac.getName());
      assertNotNull(fiac.getPrototypeBean());
    }
  }

  // =========================================================================
  // Thread Safety Tests
  // =========================================================================

  @Nested
  @DisplayName("Thread Safety")
  class ThreadSafetyTests {

    @Test
    @DisplayName("Factory is thread-safe for concurrent creation")
    void factoryThreadSafety() throws InterruptedException {
      DimensionDI.builder()
          .scanPackages(TEST_BEANS_PACKAGE)
          .bindFactory(AssistedComponentFactory.class, AssistedComponent.class)
          .buildAndInit();

      AssistedComponentFactory factory = ServiceLocator.get(AssistedComponentFactory.class);
      SingletonBean expectedSingleton = ServiceLocator.get(SingletonBean.class);

      int threadCount = 10;
      int iterationsPerThread = 100;
      java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
      java.util.concurrent.CopyOnWriteArrayList<AssistedComponent> results = new java.util.concurrent.CopyOnWriteArrayList<>();
      java.util.concurrent.CopyOnWriteArrayList<Throwable> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

      for (int t = 0; t < threadCount; t++) {
        final int threadNum = t;
        new Thread(() -> {
          try {
            for (int i = 0; i < iterationsPerThread; i++) {
              AssistedComponent c = factory.create("Thread" + threadNum + "-" + i, i);
              results.add(c);
            }
          } catch (Throwable e) {
            errors.add(e);
          } finally {
            latch.countDown();
          }
        }).start();
      }

      latch.await();

      assertTrue(errors.isEmpty(), "No errors should occur: " + errors);
      assertEquals(threadCount * iterationsPerThread, results.size());

      // All should share the same singleton
      for (AssistedComponent c : results) {
        assertSame(expectedSingleton, c.getSingletonBean());
      }
    }
  }
}