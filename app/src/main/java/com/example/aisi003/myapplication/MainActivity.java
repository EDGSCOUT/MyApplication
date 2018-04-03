package com.example.aisi003.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private String TAG = "MyApp";

    private Button saomiao;
    private TextView lianjiezhuangtai, xinlv;
    private ListView list;
    private LocationManager lm;//【位置管理】

    // IP地址
    private static String IP = "ccpl.psych.ac.cn:20049";

    //时间
    private Timer timer;
    private static final int TIME_SECOND = 15;

    Connection con = null;

    private int stepNum = 0, shijian = 0, HeartRate = 0, battery = 0, HReffectiveFlag = 0, crc = 0, HRtime;//HReffectiveFlag (0:未佩戴, 1:采样时间未就绪, 2:有效)
    private int heart, steps, electricQuantity, sports, wearFlag;
    private String uniqueId;

    private static final int msgKey1 = 1;

    BluetoothAdapter bluetoothAdapter;
    BluetoothGatt bluetoothGatt;
    BluetoothDevice bluetoothDevice;
    BluetoothGattService bluetoothGattServices;
    BluetoothGattCharacteristic NotificationCharacteristic, batteryCharacteristic, writeCharacteristic;

    List<BluetoothDevice> deviceList = new ArrayList<>();
    List<String> serviceslist = new ArrayList<String>();


    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        getMyUUID();

        //startService(new Intent(getApplicationContext(), My_service_mysql.class));

        //蓝牙管理，这是系统服务可以通过getSystemService(BLUETOOTH_SERVICE)的方法获取实例
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        //通过蓝牙管理实例获取适配器，然后通过扫描方法（scan）获取设备(device)
        bluetoothAdapter = bluetoothManager.getAdapter();

        //得到系统的位置服务，判断GPS是否激活
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean ok = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (ok) {
            Toast.makeText(this, "已开启GPS定位服务", 1).show();
        } else {
            Toast.makeText(this, "系统检测到未开启GPS定位服务", 1).show();
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        new TimeThread().start();
    }

    public class TimeThread extends Thread {
        @Override
        public void run() {
            super.run();
            do {
                try {
                    Thread.sleep(4000);
                    Message msg = new Message();
                    msg.what = msgKey1;
                    mHandler.sendMessage(msg);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (true);
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case msgKey1:
                    SharedPreferences sharedPreferences = getSharedPreferences("userdata",
                            Activity.MODE_PRIVATE);
                    sharedPreferences.getInt("userHer", HeartRate);
                    xinlv.setText(HeartRate + "");
                    break;
                default:
                    break;
            }
        }
    };

    private void initView() {
        saomiao = (Button) findViewById(R.id.button1);

        list = (ListView) findViewById(R.id.list);

        lianjiezhuangtai = (TextView) findViewById(R.id.lianjiezhuangtai);
        xinlv = (TextView) findViewById(R.id.xinlv);

        saomiao.setOnClickListener(this);

        //item 监听事件
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothDevice = deviceList.get(i);
                //连接设备的方法,返回值为bluetoothgatt类型
                bluetoothGatt = bluetoothDevice.connectGatt(MainActivity.this, false, gattcallback);
                lianjiezhuangtai.setText("连接" + bluetoothDevice.getName() + "中...");
            }
        });

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button1:
                //开始扫描前开启蓝牙
                Intent turn_on = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turn_on, 0);
                Toast.makeText(MainActivity.this, "蓝牙已经开启", Toast.LENGTH_SHORT).show();

                Thread scanThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i("TAG", "run: saomiao ...");
                        saomiao();
                    }
                });
                scanThread.start();
                break;
        }

    }

    //启动扫描
    public void saomiao() {
        timer = new Timer();
        //扫描前清理
        deviceList.clear();

        //开始扫描
        bluetoothAdapter.startLeScan(callback);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                bluetoothAdapter.stopLeScan(callback);
                Log.i(TAG, "15s  auto scan");
            }
        }, TIME_SECOND * 1000);
    }

    //扫描回调
    public BluetoothAdapter.LeScanCallback callback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice bluetoothDevice, int rssi, byte[] values) {
            Log.i("TAG", "onLeScan: " + bluetoothDevice.getName() + "/t" + bluetoothDevice.getAddress() + "/t" + bluetoothDevice.getBondState());
            //重复过滤方法，列表中包含不该设备才加入列表中，并刷新列表
            if (!deviceList.contains(bluetoothDevice)) {
                deviceList.add(bluetoothDevice);
                //将设备加入列表数据中
                list.setAdapter(new MyAdapter(MainActivity.this, deviceList));
            }
        }
    };


    //连接服务
    private BluetoothGattCallback gattcallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, final int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String status;
                    switch (newState) {
                        //已经连接
                        case BluetoothGatt.STATE_CONNECTED:
                            lianjiezhuangtai.setText(bluetoothDevice.getName() + "已连接");
                            //该方法用于获取设备的服务，寻找服务
                            bluetoothGatt.discoverServices();
                            break;
                        //正在连接
                        case BluetoothGatt.STATE_CONNECTING:
                            lianjiezhuangtai.setText("正在连接");
                            break;
                        //连接断开
                        case BluetoothGatt.STATE_DISCONNECTED:
                            lianjiezhuangtai.setText("已断开");
                            break;
                        //正在断开
                        case BluetoothGatt.STATE_DISCONNECTING:
                            lianjiezhuangtai.setText("断开中");
                            break;
                    }
                    //pd.dismiss();
                }

            });
        }

        //授时，write
        public void writeTime() {
            //授时，write
            if (writeCharacteristic != null && bluetoothGatt != null) {
                Log.i(TAG, "write Value start");
                int time = (int) (System.currentTimeMillis() / 1000);
                final byte[] temp_time = new byte[4];
                temp_time[0] = (byte) (0xff & time);
                temp_time[1] = (byte) ((0xff00 & time) >> 8);
                temp_time[2] = (byte) ((0xff0000 & time) >> 16);
                temp_time[3] = (byte) ((0xff000000 & time) >> 24);
                while (writeCharacteristic.setValue(new byte[]{0x04, 0x05, temp_time[0], temp_time[1], temp_time[2], temp_time[3], (byte) 0xff}) == false)
                    ;
                while (bluetoothGatt.writeCharacteristic(writeCharacteristic) == false) ;
            }
        }


        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = bluetoothGatt.getServices();
                for (BluetoothGattService bluetoothGattService : services) {
                   // Log.i(TAG, " server:" + bluetoothGattService.getUuid().toString());
                    List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
                    for (BluetoothGattCharacteristic bluetoothGattCharacteristic : characteristics) {String CharacteristicUUid = bluetoothGattCharacteristic.getUuid().toString();
                        //Log.e(TAG, " charac:" + CharacteristicUUid);
                        if (CharacteristicUUid.equals(BleDefinedUUIDs.Characteristic.BATTERY_LEVEL_CHARACTERISTIC_UUID)) {
                            batteryCharacteristic = bluetoothGattCharacteristic;
                        } else if (CharacteristicUUid.equals(BleDefinedUUIDs.Characteristic.Notification_CHARACTERISTIC_UUID)) {
                            NotificationCharacteristic = bluetoothGattCharacteristic;
                        } else if (CharacteristicUUid.equals(BleDefinedUUIDs.Characteristic.Write_CHARACTERISTIC_UUID)) {
                            writeCharacteristic = bluetoothGattCharacteristic;
                        }
                    }
                }
                writeTime();//连接之后，发现服务，为服务属性赋值，然后授时
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.i(TAG, "write Value onCharacteristicWrite");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, " write success");
                new Thread() {
                    @Override
                    public void run() {
                        //这里写入子线程需要做的工作
                        while (!enableNotification(true, NotificationCharacteristic)) {
                            Log.e(TAG, " NotificationCharacteristic false");
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                            }
                        }
                        Log.e(TAG, " NotificationCharacteristic ok");
                    }
                }.start();
            } else {
                Log.e(TAG, " write fail");
                //while(bluetoothGatt.writeCharacteristic(writeCharacteristic) == false);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            String characteristicUUID = characteristic.getUuid().toString();
            if (characteristicUUID.equals(NotificationCharacteristic.getUuid().toString())) {
                byte[] values = characteristic.getValue();
                //0x01
                int battery = 0;
                //0x02
                //int HeartRate = 0;
                //long HRtime = 0;
                //0x03
                //acc=Acceleration
                int acc_x = 0;
                int acc_y = 0;
                int acc_z = 0;
                int gyro_x = 0;
                int gyro_y = 0;
                int gyro_z = 0;
                if (values[0] == 0x02) {
                    HeartRate = (values[2] & 0x00ff);
                    // if( HeartRate==0 || HeartRate==255 )
                    //HRtime = ((values[6]<<24)&0xff000000) | ((values[5]<<16)&0x00ff0000) | ((values[4]<<8)&0x0000ff00) | (values[3]&0x000000ff);
                    //v1.4
                    HRtime = ((values[9] << 24) & 0xff000000) | ((values[8] << 16) & 0x00ff0000) | ((values[7] << 8) & 0x0000ff00) | (values[6] & 0x000000ff);
                    //v1.5
                    HReffectiveFlag = values[10] & 0x03;
                    //历史数据与当前数据标识
                    crc = values[11] & 0x00ff;

                    Log.i(TAG, "info_2:HeartRate=" + HeartRate + ",time=" + HRtime);

                    //braceletListener.onHeartRateMeasurement(HeartRate);

                    SharedPreferences mySharedPreferences = getSharedPreferences("userdata", Activity.MODE_PRIVATE);
                    //实例化SharedPreferences.Editor对象（第二步）
                    SharedPreferences.Editor editor = mySharedPreferences.edit();
                    //用putString的方法保存数据
                    editor.putInt("userHer", HeartRate);
                    editor.putInt("userTime", HRtime);
                    //提交当前数据
                    editor.commit();

                    new MyThread().start();
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
        }
    };


    private boolean enableNotification(boolean enable, BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || characteristic == null)
            return false;
        if (!bluetoothGatt.setCharacteristicNotification(characteristic, enable))
            return false;
        BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if (clientConfig == null)
            return false;
        if (enable) {
            clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        Log.e(TAG, " NotificationCharacteristic  begin");
        return bluetoothGatt.writeDescriptor(clientConfig);
    }

    private String getMyUUID() {
        final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
        final String tmDevice, tmSerial, tmPhone, androidId;
        tmDevice = "" + tm.getDeviceId();
        tmSerial = "" + tm.getSimSerialNumber();
        androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        UUID deviceUuid = new UUID(androidId.hashCode(), ((long) tmDevice.hashCode() << 32) | tmSerial.hashCode());
        uniqueId = deviceUuid.toString();
        Log.d("debug", "uuid=" + uniqueId);

        SharedPreferences mySharedPreferences = getSharedPreferences("userdata", Activity.MODE_PRIVATE);
        //实例化SharedPreferences.Editor对象（第二步）
        SharedPreferences.Editor editor = mySharedPreferences.edit();
        //用putString的方法保存数据
        editor.putString("userID", uniqueId);
        //提交当前数据
        editor.commit();

        return uniqueId;
    }

    private final byte[] hex = "0123456789ABCDEF".getBytes();

    // 从字节数组到十六进制字符串转换
    private String Bytes2HexString(byte[] b) {
        byte[] buff = new byte[2 * b.length];
        for (int i = 0; i < b.length; i++) {
            buff[2 * i] = hex[(b[i] >> 4) & 0x0f];
            buff[2 * i + 1] = hex[b[i] & 0x0f];
        }
        return new String(buff);
    }


    //post上传线程
    public class MyThread extends Thread {
        @Override
        public void run() {
            Looper.prepare();
            String urlPath = "http://" + IP + "/Login";
            URL url;
            try {
                url = new URL(urlPath);
                Log.i(TAG, "Urlstr-----------------------" + url);
                /*封装子对象*/
                JSONObject ClientKey = new JSONObject();
                ClientKey.put("appuuid", uniqueId);
                ClientKey.put("apphr", HeartRate + "");
                ClientKey.put("apphrtime", HRtime + "");

                /*封装Person数组*/
                JSONObject params = new JSONObject();
                params.put("Person", ClientKey);
                /*把JSON数据转换成String类型使用输出流向服务器写*/
                String content = String.valueOf(params);
                Log.i(TAG, "Gsonstr------------" + content);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setDoOutput(true);//设置允许输出
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", "Fiddler");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Charset", "UTF-8");
                OutputStream os = conn.getOutputStream();
                os.write(content.getBytes());
                os.close();

                /*服务器返回的响应码*/
                int code = conn.getResponseCode();
                Log.i(TAG,"Codestr---------------------------"+code);
                if (code == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String retData = null;
                    String responseData = "";
                    while ((retData = in.readLine()) != null) {
                        responseData += retData;
                    }
                    JSONObject jsonObject = new JSONObject(responseData);
                    JSONObject succObject = jsonObject.getJSONObject("regsucc");
                    //System.out.println(result);
                    String success = succObject.getString("id");

                    in.close();
                    //System.out.println(success);
                    Toast.makeText(MainActivity.this, success, Toast.LENGTH_SHORT).show();
                    /*Intent intentToLogin = new Intent();
                    intentToLogin.setClass(MainActivity.this, MainActivity.class);
                    startActivity(intentToLogin);
                    finish();*/
                } else {
                    Toast.makeText(getApplicationContext(), "数据提交失败", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                // TODO: handle exception
                throw new RuntimeException(e);
            }
            Looper.loop();
        }
    }






















/*    Handler myHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle data = new Bundle();
            data = msg.getData();
        }
    };

    Runnable runnable = new Runnable() {
        private Connection connection = null;

        @Override
        public void run() {
            try {
                Class.forName("com.mysql.jdbc.Driver");
                Log.d("加载驱动", "==========完成");
                connection = DriverManager.getConnection("jdbc:mysql://192.168.16.56:3306/cashi", "root", "159753");
                Log.d("连接成功", "+++++++++++++++++++++++++++++++++++++++");
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (SQLException e1) {
                e1.printStackTrace();
                Log.e("数据库连接失败", e1.toString());
            }
            *//*try {
                test(connection);    //测试数据库连接
            } catch (java.sql.SQLException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }*//*
        }

        public void test(Connection con1) throws java.sql.SQLException {
            try {
                String sql = "select * from user";        //查询表名为“user”的所有内容
                Statement stmt = con1.createStatement();      //创建Statement
                ResultSet rs = stmt.executeQuery(sql);     //ResultSet类似Cursor

                //<code>ResultSet</code>最初指向第一行
                Bundle bundle = new Bundle();
                while (rs.next()) {
                    bundle.clear();
                    bundle.putString("username", rs.getString("username"));
                    bundle.putString("userpass", rs.getString("userpass"));
                    Message msg = new Message();
                    msg.setData(bundle);
                    myHandler.sendMessage(msg);
                }

                rs.close();
                stmt.close();
            } catch (SQLException e) {

            } finally {
                if (con1 != null)
                    try {
                        con1.close();
                    } catch (SQLException e) {
                    }
            }
        }
    };*/
/*    public class MyThread extends Thread {
        @Override
        public void run() {
            //用于提交数据的client
            HttpClient client = new DefaultHttpClient();
            //这是提交的服务端地址
            String Urlpath = "http://" + IP + "/Login";
            //采用的是Post方式进行数据的提交
            HttpPost post = new HttpPost(Urlpath);
            List<NameValuePair> paramsList = new ArrayList<NameValuePair>();

            //这里就是提交的数据,你在服务端就可以通过request.getParameter("字段名称")
            paramsList.add(new BasicNameValuePair("id", uniqueId));
            paramsList.add(new BasicNameValuePair("her", HeartRate + ""));
            paramsList.add(new BasicNameValuePair("time", HRtime+""));

            Log.i(TAG, "Gson  str" + paramsList);

            try {
                post.setEntity(new UrlEncodedFormEntity(paramsList,
                        HTTP.UTF_8));
                Log.i(TAG, "Gson  str" + paramsList);

                HttpResponse response = client.execute(post);
                if (response.getStatusLine().getStatusCode() == 200) {
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    InputStream in = response.getEntity()
                            .getContent();
                    byte[] data = new byte[4096];
                    int count = -1;
                    while ((count = in.read(data, 0, 4096)) != -1)
                        outStream.write(data, 0, count);
                    data = null;
                    //这是服务端返回的数据
                    String content = new String(outStream.toByteArray(), "utf-8");
                    Log.d(content,"+++++++++++++++++++++++++++++++++");
                }
            } catch (Exception ex) {
            }
        }
    }*/


}
