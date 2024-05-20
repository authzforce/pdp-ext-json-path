/*
 * Copyright 2012-2024 THALES.
 *
 * This file is part of AuthzForce CE.
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
package org.ow2.authzforce.pdp.ext.jsonpath.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.ow2.authzforce.core.pdp.api.EvaluationContext;
import org.ow2.authzforce.core.pdp.api.IndeterminateEvaluationException;
import org.ow2.authzforce.core.pdp.api.expression.ConstantExpression;
import org.ow2.authzforce.core.pdp.api.expression.ConstantPrimitiveAttributeValueExpression;
import org.ow2.authzforce.core.pdp.api.expression.Expression;
import org.ow2.authzforce.core.pdp.api.expression.FunctionExpression;
import org.ow2.authzforce.core.pdp.api.func.FirstOrderFunction;
import org.ow2.authzforce.core.pdp.api.func.Function;
import org.ow2.authzforce.core.pdp.api.func.FunctionCall;
import org.ow2.authzforce.core.pdp.api.value.AttributeDatatype;
import org.ow2.authzforce.core.pdp.api.value.AttributeValue;
import org.ow2.authzforce.core.pdp.api.value.Bag;
import org.ow2.authzforce.core.pdp.api.value.Datatype;
import org.ow2.authzforce.core.pdp.api.value.PrimitiveDatatype;
import org.ow2.authzforce.core.pdp.api.value.PrimitiveValue;
import org.ow2.authzforce.core.pdp.api.value.StandardDatatypes;
import org.ow2.authzforce.core.pdp.api.value.Value;
import org.ow2.authzforce.pdp.ext.jsonpath.JsonValue;
import org.ow2.authzforce.xacml.identifiers.XacmlStatusCode;

/**
 * An abstract class to easily test a function evaluation, according to a given function name, a list of arguments, and expected result. In order to perform a function test, simply extend this class
 * and give the test values on construction.
 * 
 */
public abstract class StandardFunctionTest
{
	/**
	 * Bag value expression
	 *
	 * @param <BV>
	 *            bag type
	 */
	private static final class BagValueExpression<BV extends Bag<?>> extends ConstantExpression<BV>
	{

		protected BagValueExpression(final Datatype<BV> datatype, final BV v) throws IllegalArgumentException
		{
			super(datatype, v);
		}

	}
	
	private static final Map<Class<? extends AttributeValue>, Datatype<? extends AttributeValue>> FUNC_ARG_CLASS_TO_ATT_DATATYPE_MAP = new HashMap<>();
	static {
		for(final PrimitiveDatatype<? extends AttributeValue> datatype: new PrimitiveDatatype[] { StandardDatatypes.STRING, StandardDatatypes.BOOLEAN, StandardDatatypes.DOUBLE, StandardDatatypes.INTEGER, JsonValue.DATATYPE }) {
			FUNC_ARG_CLASS_TO_ATT_DATATYPE_MAP.put(datatype.getInstanceClass(), datatype);
		}
	}
	

	// private static final Logger LOGGER = LoggerFactory.getLogger(GeneralFunctionTest.class);

	private FunctionCall<?> funcCall;
	private final Value expectedResult;
	private final String toString;
	private final boolean areBagsComparedAsSets;
	private boolean isTestOkBeforeFuncCall = false;

	/**
	 * Creates instance
	 * 
	 * @param function function to be tested. The function must be supported by the StandardFunctionRegistry.
	 * @param inputs
	 *            The list of the function arguments as expressions, in order.
	 * @param expectedResult
	 *            The expected function evaluation result, according to the given inputs; null if evaluation expected to throw an error (IndeterminateEvaluationException)
	 * @param compareBagsAsSets
	 *            true iff result bags should be compared as sets for equality check
	 */
	private StandardFunctionTest(final Function<?> function, final List<Expression<?>> inputs, final boolean compareBagsAsSets, final Value expectedResult)
	{
		/*
		TODO: support higher-order functions
		 */
		// Determine whether this is a higher-order function, i.e. first parameter is a sub-function
		/*
		final Datatype<? extends AttributeValue> subFuncReturnType;
		if (inputs.isEmpty())
		{
			subFuncReturnType = null;
		} else
		{
			final Expression<?> xpr0 = inputs.get(0);
			if (xpr0 instanceof FunctionExpression)
			{
				subFuncReturnType = ((FunctionExpression) xpr0).getValue().get().getReturnType();
			} else
			{
				subFuncReturnType = null;
			}
		}
		*/

		try
		{
			funcCall = function.newCall(inputs);
		} catch (final IllegalArgumentException e)
		{
			/*
			 * Some syntax errors might be caught at initialization time, which is expected if expectedResult == null
			 */
			if (expectedResult != null)
			{
				/*
				 * IllegalArgumentException should not have been thrown, since we expect a result of the function call
				 */
				throw new RuntimeException("expectedResult != null but invalid args in test definition prevented the function call", e);
			}

			funcCall = null;
			// expectedResult == null
			isTestOkBeforeFuncCall = true;
		}

		/*
		 * If test not yet OK, we need to run the function call (funcCall.evaluate(...)), so funcCall must be defined
		 */
		if (!isTestOkBeforeFuncCall && funcCall == null)
		{
			throw new RuntimeException("Failed to initialize function call for unknown reason");
		}

		this.expectedResult = expectedResult;
		this.toString = function + "( " + inputs + " )";

		this.areBagsComparedAsSets = compareBagsAsSets;
	}

	// @Before
	// public void skipIfFunctionNotSupported()
	// {
	// // assume test OK if function not supported -> skip it
	// org.junit.Assume.assumeTrue(function == null);
	// }

	private static <V extends AttributeValue> Expression<?> createValueExpression(final Datatype<V> datatype, final AttributeValue rawValue) {
		// static expression only if not xpathExpression
		return new ConstantPrimitiveAttributeValueExpression<>(datatype, datatype.cast(rawValue));
	}

	private static <V extends Bag<?>> Expression<?> createValueExpression(final Datatype<V> datatype, final Bag<?> rawValue) {
		return new StandardFunctionTest.BagValueExpression<>(datatype, datatype.cast(rawValue));
	}

	private static final class IndeterminateExpression<V extends Value> implements Expression<V>
	{
		private final Datatype<V> returnType;

		private IndeterminateExpression(final Datatype<V> returnType)
		{
			this.returnType = returnType;
		}

		@Override
		public Datatype<V> getReturnType() {
			return returnType;
		}

		@Override
		public V evaluate(final EvaluationContext individualDecisionContext, final Optional<EvaluationContext> mdpContext) throws IndeterminateEvaluationException {
			throw new IndeterminateEvaluationException("Missing attribute", XacmlStatusCode.MISSING_ATTRIBUTE.value());
		}

		@Override
		public Optional<V> getValue() {
			throw new UnsupportedOperationException("No constant defined for Indeterminate expression");
		}

	}

	// private static <V extends Value> IndeterminateExpression<V> newIndeterminateExpression

	private static List<Expression<?>> toExpressions(final FirstOrderFunction<?> subFunction, final List<Value> values) {
		final List<Expression<?>> inputExpressions = new ArrayList<>();
		if (subFunction != null)
		{
			// sub-function of higher-order function
			inputExpressions.add(new FunctionExpression(subFunction));
		}

		for (final Value val : values)
		{
			final Expression<?> valExpr;
			if (val instanceof NullValue nullVal)
			{
				/*
				 * Undefined arg -> wrap in a special expression that always return Indeterminate (useful for testing functions that do not need all arguments to return a result, such as logical
				 * or/and/n-o
				 */
				valExpr = nullVal.isBag() ? new IndeterminateExpression<>(nullVal.getDatatype().getBagDatatype()) : new IndeterminateExpression<>(nullVal.getDatatype());
			} else if (val instanceof AttributeValue primVal)
			{
				valExpr = createValueExpression(FUNC_ARG_CLASS_TO_ATT_DATATYPE_MAP.get(primVal.getClass()), primVal);
			} else if (val instanceof Bag<?> bagVal)
			{
				final Datatype<?> bagEltDatatype = bagVal.getElementDatatype();
				if(!(bagEltDatatype instanceof AttributeDatatype)) {
					throw new RuntimeException("Invalid input bag datatype: actual: " + bagEltDatatype + "; expected an instance of " + AttributeDatatype.class);
				}
				
				valExpr = createValueExpression(((AttributeDatatype<?>) bagEltDatatype).getBagDatatype(), bagVal);
			} else
			{
				throw new UnsupportedOperationException("Unsupported type of Value: " + val.getClass());
			}

			inputExpressions.add(valExpr);
		}

		return inputExpressions;
	}

	/**
	 * Creates instance
	 * 
	 * @param function
	 *            The function to be tested. The function must be supported by the StandardFunctionRegistry.
	 * @param subFunction
	 *            (optional) sub-function specified iff {@code function} is a higher-order function; else null
	 * @param inputs
	 *            The list of the function arguments as constant values, in order. Specify a null argument to indicate it is undefined. It will be considered as Indeterminate (wrapped in a Expression
	 *            that always evaluate to Indeterminate result). This is useful to test specific function behavior when one (or more) of the arguments is indeterminate; e.g. logical or/and/n-of
	 *            functions are able to return False/True even if some of the arguments are Indeterminate.
	 * @param expectedResult
	 *            The expected function evaluation result, according to the given inputs; null if evaluation expected to throw an error (IndeterminateEvaluationException)
	 * @param compareBagsAsSets
	 *            true iff result bags should be compared as sets for equality check
	 */
	public StandardFunctionTest(final Function<?> function, final FirstOrderFunction<?> subFunction, final List<Value> inputs, final boolean compareBagsAsSets, final Value expectedResult)
	{
		this(function, toExpressions(subFunction, inputs), compareBagsAsSets, expectedResult);
	}

	/**
	 * Creates instance
	 * 
	 * @param function
	 *            The function to be tested. The function must be supported by the StandardFunctionRegistry.
	 * @param subFunction
	 *            (optional) sub-function specified iff {@code function} corresponds to a higher-order function; else null
	 * @param inputs
	 *            The list of the function arguments, as constant values, in order.
	 * @param expectedResult
	 *            The expected function evaluation result, according to the given inputs; null if evaluation expected to throw an error (IndeterminateEvaluationException)
	 */
	public StandardFunctionTest(final Function<?> function, final FirstOrderFunction<?> subFunction, final List<Value> inputs, final Value expectedResult)
	{
		this(function, subFunction, inputs, false, expectedResult);
	}

	private static Set<PrimitiveValue> bagToSet(final Bag<?> bag) {
		final Set<PrimitiveValue> set = new HashSet<>();
		for (final PrimitiveValue val : bag)
		{
			set.add(val);
		}

		return set;
	}

	@Test
	public void testEvaluate() throws IndeterminateEvaluationException {
		if (isTestOkBeforeFuncCall)
		{
			/*
			 * Test already OK (syntax error was expected and occured when creating the function call already), no need to carry on the function call
			 */
			return;
		}

		/*
		 * Use null context as all inputs given as values in function tests, therefore already provided as inputs to function call
		 */
		try
		{
			/*
			 * funcCall != null (see constructor)
			 */
			final Value actualResult = funcCall.evaluate(null, Optional.empty());
			if (expectedResult instanceof Bag && actualResult instanceof Bag && areBagsComparedAsSets)
			{
				final Set<?> expectedSet = bagToSet((Bag<?>) expectedResult);
				final Set<?> actualSet = bagToSet((Bag<?>) actualResult);
				Assert.assertEquals(toString, expectedSet, actualSet);
			} else if (expectedResult != null)
			{
				Assert.assertEquals(toString, expectedResult, actualResult);
			}
		} catch (final IndeterminateEvaluationException e)
		{
			if (expectedResult != null)
			{
				// unexpected error
				throw e;
			}
		}
	}
}
