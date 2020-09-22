package org.axonframework.extension.kotlin.spring

import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AnnotationUtils

/**
 * Annotation to enable aggregate scan.
 * @param basePackage specifies the package to scan for aggregates, if not specified, defaults to base package of the annotated class.
 *
 * @author Simon Zambrovski
 * @since 0.2.0
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
@Import(AggregatesWithImmutableIdentifierConfiguration::class)
annotation class EnableAggregateWithImmutableIdentifierScan(
    val basePackage: String = NULL_VALUE
) {
    companion object {

        /**
         * Null value to allow package scan on bean.
         */
        const val NULL_VALUE = ""

        /**
         * Reads base package from annotation or, if not provided from the annotated class.
         * @param bean annotated bean
         * @return base package or <code>null</code> if not defined and can't be read.
         */
        fun getBasePackage(bean: Any): String? {
            val annotation = AnnotationUtils.findAnnotation(bean::class.java, EnableAggregateWithImmutableIdentifierScan::class.java) ?: return null
            return if (annotation.basePackage == NULL_VALUE) {
                bean::class.java.`package`.name
            } else {
                annotation.basePackage
            }
        }
    }
}