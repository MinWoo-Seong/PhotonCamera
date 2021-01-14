package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLBasePipeline;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.parameters.ResolutionSolution;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.nio.ByteBuffer;

public class PostPipeline extends GLBasePipeline {
    public ByteBuffer stackFrame;
    public ByteBuffer lowFrame;
    public ByteBuffer highFrame;
    public GLTexture FusionMap;
    public GLTexture GainMap;
    public float[] analyzedBL = new float[]{0.f,0.f,0.f};;
    float regenerationSense = 1.f;
    float AecCorr = 1.f;
    public int getRotation() {
        int rotation = PhotonCamera.getParameters().cameraRotation;
        String TAG = "ParseExif";
        Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
        Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCaptureController().mSensorOrientation);
        return rotation;
    }
    @SuppressWarnings("SuspiciousNameCombination")
    private Point getRotatedCoords(Point in){
        switch (getRotation()){
            case 0:
            case 180:
                return in;
            case 90:
            case 270:
                return new Point(in.y,in.x);
        }
        return in;
    }
    public Bitmap Run(ByteBuffer inBuffer, Parameters parameters){
        mParameters = parameters;
        mSettings = PhotonCamera.getSettings();
        Point rotated = getRotatedCoords(parameters.rawSize);
        if(PhotonCamera.getSettings().energySaving || mParameters.rawSize.x*mParameters.rawSize.y < ResolutionSolution.smallRes){
            GLDrawParams.TileSize = 8;
        } else {
            GLDrawParams.TileSize = 256;
        }
        /*if (PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT) {
            rotated.x/=2;
            rotated.y/=2;
        }*/
        Bitmap output = Bitmap.createBitmap(rotated.x,rotated.y, Bitmap.Config.ARGB_8888);

        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(rotated,output, new GLFormat(GLFormat.DataType.UNSIGNED_8,4));
        glint = new GLInterface(glproc);
        stackFrame = inBuffer;
        glint.parameters = parameters;
        add(new Bayer2Float());

        add(new ExposureFusionBayer2());
        if(!IsoExpoSelector.HDR) {
            if (PhotonCamera.getSettings().cfaPattern != 4) {
                //if (PhotonCamera.getSettings().selectedMode != CameraMode.NIGHT) {
                    add(new Demosaic());
                //} else {
                //    add(new BinnedDemosaic());
                //}
            } else {
                add(new MonoDemosaic());
            }
        } else {
            add(new LFHDR());
        }
        /*
         * * * All filters after demosaicing * * *
         */

        if(PhotonCamera.getSettings().hdrxNR) {
            add(new SmartNR());
        }
        add(new DynamicBL());
        //add(new GlobalToneMapping(0,"GlobalTonemap"));

        add(new Initial());

        //add(new AWB());

        add(new Equalization());

        add(new Sharpen(R.raw.sharpeningbilateral));
        add(new RotateWatermark(getRotation()));
        return runAll();
    }
}