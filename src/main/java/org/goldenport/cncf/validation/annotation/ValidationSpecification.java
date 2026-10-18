package org.goldenport.cncf.validation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Runtime suite defaults. Empty identity/revision request discovery fallbacks.
 * @since Oct. 18, 2026
 * @version Oct. 18, 2026
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface ValidationSpecification {
    String identity() default "";
    String revision() default "";
    Class<?> declaration() default Void.class;
}
