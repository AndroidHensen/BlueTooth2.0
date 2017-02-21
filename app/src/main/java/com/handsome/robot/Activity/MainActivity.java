package com.handsome.robot.Activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.handsome.robot.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView tv_msg;
    private LinearLayout ly_device;
    private Button bt_search, bt_send;
    private BluetoothSocket BTSocket;
    private BluetoothAdapter BTAdapter;
    private BluetoothDevice device;
    private StringBuilder sb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ly_device = (LinearLayout) findViewById(R.id.ly_device);
        bt_search = (Button) findViewById(R.id.bt_search);
        bt_send = (Button) findViewById(R.id.bt_send);
        tv_msg = (TextView) findViewById(R.id.tv_msg);
        bt_search.setOnClickListener(this);
        bt_send.setOnClickListener(this);
        sb = new StringBuilder();

        show("客户端:检查BT");
        checkBT(this);
        show("客户端:注册接收者");
        registerBTReceiver();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_search:
                show("客户端:开始寻找设备");
                BTAdapter.startDiscovery();
                break;
            case R.id.bt_send:
                sendMessage();
                break;
        }
    }

    /**
     * UI文本输出
     *
     * @param msg
     */
    public void show(String msg) {
        sb.append(msg + "\n");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv_msg.setText(sb.toString());
            }
        });
    }

    /**
     * 检查蓝牙
     */
    public void checkBT(Context context) {
        BTAdapter = BluetoothAdapter.getDefaultAdapter();
        if (BTAdapter != null) {
            if (!BTAdapter.isEnabled()) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                // 设置蓝牙可见性，最多300秒
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                context.startActivity(intent);
            }
        } else {
            show("本地设备驱动异常!");
        }
    }


    /**
     * 注册广播
     */
    public void registerBTReceiver() {
        // 设置广播信息过滤
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        // 注册广播接收器，接收并处理搜索结果
        registerReceiver(BTReceive, intentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //注销广播
        unregisterReceiver(BTReceive);
    }

    /**
     * 广播接收者
     */
    private BroadcastReceiver BTReceive = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //打印Action，调试使用
            show(action);
            //找到设备通知  ACTION_FOUND,设备已配对通知  ACTION_BOND_STATE_CHANGED
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                show("客户端:找到的BT名:" + device.getName());
                // 如果查找到的设备符合，添加到UI上
                addBT();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                // 获取蓝牙设备的连接状态
                int connectState = device.getBondState();
                // 已配对
                if (connectState == BluetoothDevice.BOND_BONDED) {
                    try {
                        show("客户端:开始连接:");
                        clientThread clientConnectThread = new clientThread();
                        clientConnectThread.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    /**
     * 添加找到的BT
     */
    private void addBT() {
        Button bt = new Button(MainActivity.this);
        bt.setTag(device.getName());
        bt.setText(device.getName());
        ly_device.addView(bt);
        //处理选中BT设备，进行绑定
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bondBT((String) v.getTag());
            }
        });
    }

    /**
     * 绑定蓝牙
     *
     * @param deviceName
     */
    private void bondBT(String deviceName) {
        if (device.getName().equalsIgnoreCase(deviceName)) {
            show("客户端:配对蓝牙开始");
            // 搜索蓝牙设备的过程占用资源比较多，一旦找到需要连接的设备后需要及时关闭搜索
            BTAdapter.cancelDiscovery();
            // 获取蓝牙设备的连接状态
            int connectState = device.getBondState();

            switch (connectState) {
                // 未配对
                case BluetoothDevice.BOND_NONE:
                    show("客户端:开始配对");
                    try {
                        Method createBondMethod = BluetoothDevice.class.getMethod("createBond");
                        createBondMethod.invoke(device);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                // 已配对
                case BluetoothDevice.BOND_BONDED:
                    try {
                        show("客户端:开始连接:");
                        clientThread clientConnectThread = new clientThread();
                        clientConnectThread.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }


    /**
     * 开启客户端
     */
    private class clientThread extends Thread {
        public void run() {
            try {
                //创建一个Socket连接：只需要服务器在注册时的UUID号
                BTSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                //连接
                show("客户端:开始连接...");
                BTSocket.connect();
                show("客户端:连接成功");
                //启动接受数据
                show("客户端:启动接受数据");
                readThread mreadThread = new readThread();
                mreadThread.start();
            } catch (IOException e) {
                show("客户端:连接服务端异常！断开连接重新试一试");
                e.printStackTrace();
            }
        }
    }

    /**
     * 读取数据
     */
    private class readThread extends Thread {
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            InputStream is = null;
            try {
                is = BTSocket.getInputStream();
                show("客户端:获得输入流");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            while (true) {
                try {
                    if ((bytes = is.read(buffer)) > 0) {
                        byte[] buf_data = new byte[bytes];
                        for (int i = 0; i < bytes; i++) {
                            buf_data[i] = buffer[i];
                        }
                        String s = new String(buf_data);
                        show("客户端:读取数据了" + s);
                    }
                } catch (IOException e) {
                    try {
                        is.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    break;
                }
            }
        }
    }


    /**
     * 发送数据
     */
    public void sendMessage() {
        if (BTSocket == null) {
            Toast.makeText(this, "没有连接", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            OutputStream os = BTSocket.getOutputStream();
            os.write("我爱你dahsid132456@#%￥*".getBytes());
            os.flush();
            show("客户端:发送信息成功");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
