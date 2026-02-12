package org.patchbukkit.testplugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ConformanceTest {
    String name();
    TestCategory category();
    TestExpectation expectation() default TestExpectation.SHOULD_WORK;
}
