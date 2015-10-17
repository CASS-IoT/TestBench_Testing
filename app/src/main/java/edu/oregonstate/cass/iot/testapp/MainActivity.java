package edu.oregonstate.cass.iot.testapp;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.alljoyn.bus.AboutListener;
import org.alljoyn.bus.AboutObjectDescription;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.Variant;

import java.util.Map;


public class MainActivity extends AppCompatActivity{
//--------------//Globals//-------------------//
    private static final String TAG = "testapp"; //Tag for messages logged by this class
    private ListView aboutList; //List of About Object names to be displayed in the UI
    private ArrayAdapter<String> aboutListAdapter;
    public static MainActivity activity;
    public static String PACKAGE_NAME; //Unique bus name
    public static String NAME_PREFIX = "org.alljoyn.About";
    public static String[] ABOUT_NAMES = {"org.alljoyn.*"};



//--------------//Builtins//-------------------//
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate Successful");
        PACKAGE_NAME = getApplicationContext().getPackageName();
        activity = this;

        aboutList = (ListView)findViewById(R.id.aboutList);
        aboutListAdapter = new ArrayAdapter<String>(this, android.R.layout.test_list_item);
        aboutList.setAdapter(aboutListAdapter);

        mBus.connect();
        mBus.registerBusListener(mBusListener);
        mBus.registerAboutListener(mTestListener);

        mBus.findAdvertisedName(NAME_PREFIX);
        mBus.whoImplements(null);

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



//--------------//Bus Object Instances and Constants//-------------------//
    /**
     * The bus attachment is the object that provides AllJoyn services to Java
     * clients.  Pretty much all communiation with AllJoyn is going to go through
     * this obejct.
     */
    private BusAttachment mBus  = new BusAttachment(PACKAGE_NAME, BusAttachment.RemoteMessage.Receive);
    /**
     * An instance of an AllJoyn bus listener that knows what to do with
     * foundAdvertisedName and lostAdvertisedName notifications.  Although
     * we often use the anonymous class idiom when talking to AllJoyn, the
     * bus listener works slightly differently and it is better to use an
     * explicitly declared class in this case.
     */
    private AboutBusListener mBusListener = new AboutBusListener();
    /**
     * Instance of the custom About Listener class
     */
    private TestAboutListener mTestListener = new TestAboutListener();



//--------------//Bus Attachment and Listeners//-------------------//
    /**
     * Custom version of the BusListener that is only set up to handle About Advertisements
     */
    class AboutBusListener extends BusListener {

        public void foundAdvertisedName(final String name, final short transport, final String namePrefix){
            Log.i(TAG, "BusListener(): Found new Advertised Name: "+name);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    aboutListAdapter.add(name + " - BusListener reported name");
                    aboutListAdapter.add(transport + " - BusListener reported transport");
                    aboutListAdapter.add(namePrefix + " - BusListener reported namePrefix");
                }
            });
        }
        public void lostAdvertisedName(final String name, final short transport, final String namePrefix){
            Log.i(TAG, "BusListener(): Lost Advertised Name: "+name);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    aboutListAdapter.remove(name + " - BusListener reported name");
                    aboutListAdapter.remove(transport + " - BusListener reported transport");
                    aboutListAdapter.remove(namePrefix + " - BusListener reported namePrefix");
                }
            });
        }
    }
    /**
     * AboutListener that adds the announced 'busName' to the global list of About announcement
     * names via the MainActivity addFoundAbout() method. It also creates a new AboutProxy
     * object that references the found About interface and adds it to a list of AboutProxy
     * objects so we can get the contents of the About Announcement later when the user
     * selects that particular announcement.
     */
    public class TestAboutListener implements AboutListener {
        public void announced(final String busName, int version, short sessionPort, AboutObjectDescription[] aboutObjectDescriptions, Map<String, Variant> aboutData) {
            Log.i(TAG, "AboutListener(): Found About Interface: "+busName);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    aboutListAdapter.add(busName + " aboutlistener");
                }
            });
        }
    }

//--------------//Misc.//-------------------//
    /**
     * Manually load in the NDK library so we can use the methods defined inside.
     */
    static {
        Log.i(TAG, "System.loadLibrary(\"alljoyn_java\")");
        System.loadLibrary("alljoyn_java");
    }


}
