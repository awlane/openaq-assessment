package com.awlane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import kong.unirest.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * NOTES: CONVERTING ALL UNITS TO PPM FOR SAKE OF TIME AND AMBIGUOUS SPEC. THIS
 * IS THEORETICALLY BAD PRACTICE BUT HEAT MAPS DON'T NECESSARILY NEED PRECISE VALUES,
 * JUST RELATIVELY CORRECT ONES
 */
class Result{
    // Due to a quirk of deserializer, this was the fastest solution
    class Coordinates{
        private double latitude;
        private double longitude;
        public Coordinates(double latitude, double longitude){
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude(){
            return latitude;
        }

        public double getLongitude(){
            return longitude;
        }
    }
    class Measurement {
        private String parameter;
        private double value;
        private String unit;
        public Measurement (String parameter, double value, String unit){
            this.parameter = parameter;
            this.value = value;
            this.unit = unit;
        }
        public String getParameter (){
            return this.parameter;
        }
        public double getValue (){
            // This is likely not formally correct but close enough is probably fine
            // for a heatmap?
            if (this.unit.equals("µg/m³")){
                return this.value / 1000;
            }
            else{
                return this.value;
            }
        }
    }
    private Coordinates coordinates;
    private Measurement[] measurements;

    public Result(Coordinates coordinates, Measurement[] measurements){
        this.coordinates = coordinates;
        this.measurements = measurements;
    }

    public double getLatitude(){
        return coordinates.getLatitude();
    }

    public double getLongitude(){
        return coordinates.getLongitude();
    }

    public double getParameterValue(String parameter){
        Measurement paramMeasurement = new Measurement("", -1.0, "");
        for (int i = 0; i < measurements.length; i++){
            if (measurements[i].getParameter().equals(parameter)){
                paramMeasurement = measurements[i];
                break;
            }
        }
        return paramMeasurement.getValue();
    }

}

class MapEntry implements Comparable<MapEntry>{
    private double value;
    private double latitude;
    private double longitude;

    public MapEntry(double value, double latitude, double longitude){
        this.value = value;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getValue(){
        return value;
    }

    public double getLatitude(){
        return latitude;
    }

    public double getLongitude(){
        return longitude;
    }
    // By having our objects be sortable by value, we can easily find quartile
    // values for building our scale
    @Override
    public int compareTo(MapEntry m){
        if (this.value < m.getValue()){
            return -1;
        }
        else if (this.value > m.getValue()){
            return 1;
        }
        else {
            return 0;
        }
    }
}
//JSON file containing map data to be serialized
class MapPayload {
    private String parameter;
    private double[] rangeValues;
    private ArrayList<MapEntry> mapEntries;

    public MapPayload(){
        parameter= "";
        rangeValues = new double[0];
        mapEntries = new ArrayList<MapEntry>();
    }

    public MapPayload(String parameter, double[] rangeValues, ArrayList<MapEntry> mapEntries){
        this.parameter = parameter;
        this.rangeValues = rangeValues;
        this.mapEntries = mapEntries;
    }
}

public class App
{
    final static Set<String> pollutants = new HashSet<>(Arrays.asList("pm25", "pm10", "co", "bc", "so2", "no2", "o3"));
    private static String getAPIData( double latitude, double longitude, int radius ){
        String radString = Integer.toString(radius);
        String coordString = String.format("%f,%f", latitude, longitude);
        Unirest.config().defaultBaseUrl("https://api.openaq.org/");
        String response = Unirest.get("/v2/latest")
            .queryString("coordinates", coordString)
            .queryString("radius", radString)
            .queryString("limit", "100000")
            .asString()
            .getBody();
        return response;
    }

    public static String getAPIData(String countryCode){
        Unirest.config().defaultBaseUrl("https://api.openaq.org/");
        String response = Unirest.get("/v2/latest")
            .queryString("country_id", countryCode)
            .queryString("limit", "100000")
            .asString()
            .getBody();
        return response;
    }

    public static MapPayload processResponse(String response, String chosenParam){
        JsonObject tempJson = new JsonParser().parse(response).getAsJsonObject();
        JsonArray temp = new JsonArray();
        try{
            temp = tempJson.get("results").getAsJsonArray();
        }
        catch (NullPointerException e){
            System.err.println("No responses received for API call, returning placeholder values");
            return new MapPayload();
        }
        Gson gson = new Gson();
        Result[] resultArr = gson.fromJson(temp, Result[].class);
        // Measurement locations may not potentially check for a potential pollutant,
        // consequently we use an ArrayList
        ArrayList<MapEntry> entries = new ArrayList<MapEntry>();
        for (int i = 0; i < resultArr.length; i++){
            Result result = resultArr[i];
            double value = result.getParameterValue(chosenParam);
            // If no valid entries are found, skip to next unit
            if (value < 0){
                continue;
            }
            MapEntry tempEntry;
            try{
                tempEntry = new MapEntry(value, result.getLatitude(), result.getLongitude());

            }
            catch (NullPointerException e){
                continue;
            }
            entries.add(tempEntry);
        }
        // Rough math to get quartile ranges to define "heat" tiers
        // Basing ranges on quartiles is a bit fraught if you don't have enough entries
        // for quartiles
        Collections.sort(entries);
        double[] rangeValues;
        if (entries.size() < 5){
            rangeValues = new double[entries.size()];
            for (int i = 0; i < entries.size(); i++){
                rangeValues[i] = entries.get(i).getValue();
            }
        }
        else {
            rangeValues = new double[5];
            for (int i = 0; i < 5; i ++){
                double quartilePercentage = i * 0.25;
                double tempVal = (entries.size() - 1) * quartilePercentage;
                int resultIndex = (int)Math.round(tempVal);
                rangeValues[i] = entries.get(resultIndex).getValue();
            }
        }
        MapPayload tempPayload = new MapPayload(chosenParam, rangeValues, entries);
        return tempPayload;
    }
    public static void main(String[] args)
    {
        String response;
        String chosenParam;
        if (args.length == 2){
            if (args[0].length() == 2 && args[0].matches("[a-zA-Z]+") && pollutants.contains(args[1])){
                response = getAPIData(args[0]);
                chosenParam = args[1];
            }
            else{
                System.err.println("Invalid country code or parameter!");
                return;
            }
        }
        else if (args.length == 4){
            // Using the coordinate regex from OpenAQ with obvious modifications
            // This is a style guide faux pas, forgive me lol
            if (args[0].matches("^-?\\d{1,2}\\.?\\d{0,8}$") &&
                args[1].matches("^-?1?\\d{1,2}\\.?\\d{0,8}$") &&
                pollutants.contains(args[3])){
                    int tempRad;
                    try {
                        tempRad = Integer.parseInt(args[2]);
                        if (tempRad > 100000 || tempRad < 0){
                            throw new NumberFormatException();
                        }
                    }
                    catch (NumberFormatException e){
                        System.err.println("Invalid radius!");
                    }
                    double latitude = Double.parseDouble(args[0]);
                    double longitude = Double.parseDouble(args[1]);
                    int radius = Integer.parseInt(args[2]);
                    response = getAPIData(latitude, longitude, radius);
                    chosenParam = args[3];
            }
            else{
                System.err.println("Invalid coordinates or parameters!");
                return;
            }
        }
        else{
            System.out.println("Please execute the command with arguments in one of the following formats:");
            System.out.println("java app [latitude] [longitude] [radius (integer less than 100000)] [pollutant/parameter]");
            System.out.println("All coordinate values should be in coordinate degree format");
            System.out.println("java app [two letter country code] [pollutant/parameter]");
            System.out.println("To store output to a file (recommended), please append command with > and a file path");
            return;
        }
        MapPayload serializedPayload = processResponse(response, chosenParam);
        String payloadStr = new Gson().toJson(serializedPayload);
        System.out.println( payloadStr );
    }
}