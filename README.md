# openaq-assessment

## REQUIREMENTS
To build from source, the JDK and Maven are required. Use "mvn clean install" and execute the JAR at "openaqassessment/target/openaqassessment-1.0-jar-with-dependencies.jar". A precompiled version of this will be provided in the directory. This is also dependent on access to the internet, as it relies on the OpenAQ API.

## RUNNING
You can execute this JAR from the CLI using one of two command formats
`java -jar [JAR path] [latitude] [longitude] [radius (integer less than 100000)] [pollutant/parameter]`
or
`java -jar [JAR path] [two letter country code] [pollutant/parameter]`.
The list of tracked pollutants is as follows: pm25, pm10, co, bc, so2, no2, o3

## OUTPUT FORMAT
The output is a JSON object with the following fields:

```{
    parameter: String containing the "parameter" or relevant pollutant,
    rangevalues: Array of floating point numbers aligned roughly with the quartile values- number varies with results due to mathematical limits,
    mapEntries: Array of JSON objects with fields for latitude, longitude, and value (ie the most recent pollutant measurement at said location). These can be used to map specific values onto map.
}```

NOTES: All units are in PPM to ensure consistent comparison. The maximum number of allowed data points from the API is 100000, which may effect mapping on large and densely populated nations. We are using the latest endpoint to save on processing per the spec, so historical data would likely require additional changes.
