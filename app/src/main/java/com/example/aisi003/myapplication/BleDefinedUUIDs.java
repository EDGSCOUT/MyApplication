package com.example.aisi003.myapplication;

import java.util.UUID;

/**
 * Created by aisi003 on 2018/3/7.
 */

public class BleDefinedUUIDs {
    public static final int UPDATE_HEART_RATE = 11;




    public static class Characteristic {
        //读写数据类型用到的uuid
        public final static String Notification_CHARACTERISTIC_UUID = "00002a37-0000-1000-8000-00805f9b34fb";
        public final static String Write_CHARACTERISTIC_UUID = "00002a08-0000-1000-8000-00805f9b34fb";
        public final static String BATTERY_LEVEL_CHARACTERISTIC_UUID = "00002a39-0000-1000-8000-00805f9b34fb";
    }


}
