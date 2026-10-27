package org.goldenport.cncf.finding.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Public V1 annotation for handwritten Scala/Java declarations.
 * @since Oct. 27, 2026
 * @version Oct. 27, 2026
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
@Repeatable(FindingDispositions.class)
public @interface FindingDisposition {
    int abiVersion() default 1;
    String rule();
    String finding();
    String scope();
    String target();
    String kind();
    String reason();
    String provenance();
}
