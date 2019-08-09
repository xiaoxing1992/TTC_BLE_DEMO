package com.ble.demo;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentTabHost;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TabHost.TabSpec;

import com.ble.ble.BleService;
import com.ble.ble.scan.LeScanner;
import com.ble.demo.ui.ConnectedFragment;
import com.ble.demo.ui.MtuFragment;
import com.ble.demo.ui.ScanFragment;
import com.ble.demo.util.LeProxy;
import com.ble.utils.ToastUtil;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public static final String EXTRA_DEVICE_ADDRESS = "extra_device_address";
    public static final String EXTRA_DEVICE_NAME = "extra_device_name";

    private static final int FRAGMENT_SCAN = 0;
    private static final int FRAGMENT_CONNECTED = 1;
    private static final int FRAGMENT_MTU = 2;

    private MainActivity mContext;

    private final BroadcastReceiver mLocalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String address = intent.getStringExtra(LeProxy.EXTRA_ADDRESS);

            switch (intent.getAction()) {
                case LeProxy.ACTION_GATT_CONNECTED:
                    ToastUtil.show(mContext, getString(R.string.scan_connected) + " " + address);
                    break;
                case LeProxy.ACTION_GATT_DISCONNECTED:
                    ToastUtil.show(mContext, getString(R.string.scan_disconnected) + " " + address);
                    break;
                case LeProxy.ACTION_CONNECT_ERROR:
                    ToastUtil.show(mContext, getString(R.string.scan_connection_error) + " " + address);
                    break;
                case LeProxy.ACTION_CONNECT_TIMEOUT:
                    ToastUtil.show(mContext, getString(R.string.scan_connect_timeout) + " " + address);
                    break;
                case LeProxy.ACTION_GATT_SERVICES_DISCOVERED:
                    ToastUtil.show(mContext, "Services discovered: " + address);
                    break;
            }
        }
    };


    private IntentFilter makeFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(LeProxy.ACTION_GATT_CONNECTED);
        filter.addAction(LeProxy.ACTION_GATT_DISCONNECTED);
        filter.addAction(LeProxy.ACTION_CONNECT_ERROR);
        filter.addAction(LeProxy.ACTION_CONNECT_TIMEOUT);
        filter.addAction(LeProxy.ACTION_GATT_SERVICES_DISCOVERED);
        return filter;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "onServiceDisconnected()");
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LeProxy.getInstance().setBleService(service);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setSubtitle(getAppVersion());
        mContext = this;

        initView();
        bindService(new Intent(this, BleService.class), mConnection, BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalReceiver, makeFilter());
    }


    private String getAppVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            return "";
        }
    }


    private void initView() {
        String[] fragmentTitles = getResources().getStringArray(R.array.fragment_titles);
        FragmentTabHost fragmentTabHost = (FragmentTabHost) findViewById(R.id.tabhost);
        fragmentTabHost.setup(this, getSupportFragmentManager(), R.id.frame_content);
        for (int i = 0; i < fragmentTitles.length; i++) {
            TabSpec ts = fragmentTabHost.newTabSpec(fragmentTitles[i]);
            ts.setIndicator(fragmentTitles[i]);
            switch (i) {
                case FRAGMENT_SCAN:
                    fragmentTabHost.addTab(ts, ScanFragment.class, null);
                    break;
                case FRAGMENT_CONNECTED:
                    fragmentTabHost.addTab(ts, ConnectedFragment.class, null);
                    break;
                case FRAGMENT_MTU:
                    fragmentTabHost.addTab(ts, MtuFragment.class, null);
                    break;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            //申请打开手机蓝牙，requestCode为LeScanner.REQUEST_ENABLE_BT
            LeScanner.requestEnableBluetooth(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == LeScanner.REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mLocalReceiver);
        unbindService(mConnection);
    }
}