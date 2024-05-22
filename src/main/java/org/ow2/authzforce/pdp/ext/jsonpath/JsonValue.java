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

import java.util.Objects;

import com.google.common.base.Preconditions;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.spi.json.JsonProvider;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import org.ow2.authzforce.core.pdp.api.func.Function;
import org.ow2.authzforce.core.pdp.api.value.AttributeDatatype;
import org.ow2.authzforce.core.pdp.api.value.Datatype;
import org.ow2.authzforce.core.pdp.api.value.StringContentOnlyValueFactory;
import org.ow2.authzforce.core.pdp.api.value.StringParseableValue;

/**
 * XACML datatype for JSON object/array values (cf. RFC 8259), the Java representation is optimized for JSONPath processing {@link JsonPathFunctions}
 * <p>
 *         N.B.: this datatype supports only JSON objects and arrays, since simpler values (number, string, boolean, null) can be represented by XACML standard datatypes already.
 *
 * @version $Id: $
 */
public final class JsonValue extends StringParseableValue<String>
{
	/**
	 * Create a XACML Datatype for JSON values
	 */
	public static final AttributeDatatype<JsonValue> DATATYPE = new AttributeDatatype<>(JsonValue.class, Datatype.AUTHZFORCE_EXTENSION_PREFIX + "json",
				Function.AUTHZFORCE_EXTENSION_PREFIX + "json-", ItemType.STRING);

	/**
	 * JsonPath processing configuration
	 */
	public static final Configuration JSON_PROCESSOR_CONFIGURATION = Configuration.defaultConfiguration();

	// jsonPathReadCtx.json() should return Map<String, ?> for JSON object, List<?> for JSON array
	private final ReadContext jsonPathReadCtx;

	private transient volatile XdmItem xdmItem = null;

	/**
	 * Returns a new <code>JsonValue</code>.
	 *
	 * @param val
	 *            a string representing the JSON object/array
	 * @throws java.lang.IllegalArgumentException
	 *             if format of {@code val} does not comply with the JSON specification (RFC 8259)
	 */
	public JsonValue(final String val) throws IllegalArgumentException
	{
		super(val);
		try
		{
			jsonPathReadCtx = JsonPath.using(JSON_PROCESSOR_CONFIGURATION).parse(val);
		} catch (InvalidJsonException e) {
			throw new IllegalArgumentException("Invalid JSON", e);
		}

		final JsonProvider jsonProvider = JSON_PROCESSOR_CONFIGURATION.jsonProvider();
		final Object json = jsonPathReadCtx.json();
		Preconditions.checkArgument(jsonProvider.isArray(json) || jsonProvider.isMap(json), "Invalid input for JsonValue datatype: expected: JSON object (Map) or array (List); actual: " + json.getClass());
	}

	@SuppressFBWarnings(value="EI_EXPOSE_REP", justification="According to Saxon documentation, an XdmValue is immutable.")
	@Override
	public XdmItem getXdmItem()
	{
		if(xdmItem == null) {
			xdmItem = new XdmAtomicValue(value);
		}
		return xdmItem;
	}

	@Override
	public String printXML()
	{
		return this.value;
	}

	private transient volatile int hashCode = 0; // Effective Java - Item 9

	/** {@inheritDoc} */
	@Override
	public int hashCode()
	{
		if (hashCode == 0)
		{
			// hash regardless of letter case
			hashCode = Objects.hash(jsonPathReadCtx);
		}

		return hashCode;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 *
	 * We override the equals because for hostname, we can use equalsIgnoreCase() instead of equals() to compare, and PortRange.equals() for the portRange attribute (more optimal than String equals)
	 */
	/** {@inheritDoc} */
	@Override
	public boolean equals(final Object obj)
	{
		if (this == obj)
		{
			return true;
		}

		if (!(obj instanceof JsonValue other))
		{
			return false;
		}

		// hostname and portRange are not null
		/*
		 * if (hostname == null) { if (other.hostname != null) return false; } else
		 */
		return jsonPathReadCtx.json().equals(other.jsonPathReadCtx.json());
	}

	/**
	 * Get the context for JSON Path evaluation
	 * @return JSONPath reading context
	 */
	public ReadContext getJsonPathReadContext()
	{
		return this.jsonPathReadCtx;
	}

	/**
	 * JsonValue factory
	 */
	public static final class Factory extends StringContentOnlyValueFactory<JsonValue>
	{
		/**
		 * Factory constructor
		 */
		public Factory()
		{
			super(DATATYPE);
		}

		@Override
		public JsonValue parse(final String val)
		{
			return new JsonValue(val);
		}

	}
}
