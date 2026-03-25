/**
 * ProfileManager - Manages multiple controller layout profiles
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages multiple controller layout profiles.
 * Allows users to save, load, switch between different button layouts.
 */
public class ProfileManager {

    private static final String PROFILES_PREF = "OSC_PROFILES";
    private static final String ACTIVE_PROFILE_KEY = "ACTIVE_PROFILE";
    private static final String PROFILES_LIST_KEY = "PROFILES_LIST";
    private static final String DEFAULT_PROFILE_NAME = "Default";

    /**
     * Represents a controller profile
     */
    public static class Profile {
        public String name;
        public String id;

        public Profile(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /**
     * Get the list of available profiles
     */
    public static List<Profile> getProfiles(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PROFILES_PREF, Activity.MODE_PRIVATE);
        List<Profile> profiles = new ArrayList<>();

        // Always include default profile
        profiles.add(new Profile("default", DEFAULT_PROFILE_NAME));

        // Load custom profiles
        String profilesJson = pref.getString(PROFILES_LIST_KEY, "[]");
        try {
            JSONArray profilesArray = new JSONArray(profilesJson);
            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject profileObj = profilesArray.getJSONObject(i);
                String id = profileObj.getString("id");
                String name = profileObj.getString("name");
                profiles.add(new Profile(id, name));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return profiles;
    }

    /**
     * Get the currently active profile ID
     */
    public static String getActiveProfileId(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PROFILES_PREF, Activity.MODE_PRIVATE);
        return pref.getString(ACTIVE_PROFILE_KEY, "default");
    }

    /**
     * Set the active profile
     */
    public static void setActiveProfile(Context context, String profileId) {
        SharedPreferences pref = context.getSharedPreferences(PROFILES_PREF, Activity.MODE_PRIVATE);
        pref.edit().putString(ACTIVE_PROFILE_KEY, profileId).apply();
    }

    /**
     * Get the preference key for a specific profile element
     */
    public static String getProfileElementKey(String profileId, int elementId) {
        return profileId + "_" + elementId;
    }

    /**
     * Get the combo buttons key for a specific profile
     */
    public static String getProfileComboButtonsKey(String profileId) {
        return profileId + "_COMBO_BUTTONS";
    }

    /**
     * Create a new profile
     */
    public static void createProfile(final Context context, final ProfileCreateListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("New Profile Name");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Enter profile name");
        builder.setView(input);

        builder.setPositiveButton("Create", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    name = "Profile " + (getProfiles(context).size());
                }

                String id = "profile_" + System.currentTimeMillis();

                // Add to profiles list
                SharedPreferences pref = context.getSharedPreferences(PROFILES_PREF, Activity.MODE_PRIVATE);
                String profilesJson = pref.getString(PROFILES_LIST_KEY, "[]");
                try {
                    JSONArray profilesArray = new JSONArray(profilesJson);
                    JSONObject newProfile = new JSONObject();
                    newProfile.put("id", id);
                    newProfile.put("name", name);
                    profilesArray.put(newProfile);

                    pref.edit().putString(PROFILES_LIST_KEY, profilesArray.toString()).apply();

                    if (listener != null) {
                        listener.onProfileCreated(id, name);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(context, "Failed to create profile", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Delete a profile
     */
    public static void deleteProfile(final Context context, final String profileId, final ProfileDeleteListener listener) {
        if (profileId.equals("default")) {
            Toast.makeText(context, "Cannot delete default profile", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(context)
                .setTitle("Delete Profile")
                .setMessage("Are you sure you want to delete this profile?")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Remove from profiles list
                        SharedPreferences pref = context.getSharedPreferences(PROFILES_PREF, Activity.MODE_PRIVATE);
                        String profilesJson = pref.getString(PROFILES_LIST_KEY, "[]");
                        try {
                            JSONArray profilesArray = new JSONArray(profilesJson);
                            JSONArray newArray = new JSONArray();

                            for (int i = 0; i < profilesArray.length(); i++) {
                                JSONObject profileObj = profilesArray.getJSONObject(i);
                                if (!profileObj.getString("id").equals(profileId)) {
                                    newArray.put(profileObj);
                                }
                            }

                            SharedPreferences.Editor editor = pref.edit();
                            editor.putString(PROFILES_LIST_KEY, newArray.toString());

                            // Clear all profile data
                            // We need to find and remove all keys starting with profileId_
                            // This is done by clearing element configs and combo buttons
                            editor.remove(getProfileComboButtonsKey(profileId));

                            // Also clear element configs (we'll do this in VirtualControllerConfigurationLoader)
                            editor.apply();

                            if (listener != null) {
                                listener.onProfileDeleted(profileId);
                            }

                            Toast.makeText(context, "Profile deleted", Toast.LENGTH_SHORT).show();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Rename a profile
     */
    public static void renameProfile(final Context context, final String profileId, 
                                      final String newName, final ProfileRenameListener listener) {
        if (profileId.equals("default")) {
            Toast.makeText(context, "Cannot rename default profile", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences pref = context.getSharedPreferences(PROFILES_PREF, Activity.MODE_PRIVATE);
        String profilesJson = pref.getString(PROFILES_LIST_KEY, "[]");
        try {
            JSONArray profilesArray = new JSONArray(profilesJson);

            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject profileObj = profilesArray.getJSONObject(i);
                if (profileObj.getString("id").equals(profileId)) {
                    profileObj.put("name", newName);
                    break;
                }
            }

            pref.edit().putString(PROFILES_LIST_KEY, profilesArray.toString()).apply();

            if (listener != null) {
                listener.onProfileRenamed(profileId, newName);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Copy current profile to a new profile
     */
    public static void copyToNewProfile(final Context context, final VirtualController controller) {
        createProfile(context, new ProfileCreateListener() {
            @Override
            public void onProfileCreated(String profileId, String name) {
                // Save current layout to new profile
                VirtualControllerConfigurationLoader.saveProfileTo(controller, context, profileId);
                Toast.makeText(context, "Profile '" + name + "' created with current layout", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Show profile selection dialog
     */
    public static void showProfileSelector(final Context context, final VirtualController controller,
                                            final ProfileSelectListener listener) {
        final List<Profile> profiles = getProfiles(context);
        String activeProfileId = getActiveProfileId(context);

        CharSequence[] items = new CharSequence[profiles.size()];
        int checkedItem = 0;

        for (int i = 0; i < profiles.size(); i++) {
            items[i] = profiles.get(i).name;
            if (profiles.get(i).id.equals(activeProfileId)) {
                checkedItem = i;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Select Profile");
        builder.setSingleChoiceItems(items, checkedItem, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Selection made - don't dismiss yet
            }
        });
        builder.setPositiveButton("Load", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                if (selectedPosition >= 0 && selectedPosition < profiles.size()) {
                    Profile selected = profiles.get(selectedPosition);
                    setActiveProfile(context, selected.id);
                    
                    if (listener != null) {
                        listener.onProfileSelected(selected.id, selected.name);
                    }
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    /**
     * Show profile management dialog (create, delete, rename, switch)
     */
    public static void showProfileManagerDialog(final Context context, final VirtualController controller) {
        final List<Profile> profiles = getProfiles(context);
        String activeProfileId = getActiveProfileId(context);

        CharSequence[] items = new CharSequence[]{
                "Switch Profile",
                "Save Current Layout to New Profile",
                "Rename Current Profile",
                "Delete Current Profile",
                "Cancel"
        };

        // Disable delete/rename for default profile
        boolean isDefault = activeProfileId.equals("default");

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Profile: " + getActiveProfileName(context, profiles, activeProfileId));

        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: // Switch Profile
                        showProfileSelector(context, controller, new ProfileSelectListener() {
                            @Override
                            public void onProfileSelected(String profileId, String name) {
                                controller.refreshLayout();
                                Toast.makeText(context, "Loaded profile: " + name, Toast.LENGTH_SHORT).show();
                            }
                        });
                        break;
                    case 1: // Save Current Layout to New Profile
                        copyToNewProfile(context, controller);
                        break;
                    case 2: // Rename Current Profile
                        if (!isDefault) {
                            showRenameDialog(context, activeProfileId);
                        } else {
                            Toast.makeText(context, "Cannot rename default profile", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case 3: // Delete Current Profile
                        if (!isDefault) {
                            deleteProfile(context, activeProfileId, new ProfileDeleteListener() {
                                @Override
                                public void onProfileDeleted(String profileId) {
                                    // Switch to default profile
                                    setActiveProfile(context, "default");
                                    controller.refreshLayout();
                                }
                            });
                        } else {
                            Toast.makeText(context, "Cannot delete default profile", Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            }
        });

        builder.show();
    }

    private static String getActiveProfileName(Context context, List<Profile> profiles, String activeId) {
        for (Profile p : profiles) {
            if (p.id.equals(activeId)) {
                return p.name;
            }
        }
        return DEFAULT_PROFILE_NAME;
    }

    private static void showRenameDialog(final Context context, final String profileId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Rename Profile");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("Rename", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    renameProfile(context, profileId, newName, null);
                    Toast.makeText(context, "Profile renamed to: " + newName, Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Listeners
    public interface ProfileCreateListener {
        void onProfileCreated(String profileId, String name);
    }

    public interface ProfileDeleteListener {
        void onProfileDeleted(String profileId);
    }

    public interface ProfileRenameListener {
        void onProfileRenamed(String profileId, String newName);
    }

    public interface ProfileSelectListener {
        void onProfileSelected(String profileId, String name);
    }
}
