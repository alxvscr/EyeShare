package com.naukma.alexveshcher.eyeshare;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.naukma.alexveshcher.eyeshare.util.Constants;
import com.naukma.alexveshcher.eyeshare.util.LogRTCListener;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import me.kevingleason.pnwebrtc.PnPeer;
import me.kevingleason.pnwebrtc.PnRTCClient;

/**
 * This chat will begin/subscribe to a video chat.
 * REQUIRED: The intent must contain a
 */
public class VideoChatActivity extends Activity {
    public String tag = "connn";
    public static final String VIDEO_TRACK_ID = "videoPN";
    public static final String AUDIO_TRACK_ID = "audioPN";
    public static final String LOCAL_MEDIA_STREAM_ID = "localStreamPN";

    public String role = "d";

    private PnRTCClient pnRTCClient;
    private VideoSource localVideoSource;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private GLSurfaceView videoView;
    private TextView mCallStatus;

    private String username;
    private boolean backPressed = false;
    private Thread  backPressedThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_chat);

        Bundle extras = getIntent().getExtras();
        role = extras.getString("ROLE");
        if (extras == null || !extras.containsKey(Constants.USER_NAME)) {
            Intent intent = new Intent(this, WaitActivity.class);
            startActivity(intent);
            Toast.makeText(this, "Need to pass username to VideoChatActivity in intent extras (Constants.USER_NAME).",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        this.username      = extras.getString(Constants.USER_NAME);
        this.mCallStatus   = (TextView) findViewById(R.id.call_status);


        Log.d("role",role);
        // First, we initiate the PeerConnectionFactory with our application context and some options.

        if(role.equals("VOLUNTEER")){
            PeerConnectionFactory.initializeAndroidGlobals(
                    this,  // Context
                    true,  // Audio Enabled
                    true,  // Video Enabled
                    true,  // Hardware Acceleration Enabled
                    null); // Render EGL Context
        }

        else   PeerConnectionFactory.initializeAndroidGlobals(
                    this,  // Context
                    true,  // Audio Enabled
                    true,  // Video Enabled
                    true,  // Hardware Acceleration Enabled
                    null); // Render EGL Context


        PeerConnectionFactory pcFactory = new PeerConnectionFactory();
        this.pnRTCClient = new PnRTCClient(Constants.PUB_KEY, Constants.SUB_KEY, this.username);

        // Returns the number of cams & front/back face device name
        int camNumber = VideoCapturerAndroid.getDeviceCount();
        //String frontFacingCam = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        String backFacingCam = VideoCapturerAndroid.getNameOfBackFacingDevice();

        // Creates a VideoCapturerAndroid instance for the device name
        VideoCapturer capturer = VideoCapturerAndroid.create(backFacingCam);

        // First create a Video Source, then we can make a Video Track
        VideoTrack localVideoTrack = null;
        if(role.equals("BLIND")){
            localVideoSource = pcFactory.createVideoSource(capturer, this.pnRTCClient.videoConstraints());
            localVideoTrack = pcFactory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource);
        }


        // First we create an AudioSource then we can create our AudioTrack
        AudioSource audioSource = pcFactory.createAudioSource(this.pnRTCClient.audioConstraints());
        AudioTrack localAudioTrack = pcFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);

        // To create our VideoRenderer, we can use the included VideoRendererGui for simplicity
        // First we need to set the GLSurfaceView that it should render to
        this.videoView = (GLSurfaceView) findViewById(R.id.gl_surface);

        // Then we set that view, and pass a Runnable to run once the surface is ready
        VideoRendererGui.setView(videoView, null);

        // Now that VideoRendererGui is ready, we can get our VideoRenderer.
        // IN THIS ORDER. Effects which is on top or bottom
        //remoteRender = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
        if(role.equals("VOLUNTEER")){
            remoteRender = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
            //localRender = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);
        }
        else
            localRender = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);

        // We start out with an empty MediaStream object, created with help from our PeerConnectionFactory
        //  Note that LOCAL_MEDIA_STREAM_ID can be any string
        MediaStream mediaStream = pcFactory.createLocalMediaStream(LOCAL_MEDIA_STREAM_ID);

        // Now we can add our tracks.
        if(role.equals("BLIND"))
            mediaStream.addTrack(localVideoTrack);
        mediaStream.addTrack(localAudioTrack);

        // First attach the RTC Listener so that callback events will be triggered
        this.pnRTCClient.attachRTCListener(new DemoRTCListener());

        // Then attach your local media stream to the PnRTCClient.
        //  This will trigger the onLocalStream callback.
        this.pnRTCClient.attachLocalMediaStream(mediaStream);

        // Listen on a channel. This is your "phone number," also set the max chat users.
        this.pnRTCClient.listenOn("levi");
        this.pnRTCClient.setMaxConnections(5);

        // If the intent contains a number to dial, call it now that you are connected.
        //  Else, remain listening for a call.
        if (extras.containsKey(Constants.CALL_USER)) {
            String callUser = extras.getString(Constants.CALL_USER);
            Toast.makeText(getApplicationContext(),username + "->" +callUser ,Toast.LENGTH_SHORT).show();
            connectToUser(callUser);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_video_chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.videoView.onPause();
        if(role.equals("BLIND"))
            this.localVideoSource.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.videoView.onResume();
        if(role.equals("BLIND"))
            this.localVideoSource.restart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.localVideoSource != null) {
            this.localVideoSource.stop();
        }
        if (this.pnRTCClient != null) {
            this.pnRTCClient.onDestroy();
        }
    }

    @Override
    public void onBackPressed() {
        if (!this.backPressed){
            this.backPressed = true;
            Toast.makeText(this,"Press back again to end.",Toast.LENGTH_SHORT).show();
            this.backPressedThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(5000);
                        backPressed = false;
                    } catch (InterruptedException e){ Log.d("VCA-oBP","Successfully interrupted"); }
                }
            });
            this.backPressedThread.start();
            return;
        }
        if (this.backPressedThread != null)
            this.backPressedThread.interrupt();
        super.onBackPressed();
    }

    public void connectToUser(String user) {
        this.pnRTCClient.connect(user);
    }

    public void hangup(View view) {
        this.pnRTCClient.closeAllConnections();
        endCall();
    }

    private void endCall() {
        Intent intent;
        if(role.equals("BLIND")){
            intent = new Intent(VideoChatActivity.this, ChooseActivity.class);
        }
        else {
            intent = new Intent(VideoChatActivity.this, WaitActivity.class);
            intent.putExtra(Constants.USER_NAME,username);
        }
        startActivity(intent);
        finish();
    }



    /**
     * LogRTCListener is used for debugging purposes, it prints all RTC messages.
     * DemoRTC is just a Log Listener with the added functionality to append screens.
     */
    private class DemoRTCListener extends LogRTCListener {
        @Override
        public void onLocalStream(final MediaStream localStream) {
            super.onLocalStream(localStream); // Will log values
            VideoChatActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(role.equals("BLIND")) {
                        if(localStream.videoTracks.size()==0) return;
                        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
                    }
                    Log.d(tag+"onLocalStream ok","onLocalStream ok");
                }
            });
        }

        @Override
        public void onAddRemoteStream(final MediaStream remoteStream, final PnPeer peer) {
            super.onAddRemoteStream(remoteStream, peer); // Will log values
            VideoChatActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(VideoChatActivity.this,"Connected to " + peer.getId(), Toast.LENGTH_SHORT).show();
                    try {
                        if(remoteStream.audioTracks.size()==0 || remoteStream.videoTracks.size()==0) return;
                        mCallStatus.setVisibility(View.GONE);
                        if(role.equals("VOLUNTEER")){
                            remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
                            VideoRendererGui.update(remoteRender, 0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
                        }
                        else VideoRendererGui.update(localRender, 0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
                    }
                    catch (Exception e){
                        Log.d(tag,e.toString());
                        Toast.makeText(getApplicationContext(),e.toString() + peer.getId(), Toast.LENGTH_SHORT).show(); }
                }
            });
        }



        @Override
        public void onPeerConnectionClosed(PnPeer peer) {
            super.onPeerConnectionClosed(peer);
            VideoChatActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCallStatus.setText("Call Ended...");
                    mCallStatus.setVisibility(View.VISIBLE);
                }
            });
            try {Thread.sleep(1500);} catch (InterruptedException e){e.printStackTrace();}
            endCall();
        }
    }
}