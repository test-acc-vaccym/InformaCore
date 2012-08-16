package org.witness.informacam.app.editors.video;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.witness.informacam.storage.DatabaseHelper;
import org.witness.informacam.storage.IOCipherService;
import org.witness.informacam.utils.MediaHasher;
import org.witness.informacam.utils.Constants.App;
import org.witness.informacam.utils.Constants.Crypto;
import org.witness.informacam.utils.Constants.Informa;
import org.witness.informacam.utils.Constants.Media;
import org.witness.informacam.utils.Constants.Settings;
import org.witness.informacam.utils.Constants.Storage;
import org.witness.informacam.utils.Constants.Crypto.PGP;
import org.witness.informacam.utils.Constants.Informa.CaptureEvent;
import org.witness.informacam.utils.Constants.Informa.Keys.Genealogy;
import org.witness.informacam.utils.Constants.Storage.Tables;
import org.witness.informacam.utils.Constants.TrustedDestination;
import org.witness.informacam.app.editors.video.BinaryInstaller;
import org.witness.informacam.app.editors.video.ObscureRegion;
import org.witness.informacam.app.editors.video.RegionTrail;
import org.witness.informacam.app.editors.video.ShellUtils.ShellCallback;
import org.witness.informacam.crypto.EncryptionUtility;
import org.witness.informacam.informa.InformaService;
import org.witness.informacam.informa.LogPack;

import com.google.common.cache.LoadingCache;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

public class VideoConstructor {

	static String[] libraryAssets = {"ffmpeg"};
	static File fileBinDir;
	Context context;
	
	JSONObject metadataObject;
	ArrayList<Map<Long, String>> metadataForEncryption;
	final static String LOGTAG = App.LOG;
	DatabaseHelper dh;
	SQLiteDatabase db;
	SharedPreferences sp;
	
	File clone;
	
	public static VideoConstructor videoConstructor;

	public VideoConstructor(Context _context) throws FileNotFoundException, IOException {
		context = _context;
		fileBinDir = context.getDir("bin",0);

		if (!new File(fileBinDir,libraryAssets[0]).exists())
		{
			BinaryInstaller bi = new BinaryInstaller(context,fileBinDir);
			bi.installFromRaw();
		}
		
		videoConstructor = this;
	}
	
	public static VideoConstructor getVideoConstructor() {
		return videoConstructor;
	}
	
	private static void execProcess(String[] cmds, ShellCallback sc) throws Exception {
			ProcessBuilder pb = new ProcessBuilder(cmds);
			pb.redirectErrorStream(true);
	    	Process process = pb.start();      
	    	
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
	
			String line;
			
			while ((line = reader.readLine()) != null)
			{
				if (sc != null)
					sc.shellOut(line.toCharArray());
			}

			
		    if (process != null) {
		    	process.destroy();        
		    }

	}
	
	public class FFMPEGArg
	{
		String key;
		String value;
		
		public static final String ARG_VIDEOCODEC = "vcodec";
		public static final String ARG_VERBOSITY = "v";
		public static final String ARG_FILE_INPUT = "i";
		public static final String ARG_SIZE = "-s";
		public static final String ARG_FRAMERATE = "-r";
		public static final String ARG_FORMAT = "-f";
		
	}
	
	public void processVideo(File redactSettingsFile, 
			ArrayList<RegionTrail> obscureRegionTrails, File inputFile, File outputFile, String format, int mDuration,
			int iWidth, int iHeight, int oWidth, int oHeight, int frameRate, int kbitRate, String vcodec, String acodec, ShellCallback sc) throws Exception {
		
		float widthMod = ((float)oWidth)/((float)iWidth);
		float heightMod = ((float)oHeight)/((float)iHeight);
		
		writeRedactData(redactSettingsFile, obscureRegionTrails, widthMod, heightMod, mDuration);
		    	
		if (vcodec == null)
			vcodec = "copy";//"libx264"
		
		if (acodec == null)
			acodec = "copy";
		
    	String ffmpegBin = new File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " +ffmpegBin);
    	
    	String[] ffmpegCommand = {ffmpegBin, "-v", "10", "-y", "-i", inputFile.getPath(), 
				"-vcodec", vcodec, 
				"-b", kbitRate+"k", 
				"-s",  oWidth + "x" + oHeight, 
				"-r", ""+frameRate,
				"-acodec", "copy",
				"-f", format,
				"-vf","redact=" + redactSettingsFile.getAbsolutePath(),
				outputFile.getPath()};
    	
    	clone = outputFile;
    	
    	//ffmpeg -v 10 -y -i /sdcard/org.witness.sscvideoproto/videocapture1042744151.mp4 -vcodec libx264 -b 3000k -s 720x480 -r 30 -acodec copy -f mp4 -vf 'redact=/data/data/org.witness.sscvideoproto/redact_unsort.txt' /sdcard/org.witness.sscvideoproto/new.mp4
    	
    	//"-vf" , "redact=" + Environment.getExternalStorageDirectory().getPath() + "/" + PACKAGENAME + "/redact_unsort.txt",

    	
    	// Need to make sure this will create a legitimate mp4 file
    	//"-acodec", "ac3", "-ac", "1", "-ar", "16000", "-ab", "32k",
    	

    	/*
    	String[] ffmpegCommand = {"/data/data/"+PACKAGENAME+"/ffmpeg", "-v", "10", "-y", "-i", recordingFile.getPath(), 
    					"-vcodec", "libx264", "-b", "3000k", "-vpre", "baseline", "-s", "720x480", "-r", "30",
    					//"-vf", "drawbox=10:20:200:60:red@0.5",
    					"-vf" , "\"movie="+ overlayImage.getPath() +" [logo];[in][logo] overlay=0:0 [out]\"",
    					"-acodec", "copy",
    					"-f", "mp4", savePath.getPath()+"/output.mp4"};
    	*/
    	
    	execProcess(ffmpegCommand, sc);
	    
	}
	
	private void writeRedactData(File redactSettingsFile, ArrayList<RegionTrail> regionTrails, float widthMod, float heightMod, int mDuration) throws IOException {
		// Write out the finger data
					
		FileWriter redactSettingsFileWriter = new FileWriter(redactSettingsFile);
		PrintWriter redactSettingsPrintWriter = new PrintWriter(redactSettingsFileWriter);
		ObscureRegion or = null, lastOr = null;
		String orData = "";
		
		for (RegionTrail trail : regionTrails)
		{
			
			if (trail.isDoTweening())
			{
				int timeInc = 100;
				
				for (int i = 0; i < mDuration; i = i+timeInc)
				{
					or = trail.getCurrentRegion(i, trail.isDoTweening());
					if (or != null)
					{
						if(!trail.getObscureMode().equals(RegionTrail.OBSCURE_MODE_IDENTIFY)) {
							orData = or.getStringData(widthMod, heightMod,i,timeInc, trail.getObscureMode());
							redactSettingsPrintWriter.println(orData);
						}
					}
				}
				
			}
			else
			{
				
				for (Integer orKey : trail.getRegionKeys())
				{
					or = trail.getRegion(orKey);
					
					if (lastOr != null)
					{
						if(!trail.getObscureMode().equals(RegionTrail.OBSCURE_MODE_IDENTIFY))
							orData = lastOr.getStringData(widthMod, heightMod,or.timeStamp,or.timeStamp-lastOr.timeStamp, trail.getObscureMode());
					}
					
					
					redactSettingsPrintWriter.println(orData);
					
					lastOr = or;
				}
				
				if (or != null)
				{
					if(!trail.getObscureMode().equals(RegionTrail.OBSCURE_MODE_IDENTIFY)) {
						orData = lastOr.getStringData(widthMod, heightMod,or.timeStamp,or.timeStamp-lastOr.timeStamp, trail.getObscureMode());
						redactSettingsPrintWriter.println(orData);
					}
				}
			}
		}
		
		redactSettingsPrintWriter.flush();
		
		redactSettingsPrintWriter.close();

				
	}
	
	class FileMover {

		InputStream inputStream;
		File destination;
		
		public FileMover(InputStream _inputStream, File _destination) {
			inputStream = _inputStream;
			destination = _destination;
		}
		
		public void moveIt() throws IOException {
		
			OutputStream destinationOut = new BufferedOutputStream(new FileOutputStream(destination));
				
			int numRead;
			byte[] buf = new byte[1024];
			while ((numRead = inputStream.read(buf) ) >= 0) {
				destinationOut.write(buf, 0, numRead);
			}
			    
			destinationOut.flush();
			destinationOut.close();
		}
	}
	
	public void buildInformaVideo(Context c, LoadingCache<Long, LogPack> annotationCache, long[] encryptList) {
		long saveTime = System.currentTimeMillis();
		InformaService.getInstance().informa.setSaveTime(saveTime);
		
		dh = new DatabaseHelper(c);
		db = dh.getWritableDatabase(PreferenceManager.getDefaultSharedPreferences(c).getString(Settings.Keys.CURRENT_LOGIN, ""));
		
		try {
			dh.setTable(db, Tables.Keys.KEYRING);
			
			for(long td : encryptList) {
				Cursor cursor = dh.getValue(db, new String[] {PGP.Keys.PGP_DISPLAY_NAME, PGP.Keys.PGP_EMAIL_ADDRESS, PGP.Keys.PGP_PUBLIC_KEY, Crypto.Keyring.Keys.TRUSTED_DESTINATION_ID}, TrustedDestination.Keys.KEYRING_ID, td);
				
				if(cursor != null && cursor.moveToFirst()) {
					for(String s : cursor.getColumnNames())
						Log.d(Storage.LOG, s);
					
					String forName = cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_DISPLAY_NAME));
					Log.d(Storage.LOG, forName);
					
					// add into intent
					InformaService.getInstance().informa.setTrustedDestination(cursor.getString(cursor.getColumnIndex(PGP.Keys.PGP_EMAIL_ADDRESS)));
					
					
					// bundle up informadata
					String informaMetadata = EncryptionUtility.encrypt(InformaService.getInstance().informa.bundle().getBytes(), cursor.getBlob(cursor.getColumnIndex(PGP.Keys.PGP_PUBLIC_KEY)));
					
					
					// insert metadata
					File version = new File(Storage.FileIO.DUMP_FOLDER, System.currentTimeMillis() + "_" + forName.replace(" ", "-") + Media.Type.MKV);
					constructVideo(version, informaMetadata);
					
					// XXX:  move back to IOCipher and remove this version from public filestore
					info.guardianproject.iocipher.File ioCipherVersion = IOCipherService.getInstance().moveFileToIOCipher(version, MediaHasher.hash(clone, "SHA-1"), true);
					
					
					dh.setTable(db, Tables.Keys.MEDIA);
					db.insert(dh.getTable(), null, InformaService.getInstance().informa.initMetadata(ioCipherVersion.getAbsolutePath(), cursor.getLong(cursor.getColumnIndex(Crypto.Keyring.Keys.TRUSTED_DESTINATION_ID))));
					
					cursor.close();
				}
			}
			
			db.close();
			dh.close();
			
			InformaService.getInstance().versionsCreated();
		} catch(IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
	
	public void constructVideo(File video, String metadata) throws IOException, JSONException {
		String ffmpegBin = new File(fileBinDir,"ffmpeg").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " + ffmpegBin);
		
		File mdFile = stringToFile(metadata, Storage.FileIO.DUMP_FOLDER, Storage.FileIO.TMP_VIDEO_DATA_FILE_NAME);
		
		String[] ffmpegCommand = new String[] {
			ffmpegBin, "-i", clone.getAbsolutePath(),
			"-attach", mdFile.getAbsolutePath(),
			"-metadata:s:2", "mimetype=text/plain",
			"-vcodec", "copy",
			"-acodec", "copy",
			video.getAbsolutePath()
		};
		
		StringBuffer sb = new StringBuffer();
		for(String f: ffmpegCommand) {
			sb.append(f + " ");
		}
		Log.d(LOGTAG, "command to ffmpeg: " + sb.toString());
		
		try {
			execProcess(ffmpegCommand, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static File stringToFile(String data, String dir, String filename) {
		File file = new File(dir, filename);
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(file));
			out.write(data);
			out.close();
			return file;
		} catch(IOException e) {
			return null;
		}
		
	}

}