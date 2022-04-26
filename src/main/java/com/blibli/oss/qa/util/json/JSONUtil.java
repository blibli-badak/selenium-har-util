package com.blibli.oss.qa.util.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class JSONUtil {

    // create function for writting json file with given path from object
    public static void appendJson(Object obj, String path) {
        // read json file from path
        String json = JSONUtil.readJson(path);
        ObjectMapper mapper = new ObjectMapper();
        // convert json to json objectMapper
        ArrayList<Object> jsonObject = null;
        try {
            jsonObject = mapper.readValue(json, ArrayList.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            jsonObject = new ArrayList<>();
        }
        jsonObject.add(obj);

        try {
            json = mapper.writeValueAsString(jsonObject);
//            System.out.println("ResultingJSONstring = " + json);
            // write json into file from path parameter
//            mapper.writeValue(new java.io.File(path), obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readJson(String path) {
        // check if  path file is exist
        if (new java.io.File(path).exists()) {
            // read json file from path
            ObjectMapper mapper = new ObjectMapper();
            try {
                return mapper.readValue(new java.io.File(path), String.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }else{
            // create file from path
            try {
                // create empty json file from path
                Path p = Paths.get(path);
                Files.write(p,"{}".getBytes(StandardCharsets.UTF_8));
                return "{}";
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
