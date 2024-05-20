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
package org.ow2.authzforce.pdp.ext.jsonpath;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import com.google.common.base.Preconditions;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.ow2.authzforce.core.pdp.api.IndeterminateEvaluationException;
import org.ow2.authzforce.core.pdp.api.expression.Expression;
import org.ow2.authzforce.core.pdp.api.expression.Expressions;
import org.ow2.authzforce.core.pdp.api.func.BaseFirstOrderFunctionCall;
import org.ow2.authzforce.core.pdp.api.func.FirstOrderFunctionCall;
import org.ow2.authzforce.core.pdp.api.func.Function;
import org.ow2.authzforce.core.pdp.api.func.MultiParameterTypedFirstOrderFunction;
import org.ow2.authzforce.core.pdp.api.value.ArbitrarilyBigInteger;
import org.ow2.authzforce.core.pdp.api.value.AttributeDatatype;
import org.ow2.authzforce.core.pdp.api.value.AttributeValue;
import org.ow2.authzforce.core.pdp.api.value.Bag;
import org.ow2.authzforce.core.pdp.api.value.BagDatatype;
import org.ow2.authzforce.core.pdp.api.value.Bags;
import org.ow2.authzforce.core.pdp.api.value.BooleanValue;
import org.ow2.authzforce.core.pdp.api.value.Datatype;
import org.ow2.authzforce.core.pdp.api.value.DoubleValue;
import org.ow2.authzforce.core.pdp.api.value.IntegerValue;
import org.ow2.authzforce.core.pdp.api.value.StandardDatatypes;
import org.ow2.authzforce.core.pdp.api.value.StringParseableValue;
import org.ow2.authzforce.core.pdp.api.value.StringValue;
import org.ow2.authzforce.xacml.identifiers.XacmlStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XACML JSONPath functions applied to JSON objects
 */
public final class JsonPathFunctions
{
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonPathFunctions.class);

	private JsonPathFunctions()
	{
		// nothing, hide constructor
	}

	private static <AV extends AttributeValue> Bag<AV> newBagFromJsonPathEvalResult(final Object jsonPathEvalResult, final String jsonPathForLogging, AttributeDatatype<AV> elementDatatype, java.util.function.Function<Object, AV> converter)
	{
		if (jsonPathEvalResult instanceof List<?> results)
		{
			final List<AV> returnedList = new ArrayList<>();
			for (final Object result : results)
			{
				// returns null if result is not convertible
				final AV bagElement = converter.apply(result);
				if (bagElement == null)
				{
					if(LOGGER.isWarnEnabled())
					{
						// Invalid value
						LOGGER.warn("Evaluation of JSONPath '{}' returned a list with an invalid value type: expected: {}; actual: {}", jsonPathForLogging.replaceAll("[\r\n]", ""), elementDatatype.getInstanceClass().toString().replaceAll("[\r\n]", ""), result.getClass().toString().replaceAll("[\r\n]", ""));
					}
				} else {
					returnedList.add(bagElement);
				}
			}

			return Bags.newBag(elementDatatype, returnedList);
		}
		// jsonPathEvalResult is a single value
		// returns null if result is not convertible
		final AV bagElement = converter.apply(jsonPathEvalResult);
		if (bagElement == null)
		{
			// Invalid value
			return Bags.empty(elementDatatype, new IndeterminateEvaluationException("Evaluation of JSONPath '" + jsonPathForLogging + "' returned an invalid value type: expected: " + elementDatatype.getInstanceClass() + "; actual: " + jsonPathEvalResult.getClass(), XacmlStatusCode.PROCESSING_ERROR.value()));
		}

		// Valid value
		return Bags.singleton(elementDatatype, bagElement);
	}

	/**
	 * Non-standard XACML function that evaluates a JSON path against a JSON object/array: {@code json-path(JSON, JsonPath)} -> bag.
	 */
	private static abstract class JsonPathFunction<RETURN_BAG_ELEMENT_TYPE extends StringParseableValue<?>> extends MultiParameterTypedFirstOrderFunction<Bag<RETURN_BAG_ELEMENT_TYPE>>
	{

		private final BagDatatype<RETURN_BAG_ELEMENT_TYPE> returnType;

		/**
		 * Default constructor for the json path function that may return a bag of strings, integers, booleans, etc. and takes parameters: JSON object/array, JSON path (string)
		 */
		public JsonPathFunction(final String returnBagElementTypeShortName, final BagDatatype<RETURN_BAG_ELEMENT_TYPE> returnType)
		{
			super(Function.AUTHZFORCE_EXTENSION_PREFIX + returnBagElementTypeShortName + "-from-json-path", returnType, false, Arrays.asList(JsonValue.DATATYPE, StandardDatatypes.STRING));
			this.returnType = returnType;
		}

		protected abstract Bag<RETURN_BAG_ELEMENT_TYPE> newBagFromJsonPathEvalResult(final Object jsonPathEvalResult, final String jsonPathForErrorLogging);

		@Override
		public FirstOrderFunctionCall<Bag<RETURN_BAG_ELEMENT_TYPE>> newCall(final List<Expression<?>> argExpressions, final Datatype<?>... remainingArgTypes) throws IllegalArgumentException
		{
			assert argExpressions != null && argExpressions.size() == 2;
			final Expression<?> argExp1 = argExpressions.get(1);
			final BiFunction<JsonValue, Deque<AttributeValue>, Bag<RETURN_BAG_ELEMENT_TYPE>> jsonPathEvalFunction;
			BiFunction<JsonValue, Deque<AttributeValue>, Bag<RETURN_BAG_ELEMENT_TYPE>> _jsonPathEvalFunction;

			/*
			 * Check whether first arg - JSONPath - is constant/literal, in which case we can pre-compile it for optimisation purposes.
			 */
			try
			{
				final StringValue jsonPathAttVal = Expressions.eval(argExp1, null, Optional.empty(), StandardDatatypes.STRING);
				final JsonPath compiledJsonPath = JsonPath.compile(jsonPathAttVal.getUnderlyingValue());
				_jsonPathEvalFunction = (jsonArg, nextArgs) -> {
					final Object result = compiledJsonPath.read((Object) jsonArg.getJsonPathReadContext().json(), JsonValue.JSON_PROCESSOR_CONFIGURATION);
					return newBagFromJsonPathEvalResult(result, compiledJsonPath.getPath());
				};
			}
			catch (final IndeterminateEvaluationException e)
			{
				// JSONPath is not constant but dependent on request context attributes, therefore cannot be compiled in advance.
				// The JSONPath is the second argument
				_jsonPathEvalFunction = (jsonArg, nextArgs) -> {
					final AttributeValue arg1 = nextArgs.poll();
					Preconditions.checkArgument(arg1 != null, "Missing arg #1 (JSON path) to json-path function");
					final String jsonPath = StandardDatatypes.STRING.cast(arg1).getUnderlyingValue();
					final Object result = jsonArg.getJsonPathReadContext().read(jsonPath);
					return newBagFromJsonPathEvalResult(result, jsonPath);
				};
			}

			jsonPathEvalFunction = _jsonPathEvalFunction;
			return new BaseFirstOrderFunctionCall.EagerMultiPrimitiveTypeEval<>(functionSignature, argExpressions, remainingArgTypes)
			{

				@Override
				protected Bag<RETURN_BAG_ELEMENT_TYPE> evaluate(Deque<AttributeValue> args)
				{
					// first arg is the JSON object/array
					final AttributeValue arg0 = args.poll();
					Preconditions.checkArgument(arg0 != null, "Missing arg #0 (JSON array/object) to json-path function");
					final JsonValue jsonAttVal = (JsonValue) arg0;
					try
					{
						return jsonPathEvalFunction.apply(jsonAttVal, args);
					}
					catch (PathNotFoundException e)
					{
						return Bags.empty(returnType.getElementType(), new IndeterminateEvaluationException("Error evaluating JSONPath", XacmlStatusCode.PROCESSING_ERROR.value(), e));
					}
				}

			};
		}
	}

	/**
	 * Implements the boolean-from-json-path function that evaluates a JSON path against a JSON object/array and returns a list of boolean values as a result.
	 * If the JSONPath evaluation does not return any boolean, an empty bag is returned.
	 */
	public static final class BooleansFromJsonPathFunction extends JsonPathFunction<BooleanValue>
	{
		private static final java.util.function.Function<Object, BooleanValue> CONVERTER = o -> o instanceof Boolean b ? BooleanValue.valueOf(b) : null;

		/**
		 * Constructor
		 */
		public BooleansFromJsonPathFunction()
		{
			super("boolean", StandardDatatypes.BOOLEAN.getBagDatatype());
		}

		@Override
		protected Bag<BooleanValue> newBagFromJsonPathEvalResult(final Object jsonPathEvalResult, final String jsonPathForLogging)
		{
			return JsonPathFunctions.newBagFromJsonPathEvalResult(jsonPathEvalResult, jsonPathForLogging, StandardDatatypes.BOOLEAN, CONVERTER);
		}
	}

	/**
	 * Implements the double-from-json-path function that evaluates a JSON path against a JSON object/array and returns a list of double values as a result.
	 * If the JSONPath evaluation does not return any double, an empty bag is returned.
	 */
	public static final class DoublesFromJsonPathFunction extends JsonPathFunctions.JsonPathFunction<DoubleValue>
	{
		private static final java.util.function.Function<Object, DoubleValue> CONVERTER = o -> o instanceof Double d ? new DoubleValue(d) : null;

		/**
		 * Constructor
		 */
		public DoublesFromJsonPathFunction()
		{
			super("double", StandardDatatypes.DOUBLE.getBagDatatype());
		}

		@Override
		protected Bag<DoubleValue> newBagFromJsonPathEvalResult(final Object jsonPathEvalResult, final String jsonPathForLogging)
		{
			// TODO: support BigDecimal as input too?
			return JsonPathFunctions.newBagFromJsonPathEvalResult(jsonPathEvalResult, jsonPathForLogging, StandardDatatypes.DOUBLE, CONVERTER);
		}
	}


	/**
	 * Implements the integer-from-json-path function that evaluates a JSON path against a JSON object/array and returns a list of integer values as a result.
	 * If the JSONPath evaluation does not return any integer, an empty bag is returned.
	 */
	public static final class IntegersFromJsonPathFunction extends JsonPathFunction<IntegerValue>
	{

		private static final java.util.function.Function<Object, IntegerValue> CONVERTER = o -> {
			if (o instanceof Short s)
			{
				return IntegerValue.valueOf(s.intValue());
			}

			if (o instanceof Integer i)
			{
				return IntegerValue.valueOf(i);
			}

			if (o instanceof Long l)
			{
				return IntegerValue.valueOf(l);
			}

			if(o instanceof BigInteger b)
			{
				return new IntegerValue(new ArbitrarilyBigInteger(b));
			}

			return null;
		};

		/**
		 * Constructor
		 */
		public IntegersFromJsonPathFunction()
		{
			super("integer", StandardDatatypes.INTEGER.getBagDatatype());
		}

		@Override
		protected Bag<IntegerValue> newBagFromJsonPathEvalResult(final Object jsonPathEvalResult, final String jsonPathForLogging)
		{
			return JsonPathFunctions.newBagFromJsonPathEvalResult(jsonPathEvalResult, jsonPathForLogging, StandardDatatypes.INTEGER, CONVERTER);
		}
	}

	/**
	 * Implements the string-from-json-path function that evaluates a JSON path against a JSON object/array and returns a list of string values as a result.
	 * If the JSONPath evaluation does not return any string, an empty bag is returned.
	 */
	public static final class StringsFromJsonPathFunction extends JsonPathFunction<StringValue>
	{
		private static final java.util.function.Function<Object, StringValue> CONVERTER = o -> o instanceof String s ? new StringValue(s) : null;

		/**
		 * Constructor
		 */
		public StringsFromJsonPathFunction()
		{
			super("string", StandardDatatypes.STRING.getBagDatatype());
		}

		@Override
		protected Bag<StringValue> newBagFromJsonPathEvalResult(final Object jsonPathEvalResult, final String jsonPathForLogging)
		{
			return JsonPathFunctions.newBagFromJsonPathEvalResult(jsonPathEvalResult, jsonPathForLogging, StandardDatatypes.STRING, CONVERTER);
		}
	}

}
