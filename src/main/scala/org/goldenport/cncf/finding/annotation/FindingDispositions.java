package org.goldenport.cncf.finding.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Runtime container for repeated FindingDisposition declarations.
 * @since Oct. 27, 2026
 * @version Oct. 27, 2026
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface FindingDispositions {
    FindingDisposition[] value();
}
