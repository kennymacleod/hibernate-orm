/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.results.dynamic;

import java.util.Objects;
import java.util.function.BiFunction;
import jakarta.persistence.AttributeConverter;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.model.convert.internal.JpaAttributeConverterImpl;
import org.hibernate.metamodel.model.convert.spi.BasicValueConverter;
import org.hibernate.query.results.ResultsHelper;
import org.hibernate.query.results.ResultSetMappingSqlSelection;
import org.hibernate.resource.beans.spi.ManagedBean;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;
import org.hibernate.resource.beans.spi.ProvidedInstanceManagedBeanImpl;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.sql.results.graph.basic.BasicResult;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMetadata;
import org.hibernate.type.BasicType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.spi.JavaTypeRegistry;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * A ResultBuilder for explicitly converted scalar results
 *
 * @author Steve Ebersole
 */
public class DynamicResultBuilderBasicConverted<O,R> implements DynamicResultBuilderBasic {
	private final String columnAlias;
	private final BasicValueConverter<O,R> basicValueConverter;

	public DynamicResultBuilderBasicConverted(
			String columnAlias,
			Class<O> domainJavaType,
			Class<R> jdbcJavaType,
			AttributeConverter<O, R> converter,
			SessionFactoryImplementor sessionFactory) {
		final TypeConfiguration typeConfiguration = sessionFactory.getTypeConfiguration();
		final JavaTypeRegistry jtdRegistry = typeConfiguration.getJavaTypeRegistry();
		final JavaType<? extends AttributeConverter<O, R>> converterJtd = jtdRegistry.getDescriptor( converter.getClass() );
		final ManagedBean<? extends AttributeConverter<O,R>> bean = new ProvidedInstanceManagedBeanImpl<>( converter );

		this.columnAlias = columnAlias;
		this.basicValueConverter = new JpaAttributeConverterImpl<>(
				bean,
				converterJtd,
				jtdRegistry.getDescriptor( domainJavaType ),
				jtdRegistry.getDescriptor( jdbcJavaType )
		);
	}

	public DynamicResultBuilderBasicConverted(
			String columnAlias,
			Class<O> domainJavaType,
			Class<R> jdbcJavaType,
			Class<? extends AttributeConverter<O, R>> converterJavaType,
			SessionFactoryImplementor sessionFactory) {
		final ManagedBeanRegistry beans = sessionFactory.getServiceRegistry().getService( ManagedBeanRegistry.class );
		final TypeConfiguration typeConfiguration = sessionFactory.getTypeConfiguration();
		final JavaTypeRegistry jtdRegistry = typeConfiguration.getJavaTypeRegistry();
		final JavaType<? extends AttributeConverter<O, R>> converterJtd = jtdRegistry.getDescriptor( converterJavaType );
		final ManagedBean<? extends AttributeConverter<O, R>> bean = beans.getBean( converterJavaType );

		this.columnAlias = columnAlias;
		this.basicValueConverter = new JpaAttributeConverterImpl<>(
				bean,
				converterJtd,
				jtdRegistry.getDescriptor( domainJavaType ),
				jtdRegistry.getDescriptor( jdbcJavaType )
		);
	}

	@Override
	public Class<?> getJavaType() {
		return basicValueConverter.getDomainJavaType().getJavaTypeClass();
	}

	@Override
	public DynamicResultBuilderBasicConverted cacheKeyInstance() {
		return this;
	}

	@Override
	public BasicResult<?> buildResult(
			JdbcValuesMetadata jdbcResultsMetadata,
			int resultPosition,
			BiFunction<String, String, DynamicFetchBuilderLegacy> legacyFetchResolver,
			DomainResultCreationState domainResultCreationState) {
		final TypeConfiguration typeConfiguration = domainResultCreationState.getSqlAstCreationState()
				.getCreationContext()
				.getSessionFactory()
				.getTypeConfiguration();

		final SqlExpressionResolver sqlExpressionResolver = domainResultCreationState.getSqlAstCreationState().getSqlExpressionResolver();
		final SqlSelection sqlSelection = sqlExpressionResolver.resolveSqlSelection(
				sqlExpressionResolver.resolveSqlExpression(
						columnAlias,
						state -> {
							final int currentJdbcPosition = resultPosition + 1;

							final int jdbcPosition;
							if ( columnAlias != null ) {
								jdbcPosition = jdbcResultsMetadata.resolveColumnPosition( columnAlias );
							}
							else {
								jdbcPosition = currentJdbcPosition;
							}
							final BasicType<?> basicType = jdbcResultsMetadata.resolveType(
									jdbcPosition,
									basicValueConverter.getRelationalJavaType(),
									domainResultCreationState.getSqlAstCreationState()
											.getCreationContext()
											.getSessionFactory()
							);

							final int valuesArrayPosition = ResultsHelper.jdbcPositionToValuesArrayPosition( jdbcPosition );
							return new ResultSetMappingSqlSelection( valuesArrayPosition, (BasicValuedMapping) basicType );
						}
				),
				basicValueConverter.getRelationalJavaType(),
				null,
				typeConfiguration
		);

		return new BasicResult<>(
				sqlSelection.getValuesArrayPosition(),
				columnAlias,
				basicValueConverter.getDomainJavaType(),
				basicValueConverter
		);
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}

		DynamicResultBuilderBasicConverted<?, ?> that = (DynamicResultBuilderBasicConverted<?, ?>) o;

		if ( !Objects.equals( columnAlias, that.columnAlias ) ) {
			return false;
		}
		return basicValueConverter.equals( that.basicValueConverter );
	}

	@Override
	public int hashCode() {
		int result = columnAlias != null ? columnAlias.hashCode() : 0;
		result = 31 * result + basicValueConverter.hashCode();
		return result;
	}
}
