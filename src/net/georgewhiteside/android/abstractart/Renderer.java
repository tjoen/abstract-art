package net.georgewhiteside.android.abstractart;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import net.georgewhiteside.android.abstractart.Wallpaper;
import net.georgewhiteside.android.abstractart.Wallpaper.AbstractArtEngine;

import org.jf.GLWallpaper.GLWallpaperService;

// float refreshrate = getWindowManager().getDefaultDisplay().getRefreshRate();

// "The PowerVR 530/535 is very slow. Andreno 200 and PowerVR 530/535 are first GPU generation
// (OpenGL ES 2.x) for hdpi resolution. You can't redraw a full screen at 60FPS with a simple texture."

public class Renderer implements GLWallpaperService.Renderer, GLSurfaceView.Renderer
{
	private static final String TAG = "Renderer";
	private Context context;
	
	private SharedPreferences sharedPreferences;
	
	private BattleBackground bbg;
	private Enemy enemy;
	private ShaderFactory shader;
	
	private FPSCounter mFPSCounter = new FPSCounter();
	
	private FloatBuffer quadVertexBuffer;
	private FloatBuffer textureVertexBuffer;
	private FloatBuffer textureOutputBuffer;
	
	private int mProgram, hFXProgram;
	private int mPositionHandle, hPosition;
	private int mTextureHandle, hTexture;
	private int mBaseMapTexId;
	private int mBg3TextureLoc, hTextureA;
	private int mBg4TextureLoc;
	private int hBaseMap;
	private int mPaletteLoc;
	
	private int mPaletteRotation;
	
	private int mResolutionLoc;
	private int mCompressionLoc;
	private int mBg3DistTypeLoc, mBg4DistTypeLoc;
	private int mBg3DistLoc, mBg4DistLoc;
	private int mDistTypeLoc;
	private int mBg3Scroll, mBg4Scroll;
	private int mBg3PaletteLoc, mBg4PaletteLoc;
	private int mBg3CompressionLoc, mBg4CompressionLoc;
	private int mBg3RotationLoc, mBg4RotationLoc;
	private int mCycleTypeLoc;
	
	private int mSurfaceWidth;
	private int mSurfaceHeight;
	private float mRenderWidth;
	private float mRenderHeight;
	
	private int hMVPMatrix;
	private float[] mProjMatrix = new float[16];
	
	private int[] mFramebuffer = new int[1];
	private int[] mRenderTexture = new int[1];
	
	
	private Boolean mFilterOutput = false;
	
	
	
	private int[] mTextureId = new int[3];
	private ByteBuffer mTextureA, mTextureB;
	private ByteBuffer mPalette;
	
	private int currentBackground;
	private boolean persistBackgroundSelection;
	
	public boolean isPreview;
	
	private long startTime, endTime;
	
	Random rand = new Random();
	
	private boolean mHighRes = false;
	private long frameTime = 60;
	
	private boolean mirrorVertical = false;
	
	private Object lock = new Object();
	
	public int getRomBackgroundIndex(int address)
	{
		return bbg.getRomBackgroundIndex(address);
	}
	
	public int getCacheableImagesTotal()
	{
		int images = 103; // TODO: don't hardcode this
		
		return images;
	}
	
	public int getBackgroundsTotal()
	{
		return bbg.getNumberOfBackgrounds();
	}
	
	public void cacheImage(int index)
	{
		bbg.setIndex(index);
	}
	
	public void setRandomBackground()
	{
		int number = Wallpaper.random.nextInt(bbg.getNumberOfBackgrounds() - 1) + 1;
		loadBattleBackground(number);
	}
	
	public void setPersistBackgroundSelection(boolean value)
	{
		persistBackgroundSelection = value;
	}
	
	public void setIsPreview(boolean value)
	{
		isPreview = value;
	}
	
	public Renderer(Context context)
	{
		this.context = context;
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		bbg = new BattleBackground(context);
		enemy = new Enemy(context); enemy.load(1);
		shader = new ShaderFactory(context);
		mTextureA = ByteBuffer.allocateDirect(256 * 256 * 1);
		mTextureB = ByteBuffer.allocateDirect(256 * 256 * 1);
		mPalette = ByteBuffer.allocateDirect(16 * 16 * 4);
		
		startTime = endTime = 0;
		
		isPreview = false;
		
		currentBackground = -1;
		persistBackgroundSelection = false;
	}
	
	public Renderer(Context context, boolean mirrorVertical)
	{
		this(context);
		this.mirrorVertical = mirrorVertical;
	}
	
	public Renderer(Context context, int initialBackground)
	{
		this(context);
		this.currentBackground = initialBackground;
	}
	
	public void onDrawFrame(GL10 unused)
	{
		endTime = System.currentTimeMillis();
		long delta = endTime - startTime;
		if(delta < frameTime)
		{
			try {
				Thread.sleep(frameTime - delta);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		startTime = System.currentTimeMillis();
		
		mFPSCounter.logStartFrame();
		
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0); // target screen
		GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
		mRenderWidth = mSurfaceWidth;
		mRenderHeight = mSurfaceHeight;
		
		if(mHighRes)
		{
			renderBattleBackground();
		}
		else
		{
			renderToTexture();
		}
			
		bbg.doTick();
		
		mFPSCounter.logEndFrame();
	}

	private int mSurfaceVerticalOffset = 0;
	public void onSurfaceChanged(GL10 unused, int width, int height)
	{
		
		/*
		mSurfaceWidth = width;
		mSurfaceHeight = height;
		//GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
		
		float surfaceRatio = (float) mSurfaceWidth / mSurfaceHeight;
		float textureRatio = 256.0f / 224.0f;
		
		Matrix.orthoM(mProjMatrix, 0, -surfaceRatio, surfaceRatio, -1.0f, 1.0f, 0.0f, 2.0f);	// configure projection matrix
		
		//Matrix.scaleM(mProjMatrix, 0, 1, 224.0f / 256.0f, 1); // scale it vertically to match the 256x224 texture
		//Matrix.scaleM(mProjMatrix, 0, 256.0f / 224.0f, 256.0f / 224.0f, 1); // expand x and y to fill output
		Matrix.scaleM(mProjMatrix, 0, textureRatio, 1, 1);
		*/
		
		
		
		mSurfaceWidth = width;
		mSurfaceHeight = height;
		
		//GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight); // does nothing; gets set later
		
		// TODO: this was done all sloppy; the output looks acceptable (it's only marginally off), but it's liable
		// to break down the road on different screens... this needs to be corrected at some point
		
		float surfaceRatio = (float) mSurfaceWidth / mSurfaceHeight;
		float textureRatio = 256.0f / 224.0f;
		
		if(surfaceRatio == textureRatio) // thumbnail
		{
			Matrix.orthoM(mProjMatrix, 0, -1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f);
		}
		else if(surfaceRatio < textureRatio)
		{
			boolean letterbox = false;
			
			if(letterbox)
			{
				// letter box output (scale height to nearest multiple of 224 < screen height)
				
				if(height >= 224 )
				{
					int multiples = mSurfaceHeight / 224;
					
					int bestFit = multiples * 224;
					
					float ratio = (float)mSurfaceHeight;
					
					mSurfaceWidth = (int)(width);
					mSurfaceHeight = bestFit;
					mSurfaceVerticalOffset = (height - bestFit) / 2;
				}
				
				
				Matrix.orthoM(mProjMatrix, 0, -surfaceRatio, surfaceRatio, -1.0f, 1.0f, -1.0f, 1.0f);	// configure projection matrix
				
				//Matrix.scaleM(mProjMatrix, 0, 1.0f, 1.0f, 1.0f);
				
			}
			else
			{
				Matrix.orthoM(mProjMatrix, 0, -surfaceRatio / textureRatio, surfaceRatio / textureRatio, -1.0f, 1.0f, -1.0f, 1.0f);	// configure projection matrix
			}
			
			
		}
		else
		{
			int multiples = mSurfaceHeight / 224;
			
			int bestFit = multiples * 224;
			
			float ratio = (float)mSurfaceHeight;
			
			mSurfaceWidth = (int)(width);
			mSurfaceHeight = bestFit;
			mSurfaceVerticalOffset = (height - bestFit) / 2;
			
			Matrix.orthoM(mProjMatrix, 0, -surfaceRatio, surfaceRatio, -1.0f, 1.0f, -1.0f, 1.0f);
			Matrix.scaleM(mProjMatrix, 0, textureRatio, 1, 1);
		}
	}
	
	private void setupQuad()
	{
		float quadVertices[] =
		{
			-1.0f,	-1.0f,	 0.0f,
			 1.0f,	-1.0f,	 0.0f,
			-1.0f,	 1.0f,	 0.0f,
			 1.0f,	 1.0f,	 0.0f			 
		};
		
		float textureMap[] =
		{
				0.0f,	 0.875f,
				 1.0f,	 0.875f,
				 0.0f,	 0.0f,
				 1.0f,	 0.0f 
		};
		
		float textureMapFlip[] =
		{
				0.0f,	 0.0f,
				 1.0f,	 0.0f,
				 0.0f,	 0.875f,
				 1.0f,	 0.875f 
		};

		quadVertexBuffer = ByteBuffer
				.allocateDirect(quadVertices.length * 4) // float is 4 bytes
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer(); 
		quadVertexBuffer.put(quadVertices);
		quadVertexBuffer.position(0);
		
		textureVertexBuffer = ByteBuffer
				.allocateDirect(textureMap.length * 4) // float is 4 bytes
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer(); 
		textureVertexBuffer.put(textureMap);
		textureVertexBuffer.position(0);
		
		textureOutputBuffer = ByteBuffer
				.allocateDirect(textureMap.length * 4) // float is 4 bytes
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer(); 
		textureOutputBuffer.put(textureMapFlip);
		textureOutputBuffer.position(0);
		
	}

	public void onSurfaceCreated( GL10 unused, EGLConfig config )
	{
		//queryGl(unused);
		
		setupQuad();
		
		GLES20.glClearColor( 0.0f, 0.0f, 0.0f, 1.0f );	// set surface background color
		GLES20.glDisable(GLES20.GL_DITHER); // dithering causes really crappy/distracting visual artifacts when distorting the textures
		
		GLES20.glGenFramebuffers(1, mFramebuffer, 0);
		GLES20.glGenTextures(1, mRenderTexture, 0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mRenderTexture[0]);
		GLES20.glTexImage2D( GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 256, 256, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null );//GLES20.GL_UNSIGNED_SHORT_5_6_5, null ); //GLES20.GL_UNSIGNED_BYTE, null );
		int filter = mFilterOutput ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
		GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter );
		GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR );
		GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE );
		GLES20.glTexParameteri( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE );
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer[0]); // do I need to do this here?
		GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mRenderTexture[0], 0); // specify texture as color attachment
		
		if(isPreview)
		{
			setRandomBackground();
		}
		else if(persistBackgroundSelection && currentBackground >= 0 && currentBackground < getBackgroundsTotal())
		{
			loadBattleBackground(currentBackground);
		}
		else
		{
			//setRandomBackground();
			Wallpaper.setNewBackground(this);
		}
		
		/* shader for final output texture (the "low res") output */
		
		hFXProgram = createProgram(readTextFile(R.raw.passthrough_vert), readTextFile(R.raw.passthrough_frag));
		if(hFXProgram == 0) { throw new RuntimeException("[...] shader compilation failed"); }
		
		hPosition = GLES20.glGetAttribLocation(hFXProgram, "a_position"); // a_position
		hTexture = GLES20.glGetAttribLocation(hFXProgram, "a_texCoord"); // a_texCoord
		hBaseMap = GLES20.glGetUniformLocation(hFXProgram, "s_texture"); // get sampler locations
		
		// used when rendering to the GLOffscreenSurface to mirror screenshots about the horizontal axis
		
		if(mirrorVertical) {
			Matrix.scaleM(mProjMatrix, 0, 1, -1, 1);
		}
		
		// handle the rendering knobs
		
		frameTime = 1000 / sharedPreferences.getInt("intFramerate", 60); // SharedPreference
		mHighRes = sharedPreferences.getBoolean("boolNativeResolution", false); // SharedPreference
	}
	
	private void updateShaderVariables()
	{
		// glUniform* calls always act on the current program that is bound with glUseProgram
		// have this method take an argument to determine which program to apply to
		
		Layer bg3 = bbg.getBg3();
		Layer bg4 = bbg.getBg4();
		
		// update shader resolution
		
		GLES20.glUniform2f(mResolutionLoc, mRenderWidth, mRenderHeight);
		
		// update distortion effect variables for the shader program
		
		GLES20.glUniform1i(mBg3DistTypeLoc, bg3.distortion.getType());
		GLES20.glUniform1i(mBg4DistTypeLoc, bg4.distortion.getType());
		GLES20.glUniform3f(mBg3DistLoc, bg3.distortion.computeShaderAmplitude(), bg3.distortion.computeShaderFrequency(), bg3.distortion.computeShaderSpeed());
		GLES20.glUniform3f(mBg4DistLoc, bg4.distortion.computeShaderAmplitude(), bg4.distortion.computeShaderFrequency(), bg4.distortion.computeShaderSpeed());
		GLES20.glUniform1f(mBg3CompressionLoc, bg3.distortion.computeShaderCompression());
		GLES20.glUniform1f(mBg4CompressionLoc, bg4.distortion.computeShaderCompression());
		
		// update translation effect variables for the shader program
		
		GLES20.glUniform2f(mBg3Scroll, bg3.translation.getHorizontalOffset(), bg3.translation.getVerticalOffset());
		GLES20.glUniform2f(mBg4Scroll, bg4.translation.getHorizontalOffset(), bg4.translation.getVerticalOffset());
		
		// update palette
		
		GLES20.glUniform4f(mBg3PaletteLoc, (float)bg3.getPaletteCycle1Begin(), (float)bg3.getPaletteCycle1End(), (float)bg3.getPaletteCycle2Begin(), (float)bg3.getPaletteCycle2End());
		GLES20.glUniform4f(mBg4PaletteLoc, (float)bg4.getPaletteCycle1Begin(), (float)bg4.getPaletteCycle1End(), (float)bg4.getPaletteCycle2Begin(), (float)bg4.getPaletteCycle2End());
		GLES20.glUniform1f(mBg3RotationLoc, (float)bg3.getPaletteRotation());
		GLES20.glUniform1f(mBg4RotationLoc, (float)bg4.getPaletteRotation());
		
		// old stuff
		GLES20.glUniform2i(mDistTypeLoc, bg3.distortion.getType(), bg4.distortion.getType());
		GLES20.glUniform2i(mCycleTypeLoc, bg3.getPaletteCycleType(), bg4.getPaletteCycleType());
	}
	
	public void loadBattleBackground(int index)
	{	
		synchronized(lock) {
			currentBackground = index;
			
			bbg.setIndex(index);
			
			byte[] dataA = bbg.getBg3().getImage();
			byte[] dataB = bbg.getBg4().getImage();
			byte[] paletteBg3 = bbg.getBg3().getPalette();
			byte[] paletteBg4 = bbg.getBg4().getPalette();
			int filter = mFilterOutput ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
			
			//bbg.layerA.distortion.dump(0);
			//bbg.layerA.translation.dump(0);
			
			boolean enablePaletteEffects = sharedPreferences.getBoolean("enablePaletteEffects", true); // SharedPreference
			
			int bufferSize;
			int format;
			if(enablePaletteEffects == true) {
				bufferSize = 256 * 256 * 1;
				format = GLES20.GL_LUMINANCE;
			} else {
				bufferSize = 256 * 256 * 4;
				format = GLES20.GL_RGBA;
			}
			
			if(mTextureA.capacity() != bufferSize) {
				mTextureA = ByteBuffer.allocateDirect(bufferSize);
				mTextureB = ByteBuffer.allocateDirect(bufferSize);
			}
			
	        mTextureA.put(dataA).position(0);
	        mTextureB.put(dataB).position(0);
	        
	        mPalette.position(0);
	        mPalette.put(paletteBg3).position(0);
	        
	        mPalette.position(16 * 1 * 4);
	        mPalette.put(paletteBg4).position(0);
	        
	        GLES20.glGenTextures(3, mTextureId, 0);
	        
	        // BG3 background layer
	        
	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId[0]);
	
	        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, 256, 256, 0, format, GLES20.GL_UNSIGNED_BYTE, mTextureA);
	        
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
	        
	        // BG4 background layer
	
	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId[1]);
	
	        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, format, 256, 256, 0, format, GLES20.GL_UNSIGNED_BYTE, mTextureB);
	
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
	        
	        // palettes
	        
	        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId[2]);
	
	        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 16, 16, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPalette);
	
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
	        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
	        
	        
	         
	        
	        
	        /* shader for effects, update program uniforms */
			
			mProgram = shader.getShader(bbg);
			
			//mProgram = createProgram(readTextFile(R.raw.aspect_vert), readTextFile(R.raw.distortion_frag));
			if(mProgram == 0) { throw new RuntimeException("[...] shader compilation failed"); }
			
			mPositionHandle = GLES20.glGetAttribLocation(mProgram, "a_position"); // a_position
			mTextureHandle = GLES20.glGetAttribLocation(mProgram, "a_texCoord"); // a_texCoord
			mBg3TextureLoc = GLES20.glGetUniformLocation(mProgram, "bg3_texture"); // get sampler locations
			mBg4TextureLoc = GLES20.glGetUniformLocation(mProgram, "bg4_texture"); // get sampler locations
			mPaletteLoc = GLES20.glGetUniformLocation(mProgram, "s_palette");
			
			mResolutionLoc = GLES20.glGetUniformLocation(mProgram, "resolution");
			mBg3DistTypeLoc = GLES20.glGetUniformLocation(mProgram, "bg3_dist_type");
			mBg4DistTypeLoc = GLES20.glGetUniformLocation(mProgram, "bg4_dist_type");
			mBg3DistLoc = GLES20.glGetUniformLocation(mProgram, "bg3_dist");
			mBg4DistLoc = GLES20.glGetUniformLocation(mProgram, "bg4_dist");
			mBg3Scroll = GLES20.glGetUniformLocation(mProgram, "bg3_scroll");
			mBg4Scroll = GLES20.glGetUniformLocation(mProgram, "bg4_scroll");
			mBg3PaletteLoc = GLES20.glGetUniformLocation(mProgram, "bg3_palette");
			mBg4PaletteLoc = GLES20.glGetUniformLocation(mProgram, "bg4_palette");
			mBg3CompressionLoc = GLES20.glGetUniformLocation(mProgram, "bg3_compression");
			mBg3RotationLoc = GLES20.glGetUniformLocation(mProgram, "bg3_rotation");
			mBg4CompressionLoc = GLES20.glGetUniformLocation(mProgram, "bg4_compression");
			mBg4RotationLoc = GLES20.glGetUniformLocation(mProgram, "bg4_rotation");
			
			// old stuff
			mCycleTypeLoc = GLES20.glGetUniformLocation(mProgram, "u_cycle_type");
			mDistTypeLoc = GLES20.glGetUniformLocation(mProgram, "u_dist_type");
		}
	}
	
	private void renderToTexture() // "low res" render
	{
		mRenderWidth = 256.0f;
		mRenderHeight = 224.0f;
		
		GLES20.glViewport(0, 0, 256, 224);	// render to native texture size, scale up later
		
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer[0]);
		
		/* it may be prudent to check the framebuffer status here before continuing... */
		
		renderBattleBackground();
		
		/* now, try to render the texture? */
		
		GLES20.glUseProgram(hFXProgram);
		
		GLES20.glViewport(0, mSurfaceVerticalOffset, mSurfaceWidth, mSurfaceHeight);		// now we're scaling the framebuffer up to size
		
		hMVPMatrix = GLES20.glGetUniformLocation(hFXProgram, "uMVPMatrix");/* projection and camera */
		
		/* load vertex positions */
		
		GLES20.glVertexAttribPointer(hPosition, 3, GLES20.GL_FLOAT, false, 12, quadVertexBuffer);
		GLES20.glEnableVertexAttribArray(hPosition);
		
		/* load texture mapping */

		GLES20.glVertexAttribPointer(hTexture, 2, GLES20.GL_FLOAT, false, 8, textureOutputBuffer);
		GLES20.glEnableVertexAttribArray(hTexture);
		
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mRenderTexture[0]);
		
		GLES20.glUniform1i(hBaseMap, 0);
		
		GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
		
		GLES20.glUniformMatrix4fv(hMVPMatrix, 1, false, mProjMatrix, 0);	/* projection and camera */
		
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
	}
	
	private void renderBattleBackground() // "high res" render
	{
		hMVPMatrix = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");/* projection and camera */
		
		//GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0); // render to screen buffer
		
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
		
		GLES20.glUseProgram(mProgram);
		
		/* load vertex positions */
		
		GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 12, quadVertexBuffer);
		GLES20.glEnableVertexAttribArray(mPositionHandle);
		
		/* load texture mapping */

		GLES20.glVertexAttribPointer(mTextureHandle, 2, GLES20.GL_FLOAT, false, 8, textureVertexBuffer);
		GLES20.glEnableVertexAttribArray(mTextureHandle);
		
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId[0]);
		GLES20.glUniform1i(mBg3TextureLoc, 0);
		
		GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId[1]);
		GLES20.glUniform1i(mBg4TextureLoc, 1);
		
		GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId[2]);
		GLES20.glUniform1i(mPaletteLoc, 2);
		
		updateShaderVariables(); // be mindful of which active program this applies to!!
		
		/* apply model view projection transformation */
		
		GLES20.glUniformMatrix4fv(hMVPMatrix, 1, false, mProjMatrix, 0);	/* projection and camera */
		
		/* draw the triangles */
		
		GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
	}
	
	private void checkGlError(String op)
	{
		/* from developer.android.com */
		int error;
		while((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(TAG, op + ": glError " + error);
			throw new RuntimeException(op + ": glError " + error);
		}
	}
	
	private int createProgram(String vertexSource, String fragmentSource)
	{
		// courtesy of android.developer.com
		int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
		if(vertexShader == 0) {
			return 0;
		}

		int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
		if(pixelShader == 0) {
			return 0;
		}

		int program = GLES20.glCreateProgram();
		if(program != 0) {
			GLES20.glAttachShader(program, vertexShader);
			checkGlError("glAttachShader");
			GLES20.glAttachShader(program, pixelShader);
			checkGlError("glAttachShader");
			GLES20.glLinkProgram(program);
			int[] linkStatus = new int[1];
			GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
			if(linkStatus[0] != GLES20.GL_TRUE) {
				Log.e(TAG, "Could not link program: ");
				Log.e(TAG, GLES20.glGetProgramInfoLog(program));
				GLES20.glDeleteProgram(program);
				program = 0;
			}
		}
		return program;
	}
	
	private int loadShader(int shaderType, String source)
	{
		
//	int loadShader(int type, String code) {
//		int shader = GLES20.glCreateShader(type);
//		GLES20.glShaderSource(shader, code);
//		GLES20.glCompileShader(shader);
//		return shader;
//	}
		
		int shader = GLES20.glCreateShader(shaderType);
		if(shader != 0) {
			GLES20.glShaderSource(shader, source);
			GLES20.glCompileShader(shader);
			int[] compiled = new int[1];
			GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
			if(compiled[0] == 0) {
				Log.e(TAG, "Could not compile shader " + shaderType + ":");
				Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
				GLES20.glDeleteShader(shader);
				shader = 0;
			}
		}
		return shader;
	}
	
	private String readTextFile(final int resourceId)
	{
		/* method lifted from learnopengles.com */
		final InputStream inputStream = context.getResources().openRawResource(resourceId);
		final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
		final BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
 
		String nextLine;
		final StringBuilder body = new StringBuilder();
		 
		try
		{
			while ((nextLine = bufferedReader.readLine()) != null)
			{
				body.append(nextLine);
				body.append('\n');
			}
		}
		catch (IOException e)
		{
			return null;
		}
 
		return body.toString();
	}
	
	private void queryGl(GL10 gl10)
	{
		//String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
		//Log.i("GLInfo", extensions);
		int[] params = new int[64];
		
		GLES20.glGetIntegerv(GLES20.GL_ALIASED_LINE_WIDTH_RANGE, params, 0);
		Log.i("GLInfo", String.format("GLES20.GL_ALIASED_LINE_WIDTH_RANGE: %d - %d", params[0], params[1]));
	}
}
