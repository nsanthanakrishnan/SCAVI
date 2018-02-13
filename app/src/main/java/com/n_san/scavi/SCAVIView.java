package com.n_san.scavi;

/**
 * Created by n_san on 28-11-2017.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public class SCAVIView extends View {
    private float[] DepthValue = new float[9];
    private float[] eachPixel = new float[2];
    private float[] rectangleLeft = new float[9];
    private float[] rectangleRight = new float[9];
    private float[] rectangleTop = new float[9];
    private float[] rectangleBottom = new float[9];

    private void setRectangleCoordinates(){
        for(int i = 0,x=0,y=0; i < 9; i++,x++) {
            if(i==3){
                x=0;
                y=75;
            }
            if(i==6){
                x=0;
                y=150;
            }
            rectangleLeft[i] = 6*x*58;
            rectangleRight[i] = 6*(57+(x*58));
            rectangleTop[i] = 6*y;
            rectangleBottom[i] = 6*(y+74);
        }
    }


    public void setCurrentPoint(float[] currentPointZ){
        DepthValue = currentPointZ;
        this.postInvalidate();
    }

    Paint paint = new Paint();
    public SCAVIView(Context context) {
        super(context);
    }

    @Override
    public void onDraw(Canvas canvas) {
        paint.setStrokeWidth(1);
        setRectangleCoordinates();
        int z = 0;
        int color;
        for(int i = 0; i < 9; i++){
            color = (int) (DepthValue[i]);
            paint.setColor(Color.rgb(color, (int) (color-(0.4*color)), (int) (color+(0.4*color))));
            canvas.drawRect(rectangleLeft[i],rectangleTop[i], rectangleRight[i], rectangleBottom[i], paint);
        }
    }
}