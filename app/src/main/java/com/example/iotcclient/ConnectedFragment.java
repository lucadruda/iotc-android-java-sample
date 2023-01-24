package com.example.iotcclient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.iotcclient.databinding.ConnectedBinding;
import com.example.iotcclient.device.DeviceCredential;
import com.example.iotcclient.device.ProvisioningClient;
import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubMessageResult;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.MessageSentCallback;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.device.twin.DesiredPropertiesCallback;
import com.microsoft.azure.sdk.iot.device.twin.DirectMethodPayload;
import com.microsoft.azure.sdk.iot.device.twin.DirectMethodResponse;
import com.microsoft.azure.sdk.iot.device.twin.MethodCallback;
import com.microsoft.azure.sdk.iot.device.twin.Twin;

public class ConnectedFragment extends Fragment implements DesiredPropertiesCallback, MethodCallback {

    private ConnectedBinding binding;
    private final String SCOPE_ID = "0ne00701441";
    private final String DEVICE_KEY = "ESAUTojX0INQX2myN5faAiX3cdxhmeYKQyZosqXGT3A=";
    private final String DEVICE_ID = "dev01";
    private final String connString = "";

    private DeviceClient client;
    private Twin twin;
    private TextView scrollView;
    private ProgressBar progressIndicator;

    IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;
    private View.OnClickListener connectListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    InitClient();
                }
            }).start();
        }
    };
    private View.OnClickListener disconnectListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            client.close();
            binding.connectBtn.setText("Connect");
            binding.connectBtn.setOnClickListener(connectListener);
            scrollView.setText("");
            binding.sendBtn.setEnabled(false);

        }
    };

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = ConnectedBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.connectBtn.setOnClickListener(connectListener);
        binding.sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btnSendOnClick(view);
            }
        });
        scrollView = binding.messageView;
        progressIndicator = binding.progressBar;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // Our MQTT doesn't support abandon/reject, so we will only display the messaged received
    // from IoTHub and return COMPLETE
    static class MessageCallbackMqtt implements com.microsoft.azure.sdk.iot.device.MessageCallback {
        public IotHubMessageResult onCloudToDeviceMessageReceived(Message msg, Object context) {
            Counter counter = (Counter) context;
            System.out.println(
                    "Received message " + counter.toString()
                            + " with content: " + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));

            counter.increment();

            return IotHubMessageResult.COMPLETE;
        }
    }

    private void InitClient() {
        ProvisioningClient provisioningClient;
        try {
            DeviceCredential credential = DeviceCredential.FromString(getArguments().getString("deviceCredential"));
            provisioningClient = new ProvisioningClient(credential.getScopeId(), credential.getDeviceKey(), credential.getDeviceId());
        } catch (Exception ex) {
            provisioningClient = new ProvisioningClient(SCOPE_ID, DEVICE_KEY, DEVICE_ID);
        }

        try {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    binding.connectBtn.setEnabled(false);
                    progressIndicator.setVisibility(View.VISIBLE);
                    scrollView.append("Connecting...\n");
                }
            });

            client = provisioningClient.Connect();
            if (client == null) {
                scrollView.append("Device cannot be connected!");
            }
            client.open(true);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressIndicator.setVisibility(View.INVISIBLE);
                    binding.sendBtn.setEnabled(true);
                    binding.connectBtn.setEnabled(true);
                    binding.connectBtn.setText("Disconnect");
                    binding.connectBtn.setOnClickListener(disconnectListener);
                    scrollView.append("Client connected to " + client.getConfig().getDeviceId() + ".\n");
                }
            });

            if (protocol == IotHubClientProtocol.MQTT) {
                MessageCallbackMqtt callback = new MessageCallbackMqtt();
                Counter counter = new Counter(0);
                client.setMessageCallback(callback, counter);
            } else {
                MessageCallback callback = new MessageCallback();
                Counter counter = new Counter(0);
                client.setMessageCallback(callback, counter);
            }

            client.subscribeToMethods(this, null);
            client.subscribeToDesiredProperties(this, null);
            twin = client.getTwin();
        } catch (Exception e2) {
            System.err.println("Exception while opening IoTHub connection: " + e2.getMessage());
            client.close();
            System.out.println("Shutting down...");
        }
    }

    public void btnSendOnClick(View v) {
        double temperature = 20.0 + Math.random() * 10;

        String msgStr = "{\"temperature\":" + temperature + "}";
        try {
            Message msg = new Message(msgStr);
            msg.setMessageId(java.util.UUID.randomUUID().toString());
            client.sendEventAsync(msg, new MessageSentCallbackImpl(), null);
            scrollView.append("Sending temperature: " + temperature + "\n");
        } catch (Exception e) {
            System.err.println("Exception while sending event: " + e.getMessage());
        }
    }


    @Override
    public void onDesiredPropertiesUpdated(Twin desiredPropertiesUpdate, Object context) {
        System.out.println("Received desired property update:");
        System.out.println(desiredPropertiesUpdate);
        twin.getDesiredProperties().putAll(desiredPropertiesUpdate.getDesiredProperties());
        twin.getDesiredProperties().setVersion(desiredPropertiesUpdate.getDesiredProperties().getVersion());
    }

    private static final int METHOD_SUCCESS = 200;
    private static final int METHOD_NOT_DEFINED = 404;

    @Override
    public DirectMethodResponse onMethodInvoked(String methodName, DirectMethodPayload directMethodPayload, Object payload) {
        // simulating a device that knows what to do when given a command with the method name "performAction".
        if (methodName.equals("performAction")) {
            return new DirectMethodResponse(METHOD_SUCCESS, null);
        }

        // if the command was unrecognized, return a status code to signal that to the client that invoked the method.
        return new DirectMethodResponse(METHOD_NOT_DEFINED, null);
    }

    static class MessageSentCallbackImpl implements MessageSentCallback {
        @Override
        public void onMessageSent(Message message, IotHubClientException e, Object o) {
            if (e == null) {
                System.out.println("IoT Hub responded to message " + message.getMessageId() + " with status OK");
            } else {
                System.out.println("IoT Hub responded to message " + message.getMessageId() + " with status " + e.getStatusCode().name());
            }
        }
    }

    static class MessageCallback implements com.microsoft.azure.sdk.iot.device.MessageCallback {
        public IotHubMessageResult onCloudToDeviceMessageReceived(Message msg, Object context) {
            Counter counter = (Counter) context;
            System.out.println(
                    "Received message " + counter.toString()
                            + " with content: " + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));

            counter.increment();

            return IotHubMessageResult.COMPLETE;
        }
    }


    /**
     * Used as a counter in the message callback.
     */
    static class Counter {
        int num;

        Counter(int num) {
            this.num = num;
        }

        int get() {
            return this.num;
        }

        void increment() {
            this.num++;
        }

        @Override
        public String toString() {
            return Integer.toString(this.num);
        }
    }
}