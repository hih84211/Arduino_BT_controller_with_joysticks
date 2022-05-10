package com.example.arduino_rc_vehicle;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Objects;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class JoyStick extends AppCompatActivity
{
    private String debug = "Direction debug";    // Android device 中系統logcat 的標籤(測試或除錯用途)
    private String Device;    // 藍芽連線Server端的藍芽實體位置
    private BluetoothController mController = null;
    private BroadcastReceiver BTreceiver = null;
    private boolean isConnected = true;
    private long lastSend_R = System.currentTimeMillis();;
    private long lastSend_L = System.currentTimeMillis();;

    private TextView mTextViewAngleLeft;
    private TextView mTextViewStrengthLeft;
    private TextView mTextViewAngleRight;
    private TextView mTextViewStrengthRight;
    private TextView mTextViewCoordinateRight;
    static class mHandler extends Handler    // 接收訊息(座標變更、藍芽連線狀況等訊息)的handler 類別
    {
        private final WeakReference<JoyStick> mactivity;

        mHandler(JoyStick activity)
        {
            mactivity = new WeakReference(activity);
        }

        @Override
        public void handleMessage(Message msg)    // Handler定義跟MainActivity差不多
        {
            super.handleMessage(msg);
            JoyStick activity = mactivity.get();

            switch (msg.what)
            {
                case Constants.STATE__RECONNECTED:
                    activity.setState(true);
                    break;
                case MessageConstants.MESSAGE_READ:    // 若有需要從Arduino 端接收訊息，以下程式碼請自取(不敢保證是否能運作)

                    /*try
                    {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                        recieveData = readMessage; //拼湊每次收到的字元成字串
                    }
                    catch (UnsupportedEncodingException e)
                    {
                        e.printStackTrace();
                    }
                    Toast.makeText(MainActivity.this, _recieveData, Toast.LENGTH_SHORT).show();
                    recieveText.setText(_recieveData);*/
                    break;
                case MessageConstants.MESSAGE_WRITE:
                    String instr = msg.obj.toString();
                    Log.d(activity.debug,"Handler:" + instr);
                    if (activity.mController != null)
                        activity.mController.sendInstructions(instr);
                    // Toast.makeText(activity, instr, Toast.LENGTH_SHORT).show();
                    break;
                case MessageConstants.MESSAGE_TOAST:
                    String error = msg.getData().getString("toast", "");
                    Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                    break;
                case MessageConstants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1)
                    {
                        case Constants.STATE_CONNECT_FAILED:
                            Toast.makeText(activity, "連線已斷開，請重新連接", Toast.LENGTH_SHORT).show();
                            //activity.finish(); 20190113
                            break;
                    }
                    break;
            }
        }
    }
    private final mHandler mHandler = new mHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_joy_stick);
        Bundle bundle =this.getIntent().getExtras();
        Device = Objects.requireNonNull(bundle).getString("device");
        if(!"".equals(Device))    // if判斷式是為了後門而存在的
        {
            mController = BluetoothController.getInstance(mHandler);

            BTreceiver = new BroadcastReceiver()    //實作廣播接收器偵測藍芽連線狀況，如果斷線就會呼叫BluetoothController中的connectionLost()方法
            {
                @Override
                public void onReceive(Context context, Intent intent)
                {
                    setState(false);
                    if(mController != null)
                        mController.connectionLost();
                }
            };
            if(mController != null)
            {
                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                registerReceiver(BTreceiver, filter);
            }

        }
        mTextViewAngleLeft = (TextView) findViewById(R.id.textView_angle_left);
        mTextViewStrengthLeft = (TextView) findViewById(R.id.textView_strength_left);
        mTextViewCoordinateRight = findViewById(R.id.textView_coordinate_right);


        JoystickView joystickLeft = (JoystickView) findViewById(R.id.joystickView_left);
        joystickLeft.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength)
            {
                long current = System.currentTimeMillis();
                if((current-lastSend_L)>15)
                {
                    lastSend_L = current;
                    int msg = (-joystickLeft.getNormalizedY() + 100)/2;
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, (char)msg).sendToTarget();
                }

                mTextViewAngleLeft.setText(angle + "°");
                mTextViewStrengthLeft.setText(strength + "%");

            }
        });


        mTextViewAngleRight = (TextView) findViewById(R.id.textView_angle_right);
        mTextViewStrengthRight = (TextView) findViewById(R.id.textView_strength_right);
        final JoystickView joystickRight = (JoystickView) findViewById(R.id.joystickView_right);
        joystickRight.setOnMoveListener(new JoystickView.OnMoveListener() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onMove(int angle, int strength)
            {
                long current = System.currentTimeMillis();
                if((current-lastSend_R)>15)
                {
                    lastSend_R = current;
                    int msg = (-joystickRight.getNormalizedY() + 250)/2;
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, (char)msg).sendToTarget();
                }
                mTextViewAngleRight.setText(angle + "°");
                mTextViewStrengthRight.setText(strength + "%");
                mTextViewCoordinateRight.setText(
                        String.format("x%03d:y%03d",
                                joystickRight.getNormalizedX(),
                                -joystickRight.getNormalizedY()+300)
                );
            }
        });
    }

    public synchronized void setState(boolean state)    // isConnected 的set 方法
    {
        isConnected = state;
    }
}