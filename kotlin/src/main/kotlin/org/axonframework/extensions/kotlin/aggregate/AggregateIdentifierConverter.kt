/*
 * Copyright (c) 2010-2020. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.kotlin.aggregate

import java.util.*
import java.util.function.Function

/**
 * Defines a converter from a string to custom identifier type.
 * @param [ID] type of aggregate identifier.
 *
 * @author Simon Zambrovski
 * @since 0.2.0
 */
interface AggregateIdentifierConverter<ID> : Function<String, ID> {

    /**
     * Default string converter.
     */
    object DefaultString : AggregateIdentifierConverter<String> {
        override fun apply(it: String): String = it
        override fun toString(): String = this::class.qualifiedName!!
    }

    /**
     * Default UUID converter.
     */
    object DefaultUUID : AggregateIdentifierConverter<UUID> {
        override fun apply(it: String): UUID = UUID.fromString(it)
        override fun toString(): String = this::class.qualifiedName!!
    }
}