package com.handsome.robot_server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    BluetoothSocket BTSocket;
    BluetoothAdapter BTAdapter;
    Button bt_start;
    TextView tv_msg;
    StringBuilder sb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bt_start = (Button) findViewById(R.id.bt_start);
        tv_msg = (TextView) findViewById(R.id.tv_msg);
        bt_start.setOnClickListener(this);
        sb = new StringBuilder();

        show("服务端:检查BT");
        checkBT(this);
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

    @Override
    public void onClick(View v) {
        //开启服务器
        ServerThread startServerThread = new ServerThread();
        startServerThread.start();
    }

    /**
     * 开启服务器
     */
    private class ServerThread extends Thread {
        public void run() {
            try {
                BluetoothServerSocket mserverSocket = BTAdapter.listenUsingRfcommWithServiceRecord("btspp",
                        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                show("服务端:等待连接");

                BTSocket = mserverSocket.accept();
                show("服务端:连接成功");

                readThread mreadThread = new readThread();
                mreadThread.start();
                show("服务端:启动接受数据");
            } catch (IOException e) {
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
            InputStream mmInStream = null;
            try {
                mmInStream = BTSocket.getInputStream();
                show("服务端:获得输入流");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            while (true) {
                try {
                    if ((bytes = mmInStream.read(buffer)) > 0) {
                        byte[] buf_data = new byte[bytes];
                        for (int i = 0; i < bytes; i++) {
                            buf_data[i] = buffer[i];
                        }
                        String s = new String(buf_data);
                        show("服务端:读取数据了~~" + s);
                    }
                } catch (IOException e) {
                    try {
                        mmInStream.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    break;
                }
            }
        }
    }
}
