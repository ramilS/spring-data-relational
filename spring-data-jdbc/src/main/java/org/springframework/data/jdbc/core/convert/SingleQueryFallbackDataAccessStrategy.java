/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.jdbc.core.convert;

import java.util.Collections;

import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.util.Assert;

/**
 * {@link DelegatingDataAccessStrategy} applying Single Query Loading if the underlying aggregate type allows Single
 * Query Loading.
 *
 * @author Mark Paluch
 * @since 3.2
 */
class SingleQueryFallbackDataAccessStrategy extends DelegatingDataAccessStrategy {

	private final SqlGeneratorSource sqlGeneratorSource;
	private final SingleQueryDataAccessStrategy singleSelectDelegate;
	private final JdbcConverter converter;

	public SingleQueryFallbackDataAccessStrategy(SqlGeneratorSource sqlGeneratorSource, JdbcConverter converter,
			NamedParameterJdbcOperations operations, DataAccessStrategy fallback) {

		super(fallback);

		Assert.notNull(sqlGeneratorSource, "SqlGeneratorSource must not be null");
		Assert.notNull(converter, "JdbcConverter must not be null");
		Assert.notNull(operations, "NamedParameterJdbcOperations must not be null");

		this.sqlGeneratorSource = sqlGeneratorSource;
		this.converter = converter;

		this.singleSelectDelegate = new SingleQueryDataAccessStrategy(sqlGeneratorSource.getDialect(), converter,
				operations);
	}

	@Override
	public <T> T findById(Object id, Class<T> domainType) {

		if (isSingleSelectQuerySupported(domainType)) {
			return singleSelectDelegate.findById(id, domainType);
		}

		return super.findById(id, domainType);
	}

	@Override
	public <T> Iterable<T> findAll(Class<T> domainType) {

		if (isSingleSelectQuerySupported(domainType)) {
			return singleSelectDelegate.findAll(domainType);
		}

		return super.findAll(domainType);
	}

	@Override
	public <T> Iterable<T> findAllById(Iterable<?> ids, Class<T> domainType) {

		if (!ids.iterator().hasNext()) {
			return Collections.emptyList();
		}

		if (isSingleSelectQuerySupported(domainType)) {
			return singleSelectDelegate.findAllById(ids, domainType);
		}

		return super.findAllById(ids, domainType);
	}

	private boolean isSingleSelectQuerySupported(Class<?> entityType) {

		return sqlGeneratorSource.getDialect().supportsSingleQueryLoading()//
				&& entityQualifiesForSingleSelectQuery(entityType);
	}

	private boolean entityQualifiesForSingleSelectQuery(Class<?> entityType) {

		boolean referenceFound = false;
		for (PersistentPropertyPath<RelationalPersistentProperty> path : converter.getMappingContext()
				.findPersistentPropertyPaths(entityType, __ -> true)) {
			RelationalPersistentProperty property = path.getLeafProperty();
			if (property.isEntity()) {

				// embedded entities are currently not supported
				if (property.isEmbedded()) {
					return false;
				}

				// only a single reference is currently supported
				if (referenceFound) {
					return false;
				}

				referenceFound = true;
			}

			// AggregateReferences aren't supported yet
			if (property.isAssociation()) {
				return false;
			}
		}
		return true;

	}
}
