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

import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmValue;
import org.ow2.authzforce.core.pdp.api.value.AttributeDatatype;
import org.ow2.authzforce.core.pdp.api.value.Value;

/**
 * Special value to be interpreted as Indeterminate. (Mapped to IndeterminateExpression in FunctionTest class.) For testing only.
 *
 */
public class NullValue implements Value
{
	private final AttributeDatatype<?> datatype;
	private final boolean isBag;

	public NullValue(AttributeDatatype<?> datatype)
	{
		this(datatype, false);
	}

	public NullValue(AttributeDatatype<?> datatype, boolean isBag)
	{
		this.datatype = datatype;
		this.isBag = isBag;
	}

	public AttributeDatatype<?> getDatatype()
	{
		return this.datatype;
	}

	public boolean isBag()
	{
		return this.isBag;
	}

	@Override
	public XdmValue getXdmValue()
	{
		return XdmEmptySequence.getInstance();
	}
}