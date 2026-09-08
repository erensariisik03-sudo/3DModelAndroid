package com.eren.maxxumviewer;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Temporary renderer: a clean, no-UI 360° tractor preview.
 * The shipped GIANTS asset is kept under assets/tractor_source and can replace
 * this procedural mesh after conversion to glTF/GLB.
 */
public class TractorRenderer implements GLSurfaceView.Renderer {
    private int program;
    private FloatBuffer cube;
    private float[] proj = new float[16], view = new float[16], model = new float[16], mvp = new float[16];
    private long start;
    private final String vs = "attribute vec3 aPos; uniform mat4 uMvp; void main(){gl_Position=uMvp*vec4(aPos,1.0);}";
    private final String fs = "precision mediump float; uniform float uShade; void main(){gl_FragColor=vec4(0.74*uShade,0.09*uShade,0.04*uShade,1.0);}";

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config){
        GLES20.glClearColor(0.07f,0.08f,0.10f,1f);
        program = makeProgram(vs, fs);
        cube = makeCube();
        start = System.nanoTime();
    }
    @Override public void onSurfaceChanged(GL10 gl, int w, int h){
        GLES20.glViewport(0,0,w,h);
        float ratio=(float)w/h;
        Matrix.perspectiveM(proj,0,35f,ratio,0.1f,100f);
    }
    @Override public void onDrawFrame(GL10 gl){
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        Matrix.setLookAtM(view,0,0f,2.0f,7.0f,0f,0.8f,0f,0f,1f,0f);
        float sec=(System.nanoTime()-start)/1_000_000_000f;
        drawTractor(sec*12f);
    }
    private void drawTractor(float angle){
        GLES20.glUseProgram(program);
        int pos=GLES20.glGetAttribLocation(program,"aPos");
        int umvp=GLES20.glGetUniformLocation(program,"uMvp");
        int shade=GLES20.glGetUniformLocation(program,"uShade");
        cube.position(0); GLES20.glEnableVertexAttribArray(pos); GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,cube);
        // Main body and hood
        box(0,0.9f,0,2.2f,0.75f,3.0f,umvp,shade,1.0f,angle);
        box(0,1.45f,0.15f,1.65f,0.75f,1.35f,umvp,shade,1.08f,angle);
        box(0,1.95f,0.35f,1.15f,0.9f,1.1f,umvp,shade,1.16f,angle);
        // Wheels as chunky boxes; enough for preview until GLB import is wired.
        wheel(-1.35f,0.65f,0.95f,0.9f,1.5f,angle); wheel(1.35f,0.65f,0.95f,0.9f,1.5f,angle);
        wheel(-1.45f,0.5f,-0.9f,0.65f,1.1f,angle); wheel(1.45f,0.5f,-0.9f,0.65f,1.1f,angle);
        GLES20.glDisableVertexAttribArray(pos);
    }
    private void wheel(float x,float y,float z,float w,float h,float angle){ box(x,y,z,w,h,h, GLES20.glGetUniformLocation(program,"uMvp"),GLES20.glGetUniformLocation(program,"uShade"),0.72f,angle); }
    private void box(float x,float y,float z,float sx,float sy,float sz,int umvp,int shade,float s,float angle){
        Matrix.setIdentityM(model,0); Matrix.rotateM(model,0,angle,0,1,0); Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,sx,sy,sz);
        float[] mv=new float[16]; Matrix.multiplyMM(mv,0,view,0,model,0); Matrix.multiplyMM(mvp,0,proj,0,mv,0);
        GLES20.glUniformMatrix4fv(umvp,1,false,mvp,0); GLES20.glUniform1f(shade,s);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
    }
    private FloatBuffer makeCube(){
        float[] v={-1,-1,-1, 1,-1,-1, 1,1,-1, -1,-1,-1, 1,1,-1, -1,1,-1,
                   -1,-1,1, 1,-1,1, 1,1,1, -1,-1,1, 1,1,1, -1,1,1,
                   -1,-1,-1, -1,1,-1, -1,1,1, -1,-1,-1, -1,1,1, -1,-1,1,
                    1,-1,-1, 1,1,-1, 1,1,1, 1,-1,-1, 1,1,1, 1,-1,1,
                   -1,-1,-1, -1,-1,1, 1,-1,1, -1,-1,-1, 1,-1,1, 1,-1,-1,
                   -1,1,-1, -1,1,1, 1,1,1, -1,1,-1, 1,1,1, 1,1,-1};
        ByteBuffer b=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder()); FloatBuffer f=b.asFloatBuffer(); f.put(v).position(0); return f;
    }
    private int makeProgram(String v,String f){int vs=shader(GLES20.GL_VERTEX_SHADER,v), fs=shader(GLES20.GL_FRAGMENT_SHADER,f), p=GLES20.glCreateProgram();GLES20.glAttachShader(p,vs);GLES20.glAttachShader(p,fs);GLES20.glLinkProgram(p);return p;}
    private int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
}
