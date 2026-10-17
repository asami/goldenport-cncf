package org.goldenport.cncf.validation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Metadata anchor naming one exact registered ScalaTest test name.
 * @since Oct. 18, 2026
 * @version Oct. 18, 2026
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ValidationScenario {
    String name();
    String identity() default "";
    String revision() default "";
    Class<?> declaration() default Void.class;
}
