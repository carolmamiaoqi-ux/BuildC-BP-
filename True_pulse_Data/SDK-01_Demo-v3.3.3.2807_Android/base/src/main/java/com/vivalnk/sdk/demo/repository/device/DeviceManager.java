package com.vivalnk.sdk.demo.repository.device;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import com.tencent.mmkv.MMKV;
import com.vivalnk.sdk.BuildConfig;
import com.vivalnk.sdk.Callback;
import com.vivalnk.sdk.CommandRequest;
import com.vivalnk.sdk.DataReceiveListener;
import com.vivalnk.sdk.DefaultCallback;
import com.vivalnk.sdk.DeviceStatusListener;
import com.vivalnk.sdk.SampleDataReceiveListener;
import com.vivalnk.sdk.VitalClient;
import com.vivalnk.sdk.VitalClient.Builder.Key;
import com.vivalnk.sdk.ble.BluetoothConnectListener;
import com.vivalnk.sdk.common.ble.connect.BleConnectOptions;
import com.vivalnk.sdk.common.ble.exception.BleCode;
import com.vivalnk.sdk.common.ble.utils.BluetoothUtils;
import com.vivalnk.sdk.common.eventbus.EventBus;
import com.vivalnk.sdk.common.utils.log.LogInfo.LogType;
import com.vivalnk.sdk.common.utils.log.LogUtils;
import com.vivalnk.sdk.common.utils.log.VitalLog;
import com.vivalnk.sdk.demo.repository.database.DatabaseManager;
import com.vivalnk.sdk.demo.repository.database.VitalDevice;
import com.vivalnk.sdk.device.bp5s.BP5SManager;
import com.vivalnk.sdk.device.checkmeo2.CheckmeO2Manager;
import com.vivalnk.sdk.device.vv330.DataStreamMode;
import com.vivalnk.sdk.device.vv330.VV330Manager;
import com.vivalnk.sdk.engineer.test.LogerManager;
import com.vivalnk.sdk.model.BatteryInfo;
import com.vivalnk.sdk.model.Device;
import com.vivalnk.sdk.model.DeviceModel;
import com.vivalnk.sdk.model.SampleData;
import com.vivalnk.sdk.model.common.DataType;
import com.vivalnk.sdk.open.VivaLINKMMKV;
import com.vivalnk.sdk.open.manager.SubjectManager;
import com.vivalnk.sdk.utils.GSON;
import com.vivalnk.sdk.utils.LogCommon;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.internal.schedulers.SingleScheduler;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by JakeMo on 18-4-26.
 */
public class DeviceManager {

  public static final String TAG = "DeviceManager";

  private Context mContext;

  public static final String projectId = "VivaLNK_SDKTest";
  public static final String subjectId = "testAndroid";

  public BP5SManager getBP5SMananger() {
    return mBP5SMananger;
  }

  private BP5SManager mBP5SMananger;
  private SubjectManager subjectManager;

  private Subject<Runnable> subject = PublishSubject.<Runnable>create().toSerialized();

  public VitalClient.Builder builder;

  public boolean allowConnectLastConnectedDevice=true;

  public boolean autoConnectIfDisconnectDevice=true;

  public boolean allowCheckme_02ReadFile=true;

  public float checkme_o2RealtimeDataInterval=4;

  public boolean allowAnimalHrAlgo=false;

  public boolean allowUploadDataToCloud=false;

  public boolean allowForceClockSyncOnceConnected=true;

  private DataReceiveListener dataReceiveListener;

  private DeviceStatusListener deviceStatusListener;

  private static class SingletonHolder {

    private static final DeviceManager
        INSTANCE = new DeviceManager();
  }

  private DeviceManager() {
  }

  public static DeviceManager getInstance() {
    return SingletonHolder.INSTANCE;
  }

  public void setDataReceiveListener(DataReceiveListener dataReceiveListener) {
    this.dataReceiveListener = dataReceiveListener;
  }

  public void setDeviceStatusListener(DeviceStatusListener deviceStatusListener) {
    this.deviceStatusListener = deviceStatusListener;
  }

  public void init(Application application) {
    mContext = application.getApplicationContext();

    DatabaseManager.getInstance().init(mContext);

    subject
          //event source will be execute on this thread
        .subscribeOn(new SingleScheduler())
          //observer's callback onNext/onError/onComplete will be execute in this thread
        .observeOn(new SingleScheduler())
        .subscribe(new Observer<Runnable>() {
          @Override
          public void onSubscribe(Disposable d) {

          }

          @Override
          public void onNext(Runnable runnable) {
            runnable.run();
          }

          @Override
          public void onError(Throwable e) {
            VitalLog.e(e);
          }

          @Override
          public void onComplete() {

          }
        });

    builder = new VitalClient.Builder();
    builder.setConnectResumeListener(myConnectListener);

    //allow sdk upload data to vCloud
    //default is false, not grant, set to true in demo
    builder.allowSaveDataToDB(allowUploadDataToCloud);

    builder.allowUploadDataToCloud(allowUploadDataToCloud);
    //auto start sampling once connected
    builder.setAutoStartSampling(true);

    //allow sdk obtain network information, example, network operator name, local ip address, type.
    //default is false, not grant
    builder.grantNetworkInfo(true);

    //allow sdk obtain GPS location
    //default is false, not grant
    builder.grantLocationInfo(true);

    //allow force clock sync once sdk connected to a device
    builder.allowForceClockSyncOnceConnected(allowForceClockSyncOnceConnected);
    //set project id/subject id
    builder.putExtra(Key.projectId, MMKV.defaultMMKV().getString(Key.projectId, projectId));
    builder.putExtra(Key.subjectId, MMKV.defaultMMKV().getString(Key.subjectId, subjectId));

    //10MB
//    builder.putExtra(Key.maxFileSize, 10 * 1024 * 1024);
    // min expire time should >= 1 day
//    builder.putExtra(Key.maxAliveTime,  1 * 24 * 60 * 60);
    VitalClient.getInstance().allowWriteToFile(isAllowWriteToFile);
    VitalClient.getInstance().init(mContext, builder);
    if (isAllowWriteToFile) {
      VitalClient.getInstance().openLog();
    } else {
      VitalClient.getInstance().closeLog();
    }
    subjectManager = new SubjectManager();
    if (allowUploadDataToCloud) {
      if (MMKV.defaultMMKV().getString(Key.projectId, "").length() == 0) {
        if (VitalClient.getInstance().getBuilder().getExtras().get(Key.projectId) != null) {
          subjectManager.register(VitalClient.getInstance().getBuilder().getExtras().get(Key.projectId).toString(), "", new DefaultCallback());
        } else {
          subjectManager.register(MMKV.defaultMMKV().getString(Key.projectId,projectId), MMKV.defaultMMKV().getString(Key.subjectId,subjectId), new DefaultCallback());
        }
      } else {
        subjectManager.register(MMKV.defaultMMKV().getString(Key.projectId,projectId), MMKV.defaultMMKV().getString(Key.subjectId,subjectId), new DefaultCallback());
      }
    }
    mBP5SMananger = new BP5SManager(application);
    InputStream is = null;
    try {
      is = mContext.getAssets().open(BuildConfig.sdkChannel.equals("sdk01") ? "com_vivalnk_sdk_vSDK_demo_android.pem" : "com_vivalnk_sdk_vSDK_demo01a_android.pem");
      mBP5SMananger.init(new Callback() {
        @Override
        public void onStart() {

        }

        @Override
        public void onComplete(Map<String, Object> data) {

        }

        @Override
        public void onError(int code, String msg) {

        }

        @Override
        public void onCancel() {

        }
      }, is);
    } catch (IOException e) {
      e.printStackTrace();
    }

    LogerManager.getInstance().init();

  }
  public void setDataToCloudEnable(boolean enable) {
    VitalClient.getInstance().setDataToCloudEnable(enable);
  }

    public void clearLog() {
    VitalClient.getInstance().clearLog();
  }

  private SampleDataReceiveListener sampleDataReceiveListener = new SampleDataReceiveListener() {
    @Override
    public void onReceiveSampleData(Device device, boolean flash, SampleData data) {
      //simplify the usage of onReceiveData, just output sample data
      Log.d(TAG, "onReceiveSampleData: flash = " + flash + ", " + "data = " + data);
    }
  };

  Map<String, VV330Manager> vv330ManagerMap = new HashMap<>();
  private void handleSampleData(final Device device, final Map<String, Object> data) {
    SampleData sampleData = (SampleData) data.get("data");

    if (sampleData == null) {
      return;
    }

    //save to database
    DataStreamMode mode = sampleData.getData(DataType.DataKey.dataStreamMode);
    if (mode != null && mode == DataStreamMode.FullDualMode) {
      if (sampleData.isFlash()) {
        //under FullDualMode, we just store the flash data, and only show the RTS data waveform.
        DatabaseManager.getInstance().insert(new SampleData(sampleData));
      }
    } else {
        DatabaseManager.getInstance().insert(new SampleData(sampleData));
    }

    runOnUiThread(() -> EventBus.getDefault().post(sampleData));

  }

  public VV330Manager getVV330Manager(Device device) {
    VV330Manager manager = null;
    //It's a VivaLNK ecg device
    if (isVivaLNKEcgDevice(device.getName())) {
      manager = vv330ManagerMap.get(device.getId());
      if (manager == null) {
        manager = new VV330Manager(device);
        manager.setAnimalHrAlgoEnable(allowAnimalHrAlgo);
        vv330ManagerMap.put(device.getId(), manager);
      }
    }
    return manager;
  }

  public void removeVV330Manager(Device device) {
    vv330ManagerMap.remove(device.getId());
  }

  private boolean isVivaLNKEcgDevice(String name) {
    return name.startsWith("ECGRec_") || name.startsWith("VitalScout_");
  }

  private BluetoothConnectListener myConnectListener = new MyConnectListener();

  Handler mMainHandler = new Handler(Looper.getMainLooper());

  public int checkBle() {
    return VitalClient.getInstance().checkBle();
  }

  public void enableBle() {
    VitalClient.getInstance().enableBle();
  }

  public void disableBle() {
    VitalClient.getInstance().disableBle();
  }

  /**
   * In some cases, it may cause the OTA to stop at a certain, and if this happens,
   * we can use this function to continue our OTA process if we found the OTA device which name is
   * DfuTarg_First_step or DfuTarg_Second_step.
   * @param device
   */
  public static final String DfuTarg_First_step = "DfuTarg_First_step";
  public static final String DfuTarg_Second_step = "DfuTarg_Second_step";
  public static final String O2_Updater = "O2 Updater";
  public boolean isContinueOTADevice(Device device) {
    return DeviceManager.DfuTarg_First_step.equalsIgnoreCase(device.getName())
        || DeviceManager.DfuTarg_Second_step.equalsIgnoreCase(device.getName())
        || DeviceManager.O2_Updater.equalsIgnoreCase(device.getName());
  }

  public void connect(final Device device) {
    connect(device, 6);
  }

  /**
   * connect device.
   *
   * @param device your vital device
   */
  public void connect(final Device device, int retry) {
    ConnectEvent connectEvent = new ConnectEvent();
    connectEvent.device = device;
    connectEvent.event = ConnectEvent.ON_ERROR;
    if (null == device || TextUtils.isEmpty(device.getId())) {
      connectEvent.code = BleCode.BLUETOOTH_CONNECT_ERROR;
      connectEvent.msg = "device can not be null";
      runOnUiThread(() -> EventBus.getDefault().post(connectEvent));
      return;
    }

    if (BluetoothUtils.isDeviceConnected(mContext, device.getId())) {
      connectEvent.code = BleCode.BLUETOOTH_CONNECT_ERROR;
      connectEvent.msg = "device is connected";
      runOnUiThread(() -> EventBus.getDefault().post(connectEvent));
      return;
    }

    BleConnectOptions options = new BleConnectOptions.Builder()
        .setConnectRetry(retry)
        .setConnectTimeout(10 * 1000)
        .setAutoConnect(autoConnectIfDisconnectDevice)
        .build();
    VitalLog.d(TAG, "user connect to " + GSON.toJson(device));
    VitalClient.getInstance().connect(device, options, myConnectListener);
  }

  public void disconnect(Device device) {
    VitalClient.getInstance().disconnect(device);
  }

  public void disconnectAll() {
    VitalClient.getInstance().disconnectAll();
  }

  public void disconnectAll(boolean quietly) {
    VitalClient.getInstance().disconnectAll(quietly);
  }

  public boolean isConnected(Device device) {
    if (null == device || TextUtils.isEmpty(device.getId())) {
      throw new NullPointerException("device is null, or has a empty  mac id");
    }
    return BluetoothUtils.isDeviceConnected(mContext, device.getId());
  }

  private void registerDataReceiver(Device device) {
    if (BuildConfig.sdkChannel.equals("sdk01")) {
      VitalClient.getInstance().registerDataReceiver(device, dataReceiveListener);
    } else {
      VitalClient.getInstance().registerDeviceStatusListener(device, deviceStatusListener);
    }
  }

  public void execute(Device device, CommandRequest command, Callback callback) {
    if (null == device || TextUtils.isEmpty(device.getId())) {
      throw new NullPointerException("device is null, or has a empty  mac id");
    }
    if (callback == null) {
      callback = new DefaultCallback();
    }
    VitalClient.getInstance().execute(device, command, callback);
  }

  public void runOnUiThread(Runnable runnable) {
    mMainHandler.post(runnable);
  }

  private class MyConnectListener implements BluetoothConnectListener {

    @Override
    public boolean onResume(Device device) {
      //false: not interrupt the sdk reconnecting progress
      //true : you have a custom connecting operation, you don't need sdk to reconnect the device
      Log.d(TAG, "Connect onResume " + device.toString());
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onResume(device)));
      return false;
    }

    @Override
    public void onStartScan(Device device) {
      Log.d(TAG, "Connect onStartScan " + device.toString());
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onStartScan(device)));
    }

    @Override
    public void onStopScan(Device device) {
      Log.d(TAG, "Connect onStopScan " + device.toString());
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onStopScan(device)));
    }

    @Override
    public void onStart(Device device) {
      Log.d(TAG, "Connect onStart " + device.toString());
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onStart(device)));
    }

    @Override
    public void onConnecting(Device device) {
      Log.d(TAG, "Connect onConnecting " + device.toString());
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onConnecting(device)));
    }

    @Override
    public void onConnected(Device device) {
      Log.d(TAG, "Connect onConnected " + device.toString());
      if(device.getModel() == DeviceModel.Checkme_O2){
        CheckmeO2Manager checkmeO2Manager = new CheckmeO2Manager(device);
        if (null != checkmeO2Manager) {
          checkmeO2Manager.setRealtimeDataInterval((long)(DeviceManager.getInstance().checkme_o2RealtimeDataInterval*1000));
          checkmeO2Manager.setWhetherAllowRegularReadHistoryData(DeviceManager.getInstance().allowCheckme_02ReadFile);
        }
      }

      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onConnected(device)));
    }

    @Override
    public void onServiceReady(Device device) {
      Log.d(TAG, "Connect onServiceReady " + device.toString());
      registerDataReceiver(device);
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onServiceReady(device)));
    }

    @Override
    public void onEnableNotify(Device device) {
      Log.d(TAG, "Connect onEnableNotify " + device.toString());
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onEnableNotify(device)));
    }

    @Override
    public void onDeviceReady(Device device) {
      Log.d(TAG, "Connect onDeviceReady " + device.toString());
      DatabaseManager.getInstance().getDeviceDAO().insert(new VitalDevice(device));
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onDeviceReady(device)));
      if (TextUtils.isEmpty(VitalClient.getInstance().getBuilder().getExtra(Key.projectId))) {
        subjectManager.bindDevice(device.getName(), projectId, "testAndroid", System.currentTimeMillis());
      }

      //switch to target mode
      DataStreamMode mode = getDataStreamMode(device);
      VitalLog.d(TAG, LogCommon.getPrefix(device) + ", last datastream mode = " + mode);
      if (mode != DataStreamMode.None) {
        getVV330Manager(device).switchToMode(mode, new DefaultCallback());
      }
    }

    @Override
    public void onTryRescanning(Device device) {
      Log.d(TAG, "Connect onTryRescanning " + device.toString());
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onTryRescanning(device)));
    }

    @Override
    public void onTryReconnect(Device device) {
      Log.d(TAG, "Connect onTryReconnect " + device.toString());
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onTryReconnect(device)));
    }

    @Override
    public void onRetryConnect(Device device, int totalRetryCount, int currentCount, long timeout) {
      Log.d(TAG, "Connect onRetryConnect: mac = " + device.toString() + ", totalCount ="
          + totalRetryCount + ", current = " + currentCount);
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onRetryConnect(device)));
    }

    @Override
    public void onDisConnecting(Device device, boolean isForce) {
      Log.d(TAG, "Connect onDisConnecting " + device.toString() + " isForce=" + isForce);
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onDisConnecting(device, isForce)));
    }

    @Override
    public void onDisconnected(Device device, boolean isForce) {
      DatabaseManager.getInstance().getDeviceDAO().delete(device.getId());
      Log.d(TAG, "Connect onDisconnected " + device.toString() + " isForce=" + isForce);
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onDisconnected(device, isForce)));
      removeVV330Manager(device);
    }

    @Override
    public void onError(Device device, int code, String msg) {
      Log.d(TAG, "Connect onError " + device.toString() + " code=" + code + " msg=" + msg);
      runOnUiThread(() -> EventBus.getDefault().post(ConnectEvent.onError(device, code, msg)));
      removeVV330Manager(device);
    }

  }

  public static void putDataStreamMode(Device device, DataStreamMode mode) {
    VivaLINKMMKV.defaultMMKV().putInt(device.getId() + "_demo_dataStreamMode", mode.ordinal());
  }

  public static DataStreamMode getDataStreamMode(Device device) {
    int modeNumber = VivaLINKMMKV.defaultMMKV().getInt(device.getId() + "_demo_dataStreamMode", DataStreamMode.None.getNumber());
    return DataStreamMode.forNumber(modeNumber);
  }

  public static class VitalSampleData {
    public Device device;
    public Map<String, Object> data;
  }

  public static class BatteryData {
    public Device device;
    public BatteryInfo batteryInfo;
  }

  public static class RssiData {
    public Device device;
    public Integer rssi;
  }

  public static class LeadStatusData {
    public Device device;
    public boolean leadOn;
  }

  private boolean isAllowWriteToFile = true;

  public void allowWriteToFile(boolean isAllowWriteToFile){
    this.isAllowWriteToFile = isAllowWriteToFile;
  }

}
