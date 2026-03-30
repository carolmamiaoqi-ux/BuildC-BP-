package com.vivalnk.sdk.demo.vital.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import butterknife.BindView;
import butterknife.OnClick;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.vivalnk.sdk.Callback;
import com.vivalnk.sdk.demo.base.app.ConnectedActivity;
import com.vivalnk.sdk.demo.base.app.Layout;
import com.vivalnk.sdk.demo.repository.device.DeviceManager;
import com.vivalnk.sdk.demo.vital.R;
import com.vivalnk.sdk.device.bp5s.BP5SManager;
import com.vivalnk.sdk.utils.GSON;
import java.util.Map;
import java.util.regex.Pattern;

public class BP5SActivity extends ConnectedActivity {

  private static final String TAG = "BP5SActivity";

  String ENGINEER_CLASS = "com.vivalnk.sdk.engineer.ui.EngineerActivity_BP5S";

  BP5SManager manager;

  @BindView(R.id.btnEngineerModule)
  Button btnEngineerModule;
  @BindView(R.id.tvPrinter)
  TextView tvPrinter;

  @Override
  protected Layout getLayout() {
    return Layout.createLayoutByID(R.layout.activity_device_bp5s);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    manager = DeviceManager.getInstance().getBP5SMananger();
    initEngineerModule();
  }

  private void initEngineerModule() {
    try {
      Class.forName(ENGINEER_CLASS);
      btnEngineerModule.setVisibility(View.VISIBLE);
    } catch (ClassNotFoundException e) {
      btnEngineerModule.setVisibility(View.GONE);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  @OnClick(R.id.btnDisconnect)
  void clickBtnDisconnect() {
    showProgressDialog("Disconnecting...");
    DeviceManager.getInstance().disconnect(mDevice);
  }

  private Callback defaultCallback = new Callback() {
    @Override
    public void onStart() {
      showProgressDialog("staring...");
    }

    @Override
    public void onComplete(Map<String, Object> data) {
      dismissProgressDialog();
      tvPrinter.setText(GSON.toJson(data));
    }

    @Override
    public void onError(int code, String msg) {
      dismissProgressDialog();
      tvPrinter.setText("error code = " + code + ", msg = " + msg);
    }
  };

  @OnClick(R.id.btnStartMeasure)
  public void btnStartMeasure() {
    manager.startMeasure(mDevice, new Callback() {
      @Override
      public void onStart() {
        showProgressDialog("staring...");
      }

      @Override
      public void onComplete(Map<String, Object> data) {
        dismissProgressDialog();
        tvPrinter.setText(GSON.toJson(data));
      }

      @Override
      public void onError(int code, String msg) {
        dismissProgressDialog();
        tvPrinter.setText("error code = " + code + ", msg = " + msg);
      }
    });
  }

  @OnClick(R.id.btnStopMeasure)
  public void btnStopMeasure() {
    manager.stopMeasure(mDevice, defaultCallback);
  }

  @OnClick(R.id.btnReadHistoryData)
  public void btnReadHistoryData() {
    manager.readHistory(mDevice, defaultCallback);
  }

  @OnClick(R.id.btnGetBattery)
  public void btnGetBattery() {
    manager.readDeviceBattery(mDevice, defaultCallback);
  }

  @OnClick(R.id.btnReadDeviceInfo)
  public void btnReadDeviceInfo() {
    manager.readDeviceInfo(mDevice, defaultCallback);
  }

  @OnClick(R.id.btnEnableOfflineMode)
  public void btnEnableOfflineMode() {
    manager.setOfflineDetector(mDevice, true, defaultCallback);
  }

  @OnClick(R.id.btnDisableOfflineMode)
  public void btnDisableOfflineMode() {
    manager.setOfflineDetector(mDevice, false, defaultCallback);
  }

  @OnClick(R.id.btnGetHistoryDataNum)
  public void btnGetHistoryDataNum() {
    manager.getHistoryDataNum(mDevice, defaultCallback);
  }

  @OnClick(R.id.btnEngineerModule)
  public void btnEngineerModule() {
    try {
      // look for engineer Activity
      Intent engineerActivity = new Intent(this, Class.forName(ENGINEER_CLASS));
      engineerActivity.putExtra("device", mDevice);
      startActivity(engineerActivity);
    } catch (final Exception e) {
      showToast(R.string.error_no_support_engineer_module);
    }
  }


  private static final int OTA_RET_CODE = 2019;
  private static final int ACTIVITY_CHOOSE_FILE = 3;
  @OnClick(R.id.btnOTA)
  public void clickOTA() {
    openFileSelector();
  }

  private void openFileSelector() {
    Intent intent = new Intent(this, FilePickerActivity.class);
    intent.putExtra(FilePickerActivity.ARG_FILTER, Pattern.compile("CheckO2_app_vivalnk_(\\d+\\.\\d\\.\\d(\\.\\d+)*)*\\.zip"));
    startActivityForResult(intent, ACTIVITY_CHOOSE_FILE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == ACTIVITY_CHOOSE_FILE) {
      if (resultCode != RESULT_OK || data == null) {
        isOTAing = false;
      } else {
        String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
        startActivityForResult(OTAActivity.newIntent(this, mDevice, filePath), OTA_RET_CODE);
        isOTAing = true;
      }
    } else if (requestCode == OTA_RET_CODE) {
      isOTAing = false;
      if (resultCode != Activity.RESULT_OK) {
        showToast("OTA failed");
      }
      finish();
    } else {
      finish();
    }
  }

}
