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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.ow2.authzforce.core.pdp.api.func.Function;
import org.ow2.authzforce.core.pdp.api.value.Bags;
import org.ow2.authzforce.core.pdp.api.value.StandardDatatypes;
import org.ow2.authzforce.core.pdp.api.value.StringValue;
import org.ow2.authzforce.core.pdp.api.value.Value;
import org.ow2.authzforce.pdp.ext.jsonpath.JsonPathFunctions.StringsFromJsonPathFunction;
import org.ow2.authzforce.pdp.ext.jsonpath.JsonValue;

@RunWith(Parameterized.class)
public class JsonPathFunctionTest extends StandardFunctionTest
{
	private static final Function<?> TESTED_FUNCTION = new StringsFromJsonPathFunction();

	public JsonPathFunctionTest(final List<Value> inputs, final Value expectedResult)
	{
		super(TESTED_FUNCTION, null, inputs, expectedResult);
	}

	@Parameters(name = "{index}: {0}")
	public static Collection<Object[]> params() throws Exception
	{
		return Arrays.asList(
		        /*
		         * TODO: Invalid args
		         */

		        /*
		         * Valid args
		         */
		        new Object[] { Arrays.asList(new JsonValue(
		                "{\"id\":\"54c2d6e1-764f-47be-9c09-e5afe9426bc6\",\"createdTimestamp\":1529701638557,\"username\":\"1000\",\"enabled\":true,\"totp\":false,\"emailVerified\":false,\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane.doe@example.com\",\"attributes\":{\"telephoneNumber\":[\"1000\"]},\"disableableCredentialTypes\":[],\"requiredActions\":[],\"notBefore\":0,\"access\":{\"manageGroupMembership\":true,\"view\":true,\"mapRoles\":true,\"impersonate\":true,\"manage\":true}}"),
		                new StringValue("$.id")), Bags.singleton(StandardDatatypes.STRING, new StringValue("54c2d6e1-764f-47be-9c09-e5afe9426bc6")) }, //

		        new Object[] {
		                Arrays.asList(new JsonValue("{\"id\":\"aa0d0934-d33a-49da-a944-d7408f6e1cfc\",\"name\":\"RESTRICTED\",\"path\":\"/Classification/RESTRICTED\"}"), new StringValue("$.name")),
		                Bags.newBag(StandardDatatypes.STRING, List.of(new StringValue("RESTRICTED"))) } //
		);
	}

}
