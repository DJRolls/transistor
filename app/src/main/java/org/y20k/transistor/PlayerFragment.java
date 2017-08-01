/**
 * PlayerFragment.java
 * Implements the main fragment of the player activity
 * This fragment is a detail view with the ability to start and stop playback
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-17 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.transistor;

import android.app.Activity;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import org.y20k.transistor.adapter.CollectionViewModel;
import org.y20k.transistor.core.Station;
import org.y20k.transistor.helpers.DialogDelete;
import org.y20k.transistor.helpers.DialogRename;
import org.y20k.transistor.helpers.ImageHelper;
import org.y20k.transistor.helpers.LogHelper;
import org.y20k.transistor.helpers.PermissionHelper;
import org.y20k.transistor.helpers.ShortcutHelper;
import org.y20k.transistor.helpers.TransistorKeys;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


/**
 * PlayerFragment class
 */
public final class PlayerFragment extends Fragment implements TransistorKeys {

    /* Define log tag */
    private static final String LOG_TAG = PlayerFragment.class.getSimpleName();


    /* Main class variables */
    private Activity mActivity;
    private CollectionViewModel mCollectionViewModel;
    private View mRootView;
    private TextView mStationNameView;
    private TextView mStationMetadataView;
    private TextView mStationDataSheetName;
    private TextView mStationDataSheetStreamUrl;
    private ImageView mStationImageView;
    private ImageButton mStationMenuView;
    private ImageView mPlaybackIndicator;
    private ImageButton mPlaybackButton;
    private LinearLayout mStationDataSheetNameLayout;
    private LinearLayout mStationDataSheetMetadataLayout;
    private LinearLayout mStationDataSheetStreamUrlLayout;
    private LinearLayout mStationDataSheetMimeTypeLayout;
    private LinearLayout mStationDataSheetChannelCountLayout;
    private LinearLayout mStationDataSheetSampleRateLayout;
    private LinearLayout mStationDataSheetBitRateLayout;
    private View mStationDataSheet;
    private TextView mStationDataSheetMetadata;
    private BottomSheetBehavior mStationDataSheetBehavior;
    private BroadcastReceiver mPlaybackStateChangedReceiver;
    private BroadcastReceiver mCollectionChangedReceiver;
    private BroadcastReceiver mMetadataChangedReceiver;
    private MediaBrowserCompat mBrowser;
    private MediaControllerCompat mController;
    private int mPlayback;
    private boolean mTwoPane;
//    private boolean mVisibility;
    private boolean mFragmentViewsCreated;
    private Station mStation;


    /* Constructor (default) */
    public PlayerFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        // get activity
        mActivity = getActivity();

        // set fragment state
        mFragmentViewsCreated = false;

        // get data from arguments
        Bundle arguments = getArguments();
        if (arguments != null) {

            // get station from arguments
            if (arguments.containsKey(ARG_STATION)) {
                mStation = arguments.getParcelable(ARG_STATION);
                arguments.remove(ARG_STATION);
            }

            // get tablet or phone mode info from arguments
            if (arguments.containsKey(ARG_TWO_PANE)) {
                mTwoPane = arguments.getBoolean(ARG_TWO_PANE, false);
                arguments.remove(ARG_TWO_PANE);
            } else {
                mTwoPane = false;
            }

        }

        // observe changes in LiveData
        mCollectionViewModel = ViewModelProviders.of((AppCompatActivity)mActivity).get(CollectionViewModel.class);
        mCollectionViewModel.getStationList().observe((LifecycleOwner)mActivity, createStationListObserver());
        mCollectionViewModel.getStation().observe((LifecycleOwner)mActivity, createStationCurrentObserver());
        mCollectionViewModel.getTwoPane().observe((LifecycleOwner)mActivity, createTwoPaneObserver());

        // fragment has options menu
        setHasOptionsMenu(!mTwoPane);
        // ((AppCompatActivity)mActivity).getSupportActionBar().setDisplayHomeAsUpEnabled(!mTwoPane);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {

        // inflate root view from xml
        mRootView = inflater.inflate(R.layout.fragment_player, container, false);

        // find views for station name and image and playback indicator
        mStationNameView = (TextView) mRootView.findViewById(R.id.player_textview_stationname);
        mStationMetadataView = (TextView) mRootView.findViewById(R.id.player_textview_station_metadata);
        mStationImageView = (ImageView) mRootView.findViewById(R.id.player_imageview_station_icon);
        mPlaybackIndicator = (ImageView) mRootView.findViewById(R.id.player_playback_indicator);
        mStationMenuView = (ImageButton) mRootView.findViewById(R.id.player_item_more_button);

        // add listener to station info view for clipboard copy
        View stationInfoView = mRootView.findViewById(R.id.player_layout_station_info);
        stationInfoView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyToClipboard(COPY_STATION_ALL);
            }
        });

        // get views for station data sheet
        View stationDataLayout = mRootView.findViewById(R.id.info_icon_layout);
        mStationDataSheet = mRootView.findViewById(R.id.station_data_sheet);
        mStationDataSheetNameLayout = (LinearLayout) mRootView.findViewById(R.id.station_data_sheet_name_layout);
        mStationDataSheetMetadataLayout = (LinearLayout) mRootView.findViewById(R.id.station_data_sheet_metadata_layout);
        mStationDataSheetStreamUrlLayout = (LinearLayout) mRootView.findViewById(R.id.station_data_sheet_stream_url_layout);
        mStationDataSheetName = (TextView) mRootView.findViewById(R.id.station_data_sheet_name);
        mStationDataSheetMetadata = (TextView) mRootView.findViewById(R.id.station_data_sheet_metadata);
        mStationDataSheetStreamUrl = (TextView) mRootView.findViewById(R.id.station_data_sheet_stream_url);
        mStationDataSheetMimeTypeLayout = (LinearLayout) mRootView.findViewById(R.id.station_data_sheet_mime_type_layout);
        mStationDataSheetChannelCountLayout = (LinearLayout) mRootView.findViewById(R.id.station_data_sheet_channel_count_layout);
        mStationDataSheetSampleRateLayout = (LinearLayout) mRootView.findViewById(R.id.station_data_sheet_sample_rate_layout);
        mStationDataSheetBitRateLayout = (LinearLayout) mRootView.findViewById(R.id.station_data_sheet_bitrate_layout);

        // set up and show station data sheet
        mStationDataSheetBehavior = BottomSheetBehavior.from(mStationDataSheet);
        mStationDataSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        mStationDataSheetBehavior.setBottomSheetCallback(getStationDataSheetCallback());
        stationDataLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mStationDataSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    mStationDataSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } else {
                    mStationDataSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            }
        });

        // attach listeners (for clipboard copy)
        mStationDataSheetMetadataLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // copy station metadata to clipboard
                copyToClipboard(COPY_STATION_METADATA);
                mStationDataSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
        mStationDataSheetStreamUrlLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // copy stream URL to clipboard
                copyToClipboard(COPY_STREAM_URL);
                mStationDataSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        // initially hide additional metadata views
        mStationMetadataView.setVisibility(View.GONE);
        mStationDataSheetMetadataLayout.setVisibility(View.GONE);

        // screen-specific adjustments
        if (mTwoPane) {
            // show three dots menu in tablet mode
            mStationMenuView.setVisibility(View.VISIBLE);
            // attach three dots menu
            mStationMenuView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PopupMenu popup = new PopupMenu(mActivity, view);
                    popup.inflate(R.menu.menu_list_list_item);
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            return handleMenuClick(item, false);
                        }
                    });
                    popup.show();
                }
            });
        } else {
            mActivity.setTitle(R.string.title_fragment_player);
        }

        // construct big playback button
        mPlaybackButton = (ImageButton)mRootView.findViewById(R.id.player_playback_button);
        mPlaybackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // handle tap on big playback button
                handlePlaybackButtonClick();
            }
        });

        return mRootView;
    }


    /* Updates this fragment's views with the current station */
    private void updateStationViews() {

        // set station name
        mStationNameView.setText(mStation.getStationName());

        // set station image
        Bitmap stationImage = createStationImage();
        if (stationImage != null) {
            mStationImageView.setImageBitmap(stationImage);
        }

        // show now playing metadata
        if (mStation.getPlaybackState() != PLAYBACK_STATE_STOPPED && mStation.getMetadata() != null) {
            mStationMetadataView.setText(mStation.getMetadata());
            mStationMetadataView.setVisibility(View.VISIBLE);
            mStationMetadataView.setSelected(true);
            mStationDataSheetMetadata.setText( mStation.getMetadata());
            mStationDataSheetMetadataLayout.setVisibility(View.VISIBLE);
        }

        // fill name and url
        mStationDataSheetName.setText(mStation.getStationName());
        mStationDataSheetStreamUrl.setText(mStation.getStreamUri().toString());

    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // update fragment state
        mFragmentViewsCreated = true;

        if (mStation != null) {
            // set up station information
            updateStationViews();
            // set up button symbol and playback indicator
            setVisualState();
        }

        // check if activity started from shortcut
        Bundle args = getArguments();
        if (args != null && args.getBoolean(ARG_PLAYBACK)) {
            // check if this station is not already playing
            Station station = PlayerService.getStation();
            if (station != null && station.getStreamUri().equals(mStation.getStreamUri()) && station.getPlaybackState() != PLAYBACK_STATE_STOPPED) {
                LogHelper.v(LOG_TAG, "Try to start playback from shortcut, but station is already running.");
            } else {
                startPlayback();
                LogHelper.v(LOG_TAG, "Staring playback from shortcut.");
            }
        }

    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // inflate the menu items for use in the action bar
        inflater.inflate(R.menu.menu_player_actionbar, menu);

//        ((AppCompatActivity)mActivity).getSupportActionBar().setDisplayShowHomeEnabled(!mTwoPane);
//        ((AppCompatActivity)mActivity).getSupportActionBar().setHomeButtonEnabled(!mTwoPane);

    }


    @Override
    public void onResume() {
        super.onResume();

    }


    @Override
    public void onPause() {
        super.onPause();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
//        unregisterBroadcastReceivers();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return handleMenuClick(item, super.onOptionsItemSelected(item));
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_IMAGE_PICKER_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted - get system picker for images
                    Intent pickImageIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(pickImageIntent, REQUEST_LOAD_IMAGE);
                } else {
                    // permission denied
                    Toast.makeText(mActivity, mActivity.getString(R.string.toastalert_permission_denied) + " READ_EXTERNAL_STORAGE", Toast.LENGTH_LONG).show();
                }
            }
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // retrieve selected image Uri from image picker
        Uri newImageUri = null;
        if (null != data) {
            newImageUri = data.getData();
        }

        if (requestCode == REQUEST_LOAD_IMAGE && resultCode == Activity.RESULT_OK && null != data) {

            ImageHelper imageHelper = new ImageHelper(newImageUri, mActivity);
            Bitmap newImage = imageHelper.getInputImage();

            if (newImage != null) {
                // write image to storage
                File stationImageFile = mStation.getStationImageFile();
                try (FileOutputStream out = new FileOutputStream(stationImageFile)) {
                    newImage.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (IOException e) {
                    LogHelper.e(LOG_TAG, "Unable to save: " + newImage.toString());
                }
                // change mStationImageView
                Bitmap stationImage = imageHelper.createCircularFramedImage(192, R.color.transistor_grey_lighter);
                mStationImageView.setImageBitmap(stationImage);
            } else {
                LogHelper.e(LOG_TAG, "Unable to get image from media picker: " + newImageUri.toString());
            }

        }
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // save current station
        outState.putParcelable(INSTANCE_STATION, mStation);
//        outState.putInt(INSTANCE_STATION_ID, mStationId);
        outState.putInt(INSTANCE_PLAYBACK, mPlayback);
    }


    /* Starts player service */
    private void startPlayback() {
        // set playback status
        mPlayback = PLAYBACK_STATE_LOADING_STATION;
        mStation.setPlaybackState(PLAYBACK_STATE_LOADING_STATION);

        // rotate playback button
        changeVisualState(mActivity);

        // start player service using intent
        Intent intent = new Intent(mActivity, PlayerService.class);
        intent.setAction(ACTION_PLAY);
        intent.putExtra(EXTRA_STATION, mStation);
//        intent.putExtra(EXTRA_STATION_ID, mStationId);
        mActivity.startService(intent);
        LogHelper.v(LOG_TAG, "Starting player service.");
    }


    /* Stops player service */
    private void stopPlayback() {
        // set playback false
        mPlayback = PLAYBACK_STATE_STOPPED;
        mStation.setPlaybackState(PLAYBACK_STATE_STOPPED);

        // reset metadata // todo remove
//        mStationMetadata = null;
//        mStationMimeType = null;
//        mStationChannelCount = -1;
//        mStationSampleRate = -1;
//        mStationBitRate = -1;

        // rotate playback button
        changeVisualState(mActivity);

        // stop player service using intent
        Intent intent = new Intent(mActivity, PlayerService.class);
        intent.setAction(ACTION_STOP);
        mActivity.startService(intent);
        LogHelper.v(LOG_TAG, "Stopping player service.");
    }


    /* Handles tap on the big playback button */
    private void handlePlaybackButtonClick() {
        // playback stopped or new station - start playback
        if (mPlayback == PLAYBACK_STATE_STOPPED || mStation.getPlaybackState() == PLAYBACK_STATE_STOPPED) {
            startPlayback();
        }
        // playback active - stop playback
        else {
            stopPlayback();
        }
    }


    /* Handles menu selection */
    private boolean handleMenuClick(MenuItem item, boolean defaultReturn) {

        switch (item.getItemId()) {

            // CASE HOME
            case android.R.id.home:
                LogHelper.e(LOG_TAG, "HOME - Backstack: " + ((AppCompatActivity)mActivity).getSupportFragmentManager().getBackStackEntryCount()); // todo remove
                // initiate back action
                ((AppCompatActivity)mActivity).getSupportFragmentManager().popBackStack();
                return true;

            // CASE ICON
            case R.id.menu_icon:
                // get system picker for images
                selectFromImagePicker();
                return true;

            // CASE RENAME
            case R.id.menu_rename:
                // construct and run rename dialog
                final DialogRename dialogRename = new DialogRename(mActivity, mStation);
                dialogRename.show();
                return true;

            // CASE DELETE
            case R.id.menu_delete:
                // stop player service using intent
                Intent intent = new Intent(mActivity, PlayerService.class);
                intent.setAction(ACTION_DISMISS);
                mActivity.startService(intent);
                LogHelper.v(LOG_TAG, "Opening delete dialog. Stopping player service.");
                // construct and run delete dialog
                DialogDelete dialogDelete = new DialogDelete(mActivity, mStation);
                dialogDelete.show();
                return true;

            // CASE SHORTCUT
            case R.id.menu_shortcut: {
                // create shortcut
                ShortcutHelper shortcutHelper = new ShortcutHelper(mActivity.getApplication().getApplicationContext());
                shortcutHelper.placeShortcut(mStation);
                return true;
            }

            // CASE DEFAULT
            default:
                return defaultReturn;
        }

    }


    /* Create Bitmap image for station */
    private Bitmap createStationImage () {
        Bitmap stationImageSmall;
        ImageHelper imageHelper;
        if (mStation != null &&  mStation.getStationImageFile().exists()) {
            // get image from collection
            stationImageSmall = BitmapFactory.decodeFile(mStation.getStationImageFile().toString());
        } else {
            // get default image
            stationImageSmall = null;
        }
        imageHelper = new ImageHelper(stationImageSmall, mActivity);

        return imageHelper.createCircularFramedImage(192, R.color.transistor_grey_lighter);
    }


    /* Copy station data to system clipboard */
    private void copyToClipboard(int contentType) {

        String clipboardText = null;
        ClipData clip;

        switch (contentType) {
            case COPY_STATION_ALL:
                // set clip text
                if ( mStation.getMetadata() != null) {
                    clipboardText = mStation.getStationName() +  " - " +  mStation.getMetadata() + " (" +  mStation.getStreamUri().toString() + ")";
                } else {
                    clipboardText = mStation.getStationName() + " (" + mStation.getStreamUri().toString() + ")";
                }
                // notify user
                Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_station_copied), Toast.LENGTH_SHORT).show();

                break;

            case COPY_STATION_METADATA:
                // set clip text and notify user
                clipboardText =  mStation.getMetadata();
                Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_copied_to_clipboard_metadata), Toast.LENGTH_SHORT).show();
                break;

            case COPY_STREAM_URL:
                // set clip text and notify user
                clipboardText = mStation.getStreamUri().toString();
                Toast.makeText(mActivity, mActivity.getString(R.string.toastmessage_copied_to_clipboard_url), Toast.LENGTH_SHORT).show();
                break;

        }

        // create clip and to clipboard
        if (clipboardText != null) {
            clip = ClipData.newPlainText("simple text", clipboardText);
            ClipboardManager cm = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(clip);
        }

    }


    /* Animate button and then set visual state */
    private void changeVisualState(Context context) {
        // get rotate animation from xml
        Animation rotate;
        if (mPlayback == PLAYBACK_STATE_STOPPED) {
            // if playback has been stopped get stop animation
            rotate = AnimationUtils.loadAnimation(context, R.anim.rotate_counterclockwise_fast);
        } else {
            // if playback has been started get start animation
            rotate = AnimationUtils.loadAnimation(context, R.anim.rotate_clockwise_slow);
        }

        // attach listener for animation end
        rotate.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                // set up button symbol and playback indicator afterwards
                setVisualState();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        // start animation of button
        mPlaybackButton.startAnimation(rotate);

    }


    /* Set button symbol and playback indicator */
    private void setVisualState() {

        // this station is running
//        if (mPlayback && mStationId == mStationIdCurrent) {
        int playBackState = mStation.getPlaybackState();

        // state: station loading
        if (mFragmentViewsCreated && mStation != null && mStation.getPlaybackState() == PLAYBACK_STATE_LOADING_STATION) {
            // change playback button image to stop
            mPlaybackButton.setImageResource(R.drawable.smbl_stop);
            // change playback indicator and metadata views
            mPlaybackIndicator.setBackgroundResource(R.drawable.ic_playback_indicator_loading_24dp);
            mStationMetadataView.setText(R.string.descr_station_stream_loading);
            mStationDataSheetMetadata.setText(R.string.descr_station_stream_loading);
            // show metadata views
            mStationMetadataView.setVisibility(View.VISIBLE);
            mStationMetadataView.setSelected(true);
            mStationDataSheetMetadataLayout.setVisibility(View.VISIBLE);
            displayExtendedMetaData();
        }
        //  state: playback started
        else if (mFragmentViewsCreated && mStation != null && playBackState == PLAYBACK_STATE_STARTED) {
            // change playback button image to stop
            mPlaybackButton.setImageResource(R.drawable.smbl_stop);
            // change playback indicator and metadata views
            mPlaybackIndicator.setBackgroundResource(R.drawable.ic_playback_indicator_started_24dp);
            mStationMetadataView.setText(mStation.getMetadata());
            mStationDataSheetMetadata.setText(mStation.getMetadata());
            // show metadata views
            mStationMetadataView.setVisibility(View.VISIBLE);
            mStationMetadataView.setSelected(true);
            mStationDataSheetMetadataLayout.setVisibility(View.VISIBLE);
            displayExtendedMetaData();
        }
        //  state: playback stopped
        else if (mFragmentViewsCreated) {
            // change playback button image to play
            mPlaybackButton.setImageResource(R.drawable.smbl_play);
            // change playback indicator
            mPlaybackIndicator.setBackgroundResource(R.drawable.ic_playback_indicator_stopped_24dp);
            // hide metadata views
            mStationMetadataView.setVisibility(View.GONE);
            mStationDataSheetMetadataLayout.setVisibility(View.GONE);
            mStationDataSheetMimeTypeLayout.setVisibility(View.GONE);
            mStationDataSheetChannelCountLayout.setVisibility(View.GONE);
            mStationDataSheetSampleRateLayout.setVisibility(View.GONE);
            mStationDataSheetBitRateLayout.setVisibility(View.GONE);
        }
    }


    /* Saves app state to SharedPreferences (not in use) */
    private void saveAppState(Context context) {
        LogHelper.v(LOG_TAG, "Saving state.");
    }


    /* Loads app state from preferences */
    private void loadAppState(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        mTwoPane = settings.getBoolean(PREF_TWO_PANE, false);
        LogHelper.v(LOG_TAG, "Loading state.");
    }


    /* Check permissions and start image picker */
    private void selectFromImagePicker() {
        // request permissions
        PermissionHelper permissionHelper = new PermissionHelper(mActivity, mRootView);
        if (permissionHelper.requestReadExternalStorage(PERMISSION_REQUEST_IMAGE_PICKER_READ_EXTERNAL_STORAGE)) {
            // get system picker for images
            Intent pickImageIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(pickImageIntent, REQUEST_LOAD_IMAGE);
        }
    }


    /* Creates BottomSheetCallback for the statistics sheet - needed in onCreateView */
    private BottomSheetBehavior.BottomSheetCallback getStationDataSheetCallback() {
        return new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                // react to state change
                switch (newState) {
                    case BottomSheetBehavior.STATE_EXPANDED:
                        // statistics sheet expanded
                        mStationDataSheet.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.transistor_grey_darker_95percent));
                        break;
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        // statistics sheet collapsed
                        mStationDataSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        mStationDataSheet.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.transistor_transparent));
                        break;
                    case BottomSheetBehavior.STATE_HIDDEN:
                        // statistics sheet hidden
                        mStationDataSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        break;
                    default:
                        break;
                }
            }
            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // react to dragging events
                if (slideOffset < 0.125f) {
                    mStationDataSheet.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.transistor_transparent));
                } else {
                    mStationDataSheet.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.transistor_grey_darker_95percent));
                }

            }
        };
    }


    /* Creates an observer for collection of stations stored as LiveData */
    private Observer<ArrayList<Station>> createStationListObserver() {
        return new Observer<ArrayList<Station>>() {
            @Override
            public void onChanged(@Nullable ArrayList<Station> newStationList) {
                if (mStation == null && newStationList.size() != 0) {
                    // get first station from list
                    mStation = newStationList.get(0);
                    // set up station information
                    updateStationViews();
                    // set up button symbol and playback indicator
                    setVisualState();
                }

                // todo check for
                // a) rename, delete
                // a) new metadata
                // b) playback state changes
                LogHelper.w(LOG_TAG, "Player says: Oh the list has changed. Lets see what I can do..."); // todo remove
            }
        };
    }


    /* Creates an observer for currently active stored as LiveData */
    private Observer<Station> createStationCurrentObserver() {
        return new Observer<Station>() {
            @Override
            public void onChanged(@Nullable Station newStation) {
                // todo check for
                // a) new metadata
                // b) playback state changes
                LogHelper.w(LOG_TAG, "Player says: Oh the station has changed. Lets see what I can do..."); // todo remove

                if (mStation == null) {
                    // first start? todo
                    mStation = newStation;
                } else if (!(newStation.getStreamUri().equals(mStation.getStreamUri()))) {
                    // another station was changed
                } else {
                    LogHelper.w(LOG_TAG, "Oh my. This station was changed. Lets really do something. For real."); // todo remove
                    mStation = newStation;
                    setVisualState();
                }

            }
        };
    }


    /* Creates an observer for state of two pane layout stored as LiveData */
    private Observer<Boolean> createTwoPaneObserver() {
        return new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean twoPane) {
                LogHelper.v(LOG_TAG, "Observer for two pane layout in PlayerFragment: layout has changed. TwoPane -> " + mTwoPane + twoPane);
                mTwoPane = twoPane;
                // todo change layout

                ((AppCompatActivity)mActivity).getSupportActionBar().setDisplayShowHomeEnabled(!mTwoPane);
                ((AppCompatActivity)mActivity).getSupportActionBar().setHomeButtonEnabled(!mTwoPane);


            }
        };
    }


    /* Fill the extended metadata sheez */
    private void displayExtendedMetaData() {
        // fill and show mime type bottom sheet view
        if (mStation.getMimeType() != null) {
            TextView stationDataSheetMimeTypeView = (TextView) mRootView.findViewById(R.id.station_data_sheet_mime_type);
            stationDataSheetMimeTypeView.setText(mStation.getMimeType());
            mStationDataSheetMimeTypeLayout.setVisibility(View.VISIBLE);
        }
        // fill and show channel count bottom sheet view
        if (mStation.getChannelCount() > 0) {
            TextView stationDataSheetChannelCountView = (TextView) mRootView.findViewById(R.id.station_data_sheet_channel_count);
            stationDataSheetChannelCountView.setText(String.valueOf(mStation.getChannelCount()));
            mStationDataSheetChannelCountLayout.setVisibility(View.VISIBLE);
        }
        // fill and show sample rate bottom sheet view
        if (mStation.getSampleRate() > 0) {
            TextView stationDataSheetSampleRateView = (TextView) mRootView.findViewById(R.id.station_data_sheet_sample_rate);
            stationDataSheetSampleRateView.setText(String.valueOf(mStation.getSampleRate()));
            mStationDataSheetSampleRateLayout.setVisibility(View.VISIBLE);
        }
        // fill and show bit rate bottom sheet view
        if (mStation.getBitrate() > 0) {
            TextView stationDataSheetBitRateView = (TextView) mRootView.findViewById(R.id.station_data_sheet_bitrate);
            stationDataSheetBitRateView.setText(String.valueOf(mStation.getBitrate()));
            mStationDataSheetBitRateLayout.setVisibility(View.VISIBLE);
        }
    }
























//    /* Handles adding, deleting and renaming of station */
//    private void handleCollectionChanges(Intent intent) {
//        switch (intent.getIntExtra(EXTRA_COLLECTION_CHANGE, 1)) {
//            // CASE: station was renamed
//            case STATION_RENAMED:
//                if (intent.hasExtra(EXTRA_STATION_NEW_NAME) && intent.hasExtra(EXTRA_STATION) && intent.hasExtra(EXTRA_STATION_ID)) {
//                    Station station = intent.getParcelableExtra(EXTRA_STATION);
//                    int stationId = intent.getIntExtra(EXTRA_STATION_ID, 0);
//                    mStationNameView.setText(station.getStationName());
//                    if (mPlayback != PLAYBACK_STATE_STOPPED) {
//                        NotificationHelper.update(station, null);
//                    }
//                }
//                break;
//
//            // CASE: station was deleted
//            case STATION_DELETED:
//                if (mPlayback != PLAYBACK_STATE_STOPPED) {
//                    // stop player service and notification using intent
//                    Intent i = new Intent(mActivity, PlayerService.class);
//                    i.setAction(ACTION_DISMISS);
//                    mActivity.startService(i);
//                    LogHelper.v(LOG_TAG, "Stopping player service.");
//                }
//
//                if (!mTwoPane && mVisibility) {
//                    // start main activity
//                    Intent mainActivityStartIntent = new Intent(mActivity, MainActivity.class);
//                    startActivity(mainActivityStartIntent);
//                    // finish player activity
//                    // mActivity.finish();
//                }
//                // two pane behaviour is handles by the adapter
//                break;
//        }
//    }

//    /* Initializes broadcast receivers for onCreate */
//    private void initializeBroadcastReceivers() {
//
//        // RECEIVER: state of playback has changed
//        mPlaybackStateChangedReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                if (intent.hasExtra(EXTRA_PLAYBACK_STATE_CHANGE)) {
//                    handlePlaybackStateChanges(intent);
//                }
//            }
//        };
//        IntentFilter playbackStateChangedIntentFilter = new IntentFilter(ACTION_PLAYBACK_STATE_CHANGED);
//        LocalBroadcastManager.getInstance(mActivity).registerReceiver(mPlaybackStateChangedReceiver, playbackStateChangedIntentFilter);
//
//        // RECEIVER: station added, deleted, or changed
//        mCollectionChangedReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                if (intent != null && intent.hasExtra(EXTRA_COLLECTION_CHANGE)) {
//                    handleCollectionChanges(intent);
//                }
//            }
//        };
//        IntentFilter collectionChangedIntentFilter = new IntentFilter(ACTION_COLLECTION_CHANGED);
//        LocalBroadcastManager.getInstance(mActivity).registerReceiver(mCollectionChangedReceiver, collectionChangedIntentFilter);
//
//        // RECEIVER: station metadata has changed
//        mMetadataChangedReceiver = new BroadcastReceiver() {
//            @Override
//            public void onReceive(Context context, Intent intent) {
//                if (mPlayback != PLAYBACK_STATE_STOPPED && intent.hasExtra(EXTRA_METADATA)) {
////                    mStationMetadata = intent.getStringExtra(EXTRA_METADATA);
////                    mStationMetadataView.setText(mStationMetadata);
////                    mStationMetadataView.setSelected(true);
////                    mStationDataSheetMetadata.setText(mStationMetadata);
//                }
//            }
//        };
//        IntentFilter metadataChangedIntentFilter = new IntentFilter(ACTION_METADATA_CHANGED);
//        LocalBroadcastManager.getInstance(mActivity).registerReceiver(mMetadataChangedReceiver, metadataChangedIntentFilter);
//
//    }


//    /* Unregisters broadcast receivers */
//    private void unregisterBroadcastReceivers() {
//        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mPlaybackStateChangedReceiver);
//        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mCollectionChangedReceiver);
//        LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mMetadataChangedReceiver);
//    }


//    /* Handles changes in state of playback, eg. start, stop, loading stream */
//    private void handlePlaybackStateChanges(Intent intent) {
//
//        // get station from intent
//        Station station = intent.getParcelableExtra(EXTRA_STATION);
//
//        switch (intent.getIntExtra(EXTRA_PLAYBACK_STATE_CHANGE, 1)) {
//
//            // CASE: player is preparing stream
//            case PLAYBACK_STATE_LOADING_STATION:
//                if (mVisibility && mPlayback != PLAYBACK_STATE_LOADING_STATION && mStation != null && mStation.getStreamUri().equals(station.getStreamUri())) {
//                    // set playback true
//                    mPlayback = PLAYBACK_STATE_LOADING_STATION;
//                    mStation.setPlaybackState(PLAYBACK_STATE_LOADING_STATION);
//                    // rotate playback button
//                    changeVisualState(mActivity);
//                }
//                break;
//
//            // CASE: playback has started
//            case PLAYBACK_STATE_STARTED:
//                if (mVisibility && mPlayback != PLAYBACK_STATE_STARTED && mStation != null && mStation.getStreamUri().equals(station.getStreamUri())) {
//                    loadAppState(mActivity);
//                    // update loading status and playback indicator
//                    mPlaybackIndicator.setBackgroundResource(R.drawable.ic_playback_indicator_started_24dp);
//                    // fill metadata views
//                    mStationMetadataView.setText(mStation.getMetadata());
//                    mStationDataSheetMetadata.setText(mStation.getMetadata());
//
////                    if (mStation.getMetadata() != null) {
////                        mStationMetadataView.setText(mStation.getMetadata());
////                        mStationDataSheetMetadata.setText(mStation.getMetadata());
////                    } else {
////                        mStationMetadataView.setText(mStation.getStationName());
////                        mStationDataSheetMetadata.setText(mStationName);
////                    }
//                    // update and display extended meta data
////                    mStationMimeType = station.getMimeType();
////                    mStationChannelCount = station.getChannelCount();
////                    mStationSampleRate = station.getSampleRate();
////                    mStationBitRate = station.getBitrate();
//                    displayExtendedMetaData();
//
//                    mStationMetadataView.setSelected(true);
//                }
//                break;
//
//            // CASE: playback was stopped
//            case PLAYBACK_STATE_STOPPED:
//                if (mVisibility && mPlayback != PLAYBACK_STATE_STOPPED && mStation != null && mStation.getStreamUri().equals(station.getStreamUri())) {
//                    // set playback falseMediaB
//                    mPlayback = PLAYBACK_STATE_STOPPED;
//                    // rotate playback button
//                    if (intent.hasExtra(EXTRA_STATION_ID)) {
//                        mStation.setPlaybackState(PLAYBACK_STATE_STOPPED);
//                        changeVisualState(mActivity);
//                    }
//                }
//                break;
//        }
//    }

}