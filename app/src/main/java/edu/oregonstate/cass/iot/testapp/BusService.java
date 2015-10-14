package edu.oregonstate.cass.iot.testapp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.alljoyn.bus.AboutListener;
import org.alljoyn.bus.AboutObjectDescription;
import org.alljoyn.bus.AboutProxy;
import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.OnJoinSessionListener;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.SignalEmitter;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.Variant;
import org.alljoyn.bus.annotation.BusSignalHandler;

import java.util.Map;

/**
 * Created by iot on 10/12/15.
 */
public class BusService extends Service implements Observer{

//--------------//Globals//-------------------//
    private MainActivity mActivity = MainActivity.activity;
    private static final String TAG = "testApp.Service";
    /**
     * The session identifier of the "host" session that the application
     * provides for remote devices.  Set to -1 if not connected.
     */
    int mHostSessionId = -1;
    /**
     * A flag indicating that the application has joined a chat channel that
     * it is hosting.  See the long comment in doJoinSession() for a
     * description of this rather non-intuitively complicated case.
     */
    boolean mJoinedToSelf = false;
    /**
     * The session identifier of the "use" session that the application
     * uses to talk to remote instances.  Set to -1 if not connectecd.
     */
    int mUseSessionId = -1;



//--------------//Builtins//-------------------//
    /**
     * Our onCreate() method is called by the Android appliation framework
     * when the service is first created.  We spin up a background thread
     * to handle any long-lived requests (pretty much all AllJoyn calls that
     * involve communication with remote processes) that need to be done and
     * insinuate ourselves into the list of observers of the model so we can
     * get event notifications.
     */
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        startBusThread();
        //mActivity = MainActivity.activity;

        CharSequence title = "AllJoyn Bus Background Service";
        CharSequence message = "Background bus management service.";
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this);
        Notification notification = builder.setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.icon).setTicker(null).setWhen(System.currentTimeMillis())
                .setAutoCancel(false).setContentTitle(title)
                .setContentText(message).build();

        //Notification notification = new Notification(R.drawable.icon, null, System.currentTimeMillis());
        //notification.setLatestEventInfo(this, title, message, pendingIntent);
        //notification.flags |= Notification.DEFAULT_SOUND | Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;

        Log.i(TAG, "onCreate(): startForeground()");
        startForeground(NOTIFICATION_ID, notification);

        /*
         * We have an AllJoyn handler thread running at this time, so take
         * advantage of the fact to get connected to the bus and start finding
         * remote channel instances in the background while the rest of the app
         * is starting up.
         */
        mBackgroundHandler.connect();
        mBackgroundHandler.startDiscovery();
    }
    /**
     * We don't use the bindery to communiate between any client and this
     * service so we return null.
     */
    public IBinder onBind(Intent intent){
        Log.i(TAG, "onBind()");
        return null;
    }
    /**
     * This is the event handler for the Observable/Observed design pattern.
     * Whenever an interesting event happens in our appliation, the Model (the
     * source of the event) notifies registered observers, resulting in this
     * method being called since we registered as an Observer in onCreate().
     *
     * This method will be called in the context of the Model, which is, in
     * turn the context of an event source.  This will either be the single
     * Android application framework thread if the source is one of the
     * Activities of the application or the Service.  It could also be in the
     * context of the Service background thread.  Since the Android Application
     * framework is a fundamentally single threaded thing, we avoid multithread
     * issues and deadlocks by immediately getting this event into a separate
     * execution in the context of the Service message pump.
     *
     * We do this by taking the event from the calling component and queueing
     * it onto a "handler" in our Service and returning to the caller.  When
     * the calling componenet finishes what ever caused the event notification,
     * we expect the Android application framework to notice our pending
     * message and run our handler in the context of the single application
     * thread.
     *
     * In reality, both events are executed in the context of the single
     * Android thread.
     */
    public synchronized void update(Observable o, Object arg) {
        Log.i(TAG, "update(" + arg + ")");
        String qualifier = (String)arg;

        if (qualifier.equals(mActivity.APPLICATION_QUIT_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_APPLICATION_QUIT_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(mActivity.JOIN_SESSION_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_JOIN_SESSION_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(mActivity.LEAVE_SESSION_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_LEAVE_SESSION_EVENT);
            mHandler.sendMessage(message);
        }
        /*  -------------May be necessary in the future, leave for now -------------------------
        if (qualifier.equals(MainActivity.activity.HOST_INIT_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_HOST_INIT_CHANNEL_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(MainActivity.activity.HOST_START_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_HOST_START_CHANNEL_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(MainActivity.activity.HOST_STOP_CHANNEL_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_HOST_STOP_CHANNEL_EVENT);
            mHandler.sendMessage(message);
        }

        if (qualifier.equals(MainActivity.activity.OUTBOUND_CHANGED_EVENT)) {
            Message message = mHandler.obtainMessage(HANDLE_OUTBOUND_CHANGED_EVENT);
            mHandler.sendMessage(message);
        }*/
    }



//--------------//Bus Object Instances and Constants//-------------------//
    /**
     * The bus attachment is the object that provides AllJoyn services to Java
     * clients.  Pretty much all communiation with AllJoyn is going to go through
     * this obejct.
     */
    private BusAttachment mBus  = new BusAttachment(MainActivity.PACKAGE_NAME, BusAttachment.RemoteMessage.Receive);
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
    /**
     * Enumeration of the states of the AllJoyn bus attachment.  This
     * lets us make a note to ourselves regarding where we are in the process
     * of preparing and tearing down the fundamental connection to the AllJoyn
     * bus.
     *
     * This should really be a more private think, but for the sample we want
     * to show the user the states we are running through.  Because we are
     * really making a data hiding exception, and because we trust ourselves,
     * we don't go to any effort to prevent the UI from changing our state out
     * from under us.
     *
     * There are separate variables describing the states of the client
     * ("use") and service ("host") pieces.
     */
    public static enum BusAttachmentState {
        DISCONNECTED,    /** The bus attachment is not connected to the AllJoyn bus */
        CONNECTED,        /** The  bus attachment is connected to the AllJoyn bus */
        DISCOVERING        /** The bus attachment is discovering remote attachments hosting chat channels */
    }
    /**
     * The state of the AllJoyn bus attachment.
     */
    private BusAttachmentState mBusAttachmentState = BusAttachmentState.DISCONNECTED;
    /**
     * The signal handler for messages received from the AllJoyn bus.
     *
     * Since the messages sent on a chat channel will be sent using a bus
     * signal, we need to provide a signal handler to receive those signals.
     * This is it.  Note that the name of the signal handler has the first
     * letter capitalized to conform with the DBus convention for signal
     * handler names.
     */
    /*@BusSignalHandler(iface = "org.alljoyn.bus.samples.chat", signal = "Chat")



//--------------//Bus Attachment and Listeners//-------------------//
    /**
     * Custom version of the BusListener that is only set up to handle About Advertisements
     */
    class AboutBusListener extends BusListener {
        public void foundAdvertisedName(String name, short transport, String namePrefix){
            Log.i(TAG, "BusListener(): Found new Advertised Name");
            //wknListAdapter.add(name);
        }
        public void lostAdvertisedName(String name, short transport, String namePrefix){
            Log.i(TAG, "BusListener(): Lost Advertised Name");
            //wknListAdapter.remove(name);
        }
    }
    /**
     * AboutListener that adds the announced 'busName' to the global list of About announcement
     * names via the MainActivity addFoundAbout() method. It also creates a new AboutProxy
     * object that references the found About interface and adds it to a list of AboutProxy
     * objects so we can get the contents of the About Announcement later when the user
     * selects that particular announcement.
     */
    class TestAboutListener implements AboutListener {
        public void announced(final String busName, int version, short sessionPort, AboutObjectDescription[] aboutObjectDescriptions, Map<String, Variant> aboutData) {
            Log.i(TAG, "AboutListener(): New Bus Found");
            Mutable.IntegerValue sessionId = new Mutable.IntegerValue();
            final AboutProxy newAbout = new AboutProxy(mBus, busName, sessionId.value);
            mActivity.addFoundAbout(busName);
            mActivity.aboutProxyList.add(newAbout);
            SessionOpts sessionOpts = new SessionOpts();
            aboutUpdateListener mAboutUpdateListener = new aboutUpdateListener();
            mBus.joinSession(busName, sessionPort, sessionOpts, new SessionListener() {
                /**
                 * This method is called when the last remote participant in the
                 * chat session leaves for some reason and we no longer have anyone
                 * to chat with.
                 *
                 * In the class documentation for the BusListener note that it is a
                 * requirement for this method to be multithread safe.  This is
                 * accomplished by the use of a monitor on the ChatApplication as
                 * exemplified by the synchronized attribute of the removeFoundChannel
                 * method there.
                 */
                public void sessionLost(int sessionId, int reason) {
                    Log.i(TAG, "BusListener.sessionLost(sessionId=" + sessionId + ",reason=" + reason + ")");
                    mActivity.aboutProxyList.remove(newAbout);
                    mActivity.removeFoundAbout(busName);
                }
            }, mAboutUpdateListener, this);

        }
    }
    /**
     * Listener that is called by the bus when a session is successfully joined.
     * We'll use this to indicate whether or not an About Announcement can be
     * read or not.
     */
    class aboutUpdateListener extends OnJoinSessionListener {
        @Override
        public void onJoinSession(Status status, int sessionId, SessionOpts opts, Object context){
            /**
             * Need to implement code that somehow identifies a particular AboutProxy object as being connected or not
             */

            //AboutProxy newAboutData = new AboutProxy(mBus, tempBusName, sessionId);
            //aboutListAdapter.add(newAboutData);
        }
    }



//--------------//Event Handlers//-------------------//
    private BackgroundHandler mBackgroundHandler = null;
    /**
     * This is the Android Service message handler.  It runs in the context of the
     * main Android Service thread, which is also shared with Activities since
     * Android is a fundamentally single-threaded system.
     *
     * The important thing for us is to note that this thread cannot be blocked for
     * a significant amount of time or we risk the dreaded "force close" message.
     * We can run relatively short-lived operations here, but we need to run our
     * distributed system calls in a background thread.
     *
     * This handler serves translates from UI-related events into AllJoyn events
     * and decides whether functions can be handled in the context of the
     * Android main thread or if they must be dispatched to a background thread
     * which can take as much time as needed to accomplish a task.
     */
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLE_APPLICATION_QUIT_EVENT: //Close the application cleanly
                {
                    Log.i(TAG, "mHandler.handleMessage(): APPLICATION_QUIT_EVENT");
                    mBackgroundHandler.leaveSession();
                    mBackgroundHandler.cancelAdvertise();
                    mBackgroundHandler.unbindSession();
                    mBackgroundHandler.releaseName();
                    mBackgroundHandler.exit();
                    stopSelf();
                }
                break;
                case HANDLE_JOIN_SESSION_EVENT: //Synchronous join session
                {
                    Log.i(TAG, "mHandler.handleMessage(): USE_JOIN_CHANNEL_EVENT");
                    mBackgroundHandler.joinSession();
                }
                break;
                case HANDLE_LEAVE_SESSION_EVENT: //Gracefully close session
                {
                    Log.i(TAG, "mHandler.handleMessage(): USE_LEAVE_CHANNEL_EVENT");
                    mBackgroundHandler.leaveSession();
                }
                break;
                default:
                    break;
            }
        }
    };
    /**
     * This class contains the actions that are performed for each message that can be
     * handled by the Handler class.
     */
    private final class BackgroundHandler extends Handler {
        public BackgroundHandler(Looper looper) {
            super(looper);
        }

        /**
         * Exit the background handler thread.  This will be the last message
         * executed by an instance of the handler.
         */
        public void exit() {
            Log.i(TAG, "mBackgroundHandler.exit()");
            Message msg = mBackgroundHandler.obtainMessage(EXIT);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Connect the application to the Alljoyn bus attachment.  We expect
         * this method to be called in the context of the main Service thread.
         * All this method does is to dispatch a corresponding method in the
         * context of the service worker thread.
         */
        public void connect() {
            Log.i(TAG, "mBackgroundHandler.connect()");
            Message msg = mBackgroundHandler.obtainMessage(CONNECT);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Disonnect the application from the Alljoyn bus attachment.  We
         * expect this method to be called in the context of the main Service
         * thread.  All this method does is to dispatch a corresponding method
         * in the context of the service worker thread.
         */
        public void disconnect() {
            Log.i(TAG, "mBackgroundHandler.disconnect()");
            Message msg = mBackgroundHandler.obtainMessage(DISCONNECT);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Start discovering remote instances of the application.  We expect
         * this method to be called in the context of the main Service thread.
         * All this method does is to dispatch a corresponding method in the
         * context of the service worker thread.
         */
        public void startDiscovery() {
            Log.i(TAG, "mBackgroundHandler.startDiscovery()");
            Message msg = mBackgroundHandler.obtainMessage(START_DISCOVERY);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * Stop discovering remote instances of the application.  We expect
         * this method to be called in the context of the main Service thread.
         * All this method does is to dispatch a corresponding method in the
         * context of the service worker thread.
         */
        public void cancelDiscovery() {
            Log.i(TAG, "mBackgroundHandler.stopDiscovery()");
            Message msg = mBackgroundHandler.obtainMessage(CANCEL_DISCOVERY);
            mBackgroundHandler.sendMessage(msg);
        }

        public void requestName() {
            Log.i(TAG, "mBackgroundHandler.requestName()");
            Message msg = mBackgroundHandler.obtainMessage(REQUEST_NAME);
            mBackgroundHandler.sendMessage(msg);
        }

        public void releaseName() {
            Log.i(TAG, "mBackgroundHandler.releaseName()");
            Message msg = mBackgroundHandler.obtainMessage(RELEASE_NAME);
            mBackgroundHandler.sendMessage(msg);
        }

        public void bindSession() {
            Log.i(TAG, "mBackgroundHandler.bindSession()");
            Message msg = mBackgroundHandler.obtainMessage(BIND_SESSION);
            mBackgroundHandler.sendMessage(msg);
        }

        public void unbindSession() {
            Log.i(TAG, "mBackgroundHandler.unbindSession()");
            Message msg = mBackgroundHandler.obtainMessage(UNBIND_SESSION);
            mBackgroundHandler.sendMessage(msg);
        }

        public void advertise() {
            Log.i(TAG, "mBackgroundHandler.advertise()");
            Message msg = mBackgroundHandler.obtainMessage(ADVERTISE);
            mBackgroundHandler.sendMessage(msg);
        }

        public void cancelAdvertise() {
            Log.i(TAG, "mBackgroundHandler.cancelAdvertise()");
            Message msg = mBackgroundHandler.obtainMessage(CANCEL_ADVERTISE);
            mBackgroundHandler.sendMessage(msg);
        }

        public void joinSession() {
            Log.i(TAG, "mBackgroundHandler.joinSession()");
            Message msg = mBackgroundHandler.obtainMessage(JOIN_SESSION);
            mBackgroundHandler.sendMessage(msg);
        }

        public void leaveSession() {
            Log.i(TAG, "mBackgroundHandler.leaveSession()");
            Message msg = mBackgroundHandler.obtainMessage(LEAVE_SESSION);
            mBackgroundHandler.sendMessage(msg);
        }

        public void sendMessages() {
            Log.i(TAG, "mBackgroundHandler.sendMessages()");
            Message msg = mBackgroundHandler.obtainMessage(SEND_MESSAGES);
            mBackgroundHandler.sendMessage(msg);
        }

        /**
         * The message handler for the worker thread that handles background
         * tasks for the AllJoyn bus.
         */
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CONNECT:
                    doConnect();
                    break;
                case DISCONNECT:
                    doDisconnect();
                    break;
                case START_DISCOVERY:
                    doStartDiscovery();
                    break;
                case CANCEL_DISCOVERY:
                    doStopDiscovery();
                    break;
                case JOIN_SESSION:
                    doJoinSession();
                    break;
                case LEAVE_SESSION:
                    doLeaveSession();
                    break;
                case EXIT:
                    getLooper().quit();
                    break;
                default:
                    break;
            }
        }
    }



//--------------//Lifecycle Management//-------------------//
    /**
     * Since basically our whole reason for being is to spin up a thread to
     * handle long-lived remote operations, we provide this method to do so.
     */
    private void startBusThread() {
        HandlerThread busThread = new HandlerThread("BackgroundHandler");
        busThread.start();
        mBackgroundHandler = new BackgroundHandler(busThread.getLooper());
    }
    /**
     * When Android decides that our Service is no longer needed, we need to
     * tear down the thread that is servicing our long-lived remote operations.
     * This method does so.
     */
    private void stopBusThread() {
        mBackgroundHandler.exit();
    }



//--------------//Handler Methods//-------------------//
    /**
     * Implementation of the functionality related to connecting our app
     * to the AllJoyn bus.  We expect that this method will only be called in
     * the context of the AllJoyn bus handler thread; and while we are in the
     * DISCONNECTED state.
     */
    private void doConnect() {
        Log.i(TAG, "doConnect()");
        org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
        assert(mBusAttachmentState == BusAttachmentState.DISCONNECTED);
        mBus.useOSLogging(true);
        mBus.setDebugLevel("ALLJOYN_JAVA", 7);
        mBus.registerBusListener(mBusListener);
        mBus.registerAboutListener(mTestListener);

        /*
         * To make a service available to other AllJoyn peers, first
         * register a BusObject with the BusAttachment at a specific
         * object path.  Our service is implemented by the ChatService
         * BusObject found at the "/chatService" object path.
         */
        //Status status = mBus.registerBusObject(mChatService, OBJECT_PATH);
        //if (Status.OK != status) {
        //   mChatApplication.alljoynError(ChatApplication.Module.HOST, "Unable to register the chat bus object: (" + status + ")");
        //    return;
        //}

        Status status = mBus.connect();
        if (status != Status.OK) {
            mActivity.alljoynError(MainActivity.Module.GENERAL, "Unable to connect to the bus: (" + status + ")");
            return;
        }

        status = mBus.registerSignalHandlers(this);
        if (status != Status.OK) {
            mActivity.alljoynError(MainActivity.Module.GENERAL, "Unable to register signal handlers: (" + status + ")");
            return;
        }

        mBusAttachmentState = BusAttachmentState.CONNECTED;
    }
    /**
     * Implementation of the functionality related to disconnecting our app
     * from the AllJoyn bus.  We expect that this method will only be called
     * in the context of the AllJoyn bus handler thread.  We expect that this
     * method will only be called in the context of the AllJoyn bus handler
     * thread; and while we are in the CONNECTED state.
     */
    private boolean doDisconnect() {
        Log.i(TAG, "doDisonnect()");
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED);
        mBus.unregisterBusListener(mBusListener);
        mBus.disconnect();
        mBusAttachmentState = BusAttachmentState.DISCONNECTED;
        return true;
    }
    /**
     * Implementation of the functionality related to discovering remote apps
     * which are hosting chat channels.  We expect that this method will only
     * be called in the context of the AllJoyn bus handler thread; and while
     * we are in the CONNECTED state.  Since this is a core bit of functionalty
     * for the "use" side of the app, we always do this at startup.
     */
    private void doStartDiscovery() {
        Log.i(TAG, "doStartDiscovery()");
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED);
        Status status = mBus.findAdvertisedName(NAME_PREFIX);
        if (status == Status.OK) {
            mBusAttachmentState = BusAttachmentState.DISCOVERING;
            return;
        } else {
            mActivity.alljoynError(MainActivity.Module.USE, "Unable to start finding advertised names: (" + status + ")");
            return;
        }
    }
    /**
     * Implementation of the functionality related to stopping discovery of
     * remote apps which are hosting chat channels.
     */
    private void doStopDiscovery() {
        Log.i(TAG, "doStopDiscovery()");
        assert(mBusAttachmentState == BusAttachmentState.CONNECTED);
        mBus.cancelFindAdvertisedName(NAME_PREFIX);
        mBusAttachmentState = BusAttachmentState.CONNECTED;
    }
    /**
     * Implementation of the functionality related to joining an existing
     * local or remote session.
     */
    private void doJoinSession() {
        Log.i(TAG, "doJoinSession()");

        /*
         * There is a relatively non-intuitive behavior of multipoint sessions
         * that one needs to grok in order to understand the code below.  The
         * important thing to uderstand is that there can be only one endpoint
         * for a multipoint session in a particular bus attachment.  This
         * endpoint can be created explicitly by a call to joinSession() or
         * implicitly by a call to bindSessionPort().  An attempt to call
         * joinSession() on a session port we have created with bindSessionPort()
         * will result in an error.
         *
         * When we call bindSessionPort(), we do an implicit joinSession() and
         * thus signals (which correspond to our chat messages) will begin to
         * flow from the hosted chat channel as soon as we begin to host a
         * corresponding session.
         *
         * To achieve sane user interface behavior, we need to block those
         * signals from the implicit join done by the bind until our user joins
         * the bound chat channel.  If we do not do this, the chat messages
         * from the chat channel hosted by the application will appear in the
         * chat channel joined by the application.
         *
         * Since the messages flow automatically, we can accomplish this by
         * turning a filter on and off in the chat signal handler.  So if we
         * detect that we are hosting a channel, and we find that we want to
         * join the hosted channel we turn the filter off.
         *
         * We also need to be able to send chat messages to the hosted channel.
         * This means we need to point the mChatInterface at the session ID of
         * the hosted session.  There is another complexity here since the
         * hosted session doesn't exist until a remote session has joined.
         * This means that we don't have a session ID to use to create a
         * SignalEmitter until a remote device does a joinSession on our
         * hosted session.  This, in turn, means that we have to create the
         * SignalEmitter after we get a sessionJoined() callback in the
         * SessionPortListener passed into bindSessionPort().  We chose to
         * create the signal emitter for this case in the sessionJoined()
         * callback itself.  Note that this hosted channel signal emitter
         * must be distinct from one constructed for the usual joinSession
         * since a hosted channel may have a remote device do a join at any
         * time, even when we are joined to another session.  If they were
         * not separated, a remote join on the hosted session could redirect
         * messages from the joined session unexpectedly.
         *
         * So, to summarize, these next few lines handle a relatively complex
         * case.  When we host a chat channel, we do a bindSessionPort which
         * *enables* the creation of a session.  As soon as a remote device
         * joins the hosted chat channel, a session is actually created, and
         * the SessionPortListener sessionJoined() callback is fired.  At that
         * point, we create a separate SignalEmitter using the hosted session's
         * sessionId that we can use to send chat messages to the channel we
         * are hosting.  As soon as the session comes up, we begin receiving
         * chat messages from the session, so we need to filter them until the
         * user joins the hosted chat channel.  In a separate timeline, the
         * user can decide to join the chat channel she is hosting.  She can
         * do so either before or after the corresponding session has been
         * created as a result of a remote device joining the hosted session.
         * If she joins the hosted channel before the underlying session is
         * created, her chat messages will be discarded.  If she does so after
         * the underlying session is created, there will be a session emitter
         * waiting to use to send chat messages.  In either case, the signal
         * filter will be turned off in order to listen to remote chat
         * messages.
         */
        if (mHostChannelState != HostChannelState.IDLE) {
            if (mChatApplication.useGetChannelName().equals(mChatApplication.hostGetChannelName())) {
                mUseChannelState = UseChannelState.JOINED;
                mChatApplication.useSetChannelState(mUseChannelState);
                mJoinedToSelf = true;
                return;
            }
        }
        /*
         * We depend on the user interface and model to work together to provide
         * a reasonable name.
         */
        String wellKnownName = NAME_PREFIX + "." + mChatApplication.useGetChannelName();

        /*
         * Since we can act as the host of a channel, we know what the other
         * side is expecting to see.
         */
        short contactPort = CONTACT_PORT;
        SessionOpts sessionOpts = new SessionOpts(SessionOpts.TRAFFIC_MESSAGES, true, SessionOpts.PROXIMITY_ANY, SessionOpts.TRANSPORT_ANY);
        Mutable.IntegerValue sessionId = new Mutable.IntegerValue();

        Status status = mBus.joinSession(wellKnownName, contactPort, sessionId, sessionOpts, new SessionListener() {
            /**
             * This method is called when the last remote participant in the
             * chat session leaves for some reason and we no longer have anyone
             * to chat with.
             *
             * In the class documentation for the BusListener note that it is a
             * requirement for this method to be multithread safe.  This is
             * accomplished by the use of a monitor on the ChatApplication as
             * exemplified by the synchronized attribute of the removeFoundChannel
             * method there.
             */
            public void sessionLost(int sessionId, int reason) {
                Log.i(TAG, "BusListener.sessionLost(sessionId=" + sessionId + ",reason=" + reason + ")");
                mChatApplication.alljoynError(ChatApplication.Module.USE, "The chat session has been lost");
                mUseChannelState = UseChannelState.IDLE;
                mChatApplication.useSetChannelState(mUseChannelState);
            }
        });

        if (status == Status.OK) {
            Log.i(TAG, "doJoinSession(): use sessionId is " + mUseSessionId);
            mUseSessionId = sessionId.value;
        } else {
            mChatApplication.alljoynError(ChatApplication.Module.USE, "Unable to join chat session: (" + status + ")");
            return;
        }

        SignalEmitter emitter = new SignalEmitter(mChatService, mUseSessionId, SignalEmitter.GlobalBroadcast.Off);
        mChatInterface = emitter.getInterface(ChatInterface.class);

        mUseChannelState = UseChannelState.JOINED;
        mChatApplication.useSetChannelState(mUseChannelState);
    }
    /**
     * Implementation of the functionality related to joining an existing
     * remote session.
     */
    private void doLeaveSession() {
        Log.i(TAG, "doLeaveSession()");
        if (mJoinedToSelf == false) {
            mBus.leaveSession(mUseSessionId);
        }
        mUseSessionId = -1;
        mJoinedToSelf = false;
        mUseChannelState = UseChannelState.IDLE;
        mChatApplication.useSetChannelState(mUseChannelState);
    }



//--------------//Constants//-------------------//
    /**
     * The well-known name prefix which all bus attachments hosting a channel
     * will use.  The NAME_PREFIX and the channel name are composed to give
     * the well-known name a hosting bus attachment will request and
     * advertise.
     */
    private static final String NAME_PREFIX = "org.alljoyn.About";
    /**
     * Value for the HANDLE_APPLICATION_QUIT_EVENT case observer notification handler.
     */
    private static final int HANDLE_APPLICATION_QUIT_EVENT = 0;
    /**
     * Value for the HANDLE_JOIN_SESSION_EVENT case observer notification handler.
     */
    private static final int HANDLE_JOIN_SESSION_EVENT = 1;
    /**
     * Value for the HANDLE_LEAVE_SESSION_EVENT case observer notification handler.
     */
    private static final int HANDLE_LEAVE_SESSION_EVENT = 2;
    /**
     * Value for the HANDLE_HOST_INIT_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_HOST_INIT_CHANNEL_EVENT = 3;
    /**
     * Value for the HANDLE_HOST_START_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_HOST_START_CHANNEL_EVENT = 4;
    /**
     * Value for the HANDLE_HOST_STOP_CHANNEL_EVENT case observer notification handler.
     */
    private static final int HANDLE_HOST_STOP_CHANNEL_EVENT = 5;
    /**
     * Value for the HANDLE_OUTBOUND_CHANGED_EVENT case observer notification handler.
     */
    private static final int HANDLE_OUTBOUND_CHANGED_EVENT = 6;
    /**
     * The well-known session port used as the contact port for the chat service.
     */
    private static final short CONTACT_PORT = 27;
    /**
     * The object path used to identify the service "location" in the bus
     * attachment.
     */
    private static final String OBJECT_PATH = "/chatService";
    private static final int NOTIFICATION_ID = 0xdefaced;
    private static final int EXIT = 1;
    private static final int CONNECT = 2;
    private static final int DISCONNECT = 3;
    private static final int START_DISCOVERY = 4;
    private static final int CANCEL_DISCOVERY = 5;
    private static final int REQUEST_NAME = 6;
    private static final int RELEASE_NAME = 7;
    private static final int BIND_SESSION = 8;
    private static final int UNBIND_SESSION = 9;
    private static final int ADVERTISE = 10;
    private static final int CANCEL_ADVERTISE = 11;
    private static final int JOIN_SESSION = 12;
    private static final int LEAVE_SESSION = 13;
    private static final int SEND_MESSAGES = 14;



//--------------//Misc.//-------------------//
    /**
     * Manually load in the NDK library so we can use the methods defined inside.
     */
    static {
        Log.i(TAG, "System.loadLibrary(\"alljoyn_java\")");
        System.loadLibrary("alljoyn_java");
    }

}
