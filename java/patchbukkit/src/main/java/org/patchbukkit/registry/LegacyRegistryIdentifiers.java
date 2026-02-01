package org.patchbukkit.registry;

import com.google.common.collect.ImmutableMap;
import io.papermc.paper.registry.RegistryKey;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;


@Deprecated
public final class LegacyRegistryIdentifiers {

    public static final Map<Class<?>, RegistryKey<?>> CLASS_TO_KEY_MAP;

    static {
        final ImmutableMap.Builder<Class<?>, RegistryKey<?>> builder = ImmutableMap.builder();
        try {
            for (final Field field : RegistryKey.class.getFields()) {
                if (field.getType() == RegistryKey.class) {
                    // get the legacy type from the RegistryKey generic parameter on the field
                    final Class<?> legacyType = LegacyRegistryIdentifiers.erase(((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]);
                    builder.put(legacyType, (RegistryKey<?>) field.get(null));
                }
            }
        } catch (final ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        CLASS_TO_KEY_MAP = builder.build();
    }

    private static Class<?> erase(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        } else if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        } else if (type instanceof GenericArrayType arrayType) {
            return Array.newInstance(erase(arrayType.getGenericComponentType()), 0).getClass();
        } else if (type instanceof TypeVariable<?> typeVar) {
            return erase(typeVar.getBounds()[0]);
        } else if (type instanceof WildcardType wildcard) {
            return erase(wildcard.getUpperBounds()[0]);
        }
        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    private LegacyRegistryIdentifiers() {
    }
}
