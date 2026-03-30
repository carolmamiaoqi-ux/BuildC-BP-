package com.example.myapplication.vivalnkdemo;

import android.Manifest;
import android.Manifest.permission;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;
import com.example.myapplication.vivalnkdemo.common.logger.Log;
import com.example.myapplication.vivalnkdemo.common.logger.LogView;
import com.example.myapplication.vivalnkdemo.common.logger.LogWrapper;
import com.example.myapplication.vivalnkdemo.common.logger.MessageOnlyLogFilter;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.vivalnk.sdk.BuildConfig;
import com.vivalnk.sdk.DataReceiveListener;
import com.vivalnk.sdk.VitalClient;
import com.vivalnk.sdk.ble.BluetoothConnectListener;
import com.vivalnk.sdk.ble.BluetoothScanListener;
import com.vivalnk.sdk.common.ble.connect.BleConnectOptions;
import com.vivalnk.sdk.common.ble.scan.ScanOptions;
import com.vivalnk.sdk.common.utils.PermissionHelper;
import com.vivalnk.sdk.model.Device;
import com.vivalnk.sdk.utils.GSON;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.ArrayUtils;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "VivaLINK";

  public static String[] permissions;

  static {
    permissions = new String[]{
        permission.MANAGE_EXTERNAL_STORAGE
    };

    if (Build.VERSION.SDK_INT < 31) {
      //location permission on pre Android 12
      permissions = ArrayUtils.add(permissions, Manifest.permission.ACCESS_COARSE_LOCATION);
      permissions = ArrayUtils.add(permissions, Manifest.permission.ACCESS_FINE_LOCATION);
      // background location permission on Android 10
      // 因为包含了后台定位权限，所以请不要申请和定位无关的权限，因为在 Android 11 上面，后台定位权限不能和其他非定位的权限一起申请
      // 否则会出现只申请了后台定位权限，其他权限会被回绝掉的情况，因为在 Android 11 上面，后台定位权限是要跳 Activity，并非弹 Dialog
      // 另外如果你的应用没有后台定位的需求，请不要一同申请 Permission.ACCESS_BACKGROUND_LOCATION 权限
      // permission.ACCESS_BACKGROUND_LOCATION
    }

    if (Build.VERSION.SDK_INT >= 31) {
      //bluetooth permission on Android 12
      permissions = ArrayUtils.add(permissions, Manifest.permission.BLUETOOTH_CONNECT);
      permissions = ArrayUtils.add(permissions, Manifest.permission.BLUETOOTH_SCAN);
    }

  }


  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    VitalClient.getInstance().init(this.getApplicationContext());
    if (BuildConfig.DEBUG) {
      VitalClient.getInstance().openLog();
      VitalClient.getInstance().allowWriteToFile(true);
    }

    initializeLogging();
    checkPermission();
  }

  //request location and write permissions at rum time
  private void checkPermission() {
    XXPermissions.with(this)
        .permission(permissions)
        .request(new OnPermissionCallback() {

          @Override
          public void onGranted(List<String> permissions, boolean all) {
            if (all) {
              startScan();
              return;
            }

            if (Build.VERSION.SDK_INT < 29) {
              if (!PermissionHelper.hasPermission(MainActivity.this, permission.ACCESS_COARSE_LOCATION)) {
                showToast("You must grant the location permission under Android 10!");
                finish();
                return;
              }
            }

            // 29 <= api < 31
            if (Build.VERSION.SDK_INT < 31) {
              if (
                  !PermissionHelper.hasPermission(MainActivity.this, permission.ACCESS_COARSE_LOCATION)
                      || !PermissionHelper.hasPermission(MainActivity.this, permission.ACCESS_FINE_LOCATION)
              ) {
                showToast("You must grant the location permissions under Android 12!");
                finish();
                return;
              }
            }

            //api >= 31
            if (Build.VERSION.SDK_INT >= 31) {
              if (
                  !PermissionHelper.hasPermission(MainActivity.this, permission.ACCESS_FINE_LOCATION)
                      || !PermissionHelper.hasPermission(MainActivity.this, permission.BLUETOOTH_CONNECT)
                      || !PermissionHelper.hasPermission(MainActivity.this, permission.BLUETOOTH_SCAN)
              ) {
                showToast("You must grant the bluetooth and location permissions on Android 12!");
                finish();
                return;
              }
            }

            startScan();

          }


          @Override
          public void onDenied(List<String> permissions, boolean never) {
            if (never) {
              showToast("App permission denied for ever, please manual grant the permissions");

              XXPermissions.startPermissionActivity(MainActivity.this, permissions);
            } else {
              showToast("App permission grant failed");
            }
          }
        });

  }

  private final DataReceiveListener dataReceiveListener = new DataReceiveListener() {
    @Override
    public void onReceiveData(Device device, Map<String, Object> data) {
      Log.d(TAG, "onReceiveData: data = " + GSON.toJson(data));
    }

    @Override
    public void onBatteryChange(Device device, Map<String, Object> data) {
      Log.d(TAG, "onBatteryChange: data = " + GSON.toJson(data));
    }

    @Override
    public void onDeviceInfoUpdate(Device device, Map<String, Object> data) {
      Log.d(TAG, "onDeviceInfoUpdate: data = " + GSON.toJson(data));
    }

    @Override
    public void onLeadStatusChange(Device device, boolean isLeadOn) {
      Log.d(TAG, "onLeadStatusChange: isLeadOn = " + isLeadOn);
    }

    @Override
    public void onFlashStatusChange(Device device, int remainderFlashBlock) {
      Log.d(TAG, "onFlashStatusChange: remainderFlashBlock = " + remainderFlashBlock);
    }

    @Override
    public void onFlashUploadFinish(Device device) {
      Log.d(TAG, "onFlashUploadFinish: device = " + GSON.toJson(device));
    }
  };

  private void startScan() {
    List<Device> deviceList = new ArrayList<>();
    VitalClient.getInstance().startScan(new ScanOptions.Builder().build(), new BluetoothScanListener() {
      @Override
      public void onDeviceFound(Device device) {
        deviceList.add(device);
        Log.d(TAG, "find device: " + GSON.toJson(device));
      }

      @Override
      public void onStop() {
        Log.d(TAG, "scan stop, find " + deviceList.size() + " devices");
        Device device = findNearestEcgDevice(deviceList);
        if (device != null) {
          Log.d(TAG, "connect to device: " + GSON.toJson(device));
          BleConnectOptions options = new BleConnectOptions.Builder().setAutoConnect(false).build();
          VitalClient.getInstance().connect(device, options,
              new BluetoothConnectListener() {

                @Override
                public void onConnected(Device device) {
                  Log.d(TAG, "onConnected: " + GSON.toJson(device));
                  VitalClient.getInstance().registerDataReceiver(device, dataReceiveListener);
                }

                @Override
                public void onDeviceReady(Device device) {
                  Log.d(TAG, "onDeviceReady: " + GSON.toJson(device));
                }

                @Override
                public void onDisconnected(Device device, boolean isForce) {
                  Log.d(TAG, "onDisconnected: " + GSON.toJson(device));
                }

                @Override
                public void onError(Device device, int code, String msg) {
                  Log.d(TAG, "onError: code = " + code + ", msg = " + msg );
                }
              });
        }
      }
    });
  }

  private Device findNearestEcgDevice(List<Device> deviceList) {
    Device ret = null;
    for (Device device : deviceList) {
      if (!device.getName().startsWith("ECGRec_")) {
        continue;
      }
      if (ret == null || device.getRssi() > ret.getRssi() ) {
        ret = device;
      }
    }
    return ret;
  }

  /** Initializes a custom log class that outputs both to in-app targets and logcat.  */
  private void initializeLogging() { // Wraps Android's native log framework.
    LogWrapper logWrapper = new LogWrapper();
    // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
    Log.setLogNode(logWrapper);
    // Filter strips out everything except the message text.
    MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
    logWrapper.setNext(msgFilter);
    // On screen logging via a customized TextView.
    LogView logView = findViewById(R.id.sample_logview);
    TextViewCompat.setTextAppearance(logView, R.style.Log);
    logView.setBackgroundColor(Color.WHITE);
    msgFilter.setNext(logView);
    Log.i(TAG, "Ready");
  }

  public void showToast(CharSequence text) {
    showToast(text, Toast.LENGTH_SHORT);
  }

  public void showToast(CharSequence text, int showType) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getApplicationContext(), text, showType).show();
      }
    });
  }


}
