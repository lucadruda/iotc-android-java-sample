package com.example.iotcclient.device;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.provisioning.device.ProvisioningDeviceClient;
import com.microsoft.azure.sdk.iot.provisioning.device.ProvisioningDeviceClientRegistrationCallback;
import com.microsoft.azure.sdk.iot.provisioning.device.ProvisioningDeviceClientRegistrationResult;
import com.microsoft.azure.sdk.iot.provisioning.device.ProvisioningDeviceClientStatus;
import com.microsoft.azure.sdk.iot.provisioning.device.ProvisioningDeviceClientTransportProtocol;
import com.microsoft.azure.sdk.iot.provisioning.device.internal.exceptions.ProvisioningDeviceClientException;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProviderSymmetricKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProvisioningClient {
    // Typically "global.azure-devices-provisioning.net"
    private static final String GLOBAL_ENDPOINT = "global.azure-devices-provisioning.net";

    // Uncomment one line to choose which protocol you'd like to use
    private static final ProvisioningDeviceClientTransportProtocol PROVISIONING_DEVICE_CLIENT_TRANSPORT_PROTOCOL = ProvisioningDeviceClientTransportProtocol.MQTT;

    private static final int MAX_TIME_TO_WAIT_FOR_REGISTRATION = 10000; // in milliseconds


    public ProvisioningClient(String scopeId, String deviceKey, String deviceId) {
        this.scopeId = scopeId;
        this.deviceKey = deviceKey;
        this.deviceId = deviceId;
    }

    private String scopeId;
    private String deviceKey;
    private String deviceId;

    public DeviceClient Connect() throws Exception {
        SecurityProviderSymmetricKey securityClientSymmetricKey;
        DeviceClient deviceClient = null;

        // For the sake of security, you shouldn't save keys into String variables as that places them in heap memory. For the sake
        // of simplicity within this sample, though, we will save it as a string. Typically this key would be loaded as byte[] so that
        // it can be removed from stack memory.
        byte[] derivedSymmetricKey = deviceKey.getBytes(StandardCharsets.UTF_8);

        securityClientSymmetricKey = new SecurityProviderSymmetricKey(derivedSymmetricKey, deviceId);

        ProvisioningDeviceClient provisioningDeviceClient = null;
        try {
            ProvisioningStatus provisioningStatus = new ProvisioningStatus();

            provisioningDeviceClient = ProvisioningDeviceClient.create(GLOBAL_ENDPOINT, scopeId, PROVISIONING_DEVICE_CLIENT_TRANSPORT_PROTOCOL, securityClientSymmetricKey);

            provisioningDeviceClient.registerDevice(new ProvisioningDeviceClientRegistrationCallbackImpl(), provisioningStatus);
            while (provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() != ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_ASSIGNED) {
                if (provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_ERROR ||
                        provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_DISABLED ||
                        provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_FAILED) {
                    provisioningStatus.exception.printStackTrace();
                    System.out.println("Registration error, bailing out");
                    break;
                }
                System.out.println("Waiting for Provisioning Service to register");
                Thread.sleep(MAX_TIME_TO_WAIT_FOR_REGISTRATION);
            }

            if (provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_ASSIGNED) {
                // connect to iothub
                String iotHubUri = provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getIothubUri();
                String deviceId = provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getDeviceId();
                try {
                    deviceClient = new DeviceClient(iotHubUri, deviceId, securityClientSymmetricKey, IotHubClientProtocol.MQTT);
                    deviceClient.open(false);
                    return deviceClient;
                } catch (IOException e) {
                    if (deviceClient != null) {
                        deviceClient.close();
                    }
                }
            }
        } catch (ProvisioningDeviceClientException | InterruptedException e) {
            System.out.println("Provisioning Device Client threw an exception" + e.getMessage());
            if (provisioningDeviceClient != null) {
                provisioningDeviceClient.close();
            }
        }
        return null;
    }


    class ProvisioningStatus {
        ProvisioningDeviceClientRegistrationResult provisioningDeviceClientRegistrationInfoClient = new ProvisioningDeviceClientRegistrationResult();
        Exception exception;
    }

    class ProvisioningDeviceClientRegistrationCallbackImpl implements ProvisioningDeviceClientRegistrationCallback {
        @Override
        public void run(ProvisioningDeviceClientRegistrationResult provisioningDeviceClientRegistrationResult, Exception exception, Object context) {
            if (context instanceof ProvisioningStatus) {
                ProvisioningStatus status = (ProvisioningStatus) context;
                status.provisioningDeviceClientRegistrationInfoClient = provisioningDeviceClientRegistrationResult;
                status.exception = exception;
            } else {
                System.out.println("Received unknown context");
            }
        }
    }
}
