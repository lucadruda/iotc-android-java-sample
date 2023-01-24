package com.example.iotcclient.device;

import com.google.gson.Gson;

public class DeviceCredential {

    private String deviceId;

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceKey() {
        return deviceKey;
    }

    public String getScopeId() {
        return scopeId;
    }

    private String deviceKey;
    private String scopeId;

    @Override
    public String toString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public static DeviceCredential FromString(String data) {
        Gson gson = new Gson();
        return gson.fromJson(data, DeviceCredential.class);
    }
}
