package com.skedoodle.cordova.plugin.picker;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class Picker extends CordovaPlugin {
    private static final int REQUEST_CODE_APP_PICK = 33;
    private static final int REQUEST_CODE_APP_RESULT = 42;
    private CallbackContext callbackContext;
    private String selectedAppName;

    private static JSONObject toJsonObject(Bundle bundle) {
        //  Credit: https://github.com/napolitano/cordova-plugin-intent
        try {
            return (JSONObject) toJsonValue(bundle);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Cannot convert bundle to JSON: " + e.getMessage(), e);
        }
    }

    private static Object toJsonValue(final Object value) throws JSONException {
        //  Credit: https://github.com/napolitano/cordova-plugin-intent
        if (value == null) {
            return null;
        } else if (value instanceof Bundle) {
            final Bundle bundle = (Bundle) value;
            final JSONObject result = new JSONObject();
            for (final String key : bundle.keySet()) {
                result.put(key, toJsonValue(bundle.get(key)));
            }
            return result;
        } else if ((value.getClass().isArray())) {
            final JSONArray result = new JSONArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; ++i) {
                result.put(i, toJsonValue(Array.get(value, i)));
            }
            return result;
        } else if (value instanceof ArrayList<?>) {
            final ArrayList arrayList = (ArrayList<?>) value;
            final JSONArray result = new JSONArray();
            for (int i = 0; i < arrayList.size(); i++)
                result.put(toJsonValue(arrayList.get(i)));
            return result;
        } else if (
                value instanceof String
                        || value instanceof Boolean
                        || value instanceof Integer
                        || value instanceof Long
                        || value instanceof Double) {
            return value;
        } else {
            return String.valueOf(value);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        if ("show".equals(action)) {
            show(args.getString(0));
            return true;
        } else if ("share".equals(action)) {
            share(args.getString(0));
            return true;
        }

        return false;
    }

    private void show(String msg) {
        if (msg == null || msg.length() == 0) {
            this.callbackContext.error("Empty message!");
        } else {
            Toast.makeText(webView.getContext(), msg, Toast.LENGTH_LONG).show();
            this.callbackContext.success(msg);
        }
    }

    private void share(String msg) {
        if (msg == null || msg.length() == 0) {
            this.callbackContext.error("Empty message!");
        } else {
            // First search for compatible apps with sharing (Intent.ACTION_SEND)
            List<Intent> targetedShareIntents = new ArrayList<>();
            Intent shareIntent = new Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            // Set title and text to share when the user selects an option.
            // shareIntent.putExtra(Intent.EXTRA_TITLE, "Title 42");
            shareIntent.putExtra(Intent.EXTRA_TEXT, msg);
            List<ResolveInfo> resInfo = cordova.getActivity().getPackageManager().queryIntentActivities(shareIntent, 0);
            if (!resInfo.isEmpty()) {
                for (ResolveInfo info : resInfo) {
                    Intent targetedShare = new Intent(android.content.Intent.ACTION_SEND);
                    targetedShare.setType("text/plain");
                    targetedShare.setPackage(info.activityInfo.packageName.toLowerCase());
                    targetedShareIntents.add(targetedShare);
                }
                // Then show the ACTION_PICK_ACTIVITY to let the user select it
                Intent intentPick = new Intent();
                intentPick.setAction(Intent.ACTION_PICK_ACTIVITY);
                // Set the title of the dialog
                intentPick.putExtra(Intent.EXTRA_TITLE, "Share to");
                intentPick.putExtra(Intent.EXTRA_INTENT, shareIntent);
                intentPick.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedShareIntents.toArray());
                // Call StartActivityForResult so we can get the app name selected by the user
                cordova.setActivityResultCallback(this);
                cordova.getActivity().startActivityForResult(intentPick, REQUEST_CODE_APP_PICK);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (intent != null) {
            if (requestCode == REQUEST_CODE_APP_PICK) {
                this.selectedAppName = getSelectedAppName(intent);

                cordova.setActivityResultCallback(this);
                cordova.getActivity().startActivityForResult(intent, REQUEST_CODE_APP_RESULT);
            } else if (requestCode == REQUEST_CODE_APP_RESULT) {
                this.callbackContext.success(createJsonResult(intent, this.selectedAppName));
            }
        } else {
            this.callbackContext.success(createJsonResult(null, this.selectedAppName));
        }
    }

    private String getSelectedAppName(Intent intent) {
        final String unknownAppName = "unknown";

        if (intent == null || intent.getComponent() == null) {
            return unknownAppName;
        }

        PackageManager packageManager = cordova.getContext().getPackageManager();
        ApplicationInfo applicationInfo;

        try {
            applicationInfo =
                    packageManager.getApplicationInfo(
                            intent.getComponent().getPackageName(),
                            0
                    );
        } catch (PackageManager.NameNotFoundException e) {
            applicationInfo = null;
            e.printStackTrace();
        }

        return applicationInfo != null
                ? (String) (packageManager.getApplicationLabel(applicationInfo))
                : (unknownAppName);
    }

    private JSONObject createJsonResult(Intent activityResult, String appPicked) {
        JSONObject result;
        if (activityResult != null) {
            result = getIntentJson(activityResult);
        } else {
            result = new JSONObject();
        }

        try {
            result.put("appName", appPicked);
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Return JSON representation of intent attributes
     *
     * @param intent Credit: https://github.com/napolitano/cordova-plugin-intent
     */
    private JSONObject getIntentJson(Intent intent) {
        JSONObject intentJSON = null;
        ClipData clipData = null;
        JSONObject[] items = null;
        ContentResolver cR = this.cordova.getActivity().getApplicationContext().getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            clipData = intent.getClipData();
            if (clipData != null) {
                int clipItemCount = clipData.getItemCount();
                items = new JSONObject[clipItemCount];

                for (int i = 0; i < clipItemCount; i++) {

                    ClipData.Item item = clipData.getItemAt(i);

                    try {
                        items[i] = new JSONObject();
                        items[i].put("htmlText", item.getHtmlText());
                        items[i].put("intent", item.getIntent());
                        items[i].put("text", item.getText());
                        items[i].put("uri", item.getUri());

                        if (item.getUri() != null) {
                            String type = cR.getType(item.getUri());
                            String extension = mime.getExtensionFromMimeType(cR.getType(item.getUri()));

                            items[i].put("type", type);
                            items[i].put("extension", extension);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            }
        }

        try {
            intentJSON = new JSONObject();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (items != null) {
                    intentJSON.put("clipItems", new JSONArray(items));
                }
            }

            intentJSON.put("type", intent.getType());
            intentJSON.put("extras", toJsonObject(intent.getExtras()));
            intentJSON.put("action", intent.getAction());
            intentJSON.put("categories", intent.getCategories());
            intentJSON.put("flags", intent.getFlags());
            intentJSON.put("component", intent.getComponent());
            intentJSON.put("data", intent.getData());
            intentJSON.put("package", intent.getPackage());

            return intentJSON;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}