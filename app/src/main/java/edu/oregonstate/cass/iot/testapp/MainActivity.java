package edu.oregonstate.cass.iot.testapp;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.alljoyn.bus.AboutObjectDescription;
import org.alljoyn.bus.AboutProxy;
import org.alljoyn.bus.BusException;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity implements edu.oregonstate.cass.iot.testapp.Observable{

//--------------//Globals//-------------------//
    private static final String TAG = "testapp"; //Tag for messages logged by this class
    public List<AboutProxy> aboutProxyList = new ArrayList<>(); //List of About Proxy objects for showing detailed info later
    private ListView aboutList; //List of About Object names to be displayed in the UI
    private ArrayAdapter<String> aboutListAdapter;
    public static MainActivity activity; //pointer to allow global access to this activity's methods
    public static String PACKAGE_NAME; //Unique bus name
    ComponentName mRunningService = null; //Keep track of the background service so we can kill it on quit().



//--------------//Builtins//-------------------//
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate Successful");
        PACKAGE_NAME = getApplicationContext().getPackageName();
        activity = this;
        Intent intent = new Intent(this, BusService.class);
        mRunningService = startService(intent);
        if (mRunningService == null) {
            Log.i(TAG, "onCreate(): failed to startService()");
        }

        aboutList = (ListView)findViewById(R.id.aboutList);
        aboutListAdapter = new ArrayAdapter<String>(this, android.R.layout.test_list_item);
        aboutList.setAdapter(aboutListAdapter);

        aboutList.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final ListView aboutDetail = (ListView) findViewById(R.id.aboutDetail);
                ArrayAdapter<AboutObjectDescription> aboutDetailAdapter = new ArrayAdapter<AboutObjectDescription>(activity, android.R.layout.test_list_item);
                aboutDetail.setAdapter(aboutDetailAdapter);

                try {
                    AboutObjectDescription[] aboutInfo = ((AboutProxy) parent.getItemAtPosition(position)).getObjectDescription();
                    for (AboutObjectDescription field : aboutInfo) {
                        aboutDetailAdapter.add(field);
                    }
                } catch (BusException e) {
                    e.printStackTrace();
                }
                setContentView(R.layout.about_detail);
            }
        });

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        if (id == R.id.action_quit) {
            finish();
            System.exit(0);
        }

        return super.onOptionsItemSelected(item);
    }



//--------------//About Name Management//-------------------//
    /**
     * Call to update the UI element that contains the names of all About
     * objects discovered so far.
     */
    public void addFoundAbout(String name){
        aboutListAdapter.add(name);
    }
    /**
     * Call to remove names from the UI element that contains all About
     * object names discovered so far.
     */
    public void removeFoundAbout(String name){
        aboutListAdapter.remove(name);
    }



//--------------//Observer Stuff//-------------------//
    /**
     * The observers list is the list of all objects that have registered with
     * us as observers in order to get notifications of interesting events.
     */
    private List<edu.oregonstate.cass.iot.testapp.Observer> mObservers = new ArrayList<edu.oregonstate.cass.iot.testapp.Observer>();
    /**
     * This object is really the model of a model-view-controller architecture.
     * The observer/observed design pattern is used to notify view-controller
     * objects when the model has changed.  The observed object is this object,
     * the model.  Observers correspond to the view-controllers which in this
     * case are the Android Activities (corresponding to the use tab and the
     * hsot tab) and the Android Service that does all of the AllJoyn work.
     * When the model (this object) wants to notify its observers that some
     * interesting event has happened, it calles here and provides an object
     * that identifies what has happened.  To keep things obvious, we pass a
     * descriptive string which is then sent to all observers.  They can decide
     * to act or not based on the content of the string.
     */
    private void notifyObservers(Object arg) {
        Log.i(TAG, "notifyObservers(" + arg + ")");
        for (edu.oregonstate.cass.iot.testapp.Observer obs : mObservers) {
            Log.i(TAG, "notify observer = " + obs);
            obs.update(this, arg);
        }
    }
    /**
     * This object is really the model of a model-view-controller architecture.
     * The observer/observed design pattern is used to notify view-controller
     * objects when the model has changed.  The observed object is this object,
     * the model.  Observers correspond to the view-controllers which in this
     * case are the Android Activities (corresponding to the use tab and the
     * hsot tab) and the Android Service that does all of the AllJoyn work.
     * When an observer wants to register for change notifications, it calls
     * here.
     */
    public synchronized void addObserver(edu.oregonstate.cass.iot.testapp.Observer obs) {
        Log.i(TAG, "addObserver(" + obs + ")");
        if (mObservers.indexOf(obs) < 0) {
            mObservers.add(obs);
        }
    }
    /**
     * When an observer wants to unregister to stop receiving change
     * notifications, it calls here.
     */
    public synchronized void deleteObserver(edu.oregonstate.cass.iot.testapp.Observer obs) {
        Log.i(TAG, "deleteObserver(" + obs + ")");
        mObservers.remove(obs);
    }



//--------------//Error Handling//-------------------//
    /**
     * This is the method that AllJoyn Service calls to tell us that an error
     * has happened.  We are provided a module, which corresponds to the high-
     * level "hunk" of code where the error happened, and a descriptive string
     * that we do not interpret.
     *
     * We expect the user interface code to sort out the best activity to tell
     * the user about the error (by calling getErrorModule) and then to call in
     * to get the string.
     */
    public synchronized void alljoynError(Module m, String s) {
        mModule = m;
        mErrorString = s;
        notifyObservers(ALLJOYN_ERROR_EVENT);
    }
    /**
     * Return the high-level module that caught the last AllJoyn error.
     */
    public Module getErrorModule() {
        return mModule;
    }
    /**
     * The high-level module that caught the last AllJoyn error.
     */
    private Module mModule = Module.NONE;
    /**
     * Enumeration of the high-level moudules in the system.  There is one
     * value per module.
     */
    public static enum Module {
        NONE,
        GENERAL,
        USE,
        HOST
    }
    /**
     * Return the error string stored when the last AllJoyn error happened.
     */
    public String getErrorString() {
        return mErrorString;
    }



//--------------//Application Lifecycle Handling//-------------------//
    /**
     * Since our application is "rooted" in this class derived from Appliation
     * and we have a long-running service, we can't just call finish in one of
     * the Activities.  We have to orchestrate it from here.  We send an event
     * notification out to all of our obsservers which tells them to exit.
     *
     * Note that as a result of the notification, all of the observers will
     * stop -- as they should.  One of the things that will stop is the AllJoyn
     * Service.  Notice that it is started in the onCreate() method of the
     * Application.  As noted in the Android documentation, the Application
     * class never gets torn down, nor does it provide a way to tear itself
     * down.  Thus, if the Chat application is ever run again, we need to have
     * a way of detecting the case where it is "re-run" and then "re-start"
     * the service.
     */
    public void quit() {
        notifyObservers(APPLICATION_QUIT_EVENT);
        mRunningService = null;
    }
    /**
     * Not sure how to handle this for an activity, so the method used in the
     * application will have to do for now.
     *
     * Application components call this method to indicate that they are alive
     * and may have need of the AllJoyn Service.  This is required because the
     * Android Application class doesn't have an end to its lifecycle other
     * than through "kill -9".  See quit().
     */
    public void checkin() {
        Log.i(TAG, "checkin()");
        if (mRunningService == null) {
            Log.i(TAG, "checkin():  Starting the AllJoynService");
            Intent intent = new Intent(this, BusService.class);
            mRunningService = startService(intent);
            if (mRunningService == null) {
                Log.i(TAG, "checkin(): failed to startService()");
            }
        }
    }



//--------------//Status Strings//-------------------//
    /**
     * The string representing the last AllJoyn error that happened in the
     * AllJoyn Service.
     */
    private String mErrorString = "ER_OK";
    /**
     * The object we use in notifications to indicate that user has requested
     * that we join a channel in the "use" tab.
     */
    public static final String JOIN_SESSION_EVENT = "USE_JOIN_CHANNEL_EVENT";
    /**
     * The object we use in notifications to indicate that user has requested
     * that we leave a channel in the "use" tab.
     */
    public static final String LEAVE_SESSION_EVENT = "USE_LEAVE_CHANNEL_EVENT";
    /**
     * Pretty self explanitory. Background service is constant, so we have to manually
     * kill it when we want to exit.
     */
    public static final String APPLICATION_QUIT_EVENT = "APPLICATION_QUIT_EVENT";
    /**
     * The object we use in notifications to indicate that an AllJoyn error has
     * happened.
     */
    public static final String ALLJOYN_ERROR_EVENT = "ALLJOYN_ERROR_EVENT";
}
