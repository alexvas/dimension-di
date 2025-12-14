package ru.dimension.di;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Dimension-DI: tiny runtime locator for constructor-injected objects.
 *
 * <p>Features:
 * <ul>
 *   <li>Keys are (type, optional name) to support jakarta.inject.Named</li>
 *   <li>Providers are Suppliers invoked lazily</li>
 *   <li>Singletons are cached via a wrapper</li>
 *   <li>Cycle detection guards against circular constructor graphs</li>
 *   <li>Assisted injection for mixed DI/runtime parameters</li>
 *   <li>Factory interface generation for type-safe assisted injection</li>
 *   <li>Named-to-unnamed fallback for flexible binding resolution</li>
 * </ul>
 */
public final class ServiceLocator {

  private ServiceLocator() {}

  // =========================================================================
  // Public key type
  // =========================================================================

  public static final class Key {
    public final Class<?> type;
    public final String name;

    Key(Class<?> type, String name) {
      this.type = Objects.requireNonNull(type, "type");
      this.name = (name == null || name.isBlank()) ? null : name;
    }

    public static Key of(Class<?> type) {
      return new Key(type, null);
    }

    public static Key of(Class<?> type, String name) {
      return new Key(type, name);
    }

    /**
     * Returns the unnamed variant of this key (same type, no name).
     */
    public Key unnamed() {
      return name == null ? this : new Key(type, null);
    }

    public boolean isNamed() {
      return name != null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Key k)) return false;
      return type.equals(k.type) && Objects.equals(name, k.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, name);
    }

    @Override
    public String toString() {
      return "Key{" + type.getName() + (name != null ? ", name=" + name : "") + "}";
    }
  }

  // =========================================================================
  // Provider registry and configuration
  // =========================================================================

  private static final Map<Key, Supplier<?>> providers = new ConcurrentHashMap<>();
  private static final ThreadLocal<Deque<Key>> creationStack = ThreadLocal.withInitial(ArrayDeque::new);

  /**
   * Controls whether named lookups fall back to unnamed bindings.
   * Default is true for convenience.
   */
  private static volatile boolean namedFallbackEnabled = true;

  /**
   * Controls whether unnamed lookups can resolve to a unique named binding.
   * Default is true for maximum flexibility.
   */
  private static volatile boolean unnamedFallbackEnabled = true;

  /**
   * Enables or disables named-to-unnamed fallback.
   * When enabled, requesting @Named("x") Type will fall back to unnamed Type if named not found.
   */
  public static void setNamedFallbackEnabled(boolean enabled) {
    namedFallbackEnabled = enabled;
  }

  /**
   * Enables or disables unnamed-to-named fallback.
   * When enabled, requesting Type (unnamed) will resolve to the unique named binding if:
   * - No unnamed binding exists
   * - Exactly one named binding exists for that type
   */
  public static void setUnnamedFallbackEnabled(boolean enabled) {
    unnamedFallbackEnabled = enabled;
  }

  public static void clear() {
    providers.clear();
  }

  public static void init(Map<Key, Supplier<?>> map) {
    clear();
    providers.putAll(map);
  }

  // =========================================================================
  // Basic registration API
  // =========================================================================

  public static <T> void registerProvider(Class<T> type, Supplier<? extends T> provider) {
    providers.put(Key.of(type), provider);
  }

  public static <T> void registerProvider(Class<T> type, String name, Supplier<? extends T> provider) {
    providers.put(Key.of(type, name), provider);
  }

  public static <T> void registerInstance(Class<T> type, T instance) {
    providers.put(Key.of(type), () -> instance);
  }

  public static <T> void registerInstance(Class<T> type, String name, T instance) {
    providers.put(Key.of(type, name), () -> instance);
  }

  public static void alias(Key alias, Key target) {
    Supplier<?> s = providers.get(target);
    if (s == null) throw new IllegalStateException("No provider for target: " + target);
    providers.put(alias, s);
  }

  public static <T> void override(Key key, Supplier<? extends T> provider) {
    providers.put(key, provider);
  }

  // =========================================================================
  // Retrieval API with fallback support
  // =========================================================================

  public static <T> T get(Class<T> type) {
    return type.cast(getInternal(Key.of(type)));
  }

  public static <T> T get(Class<T> type, String name) {
    return type.cast(getInternal(Key.of(type, name)));
  }

  public static boolean has(Class<?> type) {
    return providers.containsKey(Key.of(type)) ||
        (unnamedFallbackEnabled && findUniqueNamedBinding(type) != null);
  }

  public static boolean has(Class<?> type, String name) {
    return providers.containsKey(Key.of(type, name)) ||
        (namedFallbackEnabled && providers.containsKey(Key.of(type)));
  }

  /**
   * Core resolution method with fallback logic.
   */
  private static Object getInternal(Key key) {
    Supplier<?> supplier = providers.get(key);

    // Fallback: unnamed -> single named
    if (supplier == null && key.name == null) {
      Supplier<?> onlyNamed = findSingleNamedProviderForType(key.type);
      if (onlyNamed != null) {
        supplier = onlyNamed;
      }
    }

    if (supplier == null) {
      throw new IllegalStateException("No provider registered for " + key);
    }

    Deque<Key> stack = creationStack.get();
    if (stack.contains(key)) {
      throw new IllegalStateException("Circular dependency detected: " + stack + " -> " + key);
    }
    stack.push(key);
    try {
      return supplier.get();
    } finally {
      stack.pop();
    }
  }

  private static Supplier<?> findSingleNamedProviderForType(Class<?> type) {
    Supplier<?> found = null;
    for (Map.Entry<Key, Supplier<?>> e : providers.entrySet()) {
      Key k = e.getKey();
      if (k.type.equals(type) && k.name != null) {
        if (found != null) {
          // more than one named binding – ambiguous
          return null;
        }
        found = e.getValue();
      }
    }
    return found;
  }

  /**
   * Resolves a supplier for the given key, applying fallback rules.
   */
  private static Supplier<?> resolveSupplier(Key key) {
    // 1. Try exact match first
    Supplier<?> supplier = providers.get(key);
    if (supplier != null) {
      return supplier;
    }

    // 2. Named key fallback: try unnamed binding
    if (key.isNamed() && namedFallbackEnabled) {
      supplier = providers.get(key.unnamed());
      if (supplier != null) {
        return supplier;
      }
    }

    // 3. Unnamed key fallback: try unique named binding
    if (!key.isNamed() && unnamedFallbackEnabled) {
      Key uniqueNamed = findUniqueNamedBinding(key.type);
      if (uniqueNamed != null) {
        return providers.get(uniqueNamed);
      }
    }

    return null;
  }

  /**
   * Finds the unique named binding for a type, if exactly one exists.
   * Returns null if zero or more than one named binding exists.
   */
  private static Key findUniqueNamedBinding(Class<?> type) {
    Key found = null;
    for (Key key : providers.keySet()) {
      if (key.type.equals(type) && key.isNamed()) {
        if (found != null) {
          // More than one named binding - ambiguous
          return null;
        }
        found = key;
      }
    }
    return found;
  }

  /**
   * Builds a helpful error message hint.
   */
  private static String buildFallbackHint(Key key) {
    List<Key> available = new ArrayList<>();
    for (Key k : providers.keySet()) {
      if (k.type.equals(key.type)) {
        available.add(k);
      }
    }

    if (available.isEmpty()) {
      return ". No bindings exist for this type.";
    }

    StringBuilder hint = new StringBuilder(". Available bindings for ").append(key.type.getSimpleName()).append(": ");
    for (int i = 0; i < available.size(); i++) {
      if (i > 0) hint.append(", ");
      Key k = available.get(i);
      hint.append(k.isNamed() ? "@Named(\"" + k.name + "\")" : "(unnamed)");
    }
    return hint.toString();
  }

  // =========================================================================
  // Singleton wrapper
  // =========================================================================

  public static <T> Supplier<T> singleton(Supplier<T> delegate) {
    return new SingletonSupplier<>(delegate);
  }

  private static final class SingletonSupplier<T> implements Supplier<T> {
    private final Supplier<T> delegate;
    private volatile T instance;

    private SingletonSupplier(Supplier<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T get() {
      T r = instance;
      if (r == null) {
        synchronized (this) {
          r = instance;
          if (r == null) {
            instance = r = delegate.get();
          }
        }
      }
      return r;
    }
  }

  // =========================================================================
  // Constructor-based provider creation
  // =========================================================================

  public static <T> Supplier<T> createConstructorProvider(Class<T> clazz, boolean singleton) {
    Constructor<?> ctor = findInjectConstructor(clazz);

    boolean hasAssistedParams = Arrays.stream(ctor.getParameters())
        .anyMatch(p -> p.isAnnotationPresent(Assisted.class));

    if (hasAssistedParams) {
      return () -> {
        throw new IllegalStateException(
            "Class " + clazz.getName() + " has @Assisted parameters and must be created via a factory. " +
                "Use ServiceLocator.create() or register a factory with DimensionDI.Builder.bindFactory()");
      };
    }

    MethodHandle mh = unreflectConstructor(clazz, ctor);

    Parameter[] params = ctor.getParameters();
    String[] names = new String[params.length];
    Class<?>[] types = new Class<?>[params.length];
    for (int i = 0; i < params.length; i++) {
      types[i] = params[i].getType();
      jakarta.inject.Named named = params[i].getAnnotation(jakarta.inject.Named.class);
      names[i] = (named != null && !named.value().isBlank()) ? named.value() : null;
    }

    Supplier<T> s = () -> {
      Object[] args = new Object[types.length];
      for (int i = 0; i < types.length; i++) {
        // Uses getInternal which now has fallback support
        Key key = names[i] != null ? Key.of(types[i], names[i]) : Key.of(types[i]);
        args[i] = getInternal(key);
      }

      final T instance;
      try {
        @SuppressWarnings("unchecked")
        T tmp = (T) mh.invokeWithArguments(args);
        instance = tmp;
      } catch (Throwable t) {
        if (t instanceof IllegalStateException ise) throw ise;
        if (t instanceof RuntimeException re) throw re;
        throw new RuntimeException("Failed to instantiate " + clazz.getName(), t);
      }

      injectMembers(instance);
      return instance;
    };
    return singleton ? singleton(s) : s;
  }

  // =========================================================================
  // Member injection (field and method)
  // =========================================================================

  public static void injectMembers(Object instance) {
    if (instance == null) {
      return;
    }

    Deque<Class<?>> hierarchy = new ArrayDeque<>();
    for (Class<?> c = instance.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
      hierarchy.push(c);
    }

    while (!hierarchy.isEmpty()) {
      Class<?> c = hierarchy.pop();

      // Field Injection
      for (Field f : c.getDeclaredFields()) {
        if (!f.isAnnotationPresent(jakarta.inject.Inject.class)) {
          continue;
        }

        int mod = f.getModifiers();
        if (Modifier.isStatic(mod)) continue;
        if (Modifier.isFinal(mod)) {
          throw new IllegalStateException("Cannot inject into final field: " + c.getName() + "#" + f.getName());
        }

        jakarta.inject.Named named = f.getAnnotation(jakarta.inject.Named.class);
        String name = (named != null && !named.value().isBlank()) ? named.value() : null;

        // Uses getInternal with fallback
        Key key = name != null ? Key.of(f.getType(), name) : Key.of(f.getType());
        Object dep = getInternal(key);

        try {
          f.setAccessible(true);
          f.set(instance, dep);
        } catch (IllegalAccessException e) {
          throw new RuntimeException("Failed to inject field: " + c.getName() + "#" + f.getName(), e);
        }
      }

      // Method Injection
      for (Method m : c.getDeclaredMethods()) {
        if (!m.isAnnotationPresent(jakarta.inject.Inject.class) || m.isBridge()) {
          continue;
        }

        int mod = m.getModifiers();
        if (Modifier.isStatic(mod) || Modifier.isAbstract(mod)) {
          throw new IllegalStateException(
              "Cannot inject into static or abstract method: " + c.getName() + "#" + m.getName());
        }

        Parameter[] params = m.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
          Parameter p = params[i];
          jakarta.inject.Named named = p.getAnnotation(jakarta.inject.Named.class);
          String name = (named != null && !named.value().isBlank()) ? named.value() : null;
          Key key = name != null ? Key.of(p.getType(), name) : Key.of(p.getType());
          args[i] = getInternal(key);
        }

        try {
          m.setAccessible(true);
          m.invoke(instance, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
          throw new RuntimeException("Failed to inject method: " + c.getName() + "#" + m.getName(), e);
        }
      }
    }
  }

  // =========================================================================
  // ASSISTED INJECTION - Direct creation
  // =========================================================================

  @SuppressWarnings("unchecked")
  public static <T> T create(Class<T> clazz, Object... assistedArgs) {
    Constructor<?> ctor = findInjectConstructor(clazz);
    Parameter[] params = ctor.getParameters();
    Object[] args = resolveAssistedParameters(params, assistedArgs);

    try {
      MethodHandle mh = unreflectConstructor(clazz, ctor);
      T instance = (T) mh.invokeWithArguments(args);
      injectMembers(instance);
      return instance;
    } catch (Throwable t) {
      if (t instanceof RuntimeException re) throw re;
      throw new RuntimeException("Failed to create " + clazz.getName(), t);
    }
  }

  private static Object[] resolveAssistedParameters(Parameter[] params, Object[] assistedArgs) {
    Object[] args = new Object[params.length];
    boolean[] assistedUsed = new boolean[assistedArgs.length];

    for (int i = 0; i < params.length; i++) {
      Parameter p = params[i];
      Assisted assisted = p.getAnnotation(Assisted.class);

      if (assisted != null) {
        int matchIdx = findMatchingAssistedArg(p.getType(), assisted.value(), assistedArgs, assistedUsed);
        if (matchIdx == -1) {
          throw new IllegalArgumentException(
              "No matching assisted argument for parameter " + p.getType().getName() +
                  (assisted.value().isEmpty() ? "" : " named '" + assisted.value() + "'") +
                  " at index " + i);
        }
        assistedUsed[matchIdx] = true;
        args[i] = assistedArgs[matchIdx];
      } else {
        jakarta.inject.Named named = p.getAnnotation(jakarta.inject.Named.class);
        String name = (named != null && !named.value().isBlank()) ? named.value() : null;
        Key key = name != null ? Key.of(p.getType(), name) : Key.of(p.getType());
        args[i] = getInternal(key);
      }
    }
    return args;
  }

  private static int findMatchingAssistedArg(Class<?> type, String name, Object[] args, boolean[] used) {
    for (int i = 0; i < args.length; i++) {
      if (used[i] || args[i] == null) continue;
      if (type.isInstance(args[i])) {
        return i;
      }
    }

    for (int i = 0; i < args.length; i++) {
      if (used[i] || args[i] == null) continue;
      if (isAssignableWithPrimitives(type, args[i].getClass())) {
        return i;
      }
    }

    return -1;
  }

  private static boolean isAssignableWithPrimitives(Class<?> target, Class<?> source) {
    if (target.isAssignableFrom(source)) return true;

    Map<Class<?>, Class<?>> primitiveToWrapper = Map.of(
        boolean.class, Boolean.class,
        byte.class, Byte.class,
        char.class, Character.class,
        short.class, Short.class,
        int.class, Integer.class,
        long.class, Long.class,
        float.class, Float.class,
        double.class, Double.class
    );

    if (target.isPrimitive()) {
      return primitiveToWrapper.get(target) == source;
    }
    if (source.isPrimitive()) {
      return target == primitiveToWrapper.get(source);
    }

    return false;
  }

  // =========================================================================
  // ASSISTED INJECTION - Factory creation
  // =========================================================================

  @SuppressWarnings("unchecked")
  public static <F> F createFactory(Class<F> factoryInterface, Class<?> targetClass) {
    validateFactoryInterface(factoryInterface);

    Method factoryMethod = findFactoryMethod(factoryInterface);
    Constructor<?> ctor = findInjectConstructor(targetClass);
    MethodHandle mh = unreflectConstructor(targetClass, ctor);

    Parameter[] ctorParams = ctor.getParameters();
    ParameterMapping[] mappings = buildParameterMappings(ctorParams, factoryMethod.getParameters());

    return (F) Proxy.newProxyInstance(
        factoryInterface.getClassLoader(),
        new Class<?>[] { factoryInterface },
        (proxy, method, args) -> {
          if (method.equals(factoryMethod)) {
            return invokeFactory(mh, ctorParams, mappings, args, targetClass);
          }
          return handleObjectMethod(proxy, method, args);
        }
    );
  }

  public static <F> Supplier<F> createFactorySupplier(Class<F> factoryInterface, Class<?> targetClass) {
    return singleton(() -> createFactory(factoryInterface, targetClass));
  }

  public static <F> F createFactory(Class<F> factoryInterface) {
    Method factoryMethod = findFactoryMethod(factoryInterface);
    Class<?> targetClass = factoryMethod.getReturnType();
    return createFactory(factoryInterface, targetClass);
  }

  private static void validateFactoryInterface(Class<?> factoryInterface) {
    if (!factoryInterface.isInterface()) {
      throw new IllegalArgumentException(factoryInterface.getName() + " is not an interface");
    }
  }

  private static Method findFactoryMethod(Class<?> factoryInterface) {
    Method factoryMethod = null;
    for (Method m : factoryInterface.getMethods()) {
      if (!m.isDefault() && !Modifier.isStatic(m.getModifiers())
          && m.getDeclaringClass() != Object.class) {
        if (factoryMethod != null) {
          throw new IllegalArgumentException(
              "Factory interface must have exactly one abstract method: " + factoryInterface.getName());
        }
        factoryMethod = m;
      }
    }
    if (factoryMethod == null) {
      throw new IllegalArgumentException(
          "Factory interface has no abstract method: " + factoryInterface.getName());
    }
    return factoryMethod;
  }

  private sealed interface ParameterMapping {
    record FromDI(Key key) implements ParameterMapping {}
    record FromFactory(int factoryParamIndex) implements ParameterMapping {}
  }

  private static ParameterMapping[] buildParameterMappings(Parameter[] ctorParams, Parameter[] factoryParams) {
    ParameterMapping[] mappings = new ParameterMapping[ctorParams.length];
    boolean[] factoryParamUsed = new boolean[factoryParams.length];

    for (int i = 0; i < ctorParams.length; i++) {
      Parameter p = ctorParams[i];
      Assisted assisted = p.getAnnotation(Assisted.class);

      if (assisted != null) {
        int matchIndex = findMatchingFactoryParam(p, assisted.value(), factoryParams, factoryParamUsed);
        if (matchIndex == -1) {
          throw new IllegalArgumentException(
              "No matching factory parameter for @Assisted " + p.getType().getName() +
                  (assisted.value().isEmpty() ? "" : " named '" + assisted.value() + "'"));
        }
        factoryParamUsed[matchIndex] = true;
        mappings[i] = new ParameterMapping.FromFactory(matchIndex);
      } else {
        jakarta.inject.Named named = p.getAnnotation(jakarta.inject.Named.class);
        String name = (named != null && !named.value().isBlank()) ? named.value() : null;
        mappings[i] = new ParameterMapping.FromDI(new Key(p.getType(), name));
      }
    }

    for (int i = 0; i < factoryParamUsed.length; i++) {
      if (!factoryParamUsed[i]) {
        throw new IllegalArgumentException(
            "Factory parameter at index " + i + " (" + factoryParams[i].getType().getName() +
                ") does not match any @Assisted constructor parameter");
      }
    }

    return mappings;
  }

  private static int findMatchingFactoryParam(Parameter ctorParam, String assistedName,
                                              Parameter[] factoryParams, boolean[] used) {
    Class<?> ctorParamType = ctorParam.getType();

    for (int j = 0; j < factoryParams.length; j++) {
      if (used[j]) continue;
      Parameter fp = factoryParams[j];
      if (!isTypeCompatible(ctorParamType, fp.getType())) continue;

      Assisted fAssisted = fp.getAnnotation(Assisted.class);
      String fName = fAssisted != null ? fAssisted.value() : "";

      if (assistedName.equals(fName)) {
        return j;
      }
    }

    if (assistedName.isEmpty()) {
      for (int j = 0; j < factoryParams.length; j++) {
        if (used[j]) continue;
        if (isTypeCompatible(ctorParamType, factoryParams[j].getType())) {
          Assisted fAssisted = factoryParams[j].getAnnotation(Assisted.class);
          String fName = fAssisted != null ? fAssisted.value() : "";
          if (fName.isEmpty()) {
            return j;
          }
        }
      }
    }

    return -1;
  }

  private static boolean isTypeCompatible(Class<?> ctorParamType, Class<?> factoryParamType) {
    if (ctorParamType.isAssignableFrom(factoryParamType)) return true;

    if (ctorParamType.isPrimitive()) {
      return factoryParamType == boxed(ctorParamType);
    }
    if (factoryParamType.isPrimitive()) {
      return ctorParamType == boxed(factoryParamType);
    }

    return false;
  }

  private static Class<?> boxed(Class<?> primitive) {
    if (primitive == boolean.class) return Boolean.class;
    if (primitive == byte.class) return Byte.class;
    if (primitive == char.class) return Character.class;
    if (primitive == short.class) return Short.class;
    if (primitive == int.class) return Integer.class;
    if (primitive == long.class) return Long.class;
    if (primitive == float.class) return Float.class;
    if (primitive == double.class) return Double.class;
    return primitive;
  }

  private static Object invokeFactory(MethodHandle mh, Parameter[] ctorParams,
                                      ParameterMapping[] mappings, Object[] factoryArgs,
                                      Class<?> targetClass) throws Throwable {
    Object[] args = new Object[ctorParams.length];
    for (int i = 0; i < mappings.length; i++) {
      args[i] = switch (mappings[i]) {
        case ParameterMapping.FromDI(Key key) -> getInternal(key);  // Uses fallback
        case ParameterMapping.FromFactory(int idx) -> factoryArgs != null ? factoryArgs[idx] : null;
      };
    }

    Object instance = mh.invokeWithArguments(args);
    injectMembers(instance);
    return instance;
  }

  private static Object handleObjectMethod(Object proxy, Method method, Object[] args) throws Throwable {
    return switch (method.getName()) {
      case "toString" -> proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == args[0];
      default -> throw new UnsupportedOperationException(method.toString());
    };
  }

  // =========================================================================
  // Constructor utilities
  // =========================================================================

  private static Constructor<?> findInjectConstructor(Class<?> clazz) {
    Constructor<?>[] ctors = clazz.getDeclaredConstructors();
    Constructor<?> inject = null;
    for (Constructor<?> c : ctors) {
      if (c.isAnnotationPresent(jakarta.inject.Inject.class)) {
        if (inject != null) {
          throw new IllegalStateException("Multiple @Inject constructors in " + clazz.getName());
        }
        inject = c;
      }
    }
    if (inject == null) {
      try {
        inject = clazz.getDeclaredConstructor();
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("No @Inject or default constructor in " + clazz.getName(), e);
      }
    }
    inject.setAccessible(true);
    return inject;
  }

  private static MethodHandle unreflectConstructor(Class<?> clazz, Constructor<?> ctor) {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(clazz, lookup);
      return privateLookup.unreflectConstructor(ctor);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Cannot access constructor of " + clazz.getName(), e);
    }
  }
}