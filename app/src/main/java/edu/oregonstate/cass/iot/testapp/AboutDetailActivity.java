package edu.oregonstate.cass.iot.testapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import org.alljoyn.bus.Variant;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by iot on 10/19/15.
 */
public class AboutDetailActivity extends AppCompatActivity{
    public MainActivity activity = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_detail);
        Activity activity = (MainActivity).activity;

        Intent intent = getIntent();
        Map<String, Variant> tempData = (HashMap)intent.getExtras().get("AboutInfo");

        TextView deviceName = (TextView) findViewById(R.id.deviceName);
        TextView deviceID = (TextView) findViewById(R.id.deviceID);
        TextView modelNum = (TextView) findViewById(R.id.modelNum);
        TextView hwVer = (TextView) findViewById(R.id.hwVersion);
        TextView appName = (TextView) findViewById(R.id.appName);
        TextView appID = (TextView) findViewById(R.id.appID);
        TextView swVer = (TextView) findViewById(R.id.swVersion);
        TextView ajnVer = (TextView) findViewById(R.id.ajnVersion);
        TextView description = (TextView) findViewById(R.id.description);
        TextView maker_date = (TextView) findViewById(R.id.maker_date);
        TextView supportURL = (TextView) findViewById(R.id.supportURL);
        TextView language = (TextView) findViewById(R.id.language);


        String temp;
        for (Map.Entry<String, Variant> entry : tempData.entrySet()) {
            temp = entry.getKey();
            Object obj = entry.getValue();
            switch (temp) {
                case "AppId":
                    appID.setText((int)obj);
                    break;
                case "DefaultLanguage":
                    language.setText((String)obj);
                    break;
                case "DeviceName":
                    deviceName.setText((String)obj);
                    break;
                case "DeviceId":
                    deviceID.setText((String)obj);
                    break;
                case "AppName":
                    appName.setText((String)obj);
                    break;
                case "Manufacturer":
                    maker_date.setText((String)obj);
                    break;
                case "ModelNumber":
                    modelNum.setText((String)obj);
                    break;
                case "SupportedLanguages":
                    language.setText((String)obj + language);
                    break;
                case "Description":
                    description.setText((String)obj);
                    break;
                case "DateofManufacture":
                    maker_date.setText(maker_date + (String)obj);
                    break;
                case "SoftwareVersion":
                    swVer.setText((String)obj);
                    break;
                case "AJSoftwareVersion":
                    ajnVer.setText((String)obj);
                    break;
                case "HardwareVersion":
                    hwVer.setText((String)obj);
                    break;
                case "SupportUrl":
                    supportURL.setText((String)obj);
                    break;
                default:
                    break;
            }
        }
    }
}
