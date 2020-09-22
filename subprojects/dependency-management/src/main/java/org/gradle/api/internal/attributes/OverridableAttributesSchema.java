/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.attributes;

import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.internal.component.model.AttributeMatcher;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class OverridableAttributesSchema implements AttributesSchemaInternal {
    private AttributesSchemaInternal delegate;

    public OverridableAttributesSchema(AttributesSchemaInternal delegate) {
        this.delegate = delegate;
    }

    public <T> T withOverride(AttributesSchemaInternal delegate, Supplier<T> action) {
        AttributesSchemaInternal original = delegate;
        this.delegate = delegate;
        try {
            return action.get();
        } finally {
            this.delegate = original;
        }
    }

    @Override
    public AttributeMatcher withProducer(AttributesSchemaInternal producerSchema) {
        return delegate.withProducer(producerSchema);
    }

    @Override
    public AttributeMatcher matcher() {
        return delegate.matcher();
    }

    @Override
    public CompatibilityRule<Object> compatibilityRules(Attribute<?> attribute) {
        return delegate.compatibilityRules(attribute);
    }

    @Override
    public DisambiguationRule<Object> disambiguationRules(Attribute<?> attribute) {
        return delegate.disambiguationRules(attribute);
    }

    @Override
    public List<AttributeDescriber> getConsumerDescribers() {
        return delegate.getConsumerDescribers();
    }

    @Override
    public void addConsumerDescriber(AttributeDescriber describer) {
        delegate.addConsumerDescriber(describer);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> getMatchingStrategy(Attribute<T> attribute) throws IllegalArgumentException {
        return delegate.getMatchingStrategy(attribute);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute) {
        return delegate.attribute(attribute);
    }

    @Override
    public <T> AttributeMatchingStrategy<T> attribute(Attribute<T> attribute, Action<? super AttributeMatchingStrategy<T>> configureAction) {
        return delegate.attribute(attribute, configureAction);
    }

    @Override
    public Set<Attribute<?>> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public boolean hasAttribute(Attribute<?> key) {
        return delegate.hasAttribute(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        OverridableAttributesSchema that = (OverridableAttributesSchema) o;
        return delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
