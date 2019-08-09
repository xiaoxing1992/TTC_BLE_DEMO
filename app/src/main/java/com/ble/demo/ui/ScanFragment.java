package com.ble.demo.ui;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.ble.api.DataUtil;
import com.ble.ble.LeScanRecord;
import com.ble.ble.scan.LeScanResult;
import com.ble.ble.scan.LeScanner;
import com.ble.ble.scan.OnLeScanListener;
import com.ble.demo.LeDevice;
import com.ble.demo.R;
import com.ble.demo.util.LeProxy;
import com.ble.utils.ToastUtil;
import com.uuzuche.lib_zxing.activity.CodeUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class ScanFragment extends Fragment {
    private final static String TAG = "ScanFragment";

    private static final int MSG_SCAN_STARTED = 1;
    private static final int MSG_SCAN_DEVICE = 2;
    private static final int MSG_SCAN_STOPPED = 3;

    private LeProxy mLeProxy = LeProxy.getInstance();
    private LeDeviceListAdapter mLeDeviceListAdapter = new LeDeviceListAdapter();
    private Handler mHandler = new MyHandler(new WeakReference<ScanFragment>(this));

    private SwipeRefreshLayout mRefreshLayout;
    private Button bt_scan;
   private String  mResult;
    private static class MyHandler extends Handler {
        WeakReference<ScanFragment> reference;

        MyHandler(WeakReference<ScanFragment> reference) {
            this.reference = reference;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            ScanFragment fragment = reference.get();
            if (fragment != null) {
                switch (msg.what) {
                    case MSG_SCAN_STARTED:
                        fragment.mRefreshLayout.setRefreshing(true);
                        break;

                    case MSG_SCAN_DEVICE:
                        fragment.mLeDeviceListAdapter.addDevice((LeDevice) msg.obj);
                        break;

                    case MSG_SCAN_STOPPED:
                        fragment.mRefreshLayout.setRefreshing(false);
                        break;
                }
            }
        }
    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
                if (state == BluetoothAdapter.STATE_ON) {
                    if (readyToScan()) {
                        LeScanner.startScan(mOnLeScanListener);
                    }
                }
            }
        }
    };


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.refreshLayout);
        bt_scan = view.findViewById(R.id.bt_scan);
        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mLeDeviceListAdapter.clear();
                LeScanner.startScan(mOnLeScanListener);
            }
        });

        ListView listView = (ListView) view.findViewById(R.id.listView1);
        listView.setAdapter(mLeDeviceListAdapter);
        listView.setOnItemClickListener(mOnItemClickListener);
        listView.setOnItemLongClickListener(mOnItemLongClickListener);

        bt_scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkToScan();
            }
        });
    }

    /**
     * 检查定位权限
     */
    private void checkLocationPermission() {
        if (!(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 102);
        } else {
            LeScanner.startScan(mOnLeScanListener);
            startActivityForResult(new Intent(getActivity(), TirePressureScanActivity.class), 100);
        }
    }

    /**
     * 检查相机权限
     */
    private void checkToScan() {
        if (!(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 101);
        } else {
            startActivityForResult(new Intent(getActivity(), TirePressureScanActivity.class), 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(new Intent(getActivity(), TirePressureScanActivity.class), 100);
            } else {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getActivity());
                builder.setMessage("缺少“相机”权限");
                builder.setPositiveButton("知道了", null);
                builder.setCancelable(false);
                builder.show();
            }
        } else if (requestCode == 102) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                LeScanner.startScan(mOnLeScanListener);
            } else {
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            if (Build.VERSION.SDK_INT >= 23) {
                //TODO Android6.0开始，扫描是个麻烦事，得检测APP有没有定位权限，手机定位有没有开启
                if (!LeScanner.hasFineLocationPermission(getActivity())) {
                    showAlertDialog(
                            R.string.scan_tips_no_location_permission,
                            R.string.to_grant_permission,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    LeScanner.startAppDetailsActivity(getActivity());
                                }
                            });
                } else if (!LeScanner.isLocationEnabled(getActivity())) {
                    showAlertDialog(
                            R.string.scan_tips_location_service_disabled,
                            R.string.to_enable,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    LeScanner.requestEnableLocation(getActivity());
                                }
                            });
                } else {
                    LeScanner.startScan(mOnLeScanListener);
                }
            } else {
                LeScanner.startScan(mOnLeScanListener);
            }
        }
    }


    private boolean readyToScan() {
        if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            if (Build.VERSION.SDK_INT < 23) return true;

            return LeScanner.hasFineLocationPermission(getActivity())
                    && LeScanner.isLocationEnabled(getActivity());
        }
        return false;
    }

    private void showAlertDialog(int msgId, int okBtnTextId, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(getActivity())
                .setCancelable(false)
                .setMessage(msgId)
                .setPositiveButton(okBtnTextId, okListener)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getActivity().finish();
                    }
                }).show();
    }

    @Override
    public void onPause() {
        super.onPause();
        LeScanner.stopScan();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mReceiver);
    }


    private final OnItemClickListener mOnItemClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            //单击连接设备
            LeScanner.stopScan();
            LeDevice device = mLeDeviceListAdapter.getItem(position);
            mLeProxy.connect(device.getAddress(), false);
        }
    };


    private final OnItemLongClickListener mOnItemLongClickListener = new OnItemLongClickListener() {

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            //长按查看广播数据
            LeDevice device = mLeDeviceListAdapter.getItem(position);
            showAdvDetailsDialog(device);
            return true;
        }
    };

    //显示广播数据
    private void showAdvDetailsDialog(LeDevice device) {
        LeScanRecord record = device.getLeScanRecord();

        StringBuilder sb = new StringBuilder();
        sb.append(device.getAddress() + "\n\n");
        sb.append('[' + DataUtil.byteArrayToHex(record.getBytes()) + "]\n\n");
        sb.append(record.toString());

        TextView textView = new TextView(getActivity());
        textView.setPadding(32, 32, 32, 32);
        textView.setText(sb.toString());

        Dialog dialog = new Dialog(getActivity());
        dialog.setTitle(device.getName());
        dialog.setContentView(textView);
        dialog.show();
    }


    //蓝牙扫描监听
    private final OnLeScanListener mOnLeScanListener = new OnLeScanListener() {
        @Override
        public void onScanStart() {
            mHandler.sendEmptyMessage(MSG_SCAN_STARTED);
        }

        @Override
        public void onLeScan(LeScanResult leScanResult) {
            BluetoothDevice device = leScanResult.getDevice();
            Message msg = new Message();
            msg.what = MSG_SCAN_DEVICE;
            String address = device.getAddress().replaceAll(":", "");
            if (!address.equals(mResult)) {
                return;
            }
            msg.obj = new LeDevice(
                    device.getName(),
                    device.getAddress(),
                    leScanResult.getRssi(),
                    leScanResult.getLeScanRecord().getBytes()
            );
            mHandler.sendMessage(msg);
        }

        @Override
        public void onScanFailed(int errorCode) {
        }

        @Override
        public void onScanStop() {
            mHandler.sendEmptyMessage(MSG_SCAN_STOPPED);
        }
    };


    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<LeDevice> mLeDevices = new ArrayList<>();

        void addDevice(LeDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
                notifyDataSetChanged();
            }
        }

        void clear() {
            mLeDevices.clear();
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public LeDevice getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;

            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.item_device_list, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.deviceRssi = (TextView) view.findViewById(R.id.txt_rssi);
                viewHolder.connect = (TextView) view.findViewById(R.id.btn_connect);
                viewHolder.txt_desc = (TextView) view.findViewById(R.id.txt_desc);
                viewHolder.connect.setVisibility(View.VISIBLE);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            LeDevice device = mLeDevices.get(i);

            LeScanRecord leScanRecord = device.getLeScanRecord();

            String s = DataUtil.byteArrayToHex(leScanRecord.getBytes());
            viewHolder.txt_desc.setText(s);

            String deviceName = device.getName();
            if (!TextUtils.isEmpty(deviceName))
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());
            viewHolder.deviceRssi.setText("rssi: " + device.getRssi() + "dbm");

            return view;
        }
    }

    private static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceRssi;
        TextView connect;
        TextView txt_desc;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                ToastUtil.show(getActivity(),"解析二维码失败");
                return;
            }
            Bundle bundle = data.getExtras();
            if (bundle == null) {
                ToastUtil.show(getActivity(),"解析二维码失败");
                return;
            }
            if (bundle.getInt(CodeUtils.RESULT_TYPE) == CodeUtils.RESULT_SUCCESS) {
                String result = bundle.getString(CodeUtils.RESULT_STRING);
                if (result != null) {
                    result = result.replaceAll("[^a-zA-Z0-9]", "");
                    if (result.length() == 12) {
                        mResult = result;
                        mLeDeviceListAdapter.clear();
                    } else {
                        ToastUtil.show(getActivity(),"该二维码不是12位设备码");
                    }
                } else {
                    ToastUtil.show(getActivity(),"该二维码不是12位设备码");
                }
            } else {
                ToastUtil.show(getActivity(),"解析二维码失败");
            }
        }
    }
}