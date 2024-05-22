# AuthzForce PDP extensions for JSONPath evaluation

This project provides the following PDP extensions:
- XACML datatype 'urn:ow2:authzforce:feature:pdp:datatype:json' for JSON object/array values
- XACML functions that evaluate a JSON path (second parameter of standard string datatype) against an input JSON object/array (first parameter of datatype 'urn:ow2:authzforce:feature:pdp:datatype:json') and return the result of this evaluation (the return datatype depends on the actual function used): 
  - 'urn:ow2:authzforce:feature:pdp:function:string-from-json-path' returns a bag of strings, 
  - 'urn:ow2:authzforce:feature:pdp:function:integer-from-json-path' returns a bag of integers, 
  - 'urn:ow2:authzforce:feature:pdp:function:double-from-json-path' returns a bag of doubles, 
  - 'urn:ow2:authzforce:feature:pdp:function:boolean-from-json-path' returns a bag of booleans.