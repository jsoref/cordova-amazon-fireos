/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/

package org.apache.cordova;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import com.amazon.android.webkit.AmazonWebBackForwardList;
import com.amazon.android.webkit.AmazonWebHistoryItem;
import com.amazon.android.webkit.AmazonWebChromeClient;
import com.amazon.android.webkit.AmazonWebKitFactories;
import com.amazon.android.webkit.AmazonWebSettings;
import com.amazon.android.webkit.AmazonWebView;
import com.amazon.android.webkit.AmazonWebKitFactory;
import android.widget.FrameLayout;

public class CordovaWebView extends AmazonWebView {
/*
 * This class is our web view.
 *
 * @see <a href="http://developer.android.com/guide/webapps/webview.html">WebView guide</a>
 * @see <a href="http://developer.android.com/reference/android/webkit/WebView.html">WebView</a>
 */


    public static final String TAG = "CordovaWebView";
    public static final String CORDOVA_VERSION = "3.7.0-dev";

    private HashSet<Integer> boundKeyCodes = new HashSet<Integer>();

    public PluginManager pluginManager;
    private boolean paused;

    private BroadcastReceiver receiver;


    /** Activities and other important classes **/
    private CordovaInterface cordova;
    CordovaWebViewClient viewClient;
    private CordovaChromeClient chromeClient;

    // Flag to track that a loadUrl timeout occurred
    int loadUrlTimeout = 0;

    private long lastMenuEventTime = 0;

    CordovaBridge bridge;

    /** custom view created by the browser (a video player for example) */
    private View mCustomView;
    private AmazonWebChromeClient.CustomViewCallback mCustomViewCallback;

    private CordovaResourceApi resourceApi;
    private Whitelist internalWhitelist;
    private Whitelist externalWhitelist;

    // The URL passed to loadUrl(), not necessarily the URL of the current page.
    String loadedUrl;
    private CordovaPreferences preferences;

    private static final String APPCACHE_DIR = "database";

    private static final String APPCACHE_DIR_EMPTY = "NONEXISTENT_PATH";
    private static final String SAFARI_UA = "Safari";
    private static final String MOBILE_SAFARI_UA = "Mobile Safari";
    private static final String CORDOVA_AMAZON_FIREOS_UA = "cordova-amazon-fireos/" + CORDOVA_VERSION;

    private static final String LOCAL_STORAGE_DIR = "database";

    /**
     * Arbitrary size limit for app cache resources
     */
    public static final long APP_CACHE_LIMIT = (1024 * 1024 * 50);

    /**
     * An enumeration to specify the desired back-end to use when constructing
     * the WebView.
     */
    public enum WebViewBackend {

        /** The stock Android WebView back-end */
        ANDROID,

        /** The Chromium AmazonWebView beck-end */
        CHROMIUM,

        /**
         * Automatically select the back-end depending on the device
         * configuration
         */
        AUTOMATIC
    };
    
    class ActivityResult {
        
        int request;
        int result;
        Intent incoming;
        
        public ActivityResult(int req, int res, Intent intent) {
            request = req;
            result = res;
            incoming = intent;
        }

        
    }
    
    static final FrameLayout.LayoutParams COVER_SCREEN_GRAVITY_CENTER =
            new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER);
    
    public CordovaWebView(Context context) {
        this(context, (AttributeSet)null);
    }

////fireos_change ////
    /**
     * Constructor 
     * The extraData bundle is needed for AmazonWebChromeClient.onCreateWindow callback.
     * It's just an opaque data that needs to be passed from one call to the other.
     * 
     * @param context
     * @param extraData 
     */
    public CordovaWebView(Context context, Bundle extraData) {
        this(context, (AttributeSet)null);
 
        if (CordovaInterface.class.isInstance(context))
        {
            this.cordova = (CordovaInterface) context;
            this.cordova.getFactory().initializeWebView(this, 0xFFFFFF, false, extraData);
        }
        else
        {
            Log.d(TAG, "Your activity must implement CordovaInterface to work");
        }
    }
////fireos_change ////
    
    /**
     * Constructor.
     *
     * @param context
     * @param attrs
     */
    public CordovaWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Deprecated
    public CordovaWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
/*
    @TargetApi(11)
    @Deprecated
    public CordovaWebView(Context context, AttributeSet attrs, int defStyle, boolean privateBrowsing) {
        super(context, attrs, defStyle, privateBrowsing);
    }
*/
    // Use two-phase init so that the control will work with XML layouts.
    public void init(CordovaInterface cordova, CordovaWebViewClient webViewClient, CordovaChromeClient webChromeClient,
            List<PluginEntry> pluginEntries, Whitelist internalWhitelist, Whitelist externalWhitelist,
            CordovaPreferences preferences) {
        if (this.cordova != null) {
            throw new IllegalStateException();
        }
        this.cordova = cordova;
        //this.cordova.getFactory().initializeWebView(this, 0xFFFFFF, false, null);
        this.viewClient = webViewClient;
        this.chromeClient = webChromeClient;
        this.internalWhitelist = internalWhitelist;
        this.externalWhitelist = externalWhitelist;
        this.preferences = preferences;
        super.setWebChromeClient(webChromeClient);
        super.setWebViewClient(webViewClient);

        pluginManager = new PluginManager(this, this.cordova, pluginEntries);
        bridge = new CordovaBridge(pluginManager, new NativeToJsMessageQueue(this, cordova), this.cordova.getActivity().getPackageName());
        resourceApi = new CordovaResourceApi(this.getContext(), pluginManager);

        pluginManager.addService("App", "org.apache.cordova.App");
        // This will be removed in 4.0.x in favour of the plugin not being bundled.
        pluginManager.addService(new PluginEntry("SplashScreenInternal", "org.apache.cordova.SplashScreenInternal", true));
        pluginManager.init();
        initWebViewSettings();
        exposeJsInterface();
    }

    @SuppressWarnings("deprecation")
    private void initIfNecessary() {
        if (pluginManager == null) {
            Log.w(TAG, "CordovaWebView.init() was not called. This will soon be required.");
            // Before the refactor to a two-phase init, the Context needed to implement CordovaInterface. 
            CordovaInterface cdv = (CordovaInterface)getContext();
            if (!Config.isInitialized()) {
                Config.init(cdv.getActivity());
            }
            init(cdv, makeWebViewClient(cdv), makeWebChromeClient(cdv), Config.getPluginEntries(), Config.getWhitelist(), Config.getExternalWhitelist(), Config.getPreferences());
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    private void initWebViewSettings() {
        this.setInitialScale(0);
        this.setVerticalScrollBarEnabled(false);
        // TODO: The Activity is the one that should call requestFocus().
        if (shouldRequestFocusOnInit()) {
			this.requestFocusFromTouch();
		}
		// Enable JavaScript
        AmazonWebSettings settings = this.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);    
        
        // Set the nav dump for HTC 2.x devices (disabling for ICS, deprecated entirely for Jellybean 4.2)
        try {
            Method gingerbread_getMethod =  AmazonWebSettings.class.getMethod("setNavDump", new Class[] { boolean.class });
            
            String manufacturer = android.os.Build.MANUFACTURER;
            Log.d(TAG, "CordovaWebView is running on device made by: " + manufacturer);
            if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB &&
                    android.os.Build.MANUFACTURER.contains("HTC"))
            {
                gingerbread_getMethod.invoke(settings, true);
            }
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "We are on a modern version of Android, we will deprecate HTC 2.3 devices in 2.8");
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "Doing the NavDump failed with bad arguments");
        } catch (IllegalAccessException e) {
            Log.d(TAG, "This should never happen: IllegalAccessException means this isn't Android anymore");
        } catch (InvocationTargetException e) {
            Log.d(TAG, "This should never happen: InvocationTargetException means this isn't Android anymore.");
        }

        //We don't save any form data in the application
        settings.setSaveFormData(false);
        settings.setSavePassword(false);
        
        // Jellybean rightfully tried to lock this down. Too bad they didn't give us a whitelist
        // while we do this
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN
            || (getWebViewBackend(this.cordova.getFactory()) == WebViewBackend.CHROMIUM))
            Level16Apis.enableUniversalAccess(settings);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Level17Apis.setMediaPlaybackRequiresUserGesture(settings, false);
        }
        String databasePath = getContext().getApplicationContext().getDir("database", Context.MODE_PRIVATE).getPath();
        
        if (getWebViewBackend(this.cordova.getFactory()) == WebViewBackend.ANDROID) {
        	File appCacheDir = this.cordova.getActivity().getDir(APPCACHE_DIR, Context.MODE_PRIVATE);
            if (appCacheDir.exists()) {
                settings.setAppCachePath(appCacheDir.getPath());
                settings.setAppCacheMaxSize(APP_CACHE_LIMIT);
                settings.setAppCacheEnabled(true);
            } else {
                // shouldn't get here...
                Log.e(TAG, "Unable to construct application cache directory, feature disabled");
            }
	// Enable database
        // We keep this disabled because we use or shim to get around DOM_EXCEPTION_ERROR_16
        settings.setDatabaseEnabled(true);
        settings.setDatabasePath(databasePath);
        
        settings.setGeolocationDatabasePath(databasePath);

            File storageDir = this.cordova.getActivity().getDir(LOCAL_STORAGE_DIR, Context.MODE_PRIVATE);
            if (storageDir.exists()) {
                settings.setDatabasePath(storageDir.getPath());
                settings.setDatabaseEnabled(true);
                settings.setGeolocationDatabasePath(storageDir.getPath());
            } else {
                // shouldn't get here...
                Log.e(TAG, "Unable to construct local storage directory, feature disabled");
            }
        } else {
            // setting a custom path (as well as the max cache size) is not supported by Chromium,
            // however setting the path to a non-null non-empty string is required for it to function
            settings.setAppCachePath(APPCACHE_DIR_EMPTY);
            settings.setAppCacheEnabled(true);
            
            // enable the local storage database normally with the Chromium back-end
            settings.setDatabaseEnabled(true);
        }
                
        // Enable DOM storage
        settings.setDomStorageEnabled(true);

        // Enable built-in geolocation
        settings.setGeolocationEnabled(true);
        
         // Fix UserAgent string
        String userAgent = settings.getUserAgentString();
        if ((userAgent.indexOf(MOBILE_SAFARI_UA) == -1) && (userAgent.indexOf(SAFARI_UA) != -1)) {
            // Replace Safari with Mobile Safari
            userAgent = userAgent.replace(SAFARI_UA, MOBILE_SAFARI_UA);
        }
        userAgent = userAgent.concat(" " + CORDOVA_AMAZON_FIREOS_UA); 
        settings.setUserAgentString(userAgent);
        //__FireOS__
        settings.setUseWideViewPort(true);
        //__FireOS__
        
        // Enable AppCache
        // Fix for CB-2282
        //settings.setAppCacheMaxSize(5 * 1048576);
        //settings.setAppCachePath(databasePath);
        //settings.setAppCacheEnabled(true);
        
        // Fix for CB-1405
        // Google issue 4641
        settings.getUserAgentString();
        
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        if (this.receiver == null) {
            this.receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    getSettings().getUserAgentString();
                }
            };
            getContext().registerReceiver(this.receiver, intentFilter);
        }
        // end CB-1405
    }
/*
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void enableRemoteDebugging() {
        try {
            AmazonWebView.setWebContentsDebuggingEnabled(true);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "You have one job! To turn on Remote Web Debugging! YOU HAVE FAILED! ");
            e.printStackTrace();
        }
    }
*/
    public CordovaChromeClient makeWebChromeClient(CordovaInterface cordova) {
        return new CordovaChromeClient(cordova, this);
    }

    public CordovaWebViewClient makeWebViewClient(CordovaInterface cordova) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return new CordovaWebViewClient(cordova, this);
        }
        return new IceCreamCordovaWebViewClient(cordova, this);
    }
    
    /**
     * The actual back-end used when constructing the WebView. Note that this
     * may differ from the requested back-end depending on the device
     * configuration.
     * 
     * @return either {@link WebViewBackend#AMAZON} or
     *         {@link WebViewBackend#ANDROID}
     */
    static WebViewBackend getWebViewBackend(AmazonWebKitFactory factory) {
    	// This is to figure out if WebView is using Chromium based webapp runtime or stock AndroidWebView.
    	// On Kindle devices default is Chromium based. There is no public API to figure out the difference. 
    	// EmbeddedWebKitFactory is not a plublic class so only way to check is using this AmazonWebKitFactories.EMBEDDED_FACTORY class name.
    	if (factory.getClass().getName().equals(AmazonWebKitFactories.EMBEDDED_FACTORY) ) {
            return WebViewBackend.CHROMIUM;
        }
        return WebViewBackend.ANDROID;
    }

	/**
	 * Override this method to decide whether or not you need to request the
	 * focus when your application start
	 * 
	 * @return true unless this method is overriden to return a different value
	 */
    protected boolean shouldRequestFocusOnInit() {
		return true;
	}

    private void exposeJsInterface() {
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1)) {
            Log.i(TAG, "Disabled addJavascriptInterface() bridge since Android version is old.");
            // Bug being that Java Strings do not get converted to JS strings automatically.
            // This isn't hard to work-around on the JS side, but it's easier to just
            // use the prompt bridge instead.
            return;            
        } 
        this.addJavascriptInterface(new ExposedJsApi(bridge), "_cordovaNative");
    }

    @Override
    public void setWebChromeClient(AmazonWebChromeClient client) {
        this.chromeClient = (CordovaChromeClient)client;
        super.setWebChromeClient(client);
    }
    
    public CordovaChromeClient getWebChromeClient() {
        return this.chromeClient;
    }

    
    public Whitelist getWhitelist() {
        return this.internalWhitelist;
    }

    public Whitelist getExternalWhitelist() {
        return this.externalWhitelist;
    }

    /**
     * Load the url into the webview.
     *
     * @param url
     */
    @Override
    public void loadUrl(String url) {
        if (url.equals("about:blank") || url.startsWith("javascript:")) {
            this.loadUrlNow(url);
        }
        else {
            this.loadUrlIntoView(url);
        }
    }

    /**
     * Load the url into the webview after waiting for period of time.
     * This is used to display the splashscreen for certain amount of time.
     *
     * @param url
     * @param time              The number of ms to wait before loading webview
     */
    @Deprecated
    public void loadUrl(final String url, int time) {
        if(url == null)
        {
            this.loadUrlIntoView(Config.getStartUrl());
        }
        else
        {
            this.loadUrlIntoView(url);
        }
    }

    public void loadUrlIntoView(final String url) {
        loadUrlIntoView(url, true);
    }

    /**
     * Load the url into the webview.
     *
     * @param url
     */
    public void loadUrlIntoView(final String url, boolean recreatePlugins) {
        LOG.d(TAG, ">>> loadUrl(" + url + ")");

        initIfNecessary();

        if (recreatePlugins) {
            // Don't re-initialize on first load.
            if (loadedUrl != null) {
                this.pluginManager.init();
            }
            this.loadedUrl = url;
        }

        // Got rid of the timers logic to check for errors/non-responding webpages.
        // Timers were creating threading issues and NPE in some cases where app needed to load more urls or navigate back and forth a lot.
        // PS. this change exists only on amazon-fireos platform.
        // Load url
        this.cordova.getActivity().runOnUiThread(new Runnable() {
        	public void run() {
        	    CordovaWebView.this.loadUrlNow(url);
        	}
        });
    }

    /**
     * Load URL in webview.
     *
     * @param url
     */
    void loadUrlNow(String url) {
        if (LOG.isLoggable(LOG.DEBUG) && !url.startsWith("javascript:")) {
            LOG.d(TAG, ">>> loadUrlNow()");
        }
        if (url.startsWith("file://") || url.startsWith("javascript:") || internalWhitelist.isUrlWhiteListed(url)) {
            super.loadUrl(url);
        }
    }

    /**
     * Load the url into the webview after waiting for period of time.
     * This is used to display the splashscreen for certain amount of time.
     *
     * @param url
     * @param time              The number of ms to wait before loading webview
     */
    public void loadUrlIntoView(final String url, final int time) {

        // If not first page of app, then load immediately
        // Add support for browser history if we use it.
        if ((url.startsWith("javascript:")) || this.canGoBack()) {
        }

        // If first page, then show splashscreen
        else {

            LOG.d(TAG, "loadUrlIntoView(%s, %d)", url, time);
        }

        // Load url
        this.loadUrlIntoView(url);
    }
    
    @Override
    public void stopLoading() {
        viewClient.isCurrentlyLoading = false;
        super.stopLoading();
    }

    public void onScrollChanged(int l, int t, int oldl, int oldt)
    {
        super.onScrollChanged(l, t, oldl, oldt);
        //We should post a message that the scroll changed
        LOG.d(TAG, "Scroll changed: oldl = %d, l = %d", oldl, l);
        LOG.d(TAG, "Scroll changed: oldt = %d, t = %d", oldt, t);
        ScrollEvent myEvent = new ScrollEvent(l, t, oldl, oldt, this);
        this.postMessage("onScrollChanged", myEvent);
    }
    
    /**
     * Send JavaScript statement back to JavaScript.
     * Deprecated (https://issues.apache.org/jira/browse/CB-6851)
     * Instead of executing snippets of JS, you should use the exec bridge
     * to create a Java->JS communication channel.
     * To do this:
     * 1. Within plugin.xml (to have your JS run before deviceready):
     *    <js-module><runs/></js-module>
     * 2. Within your .js (call exec on start-up):
     *    require('cordova/channel').onCordovaReady.subscribe(function() {
     *      require('cordova/exec')(win, null, 'Plugin', 'method', []);
     *      function win(message) {
     *        ... process message from java here ...
     *      }
     *    });
     * 3. Within your .java:
     *    PluginResult dataResult = new PluginResult(PluginResult.Status.OK, CODE);
     *    dataResult.setKeepCallback(true);
     *    savedCallbackContext.sendPluginResult(dataResult);
     */
    @Deprecated
    public void sendJavascript(String statement) {
        this.bridge.getMessageQueue().addJavaScript(statement);
    }

    /**
     * Send a plugin result back to JavaScript.
     * (This is a convenience method)
     *
     * @param result
     * @param callbackId
     */
    public void sendPluginResult(PluginResult result, String callbackId) {
        this.bridge.getMessageQueue().addPluginResult(result, callbackId);
    }

    /**
     * Send a message to all plugins.
     *
     * @param id            The message id
     * @param data          The message data
     */
    public void postMessage(String id, Object data) {
        if (this.pluginManager != null) {
            this.pluginManager.postMessage(id, data);
        }
    }


    /**
     * Go to previous page in history.  (We manage our own history)
     *
     * @return true if we went back, false if we are already at top
     */
    public boolean backHistory() {
        // Check webview first to see if there is a history
        // This is needed to support curPage#diffLink, since they are added to appView's history, but not our history url array (JQMobile behavior)
        if (super.canGoBack()) {
            super.goBack();
            return true;
        }
        return false;
    }


    /**
     * Load the specified URL in the Cordova webview or a new browser instance.
     *
     * NOTE: If openExternal is false, only URLs listed in whitelist can be loaded.
     *
     * @param url           The url to load.
     * @param openExternal  Load url in browser instead of Cordova webview.
     * @param clearHistory  Clear the history stack, so new page becomes top of history
     * @param params        Parameters for new app
     */
    public void showWebPage(String url, boolean openExternal, boolean clearHistory, HashMap<String, Object> params) {
        LOG.d(TAG, "showWebPage(%s, %b, %b, HashMap", url, openExternal, clearHistory);

        // If clearing history
        if (clearHistory) {
            this.clearHistory();
        }

        // If loading into our webview
        if (!openExternal) {

            // Make sure url is in whitelist
            if (url.startsWith("file://") || internalWhitelist.isUrlWhiteListed(url)) {
                // TODO: What about params?
                // Load new URL
                this.loadUrl(url);
                return;
            }
            // Load in default viewer if not
            LOG.w(TAG, "showWebPage: Cannot load URL into webview since it is not in white list.  Loading into browser instead. (URL=" + url + ")");
        }
        try {
            // Omitting the MIME type for file: URLs causes "No Activity found to handle Intent".
            // Adding the MIME type to http: URLs causes them to not be handled by the downloader.
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(url);
            if ("file".equals(uri.getScheme())) {
                intent.setDataAndType(uri, resourceApi.getMimeType(uri));
            } else {
                intent.setData(uri);
            }
            cordova.getActivity().startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            LOG.e(TAG, "Error loading url " + url, e);
        }
    }

    /**
     * Get string property for activity.
     *
     * @param name
     * @param defaultValue
     * @return the String value for the named property
     */
    public String getProperty(String name, String defaultValue) {
        Bundle bundle = this.cordova.getActivity().getIntent().getExtras();
        if (bundle == null) {
            return defaultValue;
        }
        name = name.toLowerCase(Locale.getDefault());
        Object p = bundle.get(name);
        if (p == null) {
            return defaultValue;
        }
        return p.toString();
    }

    /**
     * Handle when the back button is pressed on the current window. Depending on the state of the application, this
     * will either navigate back in the history, close the window, send a back event to the running web application,
     * or dismiss a full screen video.
     */
    public void onBackPressed() {
        // A custom view is currently displayed (e.g. playing a video)
        if (mCustomView != null) {
            this.hideCustomView();
        } else {
            // The webview is currently displayed
            // If back key is bound, then send event to JavaScript
            if (isButtonPlumbedToJs(KeyEvent.KEYCODE_BACK)) {
                this.loadUrl("javascript:cordova.fireDocumentEvent('backbutton');");
                return;
            } else {
                // If not bound
                // Give plugins a chance to override behavior
                if (this.pluginManager != null) {
                    Object returnVal = this.pluginManager.postMessage("onBackPressed", null);
                    if (returnVal != null && returnVal instanceof Boolean && (Boolean) returnVal) {
                        // The return value was a true boolean, callback was consumed
                        return;
                    }
                }
                // Go to previous page in webview if it is possible to go back
                if (this.backHistory()) {
                    return;
                }
                // If not, then invoke default behavior
                else {
                    // this.activityState = ACTIVITY_EXITING;
                    // return false;
                    // If they hit back button when app is initializing, app should exit instead of hang until
                    // initialization (CB2-458)
                    this.cordova.getActivity().finish();
                }
            }
        }
    }
    
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if(boundKeyCodes.contains(keyCode))
        {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    this.loadUrl("javascript:cordova.fireDocumentEvent('volumedownbutton');");
                    return true;
            }
            // If volumeup key
            else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    this.loadUrl("javascript:cordova.fireDocumentEvent('volumeupbutton');");
                    return true;
            }
            else
            {
                return super.onKeyDown(keyCode, event);
            }
        }
        else if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            return !(this.startOfHistory()) || isButtonPlumbedToJs(KeyEvent.KEYCODE_BACK);
        }
        else if(keyCode == KeyEvent.KEYCODE_MENU)
        {
            //How did we get here?  Is there a childView?
            View childView = this.getFocusedChild();
            if(childView != null)
            {
                //Make sure we close the keyboard if it's present
                InputMethodManager imm = (InputMethodManager) cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(childView.getWindowToken(), 0);
                cordova.getActivity().openOptionsMenu();
                return true;
            } else {
                return super.onKeyDown(keyCode, event);
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        // If back key
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // A custom view is currently displayed  (e.g. playing a video)
            if(mCustomView != null) {
                this.hideCustomView();
                return true;
            } else {
                // The webview is currently displayed
                // If back key is bound, then send event to JavaScript
                if (isButtonPlumbedToJs(KeyEvent.KEYCODE_BACK)) {
                    this.loadUrl("javascript:cordova.fireDocumentEvent('backbutton');");
                    return true;
                } else {
                    // If not bound
                    // Go to previous page in webview if it is possible to go back
                    if (this.backHistory()) {
                        return true;
                    }
                    // If not, then invoke default behavior
                }
            }
        }
        // Legacy
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (this.lastMenuEventTime < event.getEventTime()) {
                this.loadUrl("javascript:cordova.fireDocumentEvent('menubutton');");
            }
            this.lastMenuEventTime = event.getEventTime();
            return super.onKeyUp(keyCode, event);
        }
        // If search key
        else if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            this.loadUrl("javascript:cordova.fireDocumentEvent('searchbutton');");
            return true;
        }

        //Does webkit change this behavior?
        return super.onKeyUp(keyCode, event);
    }

    public void setButtonPlumbedToJs(int keyCode, boolean override) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_BACK:
                // TODO: Why are search and menu buttons handled separately?
                if (override) {
                    boundKeyCodes.add(keyCode);
                } else {
                    boundKeyCodes.remove(keyCode);
                }
                return;
            default:
                throw new IllegalArgumentException("Unsupported keycode: " + keyCode);
        }
    }

    @Deprecated // Use setButtonPlumbedToJs() instead.
    public void bindButton(boolean override)
    {
        setButtonPlumbedToJs(KeyEvent.KEYCODE_BACK, override);
    }

    @Deprecated // Use setButtonPlumbedToJs() instead.
    public void bindButton(String button, boolean override) {
        if (button.compareTo("volumeup")==0) {
            setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_UP, override);
        }
        else if (button.compareTo("volumedown")==0) {
            setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_DOWN, override);
        }
    }

    @Deprecated // Use setButtonPlumbedToJs() instead.
    public void bindButton(int keyCode, boolean keyDown, boolean override) {
        setButtonPlumbedToJs(keyCode, override);
    }

    @Deprecated // Use isButtonPlumbedToJs
    public boolean isBackButtonBound()
    {
        return isButtonPlumbedToJs(KeyEvent.KEYCODE_BACK);
    }

    public boolean isButtonPlumbedToJs(int keyCode)
    {
        return boundKeyCodes.contains(keyCode);
    }

    public void handlePause(boolean keepRunning)
    {
        LOG.d(TAG, "Handle the pause");
        // Send pause event to JavaScript
        this.loadUrl("javascript:try{cordova.fireDocumentEvent('pause');}catch(e){console.log('exception firing pause event from native');};");

        // Forward to plugins
        if (this.pluginManager != null) {
            this.pluginManager.onPause(keepRunning);
        }

        // If app doesn't want to run in background
        if (!keepRunning) {
            // Pause JavaScript timers (including setInterval)
            this.pauseTimers();
            this.onPause();
        }
        paused = true;
   
    }
    
    public void handleResume(boolean keepRunning, boolean activityResultKeepRunning)
    {

        this.loadUrl("javascript:try{cordova.fireDocumentEvent('resume');}catch(e){console.log('exception firing resume event from native');};");
        
        // Forward to plugins
        if (this.pluginManager != null) {
            this.pluginManager.onResume(keepRunning);
        }

        //resume first and then resumeTimers
        this.onResume();
        // Resume JavaScript timers (including setInterval)
        this.resumeTimers();
        paused = false;
    }
    
    public void handleDestroy()
    {
        // Cancel pending timeout timer.
        loadUrlTimeout++;

        // Send destroy event to JavaScript
        // Since baseUrl is set in loadUrlIntoView, if user hit Back button before loadUrl was called, we'll get an NPE on baseUrl (CB-2458)
        this.loadUrl("javascript:try{cordova.require('cordova/channel').onDestroy.fire();}catch(e){};");

        // Load blank page so that JavaScript onunload is called
        this.loadUrl("about:blank");
        
        //Remove last AlertDialog
        this.chromeClient.destroyLastDialog();

        // Forward to plugins
        if (this.pluginManager != null) {
            this.pluginManager.onDestroy();
        }
        
        // unregister the receiver
        if (this.receiver != null) {
            try {
                getContext().unregisterReceiver(this.receiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering configuration receiver: " + e.getMessage(), e);
            }
        }
        
        this.destroy();
    }
    
    public void onNewIntent(Intent intent)
    {
        //Forward to plugins
        if (this.pluginManager != null) {
            this.pluginManager.onNewIntent(intent);
        }
    }
    
    public boolean isPaused()
    {
        return paused;
    }

    @Deprecated // This never did anything.
    public boolean hadKeyEvent() {
        return false;
    }

    // Wrapping these functions in their own class prevents warnings in adb like:
    // VFY: unable to resolve virtual method 285: Landroid/webkit/AmazonWebSettings;.setAllowUniversalAccessFromFileURLs
    @TargetApi(16)
    private static class Level16Apis {
        static void enableUniversalAccess(AmazonWebSettings settings) {
            settings.setAllowUniversalAccessFromFileURLs(true);
        }
    }

    @TargetApi(17)
    private static final class Level17Apis {
        static void setMediaPlaybackRequiresUserGesture(AmazonWebSettings settings, boolean value) {
            settings.setMediaPlaybackRequiresUserGesture(value);
        }
    }

    public void printBackForwardList() {
        AmazonWebBackForwardList currentList = this.copyBackForwardList();
        int currentSize = currentList.getSize();
        for(int i = 0; i < currentSize; ++i)
        {
            AmazonWebHistoryItem item = currentList.getItemAtIndex(i);
            String url = item.getUrl();
            LOG.d(TAG, "The URL at index: " + Integer.toString(i) + "is " + url );
        }
    }
    
    
    //Can Go Back is BROKEN!
    public boolean startOfHistory()
    {
        AmazonWebBackForwardList currentList = this.copyBackForwardList();
        AmazonWebHistoryItem item = currentList.getItemAtIndex(0);
        if( item!=null){	// Null-fence in case they haven't called loadUrl yet (CB-2458)
	        String url = item.getUrl();
	        String currentUrl = this.getUrl();
	        LOG.d(TAG, "The current URL is: " + currentUrl);
	        LOG.d(TAG, "The URL at item 0 is:" + url);
	        return currentUrl.equals(url);
        }
        return false;
    }

    public void showCustomView(View view, AmazonWebChromeClient.CustomViewCallback callback) {
        // This code is adapted from the original Android Browser code, licensed under the Apache License, Version 2.0
        Log.d(TAG, "showing Custom View");
        // if a view already exists then immediately terminate the new one
        if (mCustomView != null) {
            callback.onCustomViewHidden();
            return;
        }
        
        // Store the view and its callback for later (to kill it properly)
        mCustomView = view;
        mCustomViewCallback = callback;
        
        // Add the custom view to its container.
        ViewGroup parent = (ViewGroup) this.getParent();
        parent.addView(view, COVER_SCREEN_GRAVITY_CENTER);
        
        // Hide the content view.
        this.setVisibility(View.GONE);
        
        // Finally show the custom view container.
        parent.setVisibility(View.VISIBLE);
        parent.bringToFront();
    }

    public void hideCustomView() {
        // This code is adapted from the original Android Browser code, licensed under the Apache License, Version 2.0
        Log.d(TAG, "Hiding Custom View");
        if (mCustomView == null) return;

        // Hide the custom view.
        mCustomView.setVisibility(View.GONE);
        
        // Remove the custom view from its container.
        ViewGroup parent = (ViewGroup) this.getParent();
        parent.removeView(mCustomView);
        mCustomView = null;
        mCustomViewCallback.onCustomViewHidden();
        
        // Show the content view.
        this.setVisibility(View.VISIBLE);
    }
    
    /**
     * if the video overlay is showing then we need to know 
     * as it effects back button handling
     * 
     * @return true if custom view is showing
     */
    public boolean isCustomViewShowing() {
        return mCustomView != null;
    }
    
    public AmazonWebBackForwardList restoreState(Bundle savedInstanceState)
    {
        AmazonWebBackForwardList myList = super.restoreState(savedInstanceState);
        Log.d(TAG, "AmazonWebView restoration crew now restoring!");
        //Initialize the plugin manager once more
        this.pluginManager.init();
        return myList;
    }

    @Deprecated // This never did anything
    public void storeResult(int requestCode, int resultCode, Intent intent) {
    }
    
    public CordovaResourceApi getResourceApi() {
        return resourceApi;
    }

    public CordovaPreferences getPreferences() {
        return preferences;
    }
}
