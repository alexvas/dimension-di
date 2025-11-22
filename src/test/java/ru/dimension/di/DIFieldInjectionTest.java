package ru.dimension.di;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.*;
import ru.dimension.di.beans.PrototypeBean;
import ru.dimension.di.beans.SingletonBean;
import ru.dimension.di.fields.*;
import ru.dimension.di.named.DefaultEventListenerImpl;
import ru.dimension.di.named.EventListener;
import ru.dimension.di.named.SpecialEventListenerImpl;

class DIFieldInjectionTest {

  @BeforeEach
  void setUp() {
    ServiceLocator.clear();
  }

  @Nested
  @DisplayName("Field Injection Tests")
  class FieldInjection {

    @Test
    @DisplayName("Field injection resolves and respects singleton/prototype scopes")
    void fieldInjectionScopes() {
      DimensionDI.builder()
          .scanPackages("ru.dimension.di.beans", "ru.dimension.di.fields")
          .buildAndInit();

      FieldConsumer c = ServiceLocator.get(FieldConsumer.class);
      assertNotNull(c);
      assertNotNull(c.singletonBean);
      assertNotNull(c.firstBean);
      assertNotNull(c.secondBean);

      // Two prototype fields must be different
      assertNotSame(c.firstBean, c.secondBean);

      // Singleton injected equals to direct one
      SingletonBean direct = ServiceLocator.get(SingletonBean.class);
      assertSame(direct, c.singletonBean);

      // Two consumers share singletons but have independent prototypes
      FieldConsumer c2 = ServiceLocator.get(FieldConsumer.class);
      assertSame(c.singletonBean, c2.singletonBean);
      assertNotSame(c.firstBean, c2.firstBean);
      assertNotSame(c.secondBean, c2.secondBean);
    }

    @Test
    @DisplayName("Private and inherited fields with @Inject are injected")
    void privateAndInheritedFieldsInjected() {
      DimensionDI.builder()
          .scanPackages("ru.dimension.di.beans", "ru.dimension.di.fields")
          .buildAndInit();

      FieldPrivateConsumer p = ServiceLocator.get(FieldPrivateConsumer.class);
      assertNotNull(p);
      assertNotNull(p.getSingleton());

      SubWithField s = ServiceLocator.get(SubWithField.class);
      assertNotNull(s);
      assertNotNull(s.getBaseSingleton(), "Superclass field should be injected");
      assertNotNull(s.subPrototype, "Subclass field should be injected");
    }

    @Test
    @DisplayName("Injecting into a final field throws a descriptive error")
    void finalFieldInjectionThrows() {
      DimensionDI.builder()
          .scanPackages("ru.dimension.di.beans", "ru.dimension.di.fields")
          .buildAndInit();

      IllegalStateException ex = assertThrows(
          IllegalStateException.class,
          () -> ServiceLocator.get(FinalFieldConsumer.class)
      );
      assertTrue(ex.getMessage().toLowerCase().contains("final field"));
    }

    @Test
    @DisplayName("Field injection supports @Named qualifiers")
    void namedFieldInjectionWorks() {
      ScheduledExecutorService executor = null;
      try {
        executor = Executors.newSingleThreadScheduledExecutor();
        final ScheduledExecutorService finalExecutor = executor;

        DimensionDI.builder()
            .scanPackages("ru.dimension.di.named", "ru.dimension.di.fields") // named beans + consumer
            // Default binding for EventListener (non-named)
            .bind(EventListener.class, DefaultEventListenerImpl.class)
            // Named binding for EventListener
            .bindNamed(EventListener.class, "eventListener", SpecialEventListenerImpl.class)
            // Provide named executor for @Named("executorService")
            .provideNamed(
                ScheduledExecutorService.class,
                "executorService",
                ServiceLocator.singleton(() -> finalExecutor)
            )
            .buildAndInit();

        NamedFieldConsumer c = ServiceLocator.get(NamedFieldConsumer.class);
        assertNotNull(c);
        assertNotNull(c.getExecutor(), "Named executor should be injected");
        assertEquals(SpecialEventListenerImpl.class, c.getEventListener().getClass(),
                     "@Named('eventListener') must resolve to SpecialEventListenerImpl");

      } finally {
        if (executor != null) {
          executor.shutdownNow();
        }
      }
    }
  }


  // --- Nested Test Beans for Method Injection ---

  public static class MethodConsumer {
    private SingletonBean singletonBean;
    private PrototypeBean prototypeBean;

    public MethodConsumer() {} // Default constructor

    @Inject
    public void setDependencies(SingletonBean sb, PrototypeBean pb) {
      this.singletonBean = sb;
      this.prototypeBean = pb;
    }
    public SingletonBean getSingletonBean() { return singletonBean; }
    public PrototypeBean getPrototypeBean() { return prototypeBean; }
  }

  public static class NamedMethodConsumer {
    private EventListener listener;
    @Inject
    public void setListener(@Named("eventListener") EventListener listener) {
      this.listener = listener;
    }
    public EventListener getListener() { return listener; }
  }

  public static class BaseMethodConsumer {
    protected SingletonBean baseSingleton;
    @Inject
    public void setBaseDep(SingletonBean singletonBean) {
      this.baseSingleton = singletonBean;
    }
    public SingletonBean getBaseSingleton() { return baseSingleton; }
  }

  public static class SubMethodConsumer extends BaseMethodConsumer {
    private PrototypeBean subPrototype;
    @Inject
    public void setSubDep(PrototypeBean prototypeBean) {
      this.subPrototype = prototypeBean;
    }
    public PrototypeBean getSubPrototype() { return subPrototype; }
  }

  public static class StaticMethodConsumer {
    @Inject
    public static void setStatic(SingletonBean bean) {
      // Should fail
    }
  }

  public static class AllInOneConsumer {
    private final SingletonBean constructorDep;
    @Inject
    private PrototypeBean fieldDep;
    private FieldConsumer methodDep;

    @Inject
    public AllInOneConsumer(SingletonBean constructorDep) {
      this.constructorDep = constructorDep;
    }

    @Inject
    public void setMethodDep(FieldConsumer methodDep) {
      this.methodDep = methodDep;
    }

    public SingletonBean getConstructorDep() { return constructorDep; }
    public PrototypeBean getFieldDep() { return fieldDep; }
    public FieldConsumer getMethodDep() { return methodDep; }
  }


  @Nested
  @DisplayName("Method Injection Tests")
  class MethodInjection {

    @Test
    @DisplayName("Method injection resolves dependencies after default construction")
    void basicMethodInjection() {
      DimensionDI.builder()
          .scanPackages("ru.dimension.di.beans") // For SingletonBean, PrototypeBean
          .provide(MethodConsumer.class, ServiceLocator.createConstructorProvider(MethodConsumer.class, false))
          .buildAndInit();

      MethodConsumer consumer = ServiceLocator.get(MethodConsumer.class);

      assertNotNull(consumer, "Consumer should be created.");
      assertNotNull(consumer.getSingletonBean(), "Singleton dependency should be injected via method.");
      assertNotNull(consumer.getPrototypeBean(), "Prototype dependency should be injected via method.");

      SingletonBean directSingleton = ServiceLocator.get(SingletonBean.class);
      assertSame(directSingleton, consumer.getSingletonBean(), "Injected singleton should be the same instance.");
    }

    @Test
    @DisplayName("Method injection supports @Named parameters")
    void namedMethodInjection() {
      DimensionDI.builder()
          .scanPackages("ru.dimension.di.named")
          .provide(NamedMethodConsumer.class, ServiceLocator.createConstructorProvider(NamedMethodConsumer.class, false))
          .bind(EventListener.class, DefaultEventListenerImpl.class)
          .bindNamed(EventListener.class, "eventListener", SpecialEventListenerImpl.class)
          .buildAndInit();

      NamedMethodConsumer consumer = ServiceLocator.get(NamedMethodConsumer.class);

      assertNotNull(consumer.getListener(), "Named dependency should be injected.");
      assertEquals(SpecialEventListenerImpl.class, consumer.getListener().getClass(),
                   "The 'special' implementation should be injected.");
    }

    @Test
    @DisplayName("Method injection works on inherited methods")
    void inheritedMethodInjection() {
      DimensionDI.builder()
          .scanPackages("ru.dimension.di.beans")
          .provide(SubMethodConsumer.class, ServiceLocator.createConstructorProvider(SubMethodConsumer.class, false))
          .buildAndInit();

      SubMethodConsumer consumer = ServiceLocator.get(SubMethodConsumer.class);

      assertNotNull(consumer.getBaseSingleton(), "Superclass method dependency should be injected.");
      assertNotNull(consumer.getSubPrototype(), "Subclass method dependency should be injected.");
    }

    @Test
    @DisplayName("Injecting into a static method throws an error")
    void staticMethodInjectionThrows() {
      DimensionDI.builder()
          .scanPackages("ru.dimension.di.beans")
          .provide(StaticMethodConsumer.class, ServiceLocator.createConstructorProvider(StaticMethodConsumer.class, false))
          .buildAndInit();

      IllegalStateException ex = assertThrows(IllegalStateException.class,
                                              () -> ServiceLocator.get(StaticMethodConsumer.class));

      assertTrue(ex.getMessage().contains("Cannot inject into static or abstract method"),
                 "Exception message should indicate illegal injection target.");
    }

    @Test
    @DisplayName("Constructor, field, and method injections all work together")
    void allInjectionTypesWorkTogether() {
      DimensionDI.builder()
          .scanPackages("ru.dimension.di.beans", "ru.dimension.di.fields")
          .provide(AllInOneConsumer.class, ServiceLocator.createConstructorProvider(AllInOneConsumer.class, false))
          .buildAndInit();

      AllInOneConsumer consumer = ServiceLocator.get(AllInOneConsumer.class);

      assertNotNull(consumer.getConstructorDep(), "Constructor dependency should be injected.");
      assertNotNull(consumer.getFieldDep(), "Field dependency should be injected.");
      assertNotNull(consumer.getMethodDep(), "Method dependency should be injected.");

      SingletonBean directSingleton = ServiceLocator.get(SingletonBean.class);
      assertSame(directSingleton, consumer.getConstructorDep(), "Constructor-injected singleton should be correct instance.");
    }
  }
}