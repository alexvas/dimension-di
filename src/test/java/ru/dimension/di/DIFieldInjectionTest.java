package ru.dimension.di;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.*;
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
    try {
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
              ServiceLocator.singleton(() -> Executors.newSingleThreadScheduledExecutor())
          )
          .buildAndInit();

      NamedFieldConsumer c = ServiceLocator.get(NamedFieldConsumer.class);
      assertNotNull(c);
      assertNotNull(c.getExecutor(), "Named executor should be injected");
      assertEquals(SpecialEventListenerImpl.class, c.getEventListener().getClass(),
                   "@Named('eventListener') must resolve to SpecialEventListenerImpl");

    } finally {
      try {
        NamedFieldConsumer c = ServiceLocator.get(NamedFieldConsumer.class);
        if (c != null && c.getExecutor() != null) {
          c.getExecutor().shutdownNow();
        }
      } catch (Exception ignored) {}
    }
  }
}