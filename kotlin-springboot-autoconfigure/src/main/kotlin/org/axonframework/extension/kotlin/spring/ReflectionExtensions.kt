/*
 * Copyright (c) 2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.axonframework.extension.kotlin.spring

import org.axonframework.extensions.kotlin.aggregate.AggregateIdentifierConverter
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure

/**
 * Scans classpath for annotated aggregate classes.
 * @param scanPackage package to scan.
 * @param annotationClazz annotation class that is used on classes.
 * @return list of annotated classes.
 */
internal fun KClass<out Annotation>.findAnnotatedAggregateClasses(scanPackage: String): List<KClass<Any>> {
    val provider = ClassPathScanningCandidateComponentProvider(false)
    provider.addIncludeFilter(AnnotationTypeFilter(this.java))
    return provider.findCandidateComponents(scanPackage).map {
        @Suppress("UNCHECKED_CAST")
        Class.forName(it.beanClassName).kotlin as KClass<Any>
    }
}

/**
 * Extracts the class of aggregate identifier from an aggregate.
 * @param aggregateClazz aggregate class.
 * @return class of aggregate identifier.
 * @throws IllegalArgumentException if the required constructor is not found.
 */
internal fun KClass<*>.extractAggregateIdentifierClass(): KClass<Any> {

    /**
     * Holder for constructor and its parameters.
     * @param constructor constructor to holf info for.
     */
    data class ConstructorParameterInfo(val constructor: KFunction<Any>) {
        private val valueProperties by lazy { constructor.parameters.filter { it.kind == KParameter.Kind.VALUE } } // collect only "val" properties

        /**
         * Check if the provided constructor has only one value parameter.
         */
        fun isConstructorWithOneValue() = valueProperties.size == 1

        /**
         * Retrieves the class of value parameter.
         * @return class of value.
         */
        fun getParameterClass(): KClass<*> = valueProperties[0].type.jvmErasure
    }

    val constructors = this.constructors.map { ConstructorParameterInfo(it) }.filter { it.isConstructorWithOneValue() } // exactly one parameter in primary constructor
    require(constructors.size == 1) { "Expected exactly one constructor with aggregate identifier parameter, but found ${constructors.size}." }
    @Suppress("UNCHECKED_CAST")
    return constructors[0].getParameterClass() as KClass<Any>
}


/**
 * Extension function to find a matching converter for provided identifier class.
 * @param idClazz class of identifier to look for.
 * @return a matching converter.
 * @throws IllegalArgumentException if converter can not be identified (none or more than one are defined).
 */
internal fun List<AggregateIdentifierConverter<*>>.findIdentifierConverter(idClazz: KClass<out Any>): AggregateIdentifierConverter<Any> {
    val converters = this.filter {
        idClazz == it.getConverterIdentifierClass()
    }.map {
        @Suppress("UNCHECKED_CAST")
        it as AggregateIdentifierConverter<Any>
    }
    require(converters.isNotEmpty()) {
        "Could not find an AggregateIdentifierConverter for ${idClazz.qualifiedName}. Consider to register a bean implementing AggregateIdentifierConverter<${idClazz.qualifiedName}>"
    }
    require(converters.size == 1) {
        "Found more than one AggregateIdentifierConverter for ${idClazz.qualifiedName}. This is currently not supported."
    }
    return converters.first()
}

/**
 * Returns the concrete class of ID.
 * @return class of aggregate identifier or <code>null</code> if it can't be resolved.
 */
internal fun AggregateIdentifierConverter<*>.getConverterIdentifierClass() = this::class.supertypes.first { superTypes -> superTypes.classifier == AggregateIdentifierConverter::class }.arguments[0].type?.jvmErasure
