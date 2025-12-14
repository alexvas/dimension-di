package ru.dimension.di;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import ru.dimension.di.ServiceLocator.Key;

public final class DimensionDI {

  private DimensionDI() {}

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final Set<String> packagesToScan = new HashSet<>();
    private final Map<Key, Supplier<?>> manualProviders = new HashMap<>();
    private final List<FactoryBinding<?>> factoryBindings = new ArrayList<>();
    private boolean autoAliasUniqueNamed = true;  // NEW: enabled by default

    public Builder scanPackages(String... packages) {
      packagesToScan.addAll(List.of(packages));
      return this;
    }

    /**
     * Controls whether to automatically create unnamed aliases for types
     * that have exactly one named binding.
     * Default is true.
     */
    public Builder autoAliasUniqueNamed(boolean enabled) {
      this.autoAliasUniqueNamed = enabled;
      return this;
    }

    public <T> Builder bind(Class<T> interfaceType, Class<? extends T> implementationType) {
      Supplier<T> provider = () -> ServiceLocator.get(implementationType);
      manualProviders.put(Key.of(interfaceType), provider);
      return this;
    }

    public <T> Builder bindNamed(Class<T> interfaceType, String name, Class<? extends T> implementationType) {
      Supplier<T> provider = () -> ServiceLocator.get(implementationType);
      manualProviders.put(new Key(interfaceType, name), provider);
      return this;
    }

    public <T> Builder provide(Class<T> type, Supplier<? extends T> provider) {
      manualProviders.put(Key.of(type), provider);
      return this;
    }

    public <T> Builder provideSingleton(Class<T> type, Supplier<? extends T> provider) {
      manualProviders.put(Key.of(type), ServiceLocator.singleton(provider));
      return this;
    }

    public <T> Builder provideNamed(Class<T> type, String name, Supplier<? extends T> provider) {
      manualProviders.put(new Key(type, name), provider);
      return this;
    }

    public <T> Builder instance(Class<T> type, T instance) {
      manualProviders.put(Key.of(type), () -> instance);
      return this;
    }

    public <T> Builder instanceNamed(Class<T> type, String name, T instance) {
      manualProviders.put(new Key(type, name), () -> instance);
      return this;
    }

    public <F> Builder bindFactory(Class<F> factoryInterface, Class<?> targetClass) {
      factoryBindings.add(new FactoryBinding<>(factoryInterface, targetClass));
      return this;
    }

    public <F> Builder bindFactory(Class<F> factoryInterface) {
      Class<?> targetClass = inferTargetClass(factoryInterface);
      factoryBindings.add(new FactoryBinding<>(factoryInterface, targetClass));
      return this;
    }

    private Class<?> inferTargetClass(Class<?> factoryInterface) {
      for (Method method : factoryInterface.getMethods()) {
        if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())
            && method.getDeclaringClass() != Object.class) {
          return method.getReturnType();
        }
      }
      throw new IllegalArgumentException(
          "Cannot infer target class from factory interface: " + factoryInterface.getName());
    }

    public void buildAndInit() {
      Map<Key, Supplier<?>> allProviders = new HashMap<>();

      // 1. Run the scanner
      if (!packagesToScan.isEmpty()) {
        List<DependencyScanner.ScanResult> scanResults =
            DependencyScanner.scan(packagesToScan.toArray(new String[0]));
        try {
          for (var result : scanResults) {
            Class<?> clazz = Class.forName(result.className());
            Supplier<?> provider = ServiceLocator.createConstructorProvider(clazz, result.isSingleton());
            allProviders.put(Key.of(clazz), provider);

            for (String ifaceName : result.interfaces()) {
              try {
                Class<?> iface = Class.forName(ifaceName);
                Key ifaceKey = Key.of(iface);
                if (!allProviders.containsKey(ifaceKey) && !manualProviders.containsKey(ifaceKey)) {
                  allProviders.put(ifaceKey, provider);
                }
              } catch (ClassNotFoundException ignored) {}
            }
          }
        } catch (ClassNotFoundException e) {
          throw new RuntimeException("Dimension-DI: A class found during scan could not be loaded", e);
        }
      }

      // 2. Add manual providers
      allProviders.putAll(manualProviders);

      // 3. NEW: Auto-create unnamed aliases for unique named bindings
      if (autoAliasUniqueNamed) {
        createUnnamedAliases(allProviders);
      }

      // 4. Initialize
      ServiceLocator.init(allProviders);

      // 5. Register factories
      for (var binding : factoryBindings) {
        registerFactory(binding);
      }
    }

    /**
     * For each type that has exactly one named binding and no unnamed binding,
     * create an unnamed alias pointing to the named one.
     */
    private void createUnnamedAliases(Map<Key, Supplier<?>> allProviders) {
      // Group named keys by type
      Map<Class<?>, List<Key>> namedKeysByType = new HashMap<>();
      Set<Class<?>> typesWithUnnamed = new HashSet<>();

      for (Key key : allProviders.keySet()) {
        if (key.isNamed()) {
          namedKeysByType.computeIfAbsent(key.type, k -> new ArrayList<>()).add(key);
        } else {
          typesWithUnnamed.add(key.type);
        }
      }

      // Create aliases for types with exactly one named binding and no unnamed
      for (var entry : namedKeysByType.entrySet()) {
        Class<?> type = entry.getKey();
        List<Key> namedKeys = entry.getValue();

        if (namedKeys.size() == 1 && !typesWithUnnamed.contains(type)) {
          Key namedKey = namedKeys.get(0);
          Key unnamedKey = Key.of(type);
          allProviders.put(unnamedKey, allProviders.get(namedKey));
        }
      }
    }

    private <F> void registerFactory(FactoryBinding<F> binding) {
      Supplier<F> factorySupplier = ServiceLocator.createFactorySupplier(
          binding.factoryInterface, binding.targetClass);
      ServiceLocator.registerProvider(binding.factoryInterface, factorySupplier);
    }

    private record FactoryBinding<F>(Class<F> factoryInterface, Class<?> targetClass) {}
  }
}