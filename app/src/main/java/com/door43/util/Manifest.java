package com.door43.util;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * This class handles the managment of a manifest file.
 *
 */
public class Manifest {
    private final File mManifestFile;
    private JSONObject mManifest = new JSONObject();
    private static final String FILE_NAME = "manifest.json";

    /**
     * Creates a new manifest object representing a file on the disk
     * @param file
     */
    private Manifest(File file) {
        mManifestFile = file;
    }

    /**
     * Generates a new manifest object.
     * If a manifest file already exists it will be loaded otherwise it will be created.
     * @param directory the directory in which the manifest file exists
     * @return the manifest object or null if the manifest could not be created
     */
    public static Manifest generate(File directory) {
        File file = new File(directory, FILE_NAME);
        if(!file.exists()) {
            file.mkdirs();
        }
        if(!file.isFile()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        Manifest m = new Manifest(file);
        m.load();
        return m;
    }

    /**
     *
     * @param key
     * @return an empty string if the key is invalid
     */
    public String getString(String key) {
        try {
            return mManifest.getString(key);
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     *
     * @param key
     * @return
     * @throws JSONException
     */
    public int getInt(String key) throws JSONException {
        return mManifest.getInt(key);
    }

    /**
     *
     * @param key
     * @return an empty json object if the key is invalid
     */
    public JSONObject getJSONObject(String key) {
        try {
            return mManifest.getJSONObject(key);
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    /**
     *
     * @param key
     * @return an empty json array if the key is invalid
     */
    public JSONArray getJSONArray(String key) {
        try {
            return mManifest.getJSONArray(key);
        } catch (JSONException e) {
            e.printStackTrace();
            return new JSONArray();
        }
    }

    /**
     * Adds an element to the manifest
     * @param key
     * @param json
     */
    public void put(String key, JSONObject json) {
        try {
            mManifest.put(key, json);
            save();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds an element to the manifest
     * @param key
     * @param json
     */
    public void put(String key, JSONArray json) {
        try {
            mManifest.put(key, json);
            save();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds an element to the manifest
     * @param key
     * @param value
     */
    public void put(String key, int value) {
        try {
            mManifest.put(key, value);
            save();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds an element to the manifest
     * @param key
     * @param value
     */
    public void put(String key, String value) {
        try {
            mManifest.put(key, value);
            save();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes an element from the manifest
     * @param key
     */
    public void remove(String key) {
        mManifest.remove(key);
        save();
    }

    /**
     * Saves the manifest to the disk
     */
    private void save() {
        try {
            FileUtils.writeStringToFile(mManifestFile, mManifest.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Deletes the manifest file
     */
    private void delete() {
        mManifestFile.delete();
        mManifest = new JSONObject();
    }

    /**
     * Reads the manifest file from the disk
     */
    private void load() {
        try {
            mManifest = new JSONObject(FileUtils.readFileToString(mManifestFile));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
